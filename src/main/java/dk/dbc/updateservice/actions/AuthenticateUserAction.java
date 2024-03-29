package dk.dbc.updateservice.actions;

import dk.dbc.updateservice.auth.AuthenticatorException;
import dk.dbc.updateservice.dto.AuthenticationDTO;
import dk.dbc.updateservice.update.JNDIResources;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.utils.DeferredLogger;
import dk.dbc.updateservice.utils.ResourceBundles;

import java.util.List;
import java.util.Properties;
import java.util.ResourceBundle;

/**
 * Action to authenticate the user from the request.
 * <p>
 * The authentication is done against the IDP webservice by the parsed
 * Authenticator EJB.
 * </p>
 */
public class AuthenticateUserAction extends AbstractAction {
    private static final DeferredLogger LOGGER = new DeferredLogger(AuthenticateUserAction.class);
    private final List<String> dbcOverwriteAgencies;

    public AuthenticateUserAction(GlobalActionState globalActionState, Properties properties) {
        super(AuthenticateUserAction.class.getSimpleName(), globalActionState);

        final String listAsString = properties.getProperty(JNDIResources.DBC_OVERWRITE_AGENCIES);
        dbcOverwriteAgencies = List.of(listAsString.split(";"));
    }

    /**
     * Performs this actions and may create any child actions.
     *
     * @return A list of ValidationError to be reported in the web service response.
     * @throws UpdateException In case of an error.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        if (state != null
                && state.getRequest() != null
                && state.getRequest().getHeader("Authorization") != null
                && state.getRequest().getHeader("Authorization").toLowerCase().startsWith("bearer ")) {
            final String authorizationHeader = state.getRequest().getHeader("Authorization");
            final String bearerToken = authorizationHeader.substring(7);

            return authenticateByBearerToken(bearerToken);
        } else {
            return authenticateByIDP();
        }
    }

    private ServiceResult authenticateByIDP() {
        validateNullableData();
        return LOGGER.call(log -> {
            final String msg;
            final ResourceBundle resourceBundle = ResourceBundles.getBundle("messages");

            if (state.getUpdateServiceRequestDTO().getAuthenticationDTO() == null) {
                msg = resourceBundle.getString("auth.user.missing.arguments");
                log.error(msg);
                return ServiceResult.newAuthErrorResult(msg);
            }
            if (state.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId() == null) {
                msg = resourceBundle.getString("auth.user.missing.username");
                log.error(msg);
                return ServiceResult.newAuthErrorResult(msg);
            }
            if (state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId() == null) {
                msg = resourceBundle.getString("auth.user.missing.groupname");
                log.error(msg);
                return ServiceResult.newAuthErrorResult(msg);
            }
            if (state.getUpdateServiceRequestDTO().getAuthenticationDTO().getPassword() == null) {
                msg = resourceBundle.getString("auth.user.missing.password");
                log.error(msg);
                return ServiceResult.newAuthErrorResult(msg);
            }

            final AuthenticationDTO authenticationDTOToUse = getAuthenticationDTO(state.getUpdateServiceRequestDTO().getAuthenticationDTO());
            try {
                if (state.getAuthenticator().authenticateUser(authenticationDTOToUse)) {
                    log.info("User {}/{} is authenticated successfully using IDP", authenticationDTOToUse.getGroupId(), authenticationDTOToUse.getUserId());
                    return ServiceResult.newOkResult();
                }

                log.error("User {}/{} could not be authenticated using IDP", authenticationDTOToUse.getGroupId(), authenticationDTOToUse.getUserId());

                return ServiceResult.newAuthErrorResult();
            } catch (AuthenticatorException ex) {
                msg = String.format(state.getMessages().getString("authentication.error"), ex.getMessage());
                log.error(msg, ex);
                log.error("Critical error in authenticating user {}/{}: {}", authenticationDTOToUse.getGroupId(), authenticationDTOToUse.getUserId(), ex.getMessage());
                return ServiceResult.newAuthErrorResult();
            }
        });
    }

    private ServiceResult authenticateByBearerToken(String bearerToken) {
        return LOGGER.call(log -> {
            try {
            final AuthenticationDTO authenticationDTO = state.getAuthenticator().authenticateUser(bearerToken);
                if (authenticationDTO != null) {
                    state.getUpdateServiceRequestDTO().setAuthenticationDTO(authenticationDTO);

                    log.info("User {}/{} is authenticated successfully using bearer token", authenticationDTO.getGroupId(), authenticationDTO.getUserId());
                    return ServiceResult.newOkResult();
                }

                log.error("User could not be authenticated using bearer token");

                return ServiceResult.newAuthErrorResult();
            } catch (AuthenticatorException ex) {
                log.error("Caught exception when calling login", ex);
                return ServiceResult.newAuthErrorResult();
            }
        });
    }

    /**
     * Sometimes we want the use different values when validating, especially the group id
     *
     * @param original AuthenticationDTO with the original input values
     * @return AuthenticationDTO with overwritten credentials
     */
    private AuthenticationDTO getAuthenticationDTO(AuthenticationDTO original) {
        return LOGGER.call(log -> {
            final AuthenticationDTO result = new AuthenticationDTO();
            result.setUserId(original.getUserId());
            result.setPassword(original.getPassword());

            if ("netpunkt-DATAIO".equals(original.getUserId())) {
                /*
                    DataIO always uses the same username and password for all agencies when calling updateservice, but
                    the groupId will match the submitter. Instead of duplicating the dataio user to every agency which
                    can possible use dataio we just have a single user, which means we have to overwrite the groupId
                    when validating against the identity provider service
                 */
                log.info("Detected dataIO username so overwriting groupId to '010100' for authentication");
                result.setGroupId("010100");
            } else if (dbcOverwriteAgencies.contains(original.getGroupId())) {
                /*
                    When users log in to dbckat they use the credentials dbc/username but when a request is made to
                    update the groupId is replaced with an agency id based on the 001 *b value of the record. As of
                    right now that agency id is always 010100. So unless we are going to duplicate users (which we are
                    not) we have to overwrite the incoming groupId if the value is 010100.
                 */
                log.info("Detected {} groupId so overwriting groupId to 'dbc' for authentication", original.getGroupId());
                result.setGroupId("dbc");
            } else {
                /*
                    No special handling, just validate with the provided credentials
                 */
                result.setGroupId(original.getGroupId());
            }

            return result;
        });
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
