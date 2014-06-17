//-----------------------------------------------------------------------------
package dk.dbc.updateservice.integration.tests;

//-----------------------------------------------------------------------------
import dk.dbc.commons.jdbc.util.JDBCUtil;
import dk.dbc.updateservice.integration.BibliographicRecordFactory;
import dk.dbc.updateservice.integration.UpdateServiceCaller;
import dk.dbc.updateservice.integration.service.Options;
import dk.dbc.updateservice.integration.service.UpdateOptionEnum;
import dk.dbc.updateservice.integration.service.UpdateRecordRequest;
import dk.dbc.updateservice.integration.service.UpdateRecordResult;
import dk.dbc.updateservice.integration.service.UpdateStatusEnum;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.SAXException;

//-----------------------------------------------------------------------------
/**
 *
 * @author stp
 */
public class UpdateRecordIT {
    public UpdateRecordIT() {        
    }
    
    @BeforeClass
    public static void setUpClass() throws ClassNotFoundException, SQLException {
        try (final Connection connection = newRawRepoConnection() ) {
            JDBCUtil.update( connection, "INSERT INTO queueworkers(worker) VALUES(?)", "fbssync");
            JDBCUtil.update( connection, "INSERT INTO queuerules(provider, worker, changed, leaf) VALUES(?, ?, ?, ?)", "opencataloging-update", "fbssync", "Y", "A");
        }
    }
    
    @AfterClass
    public static void tearDownClass() throws ClassNotFoundException, SQLException {
        try (final Connection conn = newRawRepoConnection() ) {        
            JDBCUtil.update( conn, "DELETE FROM queuerules");
            JDBCUtil.update( conn, "DELETE FROM queueworkers");
        }
    }

    @After
    public void clearRawRepo() throws SQLException, ClassNotFoundException {
        try (final Connection connection = newRawRepoConnection()) {
            JDBCUtil.update(connection, "DELETE FROM relations");
            JDBCUtil.update(connection, "DELETE FROM records");
            JDBCUtil.update(connection, "DELETE FROM queue");
        }
    }

    @Test
    public void testValidateRecord() throws ParserConfigurationException, SAXException, IOException {
        UpdateRecordRequest request = new UpdateRecordRequest();
        
        request.setAgencyId( "870970" );        
        request.setValidateSchema( "bog" );
        Options options = new Options();
        options.getOption().add( UpdateOptionEnum.VALIDATE_ONLY );
        request.setOptions( options );
        request.setTrackingId( "trackingId" );
        request.setBibliographicRecord( BibliographicRecordFactory.loadResource( "single_record.xml" ) );
        
        UpdateServiceCaller caller = new UpdateServiceCaller();
        UpdateRecordResult response = caller.updateRecord( request );
        
        assertNotNull( response );
        assertEquals( UpdateStatusEnum.VALIDATION_ERROR, response.getUpdateStatus() );
        assertEquals( 0, response.getValidateInstance().getValidateEntry().size() );
    }
    
    private static Connection newRawRepoConnection() throws ClassNotFoundException, SQLException {
        Class.forName("org.postgresql.Driver");
        Connection conn = DriverManager.getConnection(
                String.format("jdbc:postgresql://localhost:%s/%s", System.getProperty( "rawrepo.db.port" ), System.getProperty( "rawrepo.db.name" ) ),
                System.getProperty("user.name"), System.getProperty("user.name") );
        conn.setAutoCommit(true);
        
        return conn;
    }
    
}
