package dk.dbc.updateservice.actions;

import dk.dbc.commons.jsonb.JSONBException;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.opencat.connector.OpencatBusinessConnectorException;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.utils.DeferredLogger;
import dk.dbc.updateservice.utils.MDCUtil;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.MDC;

import java.util.Properties;

import static dk.dbc.updateservice.rest.ApplicationConfig.LOG_DURATION_THRESHOLD_MS;
import static dk.dbc.updateservice.utils.MDCUtil.MDC_TRACKING_ID_LOG_CONTEXT;

/**
 * Action to check a record for double records.
 */
public class DoubleRecordCheckingAction extends AbstractAction {
    private static final DeferredLogger LOGGER = new DeferredLogger(DoubleRecordCheckingAction.class);
    MarcRecord record;
    Properties settings;

    public DoubleRecordCheckingAction(GlobalActionState globalActionState, Properties properties, MarcRecord marcRecord) {
        super(DoubleRecordCheckingAction.class.getSimpleName(), globalActionState);
        settings = properties;
        record = marcRecord;
    }

    /**
     * Performs this actions and may create any child actions.
     *
     * @return A ServiceResult to be reported in the web service response.
     * @throws UpdateException In case of an error.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        return LOGGER.callChecked(log -> {
            final StopWatch watch = new Log4JStopWatch("opencatBusiness.checkDoubleRecord").setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
            try {
                final String trackingId = MDC.get(MDC_TRACKING_ID_LOG_CONTEXT);
                state.getOpencatBusiness().checkDoubleRecord(record, trackingId);
                return ServiceResult.newOkResult();
            } catch (OpencatBusinessConnectorException | JSONBException ex) {
                final String message = String.format(state.getMessages().getString("internal.double.record.check.error"), ex.getMessage());
                log.error(message, ex);
                return ServiceResult.newOkResult();
            } finally {
                watch.stop();
            }
        });
    }

    @Override
    public void setupMDCContext() {
        MDCUtil.setupContextForRecord(record);
    }
}
