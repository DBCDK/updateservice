package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.utils.json.Json;
import dk.dbc.updateservice.dto.UpdateStatusEnumDto;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.dto.DoubleRecordFrontendDto;
import dk.dbc.updateservice.dto.DoubleRecordFrontendStatusDto;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.ws.MDCUtil;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.IOException;
import java.util.Properties;

/**
 * Action to check a record for double records, and if one exists return a warning to the user.
 */
public class DoubleRecordFrontendAction extends AbstractAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(DoubleRecordFrontendAction.class);
    private static final String ENTRY_POINT = "checkDoubleRecordFrontend";

    Properties settings;

    public DoubleRecordFrontendAction(GlobalActionState globalActionState, Properties properties) {
        super(DoubleRecordFrontendAction.class.getSimpleName(), globalActionState);
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
        ServiceResult result = null;
        try {
            logger.info("Handling record:\n{}", state.readRecord());
            Object jsResult = state.getScripter().callMethod(ENTRY_POINT, Json.encode(state.readRecord()), settings);
            logger.debug("Result from " + ENTRY_POINT + " JS (" + jsResult.getClass().getName() + "): " + jsResult);
            result = parseJavascript(jsResult);
            return result;
        } catch (IOException | ScripterException e) {
            String message = String.format(state.getMessages().getString("internal.double.record.frontend.check.error"), e.getMessage());
            logger.error(message, e);
            return result = ServiceResult.newOkResult();
        } finally {
            logger.exit(result);
        }
    }

    private ServiceResult parseJavascript(Object o) throws IOException {
        ServiceResult result;
        DoubleRecordFrontendStatusDto doubleRecordFrontendStatusDto = Json.decode(o.toString(), DoubleRecordFrontendStatusDto.class);
        if ("ok".equals(doubleRecordFrontendStatusDto.getStatus())) {
            result = ServiceResult.newOkResult();
        } else if ("doublerecord".equals(doubleRecordFrontendStatusDto.getStatus())) {
            result = new ServiceResult();
            for (DoubleRecordFrontendDto doubleRecordFrontendDto : doubleRecordFrontendStatusDto.getDoubleRecordFrontendDtos()) {
                result.addServiceResult(ServiceResult.newDoubleRecordErrorResult(UpdateStatusEnumDto.FAILED, doubleRecordFrontendDto, state));
            }
            result.setDoubleRecordKey(state.getUpdateStore().getNewDoubleRecordKey());
        } else {
            String msg = "Unknown error";
            if (doubleRecordFrontendStatusDto.getDoubleRecordFrontendDtos() != null && !doubleRecordFrontendStatusDto.getDoubleRecordFrontendDtos().isEmpty()) {
                msg = doubleRecordFrontendStatusDto.getDoubleRecordFrontendDtos().get(0).getMessage();
            }
            result = ServiceResult.newErrorResult(UpdateStatusEnumDto.FAILED, msg, state);
        }
        return result;
    }

    @Override
    public void setupMDCContext() {
        MDCUtil.setupContextForRecord(state.readRecord());
    }
}