/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.common.records.utils.LogUtils;
import dk.dbc.common.records.utils.RecordContentTransformer;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.utils.DeferredLogger;

import java.io.UnsupportedEncodingException;
import java.util.Properties;

/**
 * Action to move an existing enrichment to another common record.
 * <p>
 * The only change that is made to the enrichment record is in 001a.
 * The rest of the record is unchanged.
 * </p>
 * <p>
 * The action verifies that the new common record exists.
 * </p>
 * <p>
 * The old enrichment record is deleted from the rawrepo.
 * </p>
 */
public class MoveEnrichmentRecordAction extends AbstractRawRepoAction {
    private static final DeferredLogger LOGGER = new DeferredLogger(MoveEnrichmentRecordAction.class);

    private final boolean isClassificationChangedInCommonRecs;
    private final boolean isLinkRecInProduction;
    private String targetRecordId = null;
    Properties settings;

    public MoveEnrichmentRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord enrichmentToMove, boolean classificationChanged, boolean linkRecInProduction) {
        super(MoveEnrichmentRecordAction.class.getSimpleName(), globalActionState, enrichmentToMove);
        settings = properties;
        isClassificationChangedInCommonRecs = classificationChanged;
        isLinkRecInProduction = linkRecInProduction;
    }

    void setTargetRecordId(String commonRecord) {
        targetRecordId = commonRecord;
    }

    /**
     * Performs this actions and may create any child actions.
     *
     * @return A ServiceResult to be reported in the web service response.
     * @throws UpdateException In case of an error.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        LOGGER.use(log -> {
            if (log.isInfoEnabled()) {
                log.info("Handling record: {}", LogUtils.base64Encode(marcRecord));
            }
        });
        children.add(createDeleteEnrichmentAction());
        children.add(createMoveEnrichmentToCommonRecordAction());
        return ServiceResult.newOkResult();
    }

    /**
     * Constructs an action to delete the enrichment record in this action.
     *
     * @return An instance of UpdateRecordAction
     */
    private ServiceAction createDeleteEnrichmentAction() {
        final MarcRecord deleteRecord = new MarcRecord(marcRecord);
        final MarcRecordReader reader = new MarcRecordReader(deleteRecord);
        final String recordId = reader.getRecordId();
        final String agencyId = reader.getAgencyId();

        LOGGER.use(log -> log.info("Create action to delete old enrichment record {{}:{}}", recordId, agencyId));

        final MarcRecordWriter writer = new MarcRecordWriter(deleteRecord);
        writer.markForDeletion();
        return createUpdateRecordAction(deleteRecord);
    }

    /**
     * Constructs an action to delete the enrichment record in this action.
     *
     * @return An instance of UpdateRecordAction or UpdateRecordAndClassificationsAction
     */
    private ServiceAction createMoveEnrichmentToCommonRecordAction() throws UpdateException {
        return LOGGER.callChecked(log -> {
            try {
                final MarcRecord newEnrichmentRecord = new MarcRecord(marcRecord);
                final MarcRecordWriter writer = new MarcRecordWriter(newEnrichmentRecord);
                writer.addOrReplaceSubfield("001", "a", targetRecordId);

                final MarcRecordReader reader = new MarcRecordReader(marcRecord);
                final String recordId = reader.getRecordId();
                final String agencyId = reader.getAgencyId();
                log.info("Create action to let new enrichment record {{}:{}} point to common record {}", recordId, agencyId, targetRecordId);

                if (state.getLibraryRecordsHandler().hasClassificationData(newEnrichmentRecord)) {
                    log.info("Enrichment record has classifications. Creating sub action to update it.");
                    return createUpdateRecordAction(newEnrichmentRecord);
                }
                final MarcRecord currentCommonRecord = RecordContentTransformer.decodeRecord(rawRepo.fetchRecord(recordId, RawRepo.COMMON_AGENCY).getContent());

                log.debug("ClassificationChangedInCommonRecs {} ", isClassificationChangedInCommonRecs);
                log.debug("isLinkRecInProduction {} ", isLinkRecInProduction);

                if (isClassificationChangedInCommonRecs) {
                    if (isLinkRecInProduction) {
                        log.info("Creating enrichment record without classifications, because the linkRecord is in production.");
                        return createUpdateRecordAction(newEnrichmentRecord);
                    } else {
                        log.info("Creating enrichment record with classifications, because the linkRecord is published.");
                        return createUpdateRecordAndClassificationsAction(newEnrichmentRecord, currentCommonRecord);
                    }
                } else {
                    log.info("Creating enrichment record without classifications, because there are no change in die/live records.");
                    return createUpdateRecordAction(newEnrichmentRecord);
                }
            } catch (UnsupportedEncodingException ex) {
                throw new UpdateException(ex.getMessage(), ex);
            }
        });
    }

    /**
     * Constructs an action to update a record where classification data is
     * not updated.
     *
     * @param updateRecord The record to update.
     * @return An instance of UpdateEnrichmentRecordAction
     */
    private ServiceAction createUpdateRecordAction(MarcRecord updateRecord) {
        return new UpdateEnrichmentRecordAction(state, settings, updateRecord);
    }

    /**
     * Constructs an action to update a record where classification data is
     * updated.
     *
     * @param updateRecord The record to update.
     * @param commonRecord The common record to copy classifications data from.
     * @return An instance of updateClassificationsInEnrichmentRecordAction
     */
    private ServiceAction createUpdateRecordAndClassificationsAction(MarcRecord updateRecord, MarcRecord commonRecord) {
        final MarcRecordReader reader = new MarcRecordReader(updateRecord);
        final UpdateClassificationsInEnrichmentRecordAction updateClassificationsInEnrichmentRecordAction =
                new UpdateClassificationsInEnrichmentRecordAction(state, settings, reader.getAgencyId());
        updateClassificationsInEnrichmentRecordAction.setCurrentCommonRecord(commonRecord);
        updateClassificationsInEnrichmentRecordAction.setUpdatingCommonRecord(commonRecord);
        updateClassificationsInEnrichmentRecordAction.setEnrichmentRecord(updateRecord);
        return updateClassificationsInEnrichmentRecordAction;
    }
}
