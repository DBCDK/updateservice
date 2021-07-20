/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.utils.LogUtils;
import dk.dbc.common.records.utils.RecordContentTransformer;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.SolrException;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.vipcore.exception.VipCoreException;
import dk.dbc.vipcore.libraryrules.VipCoreLibraryRulesConnector;
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
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(UpdateSingleRecord.class);

    protected Properties settings;

    public UpdateSingleRecord(GlobalActionState globalActionState, Properties properties, MarcRecord marcRecord) {
        super(UpdateSingleRecord.class.getSimpleName(), globalActionState, marcRecord);
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
        try {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Handling record: {}", LogUtils.base64Encode(marcRecord));
            }
            final MarcRecordReader reader = new MarcRecordReader(marcRecord);
            final String recordId = reader.getRecordId();
            final int agencyId = reader.getAgencyIdAsInt();

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
                    final Set<RecordId> children = state.getRawRepo().children(marcRecord);
                    for (RecordId childId : children) {
                        // 870971 records are okay as children but a 870970 means it is in volume hierarchy
                        if (RawRepo.COMMON_AGENCY == childId.getAgencyId()) {
                            final String message = String.format(state.getMessages().getString("head.or.section.to.single.children"), recordId, agencyId);

                            LOGGER.info("Record can't be changed from head or section record single record. Returning error: {}", message);
                            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
                        }
                    }
                }
            }

            if (reader.markedForDeletion()) {
                // If it is deletion and a 870970 record then the group is always 010100
                // Which means we are only interested in the other libraries with holdings
                final Set<Integer> agenciesWithHoldings = state.getHoldingsItems().getAgenciesThatHasHoldingsFor(marcRecord);
                if (RawRepo.COMMON_AGENCY == reader.getAgencyIdAsInt() && !agenciesWithHoldings.isEmpty()) {
                    for (Integer agencyWithHoldings : agenciesWithHoldings) {
                        LOGGER.info("Found holdings for agency '{}'", agencyWithHoldings);
                        final boolean hasAuthExportHoldings = state.getVipCoreService().hasFeature(agencyWithHoldings.toString(), VipCoreLibraryRulesConnector.Rule.AUTH_EXPORT_HOLDINGS);
                        if (hasAuthExportHoldings) {
                            LOGGER.info("Agency '{}' has feature '{}'", agencyWithHoldings, VipCoreLibraryRulesConnector.Rule.AUTH_EXPORT_HOLDINGS);
                            final String solrQuery = SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId);
                            final boolean has002Links = state.getSolrFBS().hasDocuments(solrQuery);
                            if (!has002Links) {
                                final String message = String.format(state.getMessages().getString("delete.common.with.holdings.error"), recordId, agencyId, agencyWithHoldings);

                                LOGGER.info("Record '{}:{}' has no 002 links. Returning error: {}", recordId, reader.getAgencyId(), message);
                                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
                            }
                        } else {
                            LOGGER.info("Agency '{}' does not have feature '{}'. Accepting deletion.", agencyWithHoldings, VipCoreLibraryRulesConnector.Rule.AUTH_EXPORT_HOLDINGS);
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
        } catch (VipCoreException e) {
            throw new UpdateException(e.getMessage(), e);
        }
    }

    /**
     * Factory method to construct the ServiceAction to create a new record.
     */
    protected ServiceAction createCreateRecordAction() {
        return new CreateSingleRecordAction(state, settings, marcRecord);
    }

    /**
     * Factory method to construct the ServiceAction to overwrite an existing record.
     */
    protected ServiceAction createOverwriteRecordAction() {
        return new OverwriteSingleRecordAction(state, settings, marcRecord);
    }

    /**
     * Factory method to construct the ServiceAction to delete a record.
     */
    private ServiceAction createDeleteRecordAction() {
        return new DeleteCommonRecordAction(state, settings, marcRecord);
    }


    protected MarcRecord loadRecord(String recordId, Integer agencyId) throws UpdateException, UnsupportedEncodingException {
        Record record = rawRepo.fetchRecord(recordId, agencyId);
        return RecordContentTransformer.decodeRecord(record.getContent());
    }

    private CreateEnrichmentRecordWithClassificationsAction createJobForAddingEnrichmentRecord(String holdingAgencyId, String destinationCommonRecordId, MarcRecord linkRecord) {
        LOGGER.info("Create CreateEnrichmentRecordWithClassificationsAction for : {}:{}", holdingAgencyId, destinationCommonRecordId);
        final CreateEnrichmentRecordWithClassificationsAction createEnrichmentRecordWithClassificationsAction = new CreateEnrichmentRecordWithClassificationsAction(state, settings, holdingAgencyId);
        createEnrichmentRecordWithClassificationsAction.setUpdatingCommonRecord(linkRecord);
        createEnrichmentRecordWithClassificationsAction.setCurrentCommonRecord(linkRecord);
        createEnrichmentRecordWithClassificationsAction.setTargetRecordId(destinationCommonRecordId);

        return createEnrichmentRecordWithClassificationsAction;
    }

    private MoveEnrichmentRecordAction getMoveEnrichmentRecordAction(String newRecordId, MarcRecord enrichmentRecordData, boolean classificationsChanged, boolean oneOrBothInProduction) {
        LOGGER.info("Creating MoveEnrichmentRecordAction for record {} ", newRecordId);
        final MoveEnrichmentRecordAction moveEnrichmentRecordAction = new MoveEnrichmentRecordAction(state, settings, enrichmentRecordData, classificationsChanged, oneOrBothInProduction);
        moveEnrichmentRecordAction.setTargetRecordId(newRecordId);

        return moveEnrichmentRecordAction;
    }

    private CreateEnrichmentRecordActionForlinkedRecords getActionForCreateActionForLinkedRecords(MarcRecord marcRecord, Integer holdingAgencyId, MarcRecord recordWithHolding) {
        final CreateEnrichmentRecordActionForlinkedRecords createEnrichmentRecordActionForlinkedRecords = new CreateEnrichmentRecordActionForlinkedRecords(state, settings);
        createEnrichmentRecordActionForlinkedRecords.setAgencyId(holdingAgencyId);
        createEnrichmentRecordActionForlinkedRecords.setRecordWithHoldings(recordWithHolding);
        createEnrichmentRecordActionForlinkedRecords.setMarcRecord(marcRecord);

        return createEnrichmentRecordActionForlinkedRecords;
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
    private void performActionsFor002Links() throws UpdateException, SolrException, UnsupportedEncodingException, VipCoreException {
        try {
            final MarcRecordReader recordReader = new MarcRecordReader(marcRecord);
            final String recordIdForRecordToDelete = recordReader.getValue("001", "a");
            final Integer agencyIdForRecordToDelete = Integer.valueOf(recordReader.getValue("001", "b"));

            final String motherRecordId = state.getSolrFBS().getOwnerOf002(SolrServiceIndexer.createGetOwnerOf002QueryDBCOnly("002a", recordIdForRecordToDelete));
            if (motherRecordId.equals("")) {
                return;
            }
            LOGGER.info("Record : {} has {} as 002 field", motherRecordId, recordIdForRecordToDelete);
            MarcRecord motherRecord;

            if (rawRepo.recordExists(motherRecordId, agencyIdForRecordToDelete)) {
                motherRecord = loadRecord(motherRecordId, agencyIdForRecordToDelete);
            } else {
                LOGGER.warn("Solr index 002a points to a nonexisting record : {}:{}", agencyIdForRecordToDelete, motherRecordId);
                return;
            }
            final MarcRecord rrVersionOfRecordToDelete = loadRecord(recordIdForRecordToDelete, agencyIdForRecordToDelete);
            LOGGER.info("Holdings for " + recordIdForRecordToDelete);
            final Set<Integer> holdingAgencies = state.getHoldingsItems().getAgenciesThatHasHoldingsForId(recordIdForRecordToDelete);
            LOGGER.info("is " + holdingAgencies.toString());
            // check classification - if changed it will require modification of enrichment record - due to story #1802 messages must be merged into eventual existing enrichment
            final boolean classificationsChanged = state.getLibraryRecordsHandler().hasClassificationsChanged(motherRecord, rrVersionOfRecordToDelete);
            LOGGER.info("classificationsChanged : {}", classificationsChanged);
            LOGGER.info("Enrichments for {}", recordIdForRecordToDelete);

            final Set<RecordId> enrichmentIds = rawRepo.enrichments(new RecordId(recordIdForRecordToDelete, RawRepo.COMMON_AGENCY));
            enrichmentIds.remove(new RecordId(recordIdForRecordToDelete, RawRepo.DBC_ENRICHMENT)); // No reason to fiddle with this in th main loop
            LOGGER.info("is " + enrichmentIds);
            final Set<Integer> totalAgencies = new HashSet<>(holdingAgencies);
            for (RecordId enrichmentId : enrichmentIds) {
                totalAgencies.add(enrichmentId.getAgencyId());
            }

            final boolean isLinkRecInProduction = state.getLibraryRecordsHandler().isRecordInProduction(motherRecord);
            LOGGER.info("Record in production {}-{} : {} ", agencyIdForRecordToDelete, motherRecordId, isLinkRecInProduction);

            for (Integer workAgencyId : totalAgencies) {
                if (!state.getVipCoreService().hasFeature(workAgencyId.toString(), VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)) {
                    // Why this check ? 002 linking only works for agencies that uses enrichments - LJL says it can be PH/SBCI that makes it necessary - doesn't hurt so kept for now
                    LOGGER.info("Ignoring holdings for agency '{}', because they do not have the feature '{}'", workAgencyId, VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS);
                    continue;
                }
                final boolean hasEnrichment = enrichmentIds.contains(new RecordId(recordIdForRecordToDelete, workAgencyId));
                LOGGER.info("Agency {} has enrichment : {}", workAgencyId, hasEnrichment);
                if (hasEnrichment) {
                    final Record enrichmentRecord = rawRepo.fetchRecord(recordIdForRecordToDelete, workAgencyId);
                    final MarcRecord enrichmentRecordData = RecordContentTransformer.decodeRecord(enrichmentRecord.getContent());
                    children.add(getMoveEnrichmentRecordAction(motherRecordId, enrichmentRecordData, classificationsChanged, isLinkRecInProduction));
                } else {
                    if (classificationsChanged && holdingAgencies.contains(workAgencyId) && !isLinkRecInProduction) {
                        children.add(createJobForAddingEnrichmentRecord(workAgencyId.toString(), motherRecordId, rrVersionOfRecordToDelete));
                        children.add(getActionForCreateActionForLinkedRecords(motherRecord, workAgencyId, rrVersionOfRecordToDelete));
                    }
                }
            }
        } catch (Throwable e) {
            LOGGER.info("performActionsFor002Links fails with : {}", e.toString());
            throw e;
        }
    }

}
