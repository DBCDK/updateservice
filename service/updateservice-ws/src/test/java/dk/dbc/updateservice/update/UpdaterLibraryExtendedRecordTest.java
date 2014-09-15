//-----------------------------------------------------------------------------
package dk.dbc.updateservice.update;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.rawrepo.RawRepoDAO;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import java.util.HashSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Matchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;

//-----------------------------------------------------------------------------
/**
 * Tests the Updater EJB for updates of library extended records.
 * 
 * @author stp
 */
public class UpdaterLibraryExtendedRecordTest {
    //-------------------------------------------------------------------------
    //              Constructors
    //-------------------------------------------------------------------------

    public UpdaterLibraryExtendedRecordTest() {
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
    //              Test methods
    //-------------------------------------------------------------------------
    
    /**
     * Creation of a new extended library record.
     * 
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A repo with a common record, <code>c</code>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update a single extended library record, <code>e</code>, that 
     *          does not exist in the rawrepo and has a same id as 
     *          <code>c</code>.
     *          <p/>
     *          <code>e</code> contains its own classification data.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          <ol>
     *              <li>Record <code>e</code> is added to the rawrepo</li>
     *              <li>
     *                  A relation is created from <code>c</code> to 
     *                  <code>e</code>
     *              </li>
     *              <li>Record <code>e</code> is added to the job queue.</li>
     *          </ol>
     *      </dd>
     * </dl>
     * 
     * @throws Exception 
     */
    @Test
    public void testCreateRecord_WithClassificationData() throws Exception {
        Updater updater = new Updater( rawRepoDAO, null, recordsHandler );
        updater.init();
        
        MarcRecord dbcRec = RecordUtils.loadMarcRecord( "dbcrec_single.xml" );
        MarcRecord extRec = RecordUtils.loadMarcRecord( "extrec_single.xml" );
        
        Record rawDbcRec = RecordUtils.createRawRecord( dbcRec );
        Record rawExtRec = RecordUtils.createRawRecord( "20611529", 700100 );
        when( rawRepoDAO.fetchRecord( rawDbcRec.getId().getId(), rawDbcRec.getId().getLibrary() ) ).thenReturn( rawDbcRec );
        when( rawRepoDAO.fetchRecord( rawExtRec.getId().getId(), rawExtRec.getId().getLibrary() ) ).thenReturn( rawExtRec );
        when( rawRepoDAO.recordExists( rawExtRec.getId().getId(), RawRepoDAO.COMMON_LIBRARY ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( any( MarcRecord.class ), any( MarcRecord.class ) ) ).thenReturn( false );
        when( recordsHandler.correctLibraryExtendedRecord( dbcRec, extRec ) ).thenReturn( extRec );
        
        updater.updateRecord( extRec );
        
        // Verify update calls
        verify( recordsHandler ).correctLibraryExtendedRecord( dbcRec, extRec );
        
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

    /**
     * Tests update of a library extended record with no classification data.
     * 
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          An individual common record with no holdings.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Create a new library extended record with no classification 
     *          data for the common record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          <ol>
     *              <li>
     *                  The library extended record is updated in the rawrepo 
     *                  with null content.
     *              </li>
     *              <li>
     *                  The library extended record is related to the common 
     *                  record.
     *              </li>
     *              <li>
     *                  The library extended record is enqueued in the job 
     *                  queue.
     *              </li>
     *          </ol>
     *      </dd>
     * </dl>
     * 
     * @throws Exception 
     */
    @Test
    public void testCreateRecord_WithNoClassificationData() throws Exception {
        Updater updater = new Updater( rawRepoDAO, null, recordsHandler );
        updater.init();
        
        MarcRecord dbcRec = RecordUtils.loadMarcRecord( "dbcrec_single.xml" );
        MarcRecord extRec = RecordUtils.loadMarcRecord( "extrec_single.xml" );
        MarcRecord emptyRec = RecordUtils.createMarcRecord();
        
        Record rawDbcRec = RecordUtils.createRawRecord( dbcRec );
        Record rawExtRec = RecordUtils.createRawRecord( "20611529", 700100 );
        when( rawRepoDAO.fetchRecord( rawDbcRec.getId().getId(), rawDbcRec.getId().getLibrary() ) ).thenReturn( rawDbcRec );
        when( rawRepoDAO.fetchRecord( rawExtRec.getId().getId(), rawExtRec.getId().getLibrary() ) ).thenReturn( rawExtRec );
        when( rawRepoDAO.recordExists( rawExtRec.getId().getId(), RawRepoDAO.COMMON_LIBRARY ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( any( MarcRecord.class ), any( MarcRecord.class ) ) ).thenReturn( false );
        when( recordsHandler.correctLibraryExtendedRecord( dbcRec, extRec ) ).thenReturn( emptyRec );
        
        updater.updateRecord( extRec );
        
        // Verify update calls
        verify( recordsHandler ).correctLibraryExtendedRecord( dbcRec, extRec );
        
        /*
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
        */
    }
    
}
