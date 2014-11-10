//-----------------------------------------------------------------------------
package dk.dbc.updateservice.javascript;

//-----------------------------------------------------------------------------
import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Properties;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import com.github.tomakehurst.wiremock.WireMockServer;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

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

    @BeforeClass
    public static void startWireMockServerAndLoadSettings() throws IOException {
        int serverPort = 12100;
        String serverRootDir = Paths.get( "." ).toFile().getCanonicalPath() + "/src/test/resources/wiremock/solr";

        solrServer = new WireMockServer( wireMockConfig().port( serverPort ).withRootDirectory( serverRootDir ) );
        solrServer.start();
    }

    @AfterClass
    public static void stopWireMockServers() {
        solrServer.stop();
    }

    @Before
	public void initJSEngine() {
		jsEngine = new JSEngine();
		try {
			Properties props = new Properties();
			props.load( getClass().getResourceAsStream( "/dk/dbc/updateservice/javascript/settings.properties" ) );

			jsEngine.initialize( props );
        } catch ( IOException | IllegalArgumentException ex ) {
            logger.catching( XLogger.Level.WARN, ex );
        }
	}

	@Test
	public void test() throws Exception {
		assertEquals( 0.0, jsEngine.callEntryPoint( "main" ) );
	}

    private static WireMockServer solrServer;
}
