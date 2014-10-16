//-----------------------------------------------------------------------------
package dk.dbc.updateservice.integration.tests;

//-----------------------------------------------------------------------------
import dk.dbc.commons.jdbc.util.JDBCUtil;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.updateservice.integration.UpdateServiceCaller;
import dk.dbc.updateservice.integration.service.GetSchemasRequest;
import dk.dbc.updateservice.integration.service.GetSchemasResult;
import dk.dbc.updateservice.integration.service.Schema;
import dk.dbc.updateservice.integration.service.SchemasStatusEnum;

import java.io.IOException;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import org.junit.After;
import org.junit.AfterClass;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import com.github.tomakehurst.wiremock.WireMockServer;

//-----------------------------------------------------------------------------
/**
 *
 * @author stp
 */
public class ValidateSchemasIT {
    private final XLogger logger = XLoggerFactory.getXLogger( this.getClass() );
    
    @BeforeClass
    public static void setUpClass() throws ClassNotFoundException, SQLException, IOException {
        int serverPort = 12800;
        String serverRootDir = Paths.get( "." ).toFile().getCanonicalPath() + "/src/test/resources/wiremock/solr";
        
        solrServer = new WireMockServer( wireMockConfig().port( serverPort ).withRootDirectory( serverRootDir ) );
        solrServer.start();

        try (final Connection connection = newRawRepoConnection() ) {
            JDBCUtil.update( connection, "INSERT INTO queueworkers(worker) VALUES(?)", "fbssync");
            JDBCUtil.update( connection, "INSERT INTO queuerules(provider, worker, changed, leaf) VALUES(?, ?, ?, ?)", "opencataloging-update", "fbssync", "Y", "A");
        }
    }
    
    @AfterClass
    public static void tearDownClass() throws ClassNotFoundException, SQLException, IOException {
        solrServer.stop();

        try (final Connection conn = newRawRepoConnection() ) {        
            JDBCUtil.update( conn, "DELETE FROM queuerules");
            JDBCUtil.update( conn, "DELETE FROM queueworkers");
        }
    }

    @After
    public void clearRawRepo() throws SQLException, ClassNotFoundException, IOException {
        try (final Connection connection = newRawRepoConnection()) {
            JDBCUtil.update(connection, "DELETE FROM relations");
            JDBCUtil.update(connection, "DELETE FROM records");
            JDBCUtil.update(connection, "DELETE FROM queue");
        }
    }
    
    @Test
    public void testGetValidateSchemas() throws IOException {
        GetSchemasRequest request = new GetSchemasRequest();
        request.setAgencyId( "870970" );
        request.setTrackingId( "trackingId" );
        
        UpdateServiceCaller caller = new UpdateServiceCaller();
        GetSchemasResult response = caller.getSchemas( request );
        
        assertNotNull( response );
        assertNotNull( response.getSchema() );
        
        assertEquals( SchemasStatusEnum.OK, response.getSchemasStatus() );
        assertFalse( response.getSchema().isEmpty() );
        for( Schema schema : response.getSchema() ) {
            assertFalse( schema.getSchemaName().isEmpty() );
        }
    }

    private static Connection newRawRepoConnection() throws ClassNotFoundException, SQLException, IOException {
        Properties settings = IOUtils.loadProperties( ValidateSchemasIT.class.getClassLoader(), "settings.properties" );

        Class.forName( settings.getProperty( "rawrepo.jdbc.driver" ) );
        Connection conn = DriverManager.getConnection(
                String.format( settings.getProperty( "rawrepo.jdbc.conn" ), settings.getProperty( "rawrepo.host" ), settings.getProperty( "rawrepo.port" ), settings.getProperty( "rawrepo.dbname" ) ),
                settings.getProperty( "rawrepo.user.name" ), settings.getProperty( "rawrepo.user.passwd" ) );
        conn.setAutoCommit(true);
        
        return conn;
    }
    
    private static WireMockServer solrServer;    
}
