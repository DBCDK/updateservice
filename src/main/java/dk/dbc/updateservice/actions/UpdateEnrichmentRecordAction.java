package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.RawRepoDecoder;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.Properties;

/**
 * Action to update an enrichment record.
 * <p>
 * This action does not actual update the enrichment record, but creates child
 * actions to do the actual update. The record is checked for integrity so
 * the data model is not violated.
 * </p>
 */
public class UpdateEnrichmentRecordAction extends AbstractRawRepoAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(UpdateEnrichmentRecordAction.class);
    private static final XLogger bizLogger = XLoggerFactory.getXLogger(BusinessLoggerFilter.LOGGER_NAME);

    RawRepoDecoder decoder = new RawRepoDecoder();
    Properties settings;

    public UpdateEnrichmentRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord marcRecord) {
        super(UpdateEnrichmentRecordAction.class.getSimpleName(), globalActionState, marcRecord);
        settings = properties;
    }

    /**
     * Constructs child actions to update or delete the enrichment record.
     * <p>
     * An enrichment record is updated as follows:
     * <ol>
     * <li>
     * If it is marked for deletion when creates actions for
     * deletion and return.
     * </li>
     * <li>
     * Correct classification data in the enrichment record from
     * the common record. If the record is empty then create child
     * actions for deletion and return.
     * </li>
     * <li>
     * Create child actions to save the enrichment record in rawrepo.
     * </li>
     * </ol>
     * </p>
     * <b>Note:</b> This implementation assumes that the common record exists in
     * rawrepo so the record being updated or deleted is an enrichment record.
     *
     * @return Service result with OK.
     * @throws UpdateException In case of an error.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        logger.entry();

        try {
            bizLogger.info("Handling record:\n{}", record);

            MarcRecordReader reader = new MarcRecordReader(record);
            if (reader.markedForDeletion()) {
                return performDeletionAction();
            }

            String recordId = reader.recordId();

            String parentId = reader.parentId();
            if (parentId != null && !parentId.isEmpty()) {
                String agencyId = reader.agencyId();
                String message = String.format(state.getMessages().getString("enrichment.has.parent"), recordId, agencyId);
                bizLogger.warn("Unable to update enrichment record doing to an error: {}", message);
                return ServiceResult.newErrorResult(UpdateStatusEnum.FAILED, message, state);
            }

            if (!rawRepo.recordExists(recordId, commonRecordAgencyId())) {
                String message = String.format(state.getMessages().getString("record.does.not.exist"), recordId);
                bizLogger.warn("Unable to update enrichment record doing to an error: {}", message);
                return ServiceResult.newErrorResult(UpdateStatusEnum.FAILED, message, state);
            }

            if (!rawRepo.recordExists(recordId, reader.agencyIdAsInteger())) {
                if (state.getSolrService().hasDocuments(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", reader.recordId()))) {
                    String message = state.getMessages().getString("update.record.with.002.links");
                    bizLogger.error("Unable to create sub actions due to an error: {}", message);
                    return ServiceResult.newErrorResult(UpdateStatusEnum.FAILED, message, state);
                }
            }
            Record commonRecord = rawRepo.fetchRecord(recordId, commonRecordAgencyId());
            MarcRecord enrichmentRecord = state.getLibraryRecordsHandler().correctLibraryExtendedRecord(decoder.decodeRecord(commonRecord.getContent()), record);

            bizLogger.info("Correct content of enrichment record.");
            bizLogger.info("Old content:\n{}", record);
            bizLogger.info("New content:\n{}", enrichmentRecord);
            if (enrichmentRecord.isEmpty()) {
                return performDeletionAction();
            }
            return performSaveRecord(enrichmentRecord);
        } catch (UnsupportedEncodingException | ScripterException ex) {
            logger.error("Update error: " + ex.getMessage(), ex);
            throw new UpdateException(ex.getMessage(), ex);
        } finally {
            logger.exit();
        }
    }

    /**
     * Creates child actions to update an enrichment record that is not
     * marked for deletion.
     * <p>
     * Enrichment records are updated as follows:
     * <ol>
     * <li>Store the record.</li>
     * <li>Link it to the common record.</li>
     * <li>Enqueue the record.</li>
     * </ol>
     * </p>
     *
     * @return OK.
     * @throws UpdateException In case of critical errors.
     */
    private ServiceResult performSaveRecord(MarcRecord enrichmentRecord) throws UpdateException {
        logger.entry();

        try {
            String recordId = new MarcRecordReader(record).recordId();

            StoreRecordAction storeRecordAction = new StoreRecordAction(state, settings, enrichmentRecord);
            storeRecordAction.setMimetype(MarcXChangeMimeType.ENRICHMENT);
            children.add(storeRecordAction);

            LinkRecordAction linkRecordAction = new LinkRecordAction(state, enrichmentRecord);
            linkRecordAction.setLinkToRecordId(new RecordId(recordId, commonRecordAgencyId()));
            children.add(linkRecordAction);
            children.add(EnqueueRecordAction.newEnqueueAction(state, enrichmentRecord, settings, MarcXChangeMimeType.ENRICHMENT));

            return ServiceResult.newOkResult();
        } finally {
            logger.exit();
        }
    }

    /**
     * Creates child actions to update a record that is
     * marked for deletion.
     * <p>
     * Records are deleted as follows:
     * <ol>
     * <li>Remove existing links to other records.</li>
     * <li>Store the record and mark it as deleted.</li>
     * <li>Enqueue the record.</li>
     * </ol>
     * </p>
     *
     * @return OK.
     * @throws UpdateException In case of critical errors.
     */
    private ServiceResult performDeletionAction() throws UpdateException {
        logger.entry();
        try {
            MarcRecordReader reader = new MarcRecordReader(record);
            String recordId = reader.recordId();
            Integer agencyId = reader.agencyIdAsInteger();

            if (!rawRepo.recordExists(recordId, agencyId)) {
                bizLogger.info("The enrichment record {{}:{}} does not exist, so no actions is added for deletion.", recordId, agencyId);
                return ServiceResult.newOkResult();
            }
            bizLogger.info("Creating sub actions to delete enrichment record successfully");
            children.add(new RemoveLinksAction(state, record));

            DeleteRecordAction deleteRecordAction = new DeleteRecordAction(state, settings, record);
            deleteRecordAction.setMimetype(MarcXChangeMimeType.ENRICHMENT);
            children.add(deleteRecordAction);
            children.add(EnqueueRecordAction.newEnqueueAction(state, record, settings, MarcXChangeMimeType.ENRICHMENT));

            return ServiceResult.newOkResult();
        } finally {
            logger.exit();
        }
    }

    protected Integer commonRecordAgencyId() {
        return RawRepo.RAWREPO_COMMON_LIBRARY;
    }
}
