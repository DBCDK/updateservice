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
 * The authentication is done against the IDP webservice by the parsed
 * Authenticator EJB.
 * </p>
 */
public class AuthenticateUserAction extends AbstractAction {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(AuthenticateUserAction.class);

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
        try {
            validateNullableData();
            String msg;
            final ResourceBundle resourceBundle = ResourceBundles.getBundle("messages");

            if (state.getUpdateServiceRequestDTO().getAuthenticationDTO() == null) {
                msg = resourceBundle.getString("auth.user.missing.arguments");
                LOGGER.error(msg);
                return ServiceResult.newAuthErrorResult(msg);
            }
            if (state.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId() == null) {
                msg = resourceBundle.getString("auth.user.missing.username");
                LOGGER.error(msg);
                return ServiceResult.newAuthErrorResult(msg);
            }
            if (state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId() == null) {
                msg = resourceBundle.getString("auth.user.missing.groupname");
                LOGGER.error(msg);
                return ServiceResult.newAuthErrorResult(msg);
            }
            if (state.getUpdateServiceRequestDTO().getAuthenticationDTO().getPassword() == null) {
                msg = resourceBundle.getString("auth.user.missing.password");
                LOGGER.error(msg);
                return ServiceResult.newAuthErrorResult(msg);
            }
            if (state.getAuthenticator().authenticateUser(state)) {
                LOGGER.info("User {}/{} is authenticated successfully", state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), state.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId());
                return ServiceResult.newOkResult();
            }

            LOGGER.error("User {}/{} could not be authenticated", state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), state.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId());

            return ServiceResult.newAuthErrorResult();
        } catch (AuthenticatorException ex) {
            String message = String.format(state.getMessages().getString("authentication.error"), ex.getMessage());
            LOGGER.error(message, ex);
            LOGGER.error("Critical error in authenticating user {}/{}: {}", state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), state.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId(), ex.getMessage());
            return ServiceResult.newAuthErrorResult();
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
    public void setupMDCContext() {
    }
}
