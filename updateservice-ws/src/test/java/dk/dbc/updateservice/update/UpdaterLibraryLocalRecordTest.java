//-----------------------------------------------------------------------------
package dk.dbc.updateservice.update;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.rawrepo.RawRepoDAO;
import dk.dbc.rawrepo.Record;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;

//-----------------------------------------------------------------------------
/**
 * Tests the Updater EJB for updates of local library records.
 * 
 * @author stp
 */
public class UpdaterLibraryLocalRecordTest {
    //-------------------------------------------------------------------------
    //              Constructors
    //-------------------------------------------------------------------------

    public UpdaterLibraryLocalRecordTest() {
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
     * Creation of a new local library record.
     * 
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          An empty rawrepo
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update a single local library record that does not exist in 
     *          the rawrepo (create new record).
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          The record is added to the rawrepo.
     *      </dd>
     * </dl>
     * 
     * @throws Exception 
     */
    @Test
    public void testCreateSingleRecord() throws Exception {
        Updater updater = new Updater( rawRepoDAO, null, recordsHandler );
        updater.init();
        
        Record rec = RecordUtils.createRawRecord( "20611529", 700100 );
        MarcRecord recData = RecordUtils.loadMarcRecord( "localrec_single_v1.xml" );
        when( rawRepoDAO.fetchRecord( rec.getId().getBibliographicRecordId(), rec.getId().getAgencyId() ) ).thenReturn( rec );
        
        updater.updateRecord( recData );
        
        // Verify calls to RawRepoDAO
        ArgumentCaptor<Record> argRecord = ArgumentCaptor.forClass( Record.class );
        verify( rawRepoDAO ).saveRecord( argRecord.capture() );
        assertEquals( rec.getId(), argRecord.getValue().getId() );
        assertNotNull( argRecord.getValue().getContent() );
        assertEquals( recData, new Updater().decodeRecord( argRecord.getValue().getContent() ) );
        
        verify( rawRepoDAO, never() ).setRelationsFrom( null, null );        
        verify( rawRepoDAO ).changedRecord( Updater.PROVIDER, rec.getId() );
    }

    /**
     * Update of an existing local library record.
     * 
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with a record, <code>r1</code> with the same id.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update a single local library record, <code>r2</code>, with the same id as 
     *          <code>r1</code>, but with bibliographic changes.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          The content of <code>r1</code> is overwrited by the content of 
     *          <code>r2</code>
     *      </dd>
     * </dl>
     * 
     * @throws Exception 
     */
    @Test
    public void testUpdateSingleRecord() throws Exception {
        Updater updater = new Updater( rawRepoDAO, null, recordsHandler );
        updater.init();
        
        Record rec = RecordUtils.createRawRecord( "localrec_single_v1.xml" );
        when( rawRepoDAO.recordExists( rec.getId().getBibliographicRecordId(), rec.getId().getAgencyId() ) ).thenReturn( true );
        when( rawRepoDAO.fetchRecord( rec.getId().getBibliographicRecordId(), rec.getId().getAgencyId() ) ).thenReturn( rec );
        
        MarcRecord recData = RecordUtils.loadMarcRecord( "localrec_single_v2.xml" );
        updater.updateRecord( recData );
        
        // Verify calls to RawRepoDAO
        ArgumentCaptor<Record> argRecord = ArgumentCaptor.forClass( Record.class );
        verify( rawRepoDAO ).saveRecord( argRecord.capture() );
        assertEquals( rec.getId(), argRecord.getValue().getId() );
        assertNotNull( argRecord.getValue().getContent() );
        assertEquals( recData, updater.decodeRecord( argRecord.getValue().getContent() ) );

        verify( rawRepoDAO, never() ).setRelationsFrom( null, null );        
        verify( rawRepoDAO ).changedRecord( Updater.PROVIDER, rec.getId() );
    }
}
