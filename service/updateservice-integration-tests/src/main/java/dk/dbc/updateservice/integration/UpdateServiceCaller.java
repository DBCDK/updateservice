//-----------------------------------------------------------------------------
package dk.dbc.updateservice.integration;

//-----------------------------------------------------------------------------

import dk.dbc.updateservice.integration.service.CatalogingUpdatePortType;
import dk.dbc.updateservice.integration.service.CatalogingUpdateServices;
import dk.dbc.updateservice.integration.service.UpdateRecordRequest;
import dk.dbc.updateservice.integration.service.UpdateRecordResult;
import javax.xml.ws.BindingProvider;

/**
 *
 * @author stp
 */
public class UpdateServiceCaller {
    public UpdateServiceCaller() {
        this.endpoint = String.format( "http://localhost:%s/CatalogingUpdateServices/UpdateService", System.getProperty( "container.http.port" ) );
        this.services = new CatalogingUpdateServices();
        this.callerProxy = getAndConfigureUpdateProxy();
    }
    
    public UpdateRecordResult updateRecord( UpdateRecordRequest updateRecordRequest ) {
        return this.callerProxy.updateRecord( updateRecordRequest );
    }
    
    private CatalogingUpdatePortType getAndConfigureUpdateProxy() {
        CatalogingUpdatePortType port = this.services.getCatalogingUpdatePort();
        BindingProvider proxy = (BindingProvider)port;
        
        proxy.getRequestContext().put( BindingProvider.ENDPOINT_ADDRESS_PROPERTY, endpoint );

        proxy.getRequestContext().put( "com.sun.xml.ws.connect.timeout", CONNECT_TIMEOUT_DEFAULT_IN_MS );
        proxy.getRequestContext().put( "com.sun.xml.ws.request.timeout", REQUEST_TIMEOUT_DEFAULT_IN_MS );
        
        return port;
    }
    
    private static final long CONNECT_TIMEOUT_DEFAULT_IN_MS =  1 * 60 * 1000;    // 1 minute
    private static final long REQUEST_TIMEOUT_DEFAULT_IN_MS =  3 * 60 * 1000;    // 3 minutes

    private final String endpoint;
    private final CatalogingUpdateServices services;
    private final CatalogingUpdatePortType callerProxy;
}
