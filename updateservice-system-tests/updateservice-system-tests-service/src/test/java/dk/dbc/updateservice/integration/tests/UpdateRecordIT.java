//-----------------------------------------------------------------------------
package dk.dbc.updateservice.integration.tests;

//-----------------------------------------------------------------------------

import dk.dbc.commons.jdbc.util.JDBCUtil;
import dk.dbc.iscrum.records.MarcConverter;
import dk.dbc.iscrum.records.MarcReader;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.rawrepo.RawRepoDAO;
import dk.dbc.updateservice.integration.BibliographicRecordFactory;
import dk.dbc.updateservice.integration.ExternWebServers;
import dk.dbc.updateservice.integration.UpdateServiceCaller;
import dk.dbc.updateservice.integration.service.*;
import dk.dbc.updateservice.integration.service.Error;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.*;

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
    
    private static final String AUTH_OK_GROUP_ID = "010100";
    private static final String AUTH_OK_USER_ID = "netpunkt";
    private static final String AUTH_OK_PASSWD = "20Koster";
    private static final String AUTH_BAD_PASSWD = "wrong_passwd";

    private static final String X_FORWARDED_FOR_HEADER = "x-forwarded-for";
    private static final String X_FORWARDED_HOST_HEADER = "x-forwarded-host";
    private static final String X_FORWARDED_SERVER_HEADER = "x-forwarded-server";

    public UpdateRecordIT() {        
    }
    
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
    public void testRecordWithBadAuthentication() throws Exception {
        UpdateRecordRequest request = new UpdateRecordRequest();
        
        Authentication auth = new Authentication();
        auth.setUserIdAut( AUTH_OK_USER_ID );
        auth.setGroupIdAut( AUTH_OK_GROUP_ID );
        auth.setPasswordAut( AUTH_BAD_PASSWD );
        
        request.setAuthentication( auth );
        request.setSchemaName( BOOK_TEMPLATE_NAME );
        request.setTrackingId( "testRecordWithBadAuthentication" );
        request.setBibliographicRecord( BibliographicRecordFactory.loadResource( "tests/single_record.xml" ) );

        UpdateServiceCaller caller = new UpdateServiceCaller();
        UpdateRecordResult response = caller.updateRecord( request );

        assertNotNull( response );
        assertEquals( Error.AUTHENTICATION_ERROR, response.getError() );

        try( final Connection connection = newRawRepoConnection() ) {
            final RawRepoDAO rawRepo = RawRepoDAO.newInstance( connection );

            assertFalse( rawRepo.recordExists( "20611529", 870970 ) );
        }
    }

    @Test
    public void testRecordWithWrongAuthenticationRights() throws Exception {
        UpdateRecordRequest request = new UpdateRecordRequest();

        Authentication auth = new Authentication();
        auth.setUserIdAut( AUTH_OK_USER_ID );
        auth.setGroupIdAut( "700400" );
        auth.setPasswordAut( AUTH_OK_PASSWD );

        request.setAuthentication( auth );
        request.setSchemaName( BOOK_TEMPLATE_NAME );
        request.setTrackingId( "testRecordWithWrongAuthenticationRights" );
        request.setBibliographicRecord( BibliographicRecordFactory.loadResource( "tests/wrong_auth_rights_record.xml" ) );

        UpdateServiceCaller caller = new UpdateServiceCaller();
        UpdateRecordResult response = caller.updateRecord( request );

        assertNotNull( response );
        assertEquals( UpdateStatusEnum.VALIDATION_ERROR, response.getUpdateStatus() );
        assertNotNull( response.getValidateInstance() );
        assertNotNull( response.getValidateInstance().getValidateEntry() );
        assertEquals( 1, response.getValidateInstance().getValidateEntry().size() );
        assertNotSame( "", response.getValidateInstance().getValidateEntry().get( 0 ).getMessage() );

        try( final Connection connection = newRawRepoConnection() ) {
            final RawRepoDAO rawRepo = RawRepoDAO.newInstance( connection );

            assertFalse( rawRepo.recordExists( "20611529", 870970 ) );
        }
    }

    @Test
    public void testRecordWithInvalidValidateSchema() throws Exception {
        UpdateRecordRequest request = new UpdateRecordRequest();

        Authentication auth = new Authentication();
        auth.setUserIdAut( AUTH_OK_USER_ID );
        auth.setGroupIdAut( AUTH_OK_GROUP_ID );
        auth.setPasswordAut( AUTH_OK_PASSWD );

        request.setAuthentication( auth );
        request.setSchemaName( "Unknown-Schema" );
        request.setTrackingId( "testRecordWithInvalidValidateSchema" );
        request.setBibliographicRecord( BibliographicRecordFactory.loadResource( "tests/record_validate_failure.xml" ) );

        HashMap<String, Object> headers = new HashMap<>();
        headers.put( X_FORWARDED_FOR_HEADER, Collections.singletonList( "127.12.14.16, 127.37.185.18" ) );
        headers.put( X_FORWARDED_HOST_HEADER, Collections.singletonList("oss-services.dbc.dk, oss-services.dbc.dk"));
        headers.put( X_FORWARDED_SERVER_HEADER, Collections.singletonList("oss-services.dbc.dk, update.osssvc.beweb.dbc.dk"));

        UpdateServiceCaller caller = new UpdateServiceCaller( headers );
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
        
        Authentication auth = new Authentication();
        auth.setUserIdAut( AUTH_OK_USER_ID );
        auth.setGroupIdAut( AUTH_OK_GROUP_ID );
        auth.setPasswordAut( AUTH_OK_PASSWD );
        
        request.setAuthentication( auth );
        request.setSchemaName( BOOK_TEMPLATE_NAME );
        request.setTrackingId( "testRecordWithInvalidRecordSchema" );
        request.setBibliographicRecord( BibliographicRecordFactory.loadResource( "tests/record_validate_failure.xml" ) );
        request.getBibliographicRecord().setRecordSchema( "Unknown-RecordSchema" );

        HashMap<String, Object> headers = new HashMap<>();
        headers.put(X_FORWARDED_FOR_HEADER, Collections.singletonList("127.12.14.16, 127.37.185.18"));
        headers.put(X_FORWARDED_HOST_HEADER, Collections.singletonList("oss-services.dbc.dk, oss-services.dbc.dk"));
        headers.put(X_FORWARDED_SERVER_HEADER, Collections.singletonList("oss-services.dbc.dk, update.osssvc.beweb.dbc.dk"));

        UpdateServiceCaller caller = new UpdateServiceCaller( headers );
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
        
        Authentication auth = new Authentication();
        auth.setUserIdAut( AUTH_OK_USER_ID );
        auth.setGroupIdAut( AUTH_OK_GROUP_ID );
        auth.setPasswordAut( AUTH_OK_PASSWD );
        
        request.setAuthentication( auth );
        request.setSchemaName( BOOK_TEMPLATE_NAME );
        request.setTrackingId( "testRecordWithInvalidRecordPacking" );
        request.setBibliographicRecord( BibliographicRecordFactory.loadResource( "tests/record_validate_failure.xml" ) );
        request.getBibliographicRecord().setRecordPacking( "Unknown-RecordPacking" );

        HashMap<String, Object> headers = new HashMap<>();
        headers.put( X_FORWARDED_FOR_HEADER, Collections.singletonList( "127.12.14.16, 127.37.185.18" ) );
        headers.put( X_FORWARDED_HOST_HEADER, Collections.singletonList("oss-services.dbc.dk, oss-services.dbc.dk"));
        headers.put( X_FORWARDED_SERVER_HEADER, Collections.singletonList("oss-services.dbc.dk, update.osssvc.beweb.dbc.dk"));

        UpdateServiceCaller caller = new UpdateServiceCaller( headers );
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
        
        Authentication auth = new Authentication();
        auth.setUserIdAut( AUTH_OK_USER_ID );
        auth.setGroupIdAut( "870970" );
        auth.setPasswordAut( AUTH_OK_PASSWD );
        
        request.setAuthentication( auth );
        request.setSchemaName( BOOK_TEMPLATE_NAME );
        Options options = new Options();
        options.getOption().add( UpdateOptionEnum.VALIDATE_ONLY );
        request.setOptions( options );
        request.setTrackingId( "testValidateRecordWithFailure" );
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
        
        Authentication auth = new Authentication();
        auth.setUserIdAut( AUTH_OK_USER_ID );
        auth.setGroupIdAut( "870970" );
        auth.setPasswordAut( AUTH_OK_PASSWD );
        
        request.setAuthentication( auth );
        request.setSchemaName( BOOK_TEMPLATE_NAME );
        Options options = new Options();
        options.getOption().add( UpdateOptionEnum.VALIDATE_ONLY );
        request.setOptions( options );
        request.setTrackingId( "testValidateRecordWithSuccess" );
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
        
        Authentication auth = new Authentication();
        auth.setUserIdAut( AUTH_OK_USER_ID );
        auth.setGroupIdAut( "870970" );
        auth.setPasswordAut( AUTH_OK_PASSWD );
        
        request.setAuthentication( auth );
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
        
        Authentication auth = new Authentication();
        auth.setUserIdAut( AUTH_OK_USER_ID );
        auth.setGroupIdAut( "870970" );
        auth.setPasswordAut( AUTH_OK_PASSWD );
        
        request.setAuthentication( auth );
        request.setSchemaName( BOOK_TEMPLATE_NAME );
        request.setTrackingId( "testUpdateRecordWithNewRecordType_singleRecord" );
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
        
        request.setAuthentication( auth );
        request.setSchemaName( BOOK_MAIN_TEMPLATE_NAME );
        Options options = new Options();
        options.getOption().add( UpdateOptionEnum.VALIDATE_ONLY );
        request.setOptions( options );
        request.setTrackingId( "testUpdateRecordWithNewRecordType_mainRecord" );
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
        
        Authentication auth = new Authentication();
        auth.setUserIdAut( AUTH_OK_USER_ID );
        auth.setGroupIdAut( "870970" );
        auth.setPasswordAut( AUTH_OK_PASSWD );
        
        request.setAuthentication( auth );
        request.setSchemaName( BOOK_TEMPLATE_NAME );
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
            MarcRecord record = MarcConverter.convertFromMarcXChange( recContent );
            assertEquals( id, MarcReader.getRecordValue( record, "001", "a" ) );
            assertEquals( String.valueOf( library ), MarcReader.getRecordValue( record, "001", "b" ) );
        }
    }

    @Test
    public void testUpdateVolumeRecordWithUnknownParent() throws Exception {
        UpdateRecordRequest request = new UpdateRecordRequest();
        
        Authentication auth = new Authentication();
        auth.setUserIdAut( AUTH_OK_USER_ID );
        auth.setGroupIdAut( "870970" );
        auth.setPasswordAut( AUTH_OK_PASSWD );
        
        request.setAuthentication( auth );
        request.setSchemaName( BOOK_VOLUME_TEMPLATE_NAME );
        request.setTrackingId( "testUpdateVolumeRecordWithUnknownParent" );
        request.setBibliographicRecord( BibliographicRecordFactory.loadResource( "tests/volume_record.xml" ) );
        
        UpdateServiceCaller caller = new UpdateServiceCaller();
        UpdateRecordResult response = caller.updateRecord( request );
        
        assertNotNull( response );
        assertEquals( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, response.getUpdateStatus() );
        assertNotNull(response.getValidateInstance());
        assertNotNull( response.getValidateInstance().getValidateEntry());
        assertEquals(1, response.getValidateInstance().getValidateEntry().size());
        ValidateEntry entry = response.getValidateInstance().getValidateEntry().get( 0 );
        assertEquals( ValidateWarningOrErrorEnum.ERROR, entry.getWarningOrError() );
        assertNull( entry.getOrdinalPositionOfField() );
        assertNull( entry.getOrdinalPositionOfSubField() );
        assertNotSame( "", entry.getMessage() );

        try (final Connection connection = newRawRepoConnection()) {
            final RawRepoDAO rawRepo = RawRepoDAO.newInstance( connection );
            
            String id = "58442895";
            int library = 870970;
            
            assertFalse( rawRepo.recordExists( id, library ) );
        }
    }

    @Test
    public void testUpdateMainRecordWithConflictingSubfieldInVolumne() throws Exception {
        /******************************************************************************
         * First we update a main record, so we can update a volumne attach to it.
         */
        UpdateRecordRequest request = new UpdateRecordRequest();

        Authentication auth = new Authentication();
        auth.setUserIdAut( AUTH_OK_USER_ID );
        auth.setGroupIdAut( "870970" );
        auth.setPasswordAut( AUTH_OK_PASSWD );

        request.setAuthentication( auth );
        request.setSchemaName( BOOK_MAIN_TEMPLATE_NAME );
        request.setTrackingId( "testUpdateMainRecordWithConflictingSubfieldInVolumne_MainRecordOk" );
        request.setBibliographicRecord( BibliographicRecordFactory.loadResource( "tests/main_record.xml" ) );

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

            String id = "58442615";
            int library = 870970;

            assertTrue( rawRepo.recordExists( id, library ) );
            String recContent = new String( rawRepo.fetchRecord( id, library ).getContent() );
            MarcRecord record = MarcConverter.convertFromMarcXChange( recContent );
            assertEquals( id, MarcReader.getRecordValue( record, "001", "a" ) );
            assertEquals( String.valueOf( library ), MarcReader.getRecordValue( record, "001", "b" ) );
        }

        /******************************************************************************
         * Now we update a volumne record as a child of the main record.
         */
        request = new UpdateRecordRequest();

        auth = new Authentication();
        auth.setUserIdAut( AUTH_OK_USER_ID );
        auth.setGroupIdAut( "870970" );
        auth.setPasswordAut( AUTH_OK_PASSWD );

        request.setAuthentication( auth );
        request.setSchemaName( BOOK_VOLUME_TEMPLATE_NAME );
        request.setTrackingId( "testUpdateMainRecordWithConflictingSubfieldInVolumne_VolumneRecord" );
        request.setBibliographicRecord( BibliographicRecordFactory.loadResource( "tests/volume_record.xml" ) );

        caller = new UpdateServiceCaller();
        response = caller.updateRecord( request );

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

            String id = "58442895";
            int library = 870970;

            assertTrue( rawRepo.recordExists( id, library ) );
            String recContent = new String( rawRepo.fetchRecord( id, library ).getContent() );
            MarcRecord record = MarcConverter.convertFromMarcXChange( recContent );
            assertEquals( id, MarcReader.getRecordValue( record, "001", "a" ) );
            assertEquals( String.valueOf( library ), MarcReader.getRecordValue( record, "001", "b" ) );
        }

        /******************************************************************************
         * Now we update the main record again and expects an validation error.
         */
        request = new UpdateRecordRequest();

        auth = new Authentication();
        auth.setUserIdAut( AUTH_OK_USER_ID );
        auth.setGroupIdAut( "870970" );
        auth.setPasswordAut( AUTH_OK_PASSWD );

        request.setAuthentication( auth );
        request.setSchemaName( BOOK_MAIN_TEMPLATE_NAME );
        request.setTrackingId( "testUpdateMainRecordWithConflictingSubfieldInVolumne_MainRecordFailure" );
        request.setBibliographicRecord( BibliographicRecordFactory.loadResource( "tests/main_record.xml" ) );

        caller = new UpdateServiceCaller();
        response = caller.updateRecord( request );

        assertNotNull( response );
        assertEquals( UpdateStatusEnum.VALIDATION_ERROR, response.getUpdateStatus() );
        assertNotSame(0, response.getValidateInstance().getValidateEntry().size());
    }

    @Test
    public void testUpdateLibraryExtendedRecord() throws Exception {
        UpdateRecordRequest request = new UpdateRecordRequest();
        
        // Update common record
        Authentication auth = new Authentication();
        auth.setUserIdAut( AUTH_OK_USER_ID );
        auth.setGroupIdAut( "870970" );
        auth.setPasswordAut( AUTH_OK_PASSWD );
        
        request.setAuthentication( auth );
        request.setSchemaName( BOOK_TEMPLATE_NAME );
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
        auth.setGroupIdAut( "010100" );
        request.setAuthentication( auth );
        request.setSchemaName( BOOK_ASSOCIATED_TEMPLATE_NAME );
        request.setTrackingId( "testUpdateAssociatedRecord_AssocRecord" );
        request.setBibliographicRecord( BibliographicRecordFactory.loadResource( "tests/associated_record.xml" ) );

        HashMap<String, Object> headers = new HashMap<>();
        headers.put( X_FORWARDED_FOR_HEADER, Collections.singletonList( "127.12.14.16, 127.37.185.18" ) );
        headers.put( X_FORWARDED_HOST_HEADER, Collections.singletonList("oss-services.dbc.dk, oss-services.dbc.dk"));
        headers.put( X_FORWARDED_SERVER_HEADER, Collections.singletonList("oss-services.dbc.dk, update.osssvc.beweb.dbc.dk"));

        caller = new UpdateServiceCaller( headers );
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
            assertTrue( rawRepo.recordExists( "20611529", 10100 ) );

            Set<Integer> localLibrariesSet = rawRepo.allAgenciesForBibliographicRecordId( "20611529" );
            assertEquals( 2, localLibrariesSet.size() );
            assertTrue(localLibrariesSet.contains(10100));
            assertTrue(localLibrariesSet.contains(870970));
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

    private static ExternWebServers externWebServers;       
}
