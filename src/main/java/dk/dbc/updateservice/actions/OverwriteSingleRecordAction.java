/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcFieldReader;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.utils.LogUtils;
import dk.dbc.common.records.utils.RecordContentTransformer;
import dk.dbc.marcrecord.ExpandCommonMarcRecord;
import dk.dbc.rawrepo.RawRepoException;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.DefaultEnrichmentRecordHandler;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.vipcore.exception.VipCoreException;
import dk.dbc.vipcore.libraryrules.VipCoreLibraryRulesConnector;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

class OverwriteSingleRecordAction extends AbstractRawRepoAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(OverwriteSingleRecordAction.class);
    private MarcRecord currentMarcRecord = null;
    protected Properties settings;

    OverwriteSingleRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord marcRecord) {
        super(OverwriteSingleRecordAction.class.getSimpleName(), globalActionState, marcRecord);
        settings = properties;
    }

    /**
     * Performs this actions and may create any child actions.
     *
     * @return A list of ValidationError to be reported in the web service response.
     * @throws UpdateException In case of an error.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        logger.entry();
        ServiceResult result = ServiceResult.newOkResult();
        try {
            logger.info("Handling record: {}", LogUtils.base64Encode(record));
            MarcRecordReader reader = new MarcRecordReader(record);
            if (RawRepo.DBC_PRIVATE_AGENCY_LIST.contains(reader.getAgencyId())) {
                performActionDBCRecord();
            } else {
                performActionDefault();
            }
            return result;
        } catch (UnsupportedEncodingException | RawRepoException ex) {
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, ex.getMessage());
        } finally {
            logger.exit(result);
        }
    }

    void performActionDBCRecord() throws UnsupportedEncodingException, UpdateException, RawRepoException {
        logger.info("Performing action for DBC record");
        final MarcRecordReader reader = new MarcRecordReader(record);

        children.add(StoreRecordAction.newStoreMarcXChangeAction(state, settings, record));
        children.add(new RemoveLinksAction(state, record));

        if (reader.getParentRecordId() != null) {
            children.add(LinkRecordAction.newLinkParentAction(state, record));
        }

        // If this is an authority record being updated, then we need to see if any depending common records needs updating
        if (RawRepo.AUTHORITY_AGENCY == reader.getAgencyIdAsInt()) {
            logger.info("Agency is 870979 - handling actions for child records");
            final boolean shouldUpdateChildrenModifiedDate = shouldUpdateChildrenModifiedDate(record);
            final boolean authorityHasClassificationChange = authorityRecordHasClassificationChange(record);

            if (shouldUpdateChildrenModifiedDate || authorityHasClassificationChange) {
                final Map<String, MarcRecord> otherAuthorityRecords = new HashMap<>();
                final Set<RecordId> ids = state.getRawRepo().children(record);

                for (RecordId id : ids) {
                    logger.info("Found child record for {}:{} - {}:{}", reader.getRecordId(), reader.getAgencyId(), id.getBibliographicRecordId(), id.getAgencyId());
                    final MarcRecord currentChildRecord = RecordContentTransformer.decodeRecord(rawRepo.fetchMergedRecord(id.getBibliographicRecordId(), id.getAgencyId()).getContent());

                    if (shouldUpdateChildrenModifiedDate) {
                        // First we need to update 001 *c on all direct children. 001 *c is updated by StoreRecordAction so we
                        // don't actually have to change anything in the child record
                        children.add(new UpdateCommonRecordAction(state, settings, currentChildRecord));

                        // We also need to change the modified date on all DBC enrichments and this way we also make sure to queue all the enrichments
                        final Set<RecordId> enrichmentsToChild = state.getRawRepo().enrichments(id);
                        for (RecordId enrichmentToChild : enrichmentsToChild) {
                            if (RawRepo.DBC_ENRICHMENT == enrichmentToChild.getAgencyId()) {
                                final MarcRecord dbcEnrichment = RecordContentTransformer.decodeRecord(state.getRawRepo().fetchRecord(
                                        enrichmentToChild.getBibliographicRecordId(), enrichmentToChild.getAgencyId()).getContent());
                                children.add(new UpdateEnrichmentRecordAction(state, settings, dbcEnrichment, id.getAgencyId()));
                            }
                        }
                    }


                    if (authorityHasClassificationChange) {
                        // If there is classification change in the authority record we need to update all the child records
                        final Set<RecordId> parents = rawRepo.parents(id);

                        // For authority records with many children there is probably an overlap in other authority records
                        // which the children are using. In an effort to optimize performance the other authority records
                        // are added to a list which is used to expand all children thus an authority record is only fetched once.
                        for (RecordId parent : parents) {
                            if (870979 == parent.getAgencyId() && !otherAuthorityRecords.containsKey(parent.getBibliographicRecordId())) {
                                final Record childRecord = rawRepo.fetchMergedRecord(parent.getBibliographicRecordId(), parent.getAgencyId());
                                otherAuthorityRecords.put(parent.getBibliographicRecordId(), RecordContentTransformer.decodeRecord(childRecord.getContent()));
                            }
                        }

                        final Map<String, MarcRecord> updatedRecordCollection = new HashMap<>(otherAuthorityRecords);
                        updatedRecordCollection.put(id.getBibliographicRecordId(), currentChildRecord);
                        updatedRecordCollection.put(reader.getRecordId(), record);

                        final Map<String, MarcRecord> currentRecordCollection = new HashMap<>(otherAuthorityRecords);
                        currentRecordCollection.put(id.getBibliographicRecordId(), currentChildRecord);
                        currentRecordCollection.put(reader.getRecordId(), loadCurrentRecord());

                        try {
                            final MarcRecord currentCommonRecord = state.getRecordSorter().sortRecord(
                                    ExpandCommonMarcRecord.expandMarcRecord(currentRecordCollection, id.getBibliographicRecordId()));
                            final MarcRecord updatedCommonRecord = state.getRecordSorter().sortRecord(
                                    ExpandCommonMarcRecord.expandMarcRecord(updatedRecordCollection, id.getBibliographicRecordId()));
                            children.addAll(createActionsForCreateOrUpdateEnrichments(updatedCommonRecord, currentCommonRecord));
                        } catch (RawRepoException e) {
                            throw new UpdateException("Exception while expanding the records", e);
                        }
                    }
                }
            }
        }

        if (RawRepo.MATVURD_AGENCY == reader.getAgencyIdAsInt()) {
            logger.info("Agency is 870976 - adding link action for r01 and r02");
            // The links are not in the record passed to this action because the record has been split in a common part
            // and an enrichment and r01 and r02 are in the enrichment.
            // Instead we have to read the original request record
            children.add(new LinkMatVurdRecordsAction(state, state.readRecord()));
        }

        children.add(new LinkAuthorityRecordsAction(state, record));
        children.add(EnqueueRecordAction.newEnqueueAction(state, record, settings));
    }

    /**
     * This function checks if the authority record has been changed in a way which affects proof printing (korrekturprint)
     * <p>
     * The rule is the child common records should be updated if field 100/110 (non repeatable), 400/410 (repeatable) or 500/510 (repeatable)
     * in the authority record has been changed.
     *
     * @param record The incoming authority record
     * @return True if field 100, 110, 400, 410, 500 or 510 has been changed
     * @throws UpdateException
     * @throws UnsupportedEncodingException
     */
    boolean shouldUpdateChildrenModifiedDate(MarcRecord record) throws UpdateException, RawRepoException, UnsupportedEncodingException {
        final MarcRecordReader reader = new MarcRecordReader(record);

        // Suppress updating B-records if A-record has "minusAJOUR" even if there are proof printing changes in field 100/400/500.
        // s13 will not be present in the common record, so we have to look at the input record
        final MarcRecordReader inputRecordReader = new MarcRecordReader(state.getMarcRecord());
        if (inputRecordReader.hasValue("s13", "a", "minusAJOUR")) {
            return false;
        }


        if (state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())) {
            final MarcRecord currentRecord = loadCurrentRecord();
            final MarcRecordReader currentReader = new MarcRecordReader(currentRecord);

            // Field exists in the updated record but not in the current record -> korrekturprint
            // It's a fact that both 100 and 110 may only exist as one field, but getField returns null if the field doesn't exist
            // therefore we get "all" fields of the to types. Number of fields are validated at another place.
            return !(currentReader.getFieldAll("100").equals(reader.getFieldAll("100")) &&
                    currentReader.getFieldAll("110").equals(reader.getFieldAll("110")) &&
                    currentReader.getFieldAll("400").equals(reader.getFieldAll("400")) &&
                    currentReader.getFieldAll("410").equals(reader.getFieldAll("410")) &&
                    currentReader.getFieldAll("500").equals(reader.getFieldAll("500")) &&
                    currentReader.getFieldAll("510").equals(reader.getFieldAll("510")));
        }

        return false;
    }

    boolean authorityRecordHasClassificationChange(MarcRecord record) throws UpdateException, RawRepoException, UnsupportedEncodingException {
        final MarcRecordReader reader = new MarcRecordReader(record);

        if (state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())) {
            final MarcRecord currentRecord = loadCurrentRecord();
            final MarcRecordReader currentReader = new MarcRecordReader(currentRecord);

            return !(currentReader.getFieldAll("100").equals(reader.getFieldAll("100")) &&
                    currentReader.getFieldAll("110").equals(reader.getFieldAll("110")));
        }

        return false;
    }

    /*
        This record find all agencies with enrichments or holdings for the volume records in under the input record.
        The function calls it self recursively until the hierarchy has been traversed
     */
    private void findChildrenAndHoldingsOnChildren(MarcRecord record, Set<Integer> librariesWithPosts) throws UpdateException, UnsupportedEncodingException {
        final MarcRecordReader reader = new MarcRecordReader(record);
        final RecordId recordId = new RecordId(reader.getRecordId(), reader.getAgencyIdAsInt());

        logger.info("Getting children for {}", recordId);

        if (recordIsHeadOrSection(record)) {
            for (RecordId child : state.getRawRepo().children(recordId)) {
                logger.info("Found child record {}", child);
                MarcRecord childRecord = RecordContentTransformer.decodeRecord(rawRepo.fetchRecord(child.getBibliographicRecordId(), child.getAgencyId()).getContent());
                findChildrenAndHoldingsOnChildren(childRecord, librariesWithPosts);
            }
        } else {
            logger.info("Getting holdings and agencies for volume {}", recordId);
            librariesWithPosts.addAll(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record));
            librariesWithPosts.addAll(state.getRawRepo().agenciesForRecordNotDeleted(record));
        }
    }

    private boolean recordIsHeadOrSection(MarcRecord record) {
        return Arrays.asList("s", "h").contains(new MarcRecordReader(record).getValue("004", "a"));
    }

    private void performActionDefault() throws UnsupportedEncodingException, UpdateException, RawRepoException {
        logger.info("Performing default action ");
        children.add(StoreRecordAction.newStoreMarcXChangeAction(state, settings, record));
        children.add(new RemoveLinksAction(state, record));

        MarcRecord currentExpandedRecord = loadCurrentRecord();
        MarcRecord newExpandedRecord = expandRecord();

        children.addAll(createActionsForCreateOrUpdateEnrichments(newExpandedRecord, currentExpandedRecord));
        children.add(new LinkAuthorityRecordsAction(state, record));
        children.add(EnqueueRecordAction.newEnqueueAction(state, record, settings));
        children.addAll(getEnqueuePHHoldingsRecordActions(state, record));
    }

    List<EnqueuePHHoldingsRecordAction> getEnqueuePHHoldingsRecordActions(GlobalActionState state, MarcRecord record) throws UpdateException {
        Set<Integer> holdingsLibraries = state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record);
        Set<String> phLibraries = state.getPHLibraries();
        List<EnqueuePHHoldingsRecordAction> result = new ArrayList<>();

        /*
            Special handling of PH libraries with holdings
            In order to update records in danbib for PH libraries it is necessary to inform dataIO when an
            common record which a PH library has holding on is updated.

            Note that the record with recordId = record.recordId and agency = phLibrary.agencyId probably doesn't exist
            but that isn't important as rawrepo won't be modified. This only serves as a marker for dataIO to do something.
         */
        for (Integer id : holdingsLibraries) {
            if (phLibraries.contains(id.toString())) {
                logger.info("Found PH library with holding! {}", id);
                RecordId recordId = new RecordId(new MarcRecordReader(record).getRecordId(), id);
                EnqueuePHHoldingsRecordAction enqueuePHHoldingsRecordAction = new EnqueuePHHoldingsRecordAction(state, settings, record, recordId);
                result.add(enqueuePHHoldingsRecordAction);
            }
        }

        return result;
    }

    MarcRecord loadCurrentRecord() throws UpdateException, RawRepoException, UnsupportedEncodingException {
        logger.entry();
        MarcRecord result = null;
        try {
            if (this.currentMarcRecord == null) {
                final MarcRecordReader reader = new MarcRecordReader(record);
                final String recordId = reader.getRecordId();
                final int agencyId = reader.getAgencyIdAsInt();
                if (RawRepo.AUTHORITY_AGENCY == agencyId) {
                    final Record currentRecord = rawRepo.fetchRecord(recordId, agencyId);

                    this.currentMarcRecord = RecordContentTransformer.decodeRecord(currentRecord.getContent());
                } else {
                    final Map<String, MarcRecord> currentRecordCollection = rawRepo.fetchRecordCollection(recordId, agencyId);

                    this.currentMarcRecord = state.getRecordSorter().sortRecord(ExpandCommonMarcRecord.expandMarcRecord(currentRecordCollection, recordId));
                }
            }

            result = this.currentMarcRecord;
            return result;
        } finally {
            logger.exit(result);
        }
    }

    protected MarcRecord loadRecord(String recordId, int agencyId) throws UpdateException, UnsupportedEncodingException {
        logger.entry(recordId, agencyId);
        MarcRecord result = null;
        try {
            Record record = rawRepo.fetchRecord(recordId, agencyId);
            return result = RecordContentTransformer.decodeRecord(record.getContent());
        } finally {
            logger.exit(result);
        }
    }

    MarcRecord expandRecord() throws UpdateException, UnsupportedEncodingException, RawRepoException {
        logger.entry();
        MarcRecord result = null;
        try {
            final MarcRecordReader reader = new MarcRecordReader(record);
            final String recordId = reader.getRecordId();

            final Map<String, MarcRecord> newRecordCollection = new HashMap<>();
            newRecordCollection.put(recordId, record);

            for (MarcField field : record.getFields()) {
                final MarcFieldReader fieldReader = new MarcFieldReader(field);

                if (fieldReader.hasSubfield("5") && fieldReader.hasSubfield("6")) {
                    final String autRecordId = fieldReader.getValue("6");

                    final Record extRecord = rawRepo.fetchRecord(autRecordId, RawRepo.AUTHORITY_AGENCY);
                    final MarcRecord autRecord = RecordContentTransformer.decodeRecord(extRecord.getContent());
                    newRecordCollection.put(autRecordId, autRecord);
                }
            }

            result = state.getRecordSorter().sortRecord(ExpandCommonMarcRecord.expandMarcRecord(newRecordCollection, recordId));

            return result;
        } finally {
            logger.exit(result);
        }

    }

    boolean hasMinusEnrichment() {
        MarcRecord inputRecord = state.getMarcRecord();
        MarcRecordReader inputRecordReader = new MarcRecordReader(inputRecord);

        return inputRecordReader.hasValue("z98", "b", "Minus påhængspost");
    }

    List<ServiceAction> createActionsForCreateOrUpdateEnrichments(MarcRecord record, MarcRecord currentRecord) throws UpdateException, UnsupportedEncodingException {
        logger.entry(record);
        List<ServiceAction> result = new ArrayList<>();
        try {
            final MarcRecordReader reader = new MarcRecordReader(record);
            final String recordId = reader.getRecordId();
            final int agencyId = reader.getAgencyIdAsInt();

            if (!hasMinusEnrichment() && state.getLibraryRecordsHandler().hasClassificationData(currentRecord) &&
                    state.getLibraryRecordsHandler().hasClassificationData(record) &&
                    state.getLibraryRecordsHandler().hasClassificationsChanged(currentRecord, record)) {
                logger.info("Classifications was changed for common record [{}:{}]", recordId, agencyId);

                final Set<Integer> librariesWithPosts = new HashSet<>();
                findChildrenAndHoldingsOnChildren(record, librariesWithPosts);

                logger.info("Found holdings or enrichments record for: {}", librariesWithPosts.toString());

                for (int id : librariesWithPosts) {
                    if (!state.getVipCoreService().hasFeature(Integer.toString(id), VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)) {
                        continue;
                    }
                    if (rawRepo.recordExists(recordId, id)) {
                        Record extRecord = rawRepo.fetchRecord(recordId, id);
                        MarcRecord extRecordData = RecordContentTransformer.decodeRecord(extRecord.getContent());
                        logger.info("Update classifications for extended library record: [{}:{}]", recordId, id);
                        result.add(getUpdateClassificationsInEnrichmentRecordActionData(extRecordData, record, currentRecord, Integer.toString(id)));
                    } else if (state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId().equals(Integer.toString(id))) {
                        logger.info("Enrichment record is not created for record [{}:{}], because groupId equals agencyid", recordId, id);
                    } else {
                        if (DefaultEnrichmentRecordHandler.shouldCreateEnrichmentRecordsResult(state.getMessages(), record, currentRecord)) {
                            logger.info("Create new enrichment library record: [{}:{}].", recordId, id);
                            result.add(getActionDataForEnrichmentWithClassification(record, currentRecord, Integer.toString(id)));
                        } else {
                            logger.warn("Enrichment record {{}:{}} was not created, because none of the common records was published.", recordId, id);
                        }
                    }
                }
            }
            return result;
        } catch (VipCoreException ex) {
            throw new UpdateException(ex.getMessage(), ex);
        } finally {
            logger.exit(result);
        }
    }

    private CreateEnrichmentRecordWithClassificationsAction getUpdateClassificationsInEnrichmentRecordActionData(MarcRecord extRecordData, MarcRecord record, MarcRecord currentRecord, String id) {
        logger.entry(extRecordData, currentRecord, id);
        UpdateClassificationsInEnrichmentRecordAction updateClassificationsInEnrichmentRecordAction = null;
        try {
            updateClassificationsInEnrichmentRecordAction = new UpdateClassificationsInEnrichmentRecordAction(state, settings, id);
            updateClassificationsInEnrichmentRecordAction.setEnrichmentRecord(extRecordData);
            updateClassificationsInEnrichmentRecordAction.setCurrentCommonRecord(currentRecord);
            updateClassificationsInEnrichmentRecordAction.setUpdatingCommonRecord(record);
            return updateClassificationsInEnrichmentRecordAction;
        } finally {
            logger.exit(updateClassificationsInEnrichmentRecordAction);
        }
    }

    private CreateEnrichmentRecordWithClassificationsAction getActionDataForEnrichmentWithClassification(MarcRecord record, MarcRecord currentRecord, String holdingAgencyId) {
        logger.entry(record, holdingAgencyId, currentRecord);
        CreateEnrichmentRecordWithClassificationsAction createEnrichmentRecordWithClassificationsAction = null;
        try {
            createEnrichmentRecordWithClassificationsAction = new CreateEnrichmentRecordWithClassificationsAction(state, settings, holdingAgencyId);
            createEnrichmentRecordWithClassificationsAction.setCurrentCommonRecord(currentRecord);
            createEnrichmentRecordWithClassificationsAction.setUpdatingCommonRecord(record);
            return createEnrichmentRecordWithClassificationsAction;
        } finally {
            logger.exit(createEnrichmentRecordWithClassificationsAction);
        }
    }

}
