package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.updateservice.auth.AuthenticatorException;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

/**
 * Action to authenticate the user from the request.
 * <p>
 * The authentication is done against the forsright webservice by the parsed
 * Authenticator EJB.
 * </p>
 */
public class AuthenticateUserAction extends AbstractAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(AuthenticateUserAction.class);
    private static final XLogger bizLogger = XLoggerFactory.getXLogger(BusinessLoggerFilter.LOGGER_NAME);

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
            if (state.getUpdateServiceRequestDto().getAuthenticationDto() == null) {
                // TODO: MOVE TO RESOURCEBUNDLE
                msg = "Authentication arguments is missing in the request.";
                bizLogger.error(msg);
                return result = ServiceResult.newAuthErrorResult(state, msg);
            }
            if (state.getUpdateServiceRequestDto().getAuthenticationDto().getUserId() == null) {
                // TODO: MOVE TO RESOURCEBUNDLE
                msg = "User name is missing in authentication arguments in the request";
                bizLogger.error(msg);
                return result = ServiceResult.newAuthErrorResult(state, msg);
            }
            if (state.getUpdateServiceRequestDto().getAuthenticationDto().getGroupId() == null) {
                // TODO: MOVE TO RESOURCEBUNDLE
                msg = "Group name is missing in authentication arguments in the request";
                bizLogger.error(msg);
                return result = ServiceResult.newAuthErrorResult(state, msg);
            }
            if (state.getUpdateServiceRequestDto().getAuthenticationDto().getPassword() == null) {
                // TODO: MOVE TO RESOURCEBUNDLE
                msg = "Password is missing in authentication arguments in the request";
                bizLogger.error(msg);
                return result = ServiceResult.newAuthErrorResult(state, msg);
            }
            if (state.getAuthenticator().authenticateUser(state)) {
                bizLogger.info("User {}/{} is authenticated successfully", state.getUpdateServiceRequestDto().getAuthenticationDto().getGroupId(), state.getUpdateServiceRequestDto().getAuthenticationDto().getUserId());
                return result = ServiceResult.newOkResult();
            }
            bizLogger.error("User {}/{} could not be authenticated", state.getUpdateServiceRequestDto().getAuthenticationDto().getGroupId(), state.getUpdateServiceRequestDto().getAuthenticationDto().getUserId());
            return result = ServiceResult.newAuthErrorResult(state);
        } catch (AuthenticatorException ex) {
            String message = String.format(state.getMessages().getString("authentication.error"), ex.getMessage());
            logger.error(message, ex);
            bizLogger.error("Critical error in authenticating user {}/{}: {}", state.getUpdateServiceRequestDto().getAuthenticationDto().getGroupId(), state.getUpdateServiceRequestDto().getAuthenticationDto().getUserId(), ex.getMessage());
            return result = ServiceResult.newAuthErrorResult(state);
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
