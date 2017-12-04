/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.utils.LogUtils;
import dk.dbc.common.records.utils.RecordContentTransformer;
import dk.dbc.marcrecord.ExpandCommonMarcRecord;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.openagency.client.OpenAgencyException;
import dk.dbc.rawrepo.RawRepoException;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.update.DefaultEnrichmentRecordHandler;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.*;

class OverwriteSingleRecordAction extends AbstractRawRepoAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(OverwriteSingleRecordAction.class);

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
        } catch (ScripterException | UnsupportedEncodingException ex) {
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, ex.getMessage(), state);
        } finally {
            logger.exit(result);
        }
    }

    void performActionDBCRecord() throws UnsupportedEncodingException, UpdateException, ScripterException {
        MarcRecordReader reader = new MarcRecordReader(record);

        children.add(StoreRecordAction.newStoreMarcXChangeAction(state, settings, record));

        if (reader.getParentRecordId() != null) {
            children.add(new RemoveLinksAction(state, record));
            children.add(LinkRecordAction.newLinkParentAction(state, record));
        }

        // If this is an authority record being updated, then we need to see if any depending common records needs updating
        if (RawRepo.AUTHORITY_AGENCY == reader.getAgencyIdAsInt()) {
            Set<RecordId> ids = state.getRawRepo().children(record);
            for (RecordId id : ids) {
                logger.info("Found child record for {}:{} - {}:{}", reader.getRecordId(), reader.getAgencyId(), id.getBibliographicRecordId(), id.getAgencyId());
                Map<String, MarcRecord> records = getRawRepo().fetchRecordCollection(id.getBibliographicRecordId(), id.getAgencyId());
                try {
                    MarcRecord currentRecord = state.getRecordSorter().sortRecord(ExpandCommonMarcRecord.expandMarcRecord(records), settings);
                    records.put(reader.getRecordId(), record);
                    MarcRecord updatedCommonRecord = state.getRecordSorter().sortRecord(ExpandCommonMarcRecord.expandMarcRecord(records), settings);
                    children.addAll(createActionsForCreateOrUpdateEnrichments(updatedCommonRecord, currentRecord));
                } catch (RawRepoException e) {
                    throw new UpdateException("Exception while expanding the records", e);
                }

            }
        }
        children.add(EnqueueRecordAction.newEnqueueAction(state, record, settings));
    }

    private void performActionDefault() throws UnsupportedEncodingException, UpdateException, ScripterException {
        MarcRecord currentRecord = loadCurrentRecord();

        children.add(StoreRecordAction.newStoreMarcXChangeAction(state, settings, record));
        children.add(new RemoveLinksAction(state, record));
        children.addAll(createActionsForCreateOrUpdateEnrichments(record, currentRecord));


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

    MarcRecord loadCurrentRecord() throws UpdateException, UnsupportedEncodingException {
        logger.entry();
        MarcRecord result = null;
        try {
            MarcRecordReader reader = new MarcRecordReader(record);
            String recordId = reader.getRecordId();
            int agencyId = reader.getAgencyIdAsInt();

            return result = loadRecord(recordId, agencyId);
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

    List<ServiceAction> createActionsForCreateOrUpdateEnrichments(MarcRecord record, MarcRecord currentRecord) throws ScripterException, UpdateException, UnsupportedEncodingException {
        logger.entry(currentRecord);
        List<ServiceAction> result = new ArrayList<>();
        try {
            MarcRecordReader reader = new MarcRecordReader(record);
            String recordId = reader.getRecordId();
            int agencyId = reader.getAgencyIdAsInt();

            if (state.getLibraryRecordsHandler().hasClassificationData(currentRecord) && state.getLibraryRecordsHandler().hasClassificationData(record)) {
                if (state.getLibraryRecordsHandler().hasClassificationsChanged(currentRecord, record)) {

                    logger.info("Classifications was changed for common record [{}:{}]", recordId, agencyId);
                    Set<Integer> holdingsLibraries = state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record);
                    Set<Integer> enrichmentLibraries = state.getRawRepo().agenciesForRecordNotDeleted(record);

                    Set<Integer> librariesWithPosts = new HashSet<>();
                    librariesWithPosts.addAll(holdingsLibraries);
                    librariesWithPosts.addAll(enrichmentLibraries);

                    logger.info("Found holdings or enrichments record for: {}", holdingsLibraries.toString());

                    for (int id : librariesWithPosts) {
                        logger.info("Local library for record: {}", id);

                        if (!state.getOpenAgencyService().hasFeature(Integer.toString(id), LibraryRuleHandler.Rule.USE_ENRICHMENTS)) {
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
            }
            return result;
        } catch (OpenAgencyException ex) {
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
