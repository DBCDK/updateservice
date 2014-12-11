//-----------------------------------------------------------------------------
package dk.dbc.updateservice.integration;

//-----------------------------------------------------------------------------

import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.updateservice.integration.service.*;

import javax.xml.ws.BindingProvider;
import javax.xml.ws.handler.MessageContext;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

/**
 *
 * @author stp
 */
public class UpdateServiceCaller {
    public UpdateServiceCaller() throws IOException {
        this( null );
    }

    public UpdateServiceCaller( Map<String, Object> headers ) throws IOException {
        Properties settings = IOUtils.loadProperties( getClass().getClassLoader(), "settings.properties" );
        
        this.endpoint = String.format( "http://%s:%s/%s", settings.getProperty( "service.host" ), settings.getProperty( "service.port" ), settings.getProperty( "service.endpoint.path" ) );
        this.services = new CatalogingUpdateServices();
        this.callerProxy = getAndConfigureUpdateProxy( headers );
    }
    
    public UpdateRecordResult updateRecord( UpdateRecordRequest updateRecordRequest ) {
        return this.callerProxy.updateRecord( updateRecordRequest );
    }
    
    public GetSchemasResult getSchemas( GetSchemasRequest request ) {
        return this.callerProxy.getSchemas( request );
    }

    private CatalogingUpdatePortType getAndConfigureUpdateProxy( Map<String, Object> headers ) {
        CatalogingUpdatePortType port = this.services.getCatalogingUpdatePort();
        BindingProvider proxy = (BindingProvider)port;

        proxy.getRequestContext().put( BindingProvider.ENDPOINT_ADDRESS_PROPERTY, endpoint );

        proxy.getRequestContext().put( "com.sun.xml.ws.connect.timeout", CONNECT_TIMEOUT_DEFAULT_IN_MS );
        proxy.getRequestContext().put( "com.sun.xml.ws.request.timeout", REQUEST_TIMEOUT_DEFAULT_IN_MS );

        if( headers != null && !headers.isEmpty() ) {
            proxy.getRequestContext().put(MessageContext.HTTP_REQUEST_HEADERS, headers);
        }

        return port;
    }
    
    private static final long CONNECT_TIMEOUT_DEFAULT_IN_MS =  1 * 60 * 1000;    // 1 minute
    private static final long REQUEST_TIMEOUT_DEFAULT_IN_MS =  3 * 60 * 1000;    // 3 minutes

    private final String endpoint;
    private final CatalogingUpdateServices services;
    private final CatalogingUpdatePortType callerProxy;
}
