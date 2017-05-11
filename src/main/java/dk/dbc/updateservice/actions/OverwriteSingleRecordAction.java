/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.records.MarcRecordWriter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.openagency.client.OpenAgencyException;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.update.DefaultEnrichmentRecordHandler;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.RawRepoDecoder;
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
            logger.info("Handling record:\n{}", record);
            MarcRecord currentRecord = loadCurrentRecord();
            children.add(StoreRecordAction.newStoreAction(state, settings, record, MarcXChangeMimeType.MARCXCHANGE));
            children.add(new RemoveLinksAction(state, record));
            children.addAll(createActionsForCreateOrUpdateEnrichments(currentRecord));
            result = performActionsFor002Links();
            children.add(EnqueueRecordAction.newEnqueueAction(state, record, settings));

            Set<Integer> holdingsLibraries = state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record);
            Set<String> phLibraries = state.getPHLibraries();

            /*
                Special handling of PH libraries with holdings
                In order to update records in danbib for PH libraries it is necessary to inform dataIO when an
                common record which a PH library has holding on is updated.

                Note that the record with recordId = record.recordId and agency = phLibrary.agencyId probably doesn't exist
                 but that isn't important as rawrepo won't be modified. This only serves as a marker for dataIO to do
                 something.
             */
            for (Integer id : holdingsLibraries) {
                if (phLibraries.contains(id.toString())) {
                    logger.info("Found PH library with holding! {}", id);
                    RecordId recordId = new RecordId(new MarcRecordReader(record).recordId(), id);
                    EnqueuePHHoldingsRecordAction enqueuePHHoldingsRecordAction = new EnqueuePHHoldingsRecordAction(state, settings, record, recordId);
                    children.add(enqueuePHHoldingsRecordAction);
                }
            }

            return result;
        } catch (ScripterException | UnsupportedEncodingException ex) {
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, ex.getMessage(), state);
        } finally {
            logger.exit(result);
        }
    }

    MarcRecord loadCurrentRecord() throws UpdateException, UnsupportedEncodingException {
        logger.entry();
        MarcRecord result = null;
        try {
            MarcRecordReader reader = new MarcRecordReader(record);
            String recordId = reader.recordId();
            Integer agencyId = reader.agencyIdAsInteger();

            Record record = rawRepo.fetchRecord(recordId, agencyId);
            return result = new RawRepoDecoder().decodeRecord(record.getContent());
        } finally {
            logger.exit(result);
        }
    }

    protected MarcRecord loadRecord(String recordId, Integer agencyId) throws UpdateException, UnsupportedEncodingException {
        logger.entry();
        MarcRecord result = null;
        try {
            Record record = rawRepo.fetchRecord(recordId, agencyId);
            return result = new RawRepoDecoder().decodeRecord(record.getContent());
        } finally {
            logger.exit(result);
        }
    }

    List<ServiceAction> createActionsForCreateOrUpdateEnrichments(MarcRecord currentRecord) throws ScripterException, UpdateException, UnsupportedEncodingException {
        logger.entry(currentRecord);
        List<ServiceAction> result = new ArrayList<>();
        try {
            MarcRecordReader reader = new MarcRecordReader(record);
            String recordId = reader.recordId();
            Integer agencyId = reader.agencyIdAsInteger();

            if (state.getLibraryRecordsHandler().hasClassificationData(currentRecord) && state.getLibraryRecordsHandler().hasClassificationData(record)) {
                if (state.getLibraryRecordsHandler().hasClassificationsChanged(currentRecord, record)) {
                    logger.info("Classifications was changed for common record [{}:{}]", recordId, agencyId);
                    Set<Integer> holdingsLibraries = state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record);
                    Set<Integer> enrichmentLibraries = state.getRawRepo().agenciesForRecord(record);

                    Set<Integer> librariesWithPosts = new HashSet<>();
                    librariesWithPosts.addAll(holdingsLibraries);
                    librariesWithPosts.addAll(enrichmentLibraries);

                    logger.info("Found holdings or enrichments record for: {}", holdingsLibraries.toString());

                    RawRepoDecoder decoder = new RawRepoDecoder();
                    for (Integer id : librariesWithPosts) {
                        logger.info("Local library for record: {}", id);

                        if (!state.getOpenAgencyService().hasFeature(id.toString(), LibraryRuleHandler.Rule.USE_ENRICHMENTS)) {
                            continue;
                        }
                        if (rawRepo.recordExists(recordId, id)) {
                            Record extRecord = rawRepo.fetchRecord(recordId, id);
                            MarcRecord extRecordData = decoder.decodeRecord(extRecord.getContent());
                            logger.info("Update classifications for extended library record: [{}:{}]", recordId, id);
                            result.add(getUpdateClassificationsInEnrichmentRecordActionData(extRecordData, currentRecord, id.toString()));
                        } else if (state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId().equals(id.toString())) {
                            logger.info("Enrichment record is not created for record [{}:{}], because groupId equals agencyid", recordId, id);
                        } else {
                            if (DefaultEnrichmentRecordHandler.shouldCreateEnrichmentRecordsResult(state.getMessages(), record, currentRecord)) {
                                logger.info("Create new extended library record: [{}:{}].", recordId, id);
                                result.add(getActionDataForEnrichmentWithClassification(currentRecord, id.toString()));
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

    private CreateEnrichmentRecordWithClassificationsAction getUpdateClassificationsInEnrichmentRecordActionData(MarcRecord extRecordData, MarcRecord currentRecord, String id) {
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

    private CreateEnrichmentRecordWithClassificationsAction getActionDataForEnrichmentRecord(String holdingAgencyId, String destinationCommonRecordId, MarcRecord linkRecord) {
        logger.entry(holdingAgencyId, destinationCommonRecordId, linkRecord);
        CreateEnrichmentRecordWithClassificationsAction createEnrichmentRecordWithClassificationsAction = null;
        try {
            createEnrichmentRecordWithClassificationsAction = new CreateEnrichmentRecordWithClassificationsAction(state, settings, holdingAgencyId);
            createEnrichmentRecordWithClassificationsAction.setUpdatingCommonRecord(linkRecord);
            createEnrichmentRecordWithClassificationsAction.setCurrentCommonRecord(linkRecord);
            createEnrichmentRecordWithClassificationsAction.setCommonRecordId(destinationCommonRecordId);
            return createEnrichmentRecordWithClassificationsAction;
        } finally {
            logger.exit(createEnrichmentRecordWithClassificationsAction);
        }
    }

    private CreateEnrichmentRecordWithClassificationsAction getActionDataForEnrichmentWithClassification(MarcRecord currentRecord, String holdingAgencyId) {
        logger.entry(holdingAgencyId, currentRecord);
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

    private MoveEnrichmentRecordAction getMoveEnrichmentRecordAction(MarcRecord enrichmentRecordData, boolean classificationsChanged, boolean oneOrBothInProduction) {
        logger.entry(enrichmentRecordData);
        MoveEnrichmentRecordAction moveEnrichmentRecordAction = null;
        try {
            moveEnrichmentRecordAction = new MoveEnrichmentRecordAction(state, settings, enrichmentRecordData, classificationsChanged, oneOrBothInProduction);
            moveEnrichmentRecordAction.setCommonRecord(record);
            return moveEnrichmentRecordAction;
        } finally {
            logger.exit(moveEnrichmentRecordAction);
        }
    }

    private CreateEnrichmentRecordActionForlinkedRecords getActionDataForEnrichmentRecord(MarcRecord currentRecord, Integer holdingAgencyId, List<MarcRecord> arrayOfRecordsWithHoldings) {
        logger.entry(holdingAgencyId, currentRecord);
        CreateEnrichmentRecordActionForlinkedRecords createEnrichmentRecordActionForlinkedRecords = null;
        try {
            createEnrichmentRecordActionForlinkedRecords = new CreateEnrichmentRecordActionForlinkedRecords(state, settings);
            createEnrichmentRecordActionForlinkedRecords.setAgencyId(holdingAgencyId);
            createEnrichmentRecordActionForlinkedRecords.setListOfRecordsToFetchClassificationDataFrom(arrayOfRecordsWithHoldings);
            createEnrichmentRecordActionForlinkedRecords.setCurrentCommonRecord(currentRecord);
            createEnrichmentRecordActionForlinkedRecords.setUpdatingCommonRecord(record);
            return createEnrichmentRecordActionForlinkedRecords;
        } finally {
            logger.exit(createEnrichmentRecordActionForlinkedRecords);
        }
    }

    private List<CreateEnrichmentRecordActionForlinkedRecords> linkForMultipleRecordsIn002(HashMap<String, List<MarcRecord>> enrichmentCandidate) {
        logger.entry();
        List<CreateEnrichmentRecordActionForlinkedRecords> createEnrichmentRecordActionForlinkedRecordsList = new ArrayList<>();
        try {
            enrichmentCandidate.forEach((agencyIdString, arrayOfRecordsWithHoldings) -> {
                if (arrayOfRecordsWithHoldings.size() > 1) {
                    createEnrichmentRecordActionForlinkedRecordsList.add(getActionDataForEnrichmentRecord(record, Integer.parseInt(agencyIdString), arrayOfRecordsWithHoldings));
                    for (MarcRecord rec : arrayOfRecordsWithHoldings) {
                        MarcRecordWriter curWriter = new MarcRecordWriter(rec);
                        curWriter.markForDeletion();
                    }
                }
            });
            if (createEnrichmentRecordActionForlinkedRecordsList.size() > 0) {
                MarcRecordWriter writer = new MarcRecordWriter(record);
                writer.removeSubfield("002", "a");
            }
            return createEnrichmentRecordActionForlinkedRecordsList;
        } finally {
            logger.exit();
        }
    }

    ServiceResult performActionsFor002Links() throws ScripterException, UpdateException, UnsupportedEncodingException {
        logger.entry();
        ServiceResult result = ServiceResult.newOkResult();
        try {
            MarcRecordReader recordReader = new MarcRecordReader(record);
            String destinationCommonRecordId = recordReader.getValue("001", "a");
            Integer agencyId = Integer.valueOf(recordReader.getValue("001", "b"));

            MarcRecord currentRecord = loadRecord(destinationCommonRecordId, agencyId);
            MarcRecordReader currentRecordReader = new MarcRecordReader(currentRecord);

            List<String> valuesFrom002 = recordReader.getValues("002", "a");
            // Check made due to story #1802, regarding multiple links in one record
            boolean isMultiple002Candidate = valuesFrom002.size() > 1;

            HashMap<String, List<MarcRecord>> enrichmentCandidate = new HashMap<>();

            boolean isTargetRecordInProduction = state.getLibraryRecordsHandler().isRecordInProduction(record);
            logger.info("IsTargetRecordInProduction {}", isTargetRecordInProduction);
            for (String recordId : valuesFrom002) {
                if (currentRecordReader.hasValue("002", "a", recordId)) {
                    logger.info("002 linked record '{}' is not changed, so it is not handled.", recordId);
                    continue;
                }

                if (!rawRepo.recordExists(recordId, RawRepo.COMMON_AGENCY)) {
                    logger.warn("002 linked record '{}' does not exist", recordId);
                    continue;
                }
                MarcRecord linkRecord = loadRecord(recordId, agencyId);
                boolean classificationsChanged = state.getLibraryRecordsHandler().hasClassificationsChanged(linkRecord, record);
                boolean isLinkRecInProduction = state.getLibraryRecordsHandler().isRecordInProduction(linkRecord);
                // The real boolean wanted is :
                // (!isLinkRecInProduction && isTargetRecordInProduction) ¦¦ (isLinkRecInProduction && !isTargetRecordInProduction) or (!isLinkRecInProduction && !isTargetRecordInProduction)
                // Fortunatedly it can be reduced to :
                boolean isOneOrBothInProduction = !(isTargetRecordInProduction && isLinkRecInProduction);
                logger.info("Is linkrec InProduction {}", isLinkRecInProduction);
                logger.info("IsOneOrBothInProduction {}", isOneOrBothInProduction);
                logger.info("Will classifications change from {{}:{}} to record '{{}:{}}': {}", recordId, agencyId, destinationCommonRecordId, agencyId, classificationsChanged);

                if (classificationsChanged) {
                    Set<Integer> holdingAgencies = state.getHoldingsItems().getAgenciesThatHasHoldingsForId(recordId);
                    Set<RecordId> enrichmentIds = rawRepo.enrichments(new RecordId(recordId, RawRepo.COMMON_AGENCY));

                    if (holdingAgencies.isEmpty()) {
                        logger.info("No holdings found for record id '{}'", recordId);
                    }

                    for (Integer holdingAgencyId : holdingAgencies) {
                        if (!state.getOpenAgencyService().hasFeature(holdingAgencyId.toString(), LibraryRuleHandler.Rule.USE_ENRICHMENTS)) {
                            logger.info("Ignoring holdings for agency '{}', because they do not have the feature '{}'", holdingAgencyId, LibraryRuleHandler.Rule.USE_ENRICHMENTS);
                            continue;
                        }
                        if (!enrichmentIds.contains(new RecordId(recordId, holdingAgencyId))) {
                            logger.warn("No enrichments found for record '{}' for agency '{}' with holdings", recordId, holdingAgencyId);
                            String holdingAgencyIdString = holdingAgencyId.toString();
                            if (isMultiple002Candidate) {
                                if (enrichmentCandidate.containsKey(holdingAgencyIdString)) {
                                    enrichmentCandidate.get(holdingAgencyIdString).add(linkRecord);
                                } else {
                                    List<MarcRecord> marcRecords = new ArrayList<>();
                                    marcRecords.add(linkRecord);
                                    enrichmentCandidate.put(holdingAgencyIdString, marcRecords);
                                }
                            }
                            if (DefaultEnrichmentRecordHandler.shouldCreateEnrichmentRecordsResult(state.getMessages(), linkRecord, currentRecord)) {
                                children.add(getActionDataForEnrichmentRecord(holdingAgencyIdString, destinationCommonRecordId, linkRecord));
                            } else {
                                logger.warn("Enrichment record {{}:{}} was not created, because none of the common records was published.", recordId, holdingAgencyId);
                            }
                        }
                    }
                } else {
                    logger.info("Holdings for linked record '{}' was not checked, because the classifications has not changed.", recordId);
                }
                Set<RecordId> enrichmentIds = rawRepo.enrichments(new RecordId(recordId, RawRepo.COMMON_AGENCY));

                for (RecordId enrichmentId : enrichmentIds) {
                    if (enrichmentId.getAgencyId() == RawRepo.DBC_ENRICHMENT) {
                        continue;
                    }
                    if (!state.getOpenAgencyService().hasFeature(String.valueOf(enrichmentId.getAgencyId()), LibraryRuleHandler.Rule.USE_ENRICHMENTS)) {
                        logger.info("Ignoring enrichment record for agency '{}', because they do not have the feature '{}'", enrichmentId.getAgencyId(), LibraryRuleHandler.Rule.USE_ENRICHMENTS);
                        continue;
                    }
                    Record enrichmentRecord = rawRepo.fetchRecord(enrichmentId.getBibliographicRecordId(), enrichmentId.getAgencyId());
                    MarcRecord enrichmentRecordData = new RawRepoDecoder().decodeRecord(enrichmentRecord.getContent());
                    children.add(getMoveEnrichmentRecordAction(enrichmentRecordData, classificationsChanged, isOneOrBothInProduction));
                }
            }
            children.addAll(linkForMultipleRecordsIn002(enrichmentCandidate));
            return result;
        } catch (OpenAgencyException ex) {
            throw new UpdateException(ex.getMessage(), ex);
        } finally {
            logger.exit(result);
        }
    }
}
