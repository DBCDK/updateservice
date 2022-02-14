package dk.dbc.updateservice.auth;

import dk.dbc.idp.connector.IDPConnector;
import dk.dbc.idp.connector.IDPConnectorException;
import dk.dbc.updateservice.dto.AuthenticationDTO;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.inject.Inject;

/**
 * EJB to authenticate users against the idp service.
 */
@Stateless
@LocalBean
public class Authenticator {
    private static final Logger LOGGER = LoggerFactory.getLogger(Authenticator.class);


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
     * @param authenticationDTO {@link AuthenticationDTO}
     * @return <code>true</code> if the user is authenticated, <code>false</code>
     * otherwise.
     * @throws AuthenticatorException if there are problems communicating with the identity service
     */
    public boolean authenticateUser(AuthenticationDTO authenticationDTO) throws AuthenticatorException {
        final StopWatch watch = new Log4JStopWatch("service.idp.lookupRight");
        try {
            final IDPConnector.RightSet rights = idpConnector.lookupRight(authenticationDTO.getUserId(), authenticationDTO.getGroupId(), authenticationDTO.getPassword());

            LOGGER.debug("Looking for product name '{}' with '{}' permission", authProductName, authProductRight);
            return rights.hasRight(authProductName, authProductRight);
        } catch (IDPConnectorException ex) {
            LOGGER.error("Caught exception:", ex);
            throw new AuthenticatorException(ex.getMessage(), ex);
        } finally {
            watch.stop();
        }
    }

}
