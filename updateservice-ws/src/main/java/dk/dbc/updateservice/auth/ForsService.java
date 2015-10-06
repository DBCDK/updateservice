//-----------------------------------------------------------------------------
package dk.dbc.updateservice.auth;

import dk.dbc.forsrights.client.ForsRights;
import dk.dbc.forsrights.client.ForsRightsException;
import dk.dbc.forsrights.client.ForsRightsServiceFromURL;
import dk.dbc.forsrights.service.ForsRightsPortType;
import dk.dbc.forsrights.service.ForsRightsRequest;
import dk.dbc.forsrights.service.ForsRightsResponse;
import dk.dbc.forsrights.service.ForsRightsService;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.updateservice.ws.JNDIResources;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.Singleton;
import javax.xml.ws.BindingProvider;
import java.util.Properties;

//-----------------------------------------------------------------------------
/**
* This class encapsulate calls to the Forsrights service using SOAP.
* 
* @author stp
*/
@Singleton
@ConcurrencyManagement( ConcurrencyManagementType.BEAN )
public class ForsService {
    @PostConstruct
    public void init() {
        forsRightsCache = new ForsRights.RightsCache( CACHE_ENTRY_TIMEOUT );

        ForsRightsServiceFromURL.Builder builder = ForsRightsServiceFromURL.builder();
        builder = builder.connectTimeout( CONNECT_TIMEOUT ).requestTimeout( REQUEST_TIMEOUT );

        forsRights = builder.build( settings.getProperty( JNDIResources.FORSRIGHTS_URL_KEY ) ).forsRights( forsRightsCache );
    }

    /**
     * Calls the forsrights
     *
     * @param userId  User id.
     * @param groupId Group id.
     * @param passwd  Password.
     *
     * @return A response from forsrights.
     */
    public ForsRights.RightSet forsRights( String userId, String groupId, String passwd ) throws ForsRightsException {
        logger.entry( userId, groupId, "****" );

        try {
            bizLogger.info( "Authenticating user {}/{} against forsright at {}", userId, groupId, settings.getProperty( JNDIResources.FORSRIGHTS_URL_KEY ) );
            return forsRights.lookupRight( userId, groupId, passwd, null );
        }
        finally {
            logger.exit();
        }
    }

	/**
	 * Calls the forsrights with an IP address.
	 * 
	 * @param userId    User id.
	 * @param groupId   Group id.
	 * @param passwd    Password.
     * @param ipAddress IP-address from the caller of this web service.
	 * 
	 * @return A response from forsrights.
	 */
	public ForsRights.RightSet forsRightsWithIp( String userId, String groupId, String passwd, String ipAddress ) throws ForsRightsException {
        logger.entry( userId, groupId, "****", ipAddress );

        try {
            bizLogger.info( "Authenticating user {}/{} with ip-address {} against forsright at {}", userId, groupId, ipAddress, settings.getProperty( JNDIResources.FORSRIGHTS_URL_KEY ) );
            return forsRights.lookupRight( userId, groupId, passwd, ipAddress );
        }
        finally {
            logger.exit();
        }
	}

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final XLogger logger = XLoggerFactory.getXLogger( ForsService.class );
    private static final XLogger bizLogger = XLoggerFactory.getXLogger( BusinessLoggerFilter.LOGGER_NAME );
    private static final long CACHE_ENTRY_TIMEOUT =  10 * 60 * 1000;
    private static final int CONNECT_TIMEOUT =  1 * 60 * 1000;
    private static final int REQUEST_TIMEOUT =  3 * 60 * 1000;

    /**
     * Resource to lookup the product name for authentication.
     */
    @Resource( lookup = JNDIResources.SETTINGS_NAME )
    private Properties settings;

    private ForsRights.RightsCache forsRightsCache;
    private ForsRights forsRights;
}
