package dk.dbc.updateservice.actions;

import dk.dbc.commons.jsonb.JSONBException;
import dk.dbc.opencat.connector.OpencatBusinessConnectorException;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.utils.DeferredLogger;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.MDC;

import java.util.Properties;

import static dk.dbc.updateservice.rest.ApplicationConfig.LOG_DURATION_THRESHOLD_MS;
import static dk.dbc.updateservice.utils.MDCUtil.MDC_TRACKING_ID_LOG_CONTEXT;

/**
 * Action to check that the validate scheme name from the request is a valid
 * name.
 * <p>
 * The action is using a Scripter to call JavaScript code to validate the
 * schema name.
 * </p>
 */
public class ValidateSchemaAction extends AbstractAction {
    private static final DeferredLogger LOGGER = new DeferredLogger(ValidateSchemaAction.class);

    Properties settings;

    public ValidateSchemaAction(GlobalActionState globalActionState, Properties properties) {
        super(ValidateSchemaAction.class.getSimpleName(), globalActionState);
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
        final StopWatch watch = new Log4JStopWatch("opencatBusiness.checkTemplate").setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        validateData();
        return LOGGER.callChecked(log -> {
            try {
                final String trackingId = MDC.get(MDC_TRACKING_ID_LOG_CONTEXT);
                if (state.getSchemaName() == null) {
                    return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, "schemaName must not be empty");
                }

                final String groupId = state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId();
                if (groupId == null) {
                    return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, "groupId must not be empty");
                }

                if (state.getIsTemplateOverwrite()) {
                    log.info("Skipping checkTemplate() as groupId is root and template is superallowall");
                    return ServiceResult.newOkResult();
                } else {
                    final boolean validateSchemaFound = state.getOpencatBusiness().checkTemplate(state.getSchemaName(),
                            state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(),
                            state.getTemplateGroup(),
                            trackingId);
                    if (validateSchemaFound) {
                        log.info("Validating schema '{}' successfully", state.getSchemaName());
                        return ServiceResult.newOkResult();
                    }
                }
                log.error("Validating schema '{}' failed", state.getSchemaName());
                final String message = String.format(state.getMessages().getString("update.schema.not.found"), state.getSchemaName());
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
            } catch (OpencatBusinessConnectorException | JSONBException ex) {
                log.info("Validating schema '{}'. Executing error: {}", state.getSchemaName(), ex.getMessage());
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, ex.getMessage());
            } finally {
                watch.stop();
            }
        });
    }

    private void validateData() {
        if (settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }
    }

    @Override
    public void setupMDCContext() {
    }
}
