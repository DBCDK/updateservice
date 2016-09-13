package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.Properties;

/**
 * Action to overwrite an existing volume record.
 */
public class OverwriteVolumeRecordAction extends OverwriteSingleRecordAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(OverwriteVolumeRecordAction.class);
    private static final XLogger bizLogger = XLoggerFactory.getXLogger(BusinessLoggerFilter.LOGGER_NAME);

    GlobalActionState state;

    public OverwriteVolumeRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord record) {
        super(globalActionState, properties, record);
        setName("OverwriteVolumeRecordAction");
        state = globalActionState;
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
        ServiceResult result = ServiceResult.newOkResult();
        try {
            bizLogger.info("Handling record:\n{}", record);
            MarcRecordReader reader = new MarcRecordReader(record);
            String recordId = reader.recordId();
            String parentId = reader.parentId();
            Integer agencyId = reader.agencyIdAsInteger();

            if (recordId.equals(parentId)) {
                Integer errorAgencyId = agencyId;
                if (errorAgencyId.equals(RawRepo.RAWREPO_COMMON_LIBRARY)) {
                    errorAgencyId = RawRepo.COMMON_LIBRARY;
                }
                String message = String.format(state.getMessages().getString("parent.point.to.itself"), recordId, errorAgencyId);
                bizLogger.error("Unable to create sub actions doing to an error: {}", message);
                return ServiceResult.newErrorResult(UpdateStatusEnum.FAILED, message, state);
            }

            if (!rawRepo.recordExists(parentId, agencyId)) {
                String message = String.format(state.getMessages().getString("reference.record.not.exist"), recordId, agencyId, parentId, agencyId);
                bizLogger.error("Unable to create sub actions doing to an error: {}", message);
                return ServiceResult.newErrorResult(UpdateStatusEnum.FAILED, message, state);
            }

            MarcRecord currentRecord = loadCurrentRecord();
            children.add(StoreRecordAction.newStoreAction(state, record, MarcXChangeMimeType.MARCXCHANGE));
            children.add(new RemoveLinksAction(state, record));
            children.add(LinkRecordAction.newLinkParentAction(state, record));
            children.addAll(createActionsForCreateOrUpdateEnrichments(currentRecord));

            result = performActionsFor002Links();
            children.add(EnqueueRecordAction.newEnqueueAction(state, record, settings, MarcXChangeMimeType.MARCXCHANGE));
            return result;
        } catch (ScripterException | UnsupportedEncodingException ex) {
            return result = ServiceResult.newErrorResult(UpdateStatusEnum.FAILED, ex.getMessage(), state);
        } finally {
            logger.exit(result);
        }
    }
}
