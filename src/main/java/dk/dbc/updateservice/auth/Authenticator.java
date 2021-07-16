/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.auth;

import dk.dbc.forsrights.client.ForsRights;
import dk.dbc.forsrights.client.ForsRightsException;
import dk.dbc.updateservice.actions.GlobalActionState;
import dk.dbc.updateservice.update.JNDIResources;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.servlet.http.HttpServletRequest;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.MessageContext;
import java.util.Enumeration;
import java.util.Properties;

/**
 * EJB to authenticate users against the forsrights service.
 */
@Stateless
@LocalBean
public class Authenticator {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(Authenticator.class);

    private final Properties settings = JNDIResources.getProperties();

    @EJB
    private ForsService forsService;

    /**
     * Calls the forsrights service and checks if the user has the proper rights.
     *
     * @param state {@link GlobalActionState}
     * @return <code>true</code> if the user is authenticated, <code>false</code>
     * otherwise.
     * @throws AuthenticatorException AuthenticatorException
     */
    public boolean authenticateUser(GlobalActionState state) throws AuthenticatorException {
        LOGGER.entry(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId(), state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), "****");
        boolean result = false;
        try {
            String endpoint = settings.get(JNDIResources.FORSRIGHTS_URL).toString();
            LOGGER.debug("Using endpoint to forsrights webservice: {}", endpoint);
            ForsRights.RightSet rights;
            Object useIpSetting = settings.get(JNDIResources.AUTH_USE_IP);
            if (useIpSetting != null && Boolean.parseBoolean(useIpSetting.toString())) {
                String ipAddress;
                if (state.getRequest() != null) {
                    ipAddress = getRemoteAddrFromMessage(state.getRequest());
                    LOGGER.info("jax-rs service detected. wsContext not used. Clients Ip is:{}", ipAddress);
                } else {
                    ipAddress = getRemoteAddrFromMessage(state.getWsContext());
                    LOGGER.info("Soap service. wsContext used. Ip is: {}", ipAddress);
                }
                rights = forsService.forsRightsWithIp(state, ipAddress);
            } else {
                rights = forsService.forsRights(state);
            }
            String productName = settings.getProperty(JNDIResources.AUTH_PRODUCT_NAME);
            LOGGER.debug("Looking for product name: {}", productName);
            return result = rights.hasRightName(productName);
        } catch (ForsRightsException ex) {
            LOGGER.error("Caught exception:", ex);
            throw new AuthenticatorException(ex.getMessage(), ex);
        } finally {
            LOGGER.exit(result);
        }
    }

    private String getRemoteAddrFromMessage(HttpServletRequest request) {
        final String X_FORWARDED_FOR = "x-forwarded-for";
        String result = "";
        try {
            String xForwaredForHeaderName = "";
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();

                LOGGER.debug("Checking header: '{}'", name);
                if (name.equalsIgnoreCase(X_FORWARDED_FOR)) {
                    xForwaredForHeaderName = name;
                    break;
                }
            }
            if (xForwaredForHeaderName.isEmpty()) {
                LOGGER.debug("No header for '{}' found. Using client address from request: {}", X_FORWARDED_FOR, request.getRemoteAddr());

                result = request.getRemoteAddr();
                return result;
            }
            String xForwardedForValue = request.getHeader(xForwaredForHeaderName);
            LOGGER.debug("Found header for '{}' -> '{}'", X_FORWARDED_FOR, xForwardedForValue);
            int index = xForwardedForValue.indexOf(",");
            if (index > -1) {
                result = xForwardedForValue.substring(0, index);
                return result;
            }
            result = xForwardedForValue;
            return result;
        } finally {
            LOGGER.exit(result);
        }
    }

    private String getRemoteAddrFromMessage(WebServiceContext wsContext) {
        LOGGER.entry(wsContext);
        MessageContext mc = wsContext.getMessageContext();
        HttpServletRequest req = (HttpServletRequest) mc.get(MessageContext.SERVLET_REQUEST);
        return getRemoteAddrFromMessage(req);
    }
}
