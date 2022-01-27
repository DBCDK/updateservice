package dk.dbc.updateservice.auth;

import dk.dbc.idp.connector.IDPConnector;
import dk.dbc.idp.connector.IDPConnectorException;
import dk.dbc.updateservice.actions.GlobalActionState;
import dk.dbc.updateservice.dto.AuthenticationDTO;
import dk.dbc.updateservice.update.JNDIResources;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.Properties;

/**
 * EJB to authenticate users against the idp service.
 */
@Stateless
@LocalBean
public class Authenticator {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(Authenticator.class);

    private final Properties settings = JNDIResources.getProperties();

    @Inject
    private IDPConnector idpConnector;

    /**
     * Calls the idp service and checks if the user has the proper rights.
     *
     * @param state {@link GlobalActionState}
     * @return <code>true</code> if the user is authenticated, <code>false</code>
     * otherwise.
     * @throws IDPConnectorException idpConnectorException
     */
    public boolean authenticateUser(GlobalActionState state) throws AuthenticatorException {
        final StopWatch watch = new Log4JStopWatch("idp.authorize");
        try {
            final AuthenticationDTO authenticationDTO = state.getUpdateServiceRequestDTO().getAuthenticationDTO();
            final IDPConnector.RightSet rights = idpConnector.lookupRight(authenticationDTO.getUserId(),
                    authenticationDTO.getGroupId(),
                    authenticationDTO.getPassword());
            final String productName = settings.getProperty(JNDIResources.AUTH_PRODUCT_NAME);
            LOGGER.debug("Looking for product name: {}", productName);
            return rights.hasRightName(productName);
        } catch (IDPConnectorException ex) {
            LOGGER.error("Caught exception:", ex);
            throw new AuthenticatorException(ex.getMessage(), ex);
        } finally {
            watch.stop();
        }
    }

}
