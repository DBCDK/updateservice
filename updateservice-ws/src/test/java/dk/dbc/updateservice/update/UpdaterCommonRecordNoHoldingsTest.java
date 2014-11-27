//-----------------------------------------------------------------------------
package dk.dbc.updateservice.update;

//-----------------------------------------------------------------------------
import dk.dbc.holdingsitems.HoldingsItemsDAO;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.rawrepo.RawRepoDAO;
import dk.dbc.rawrepo.RawRepoException;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.mockito.Matchers.any;

import org.mockito.Mock;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.MockitoAnnotations;

//-----------------------------------------------------------------------------
/**
 * Testing of update of common records, that do not have any holdings.
 * 
 * @author stp
 */
public class UpdaterCommonRecordNoHoldingsTest {
    //-------------------------------------------------------------------------
    //              Constructors
    //-------------------------------------------------------------------------

    public UpdaterCommonRecordNoHoldingsTest() {
    }
    
    @Mock
    RawRepoDAO rawRepoDAO;
    
    @Mock
    HoldingsItemsDAO holdingsItemsDAO;
    
    @Mock
    LibraryRecordsHandler recordsHandler;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }    
    
    //-------------------------------------------------------------------------
    //              Tests of updateRecord arguments
    //-------------------------------------------------------------------------
   
    /**
     * Create a new single record with no local record.
     * 
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          An empty rawrepo
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Create a new single record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          The record is created in the rawrepo.
     *      </dd>
     * </dl>
     * 
     * @throws Exception 
     */
    @Test
    public void testCreateSingleRecord() throws Exception {
        Updater updater = new Updater( rawRepoDAO, holdingsItemsDAO, recordsHandler );
        updater.init();
        
        Record rec = RecordUtils.createRawRecord( "20611529", 870970 );
        MarcRecord recData = RecordUtils.loadMarcRecord( "dbcrec_single.xml" );
        when( holdingsItemsDAO.getAgenciesThatHasHoldingsFor( any( String.class) ) ).thenReturn( new HashSet<Integer>() );
        when( recordsHandler.hasClassificationsChanged(any(MarcRecord.class), any(MarcRecord.class)) ).thenReturn( true );
        when( recordsHandler.updateRecordForUpdate(recData) ).thenReturn( recData );
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
     * Create a new single record with an existing local record.
     * 
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A local library record in the rawrepo.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Create a new common single record with the same id as the 
     *          local record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Throw UpdateException.
     *      </dd>
     * </dl>
     * 
     * @throws Exception 
     */    
    @Test( expected = UpdateException.class )
    public void testCreateSingleRecord_WithLocalRecord() throws Exception {
        Updater updater = new Updater( rawRepoDAO, holdingsItemsDAO, recordsHandler );
        updater.init();
        
        Record rec = RecordUtils.createRawRecord( "20611529", 700100 );
        HashSet<Integer> localLibraries = new HashSet<>();
        localLibraries.add( 700100 );

        MarcRecord recData = RecordUtils.loadMarcRecord( "dbcrec_single.xml" );

        when( holdingsItemsDAO.getAgenciesThatHasHoldingsFor( any( String.class) ) ).thenReturn( new HashSet<Integer>() );
        when( rawRepoDAO.allAgenciesForBibliographicRecordId("20611529") ).thenReturn( localLibraries );
        when( rawRepoDAO.fetchRecord(rec.getId().getBibliographicRecordId(), rec.getId().getAgencyId()) ).thenReturn( rec );
        when( recordsHandler.updateRecordForUpdate(recData) ).thenReturn( recData );

        updater.updateRecord( recData );
    }
    
    /**
     * Try to create a volume record that points to a main record, that does 
     * not exist.
     * 
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          An empty rawrepo
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update a volume record with a reference to a main record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Throw UpdateException.
     *      </dd>
     * </dl>
     * 
     * @throws Exception 
     */
    @Test( expected = UpdateException.class )
    public void testCreateVolumeRecord_WithUnknownParent() throws Exception {
        Updater updater = new Updater( rawRepoDAO, holdingsItemsDAO, recordsHandler );
        updater.init();

        MarcRecord recData = RecordUtils.loadMarcRecord( "dbcrec_volume.xml" );

        Record rec = RecordUtils.createRawRecord( "58442895", 870970 );
        when( holdingsItemsDAO.getAgenciesThatHasHoldingsFor( any( String.class) ) ).thenReturn( new HashSet<Integer>() );
        when( recordsHandler.hasClassificationsChanged(any(MarcRecord.class), any(MarcRecord.class)) ).thenReturn( false );
        when( recordsHandler.updateRecordForUpdate(recData) ).thenReturn( recData );
        when( rawRepoDAO.fetchRecord( rec.getId().getBibliographicRecordId(), rec.getId().getAgencyId() ) ).thenReturn( rec );
        
        final HashSet<RecordId> references = new HashSet<>();
        references.add( new RecordId( "58442615", 870970 ) );        
        doThrow( new RawRepoException() ).when( rawRepoDAO ).setRelationsFrom( rec.getId(), references ); 
        
        updater.updateRecord( recData );
    }

    /**
     * Create a new volume record in a rawrepo, that points to an existing 
     * main record.
     * 
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with a main record.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Create a new volume record, that points to the main record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          <ol>
     *              <li>The volume record is stored in rawrepo</li>
     *              <li>
     *                  There is a relation from the main record to the volume 
     *                  record
     *              </li>
     *          </ol>
     *      </dd>
     * </dl>
     * 
     * @throws Exception 
     */
    @Test
    public void testCreateVolumeRecord_WithValidParent() throws Exception {
        Updater updater = new Updater( rawRepoDAO, holdingsItemsDAO, recordsHandler );
        updater.init();
        
        Record rec = RecordUtils.createRawRecord( "58442895", 870970 );
        MarcRecord recData = RecordUtils.loadMarcRecord( "dbcrec_volume.xml" );
        when( holdingsItemsDAO.getAgenciesThatHasHoldingsFor( any( String.class) ) ).thenReturn( new HashSet<Integer>() );
        when( recordsHandler.hasClassificationsChanged(any(MarcRecord.class), any(MarcRecord.class)) ).thenReturn( false );
        when( recordsHandler.updateRecordForUpdate(recData) ).thenReturn( recData );
        when( rawRepoDAO.fetchRecord( rec.getId().getBibliographicRecordId(), rec.getId().getAgencyId() ) ).thenReturn( rec );
        when( rawRepoDAO.recordExists( "58442615", 870970 ) ).thenReturn( true );
        
        updater.updateRecord( recData );
        
        // Verify calls to RawRepoDAO
        ArgumentCaptor<Record> argRecord = ArgumentCaptor.forClass( Record.class );
        verify( rawRepoDAO ).saveRecord( argRecord.capture() );
        assertEquals( rec.getId(), argRecord.getValue().getId() );
        assertNotNull( argRecord.getValue().getContent() );
        assertEquals( recData, new Updater().decodeRecord( argRecord.getValue().getContent() ) );

        final HashSet<RecordId> references = new HashSet<>();
        references.add( new RecordId( "58442615", 870970 ) );                
        verify( rawRepoDAO ).setRelationsFrom( rec.getId(), references );
        
        verify( rawRepoDAO ).changedRecord( Updater.PROVIDER, rec.getId() );
    }
    
    /**
     * Update an existing single record with existing extended records.
     * 
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          Rawrepo with:
     *          <ul>
     *              <li>Single record with data, <code>r1</code></li>
     *              <li>
     *                  <code>r1</code> has an extended record, <code>e</code>
     *              </li>
     *          </ul>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update the common record <code>r1</code> with <code>r2</code>
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          <ol>
     *              <li>Overwrite record <code>r1</code> with <code>r2</code></li>
     *              <li>Put record <code>e</code> in the job queue.</li>
     *          </ol>
     *      </dd>
     * </dl>
     * 
     * @throws Exception 
     */    
    @Test
    public void testUpdateSingleRecord_WithExtendedRecord() throws Exception {
        Updater updater = new Updater( rawRepoDAO, holdingsItemsDAO, recordsHandler );
        updater.init();

        MarcRecord recData = RecordUtils.loadMarcRecord( "dbcrec_single_v1.xml" );

        Record commonRec = RecordUtils.createRawRecord( RecordUtils.loadMarcRecord( "dbcrec_single.xml" ) );
        Record extRec = RecordUtils.createRawRecord( RecordUtils.loadMarcRecord( "extrec_single.xml" ) );
        Set<Integer> extLibraries = new HashSet<>();
        extLibraries.add( 700100 );
        when( holdingsItemsDAO.getAgenciesThatHasHoldingsFor( any( String.class) ) ).thenReturn(new HashSet<Integer>());
        when( recordsHandler.hasClassificationsChanged(any(MarcRecord.class), any(MarcRecord.class)) ).thenReturn( false );
        when( recordsHandler.updateRecordForUpdate(recData) ).thenReturn( recData );
        when( rawRepoDAO.recordExists( commonRec.getId().getBibliographicRecordId(), commonRec.getId().getAgencyId() ) ).thenReturn( true );
        when( rawRepoDAO.fetchRecord( commonRec.getId().getBibliographicRecordId(), commonRec.getId().getAgencyId() ) ).thenReturn( commonRec );
        when( rawRepoDAO.recordExists( extRec.getId().getBibliographicRecordId(), extRec.getId().getAgencyId() ) ).thenReturn( true );
        when( rawRepoDAO.fetchRecord( extRec.getId().getBibliographicRecordId(), extRec.getId().getAgencyId() ) ).thenReturn( extRec );
        when( rawRepoDAO.allAgenciesForBibliographicRecordId( commonRec.getId().getBibliographicRecordId() ) ).thenReturn( extLibraries );
        
        updater.updateRecord( recData );
        
        // Verify calls to RawRepoDAO
        InOrder rawRepoOrder = inOrder( rawRepoDAO );

        ArgumentCaptor<Record> argRecord = ArgumentCaptor.forClass( Record.class );
        rawRepoOrder.verify( rawRepoDAO ).saveRecord( argRecord.capture() );
        assertEquals( commonRec.getId(), argRecord.getValue().getId() );
        assertNotNull( argRecord.getValue().getContent() );
        assertEquals( recData, new Updater().decodeRecord( argRecord.getValue().getContent() ) );
        
        rawRepoOrder.verify( rawRepoDAO ).changedRecord( Updater.PROVIDER, commonRec.getId() );
        //rawRepoOrder.verify( rawRepoDAO ).enqueue( extRec.getId(), Updater.PROVIDER, true, true );
    }
}
