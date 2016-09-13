package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.Properties;

/**
 * Action to creates a new volume record.
 * <p>
 * The main difference from CreateSingleRecordAction is that we need to link
 * the volume record with its parent.
 * </p>
 */
public class CreateVolumeRecordAction extends AbstractRawRepoAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(CreateVolumeRecordAction.class);
    private static final XLogger bizLogger = XLoggerFactory.getXLogger(BusinessLoggerFilter.LOGGER_NAME);

    Properties settings;

    public CreateVolumeRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord record) {
        super(CreateVolumeRecordAction.class.getSimpleName(), globalActionState, record);
        settings = properties;
    }

    /**
     * Performs this actions and may create any child actions.
     *
     * @return A list of ValidationError to be reported in the web service response.
     * @throws UpdateException In case of an error.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        logger.entry();

        try {
            bizLogger.info("Handling record:\n{}", record);

            MarcRecordReader reader = new MarcRecordReader(record);
            String recordId = reader.recordId();
            String parentId = reader.parentId();
            Integer agencyId = reader.agencyIdAsInteger();

            if (recordId.equals(parentId)) {
                String message = String.format(state.getMessages().getString("parent.point.to.itself"), recordId, agencyId);

                bizLogger.error("Unable to create sub actions doing to an error: {}", message);
                return ServiceResult.newErrorResult(UpdateStatusEnum.FAILED, message, state);
            }

            if (!rawRepo.recordExists(parentId, agencyId)) {
                String message = String.format(state.getMessages().getString("reference.record.not.exist"), recordId, agencyId, parentId, agencyId);

                bizLogger.error("Unable to create sub actions doing to an error: {}", message);
                return ServiceResult.newErrorResult(UpdateStatusEnum.FAILED, message, state);
            }

            if (!rawRepo.agenciesForRecord(record).isEmpty()) {
                String message = state.getMessages().getString("create.record.with.locals");

                bizLogger.error("Unable to create sub actions doing to an error: {}", message);
                return ServiceResult.newErrorResult(UpdateStatusEnum.FAILED, message, state);
            }

            if (state.getSolrService().hasDocuments(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId))) {
                String message = state.getMessages().getString("update.record.with.002.links");
                bizLogger.error("Unable to create sub actions doing to an error: {}", message);
                return ServiceResult.newErrorResult(UpdateStatusEnum.FAILED, message, state);
            }

            bizLogger.error("Creating sub actions successfully");
            children.add(StoreRecordAction.newStoreAction(state, record, MarcXChangeMimeType.MARCXCHANGE));
            children.add(new RemoveLinksAction(state, record));
            children.add(LinkRecordAction.newLinkParentAction(state, record));
            children.add(EnqueueRecordAction.newEnqueueAction(state, record, settings, MarcXChangeMimeType.MARCXCHANGE));
            return ServiceResult.newOkResult();
        } finally {
            logger.exit();
        }
    }
}
