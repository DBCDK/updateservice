//-----------------------------------------------------------------------------
package dk.dbc.updateservice.auth;

//-----------------------------------------------------------------------------
import dk.dbc.forsrights.service.ForsRightsResponse;
import dk.dbc.forsrights.service.Ressource;
import dk.dbc.updateservice.ws.JNDIResources;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.Resource;
import javax.ejb.LocalBean;
import javax.ejb.Stateful;
import javax.ejb.Stateless;
import javax.servlet.http.HttpServletRequest;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.MessageContext;
import java.util.Properties;

//-----------------------------------------------------------------------------
/**
 * EJB to authenticate users against the forsrights service.
 */
@Stateful
@LocalBean
public class Authenticator {
    //-------------------------------------------------------------------------
    //              Business logic
    //-------------------------------------------------------------------------

    /**
     * Calls the forsrights service and checks if the user has the proper rights.
     *
     * @param userId  User id.
     * @param groupId Group id.
     * @param passwd  Users password.
     *
     * @return <code>true</code> if the user is authenticated, <code>false</code>
     *         otherwise.
     */
    public boolean authenticateUser( WebServiceContext wsContext, String userId, String groupId, String passwd ) {
        logger.entry( userId, groupId, "****" );

        boolean result = false;
        try {
            String endpoint = settings.get(JNDIResources.FORSRIGHTS_URL_KEY).toString();
            logger.debug("Using endpoint to forsrights webservice: {}", endpoint);

            ForsService service = new ForsService( endpoint );
            ForsRightsResponse response;

            Object useIpSetting = settings.get( JNDIResources.AUTH_USE_IP_KEY );
            if( useIpSetting != null && Boolean.valueOf( useIpSetting.toString() ) ) {
                MessageContext mc = wsContext.getMessageContext();
                HttpServletRequest req = (HttpServletRequest)mc.get( MessageContext.SERVLET_REQUEST );

                response = service.forsRightsWithIp( userId, groupId, passwd, req.getRemoteAddr() );
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
}
