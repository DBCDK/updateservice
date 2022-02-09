package dk.dbc.updateservice.auth;

import dk.dbc.idp.connector.IDPConnector;
import dk.dbc.idp.connector.IDPConnectorException;
import dk.dbc.updateservice.actions.GlobalActionState;
import dk.dbc.updateservice.dto.AuthenticationDTO;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.inject.Inject;

/**
 * EJB to authenticate users against the idp service.
 */
@Stateless
@LocalBean
public class Authenticator {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(Authenticator.class);

    @Inject
    private IDPConnector idpConnector;

    @Inject
    @ConfigProperty(name = "AUTH_PRODUCT_NAME", defaultValue = "DANBIB")
    private String authProductName;

    @Inject
    @ConfigProperty(name = "AUTH_PRODUCT_RIGHT", defaultValue = "WRITE")
    private String authProductRight;

    /**
     * Calls the idp service and checks if the user has the proper rights.
     *
     * @param state {@link GlobalActionState}
     * @return <code>true</code> if the user is authenticated, <code>false</code>
     * otherwise.
     * @throws AuthenticatorException if there are problems communicating with the identity service
     */
    public boolean authenticateUser(GlobalActionState state) throws AuthenticatorException {
        final StopWatch watch = new Log4JStopWatch("idp.authorize");
        try {
            final AuthenticationDTO authenticationDTO = state.getUpdateServiceRequestDTO().getAuthenticationDTO();
            final IDPConnector.RightSet rights = idpConnector.lookupRight(authenticationDTO.getUserId(),
                    getCorrectedGroupId(authenticationDTO.getGroupId()),
                    authenticationDTO.getPassword());
            LOGGER.debug("Looking for product name '{}' with '{}' permission", authProductName, authProductRight);
            return rights.hasRight(authProductName, authProductRight);
        } catch (IDPConnectorException ex) {
            LOGGER.error("Caught exception:", ex);
            throw new AuthenticatorException(ex.getMessage(), ex);
        } finally {
            watch.stop();
        }
    }

    /**
     * <p>
     * When users log in to dbckat they use the credentials dbc/username but when a request is made to update the groupId
     * is replaced with an agency id based on the 001 *b value of the record. As of right now that agency id is always
     * 010100. So unless we are going to duplicate users (which we are not) we have to overwrite the incoming groupId if
     * the value is 010100.
     * <p>
     * This also affects dataio
     */
    private String getCorrectedGroupId(String groupId) {
        if ("010100".equals(groupId)) {
            return "dbc";
        } else {
            return groupId;
        }
    }

}
