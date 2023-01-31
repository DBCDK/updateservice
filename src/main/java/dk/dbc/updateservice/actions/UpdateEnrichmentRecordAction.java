/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.common.records.utils.RecordContentTransformer;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.utils.DeferredLogger;

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
    private static final DeferredLogger LOGGER = new DeferredLogger(UpdateEnrichmentRecordAction.class);

    Decoder decoder = new Decoder();
    Properties settings;
    private final int parentAgencyId;

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
    static class Decoder {
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
    public ServiceResult performAction() throws UpdateException {
        return LOGGER.callChecked(log -> {
            try {
                log.info("Handling record: {}", marcRecord);
                final MarcRecordReader reader = new MarcRecordReader(marcRecord);
                if (reader.markedForDeletion()) {
                    return performDeletionAction();
                }

                final String wrkRecordId = reader.getRecordId();
                final String wrkParentId = reader.getParentRecordId();
                if (wrkParentId != null && !wrkParentId.isEmpty()) {
                    final String agencyId = reader.getAgencyId();
                    final String message = String.format(state.getMessages().getString("enrichment.has.parent"), wrkRecordId, agencyId);
                    log.warn("Unable to update enrichment record due to an error: {}", message);
                    return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
                }
                if (!rawRepo.recordExists(wrkRecordId, getParentAgencyId())) {
                    final String message = String.format(state.getMessages().getString("record.does.not.exist"), wrkRecordId);
                    log.warn("Unable to update enrichment record due to an error: {}", message);
                    return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
                }
                final Record commonRecord = rawRepo.fetchRecord(wrkRecordId, getParentAgencyId());
                final MarcRecord decodedRecord = decoder.decodeRecord(commonRecord.getContent());
                final MarcRecord enrichmentRecord = state.getLibraryRecordsHandler().correctLibraryExtendedRecord(decodedRecord, marcRecord);

                log.info("Correct content of enrichment record.");
                log.info("Old content:\n{}", marcRecord);
                log.info("New content:\n{}", enrichmentRecord);
                if (enrichmentRecord.isEmpty()) {
                    return performDeletionAction();
                }

                removeMinusEnrichment(enrichmentRecord);

                return performSaveRecord(enrichmentRecord);
            } catch (UnsupportedEncodingException ex) {
                log.error("Update error: " + ex.getMessage(), ex);
                throw new UpdateException(ex.getMessage(), ex);
            }
        });
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
     */
    private ServiceResult performSaveRecord(MarcRecord enrichmentRecord) {
        final String recordId = new MarcRecordReader(marcRecord).getRecordId();
        children.add(StoreRecordAction.newStoreEnrichmentAction(state, settings, enrichmentRecord));
        final LinkRecordAction linkRecordAction = new LinkRecordAction(state, enrichmentRecord);
        linkRecordAction.setLinkToRecordId(new RecordId(recordId, getParentAgencyId()));
        children.add(linkRecordAction);
        children.add(EnqueueRecordAction.newEnqueueAction(state, enrichmentRecord, settings));

        return ServiceResult.newOkResult();
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
        return LOGGER.callChecked(log -> {
            final MarcRecordReader reader = new MarcRecordReader(marcRecord);
            final String recordId = reader.getRecordId();
            final int agencyId = reader.getAgencyIdAsInt();

            if (!rawRepo.recordExists(recordId, agencyId)) {
                log.info("The enrichment record {{}:{}} does not exist, so no actions is added for deletion.", recordId, agencyId);
                return ServiceResult.newOkResult();
            }
            log.info("Creating sub actions to delete enrichment record successfully");
            children.add(EnqueueRecordAction.newEnqueueAction(state, marcRecord, settings));
            children.add(new RemoveLinksAction(state, marcRecord));
            final DeleteRecordAction deleteRecordAction = new DeleteRecordAction(state, settings, marcRecord);
            deleteRecordAction.setMimetype(MarcXChangeMimeType.ENRICHMENT);
            children.add(deleteRecordAction);
            return ServiceResult.newOkResult();
        });

    }

    /*
    Do not try to clean the code by replacing this with the variable, there is an override in UpdateSchoolEnrichmentRecordAction.java
     */
    protected int getParentAgencyId() {
        return parentAgencyId;
    }

    void removeMinusEnrichment(MarcRecord marcRecord) {
        MarcRecordWriter writer = new MarcRecordWriter(marcRecord);

        writer.removeSubfield("z98", "b");
    }
}
