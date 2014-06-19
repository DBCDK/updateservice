//-----------------------------------------------------------------------------
package dk.dbc.updateservice.integration.tests;

//-----------------------------------------------------------------------------
import dk.dbc.commons.jdbc.util.JDBCUtil;
import dk.dbc.iscrum.records.MarcFactory;
import dk.dbc.iscrum.records.MarcReader;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.rawrepo.RawRepoDAO;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.integration.BibliographicRecordFactory;
import dk.dbc.updateservice.integration.UpdateServiceCaller;
import dk.dbc.updateservice.integration.service.Options;
import dk.dbc.updateservice.integration.service.UpdateOptionEnum;
import dk.dbc.updateservice.integration.service.UpdateRecordRequest;
import dk.dbc.updateservice.integration.service.UpdateRecordResult;
import dk.dbc.updateservice.integration.service.UpdateStatusEnum;
import dk.dbc.updateservice.integration.service.ValidateEntry;
import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.ejb.EJBException;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.xml.sax.SAXException;

//-----------------------------------------------------------------------------
/**
 *
 * @author stp
 */
public class UpdateRecordIT {
    private final XLogger logger = XLoggerFactory.getXLogger( this.getClass() );

    private static final String BOOK_TEMPLATE_NAME = "us-book";
    private static final String BOOK_MAIN_TEMPLATE_NAME = "us-bookmain";
    private static final String BOOK_VOLUME_TEMPLATE_NAME = "us-bookvolume";
    
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

    @Test( expected = javax.xml.ws.WebServiceException.class )
    public void testRecordWithInvalidValidateSchema() throws ParserConfigurationException, SAXException, IOException, ClassNotFoundException, SQLException {
        UpdateRecordRequest request = new UpdateRecordRequest();
        
        request.setAgencyId( "870970" );        
        request.setValidateSchema( "Unknown-Schema" );
        request.setOptions( new Options() );
        request.setTrackingId( "trackingId" );
        request.setBibliographicRecord( BibliographicRecordFactory.loadResource( "tests/record_validate_failure.xml" ) );

        UpdateServiceCaller caller = new UpdateServiceCaller();
        UpdateRecordResult response = caller.updateRecord( request );

        assertNotNull( response );
        assertEquals( UpdateStatusEnum.FAILED_INVALID_SCHEMA, response.getUpdateStatus() );

        try (final Connection connection = newRawRepoConnection()) {
            final RawRepoDAO rawRepo = RawRepoDAO.newInstance( connection );

            assertFalse( rawRepo.recordExists( "1 234 567 8", 870970 ) );
        }
    }
    
    @Test
    public void testRecordWithInvalidRecordSchema() throws ParserConfigurationException, SAXException, IOException, ClassNotFoundException, SQLException {
        UpdateRecordRequest request = new UpdateRecordRequest();
        
        request.setAgencyId( "870970" );        
        request.setValidateSchema( BOOK_TEMPLATE_NAME );
        request.setOptions( new Options() );
        request.setTrackingId( "trackingId" );
        request.setBibliographicRecord( BibliographicRecordFactory.loadResource( "tests/record_validate_failure.xml" ) );
        request.getBibliographicRecord().setRecordSchema( "Unknown-RecordSchema" );
        
        UpdateServiceCaller caller = new UpdateServiceCaller();
        UpdateRecordResult response = caller.updateRecord( request );
        
        assertNotNull( response );
        assertEquals( UpdateStatusEnum.FAILED_INVALID_SCHEMA, response.getUpdateStatus() );

        try (final Connection connection = newRawRepoConnection()) {
            final RawRepoDAO rawRepo = RawRepoDAO.newInstance( connection );
            
            assertFalse( rawRepo.recordExists( "1 234 567 8", 870970 ) );
        }
    }

    @Test
    public void testRecordWithInvalidRecordPacking() throws ParserConfigurationException, SAXException, IOException, ClassNotFoundException, SQLException {
        UpdateRecordRequest request = new UpdateRecordRequest();
        
        request.setAgencyId( "870970" );        
        request.setValidateSchema( BOOK_TEMPLATE_NAME );
        request.setOptions( new Options() );
        request.setTrackingId( "trackingId" );
        request.setBibliographicRecord( BibliographicRecordFactory.loadResource( "tests/record_validate_failure.xml" ) );
        request.getBibliographicRecord().setRecordPacking( "Unknown-RecordPacking" );
        
        UpdateServiceCaller caller = new UpdateServiceCaller();
        UpdateRecordResult response = caller.updateRecord( request );
        
        assertNotNull( response );
        assertEquals( UpdateStatusEnum.FAILED_INVALID_SCHEMA, response.getUpdateStatus() );

        try (final Connection connection = newRawRepoConnection()) {
            final RawRepoDAO rawRepo = RawRepoDAO.newInstance( connection );
            
            assertFalse( rawRepo.recordExists( "1 234 567 8", 870970 ) );
        }
    }

    @Test
    public void testValidateRecordWithFailure() throws ParserConfigurationException, SAXException, IOException, ClassNotFoundException, SQLException {
        UpdateRecordRequest request = new UpdateRecordRequest();
        
        request.setAgencyId( "870970" );        
        request.setValidateSchema( BOOK_TEMPLATE_NAME );
        Options options = new Options();
        options.getOption().add( UpdateOptionEnum.VALIDATE_ONLY );
        request.setOptions( options );
        request.setTrackingId( "trackingId" );
        request.setBibliographicRecord( BibliographicRecordFactory.loadResource( "tests/record_validate_failure.xml" ) );
        
        UpdateServiceCaller caller = new UpdateServiceCaller();
        UpdateRecordResult response = caller.updateRecord( request );
        
        assertNotNull( response );
        assertEquals( UpdateStatusEnum.VALIDATION_ERROR, response.getUpdateStatus() );
        assertNotSame( 0, response.getValidateInstance().getValidateEntry().size() );

        try (final Connection connection = newRawRepoConnection()) {
            final RawRepoDAO rawRepo = RawRepoDAO.newInstance( connection );
            
            assertFalse( rawRepo.recordExists( "1 234 567 8", 870970 ) );
        }
    }

    @Test
    public void testValidateRecordWithSuccess() throws ParserConfigurationException, SAXException, IOException, ClassNotFoundException, SQLException {
        UpdateRecordRequest request = new UpdateRecordRequest();
        
        request.setAgencyId( "870970" );        
        request.setValidateSchema( BOOK_TEMPLATE_NAME );
        Options options = new Options();
        options.getOption().add( UpdateOptionEnum.VALIDATE_ONLY );
        request.setOptions( options );
        request.setTrackingId( "trackingId" );
        request.setBibliographicRecord( BibliographicRecordFactory.loadResource( "tests/single_record.xml" ) );
        
        UpdateServiceCaller caller = new UpdateServiceCaller();
        UpdateRecordResult response = caller.updateRecord( request );
        
        assertNotNull( response );
        if( response.getValidateInstance() != null && response.getValidateInstance().getValidateEntry() != null && 
            !response.getValidateInstance().getValidateEntry().isEmpty() ) 
        {
            ValidateEntry entry = response.getValidateInstance().getValidateEntry().get( 0 );
            assertEquals( "", String.format( "%s: %s", entry.getOrdinalPositionOfField(), entry.getMessage() ) );
        }
        assertEquals( UpdateStatusEnum.VALIDATE_ONLY, response.getUpdateStatus() );
        assertNull( response.getValidateInstance() );

        try (final Connection connection = newRawRepoConnection()) {
            final RawRepoDAO rawRepo = RawRepoDAO.newInstance( connection );
            
            assertFalse( rawRepo.recordExists( "20611529", 870970 ) );
        }
    }

    @Test
    public void testUpdateSingleRecordWithSuccess() throws ParserConfigurationException, SAXException, IOException, ClassNotFoundException, SQLException {
        UpdateRecordRequest request = new UpdateRecordRequest();
        
        request.setAgencyId( "870970" );        
        request.setValidateSchema( BOOK_TEMPLATE_NAME );
        request.setOptions( new Options() );
        request.setTrackingId( "trackingId" );
        request.setBibliographicRecord( BibliographicRecordFactory.loadResource( "tests/single_record.xml" ) );
        
        UpdateServiceCaller caller = new UpdateServiceCaller();
        UpdateRecordResult response = caller.updateRecord( request );
        
        assertNotNull( response );
        if( response.getValidateInstance() != null && response.getValidateInstance().getValidateEntry() != null && 
            !response.getValidateInstance().getValidateEntry().isEmpty() ) 
        {
            ValidateEntry entry = response.getValidateInstance().getValidateEntry().get( 0 );
            assertEquals( "", String.format( "%s: %s", entry.getOrdinalPositionOfField(), entry.getMessage() ) );
        }
        assertEquals( UpdateStatusEnum.OK, response.getUpdateStatus() );
        assertNull( response.getValidateInstance() );

        try (final Connection connection = newRawRepoConnection()) {
            final RawRepoDAO rawRepo = RawRepoDAO.newInstance( connection );
            
            String id = "20611529";
            int library = 870970;
            
            assertTrue( rawRepo.recordExists( id, library ) );
            String recContent = new String( rawRepo.fetchRecord( id, library ).getContent() );
            List<MarcRecord> recs = new MarcFactory().createFromMarcXChange( new StringReader( recContent ) );
            assertEquals( 1, recs.size() );
            assertEquals( id, MarcReader.getRecordValue( recs.get( 0 ), "001", "a" ) );
            assertEquals( String.valueOf( library ), MarcReader.getRecordValue( recs.get( 0 ), "001", "b" ) );
        }
    }

    @Test
    public void testUpdateVolumeRecordWithUnknownParent() throws ParserConfigurationException, SAXException, IOException, ClassNotFoundException, SQLException {
        UpdateRecordRequest request = new UpdateRecordRequest();
        
        request.setAgencyId( "870970" );        
        request.setValidateSchema( BOOK_VOLUME_TEMPLATE_NAME );
        request.setOptions( new Options() );
        request.setTrackingId( "trackingId" );
        request.setBibliographicRecord( BibliographicRecordFactory.loadResource( "tests/volume_record.xml" ) );
        
        UpdateServiceCaller caller = new UpdateServiceCaller();
        UpdateRecordResult response = caller.updateRecord( request );
        
        assertNotNull( response );
        if( response.getValidateInstance() != null && response.getValidateInstance().getValidateEntry() != null && 
            !response.getValidateInstance().getValidateEntry().isEmpty() ) 
        {
            ValidateEntry entry = response.getValidateInstance().getValidateEntry().get( 0 );
            assertEquals( "", String.format( "%s: %s", entry.getOrdinalPositionOfField(), entry.getMessage() ) );
        }
        assertEquals( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, response.getUpdateStatus() );
        assertNull( response.getValidateInstance() );

        try (final Connection connection = newRawRepoConnection()) {
            final RawRepoDAO rawRepo = RawRepoDAO.newInstance( connection );
            
            String id = "58442895";
            int library = 870978;
            
            assertFalse( rawRepo.recordExists( id, library ) );
        }
    }
    
    @Test
    public void testUpdateVolumeRecordWithValidParent() throws ParserConfigurationException, SAXException, IOException, ClassNotFoundException, SQLException {
        UpdateRecordRequest request = new UpdateRecordRequest();
        
        // Update main record
        request.setAgencyId( "870970" );        
        request.setValidateSchema( BOOK_MAIN_TEMPLATE_NAME );
        request.setOptions( new Options() );
        request.setTrackingId( "trackingId" );
        request.setBibliographicRecord( BibliographicRecordFactory.loadResource( "tests/main_record.xml" ) );
        
        UpdateServiceCaller caller = new UpdateServiceCaller();
        UpdateRecordResult response = caller.updateRecord( request );

        // Check main record is updated.
        assertNotNull( response );
        if( response.getValidateInstance() != null && response.getValidateInstance().getValidateEntry() != null && 
            !response.getValidateInstance().getValidateEntry().isEmpty() ) 
        {
            ValidateEntry entry = response.getValidateInstance().getValidateEntry().get( 0 );
            assertEquals( "", String.format( "%s: %s", entry.getOrdinalPositionOfField(), entry.getMessage() ) );
        }
        assertEquals( UpdateStatusEnum.OK, response.getUpdateStatus() );
        assertNull( response.getValidateInstance() );
        try (final Connection connection = newRawRepoConnection()) {
            final RawRepoDAO rawRepo = RawRepoDAO.newInstance( connection );
            
            assertTrue( rawRepo.recordExists( "58442615", 870978 ) );
        }
        
        // Update volume record
        request = new UpdateRecordRequest();
        request.setAgencyId( "870970" );        
        request.setValidateSchema( BOOK_VOLUME_TEMPLATE_NAME );
        request.setOptions( new Options() );
        request.setTrackingId( "trackingId" );
        request.setBibliographicRecord( BibliographicRecordFactory.loadResource( "tests/volume_record.xml" ) );
        
        caller = new UpdateServiceCaller();
        response = caller.updateRecord( request );

        // Check volume record is updated.
        assertNotNull( response );
        if( response.getValidateInstance() != null && response.getValidateInstance().getValidateEntry() != null && 
            !response.getValidateInstance().getValidateEntry().isEmpty() ) 
        {
            ValidateEntry entry = response.getValidateInstance().getValidateEntry().get( 0 );
            assertEquals( "", String.format( "%s: %s", entry.getOrdinalPositionOfField(), entry.getMessage() ) );
        }
        assertEquals( UpdateStatusEnum.OK, response.getUpdateStatus() );
        assertNull( response.getValidateInstance() );

        try (final Connection connection = newRawRepoConnection()) {
            final RawRepoDAO rawRepo = RawRepoDAO.newInstance( connection );
            
            assertTrue( rawRepo.recordExists( "58442615", 870978 ) );
            assertTrue( rawRepo.recordExists( "58442895", 870978 ) );
            
            RecordId rawMainRecord = new RecordId( "58442615", 870978 );
            Set<RecordId> rawVolumeSet = rawRepo.getRelationsChildren( rawMainRecord );
            assertEquals( 1, rawVolumeSet.size() );
            
            Iterator<RecordId> iterator = rawVolumeSet.iterator();
            RecordId volumeRec = iterator.next();
            assertEquals( "58442895",volumeRec.getId() );
            assertEquals( 870978,volumeRec.getLibrary() );
        }
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
