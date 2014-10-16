//-----------------------------------------------------------------------------
package dk.dbc.updateservice.integration.tests;

//-----------------------------------------------------------------------------
import dk.dbc.commons.jdbc.util.JDBCUtil;
import dk.dbc.iscrum.records.MarcFactory;
import dk.dbc.iscrum.records.MarcReader;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.rawrepo.RawRepoDAO;
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
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.After;
import org.junit.AfterClass;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.xml.sax.SAXException;

import com.github.tomakehurst.wiremock.WireMockServer;

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
    private static final String BOOK_ASSOCIATED_TEMPLATE_NAME = "us-associated-book";
    
    public UpdateRecordIT() {        
    }
    
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

    @Test( expected = javax.xml.ws.WebServiceException.class )
    public void testRecordWithInvalidValidateSchema() throws Exception {
        UpdateRecordRequest request = new UpdateRecordRequest();
        
        request.setAgencyId( "870970" );        
        request.setSchemaName( "Unknown-Schema" );
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
    public void testRecordWithInvalidRecordSchema() throws Exception {
        UpdateRecordRequest request = new UpdateRecordRequest();
        
        request.setAgencyId( "870970" );        
        request.setSchemaName( BOOK_TEMPLATE_NAME );
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
    public void testRecordWithInvalidRecordPacking() throws Exception {
        UpdateRecordRequest request = new UpdateRecordRequest();
        
        request.setAgencyId( "870970" );        
        request.setSchemaName( BOOK_TEMPLATE_NAME );
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
    public void testValidateRecordWithFailure() throws Exception {
        UpdateRecordRequest request = new UpdateRecordRequest();
        
        request.setAgencyId( "870970" );        
        request.setSchemaName( BOOK_TEMPLATE_NAME );
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
    public void testValidateRecordWithSuccess() throws Exception {
        UpdateRecordRequest request = new UpdateRecordRequest();
        
        request.setAgencyId( "870970" );        
        request.setSchemaName( BOOK_TEMPLATE_NAME );
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
    public void testValidateRecordWithLookup() throws Exception {
        UpdateRecordRequest request = new UpdateRecordRequest();
        
        request.setAgencyId( "870970" );        
        request.setSchemaName( BOOK_TEMPLATE_NAME );
        Options options = new Options();
        options.getOption().add( UpdateOptionEnum.VALIDATE_ONLY );
        request.setOptions( options );
        request.setTrackingId( "testValidateRecordWithLookup" );
        request.setBibliographicRecord( BibliographicRecordFactory.loadResource( "tests/single_lookup_record.xml" ) );
        
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
    public void testUpdateRecordWithNewRecordType() throws Exception {
        UpdateRecordRequest request = new UpdateRecordRequest();
        
        request.setAgencyId( "870970" );        
        request.setSchemaName( BOOK_TEMPLATE_NAME );
        request.setOptions( new Options() );
        request.setTrackingId( "testValidateRecordWithUpdatedRecordType_singleRecord" );
        request.setBibliographicRecord( BibliographicRecordFactory.loadResource( "tests/change_rectype_single_record.xml" ) );
        
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

        request = new UpdateRecordRequest();
        
        request.setAgencyId( "870970" );        
        request.setSchemaName( BOOK_MAIN_TEMPLATE_NAME );
        Options options = new Options();
        options.getOption().add( UpdateOptionEnum.VALIDATE_ONLY );
        request.setOptions( options );
        request.setTrackingId( "testValidateRecordWithUpdatedRecordType_mainRecord" );
        request.setBibliographicRecord( BibliographicRecordFactory.loadResource( "tests/change_rectype_main_record.xml" ) );
        
        caller = new UpdateServiceCaller();
        response = caller.updateRecord( request );
        
        assertNotNull( response );
        assertEquals( UpdateStatusEnum.VALIDATION_ERROR, response.getUpdateStatus() );
        assertNotSame( 0, response.getValidateInstance().getValidateEntry().size() );
    }
    
    @Test
    public void testUpdateSingleRecordWithSuccess() throws Exception {
        UpdateRecordRequest request = new UpdateRecordRequest();
        
        request.setAgencyId( "870970" );        
        request.setSchemaName( BOOK_TEMPLATE_NAME );
        request.setOptions( new Options() );
        request.setTrackingId( "testUpdateSingleRecordWithSuccess" );
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
    public void testUpdateVolumeRecordWithUnknownParent() throws Exception {
        UpdateRecordRequest request = new UpdateRecordRequest();
        
        request.setAgencyId( "870970" );        
        request.setSchemaName( BOOK_VOLUME_TEMPLATE_NAME );
        request.setOptions( new Options() );
        request.setTrackingId( "testUpdateVolumeRecordWithUnknownParent" );
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
            int library = 870970;
            
            assertFalse( rawRepo.recordExists( id, library ) );
        }
    }
    
    @Test
    public void testUpdateLibraryExtendedRecord() throws Exception {
        UpdateRecordRequest request = new UpdateRecordRequest();
        
        // Update common record
        request.setAgencyId( "870970" );        
        request.setSchemaName( BOOK_TEMPLATE_NAME );
        request.setOptions( new Options() );
        request.setTrackingId( "testUpdateAssociatedRecord_CommonRecord" );
        request.setBibliographicRecord( BibliographicRecordFactory.loadResource( "tests/single_record.xml" ) );
        
        UpdateServiceCaller caller = new UpdateServiceCaller();
        UpdateRecordResult response = caller.updateRecord( request );

        // Check common record is updated.
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
            
            assertTrue( rawRepo.recordExists( "20611529", 870970 ) );
        }
        
        // Update associated record
        request = new UpdateRecordRequest();
        request.setAgencyId( "700100" );        
        request.setSchemaName( BOOK_ASSOCIATED_TEMPLATE_NAME );
        request.setOptions( new Options() );
        request.setTrackingId( "testUpdateAssociatedRecord_AssocRecord" );
        request.setBibliographicRecord( BibliographicRecordFactory.loadResource( "tests/associated_record.xml" ) );
        
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
            
            assertTrue( rawRepo.recordExists( "20611529", 870970 ) );
            assertTrue( rawRepo.recordExists( "20611529", 700100 ) );

            Set<Integer> localLibrariesSet = rawRepo.allAgenciesForBibliographicRecordId( "20611529" );
            assertEquals( 2, localLibrariesSet.size() );
            assertTrue( localLibrariesSet.contains( 700100 ) );
            assertTrue( localLibrariesSet.contains( 870970 ) );
            
            Iterator<Integer> iterator = localLibrariesSet.iterator();
            assertEquals( 700100, iterator.next().longValue() );
        }
    }
    
    private static Connection newRawRepoConnection() throws ClassNotFoundException, SQLException, IOException {
        Properties settings = IOUtils.loadProperties( UpdateRecordIT.class.getClassLoader(), "settings.properties" );

        Class.forName( settings.getProperty( "rawrepo.jdbc.driver" ) );
        Connection conn = DriverManager.getConnection(
                String.format( settings.getProperty( "rawrepo.jdbc.conn" ), settings.getProperty( "rawrepo.host" ), settings.getProperty( "rawrepo.port" ), settings.getProperty( "rawrepo.dbname" ) ),
                settings.getProperty( "rawrepo.user.name" ), settings.getProperty( "rawrepo.user.passwd" ) );
        conn.setAutoCommit(true);
        
        return conn;
    }

    private static WireMockServer solrServer;        
}
