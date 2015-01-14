//-----------------------------------------------------------------------------
package dk.dbc.updateservice.update;

//-----------------------------------------------------------------------------

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;



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
    RawRepo rawRepo;

    @Mock
    HoldingsItems holdingsItems;

    @Mock
    LibraryRecordsHandler recordsHandler;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    //-------------------------------------------------------------------------
    //              Test methods
    //-------------------------------------------------------------------------

    /*
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
        Updater updater = new Updater( rawRepo, holdingsItems, recordsHandler );
        updater.init();

        Record commonRec = RecordUtils.createRawRecord( "20611529", 870970 );
        MarcRecord oldCommonRecData = RecordUtils.createMarcRecord();
        MarcRecord recData = RecordUtils.loadMarcRecord( "dbcrec_single.xml" );
        Record extRecord = RecordUtils.createRawRecord( commonRec.getId().getBibliographicRecordId(), 700100 );

        when( recordsHandler.hasClassificationData( oldCommonRecData ) ).thenReturn( false );
        when( recordsHandler.hasClassificationData( recData ) ).thenReturn( true );
        when( rawRepo.fetchRecord(commonRec.getId().getBibliographicRecordId(), commonRec.getId().getAgencyId()) ).thenReturn( commonRec );
        when( rawRepo.fetchRecord( extRecord.getId().getBibliographicRecordId(), extRecord.getId().getAgencyId() ) ).thenReturn( extRecord );
        when( rawRepo.recordExists(commonRec.getId().getBibliographicRecordId(), extRecord.getId().getAgencyId()) ).thenReturn( false );
        when( rawRepo.recordExists( commonRec.getId().getBibliographicRecordId(), commonRec.getId().getAgencyId() ) ).thenReturn( true );
        when( recordsHandler.createLibraryExtendedRecord(oldCommonRecData, extRecord.getId().getAgencyId()) ).thenReturn( RecordUtils.loadMarcRecord( "extrec_single.xml" ) );
        when( recordsHandler.updateRecordForUpdate( recData ) ).thenReturn( recData );

        Set<Integer> items = new HashSet<>();
        items.add( extRecord.getId().getAgencyId() );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( commonRec.getId().getBibliographicRecordId() ) ).thenReturn( items );

        updater.updateRecord( recData );

        // Verify calls to update common record
        InOrder rawRepoOrder = inOrder( rawRepo );

        ArgumentCaptor<Record> argRecord = ArgumentCaptor.forClass( Record.class );
        rawRepoOrder.verify( rawRepo ).saveRecord( argRecord.capture(), eq( "" ) );
        assertEquals( commonRec.getId(), argRecord.getValue().getId() );
        assertNotNull( argRecord.getValue().getContent() );
        assertEquals( recData, new Updater().decodeRecord( argRecord.getValue().getContent() ) );

        rawRepoOrder.verify( rawRepo ).changedRecord( Updater.PROVIDER, commonRec.getId(), MarcXChangeMimeType.MARCXCHANGE );

        // Verify calls to create extended record
        rawRepoOrder.verify( rawRepo, never() ).saveRecord( any( Record.class ), eq( "" ) );

        rawRepoOrder.verify( rawRepo, never() ).changedRecord( any( String.class ), any( RecordId.class ), eq( MarcXChangeMimeType.MARCXCHANGE ) );
    }

    /*
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
        Updater updater = new Updater( rawRepo, holdingsItems, recordsHandler );
        updater.init();

        MarcRecord oldCommonRecData = RecordUtils.loadMarcRecord( "dbcrec_single_v1.xml");
        MarcRecord recData = RecordUtils.loadMarcRecord( "dbcrec_single.xml" );

        Record commonRec = RecordUtils.createRawRecord( oldCommonRecData );
        Record extRecord = RecordUtils.createRawRecord( commonRec.getId().getBibliographicRecordId(), 700100 );

        when( recordsHandler.hasClassificationData( oldCommonRecData ) ).thenReturn( true );
        when( recordsHandler.hasClassificationData( recData ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged(any(MarcRecord.class), any(MarcRecord.class)) ).thenReturn( false );
        when( recordsHandler.updateRecordForUpdate(recData) ).thenReturn( recData );
        when( rawRepo.fetchRecord( commonRec.getId().getBibliographicRecordId(), commonRec.getId().getAgencyId() ) ).thenReturn( commonRec );
        when( rawRepo.fetchRecord( extRecord.getId().getBibliographicRecordId(), extRecord.getId().getAgencyId() ) ).thenReturn( extRecord );
        when( rawRepo.recordExists( commonRec.getId().getBibliographicRecordId(), commonRec.getId().getAgencyId() ) ).thenReturn( true );
        when( rawRepo.recordExists( commonRec.getId().getBibliographicRecordId(), extRecord.getId().getAgencyId() ) ).thenReturn( false );

        Set<Integer> items = new HashSet<>();
        items.add( extRecord.getId().getAgencyId() );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( commonRec.getId().getBibliographicRecordId() ) ).thenReturn( items );

        updater.updateRecord( recData );

        // Verify calls to update common record
        InOrder rawRepoOrder = inOrder( rawRepo );

        ArgumentCaptor<Record> argRecord = ArgumentCaptor.forClass( Record.class );
        rawRepoOrder.verify( rawRepo ).saveRecord( argRecord.capture(), eq( "" ) );
        assertEquals( commonRec.getId(), argRecord.getValue().getId() );
        assertNotNull( argRecord.getValue().getContent() );
        assertEquals( recData, new Updater().decodeRecord( argRecord.getValue().getContent() ) );

        rawRepoOrder.verify( rawRepo ).changedRecord( Updater.PROVIDER, commonRec.getId(), MarcXChangeMimeType.MARCXCHANGE );

        // Verify that an extended record is *not* created.
        rawRepoOrder.verify( rawRepo, never() ).saveRecord( any( Record.class ), eq( "" ) );
        rawRepoOrder.verify( rawRepo, never() ).changedRecord( any( String.class ), any( RecordId.class ), eq( MarcXChangeMimeType.MARCXCHANGE ) );
    }

    /*
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
        Updater updater = new Updater( rawRepo, holdingsItems, recordsHandler );
        updater.init();

        MarcRecord oldCommonRecData = RecordUtils.loadMarcRecord( "dbcrec_single_v1.xml");
        MarcRecord recData = RecordUtils.loadMarcRecord( "dbcrec_single.xml" );
        MarcRecord extRecData = RecordUtils.loadMarcRecord( "extrec_single.xml");

        Record commonRec = RecordUtils.createRawRecord( oldCommonRecData );
        Record extRecord = RecordUtils.createRawRecord( extRecData );

        when( recordsHandler.hasClassificationData( oldCommonRecData ) ).thenReturn( true );
        when( recordsHandler.hasClassificationData( recData ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged(any(MarcRecord.class), any(MarcRecord.class)) ).thenReturn( false );
        when( recordsHandler.updateRecordForUpdate(recData) ).thenReturn( recData );
        when( rawRepo.fetchRecord( commonRec.getId().getBibliographicRecordId(), commonRec.getId().getAgencyId() ) ).thenReturn( commonRec );
        when( rawRepo.fetchRecord( extRecord.getId().getBibliographicRecordId(), extRecord.getId().getAgencyId() ) ).thenReturn( extRecord );
        when( rawRepo.recordExists( commonRec.getId().getBibliographicRecordId(), commonRec.getId().getAgencyId() ) ).thenReturn( true );
        when( rawRepo.recordExists( commonRec.getId().getBibliographicRecordId(), extRecord.getId().getAgencyId() ) ).thenReturn( true );
        when( recordsHandler.hasClassificationData( extRecData ) ).thenReturn( false );

        Set<Integer> items = new HashSet<>();
        items.add( extRecord.getId().getAgencyId() );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( commonRec.getId().getBibliographicRecordId() ) ).thenReturn( items );

        updater.updateRecord( recData );

        // Verify calls to update common record
        InOrder rawRepoOrder = inOrder( rawRepo, recordsHandler );

        ArgumentCaptor<Record> argRecord = ArgumentCaptor.forClass( Record.class );
        rawRepoOrder.verify( rawRepo ).saveRecord( argRecord.capture(), eq( "" ) );
        assertEquals( commonRec.getId(), argRecord.getValue().getId() );
        assertNotNull( argRecord.getValue().getContent() );
        assertEquals( recData, new Updater().decodeRecord( argRecord.getValue().getContent() ) );

        rawRepoOrder.verify( rawRepo ).changedRecord( Updater.PROVIDER, commonRec.getId(), MarcXChangeMimeType.MARCXCHANGE );

        // Verify that an extended record is *not* updated.
        rawRepoOrder.verify( rawRepo, never() ).saveRecord( any( Record.class ), eq( "" ) );
        rawRepoOrder.verify( rawRepo, never() ).changedRecord( any( String.class ), any( RecordId.class ), eq( MarcXChangeMimeType.MARCXCHANGE ) );
    }

    /*
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
        Updater updater = new Updater( rawRepo, holdingsItems, recordsHandler );
        updater.init();

        Record commonRec = RecordUtils.createRawRecord( "20611529", 870970 );
        MarcRecord oldCommonRecData = RecordUtils.createMarcRecord();
        MarcRecord recData = RecordUtils.loadMarcRecord( "dbcrec_single.xml" );

        MarcRecord extRecData = RecordUtils.loadMarcRecord( "extrec_single.xml" );
        Record extRecord = RecordUtils.createRawRecord( extRecData );

        when( recordsHandler.hasClassificationData( oldCommonRecData ) ).thenReturn( false );
        when( recordsHandler.hasClassificationData( recData ) ).thenReturn( true );
        when( recordsHandler.updateRecordForUpdate(recData) ).thenReturn( recData );
        when( rawRepo.fetchRecord( commonRec.getId().getBibliographicRecordId(), commonRec.getId().getAgencyId() ) ).thenReturn( commonRec );
        when( rawRepo.fetchRecord( extRecord.getId().getBibliographicRecordId(), extRecord.getId().getAgencyId() ) ).thenReturn( extRecord );
        when( rawRepo.recordExists( commonRec.getId().getBibliographicRecordId(), commonRec.getId().getAgencyId() ) ).thenReturn( true );
        when( rawRepo.recordExists( extRecord.getId().getBibliographicRecordId(), extRecord.getId().getAgencyId() ) ).thenReturn( true );

        Set<Integer> items = new HashSet<>();
        items.add( extRecord.getId().getAgencyId() );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( commonRec.getId().getBibliographicRecordId() ) ).thenReturn( items );

        updater.updateRecord( recData );

        // Verify calls to update common record
        InOrder rawRepoOrder = inOrder( rawRepo );

        ArgumentCaptor<Record> argRecord = ArgumentCaptor.forClass( Record.class );
        rawRepoOrder.verify( rawRepo ).saveRecord( argRecord.capture(), eq( "" ) );
        assertEquals( commonRec.getId(), argRecord.getValue().getId() );
        assertNotNull( argRecord.getValue().getContent() );
        assertEquals( recData, new Updater().decodeRecord( argRecord.getValue().getContent() ) );

        rawRepoOrder.verify( rawRepo ).changedRecord( Updater.PROVIDER, commonRec.getId(), MarcXChangeMimeType.MARCXCHANGE );

        // Verify calls to create extended record
        rawRepoOrder.verify( rawRepo, never() ).saveRecord( any( Record.class ), eq( "" ) );

        rawRepoOrder.verify( rawRepo, never() ).changedRecord( any( String.class ), any( RecordId.class ), eq( MarcXChangeMimeType.MARCXCHANGE ) );
    }

    /*
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
        Updater updater = new Updater( rawRepo, holdingsItems, recordsHandler );
        updater.init();

        MarcRecord oldCommonRecData = RecordUtils.loadMarcRecord( "dbcrec_single_v1.xml");
        MarcRecord recData = RecordUtils.loadMarcRecord( "dbcrec_single.xml" );

        Record commonRec = RecordUtils.createRawRecord( oldCommonRecData );
        Record extRecord = RecordUtils.createRawRecord( commonRec.getId().getBibliographicRecordId(), 700100 );

        when( recordsHandler.hasClassificationData( oldCommonRecData ) ).thenReturn( true );
        when( recordsHandler.hasClassificationData( recData ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged(any(MarcRecord.class), any(MarcRecord.class)) ).thenReturn( true );
        when( rawRepo.fetchRecord(commonRec.getId().getBibliographicRecordId(), commonRec.getId().getAgencyId()) ).thenReturn( commonRec );
        when( rawRepo.fetchRecord( extRecord.getId().getBibliographicRecordId(), extRecord.getId().getAgencyId() ) ).thenReturn( extRecord );
        when( rawRepo.recordExists(commonRec.getId().getBibliographicRecordId(), commonRec.getId().getAgencyId()) ).thenReturn( true );
        when( rawRepo.recordExists( commonRec.getId().getBibliographicRecordId(), extRecord.getId().getAgencyId() ) ).thenReturn( false );
        when( recordsHandler.createLibraryExtendedRecord(oldCommonRecData, extRecord.getId().getAgencyId()) ).thenReturn( RecordUtils.loadMarcRecord( "extrec_single.xml" ) );
        when( recordsHandler.updateRecordForUpdate(recData) ).thenReturn( recData );

        Set<Integer> items = new HashSet<>();
        items.add( extRecord.getId().getAgencyId() );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( commonRec.getId().getBibliographicRecordId() ) ).thenReturn( items );

        updater.updateRecord( recData );

        // Verify calls to update common record
        InOrder rawRepoOrder = inOrder( rawRepo );

        ArgumentCaptor<Record> argRecord = ArgumentCaptor.forClass( Record.class );
        rawRepoOrder.verify( rawRepo ).saveRecord( argRecord.capture(), eq( "" ) );
        assertEquals( commonRec.getId(), argRecord.getValue().getId() );
        assertNotNull( argRecord.getValue().getContent() );
        assertEquals( recData, new Updater().decodeRecord( argRecord.getValue().getContent() ) );

        rawRepoOrder.verify( rawRepo ).changedRecord( Updater.PROVIDER, commonRec.getId(), MarcXChangeMimeType.MARCXCHANGE );

        // Verify calls to create extended record
        rawRepoOrder.verify( rawRepo ).saveRecord( argRecord.capture(), eq( "" ) );
        assertEquals( extRecord.getId(), argRecord.getValue().getId() );
        assertNotNull( argRecord.getValue().getContent() );

        final HashSet<RecordId> references = new HashSet<>();
        references.add( new RecordId( extRecord.getId().getBibliographicRecordId(), RawRepo.COMMON_LIBRARY ) );
        rawRepoOrder.verify( rawRepo ).changedRecord( Updater.PROVIDER, extRecord.getId(), MarcXChangeMimeType.MARCXCHANGE );
    }

    /*
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
        Updater updater = new Updater( rawRepo, holdingsItems, recordsHandler );
        updater.init();

        MarcRecord oldCommonRecData = RecordUtils.loadMarcRecord( "dbcrec_single_v1.xml");
        MarcRecord recData = RecordUtils.loadMarcRecord( "dbcrec_single.xml" );
        MarcRecord extRecData = RecordUtils.loadMarcRecord( "extrec_single.xml");

        Record commonRec = RecordUtils.createRawRecord( oldCommonRecData );
        Record extRecord = RecordUtils.createRawRecord( extRecData );

        when( recordsHandler.hasClassificationData( oldCommonRecData ) ).thenReturn( true );
        when( recordsHandler.hasClassificationData( recData ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged(any(MarcRecord.class), any(MarcRecord.class)) ).thenReturn( true );
        when( rawRepo.fetchRecord(commonRec.getId().getBibliographicRecordId(), commonRec.getId().getAgencyId()) ).thenReturn( commonRec );
        when( rawRepo.fetchRecord( extRecord.getId().getBibliographicRecordId(), extRecord.getId().getAgencyId() ) ).thenReturn( extRecord );
        when( rawRepo.recordExists(commonRec.getId().getBibliographicRecordId(), commonRec.getId().getAgencyId()) ).thenReturn( true );
        when( rawRepo.recordExists( commonRec.getId().getBibliographicRecordId(), extRecord.getId().getAgencyId() ) ).thenReturn( true );
        when( recordsHandler.hasClassificationData(extRecData) ).thenReturn( true );
        when( recordsHandler.updateRecordForUpdate(recData) ).thenReturn( recData );

        Set<Integer> items = new HashSet<>();
        items.add( extRecord.getId().getAgencyId() );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( commonRec.getId().getBibliographicRecordId() ) ).thenReturn( items );

        updater.updateRecord( recData );

        // Verify calls to update common record
        InOrder rawRepoOrder = inOrder( rawRepo, recordsHandler );

        ArgumentCaptor<Record> argRecord = ArgumentCaptor.forClass( Record.class );
        rawRepoOrder.verify( rawRepo ).saveRecord( argRecord.capture(), eq( "" ) );
        assertEquals( commonRec.getId(), argRecord.getValue().getId() );
        assertNotNull( argRecord.getValue().getContent() );
        assertEquals( recData, new Updater().decodeRecord( argRecord.getValue().getContent() ) );

        rawRepoOrder.verify( rawRepo ).changedRecord( Updater.PROVIDER, commonRec.getId(), MarcXChangeMimeType.MARCXCHANGE );

        // Verify calls to create extended record
        rawRepoOrder.verify( recordsHandler ).hasClassificationData( extRecData );
        verify( recordsHandler, never() ).updateLibraryExtendedRecord( oldCommonRecData, extRecData );
    }

    /*
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
        Updater updater = new Updater( rawRepo, holdingsItems, recordsHandler );
        updater.init();

        MarcRecord oldCommonRecData = RecordUtils.loadMarcRecord( "dbcrec_single_v1.xml");
        MarcRecord recData = RecordUtils.loadMarcRecord( "dbcrec_single.xml" );
        MarcRecord extRecData = RecordUtils.loadMarcRecord( "extrec_single.xml");

        Record commonRec = RecordUtils.createRawRecord( oldCommonRecData );
        Record extRecord = RecordUtils.createRawRecord( extRecData );

        when( recordsHandler.hasClassificationData( oldCommonRecData ) ).thenReturn( true );
        when( recordsHandler.hasClassificationData( recData ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged(any(MarcRecord.class), any(MarcRecord.class)) ).thenReturn( true );
        when( rawRepo.fetchRecord(commonRec.getId().getBibliographicRecordId(), commonRec.getId().getAgencyId()) ).thenReturn( commonRec );
        when( rawRepo.fetchRecord( extRecord.getId().getBibliographicRecordId(), extRecord.getId().getAgencyId() ) ).thenReturn( extRecord );
        when( rawRepo.recordExists(commonRec.getId().getBibliographicRecordId(), commonRec.getId().getAgencyId()) ).thenReturn( true );
        when( rawRepo.recordExists( commonRec.getId().getBibliographicRecordId(), extRecord.getId().getAgencyId() ) ).thenReturn( true );
        when( recordsHandler.hasClassificationData(extRecData) ).thenReturn( false );
        when( recordsHandler.updateLibraryExtendedRecord(oldCommonRecData, extRecData) ).thenReturn( extRecData );
        when( recordsHandler.updateRecordForUpdate(recData) ).thenReturn( recData );

        Set<Integer> items = new HashSet<>();
        items.add( extRecord.getId().getAgencyId() );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( commonRec.getId().getBibliographicRecordId() ) ).thenReturn( items );

        updater.updateRecord( recData );

        // Verify calls to update common record
        InOrder rawRepoOrder = inOrder( rawRepo, recordsHandler );

        ArgumentCaptor<Record> argRecord = ArgumentCaptor.forClass( Record.class );
        rawRepoOrder.verify( rawRepo ).saveRecord( argRecord.capture(), eq( "" ) );
        assertEquals( commonRec.getId(), argRecord.getValue().getId() );
        assertNotNull( argRecord.getValue().getContent() );
        assertEquals( recData, new Updater().decodeRecord( argRecord.getValue().getContent() ) );

        rawRepoOrder.verify( rawRepo ).changedRecord( Updater.PROVIDER, commonRec.getId(), MarcXChangeMimeType.MARCXCHANGE );

        // Verify calls to create extended record
        rawRepoOrder.verify( recordsHandler ).hasClassificationData( extRecData );
        verify( recordsHandler ).updateLibraryExtendedRecord( oldCommonRecData, extRecData );
    }

}
