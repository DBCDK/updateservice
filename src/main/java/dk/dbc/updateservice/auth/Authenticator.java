package dk.dbc.updateservice.auth;

import dk.dbc.idp.connector.IDPConnector;
import dk.dbc.idp.connector.IDPConnectorException;
import dk.dbc.login.DBCLoginConnector;
import dk.dbc.login.DBCLoginConnectorException;
import dk.dbc.login.dto.Right;
import dk.dbc.login.dto.UserInfo;
import dk.dbc.updateservice.dto.AuthenticationDTO;
import dk.dbc.updateservice.utils.DeferredLogger;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;

import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import java.util.List;

import static dk.dbc.updateservice.rest.ApplicationConfig.LOG_DURATION_THRESHOLD_MS;

/**
 * EJB to authenticate users against the idp service.
 */
@Stateless
@LocalBean
public class Authenticator {
    private static final DeferredLogger LOGGER = new DeferredLogger(Authenticator.class);

    @Inject
    private IDPConnector idpConnector;

    @Inject
    private DBCLoginConnector dbcLoginConnector;

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
        final StopWatch watch = new Log4JStopWatch("service.idp.lookupRight").setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        return LOGGER.callChecked(log -> {
            try {
                final IDPConnector.RightSet rights = idpConnector.lookupRight(authenticationDTO.getUserId(), authenticationDTO.getGroupId(), authenticationDTO.getPassword());
                log.debug("Looking for product name '{}' with '{}' permission", authProductName, authProductRight);
                return rights.hasRight(authProductName, authProductRight);
            } catch (IDPConnectorException ex) {
                throw new AuthenticatorException("Caught exception while authenticating : " + authenticationDTO.getUserId(), ex);
            } finally {
                watch.stop();
            }
        });
    }

    public AuthenticationDTO authenticateUser(String bearerToken) throws AuthenticatorException {
        final StopWatch watch = new Log4JStopWatch("service.login.bib.dk").setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        try {
            UserInfo userInfo = dbcLoginConnector.userinfo(bearerToken);
            if (userInfo != null
                    && userInfo.getAttributes() != null
                    && userInfo.getAttributes().getDbcidp() != null
                    && !userInfo.getAttributes().getDbcidp().isEmpty()
                    && userInfo.getAttributes().getDbcidp().get(0).getRights() != null) {
                final List<Right> rights = userInfo.getAttributes().getDbcidp().get(0).getRights();
                for (Right right : rights) {
                    if (authProductName.equals(right.getProductName()) && authProductRight.equals(right.getName())) {
                        final AuthenticationDTO authenticationDTO = new AuthenticationDTO();
                        authenticationDTO.setUserId(userInfo.getAttributes().getUserId());
                        authenticationDTO.setGroupId(userInfo.getAttributes().getNetpunktAgency());

                        return authenticationDTO;
                    }
                }
            }

            return null;
        } catch (DBCLoginConnectorException ex) {
            throw new AuthenticatorException("Unable to authenticate bearer token", ex);
        } finally {
            watch.stop();
        }
    }

}
