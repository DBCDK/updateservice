//-----------------------------------------------------------------------------
package dk.dbc.updateservice.client;

//-----------------------------------------------------------------------------
import dk.dbc.updateservice.integration.service.CatalogingUpdatePortType;
import dk.dbc.updateservice.integration.service.CatalogingUpdateServices;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.xml.ws.BindingProvider;
import javax.xml.ws.handler.MessageContext;
import java.net.URL;
import java.util.Map;

//-----------------------------------------------------------------------------
/**
 * Class to call an UpdateService thought its SOAP interface.
 * <p/>
 *
 */
public class UpdateService {
    public UpdateService( URL baseUrl ) {
        this( baseUrl, null );
    }

    public UpdateService( URL baseUrl, Map<String, Object> headers ) {
        this.baseUrl = baseUrl;
        this.headers = headers;
        this.services = new CatalogingUpdateServices();

        this.connectTimeout = DEFAULT_CONNECT_TIMEOUT_MS;
        this.requestTimeout = DEFAULT_REQUEST_TIMEOUT_MS;
    }

    public CatalogingUpdatePortType createPort() {
        logger.entry();

        CatalogingUpdatePortType port = null;
        try {
            port = this.services.getCatalogingUpdatePort();
            BindingProvider proxy = (BindingProvider)port;

            String endpoint = baseUrl.toString() + ENDPOINT_PATH;

            proxy.getRequestContext().put( BindingProvider.ENDPOINT_ADDRESS_PROPERTY, endpoint );

            proxy.getRequestContext().put( "com.sun.xml.ws.connect.timeout", this.connectTimeout );
            proxy.getRequestContext().put( "com.sun.xml.ws.request.timeout", this.requestTimeout );

            if( headers != null && !headers.isEmpty() ) {
                proxy.getRequestContext().put( MessageContext.HTTP_REQUEST_HEADERS, headers );
            }

            return port;
        }
        finally {
            logger.exit( port );
        }
    }

    //-------------------------------------------------------------------------
    //                  Members
    //-------------------------------------------------------------------------

    private static final XLogger logger = XLoggerFactory.getXLogger( UpdateService.class );

    private static final String ENDPOINT_PATH = "/CatalogingUpdateServices/UpdateService";
    private static final long DEFAULT_CONNECT_TIMEOUT_MS =  1 * 60 * 1000;    // 1 minute
    private static final long DEFAULT_REQUEST_TIMEOUT_MS =  3 * 60 * 1000;    // 3 minutes

    private final URL baseUrl;
    private final Map<String, Object> headers;
    private final long connectTimeout;
    private final long requestTimeout;
    private final CatalogingUpdateServices services;
}
