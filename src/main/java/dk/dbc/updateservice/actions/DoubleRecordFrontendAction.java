/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.utils.LogUtils;
import dk.dbc.jsonb.JSONBException;
import dk.dbc.opencat.connector.OpencatBusinessConnectorException;
import dk.dbc.updateservice.dto.DoubleRecordFrontendDTO;
import dk.dbc.updateservice.dto.DoubleRecordFrontendStatusDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.utils.MDCUtil;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.MDC;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.xml.bind.JAXBException;
import java.io.UnsupportedEncodingException;
import java.util.Properties;

import static dk.dbc.updateservice.utils.MDCUtil.MDC_TRACKING_ID_LOG_CONTEXT;

/**
 * Action to check a record for double records, and if one exists return a warning to the user.
 */
public class DoubleRecordFrontendAction extends AbstractAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(DoubleRecordFrontendAction.class);

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
        final StopWatch watch = new Log4JStopWatch("opencatBusiness.checkDoubleRecordFrontend");
        ServiceResult result = null;
        try {
            final String trackingId = MDC.get(MDC_TRACKING_ID_LOG_CONTEXT);
            logger.info("Handling record: {}", LogUtils.base64Encode(state.readRecord()));
            final DoubleRecordFrontendStatusDTO doubleRecordFrontendStatusDTO = state.getOpencatBusiness().checkDoubleRecordFrontend(state.readRecord(), trackingId);
            result = doubleRecordFrontendStatusDTOToServiceResult(doubleRecordFrontendStatusDTO);
            return result;
        } catch (OpencatBusinessConnectorException | JSONBException | JAXBException | UnsupportedEncodingException e) {
            String message = String.format(state.getMessages().getString("internal.double.record.frontend.check.error"), e.getMessage());
            logger.error(message, e);
            return result = ServiceResult.newOkResult();
        } finally {
            watch.stop();
            logger.exit(result);
        }
    }

    private ServiceResult doubleRecordFrontendStatusDTOToServiceResult(DoubleRecordFrontendStatusDTO doubleRecordFrontendStatusDTO) {
        ServiceResult result;
        if ("ok".equals(doubleRecordFrontendStatusDTO.getStatus())) {
            result = ServiceResult.newOkResult();
        } else if ("doublerecord".equals(doubleRecordFrontendStatusDTO.getStatus())) {
            result = new ServiceResult();
            for (DoubleRecordFrontendDTO doubleRecordFrontendDTO : doubleRecordFrontendStatusDTO.getDoubleRecordFrontendDTOs()) {
                result.addServiceResult(ServiceResult.newDoubleRecordErrorResult(UpdateStatusEnumDTO.FAILED, doubleRecordFrontendDTO));
            }
            result.setDoubleRecordKey(state.getUpdateStore().getNewDoubleRecordKey());
        } else {
            String msg = "Unknown error";
            if (doubleRecordFrontendStatusDTO.getDoubleRecordFrontendDTOs() != null && !doubleRecordFrontendStatusDTO.getDoubleRecordFrontendDTOs().isEmpty()) {
                msg = doubleRecordFrontendStatusDTO.getDoubleRecordFrontendDTOs().get(0).getMessage();
            }
            result = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, msg);
        }
        return result;
    }

    @Override
    public void setupMDCContext() {
        MDCUtil.setupContextForRecord(state.readRecord());
    }
}