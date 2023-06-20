package dk.dbc.updateservice.actions;

import dk.dbc.common.records.ExpandCommonMarcRecord;
import dk.dbc.common.records.MarcRecordExpandException;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.marc.binding.DataField;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.marc.binding.SubField;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.DefaultEnrichmentRecordHandler;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.update.UpdateRecordContentTransformer;
import dk.dbc.updateservice.utils.DeferredLogger;
import dk.dbc.vipcore.exception.VipCoreException;
import dk.dbc.vipcore.libraryrules.VipCoreLibraryRulesConnector;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static dk.dbc.marc.binding.DataField.hasSubFieldCode;
import static dk.dbc.updateservice.update.DefaultEnrichmentRecordHandler.hasMinusEnrichment;

class OverwriteSingleRecordAction extends AbstractRawRepoAction {
    private static final DeferredLogger LOGGER = new DeferredLogger(OverwriteSingleRecordAction.class);
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
        try {
            final MarcRecordReader reader = new MarcRecordReader(marcRecord);
            if (RawRepo.DBC_PRIVATE_AGENCY_LIST.contains(reader.getAgencyId())) {
                performActionDBCRecord();
            } else {
                performActionDefault();
            }
            return ServiceResult.newOkResult();
        } catch (MarcRecordExpandException ex) {
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, ex.getMessage());
        }
    }

    void performActionDBCRecord() throws UpdateException, MarcRecordExpandException {
        LOGGER.use(log -> log.info("Performing action for DBC record"));
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);

        children.add(StoreRecordAction.newStoreMarcXChangeAction(state, settings, marcRecord));
        children.add(new RemoveLinksAction(state, marcRecord));

        if (reader.getParentRecordId() != null) {
            children.add(LinkRecordAction.newLinkParentAction(state, marcRecord));
        }

        // If this is an authority record being updated, then we need to see if any depending common records needs updating
        if (RawRepo.AUTHORITY_AGENCY == reader.getAgencyIdAsInt()) {
            LOGGER.use(log -> log.info("Agency is 870979 - handling actions for child records"));
            final boolean shouldUpdateChildrenModifiedDate = shouldUpdateChildrenModifiedDate(marcRecord);
            final boolean authorityHasClassificationChange = authorityRecordHasClassificationChange(marcRecord);

            if (shouldUpdateChildrenModifiedDate || authorityHasClassificationChange) {
                final Map<String, MarcRecord> otherAuthorityRecords = new HashMap<>();
                final Set<RecordId> ids = state.getRawRepo().children(recordId);

                for (RecordId id : ids) {
                    LOGGER.use(log -> log.info("Found child record for {}:{} - {}:{}", reader.getRecordId(), reader.getAgencyId(), id.getBibliographicRecordId(), id.getAgencyId()));

                    if (shouldUpdateChildrenModifiedDate) {
                        // First we need to update 001 *c on all direct children. 001 *c is updated by StoreRecordAction, so we
                        // don't actually have to change anything in the child record
                        children.add(StoreRecordAction.newStoreMarcXChangeAction(state, settings, id));
                        children.add(EnqueueRecordAction.newEnqueueAction(state, id, settings));

                        // We also need to change the modified date on all DBC enrichments and this way we also make sure to queue all the enrichments
                        final Set<RecordId> enrichmentsToChild = state.getRawRepo().enrichments(id);
                        for (RecordId enrichmentToChild : enrichmentsToChild) {
                            if (RawRepo.DBC_ENRICHMENT == enrichmentToChild.getAgencyId()) {
                                children.add(StoreRecordAction.newStoreEnrichmentAction(state, settings, enrichmentToChild));
                                children.add(EnqueueRecordAction.newEnqueueAction(state, enrichmentToChild, settings));
                            }
                        }
                    }

                    if (authorityHasClassificationChange) {
                        final MarcRecord currentChildRecord = UpdateRecordContentTransformer.decodeRecord(rawRepo.fetchMergedRecord(id.getBibliographicRecordId(), id.getAgencyId()).getContent());
                        // If there is classification change in the authority record we need to update all the child records
                        final Set<RecordId> parents = rawRepo.parents(id);

                        // For authority records with many children there is probably an overlap in other authority records
                        // which the children are using. In an effort to optimize performance the other authority records
                        // are added to a list which is used to expand all children thus an authority record is only fetched once.
                        for (RecordId parent : parents) {
                            if (870979 == parent.getAgencyId() && !otherAuthorityRecords.containsKey(parent.getBibliographicRecordId())) {
                                final Record childRecord = rawRepo.fetchMergedRecord(parent.getBibliographicRecordId(), parent.getAgencyId());
                                otherAuthorityRecords.put(parent.getBibliographicRecordId(), UpdateRecordContentTransformer.decodeRecord(childRecord.getContent()));
                            }
                        }

                        final Map<String, MarcRecord> updatedRecordCollection = new HashMap<>(otherAuthorityRecords);
                        updatedRecordCollection.put(id.getBibliographicRecordId(), currentChildRecord);
                        updatedRecordCollection.put(reader.getRecordId(), marcRecord);

                        final Map<String, MarcRecord> currentRecordCollection = new HashMap<>(otherAuthorityRecords);
                        currentRecordCollection.put(id.getBibliographicRecordId(), currentChildRecord);
                        currentRecordCollection.put(reader.getRecordId(), loadCurrentRecord());

                        try {
                            final MarcRecord currentCommonRecord = state.getRecordSorter().sortRecord(
                                    ExpandCommonMarcRecord.expandMarcRecord(currentRecordCollection, id.getBibliographicRecordId()));
                            final MarcRecord updatedCommonRecord = state.getRecordSorter().sortRecord(
                                    ExpandCommonMarcRecord.expandMarcRecord(updatedRecordCollection, id.getBibliographicRecordId()));
                            children.addAll(createActionsForCreateOrUpdateEnrichments(updatedCommonRecord, currentCommonRecord));
                        } catch (MarcRecordExpandException e) {
                            throw new UpdateException("Exception while expanding the records", e);
                        }
                    }
                }
            }

            // Please note that this function may modify one or more B-records in the common part of DBC records.
            // At the moment it doesn't disturb, but RDA may give some headaches in the future.
            handleUniverseLinks(marcRecord);
        }

        if (RawRepo.MATVURD_AGENCY == reader.getAgencyIdAsInt()) {
            LOGGER.use(log -> log.info("Agency is 870976 - adding link action for r01 and r02"));
            // The links are not in the record passed to this action because the record has been split in a common part
            // and an enrichment and r01 and r02 are in the enrichment.
            // Instead, we have to read the original request record
            children.add(new LinkMatVurdRecordsAction(state, state.readRecord()));
        }

        children.add(new LinkAuthorityRecordsAction(state, marcRecord));
        children.add(EnqueueRecordAction.newEnqueueAction(state, marcRecord, settings));
    }

    /**
     * This function checks if the authority record has been changed in a way which affects proof printing (korrekturprint)
     * <p>
     * The rule is the child common records should be updated if field 100/110 (non repeatable), 400/410 (repeatable) or 500/510 (repeatable)
     * in the authority record has been changed.
     *
     * @param marcRecord The incoming authority record
     * @return True if field 100, 110, 400, 410, 500 or 510 has been changed
     * @throws UpdateException Something went horribly wrong
     */
    boolean shouldUpdateChildrenModifiedDate(MarcRecord marcRecord) throws UpdateException, MarcRecordExpandException {
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);

        // Suppress updating B-records if A-record has "minusAJOUR" even if there are proof printing changes in field 100/400/500.
        // s13 will not be present in the common record, so we have to look at the input record
        final MarcRecordReader inputRecordReader = new MarcRecordReader(state.getMarcRecord());
        if (inputRecordReader.hasValue("s13", 'a', "minusAJOUR")) {
            return false;
        }


        if (state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())) {
            final MarcRecord currentRecord = loadCurrentRecord();
            final MarcRecordReader currentReader = new MarcRecordReader(currentRecord);

            // Field exists in the updated record but not in the current record -> korrekturprint
            // It's a fact that both 100 and 110 may only exist as one field, but getField returns null if the field doesn't exist
            // therefore we get "all" fields of the two types. Number of fields are validated at another place.
            return !(currentReader.getFieldAll("100").equals(reader.getFieldAll("100")) &&
                    currentReader.getFieldAll("110").equals(reader.getFieldAll("110")) &&
                    currentReader.getFieldAll("133").equals(reader.getFieldAll("133")) &&
                    currentReader.getFieldAll("134").equals(reader.getFieldAll("134")) &&
                    currentReader.getFieldAll("400").equals(reader.getFieldAll("400")) &&
                    currentReader.getFieldAll("410").equals(reader.getFieldAll("410")) &&
                    currentReader.getFieldAll("433").equals(reader.getFieldAll("433")) &&
                    currentReader.getFieldAll("434").equals(reader.getFieldAll("434")) &&
                    currentReader.getFieldAll("500").equals(reader.getFieldAll("500")) &&
                    currentReader.getFieldAll("510").equals(reader.getFieldAll("510")));
        }

        return false;
    }

    /**
     * There are three tings that will be handled, adding a universe, removing one and replacing one
     * There can only be one universe connected to a series record but there can be several B-records
     * that shall have their universe modified.
     * This modification cannot affect classification and enrichment.
     *
     * @param marcRecord The 870979 record that are updated.
     * @throws UpdateException           Something went wrong - multiple reasons.
     * @throws MarcRecordExpandException Something went wrong - multiple reasons.
     */
    void handleUniverseLinks(MarcRecord marcRecord) throws UpdateException, MarcRecordExpandException {
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);

        if (state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())) {
            final MarcRecord currentRecord = loadCurrentRecord();
            final MarcRecordReader currentReader = new MarcRecordReader(currentRecord);
            final DataField currentReaderField = currentReader.getField("234");
            final DataField newField = reader.getField("234");
            final Set<RecordId> ids = state.getRawRepo().children(recordId);
            String link = "";
            if (newField != null) {
                for (SubField subField : newField.getSubFields()) {
                    if ('6' == subField.getCode()) {
                        link = subField.getData();
                    }
                }
            }

            for (RecordId id : ids) {
                final MarcRecord currentChildRecord = UpdateRecordContentTransformer.decodeRecord(rawRepo.fetchMergedRecord(id.getBibliographicRecordId(), id.getAgencyId()).getContent());
                final MarcRecordWriter currentChildWriter = new MarcRecordWriter(currentChildRecord);
                final MarcRecordReader currentChildReader = new MarcRecordReader(currentChildRecord);
                boolean createAction = false;
                if (currentReaderField == null && newField != null) {
                    // handle new universe - that is, find all B-records that is children of the series record and add a
                    // field 846 whith a link to the universe record.
                    currentChildWriter.addFieldSubfield("846", '5', "870979");
                    currentChildWriter.addOrReplaceSubField("846", '6', link);
                    createAction = true;
                } else if (currentReaderField != null && newField == null) {
                    // handle removing universe - that is, find all B-records that is children of the series record and remove
                    // field 846 from those records.
                    currentChildWriter.removeField("846");
                    createAction = true;
                } else if (currentReaderField != null) {
                    // Just for the record, newField is never null if we reach here
                    // handle change universe - that is, find all B-records that is children of the series record and replace
                    // the content of field 846 in those records. Technically, remove the 846 fields and add new.
                    currentChildWriter.removeField("846");
                    currentChildWriter.addFieldSubfield("846", '5', "870979");
                    currentChildWriter.addOrReplaceSubField("846", '6', link);
                    createAction = true;
                }
                if (createAction) {
                    final String parentId = currentChildReader.getParentRecordId();
                    if (parentId != null && !parentId.isEmpty()) {
                        children.add(new UpdateVolumeRecord(state, settings, currentChildRecord));
                    } else {
                        children.add(new OverwriteSingleRecordAction(state, settings, currentChildRecord));
                    }
                }
            }
        }
    }

    boolean authorityRecordHasClassificationChange(MarcRecord marcRecord) throws UpdateException, MarcRecordExpandException {
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);

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
        The function calls itself recursively until the hierarchy has been traversed
     */
    private void findChildrenAndHoldingsOnChildren(MarcRecord marcRecord, Set<Integer> librariesWithPosts) throws UpdateException {
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);
        final RecordId recordId = new RecordId(reader.getRecordId(), reader.getAgencyIdAsInt());

        LOGGER.use(log -> log.info("Getting children for {}", recordId));

        if (recordIsHeadOrSection(marcRecord)) {
            for (RecordId child : state.getRawRepo().children(recordId)) {
                LOGGER.use(log -> log.info("Found child record {}", child));
                MarcRecord childRecord = UpdateRecordContentTransformer.decodeRecord(rawRepo.fetchRecord(child.getBibliographicRecordId(), child.getAgencyId()).getContent());
                findChildrenAndHoldingsOnChildren(childRecord, librariesWithPosts);
            }
        } else {
            LOGGER.use(log -> log.info("Getting holdings and agencies for volume {}", recordId));
            librariesWithPosts.addAll(state.getAgenciesWithHoldings(marcRecord));
            librariesWithPosts.addAll(state.getRawRepo().agenciesForRecordNotDeleted(reader.getRecordId()));
        }
    }

    private boolean recordIsHeadOrSection(MarcRecord marcRecord) {
        return Arrays.asList("s", "h").contains(new MarcRecordReader(marcRecord).getValue("004", 'a'));
    }

    private void performActionDefault() throws UpdateException, MarcRecordExpandException {
        LOGGER.use(log -> log.info("Performing default action "));
        children.add(StoreRecordAction.newStoreMarcXChangeAction(state, settings, marcRecord));
        children.add(new RemoveLinksAction(state, marcRecord));

        MarcRecord currentExpandedRecord = loadCurrentRecord();
        MarcRecord newExpandedRecord = expandRecord();

        children.addAll(createActionsForCreateOrUpdateEnrichments(newExpandedRecord, currentExpandedRecord));
        children.add(new LinkAuthorityRecordsAction(state, marcRecord));
        children.add(EnqueueRecordAction.newEnqueueAction(state, marcRecord, settings));
        children.addAll(getEnqueuePHHoldingsRecordActions(state, marcRecord));
    }

    List<EnqueuePHHoldingsRecordAction> getEnqueuePHHoldingsRecordActions(GlobalActionState state, MarcRecord marcRecord) throws UpdateException {
        Set<Integer> holdingsLibraries = state.getAgenciesWithHoldings(marcRecord);
        Set<String> phLibraries = state.getPHLibraries();
        List<EnqueuePHHoldingsRecordAction> result = new ArrayList<>();

        /*
            Special handling of PH libraries with holdings
            In order to update records in danbib for PH libraries it is necessary to inform dataIO when a
            common record which a PH library has holding on is updated.

            Note that the record with recordId = record.recordId and agency = phLibrary.agencyId probably doesn't exist
            but that isn't important as rawrepo won't be modified. This only serves as a marker for dataIO to do something.
         */
        return LOGGER.call(log -> {
            for (Integer id : holdingsLibraries) {
                if (phLibraries.contains(id.toString())) {
                    log.info("Found PH library with holding! {}", id);
                    RecordId recordId = new RecordId(new MarcRecordReader(marcRecord).getRecordId(), id);
                    EnqueuePHHoldingsRecordAction enqueuePHHoldingsRecordAction = new EnqueuePHHoldingsRecordAction(state, settings, marcRecord, recordId);
                    result.add(enqueuePHHoldingsRecordAction);
                }
            }
            return result;
        });
    }

    MarcRecord loadCurrentRecord() throws UpdateException, MarcRecordExpandException {
        if (this.currentMarcRecord == null) {
            final MarcRecordReader reader = new MarcRecordReader(marcRecord);
            final String recordId = reader.getRecordId();
            final int agencyId = reader.getAgencyIdAsInt();
            if (RawRepo.AUTHORITY_AGENCY == agencyId) {
                final Record currentRecord = rawRepo.fetchRecord(recordId, agencyId);

                this.currentMarcRecord = UpdateRecordContentTransformer.decodeRecord(currentRecord.getContent());
            } else {
                final Map<String, MarcRecord> currentRecordCollection = rawRepo.fetchRecordCollection(recordId, agencyId);

                this.currentMarcRecord = state.getRecordSorter().sortRecord(ExpandCommonMarcRecord.expandMarcRecord(currentRecordCollection, recordId));
            }
        }

        return this.currentMarcRecord;
    }

    protected MarcRecord loadRecord(String recordId, int agencyId) throws UpdateException, UnsupportedEncodingException {
        final Record record = rawRepo.fetchRecord(recordId, agencyId);
        return UpdateRecordContentTransformer.decodeRecord(record.getContent());
    }

    MarcRecord expandRecord() throws UpdateException, MarcRecordExpandException {
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);
        final String recordId = reader.getRecordId();

        final Map<String, MarcRecord> newRecordCollection = new HashMap<>();
        newRecordCollection.put(recordId, marcRecord);

        for (DataField field : marcRecord.getFields(DataField.class)) {
            if (field.hasSubField(hasSubFieldCode('5')) && field.hasSubField(hasSubFieldCode('6'))) {
                final String autRecordId = field.getSubField(hasSubFieldCode('6')).orElseThrow().getData();

                final Record extRecord = rawRepo.fetchRecord(autRecordId, RawRepo.AUTHORITY_AGENCY);
                final MarcRecord autRecord = UpdateRecordContentTransformer.decodeRecord(extRecord.getContent());
                newRecordCollection.put(autRecordId, autRecord);
            }
        }

        return state.getRecordSorter().sortRecord(ExpandCommonMarcRecord.expandMarcRecord(newRecordCollection, recordId));
    }

    List<ServiceAction> createActionsForCreateOrUpdateEnrichments(MarcRecord marcRecord, MarcRecord currentRecord) throws UpdateException {
        return LOGGER.callChecked(log -> {
            final List<ServiceAction> result = new ArrayList<>();
            try {
                final MarcRecordReader reader = new MarcRecordReader(marcRecord);
                final String recordId = reader.getRecordId();
                final int agencyId = reader.getAgencyIdAsInt();
                final List<String> classificationMessages = new ArrayList<>();

                // The marcRecord object in the action only contains the common part of the record
                // But because the "minus enrichment" part is in the enrichment we have to look in the input record
                if (!hasMinusEnrichment(state.getMarcRecord()) && state.getLibraryRecordsHandler().hasClassificationData(currentRecord) &&
                        state.getLibraryRecordsHandler().hasClassificationData(marcRecord) &&
                        state.getLibraryRecordsHandler().hasClassificationsChanged(currentRecord, marcRecord, classificationMessages)) {
                    log.info("Classifications was changed for common record [{}:{}]", recordId, agencyId);

                    final Set<Integer> librariesWithPosts = new HashSet<>();
                    findChildrenAndHoldingsOnChildren(marcRecord, librariesWithPosts);

                    log.info("Found holdings or enrichments record for: {}", librariesWithPosts.toString());

                    for (int id : librariesWithPosts) {
                        if (!state.getVipCoreService().hasFeature(Integer.toString(id), VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)) {
                            continue;
                        }
                        if (rawRepo.recordExists(recordId, id)) {
                            Record extRecord = rawRepo.fetchRecord(recordId, id);
                            MarcRecord extRecordData = UpdateRecordContentTransformer.decodeRecord(extRecord.getContent());
                            log.info("Update classifications for extended library record: [{}:{}]", recordId, id);
                            result.add(getUpdateClassificationsInEnrichmentRecordActionData(extRecordData, marcRecord, currentRecord, Integer.toString(id)));
                        } else if (state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId().equals(Integer.toString(id))) {
                            log.info("Enrichment record is not created for record [{}:{}], because groupId equals agencyid", recordId, id);
                        } else {
                            if (DefaultEnrichmentRecordHandler.shouldCreateEnrichmentRecordsResult(state.getMessages(), marcRecord, currentRecord)) {
                                log.info("Create new enrichment library record: [{}:{}].", recordId, id);
                                result.add(getActionDataForEnrichmentWithClassification(marcRecord, currentRecord, Integer.toString(id), classificationMessages));
                            } else {
                                log.warn("Enrichment record {{}:{}} was not created, because none of the common records was published.", recordId, id);
                            }
                        }
                    }
                }
                return result;
            } catch (VipCoreException ex) {
                throw new UpdateException(ex.getMessage(), ex);
            }
        });
    }

    private UpdateClassificationsInEnrichmentRecordAction getUpdateClassificationsInEnrichmentRecordActionData(MarcRecord extRecordData, MarcRecord marcRecord, MarcRecord currentRecord, String id) {
        final UpdateClassificationsInEnrichmentRecordAction updateClassificationsInEnrichmentRecordAction =
                new UpdateClassificationsInEnrichmentRecordAction(state, settings, extRecordData, id);
        updateClassificationsInEnrichmentRecordAction.setCurrentCommonRecord(currentRecord);
        updateClassificationsInEnrichmentRecordAction.setUpdatingCommonRecord(marcRecord);
        return updateClassificationsInEnrichmentRecordAction;
    }

    private CreateEnrichmentRecordWithClassificationsAction getActionDataForEnrichmentWithClassification(
            MarcRecord marcRecord, MarcRecord currentRecord, String holdingAgencyId, List<String> classificationMessages) {
        final CreateEnrichmentRecordWithClassificationsAction createEnrichmentRecordWithClassificationsAction =
                new CreateEnrichmentRecordWithClassificationsAction(state, settings, holdingAgencyId);
        createEnrichmentRecordWithClassificationsAction.setCurrentCommonRecord(currentRecord);
        createEnrichmentRecordWithClassificationsAction.setUpdatingCommonRecord(marcRecord);
        createEnrichmentRecordWithClassificationsAction.setReclassificationMessages(classificationMessages);
        return createEnrichmentRecordWithClassificationsAction;
    }

}
