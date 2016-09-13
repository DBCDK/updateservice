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
            if (state.getUpdateRecordRequest().getAuthentication() == null) {
                bizLogger.error("Authentication arguments is missing in the request.");
                return result = ServiceResult.newAuthErrorResult(state);
            }

            if (state.getUpdateRecordRequest().getAuthentication().getUserIdAut() == null) {
                bizLogger.error("User name is missing in authentication arguments in the request");
                return result = ServiceResult.newAuthErrorResult(state);
            }

            if (state.getUpdateRecordRequest().getAuthentication().getGroupIdAut() == null) {
                bizLogger.error("Group name is missing in authentication arguments in the request");
                return result = ServiceResult.newAuthErrorResult(state);
            }

            if (state.getUpdateRecordRequest().getAuthentication().getPasswordAut() == null) {
                bizLogger.error("Password is missing in authentication arguments in the request");
                return result = ServiceResult.newAuthErrorResult(state);
            }

            if (state.getAuthenticator().authenticateUser(state)) {
                bizLogger.info("User {}/{} is authenticated successfully", state.getUpdateRecordRequest().getAuthentication().getGroupIdAut(), state.getUpdateRecordRequest().getAuthentication().getUserIdAut());
                return result = ServiceResult.newOkResult();
            }

            bizLogger.error("User {}/{} could not be authenticated", state.getUpdateRecordRequest().getAuthentication());
            return result = ServiceResult.newAuthErrorResult(state);
        } catch (AuthenticatorException ex) {
            String message = String.format(state.getMessages().getString("authentication.error"), ex.getMessage());
            logger.error(message, ex);
            bizLogger.error("Critical error in authenticating user {}/{}: {}", state.getUpdateRecordRequest().getAuthentication().getGroupIdAut(), state.getUpdateRecordRequest().getAuthentication().getUserIdAut(), ex.getMessage());
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
    public void setupMDCContext() {
    }
}
