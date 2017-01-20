package dk.dbc.updateservice.auth;

import dk.dbc.forsrights.client.ForsRights;
import dk.dbc.forsrights.client.ForsRightsException;
import dk.dbc.updateservice.actions.GlobalActionState;
import dk.dbc.updateservice.dto.MessageEntryDTO;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.ws.JNDIResources;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.type.TypeFactory;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.servlet.http.HttpServletRequest;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.MessageContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

/**
 * EJB to authenticate users against the forsrights service.
 */
@Stateless
@LocalBean
public class Authenticator {
    private static final XLogger logger = XLoggerFactory.getXLogger(Authenticator.class);

    @Resource(lookup = JNDIResources.SETTINGS_NAME)
    private Properties settings;

    @SuppressWarnings("EjbEnvironmentInspection")
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
        logger.entry(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId(), state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), "****");
        boolean result = false;
        try {
            String endpoint = settings.get(JNDIResources.FORSRIGHTS_URL_KEY).toString();
            logger.debug("Using endpoint to forsrights webservice: {}", endpoint);
            ForsRights.RightSet rights;
            Object useIpSetting = settings.get(JNDIResources.AUTH_USE_IP_KEY);
            if (useIpSetting != null && Boolean.valueOf(useIpSetting.toString())) {
                String ipAddress = getRemoteAddrFromMessage(state.getWsContext());
                rights = forsService.forsRightsWithIp(state, ipAddress);
            } else {
                rights = forsService.forsRights(state);
            }
            String productName = settings.getProperty(JNDIResources.AUTH_PRODUCT_NAME_KEY);
            logger.debug("Looking for product name: {}", productName);
            return result = rights.hasRightName(productName);
        } catch (ForsRightsException ex) {
            logger.error("Caught exception:", ex);
            throw new AuthenticatorException(ex.getMessage(), ex);
        } finally {
            logger.exit(result);
        }
    }

    public List<MessageEntryDTO> authenticateRecord(GlobalActionState state) throws ScripterException {
        logger.entry(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId(), state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId());
        List<MessageEntryDTO> result = new ArrayList<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            Object jsResult = state.getScripter().callMethod("authenticateRecord", mapper.writeValueAsString(state.readRecord()), state.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId(), state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), settings);
            logger.debug("Result from authenticateRecord JS ({}): {}", jsResult.getClass().getName(), jsResult);
            if (jsResult instanceof String) {
                // TODO: HUST RET JAVASCRIPT OGSÅ
                List<MessageEntryDTO> validationErrors = mapper.readValue(jsResult.toString(), TypeFactory.defaultInstance().constructCollectionType(List.class, MessageEntryDTO.class));
                result.addAll(validationErrors);
                logger.trace("Number of errors: {}", result.size());
                return result;
            }
            throw new ScripterException(String.format("The JavaScript function %s must return a String value.", "authenticateRecord"));
        } catch (IOException ex) {
            throw new ScripterException(ex.getMessage(), ex);
        } finally {
            logger.exit(result);
        }
    }

    private String getRemoteAddrFromMessage(WebServiceContext wsContext) {
        logger.entry(wsContext);
        final String X_FORWARDED_FOR = "x-forwarded-for";
        String result = "";
        try {
            MessageContext mc = wsContext.getMessageContext();
            HttpServletRequest req = (HttpServletRequest) mc.get(MessageContext.SERVLET_REQUEST);

            String XForwaredForHeaderName = "";
            Enumeration<String> headerNames = req.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();

                logger.debug("Checking header: '{}'", name);
                if (name.toLowerCase().equals(X_FORWARDED_FOR.toLowerCase())) {
                    XForwaredForHeaderName = name;
                    break;
                }
            }
            if (XForwaredForHeaderName.isEmpty()) {
                logger.debug("No header for '{}' found. Using client address from request: {}", X_FORWARDED_FOR, req.getRemoteAddr());

                result = req.getRemoteAddr();
                return result;
            }
            String XForwardedForValue = req.getHeader(XForwaredForHeaderName);
            logger.debug("Found header for '{}' -> '{}'", X_FORWARDED_FOR, XForwardedForValue);
            int index = XForwardedForValue.indexOf(",");
            if (index > -1) {
                result = XForwardedForValue.substring(0, index);
                return result;
            }
            result = XForwardedForValue;
            return result;
        } finally {
            logger.exit(result);
        }
    }
}
