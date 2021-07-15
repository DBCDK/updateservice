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
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.Properties;
import java.util.Set;

/**
 * Action to delete a common record.
 * <p>
 * It supports single and volume records.
 * </p>
 */
public class DeleteCommonRecordAction extends AbstractRawRepoAction {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(DeleteCommonRecordAction.class);

    Properties settings;

    public DeleteCommonRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord record) {
        super(DeleteCommonRecordAction.class.getSimpleName(), globalActionState, record);
        settings = properties;
    }

    /**
     * Performs this actions and may create any child actions.
     *
     * @return A ServiceResult to be reported in the web service response.
     * @throws UpdateException In case of an error.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        LOGGER.entry();
        try {
            LOGGER.info("Handling record: {}", LogUtils.base64Encode(marcRecord));
            Set<RecordId> recordChildren = rawRepo.children(marcRecord);
            if (!recordChildren.isEmpty()) {
                if (checkForNotDeletableLittolkChildren(recordChildren)) {
                    deleteLittolkChildren(recordChildren);
                } else {
                    MarcRecordReader reader = new MarcRecordReader(marcRecord);
                    String recordId = reader.getRecordId();

                    String message = state.getMessages().getString("delete.record.children.error");
                    String errorMessage = String.format(message, recordId);

                    LOGGER.error("Unable to create sub actions due to an error: {}", errorMessage);
                    return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, errorMessage);
                }
            }

            for (RecordId enrichmentId : rawRepo.enrichments(marcRecord)) {
                Record rawRepoEnrichmentRecord = rawRepo.fetchRecord(enrichmentId.getBibliographicRecordId(), enrichmentId.getAgencyId());
                MarcRecord enrichmentRecord = RecordContentTransformer.decodeRecord(rawRepoEnrichmentRecord.getContent());

                MarcRecordWriter writer = new MarcRecordWriter(enrichmentRecord);
                writer.markForDeletion();

                UpdateEnrichmentRecordAction updateEnrichmentRecordAction = new UpdateEnrichmentRecordAction(state, settings, enrichmentRecord);

                children.add(updateEnrichmentRecordAction);
            }

            children.add(EnqueueRecordAction.newEnqueueAction(state, marcRecord, settings));
            children.add(new RemoveLinksAction(state, marcRecord));
            children.add(DeleteRecordAction.newDeleteRecordAction(state, settings, marcRecord));

            return ServiceResult.newOkResult();
        } catch (UnsupportedEncodingException ex) {
            LOGGER.error(ex.getMessage(), ex);
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, ex.getMessage());
        } finally {
            LOGGER.exit();
        }
    }

    /**
     * Return true if all children are 870974 littolk records
     * <p>
     * Otherwise false
     *
     * @param recordChildren The list of RecordIds to check
     * @return true if all children are 870974 littolk records which can be deleted
     */
    private boolean checkForNotDeletableLittolkChildren(Set<RecordId> recordChildren) {
        for (RecordId recordId : recordChildren) {
            if (recordId.getAgencyId() != RawRepo.LITTOLK_AGENCY) {
                return false;
            }
        }

        return true;
    }

    /**
     * This function deletes every record plus its enrichment in recordChildren input
     *
     * @param recordChildren The list of records to delete
     * @throws UpdateException In case of an error.
     * @throws UnsupportedEncodingException If the record can't be decoded
     */
    private void deleteLittolkChildren(Set<RecordId> recordChildren) throws UpdateException, UnsupportedEncodingException {
        for (RecordId recordId : recordChildren) {
            MarcRecord littolkEnrichment = RecordContentTransformer.decodeRecord(rawRepo.fetchRecord(recordId.getBibliographicRecordId(), RawRepo.DBC_ENRICHMENT).getContent());
            LOGGER.info("Creating DeleteRecordAction for {}:{}", recordId.getBibliographicRecordId(), RawRepo.DBC_ENRICHMENT);
            new MarcRecordWriter(littolkEnrichment).markForDeletion();
            children.add(new UpdateEnrichmentRecordAction(state, settings, littolkEnrichment));

            MarcRecord littolkRecord = RecordContentTransformer.decodeRecord(rawRepo.fetchRecord(recordId.getBibliographicRecordId(), RawRepo.LITTOLK_AGENCY).getContent());
            LOGGER.info("Creating DeleteRecordAction for {}:{}", recordId.getBibliographicRecordId(), RawRepo.LITTOLK_AGENCY);
            children.add(new DeleteCommonRecordAction(state, settings, littolkRecord));
        }
    }

}
