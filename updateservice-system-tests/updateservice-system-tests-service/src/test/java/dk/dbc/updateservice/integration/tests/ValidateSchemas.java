//-----------------------------------------------------------------------------
package dk.dbc.updateservice.integration.tests;

//-----------------------------------------------------------------------------

import dk.dbc.commons.jdbc.util.JDBCUtil;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.updateservice.integration.ExternWebServers;
import dk.dbc.updateservice.integration.UpdateServiceCaller;
import dk.dbc.updateservice.integration.service.*;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import static org.junit.Assert.*;

//-----------------------------------------------------------------------------
/**
 *
 * @author stp
 */
public class ValidateSchemas {
    private static final String AUTH_OK_GROUP_ID = "010100";
    private static final String AUTH_OK_USER_ID = "netpunkt";
    private static final String AUTH_OK_PASSWD = "20Koster";

    @BeforeClass
    public static void setUpClass() throws ClassNotFoundException, SQLException, IOException {
    	externWebServers = new ExternWebServers();
    	externWebServers.startServers();

        try (final Connection connection = newRawRepoConnection() ) {
            JDBCUtil.update( connection, "INSERT INTO queueworkers(worker) VALUES(?)", "fbssync");
            JDBCUtil.update( connection, "INSERT INTO queuerules(provider, worker, changed, leaf) VALUES(?, ?, ?, ?)", "opencataloging-update", "fbssync", "Y", "A");
        }
    }
    
    @AfterClass
    public static void tearDownClass() throws ClassNotFoundException, SQLException, IOException {
    	externWebServers.stopServers();

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
        
        Authentication auth = new Authentication();
        auth.setUserIdAut( AUTH_OK_USER_ID );
        auth.setGroupIdAut( AUTH_OK_GROUP_ID );
        auth.setPasswordAut( AUTH_OK_PASSWD );
        
        request.setAuthentication( auth );
        
        request.setTrackingId( "testGetValidateSchemas" );
        
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
        Properties settings = IOUtils.loadProperties( ValidateSchemas.class.getClassLoader(), "settings.properties" );

        Class.forName( settings.getProperty( "rawrepo.jdbc.driver" ) );
        Connection conn = DriverManager.getConnection(
                String.format( settings.getProperty( "rawrepo.jdbc.conn" ), settings.getProperty( "rawrepo.host" ), settings.getProperty( "rawrepo.port" ), settings.getProperty( "rawrepo.dbname" ) ),
                settings.getProperty( "rawrepo.user.name" ), settings.getProperty( "rawrepo.user.passwd" ) );
        conn.setAutoCommit(true);
        
        return conn;
    }
    
    private static ExternWebServers externWebServers;       
}
