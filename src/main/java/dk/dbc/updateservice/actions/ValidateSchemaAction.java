/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.jsonb.JSONBException;
import dk.dbc.opencat.connector.OpencatBusinessConnectorException;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
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
                return result = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, "validateSchema must not be empty");
            }
            if (state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId() == null) {
                return result = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, "groupId must not be empty");
            }
            final boolean validateSchemaFound = state.getOpencatBusiness().checkTemplate(state.getSchemaName(), state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), state.getTemplateGroup());
            if (validateSchemaFound) {
                logger.info("Validating schema '{}' successfully", state.getSchemaName());
                return result = ServiceResult.newOkResult();
            }
            logger.error("Validating schema '{}' failed", state.getSchemaName());
            String message = String.format(state.getMessages().getString("update.schema.not.found"), state.getSchemaName());
            return result = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
        } catch (OpencatBusinessConnectorException | JSONBException ex) {
            logger.info("Validating schema '{}'. Executing error: {}", state.getSchemaName(), ex.getMessage());
            return result = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, ex.getMessage());
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
