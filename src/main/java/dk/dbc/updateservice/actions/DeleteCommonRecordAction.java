package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.records.MarcRecordWriter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDto;
import dk.dbc.updateservice.update.RawRepoDecoder;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.Properties;

/**
 * Action to delete a common record.
 * <p>
 * It supports single and volume records.
 * </p>
 */
public class DeleteCommonRecordAction extends AbstractRawRepoAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(OverwriteSingleRecordAction.class);

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
        logger.entry();
        try {
            logger.info("Handling record:\n{}", record);
            if (!rawRepo.children(record).isEmpty()) {
                MarcRecordReader reader = new MarcRecordReader(record);
                String recordId = reader.recordId();

                String message = state.getMessages().getString("delete.record.children.error");
                String errorMessage = String.format(message, recordId);

                logger.error("Unable to create sub actions doing to an error: {}", errorMessage);
                return ServiceResult.newErrorResult(UpdateStatusEnumDto.FAILED, errorMessage, state);
            }

            for (RecordId enrichmentId : rawRepo.enrichments(record)) {
                Record rawRepoEnrichmentRecord = rawRepo.fetchRecord(enrichmentId.getBibliographicRecordId(), enrichmentId.getAgencyId());
                MarcRecord enrichmentRecord = new RawRepoDecoder().decodeRecord(rawRepoEnrichmentRecord.getContent());

                MarcRecordWriter writer = new MarcRecordWriter(enrichmentRecord);
                writer.markForDeletion();

                UpdateEnrichmentRecordAction updateEnrichmentRecordAction = new UpdateEnrichmentRecordAction(state, settings, enrichmentRecord);

                children.add(updateEnrichmentRecordAction);
            }
            logger.error("Creating sub actions successfully");

            children.add(new RemoveLinksAction(state, record));
            children.add(DeleteRecordAction.newDeleteRecordAction(state, settings, record, MarcXChangeMimeType.MARCXCHANGE));
            children.add(ActionFactory.newEnqueueAction(state, record, settings, MarcXChangeMimeType.MARCXCHANGE));
            return ServiceResult.newOkResult();
        } catch (UnsupportedEncodingException ex) {
            logger.error(ex.getMessage(), ex);
            return ServiceResult.newErrorResult(UpdateStatusEnumDto.FAILED, ex.getMessage(), state);
        } finally {
            logger.exit();
        }
    }
}
