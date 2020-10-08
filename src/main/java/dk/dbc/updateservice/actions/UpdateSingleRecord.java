/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.utils.LogUtils;
import dk.dbc.common.records.utils.RecordContentTransformer;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.openagency.client.OpenAgencyException;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.SolrException;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Action to create, overwrite or delete a single record.
 */
public class UpdateSingleRecord extends AbstractRawRepoAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(UpdateSingleRecord.class);

    protected Properties settings;

    public UpdateSingleRecord(GlobalActionState globalActionState, Properties properties, MarcRecord record) {
        super(UpdateSingleRecord.class.getSimpleName(), globalActionState, record);
        settings = properties;
    }

    /**
     * Performs this actions and may create any child actions.
     *
     * @return A ServiceResult to be reported in the web service response.
     * @throws UpdateException In case of an error.
     */
    @Override
    public ServiceResult performAction() throws UpdateException, SolrException {
        logger.entry();
        try {
            logger.info("Handling record: {}", LogUtils.base64Encode(record));
            MarcRecordReader reader = new MarcRecordReader(record);
            String recordId = reader.getRecordId();
            int agencyId = reader.getAgencyIdAsInt();

            if (!rawRepo.recordExists(recordId, agencyId)) {
                children.add(createCreateRecordAction());
                return ServiceResult.newOkResult();
            }

            // Check for change from head or section to single
            // Changing type from head/section to single is only allowed if the record doesn't have any common record children
            // 004 *a e = single record
            if (RawRepo.COMMON_AGENCY == reader.getAgencyIdAsInt() && reader.hasValue("004", "a", "e")) {
                final MarcRecord existingRecord = RecordContentTransformer.decodeRecord(rawRepo.fetchRecord(reader.getRecordId(), reader.getAgencyIdAsInt()).getContent());
                final MarcRecordReader existingReader = new MarcRecordReader(existingRecord);

                // 004 *a h = head record
                // 004 *a s = section record
                if (existingReader.hasValue("004", "a", "h") || existingReader.hasValue("004", "a", "s")) {
                    Set<RecordId> children = state.getRawRepo().children(record);
                    for (RecordId childId : children) {
                        // 870971 records are okay as children but a 870970 means it is in volume hierarchy
                        if (RawRepo.COMMON_AGENCY == childId.getAgencyId()) {
                            String message = String.format(state.getMessages().getString("head.or.section.to.single.children"), recordId, agencyId);

                            logger.info("Record can't be changed from head or section record single record. Returning error: {}", message);
                            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
                        }
                    }
                }
            }

            if (reader.markedForDeletion()) {
                // If it is deletion and a 870970 record then the group is always 010100
                // Which means we are only interested in the other libraries with holdings
                Set<Integer> agenciesWithHoldings = state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record);
                if (RawRepo.COMMON_AGENCY == reader.getAgencyIdAsInt() && !agenciesWithHoldings.isEmpty()) {
                    for (Integer agencyWithHoldings : agenciesWithHoldings) {
                        logger.info("Found holdings for agency '{}'", agencyWithHoldings);
                        boolean hasAuthExportHoldings = state.getOpenAgencyService().hasFeature(agencyWithHoldings.toString(), LibraryRuleHandler.Rule.AUTH_EXPORT_HOLDINGS);
                        if (hasAuthExportHoldings) {
                            logger.info("Agency '{}' has feature '{}'", agencyWithHoldings, LibraryRuleHandler.Rule.AUTH_EXPORT_HOLDINGS);
                            String solrQuery = SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId);
                            boolean has002Links = state.getSolrFBS().hasDocuments(solrQuery);
                            if (!has002Links) {
                                String message = String.format(state.getMessages().getString("delete.common.with.holdings.error"), recordId, agencyId, agencyWithHoldings);

                                logger.info("Record '{}:{}' has no 002 links. Returning error: {}", recordId, reader.getAgencyId(), message);
                                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
                            }
                        } else {
                            logger.info("Agency '{}' does not have feature '{}'. Accepting deletion.", agencyWithHoldings, LibraryRuleHandler.Rule.AUTH_EXPORT_HOLDINGS);
                        }
                    }
                }
                performActionsFor002Links();
                children.add(createDeleteRecordAction());
                return ServiceResult.newOkResult();
            }
            children.add(createOverwriteRecordAction());
            return ServiceResult.newOkResult();
        } catch (UnsupportedEncodingException ex) {
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, ex.getMessage());
        } catch (OpenAgencyException e) {
            throw new UpdateException(e.getMessage(), e);
        } finally {
            logger.exit();
        }
    }

    /**
     * Factory method to construct the ServiceAction to create a new record.
     */
    protected ServiceAction createCreateRecordAction() {
        return new CreateSingleRecordAction(state, settings, record);
    }

    /**
     * Factory method to construct the ServiceAction to overwrite an existing record.
     */
    protected ServiceAction createOverwriteRecordAction() {
        return new OverwriteSingleRecordAction(state, settings, record);
    }

    /**
     * Factory method to construct the ServiceAction to delete a record.
     */
    private ServiceAction createDeleteRecordAction() {
        return new DeleteCommonRecordAction(state, settings, record);
    }


    protected MarcRecord loadRecord(String recordId, Integer agencyId) throws UpdateException, UnsupportedEncodingException {
        logger.entry(recordId, agencyId);
        MarcRecord result = null;
        try {
            Record record = rawRepo.fetchRecord(recordId, agencyId);
            return result = RecordContentTransformer.decodeRecord(record.getContent());
        } finally {
            logger.exit(result);
        }
    }

    private CreateEnrichmentRecordWithClassificationsAction createJobForAddingEnrichmentRecord(String holdingAgencyId, String destinationCommonRecordId, MarcRecord linkRecord) {
        logger.entry(holdingAgencyId, destinationCommonRecordId, linkRecord);
        CreateEnrichmentRecordWithClassificationsAction createEnrichmentRecordWithClassificationsAction = null;
        try {
            logger.info("Create CreateEnrichmentRecordWithClassificationsAction for : {}:{}", holdingAgencyId, destinationCommonRecordId);
            createEnrichmentRecordWithClassificationsAction = new CreateEnrichmentRecordWithClassificationsAction(state, settings, holdingAgencyId);
            createEnrichmentRecordWithClassificationsAction.setUpdatingCommonRecord(linkRecord);
            createEnrichmentRecordWithClassificationsAction.setCurrentCommonRecord(linkRecord);
            createEnrichmentRecordWithClassificationsAction.setTargetRecordId(destinationCommonRecordId);
            return createEnrichmentRecordWithClassificationsAction;
        } finally {
            logger.exit(createEnrichmentRecordWithClassificationsAction);
        }
    }

    private MoveEnrichmentRecordAction getMoveEnrichmentRecordAction(String newRecordId, MarcRecord enrichmentRecordData, boolean classificationsChanged, boolean oneOrBothInProduction) {
        logger.entry(newRecordId, enrichmentRecordData);
        MoveEnrichmentRecordAction moveEnrichmentRecordAction = null;
        try {
            logger.info("Creating MoveEnrichmentRecordAction for record {} ", newRecordId);
            moveEnrichmentRecordAction = new MoveEnrichmentRecordAction(state, settings, enrichmentRecordData, classificationsChanged, oneOrBothInProduction);
            moveEnrichmentRecordAction.setTargetRecordId(newRecordId);
            return moveEnrichmentRecordAction;
        } finally {
            logger.exit(moveEnrichmentRecordAction);
        }
    }

    private CreateEnrichmentRecordActionForlinkedRecords getActionForCreateActionForLinkedRecords(MarcRecord record, Integer holdingAgencyId, MarcRecord recordWithHolding) {
        logger.entry(holdingAgencyId, record);
        CreateEnrichmentRecordActionForlinkedRecords createEnrichmentRecordActionForlinkedRecords = null;
        try {
            createEnrichmentRecordActionForlinkedRecords = new CreateEnrichmentRecordActionForlinkedRecords(state, settings);
            createEnrichmentRecordActionForlinkedRecords.setAgencyId(holdingAgencyId);
            createEnrichmentRecordActionForlinkedRecords.setRecordWithHoldings(recordWithHolding);
            createEnrichmentRecordActionForlinkedRecords.setRecord(record);
            return createEnrichmentRecordActionForlinkedRecords;
        } finally {
            logger.exit(createEnrichmentRecordActionForlinkedRecords);
        }
    }

    /*
    Uden opstillingsændring, ingen bestand, ingen påhæng, ingen lokalopstil : NOP, der skal ikke ske noget - DONE
    Uden opstillingsændring, ingen bestand, ingen påhæng, med lokalopstil : Umulig - kræver en påhæng -NOP
    Uden opstillingsændring, ingen bestand, med påhæng, ingen lokalopstil : påhæng flyttes - DONE
    Uden opstillingsændring, ingen bestand, med påhæng, med lokalopstil : påhæng flyttes - DONE
    --
    Uden opstillingsændring, med bestand, ingen påhæng, ingen lokalopstil : NOP, der skal ikke ske noget - DONE
    Uden opstillingsændring, med bestand, ingen påhæng, med lokalopstil : umulig - kræver påhæng - NOP
    Uden opstillingsændring, med bestand, med påhæng, uden lokalopstil : påhæng flyttes - DONE
    Uden opstillingsændring, med bestand, med påhæng, med lokalopstil : påhæng flyttes - DONE
    ______
    Med opstillingsændring, ingen bestand, uden påhæng, uden lokalopstil : NOP, der skal ikke ske noget - DONE
    Med opstillingsændring, ingen bestand, uden påhæng, med lokalopstil : umulig - kræver påhæng - NOP
    Med opstillingsændring, ingen bestand, med påhæng, uden lokalopstil :
            afhænger af produktion :
                    hvis moder record er i prod -> flyt uden y08aOpstillingsændring; -DONE
                    hvis moder record ikke er i prod -> flyt og tilføj y08aOpstillingsændring - DONE
    Med opstillingsændring, ingen bestand, med påhæng, med lokalopstil : påhæng flyttes - DONE
    --
    Med opstillingsændring, med bestand, ingen påhæng, uden lokalopstil : opret påhæng med y08 - DONE
            afhænger af produktion :
                    hvis moder record er i prod -> ingenting - DONE
                    hvis moder record ikke er i prod -> opret påhæng med opstil og tilføj flyttemeddelelse - DONE
    Med opstillingsændring, med bestand, ingen påhæng, med lokalopstil : umulig - kræver påhæng - NOP
    Med opstillingsændring, med bestand, med påhæng, uden lokalopstil :
            afhænger af produktion :
                    hvis moder record er i prod -> flyt uden y08aOpstillingsændring; -DONE
                    hvis moder record ikke er i prod -> flyt og tilføj y08aOpstillingsændring - DONE
    Med opstillingsændring, med bestand, med påhæng, med lokalopstil : påhæng flyttes - DONE

    Produktion : Hvis 032*a/x har en kode ude i fremtiden så er posten under produktion - samme hvis den indeholder 999999
     */
    private void performActionsFor002Links() throws UpdateException, SolrException, UnsupportedEncodingException, OpenAgencyException {
        logger.entry("performActionsFor002Links");
        try {
            MarcRecordReader recordReader = new MarcRecordReader(record);
            String recordIdForRecordToDelete = recordReader.getValue("001", "a");
            Integer agencyIdForRecordToDelete = Integer.valueOf(recordReader.getValue("001", "b"));

            String motherRecordId = state.getSolrFBS().getOwnerOf002(SolrServiceIndexer.createGetOwnerOf002QueryDBCOnly("002a", recordIdForRecordToDelete));
            if (motherRecordId.equals("")) {
                return;
            }
            logger.info("Record : {} has {} as 002 field", motherRecordId, recordIdForRecordToDelete);
            MarcRecord motherRecord;

            if (rawRepo.recordExists(motherRecordId, agencyIdForRecordToDelete)) {
                motherRecord = loadRecord(motherRecordId, agencyIdForRecordToDelete);
            } else {
                logger.warn("Solr index 002a points to a nonexisting record : {}:{}", agencyIdForRecordToDelete, motherRecordId);
                return;
            }
            MarcRecord rrVersionOfRecordToDelete = loadRecord(recordIdForRecordToDelete, agencyIdForRecordToDelete);
            logger.info("Holdings for " + recordIdForRecordToDelete);
            Set<Integer> holdingAgencies = state.getHoldingsItems().getAgenciesThatHasHoldingsForId(recordIdForRecordToDelete);
            logger.info("is " + holdingAgencies.toString());
            // check classification - if changed it will require modification of enrichment record - due to story #1802 messages must be merged into eventual existing enrichment
            boolean classificationsChanged = state.getLibraryRecordsHandler().hasClassificationsChanged(motherRecord, rrVersionOfRecordToDelete);
            logger.info("classificationsChanged : {}", classificationsChanged);
            logger.info("Enrichments for {}", recordIdForRecordToDelete);

            Set<RecordId> enrichmentIds = rawRepo.enrichments(new RecordId(recordIdForRecordToDelete, RawRepo.COMMON_AGENCY));
            enrichmentIds.remove(new RecordId(recordIdForRecordToDelete, RawRepo.DBC_ENRICHMENT)); // No reason to fiddle with this in th main loop
            logger.info("is " + enrichmentIds.toString());
            Set<Integer> totalAgencies = new HashSet<>(holdingAgencies);
            for (RecordId enrichmentId : enrichmentIds) {
                totalAgencies.add(enrichmentId.getAgencyId());
            }

            boolean isLinkRecInProduction = state.getLibraryRecordsHandler().isRecordInProduction(motherRecord);
            logger.info("Record in production {}-{} : {} ", agencyIdForRecordToDelete, motherRecordId, isLinkRecInProduction);

            for (Integer workAgencyId : totalAgencies) {
                if (!state.getOpenAgencyService().hasFeature(workAgencyId.toString(), LibraryRuleHandler.Rule.USE_ENRICHMENTS)) {
                    // Why this check ? 002 linking only works for agencies that uses enrichments - LJL says it can be PH/SBCI that makes it necessary - doesn't hurt so kept for now
                    logger.info("Ignoring holdings for agency '{}', because they do not have the feature '{}'", workAgencyId, LibraryRuleHandler.Rule.USE_ENRICHMENTS);
                    continue;
                }
                boolean hasEnrichment = enrichmentIds.contains(new RecordId(recordIdForRecordToDelete, workAgencyId));
                logger.info("Agency {} has enrichment : {}", workAgencyId, hasEnrichment);
                if (hasEnrichment) {
                    Record enrichmentRecord = rawRepo.fetchRecord(recordIdForRecordToDelete, workAgencyId);
                    MarcRecord enrichmentRecordData = RecordContentTransformer.decodeRecord(enrichmentRecord.getContent());
                    children.add(getMoveEnrichmentRecordAction(motherRecordId, enrichmentRecordData, classificationsChanged, isLinkRecInProduction));
                } else {
                    if (classificationsChanged && holdingAgencies.contains(workAgencyId) && !isLinkRecInProduction) {
                        children.add(createJobForAddingEnrichmentRecord(workAgencyId.toString(), motherRecordId, rrVersionOfRecordToDelete));
                        children.add(getActionForCreateActionForLinkedRecords(motherRecord, workAgencyId, rrVersionOfRecordToDelete));
                    }
                }
            }
        } catch (Throwable e) {
            logger.info("performActionsFor002Links fails with : {}", e.toString());
            throw e;
        }
    }

}
