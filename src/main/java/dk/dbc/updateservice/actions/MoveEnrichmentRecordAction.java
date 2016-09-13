package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.records.MarcRecordWriter;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.RawRepoDecoder;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.Properties;

/**
 * Action to move an existing enrichment to another common record.
 * <p>
 * The only change that is maked to the enrichment record is in 001a.
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
    private static final XLogger logger = XLoggerFactory.getXLogger(MoveEnrichmentRecordAction.class);
    private static final XLogger bizLogger = XLoggerFactory.getXLogger(BusinessLoggerFilter.LOGGER_NAME);

    private MarcRecord commonRecord = null;
    Properties settings;

    public MoveEnrichmentRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord marcRecord) {
        super(MoveEnrichmentRecordAction.class.getSimpleName(), globalActionState, marcRecord);
        settings = properties;
    }

    public MarcRecord getCommonRecord() {
        return commonRecord;
    }

    public void setCommonRecord(MarcRecord commonRecord) {
        this.commonRecord = commonRecord;
    }

    /**
     * Performs this actions and may create any child actions.
     *
     * @return A ServiceResult to be reported in the web service response.
     * @throws UpdateException In case of an error.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        logger.entry();
        ServiceResult result = null;
        try {
            bizLogger.info("Handling record:\n{}", record);
            children.add(createDeleteEnrichmentAction());
            children.add(createMoveEnrichmentToCommonRecordAction());
            return result = ServiceResult.newOkResult();
        } finally {
            logger.exit(result);
        }
    }

    /**
     * Constructs an action to delete the enrichment record in this action.
     *
     * @return An instance of UpdateEnrichmentRecordAction
     */
    private ServiceAction createDeleteEnrichmentAction() {
        logger.entry();
        ServiceAction result = null;
        try {
            MarcRecord deleteRecord = new MarcRecord(record);
            MarcRecordReader reader = new MarcRecordReader(deleteRecord);
            String recordId = reader.recordId();
            String agencyId = reader.agencyId();
            bizLogger.info("Create action to delete old enrichment record {{}:{}}", recordId, agencyId);
            MarcRecordWriter writer = new MarcRecordWriter(deleteRecord);
            writer.markForDeletion();
            return createUpdateRecordAction(deleteRecord);
        } finally {
            logger.exit(result);
        }
    }

    /**
     * Constructs an action to delete the enrichment record in this action.
     *
     * @return An instance of UpdateEnrichmentRecordAction
     */
    private ServiceAction createMoveEnrichmentToCommonRecordAction() throws UpdateException {
        logger.entry();
        ServiceAction result = null;
        try {
            String commonRecordId = new MarcRecordReader(commonRecord).recordId();
            MarcRecord newEnrichmentRecord = new MarcRecord(record);
            MarcRecordWriter writer = new MarcRecordWriter(newEnrichmentRecord);
            writer.addOrReplaceSubfield("001", "a", commonRecordId);

            MarcRecordReader reader = new MarcRecordReader(record);
            String recordId = reader.recordId();
            String agencyId = reader.agencyId();
            bizLogger.info("Create action to let new enrichment record {{}:{}} point to common record {}", recordId, agencyId, commonRecordId);

            if (state.getLibraryRecordsHandler().hasClassificationData(newEnrichmentRecord)) {
                bizLogger.info("Enrichment record has classifications. Creating sub action to update it.");
                return createUpdateRecordAction(newEnrichmentRecord);
            }
            MarcRecord currentCommonRecord = new RawRepoDecoder().decodeRecord(rawRepo.fetchRecord(recordId, RawRepo.RAWREPO_COMMON_LIBRARY).getContent());
            ServiceResult shouldCreateEnrichmentRecords = state.getLibraryRecordsHandler().shouldCreateEnrichmentRecords(settings, currentCommonRecord, commonRecord);
            bizLogger.info("Should we create enrichment records result: {}", shouldCreateEnrichmentRecords);

            if (shouldCreateEnrichmentRecords.getStatus() == UpdateStatusEnum.OK) {
                bizLogger.info("Creating enrichment record with classifications, because the common record is published.");
                return createUpdateRecordAndClassificationsAction(newEnrichmentRecord, currentCommonRecord);
            } else {
                bizLogger.info("Creating enrichment record without classifications, because the common record is still in production.");
                return createUpdateRecordAction(newEnrichmentRecord);
            }
        } catch (ScripterException | UnsupportedEncodingException ex) {
            throw new UpdateException(ex.getMessage(), ex);
        } finally {
            logger.exit(result);
        }
    }

    /**
     * Constructs an action to update a record where classification data is
     * not updated.
     *
     * @param updateRecord The record to update.
     * @return An instance of UpdateEnrichmentRecordAction
     */
    private ServiceAction createUpdateRecordAction(MarcRecord updateRecord) {
        logger.entry();
        UpdateEnrichmentRecordAction updateEnrichmentRecordAction = null;
        try {
            updateEnrichmentRecordAction = new UpdateEnrichmentRecordAction(state, settings, updateRecord);
            return updateEnrichmentRecordAction;
        } finally {
            logger.exit(updateEnrichmentRecordAction);
        }
    }

    /**
     * Constructs an action to update a record where classification data is
     * updated.
     *
     * @param updateRecord The record to update.
     * @param commonRecord The common record to copy classifications data from.
     * @return An instance of UpdateEnrichmentRecordAction
     */
    private ServiceAction createUpdateRecordAndClassificationsAction(MarcRecord updateRecord, MarcRecord commonRecord) {
        logger.entry();
        UpdateClassificationsInEnrichmentRecordAction updateClassificationsInEnrichmentRecordAction = null;
        try {
            MarcRecordReader reader = new MarcRecordReader(updateRecord);
            updateClassificationsInEnrichmentRecordAction = new UpdateClassificationsInEnrichmentRecordAction(state, settings, reader.agencyId());
            updateClassificationsInEnrichmentRecordAction.setCurrentCommonRecord(commonRecord);
            updateClassificationsInEnrichmentRecordAction.setUpdatingCommonRecord(commonRecord);
            updateClassificationsInEnrichmentRecordAction.setEnrichmentRecord(updateRecord);
            return updateClassificationsInEnrichmentRecordAction;
        } finally {
            logger.exit(updateClassificationsInEnrichmentRecordAction);
        }
    }
}
