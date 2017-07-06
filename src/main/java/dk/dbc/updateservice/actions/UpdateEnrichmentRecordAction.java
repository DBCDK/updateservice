/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.update.*;
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

    RawRepoDecoder decoder = new RawRepoDecoder();
    Properties settings;
    private Integer parentAgencyId;

    public UpdateEnrichmentRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord marcRecord) {
        this(globalActionState, properties, marcRecord, RawRepo.COMMON_AGENCY);
    }

    public UpdateEnrichmentRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord marcRecord, Integer parentAgencyId) {
        super(UpdateEnrichmentRecordAction.class.getSimpleName(), globalActionState, marcRecord);
        this.settings = properties;
        this.parentAgencyId = parentAgencyId;
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
    public ServiceResult performAction() throws UpdateException, SolrException {
        logger.entry();
        try {
            logger.info("Handling record:\n" + record);
            MarcRecordReader reader = new MarcRecordReader(record);
            if (reader.markedForDeletion()) {
                return performDeletionAction();
            }

            String recordId = reader.recordId();
            String parentId = reader.parentRecordId();
            if (parentId != null && !parentId.isEmpty()) {
                String agencyId = reader.agencyId();
                String message = String.format(state.getMessages().getString("enrichment.has.parent"), recordId, agencyId);
                logger.warn("Unable to update enrichment record due to an error: " + message);
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
            }
            if (!rawRepo.recordExists(recordId, getParentAgencyId())) {
                String message = String.format(state.getMessages().getString("record.does.not.exist"), recordId);
                logger.warn("Unable to update enrichment record due to an error: " + message);
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
            }
            if (!rawRepo.recordExists(recordId, reader.agencyIdAsInteger())) {
                if (state.getSolrService().hasDocuments(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", reader.recordId()))) {
                    String message = state.getMessages().getString("update.record.with.002.links");
                    logger.error("Unable to create sub actions due to an error: " + message);
                    return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
                }
            }
            Record commonRecord = rawRepo.fetchRecord(recordId, getParentAgencyId());
            MarcRecord decodedRecord = decoder.decodeRecord(commonRecord.getContent());
            MarcRecord enrichmentRecord = state.getLibraryRecordsHandler().correctLibraryExtendedRecord(decodedRecord, record);

            logger.info("Correct content of enrichment record.");
            logger.info("Old content:\n" + record);
            logger.info("New content:\n" + enrichmentRecord);
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

            children.add(StoreRecordAction.newStoreEnrichmentAction(state, settings, enrichmentRecord));
            LinkRecordAction linkRecordAction = new LinkRecordAction(state, enrichmentRecord);
            linkRecordAction.setLinkToRecordId(new RecordId(recordId, getParentAgencyId()));
            children.add(linkRecordAction);
            children.add(EnqueueRecordAction.newEnqueueAction(state, enrichmentRecord, settings));
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
                logger.info("The enrichment record {{}:{}} does not exist, so no actions is added for deletion.", recordId, agencyId);
                return ServiceResult.newOkResult();
            }
            logger.info("Creating sub actions to delete enrichment record successfully");
            children.add(EnqueueRecordAction.newEnqueueAction(state, record, settings));
            children.add(new RemoveLinksAction(state, record));
            DeleteRecordAction deleteRecordAction = new DeleteRecordAction(state, settings, record);
            deleteRecordAction.setMimetype(MarcXChangeMimeType.ENRICHMENT);
            children.add(deleteRecordAction);

            return ServiceResult.newOkResult();
        } finally{
            logger.exit();
        }

    }

    protected Integer getParentAgencyId() {
        return parentAgencyId;
    }
}
