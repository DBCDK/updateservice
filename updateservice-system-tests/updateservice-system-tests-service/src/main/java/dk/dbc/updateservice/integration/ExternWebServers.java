package dk.dbc.updateservice.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import dk.dbc.iscrum.utils.IOUtils;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Helper class to mock external used webservers with WireMock for JUnit classes
 * that calls and tests UpdateService. 
 * 
 * @author stp
 */
public class ExternWebServers {
	public ExternWebServers() throws IOException {
		int port;
		String rootDir;

		this.settings = IOUtils.loadProperties( getClass().getClassLoader(), "settings.properties" );
		
		port = Integer.valueOf( settings.getProperty( "solr.server.port" ) );
		rootDir = settings.getProperty( "solr.server.rootdir" );
        this.solrServer = new WireMockServer( wireMockConfig().port( port ).withRootDirectory( rootDir ) );
		
		port = Integer.valueOf( settings.getProperty( "forsrights.server.port" ) );
		rootDir = settings.getProperty( "forsrights.server.rootdir" );
        this.forsrightsServer = new WireMockServer( wireMockConfig().port( port ).withRootDirectory( rootDir ) );
	}

    public ExternWebServers( File testcaseDir ) throws IOException {
        int port;
        String rootDir;

        this.settings = IOUtils.loadProperties( getClass().getClassLoader(), "settings.properties" );

        File solrDir = new File( testcaseDir.getCanonicalPath() + "/" + settings.getProperty( "solr.server.rootdir" ) );
        if( solrDir.exists() && solrDir.isDirectory() ) {
            port = Integer.valueOf( settings.getProperty( "solr.server.port" ) );
            this.solrServer = new WireMockServer( wireMockConfig().port( port ).withRootDirectory( solrDir.getCanonicalPath() ) );
        }

        File forsrightsDir = new File( testcaseDir.getCanonicalPath() + "/" + settings.getProperty( "forsrights.server.rootdir" ) );
        if( forsrightsDir.exists() && forsrightsDir.isDirectory() ) {
            port = Integer.valueOf( settings.getProperty( "forsrights.server.port" ) );
            this.forsrightsServer = new WireMockServer( wireMockConfig().port( port ).withRootDirectory( forsrightsDir.getCanonicalPath() ) );
        }
    }

	public void startServers() {
        if( solrServer != null ) {
            solrServer.start();
        }

        if( forsrightsServer != null ) {
            forsrightsServer.start();
        }
	}
	
	public void stopServers() {
        if( solrServer != null ) {
            solrServer.stop();
        }

        if( forsrightsServer != null ) {
            forsrightsServer.stop();
        }
	}

	private Properties settings;
    private WireMockServer solrServer;       
    private WireMockServer forsrightsServer;       
	
}
