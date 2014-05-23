//-----------------------------------------------------------------------------
package dk.dbc.validateservice.ws;

//-----------------------------------------------------------------------------

import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.iscrumjs.ejb.JSEngine;
import java.io.IOException;
import java.util.Properties;
import org.slf4j.ext.XLoggerFactory;

/**
 *
 * @author stp
 */
public class OpenValidateMock extends OpenValidate {
    public OpenValidateMock() {
        super();
    }
    
    @Override
    public void init() {        
        setLogger( XLoggerFactory.getXLogger( this.getClass() ) );
    }
    
    public static OpenValidate newInstance( String scriptResource ) throws IOException {
        OpenValidate service = new OpenValidateMock();
        
        Properties props = IOUtils.loadProperties( ";", "dk/dbc/validateservice/ws/settings.properties",
                                                        "javascript/iscrum/settings.properties" );
        props.setProperty( "entryresource", scriptResource );
        //props.setProperty( "modulesearchpath", "javascript/iscrum/rules/;javascript/iscrum/templates/" );

        JSEngine bean = new JSEngine();
        bean.init();
        bean.initialize( props );
        service.setJavaScriptProvider( bean );
        service.init();
        
        return service;
    }
}
