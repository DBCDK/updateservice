/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.utils.RecordContentTransformer;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.SolrException;
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

    Decoder decoder = new Decoder();
    Properties settings;
    private int parentAgencyId;

    public UpdateEnrichmentRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord marcRecord) {
        this(globalActionState, properties, marcRecord, RawRepo.COMMON_AGENCY);
    }

    public UpdateEnrichmentRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord marcRecord, int parentAgencyId) {
        super(UpdateEnrichmentRecordAction.class.getSimpleName(), globalActionState, marcRecord);
        this.settings = properties;
        this.parentAgencyId = parentAgencyId;
    }

    /**
     * Class used for mocking during unit test
     */
    class Decoder {
        MarcRecord decodeRecord(byte[] bytes) throws UnsupportedEncodingException {
            return RecordContentTransformer.decodeRecord(bytes);
        }
    }

    /**
     * Constructs child actions to update or delete the enrichment record.
     * <p>
     * An enrichment record is updated as follows:
     * <ol>
     * <li>
     * If it is marked for deletion then creates actions for
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
            logger.info("Handling record: {}", record);
            MarcRecordReader reader = new MarcRecordReader(record);
            if (reader.markedForDeletion()) {
                return performDeletionAction();
            }

            String wrkRecordId = reader.getRecordId();
            String wrkParentId = reader.getParentRecordId();
            if (wrkParentId != null && !wrkParentId.isEmpty()) {
                String agencyId = reader.getAgencyId();
                String message = String.format(state.getMessages().getString("enrichment.has.parent"), wrkRecordId, agencyId);
                logger.warn("Unable to update enrichment record due to an error: " + message);
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
            }
            if (!rawRepo.recordExists(wrkRecordId, getParentAgencyId())) {
                String message = String.format(state.getMessages().getString("record.does.not.exist"), wrkRecordId);
                logger.warn("Unable to update enrichment record due to an error: " + message);
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
            }
            Record commonRecord = rawRepo.fetchRecord(wrkRecordId, getParentAgencyId());
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
            String recordId = new MarcRecordReader(record).getRecordId();

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
            String recordId = reader.getRecordId();
            int agencyId = reader.getAgencyIdAsInt();

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
        } finally {
            logger.exit();
        }

    }

    /*
    Do not try to clean the code by replacing this with the variable, there is an override in UpdateSchoolEnrichmentRecordAction.java
     */
    protected int getParentAgencyId() {
        return parentAgencyId;
    }
}
