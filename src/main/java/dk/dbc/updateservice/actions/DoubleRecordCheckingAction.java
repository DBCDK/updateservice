package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.utils.json.Json;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.ws.MDCUtil;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.IOException;
import java.util.Properties;

/**
 * Action to check a record for double records.
 */
public class DoubleRecordCheckingAction extends AbstractAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(DoubleRecordCheckingAction.class);
    private static final XLogger bizLogger = XLoggerFactory.getXLogger(BusinessLoggerFilter.LOGGER_NAME);
    private static final String ENTRY_POINT = "checkDoubleRecord";

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
        logger.entry();
        ServiceResult result = null;
        try {
            bizLogger.info("Handling record:\n{}", record);
            state.getScripter().callMethod(ENTRY_POINT, Json.encode(record), settings);
            return result = ServiceResult.newOkResult();
        } catch (IOException | ScripterException ex) {
            String message = String.format(state.getMessages().getString("internal.double.record.check.error"), ex.getMessage());
            logger.error(message, ex);
            return result = ServiceResult.newOkResult();
        } finally {
            logger.exit(result);
        }
    }

    @Override
    public void setupMDCContext() {
        MDCUtil.setupContextForRecord(record);
    }
}
