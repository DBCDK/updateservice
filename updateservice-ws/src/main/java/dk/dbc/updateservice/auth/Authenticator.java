//-----------------------------------------------------------------------------
package dk.dbc.updateservice.auth;

//-----------------------------------------------------------------------------

import com.google.gson.Gson;
import dk.dbc.forsrights.service.ForsRightsResponse;
import dk.dbc.forsrights.service.Ressource;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.iscrumjs.ejb.JSEngine;
import dk.dbc.iscrumjs.ejb.JavaScriptException;
import dk.dbc.updateservice.ws.JNDIResources;
import dk.dbc.updateservice.ws.ValidationError;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Stateful;
import javax.servlet.http.HttpServletRequest;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.MessageContext;
import java.io.IOException;
import java.util.*;

//-----------------------------------------------------------------------------
/**
 * EJB to authenticate users against the forsrights service.
 */
@Stateful
@LocalBean
public class Authenticator {
    //-------------------------------------------------------------------------
    //              Java EE
    //-------------------------------------------------------------------------

    //!\name Construction
    //@{
    @PostConstruct
    public void init() {
        logger.entry();

        try {
            logger.info("Classpath: {}", System.getProperty("classpath"));

            if (jsProvider != null) {
                try {
                    jsProvider.initialize(IOUtils.loadProperties(Authenticator.class.getClassLoader(),
                            ";", "dk/dbc/updateservice/ws/settings.properties",
                            "javascript/iscrum/settings.properties"));
                } catch (IOException | IllegalArgumentException ex) {
                    logger.catching(XLogger.Level.WARN, ex);
                }
            }
        }
        finally {
            logger.exit();
        }
    }
    //@}

    //-------------------------------------------------------------------------
    //              Business logic
    //-------------------------------------------------------------------------

    /**
     * Calls the forsrights service and checks if the user has the proper rights.
     *
     * @param wsContext WebServiceContext
     * @param userId    User id
     * @param groupId   Group id
     * @param passwd    Users password
     *
     * @return <code>true</code> if the user is authenticated, <code>false</code>
     *         otherwise.
     * @throws AuthenticatorException AuthenticatorException
     */
    public boolean authenticateUser( WebServiceContext wsContext, String userId, String groupId, String passwd ) throws AuthenticatorException {
        logger.entry( userId, groupId, "****" );

        boolean result = false;
        try {
            String endpoint = settings.get(JNDIResources.FORSRIGHTS_URL_KEY).toString();
            logger.debug("Using endpoint to forsrights webservice: {}", endpoint);

            ForsService service = new ForsService( endpoint );
            ForsRightsResponse response;

            Object useIpSetting = settings.get( JNDIResources.AUTH_USE_IP_KEY );
            if( useIpSetting != null && Boolean.valueOf( useIpSetting.toString() ) ) {
                String ipAddress = getRemoteAddrFromMessage( wsContext );
                response = service.forsRightsWithIp( userId, groupId, passwd, ipAddress );
            }
            else {
                response = service.forsRights( userId, groupId, passwd );
            }

            if (response.getError() != null) {
                logger.debug( "Response contains an authentication error." );
            }
            else if (response.getRessource() == null) {
                logger.debug( "Response contains no resources." );
            }
            else {
                String productName = settings.getProperty(JNDIResources.AUTH_PRODUCT_NAME_KEY);
                logger.debug( "Looking for product name: {}", productName );

                for (Ressource res : response.getRessource()) {
                    logger.debug( "Checking product name: {}", res.getName() );

                    if (res.getName().equals(productName)) {
                        logger.debug( "Found correct product in resources." );
                        result = true;
                        break;
                    }
                }
            }

            return result;
        }
        catch( RuntimeException ex ) {
            logger.error( "Caught exception:", ex );
            throw new AuthenticatorException( ex.getMessage(), ex );
        }
        finally {
            logger.exit( result );
        }
    }

    public List<ValidationError> authenticateRecord( MarcRecord record, String userId, String groupId ) throws JavaScriptException {
        logger.entry( record, userId, groupId );
        List<ValidationError> result = new ArrayList<>();

        try {
            Object jsResult;
            try {
                jsResult = jsProvider.callEntryPoint( "authenticateRecord", new Gson().toJson( record ), userId, groupId );
            }
            catch( IllegalStateException ex ) {
                logger.error( "Error when executing JavaScript to authenticate the record", ex );
                jsResult = "";
            }

            logger.trace( "Result from JS ({}): {}", jsResult.getClass().getName(), jsResult );

            if( jsResult instanceof String ) {
                Gson gson = new Gson();
                ValidationError[] validationErrors = gson.fromJson( jsResult.toString(), ValidationError[].class );

                result.addAll( Arrays.asList( validationErrors ) );
                logger.trace( "Number of errors: {}", result.size() );

                return result;
            }

            throw new JavaScriptException( String.format( "The JavaScript function %s must return a String value.", "authenticateRecord" ) );
        }
        finally {
            logger.exit( result );
        }
    }

    //-------------------------------------------------------------------------
    //              Helpers
    //-------------------------------------------------------------------------

    private String getRemoteAddrFromMessage( WebServiceContext wsContext ) {
        logger.entry( wsContext );

        final String X_FORWARDED_FOR = "x-forwarded-for";
        String result = "";
        try {
            MessageContext mc = wsContext.getMessageContext();
            HttpServletRequest req = (HttpServletRequest) mc.get(MessageContext.SERVLET_REQUEST);

            String XForwaredForHeaderName = "";
            Enumeration<String> headerNames = req.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();

                logger.debug( "Checking header: '{}'", name );
                if (name.toLowerCase().equals( X_FORWARDED_FOR.toLowerCase() ) ) {
                    XForwaredForHeaderName = name;
                    break;
                }
            }

            if (XForwaredForHeaderName.isEmpty()) {
                logger.debug( "No header for '{}' found. Using client address from request: {}", X_FORWARDED_FOR, req.getRemoteAddr() );

                result = req.getRemoteAddr();
                return result;
            }

            String XForwardedForValue = req.getHeader( XForwaredForHeaderName );
            logger.debug( "Found header for '{}' -> '{}'", X_FORWARDED_FOR, XForwardedForValue );

            int index = XForwardedForValue.indexOf( "," );
            if( index > -1 ) {
                result = XForwardedForValue.substring( 0, index );
                return result;
            }

            result = XForwardedForValue;
            return result;
        }
        finally {
            logger.exit( result );
        }
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final XLogger logger = XLoggerFactory.getXLogger( Authenticator.class );

    /**
     * Resource to lookup the product name for authentication.
     */
    @Resource( lookup = JNDIResources.SETTINGS_NAME )
    private Properties settings;

    @EJB
    private JSEngine jsProvider;
}
