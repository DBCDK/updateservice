//-----------------------------------------------------------------------------
package dk.dbc.updateservice.update;

//-----------------------------------------------------------------------------
import dk.dbc.holdingsitems.HoldingsItemsDAO;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.rawrepo.RawRepoDAO;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import static org.mockito.Matchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;



//-----------------------------------------------------------------------------
/**
 * Testing of update of common records with holdings.
 * 
 * @author stp
 */
public class UpdaterCommonRecordWithHoldingsTest {
    //-------------------------------------------------------------------------
    //              Constructors
    //-------------------------------------------------------------------------

    public UpdaterCommonRecordWithHoldingsTest() {
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
    //              Test methods
    //-------------------------------------------------------------------------

    /**
     * Update a single record with classification data, but with no content 
     * in rawrepo and with no extended records to it.
     * 
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          <ol>
     *              <li>
     *                  Single record with no content, but with holdings for 
     *                  a single library.
     *              </li>
     *              <li>No extended records.</li>
     *          </ol>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update the single record with new classification data.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          The single record is simply updated in the rawrepo. No extended 
     *          records are created or updated.
     *      </dd>
     * </dl>
     * 
     * @throws Exception 
     */
    @Test
    public void testUpdateSingleRecord_NoExistingClassification_NoExtendedRecords() throws Exception {
        Updater updater = new Updater( rawRepoDAO, holdingsItemsDAO, recordsHandler );
        updater.init();
        
        Record commonRec = RecordUtils.createRawRecord( "20611529", 870970 );
        MarcRecord oldCommonRecData = RecordUtils.createMarcRecord();
        MarcRecord recData = RecordUtils.loadMarcRecord( "dbcrec_single.xml" );
        Record extRecord = RecordUtils.createRawRecord( commonRec.getId().getId(), 700100 );
        
        when( recordsHandler.hasClassificationData( oldCommonRecData ) ).thenReturn( false );
        when( recordsHandler.hasClassificationData( recData ) ).thenReturn( true );
        when( rawRepoDAO.fetchRecord( commonRec.getId().getId(), commonRec.getId().getLibrary() ) ).thenReturn( commonRec );
        when( rawRepoDAO.fetchRecord( extRecord.getId().getId(), extRecord.getId().getLibrary() ) ).thenReturn( extRecord );
        when( rawRepoDAO.recordExists( commonRec.getId().getId(), commonRec.getId().getLibrary() ) ).thenReturn( true );
        when( rawRepoDAO.recordExists( commonRec.getId().getId(), extRecord.getId().getLibrary() ) ).thenReturn( false );
        when( recordsHandler.createLibraryExtendedRecord( oldCommonRecData, extRecord.getId().getLibrary() ) ).thenReturn( RecordUtils.loadMarcRecord( "extrec_single.xml" ) );
        
        Set<Integer> holdingsItems = new HashSet<>();
        holdingsItems.add( extRecord.getId().getLibrary() );        
        when( holdingsItemsDAO.getAgenciesThatHasHoldingsFor( commonRec.getId().getId() ) ).thenReturn( holdingsItems );
        
        updater.updateRecord( recData );
        
        // Verify calls to update common record
        InOrder rawRepoOrder = inOrder( rawRepoDAO );
        
        ArgumentCaptor<Record> argRecord = ArgumentCaptor.forClass( Record.class );
        rawRepoOrder.verify( rawRepoDAO ).saveRecord( argRecord.capture() );
        assertEquals( commonRec.getId(), argRecord.getValue().getId() );
        assertTrue( argRecord.getValue().hasContent() );
        assertEquals( recData, new Updater().decodeRecord( argRecord.getValue().getContent() ) );

        rawRepoOrder.verify( rawRepoDAO ).changedRecord( Updater.PROVIDER, commonRec.getId() );

        // Verify calls to create extended record
        rawRepoOrder.verify( rawRepoDAO, never() ).saveRecord( any( Record.class ) );
    
        rawRepoOrder.verify( rawRepoDAO, never() ).setRelationsFrom( any( RecordId.class ), any( HashSet.class ) );
        rawRepoOrder.verify( rawRepoDAO, never() ).changedRecord( any( String.class ), any( RecordId.class ) );
        rawRepoOrder.verify( rawRepoDAO, never() ).enqueue( any( RecordId.class ), any( String.class ), any( Boolean.class ), any( Boolean.class ) );
    }

    /**
     * Update a single record without classification changes, and without 
     * extended records to it.
     * 
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          <ol>
     *              <li>
     *                  Single record with no content, but with holdings for 
     *                  a single library.
     *              </li>
     *              <li>No extended records.</li>
     *          </ol>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update the single record with new classification data.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          The single record is simply updated in the rawrepo. No extended 
     *          records are created or updated.
     *      </dd>
     * </dl>
     * 
     * @throws Exception 
     */
    @Test
    public void testUpdateSingleRecord_ClassificationTheSame_NoExtendedRecord() throws Exception {
        Updater updater = new Updater( rawRepoDAO, holdingsItemsDAO, recordsHandler );
        updater.init();
        
        MarcRecord oldCommonRecData = RecordUtils.loadMarcRecord( "dbcrec_single_v1.xml");
        MarcRecord recData = RecordUtils.loadMarcRecord( "dbcrec_single.xml" );
        
        Record commonRec = RecordUtils.createRawRecord( oldCommonRecData );
        Record extRecord = RecordUtils.createRawRecord( commonRec.getId().getId(), 700100 );
        
        when( recordsHandler.hasClassificationData( oldCommonRecData ) ).thenReturn( true );
        when( recordsHandler.hasClassificationData( recData ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( any( MarcRecord.class ), any( MarcRecord.class ) ) ).thenReturn( false );
        when( rawRepoDAO.fetchRecord( commonRec.getId().getId(), commonRec.getId().getLibrary() ) ).thenReturn( commonRec );
        when( rawRepoDAO.fetchRecord( extRecord.getId().getId(), extRecord.getId().getLibrary() ) ).thenReturn( extRecord );
        when( rawRepoDAO.recordExists( commonRec.getId().getId(), commonRec.getId().getLibrary() ) ).thenReturn( true );
        when( rawRepoDAO.recordExists( commonRec.getId().getId(), extRecord.getId().getLibrary() ) ).thenReturn( false );
        
        Set<Integer> holdingsItems = new HashSet<>();
        holdingsItems.add( extRecord.getId().getLibrary() );        
        when( holdingsItemsDAO.getAgenciesThatHasHoldingsFor( commonRec.getId().getId() ) ).thenReturn( holdingsItems );
        
        updater.updateRecord( recData );
        
        // Verify calls to update common record
        InOrder rawRepoOrder = inOrder( rawRepoDAO );
        
        ArgumentCaptor<Record> argRecord = ArgumentCaptor.forClass( Record.class );
        rawRepoOrder.verify( rawRepoDAO ).saveRecord( argRecord.capture() );
        assertEquals( commonRec.getId(), argRecord.getValue().getId() );
        assertTrue( argRecord.getValue().hasContent() );
        assertEquals( recData, new Updater().decodeRecord( argRecord.getValue().getContent() ) );

        rawRepoOrder.verify( rawRepoDAO ).changedRecord( Updater.PROVIDER, commonRec.getId() );

        // Verify that an extended record is *not* created.
        rawRepoOrder.verify( rawRepoDAO, never() ).saveRecord( any( Record.class ) );    
        rawRepoOrder.verify( rawRepoDAO, never() ).setRelationsFrom( any( RecordId.class ), any( HashSet.class ) );
        rawRepoOrder.verify( rawRepoDAO, never() ).changedRecord( any( String.class ), any( RecordId.class ) );
        rawRepoOrder.verify( rawRepoDAO, never() ).enqueue( any( RecordId.class ), any( String.class ), any( Boolean.class ), any( Boolean.class ) );
    }

    /**
     * Update a single record without classification changes, and with 
     * extended records to it.
     * 
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          <ol>
     *              <li>
     *                  Single record with no content, but with holdings for 
     *                  a single library.
     *              </li>
     *              <li>No extended records.</li>
     *          </ol>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update the single record with new classification data.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          The single record is simply updated in the rawrepo. No extended 
     *          records are created or updated.
     *      </dd>
     * </dl>
     * 
     * @throws Exception 
     */
    @Test
    public void testUpdateSingleRecord_ClassificationTheSame_ExtendedRecord() throws Exception {
        Updater updater = new Updater( rawRepoDAO, holdingsItemsDAO, recordsHandler );
        updater.init();
        
        MarcRecord oldCommonRecData = RecordUtils.loadMarcRecord( "dbcrec_single_v1.xml");
        MarcRecord recData = RecordUtils.loadMarcRecord( "dbcrec_single.xml" );
        MarcRecord extRecData = RecordUtils.loadMarcRecord( "extrec_single.xml");
        
        Record commonRec = RecordUtils.createRawRecord( oldCommonRecData );
        Record extRecord = RecordUtils.createRawRecord( extRecData ); 
        
        when( recordsHandler.hasClassificationData( oldCommonRecData ) ).thenReturn( true );
        when( recordsHandler.hasClassificationData( recData ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( any( MarcRecord.class ), any( MarcRecord.class ) ) ).thenReturn( false );
        when( rawRepoDAO.fetchRecord( commonRec.getId().getId(), commonRec.getId().getLibrary() ) ).thenReturn( commonRec );
        when( rawRepoDAO.fetchRecord( extRecord.getId().getId(), extRecord.getId().getLibrary() ) ).thenReturn( extRecord );
        when( rawRepoDAO.recordExists( commonRec.getId().getId(), commonRec.getId().getLibrary() ) ).thenReturn( true );
        when( rawRepoDAO.recordExists( commonRec.getId().getId(), extRecord.getId().getLibrary() ) ).thenReturn( true );
        when( recordsHandler.hasClassificationData( extRecData ) ).thenReturn( false );
        
        Set<Integer> holdingsItems = new HashSet<>();
        holdingsItems.add( extRecord.getId().getLibrary() );        
        when( holdingsItemsDAO.getAgenciesThatHasHoldingsFor( commonRec.getId().getId() ) ).thenReturn( holdingsItems );
        
        updater.updateRecord( recData );
        
        // Verify calls to update common record
        InOrder rawRepoOrder = inOrder( rawRepoDAO, recordsHandler );
        
        ArgumentCaptor<Record> argRecord = ArgumentCaptor.forClass( Record.class );
        rawRepoOrder.verify( rawRepoDAO ).saveRecord( argRecord.capture() );
        assertEquals( commonRec.getId(), argRecord.getValue().getId() );
        assertTrue( argRecord.getValue().hasContent() );
        assertEquals( recData, new Updater().decodeRecord( argRecord.getValue().getContent() ) );

        rawRepoOrder.verify( rawRepoDAO ).changedRecord( Updater.PROVIDER, commonRec.getId() );

        // Verify that an extended record is *not* updated.
        rawRepoOrder.verify( rawRepoDAO, never() ).saveRecord( any( Record.class ) );    
        rawRepoOrder.verify( rawRepoDAO, never() ).setRelationsFrom( any( RecordId.class ), any( HashSet.class ) );
        rawRepoOrder.verify( rawRepoDAO, never() ).changedRecord( any( String.class ), any( RecordId.class ) );
        rawRepoOrder.verify( rawRepoDAO, never() ).enqueue( any( RecordId.class ), any( String.class ), any( Boolean.class ), any( Boolean.class ) );
    }

    /**
     * Update a single record with classification data, but with no content 
     * in rawrepo and with extended records to it.
     * 
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          <ol>
     *              <li>
     *                  Single record with no content, but with holdings for 
     *                  a single library.
     *              </li>
     *              <li>No extended records.</li>
     *          </ol>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update the single record with new classification data.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          The single record is simply updated in the rawrepo. No extended 
     *          records are created or updated.
     *      </dd>
     * </dl>
     * 
     * @throws Exception 
     */
    @Test
    public void testUpdateSingleRecord_NoExistingClassification_ExtendedRecord() throws Exception {
        Updater updater = new Updater( rawRepoDAO, holdingsItemsDAO, recordsHandler );
        updater.init();
        
        Record commonRec = RecordUtils.createRawRecord( "20611529", 870970 );
        MarcRecord oldCommonRecData = RecordUtils.createMarcRecord();
        MarcRecord recData = RecordUtils.loadMarcRecord( "dbcrec_single.xml" );

        MarcRecord extRecData = RecordUtils.loadMarcRecord( "extrec_single.xml" );
        Record extRecord = RecordUtils.createRawRecord( extRecData );
        
        when( recordsHandler.hasClassificationData( oldCommonRecData ) ).thenReturn( false );
        when( recordsHandler.hasClassificationData( recData ) ).thenReturn( true );
        when( rawRepoDAO.fetchRecord( commonRec.getId().getId(), commonRec.getId().getLibrary() ) ).thenReturn( commonRec );
        when( rawRepoDAO.fetchRecord( extRecord.getId().getId(), extRecord.getId().getLibrary() ) ).thenReturn( extRecord );
        when( rawRepoDAO.recordExists( commonRec.getId().getId(), commonRec.getId().getLibrary() ) ).thenReturn( true );
        when( rawRepoDAO.recordExists( extRecord.getId().getId(), extRecord.getId().getLibrary() ) ).thenReturn( true );
        
        Set<Integer> holdingsItems = new HashSet<>();
        holdingsItems.add( extRecord.getId().getLibrary() );        
        when( holdingsItemsDAO.getAgenciesThatHasHoldingsFor( commonRec.getId().getId() ) ).thenReturn( holdingsItems );
        
        updater.updateRecord( recData );
        
        // Verify calls to update common record
        InOrder rawRepoOrder = inOrder( rawRepoDAO );
        
        ArgumentCaptor<Record> argRecord = ArgumentCaptor.forClass( Record.class );
        rawRepoOrder.verify( rawRepoDAO ).saveRecord( argRecord.capture() );
        assertEquals( commonRec.getId(), argRecord.getValue().getId() );
        assertTrue( argRecord.getValue().hasContent() );
        assertEquals( recData, new Updater().decodeRecord( argRecord.getValue().getContent() ) );

        rawRepoOrder.verify( rawRepoDAO ).changedRecord( Updater.PROVIDER, commonRec.getId() );

        // Verify calls to create extended record
        rawRepoOrder.verify( rawRepoDAO, never() ).saveRecord( any( Record.class ) );
    
        rawRepoOrder.verify( rawRepoDAO, never() ).setRelationsFrom( any( RecordId.class ), any( HashSet.class ) );
        rawRepoOrder.verify( rawRepoDAO, never() ).changedRecord( any( String.class ), any( RecordId.class ) );
        rawRepoOrder.verify( rawRepoDAO, never() ).enqueue( any( RecordId.class ), any( String.class ), any( Boolean.class ), any( Boolean.class ) );
    }
    
    /**
     * Update a single record with classification change and with no extended 
     * records to it.
     * 
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          <ol>
     *              <li>
     *                  Single record with existing content and holdings for 
     *                  a single library.
     *              </li>
     *              <li>No extended records.</li>
     *          </ol>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update the single record with new classification data.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          <ol>
     *              <li>The single record is updated in the rawrepo.</li> 
     *              <li>An extended record is created for the library/</li>
     *              <li>
     *                  A relation is created between the common record and 
     *                  the extended record.
     *              </li>
     *              <li>The extended record is added to the job queue.</li>
     *          </ol>
     *      </dd>
     * </dl>
     * 
     * @throws Exception 
     */
    @Test
    public void testUpdateSingleRecord_ClassificationChange_NoExtendedRecords() throws Exception {
        Updater updater = new Updater( rawRepoDAO, holdingsItemsDAO, recordsHandler );
        updater.init();
        
        MarcRecord oldCommonRecData = RecordUtils.loadMarcRecord( "dbcrec_single_v1.xml");
        MarcRecord recData = RecordUtils.loadMarcRecord( "dbcrec_single.xml" );
        
        Record commonRec = RecordUtils.createRawRecord( oldCommonRecData );
        Record extRecord = RecordUtils.createRawRecord( commonRec.getId().getId(), 700100 );
        
        when( recordsHandler.hasClassificationData( oldCommonRecData ) ).thenReturn( true );
        when( recordsHandler.hasClassificationData( recData ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( any( MarcRecord.class ), any( MarcRecord.class ) ) ).thenReturn( true );
        when( rawRepoDAO.fetchRecord( commonRec.getId().getId(), commonRec.getId().getLibrary() ) ).thenReturn( commonRec );
        when( rawRepoDAO.fetchRecord( extRecord.getId().getId(), extRecord.getId().getLibrary() ) ).thenReturn( extRecord );
        when( rawRepoDAO.recordExists( commonRec.getId().getId(), commonRec.getId().getLibrary() ) ).thenReturn( true );
        when( rawRepoDAO.recordExists( commonRec.getId().getId(), extRecord.getId().getLibrary() ) ).thenReturn( false );
        when( recordsHandler.createLibraryExtendedRecord( oldCommonRecData, extRecord.getId().getLibrary() ) ).thenReturn( RecordUtils.loadMarcRecord( "extrec_single.xml" ) );
        
        Set<Integer> holdingsItems = new HashSet<>();
        holdingsItems.add( extRecord.getId().getLibrary() );        
        when( holdingsItemsDAO.getAgenciesThatHasHoldingsFor( commonRec.getId().getId() ) ).thenReturn( holdingsItems );
        
        updater.updateRecord( recData );
        
        // Verify calls to update common record
        InOrder rawRepoOrder = inOrder( rawRepoDAO );
        
        ArgumentCaptor<Record> argRecord = ArgumentCaptor.forClass( Record.class );
        rawRepoOrder.verify( rawRepoDAO ).saveRecord( argRecord.capture() );
        assertEquals( commonRec.getId(), argRecord.getValue().getId() );
        assertTrue( argRecord.getValue().hasContent() );
        assertEquals( recData, new Updater().decodeRecord( argRecord.getValue().getContent() ) );

        rawRepoOrder.verify( rawRepoDAO ).changedRecord( Updater.PROVIDER, commonRec.getId() );

        // Verify calls to create extended record
        rawRepoOrder.verify( rawRepoDAO ).saveRecord( argRecord.capture() );
        assertEquals( extRecord.getId(), argRecord.getValue().getId() );
        assertTrue( argRecord.getValue().hasContent() );
    
        final HashSet<RecordId> references = new HashSet<>();
        references.add( new RecordId( extRecord.getId().getId(), RawRepoDAO.COMMON_LIBRARY ) );
        rawRepoOrder.verify( rawRepoDAO ).setRelationsFrom( extRecord.getId(), references );
        rawRepoOrder.verify( rawRepoDAO ).changedRecord( Updater.PROVIDER, extRecord.getId() );
        rawRepoOrder.verify( rawRepoDAO ).enqueue( extRecord.getId(), Updater.PROVIDER, true, true );
    }

    /**
     * Update a single record with classification data and with existing 
     * extended records with classification to it.
     * 
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          <ol>
     *              <li>
     *                  Single record with existing content and holdings for 
     *                  a single library.
     *              </li>
     *              <li>
     *                  An existing extended record for the library. The record
     *                  contains classification data.
     *              </li>
     *          </ol>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update the single record with new classification data.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          <ol>
     *              <li>The single record is updated in the rawrepo.</li> 
     *              <li>
     *                  A relation is created between the common record and 
     *                  the extended record.
     *              </li>
     *              <li>The extended record is added to the job queue.</li>
     *          </ol>
     *      </dd>
     * </dl>
     * 
     * @throws Exception 
     */    
    @Test
    public void testUpdateSingleRecord_ClassificationChange_ExtendedRecordWithClassification() throws Exception {
        Updater updater = new Updater( rawRepoDAO, holdingsItemsDAO, recordsHandler );
        updater.init();
        
        MarcRecord oldCommonRecData = RecordUtils.loadMarcRecord( "dbcrec_single_v1.xml");
        MarcRecord recData = RecordUtils.loadMarcRecord( "dbcrec_single.xml" );
        MarcRecord extRecData = RecordUtils.loadMarcRecord( "extrec_single.xml");
        
        Record commonRec = RecordUtils.createRawRecord( oldCommonRecData );
        Record extRecord = RecordUtils.createRawRecord( extRecData ); 
        
        when( recordsHandler.hasClassificationData( oldCommonRecData ) ).thenReturn( true );
        when( recordsHandler.hasClassificationData( recData ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( any( MarcRecord.class ), any( MarcRecord.class ) ) ).thenReturn( true );
        when( rawRepoDAO.fetchRecord( commonRec.getId().getId(), commonRec.getId().getLibrary() ) ).thenReturn( commonRec );
        when( rawRepoDAO.fetchRecord( extRecord.getId().getId(), extRecord.getId().getLibrary() ) ).thenReturn( extRecord );
        when( rawRepoDAO.recordExists( commonRec.getId().getId(), commonRec.getId().getLibrary() ) ).thenReturn( true );
        when( rawRepoDAO.recordExists( commonRec.getId().getId(), extRecord.getId().getLibrary() ) ).thenReturn( true );
        when( recordsHandler.hasClassificationData( extRecData ) ).thenReturn( true );
        
        Set<Integer> holdingsItems = new HashSet<>();
        holdingsItems.add( extRecord.getId().getLibrary() );        
        when( holdingsItemsDAO.getAgenciesThatHasHoldingsFor( commonRec.getId().getId() ) ).thenReturn( holdingsItems );
        
        updater.updateRecord( recData );
        
        // Verify calls to update common record
        InOrder rawRepoOrder = inOrder( rawRepoDAO, recordsHandler );
        
        ArgumentCaptor<Record> argRecord = ArgumentCaptor.forClass( Record.class );
        rawRepoOrder.verify( rawRepoDAO ).saveRecord( argRecord.capture() );
        assertEquals( commonRec.getId(), argRecord.getValue().getId() );
        assertTrue( argRecord.getValue().hasContent() );
        assertEquals( recData, new Updater().decodeRecord( argRecord.getValue().getContent() ) );

        rawRepoOrder.verify( rawRepoDAO ).changedRecord( Updater.PROVIDER, commonRec.getId() );

        // Verify calls to create extended record
        rawRepoOrder.verify( recordsHandler ).hasClassificationData( extRecData );
        verify( recordsHandler, never() ).updateLibraryExtendedRecord( oldCommonRecData, extRecData );
        
        rawRepoOrder.verify( rawRepoDAO ).enqueue( extRecord.getId(), Updater.PROVIDER, true, true );
    }
    
    /**
     * Update a single record with classification data and with existing 
     * extended records without classification to it.
     * 
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          <ol>
     *              <li>
     *                  Single record with existing content and holdings for 
     *                  a single library.
     *              </li>
     *              <li>
     *                  An existing extended record for the library. The record
     *                  does not contain classification data.
     *              </li>
     *          </ol>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update the single record with new classification data.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          <ol>
     *              <li>The single record is updated in the rawrepo.</li> 
     *              <li>
     *                  A relation is created between the common record and 
     *                  the extended record.
     *              </li>
     *              <li>The extended record is added to the job queue.</li>
     *          </ol>
     *      </dd>
     * </dl>
     * 
     * @throws Exception 
     */    
    @Test
    public void testUpdateSingleRecord_ClassificationChange_ExtendedRecordWithNoClassification() throws Exception {
        Updater updater = new Updater( rawRepoDAO, holdingsItemsDAO, recordsHandler );
        updater.init();
        
        MarcRecord oldCommonRecData = RecordUtils.loadMarcRecord( "dbcrec_single_v1.xml");
        MarcRecord recData = RecordUtils.loadMarcRecord( "dbcrec_single.xml" );
        MarcRecord extRecData = RecordUtils.loadMarcRecord( "extrec_single.xml");
        
        Record commonRec = RecordUtils.createRawRecord( oldCommonRecData );
        Record extRecord = RecordUtils.createRawRecord( extRecData ); 
        
        when( recordsHandler.hasClassificationData( oldCommonRecData ) ).thenReturn( true );
        when( recordsHandler.hasClassificationData( recData ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( any( MarcRecord.class ), any( MarcRecord.class ) ) ).thenReturn( true );
        when( rawRepoDAO.fetchRecord( commonRec.getId().getId(), commonRec.getId().getLibrary() ) ).thenReturn( commonRec );
        when( rawRepoDAO.fetchRecord( extRecord.getId().getId(), extRecord.getId().getLibrary() ) ).thenReturn( extRecord );
        when( rawRepoDAO.recordExists( commonRec.getId().getId(), commonRec.getId().getLibrary() ) ).thenReturn( true );
        when( rawRepoDAO.recordExists( commonRec.getId().getId(), extRecord.getId().getLibrary() ) ).thenReturn( true );
        when( recordsHandler.hasClassificationData( extRecData ) ).thenReturn( false );
        when( recordsHandler.updateLibraryExtendedRecord( oldCommonRecData, extRecData ) ).thenReturn( extRecData );
        
        Set<Integer> holdingsItems = new HashSet<>();
        holdingsItems.add( extRecord.getId().getLibrary() );        
        when( holdingsItemsDAO.getAgenciesThatHasHoldingsFor( commonRec.getId().getId() ) ).thenReturn( holdingsItems );
        
        updater.updateRecord( recData );
        
        // Verify calls to update common record
        InOrder rawRepoOrder = inOrder( rawRepoDAO, recordsHandler );
        
        ArgumentCaptor<Record> argRecord = ArgumentCaptor.forClass( Record.class );
        rawRepoOrder.verify( rawRepoDAO ).saveRecord( argRecord.capture() );
        assertEquals( commonRec.getId(), argRecord.getValue().getId() );
        assertTrue( argRecord.getValue().hasContent() );
        assertEquals( recData, new Updater().decodeRecord( argRecord.getValue().getContent() ) );

        rawRepoOrder.verify( rawRepoDAO ).changedRecord( Updater.PROVIDER, commonRec.getId() );

        // Verify calls to create extended record
        rawRepoOrder.verify( recordsHandler ).hasClassificationData( extRecData );
        verify( recordsHandler ).updateLibraryExtendedRecord( oldCommonRecData, extRecData );
        
        rawRepoOrder.verify( rawRepoDAO ).enqueue( extRecord.getId(), Updater.PROVIDER, true, true );
    }

}
