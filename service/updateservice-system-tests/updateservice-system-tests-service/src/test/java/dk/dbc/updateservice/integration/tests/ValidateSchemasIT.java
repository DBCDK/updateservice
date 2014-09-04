//-----------------------------------------------------------------------------
package dk.dbc.updateservice.integration.tests;

//-----------------------------------------------------------------------------
import dk.dbc.commons.jdbc.util.JDBCUtil;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.updateservice.integration.UpdateServiceCaller;
import dk.dbc.updateservice.integration.service.GetValidateSchemasRequest;
import dk.dbc.updateservice.integration.service.GetValidateSchemasResult;
import dk.dbc.updateservice.integration.service.Schema;
import dk.dbc.updateservice.integration.service.ValidateSchemasStatusEnum;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

//-----------------------------------------------------------------------------
/**
 *
 * @author stp
 */
public class ValidateSchemasIT {
    private final XLogger logger = XLoggerFactory.getXLogger( this.getClass() );
    
    @BeforeClass
    public static void setUpClass() throws ClassNotFoundException, SQLException, IOException {
        try (final Connection connection = newRawRepoConnection() ) {
            JDBCUtil.update( connection, "INSERT INTO queueworkers(worker) VALUES(?)", "fbssync");
            JDBCUtil.update( connection, "INSERT INTO queuerules(provider, worker, changed, leaf) VALUES(?, ?, ?, ?)", "opencataloging-update", "fbssync", "Y", "A");
        }
    }
    
    @AfterClass
    public static void tearDownClass() throws ClassNotFoundException, SQLException, IOException {
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
        GetValidateSchemasRequest request = new GetValidateSchemasRequest();
        request.setAgencyId( "870970" );
        request.setTrackingId( "trackingId" );
        
        UpdateServiceCaller caller = new UpdateServiceCaller();
        GetValidateSchemasResult response = caller.getValidateSchemas( request );
        
        assertNotNull( response );
        assertNotNull( response.getSchema() );
        
        assertEquals( ValidateSchemasStatusEnum.OK, response.getValidateSchemasStatus() );
        assertFalse( response.getSchema().isEmpty() );
        for( Schema schema : response.getSchema() ) {
            assertFalse( schema.getValidateSchemaName().isEmpty() );
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
}
