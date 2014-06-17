//-----------------------------------------------------------------------------
package dk.dbc.updateservice.validate;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.iscrumjs.ejb.JSEngine;
import java.io.IOException;
import java.util.Properties;
import org.slf4j.ext.XLoggerFactory;

//-----------------------------------------------------------------------------
/**
 *
 * @author stp
 */
public class ValidatorMock extends Validator {
    public ValidatorMock() {
        super();
    }
    
    @Override
    public void init() {        
        setLogger( XLoggerFactory.getXLogger( this.getClass() ) );
    }
    
    public static Validator newInstance( String scriptResource ) throws IOException {
        Validator service = new ValidatorMock();
        
        Properties props = IOUtils.loadProperties( ";", "dk/dbc/validateservice/ws/settings.properties",
                                                        "javascript/iscrum/settings.properties" );
        props.setProperty( "entryresource", scriptResource );

        JSEngine bean = new JSEngine();
        bean.init();
        bean.initialize( props );
        service.setJavaScriptProvider( bean );
        service.init();
        
        return service;
    }
}
