/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.updateservice.utils.ResourceBundles;
import dk.dbc.updateservice.auth.AuthenticatorException;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.ResourceBundle;

/**
 * Action to authenticate the user from the request.
 * <p>
 * The authentication is done against the forsright webservice by the parsed
 * Authenticator EJB.
 * </p>
 */
public class AuthenticateUserAction extends AbstractAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(AuthenticateUserAction.class);

    public AuthenticateUserAction(GlobalActionState globalActionState) {
        super(AuthenticateUserAction.class.getSimpleName(), globalActionState);
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
        try {
            validateNullableData();
            String msg;
            ResourceBundle resourceBundle = ResourceBundles.getBundle("messages");

            if (state.getUpdateServiceRequestDTO().getAuthenticationDTO() == null) {
                msg = resourceBundle.getString("auth.user.missing.arguments");
                logger.error(msg);
                return result = ServiceResult.newAuthErrorResult(msg);
            }
            if (state.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId() == null) {
                msg = resourceBundle.getString("auth.user.missing.username");
                logger.error(msg);
                return result = ServiceResult.newAuthErrorResult(msg);
            }
            if (state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId() == null) {
                msg = resourceBundle.getString("auth.user.missing.groupname");
                logger.error(msg);
                return result = ServiceResult.newAuthErrorResult(msg);
            }
            if (state.getUpdateServiceRequestDTO().getAuthenticationDTO().getPassword() == null) {
                msg = resourceBundle.getString("auth.user.missing.password");
                logger.error(msg);
                return result = ServiceResult.newAuthErrorResult(msg);
            }
            if (state.getAuthenticator().authenticateUser(state)) {
                logger.info("User {}/{} is authenticated successfully", state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), state.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId());
                return result = ServiceResult.newOkResult();
            }

            logger.error("User {}/{} could not be authenticated", state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), state.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId());

            return result = ServiceResult.newAuthErrorResult();
        } catch (AuthenticatorException ex) {
            String message = String.format(state.getMessages().getString("authentication.error"), ex.getMessage());
            logger.error(message, ex);
            logger.error("Critical error in authenticating user {}/{}: {}", state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), state.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId(), ex.getMessage());
            return result = ServiceResult.newAuthErrorResult();
        } finally {
            logger.exit(result);
        }
    }

    private void validateNullableData() {
        if (state == null) {
            throw new IllegalArgumentException("State object cannot be empty");
        }
        if (state.getAuthenticator() == null) {
            throw new IllegalArgumentException("Authenticator is obligatory");
        }
    }

    @Override
    public void setupMDCContext() {}
}
