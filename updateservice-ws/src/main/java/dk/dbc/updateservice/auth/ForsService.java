//-----------------------------------------------------------------------------
package dk.dbc.updateservice.auth;

import javax.xml.ws.BindingProvider;

import dk.dbc.forsrights.service.ForsRightsPortType;
import dk.dbc.forsrights.service.ForsRightsRequest;
import dk.dbc.forsrights.service.ForsRightsResponse;
import dk.dbc.forsrights.service.ForsRightsService;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

//-----------------------------------------------------------------------------
/**
* This class encapsulate calls to the Forsrights service using SOAP.
* 
* @author stp
*/
public class ForsService {
    //-------------------------------------------------------------------------
    //              Constructors
    //-------------------------------------------------------------------------
	
	/**
	 * Constructs instance with a endpoint
	 * 
	 * @param endpoint The endpoint to the forsrights service.
	 */
	public ForsService( String endpoint ) {
        this.endpoint = endpoint;
        this.services = new ForsRightsService();
        this.callerProxy = getAndConfigureUpdateProxy();
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
	public ForsRightsResponse forsRights( String userId, String groupId, String passwd ) {
        logger.entry( userId, groupId, passwd );

        try {
            ForsRightsRequest request = new ForsRightsRequest();
            request.setUserIdAut( userId );
            request.setGroupIdAut( groupId );
            request.setPasswordAut( passwd );

            logger.debug( "Sending request to {}", this.endpoint );

            return this.callerProxy.forsRights( request );
        }
        catch( Exception ex ) {
            logger.error( "Got exception: {}", ex );
            throw ex;
        }
        finally {
            logger.exit();
        }
	}
	
    //-------------------------------------------------------------------------
    //              Helpers
    //-------------------------------------------------------------------------

	private ForsRightsPortType getAndConfigureUpdateProxy() {
		ForsRightsPortType port = this.services.getForsRightsPortType();
        BindingProvider proxy = (BindingProvider)port;
        
        proxy.getRequestContext().put( BindingProvider.ENDPOINT_ADDRESS_PROPERTY, endpoint );

        proxy.getRequestContext().put( "com.sun.xml.ws.connect.timeout", CONNECT_TIMEOUT_DEFAULT_IN_MS );
        proxy.getRequestContext().put( "com.sun.xml.ws.request.timeout", REQUEST_TIMEOUT_DEFAULT_IN_MS );

        return port;
    }
	
    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final XLogger logger = XLoggerFactory.getXLogger( ForsService.class );

    private static final int CONNECT_TIMEOUT_DEFAULT_IN_MS =  1 * 60 * 1000;    // 1 minute
    private static final int REQUEST_TIMEOUT_DEFAULT_IN_MS =  3 * 60 * 1000;    // 3 minutes

    private final String endpoint;
    private final ForsRightsService services;
    private final ForsRightsPortType callerProxy;
}
