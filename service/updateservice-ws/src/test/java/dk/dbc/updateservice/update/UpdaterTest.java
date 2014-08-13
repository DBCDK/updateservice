//-----------------------------------------------------------------------------
package dk.dbc.updateservice.update;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcFactory;
import dk.dbc.iscrum.records.MarcField;
import dk.dbc.iscrum.records.MarcReader;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcXchangeFactory;
import dk.dbc.iscrum.records.marcxchange.CollectionType;
import dk.dbc.iscrum.records.marcxchange.ObjectFactory;
import dk.dbc.rawrepo.RawRepoDAO;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import static org.mockito.Mockito.*;
import org.mockito.MockitoAnnotations;

//-----------------------------------------------------------------------------
/**
 *
 * @author stp
 */
public class UpdaterTest {
    //-------------------------------------------------------------------------
    //              Constructors
    //-------------------------------------------------------------------------

    public UpdaterTest() {
    }
    
    @Mock
    RawRepoDAO rawRepoDAO;
    
    @Mock
    LibraryRecordsHandler recordsHandler;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }    
    
    //-------------------------------------------------------------------------
    //              Tests
    //-------------------------------------------------------------------------

    @Test( expected = NullPointerException.class )
    public void testUpdateRecord_NullRecord() throws Exception {
        Updater updater = new Updater( rawRepoDAO, recordsHandler );
        updater.init();
        updater.updateRecord( null );
    }
    
    @Test
    public void testUpdateRecord_SingleValidRecord() throws Exception {
        Updater updater = new Updater( rawRepoDAO, recordsHandler );
        updater.init();
        
        Record rec = new RawRepoRecordMock( "20611529", 870970 );
        when( rawRepoDAO.fetchRecord( rec.getId().getId(), rec.getId().getLibrary() ) ).thenReturn( rec );
        
        updater.updateRecord( loadMarcRecord( "single_record.xml" ) );
        
        // Verify calls to RawRepoDAO
        ArgumentCaptor<Record> argRecord = ArgumentCaptor.forClass( Record.class );
        verify( rawRepoDAO ).saveRecord( argRecord.capture() );
        assertEquals( rec.getId(), argRecord.getValue().getId() );
        assertTrue( argRecord.getValue().hasContent() );

        verify( rawRepoDAO, never() ).setRelationsFrom( null, null );        
        verify( rawRepoDAO ).changedRecord( Updater.PROVIDER, rec.getId() );
    }
    
    @Test( expected = UpdateException.class )
    public void testUpdateRecord_VolumeRecord_WithUnknownParent() throws Exception {
        Updater updater = new Updater( rawRepoDAO, recordsHandler );
        updater.init();
        
        Record rec = new RawRepoRecordMock( "58442895", 870970 );
        when( rawRepoDAO.fetchRecord( rec.getId().getId(), rec.getId().getLibrary() ) ).thenReturn( rec );
        
        final HashSet<RecordId> references = new HashSet<>();
        references.add( new RecordId( "58442615", 870970 ) );        
        doThrow( new SQLException() ).when( rawRepoDAO ).setRelationsFrom( rec.getId(), references ); 
        
        updater.updateRecord( loadMarcRecord( "volume_record.xml" ) );        
    }
    
    @Test
    public void testUpdateRecord_VolumeRecord_WithValidParent() throws Exception {
        Updater updater = new Updater( rawRepoDAO, recordsHandler );
        updater.init();
        
        Record rec = new RawRepoRecordMock( "58442895", 870970 );
        when( rawRepoDAO.fetchRecord( rec.getId().getId(), rec.getId().getLibrary() ) ).thenReturn( rec );
        when( rawRepoDAO.recordExists( "58442615", 870970 ) ).thenReturn( true );
        
        updater.updateRecord( loadMarcRecord( "volume_record.xml" ) );
        
        // Verify calls to RawRepoDAO
        ArgumentCaptor<Record> argRecord = ArgumentCaptor.forClass( Record.class );
        verify( rawRepoDAO ).saveRecord( argRecord.capture() );
        assertEquals( rec.getId(), argRecord.getValue().getId() );
        assertTrue( argRecord.getValue().hasContent() );

        final HashSet<RecordId> references = new HashSet<>();
        references.add( new RecordId( "58442615", 870970 ) );                
        verify( rawRepoDAO ).setRelationsFrom( rec.getId(), references );
        
        verify( rawRepoDAO ).changedRecord( Updater.PROVIDER, rec.getId() );
    }
  
    @Test
    public void testUpdateRecord_LibraryLocalRecord() throws Exception {
        Updater updater = new Updater( rawRepoDAO, recordsHandler );
        updater.init();
        
        Record rec = new RawRepoRecordMock( "20611529", 700100 );
        when( rawRepoDAO.fetchRecord( rec.getId().getId(), rec.getId().getLibrary() ) ).thenReturn( rec );
        
        updater.updateRecord( loadMarcRecord( "library_local_record.xml" ) );
        
        // Verify calls to RawRepoDAO
        ArgumentCaptor<Record> argRecord = ArgumentCaptor.forClass( Record.class );
        verify( rawRepoDAO ).saveRecord( argRecord.capture() );
        assertEquals( rec.getId(), argRecord.getValue().getId() );
        assertTrue( argRecord.getValue().hasContent() );

        verify( rawRepoDAO, never() ).setRelationsFrom( null, null );        
        verify( rawRepoDAO ).changedRecord( Updater.PROVIDER, rec.getId() );
    }
    
    @Test
    public void testUpdateRecord_LibraryExtendedRecord_WithClassificationData() throws Exception {
        Updater updater = new Updater( rawRepoDAO, recordsHandler );
        updater.init();
        
        MarcRecord dbcRec = loadMarcRecord( "single_record.xml" );
        MarcRecord extRec = loadMarcRecord( "library_extended_single_record.xml" );
        
        Record rawDbcRec = createRawRecord( dbcRec );
        Record rawExtRec = new RawRepoRecordMock( "20611529", 700100 );
        when( rawRepoDAO.fetchRecord( rawDbcRec.getId().getId(), rawDbcRec.getId().getLibrary() ) ).thenReturn( rawDbcRec );
        when( rawRepoDAO.fetchRecord( rawExtRec.getId().getId(), rawExtRec.getId().getLibrary() ) ).thenReturn( rawExtRec );
        when( rawRepoDAO.recordExists( rawExtRec.getId().getId(), RawRepoDAO.COMMON_LIBRARY ) ).thenReturn( true );
        when( recordsHandler.updateLibraryExtendedRecord( dbcRec, extRec ) ).thenReturn( extRec );
        
        updater.updateRecord( extRec );
        
        // Verify update calls
        verify( recordsHandler ).updateLibraryExtendedRecord( dbcRec, extRec );
        
        ArgumentCaptor<Record> argRecord = ArgumentCaptor.forClass( Record.class );
        verify( rawRepoDAO ).saveRecord( argRecord.capture() );
        assertEquals( rawExtRec.getId(), argRecord.getValue().getId() );
        assertTrue( argRecord.getValue().hasContent() );
        assertEquals( extRec, updater.decodeRecord( argRecord.getValue().getContent() ) );

        final HashSet<RecordId> references = new HashSet<>();
        references.add( new RecordId( rawExtRec.getId().getId(), RawRepoDAO.COMMON_LIBRARY ) );                
        verify( rawRepoDAO ).setRelationsFrom( rawExtRec.getId(), references );
        verify( rawRepoDAO ).changedRecord( Updater.PROVIDER, rawExtRec.getId() );
        verify( rawRepoDAO ).enqueue( rawExtRec.getId(), Updater.PROVIDER, true, true );
    }

    @Test
    public void testUpdateRecord_LibraryExtendedRecord_WithNoClassificationData() throws Exception {
        Updater updater = new Updater( rawRepoDAO, recordsHandler );
        updater.init();
        
        MarcRecord dbcRec = loadMarcRecord( "single_record.xml" );
        MarcRecord extRec = loadMarcRecord( "library_extended_single_record.xml" );
        MarcRecord emptyRec = new MarcRecord();
        emptyRec.setFields( new ArrayList<MarcField>() );
        
        Record rawDbcRec = createRawRecord( dbcRec );
        Record rawExtRec = new RawRepoRecordMock( "20611529", 700100 );
        when( rawRepoDAO.fetchRecord( rawDbcRec.getId().getId(), rawDbcRec.getId().getLibrary() ) ).thenReturn( rawDbcRec );
        when( rawRepoDAO.fetchRecord( rawExtRec.getId().getId(), rawExtRec.getId().getLibrary() ) ).thenReturn( rawExtRec );
        when( rawRepoDAO.recordExists( rawExtRec.getId().getId(), RawRepoDAO.COMMON_LIBRARY ) ).thenReturn( true );
        when( recordsHandler.updateLibraryExtendedRecord( dbcRec, extRec ) ).thenReturn( emptyRec );
        
        updater.updateRecord( extRec );
        
        // Verify update calls
        verify( recordsHandler ).updateLibraryExtendedRecord( dbcRec, extRec );
        
        ArgumentCaptor<Record> argRecord = ArgumentCaptor.forClass( Record.class );
        verify( rawRepoDAO ).saveRecord( argRecord.capture() );
        assertEquals( rawExtRec.getId(), argRecord.getValue().getId() );
        assertFalse( argRecord.getValue().hasContent() );
        assertNull( argRecord.getValue().getContent() );

        final HashSet<RecordId> references = new HashSet<>();
        references.add( new RecordId( rawExtRec.getId().getId(), RawRepoDAO.COMMON_LIBRARY ) );                
        verify( rawRepoDAO ).setRelationsFrom( rawExtRec.getId(), references );
        verify( rawRepoDAO ).changedRecord( Updater.PROVIDER, rawExtRec.getId() );
        verify( rawRepoDAO ).enqueue( rawExtRec.getId(), Updater.PROVIDER, true, true );
    }

    //-------------------------------------------------------------------------
    //              Help functions
    //-------------------------------------------------------------------------

    /**
     * Loads a MarcRecord from a file resource.
     * <p>
     * The file is assumed to be a marcxchange XML file.
     * 
     * @param resName The name of the file resource.
     * 
     * @return 
     *      The record from the marcxchange document, if the document contains 
     *      exactly one record. The function returns null otherwise.
     */
    public MarcRecord loadMarcRecord( String resName ) {
        return loadMarcRecord( getClass().getResourceAsStream( resName ) );
    }
    
    /**
     * Loads a MarcRecord from an InputStream of a marcxchange document.
     * 
     * @param is The InputStream
     * 
     * @return 
     *      The record from the marcxchange document, if the document contains 
     *      exactly one record. The function returns null otherwise.
     */
    public MarcRecord loadMarcRecord( InputStream is ) {
        MarcFactory factory = new MarcFactory();
        List<MarcRecord> records = factory.createFromMarcXChange( new InputStreamReader( is ) );
        
        if( records.size() != 1 ) {
            return null;
        }
        
        return records.get( 0 );
    }
    
    Record createRawRecord( MarcRecord record ) throws JAXBException, UnsupportedEncodingException {                
        String recId = MarcReader.getRecordValue( record, "001", "a" );
        int libraryId = Integer.parseInt( MarcReader.getRecordValue( record, "001", "b" ) );
        
        Record result = new RawRepoRecordMock( recId, libraryId );
        result.setCreated( Calendar.getInstance().getTime() );
        result.setModified( result.getCreated() );
        result.setContent( encodeRecord( record ) );
        
        return result;
    }
    
    byte[] encodeRecord( MarcRecord record ) throws JAXBException, UnsupportedEncodingException {
        MarcXchangeFactory marcXchangeFactory = new MarcXchangeFactory();
        CollectionType marcXchangeCollectionType = marcXchangeFactory.createMarcXchangeFromMarc( record );

        ObjectFactory objectFactory = new ObjectFactory();
        JAXBElement<CollectionType> jAXBElement = objectFactory.createCollection( marcXchangeCollectionType );
        
        JAXBContext jc = JAXBContext.newInstance( CollectionType.class );
        Marshaller marshaller = jc.createMarshaller();
        marshaller.setProperty( Marshaller.JAXB_SCHEMA_LOCATION, "http://www.loc.gov/standards/iso25577/marcxchange-1-1.xsd" );
        
        StringWriter recData = new StringWriter();        
        marshaller.marshal( jAXBElement, recData );
        
        return recData.toString().getBytes( "UTF-8" );
    }
    
}
