package dk.dbc.updateservice.integration;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import java.io.IOException;
import java.util.Properties;

import com.github.tomakehurst.wiremock.WireMockServer;

import dk.dbc.iscrum.utils.IOUtils;

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

	public void startServers() {
		solrServer.start();
		forsrightsServer.start();
	}
	
	public void stopServers() {
		solrServer.stop();
		forsrightsServer.stop();
	}

	private Properties settings;
    private WireMockServer solrServer;       
    private WireMockServer forsrightsServer;       
	
}
