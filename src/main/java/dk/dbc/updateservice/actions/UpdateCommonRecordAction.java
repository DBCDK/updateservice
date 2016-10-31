package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.updateservice.dto.UpdateStatusEnumDto;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.Properties;

/**
 * This action is used to update a common record.
 * <p>
 * This action does not actual update the enrichment record, but creates child
 * actions to do the actual update. The record is checked for integrity so
 * the data model is not violated.
 * </p>
 */
public class UpdateCommonRecordAction extends AbstractRawRepoAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(UpdateCommonRecordAction.class);
    static final String MIMETYPE = MarcXChangeMimeType.MARCXCHANGE;

    private Properties settings;

    public UpdateCommonRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord record) {
        super(UpdateCommonRecordAction.class.getSimpleName(), globalActionState, record);
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

            MarcRecordReader reader = new MarcRecordReader(record);
            if (!reader.markedForDeletion()) {
                logger.info("Update single");
                if (state.getSolrService().hasDocuments(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", reader.recordId()))) {
                    String message = state.getMessages().getString("update.record.with.002.links");
                    logger.error("Unable to create sub actions due to an error: {}", message);
                    return ServiceResult.newErrorResult(UpdateStatusEnumDto.FAILED, message, state);
                }
            }

            String parentId = reader.parentId();
            if (parentId != null && !parentId.isEmpty()) {
                logger.info("Update vol:\n{}", parentId);
                children.add(new UpdateVolumeRecord(state, settings, record));
            } else {
                logger.info("Update single");
                children.add(new UpdateSingleRecord(state, settings, record));
            }
            return ServiceResult.newOkResult();
        } finally {
            logger.exit();
        }
    }
}
