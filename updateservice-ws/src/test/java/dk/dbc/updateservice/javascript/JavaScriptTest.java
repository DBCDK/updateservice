//-----------------------------------------------------------------------------
package dk.dbc.updateservice.javascript;

//-----------------------------------------------------------------------------
import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.iscrumjs.ejb.JSEngine;

//-----------------------------------------------------------------------------
/**
 * Long description of this test suite...
 *  
 * @author stp
 */
public class JavaScriptTest {
	private static final XLogger logger = XLoggerFactory.getXLogger( JavaScriptTest.class );
	private JSEngine jsEngine;
	
	@Before
	public void initJSEngine() {
		jsEngine = new JSEngine();
		try {
			Properties props = new Properties();
			props.load( getClass().getResourceAsStream( "/dk/dbc/updateservice/javascript/settings.properties" ) );

			jsEngine.init();
			jsEngine.initialize( props );
        } catch ( IOException | IllegalArgumentException ex ) {
            logger.catching( XLogger.Level.WARN, ex );
        }
	}
	
	@Test
	public void test() throws Exception {
		assertEquals( 0.0, jsEngine.callEntryPoint( "main" ) );
	}

}
