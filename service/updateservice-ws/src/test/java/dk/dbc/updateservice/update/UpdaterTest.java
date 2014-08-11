//-----------------------------------------------------------------------------
package dk.dbc.updateservice.update;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcFactory;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.rawrepo.RawRepoDAO;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
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

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }    
    
    //-------------------------------------------------------------------------
    //              Tests
    //-------------------------------------------------------------------------

    @Test( expected = NullPointerException.class )
    public void testUpdateRecord_NullRecord() throws Exception {
        Updater updater = new Updater( rawRepoDAO );
        updater.init();
        updater.updateRecord( null );
    }
    
    @Test
    public void testUpdateRecord_SingleValidRecord() throws Exception {
        Updater updater = new Updater( rawRepoDAO );
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
        Updater updater = new Updater( rawRepoDAO );
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
        Updater updater = new Updater( rawRepoDAO );
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
    public void testUpdateRecord_LocalRecord() throws Exception {
        Updater updater = new Updater( rawRepoDAO );
        updater.init();
        
        Record rec = new RawRepoRecordMock( "20611529", 700100 );
        when( rawRepoDAO.fetchRecord( rec.getId().getId(), rec.getId().getLibrary() ) ).thenReturn( rec );
        
        updater.updateRecord( loadMarcRecord( "local_record.xml" ) );
        
        // Verify calls to RawRepoDAO
        ArgumentCaptor<Record> argRecord = ArgumentCaptor.forClass( Record.class );
        verify( rawRepoDAO ).saveRecord( argRecord.capture() );
        assertEquals( rec.getId(), argRecord.getValue().getId() );
        assertTrue( argRecord.getValue().hasContent() );

        verify( rawRepoDAO, never() ).setRelationsFrom( null, null );        
        verify( rawRepoDAO ).changedRecord( Updater.PROVIDER, rec.getId() );
    }
    
    @Test
    public void testUpdateRecord_AssociatedRecord() throws Exception {
        Updater updater = new Updater( rawRepoDAO );
        updater.init();
        
        Record rec = new RawRepoRecordMock( "20611529", 700100 );
        when( rawRepoDAO.fetchRecord( rec.getId().getId(), rec.getId().getLibrary() ) ).thenReturn( rec );
        when( rawRepoDAO.recordExists( rec.getId().getId(), RawRepoDAO.COMMON_LIBRARY ) ).thenReturn( true );
        
        updater.updateRecord( loadMarcRecord( "associated_record.xml" ) );
        
        // Verify calls to RawRepoDAO
        ArgumentCaptor<Record> argRecord = ArgumentCaptor.forClass( Record.class );
        verify( rawRepoDAO ).saveRecord( argRecord.capture() );
        assertEquals( rec.getId(), argRecord.getValue().getId() );
        assertTrue( argRecord.getValue().hasContent() );

        final HashSet<RecordId> references = new HashSet<>();
        references.add( new RecordId( rec.getId().getId(), RawRepoDAO.COMMON_LIBRARY ) );                
        verify( rawRepoDAO ).setRelationsFrom( rec.getId(), references );
        verify( rawRepoDAO ).changedRecord( Updater.PROVIDER, rec.getId() );
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
    
}
