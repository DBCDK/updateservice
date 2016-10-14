package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.updateservice.dto.UpdateStatusEnumDto;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.Properties;

/**
 * Action to check that the validate scheme name from the request is a valid
 * name.
 * <p>
 * The action is using a Scripter to call JavaScript code to validate the
 * schema name.
 * </p>
 */
public class ValidateSchemaAction extends AbstractAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(ValidateSchemaAction.class);
    private final XLogger bizLogger = XLoggerFactory.getXLogger(BusinessLoggerFilter.LOGGER_NAME);

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
        logger.entry();
        ServiceResult result = null;
        validateData();
        try {
            if (state.getSchemaName() == null) {
                return result = ServiceResult.newErrorResult(UpdateStatusEnumDto.FAILED, "validateSchema must not be empty", state);
            }
            if (state.getUpdateServiceRequestDto().getAuthenticationDto().getGroupId() == null) {
                return result = ServiceResult.newErrorResult(UpdateStatusEnumDto.FAILED, "groupId must not be empty", state);
            }
            Object jsResult = state.getScripter().callMethod("checkTemplate", state.getSchemaName(), state.getUpdateServiceRequestDto().getAuthenticationDto().getGroupId(), settings);
            logger.debug("Result from checkTemplate JS ({}): {}", jsResult.getClass().getName(), jsResult);
            if (jsResult instanceof Boolean) {
                Boolean validateSchemaFound = (Boolean) jsResult;
                if (validateSchemaFound) {
                    bizLogger.info("Validating schema '{}' successfully", state.getSchemaName());
                    return result = ServiceResult.newOkResult();
                }
                bizLogger.error("Validating schema '{}' failed", state.getSchemaName());
                String message = String.format(state.getMessages().getString("update.schema.not.found"), state.getSchemaName());
                return result = ServiceResult.newErrorResult(UpdateStatusEnumDto.FAILED, message, state);
            }
            String message = String.format("The JavaScript function %s must return a boolean value.", "checkTemplate");
            bizLogger.info("Validating schema '{}'. Executing error: {}", state.getSchemaName(), message);
            return result = ServiceResult.newErrorResult(UpdateStatusEnumDto.FAILED, message, state);
        } catch (ScripterException ex) {
            bizLogger.info("Validating schema '{}'. Executing error: {}", state.getSchemaName(), ex.getMessage());
            return result = ServiceResult.newErrorResult(UpdateStatusEnumDto.FAILED, ex.getMessage(), state);
        } finally {
            logger.exit(result);
        }
    }

    private void validateData() {
        if (state.getScripter() == null) {
            throw new IllegalArgumentException("scripter must not be null");
        }
        if (settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }
    }

    @Override
    public void setupMDCContext() {
    }
}
