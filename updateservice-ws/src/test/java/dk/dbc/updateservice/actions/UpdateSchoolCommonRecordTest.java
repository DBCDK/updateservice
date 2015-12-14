//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordWriter;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.updateservice.update.HoldingsItems;
import dk.dbc.updateservice.update.LibraryRecordsHandler;
import dk.dbc.updateservice.update.RawRepo;
import org.junit.Test;

import java.util.ListIterator;
import java.util.ResourceBundle;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

//-----------------------------------------------------------------------------
public class UpdateSchoolCommonRecordTest {
    public UpdateSchoolCommonRecordTest() {
        this.messages = ResourceBundles.getBundle( this, "actions" );
    }

    /**
     * Test performAction(): Create new single common school record with no school
     * enrichments in rawrepo.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with a common record.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update a common school record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>UpdateEnrichmentRecordAction: Update the common school record</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_CreateRecord_WithNoSchoolEnrichments() throws Exception {
        MarcRecord commonRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( commonRecord );

        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SCHOOL_RECORD_RESOURCE );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( recordId ), eq( RawRepo.SCHOOL_COMMON_AGENCY ) ) ).thenReturn( false );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );

        UpdateSchoolCommonRecord instance = new UpdateSchoolCommonRecord( rawRepo, record );
        instance.setRecordsHandler( recordsHandler );
        instance.setHoldingsItems( holdingsItems );
        instance.setProviderId( "xxx" );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertUpdateEnrichmentRecordAction( iterator.next(), rawRepo, record, recordsHandler, holdingsItems );

        assertThat( iterator.hasNext(), is( false ) );
    }

    /**
     * Test performAction(): Updates a single common school record with no school
     * enrichments in rawrepo.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          Rawrepo with:
     *          <ul>
     *              <li>A common record <code>c1</code></li>
     *              <li>A common school record <code>s1</code></li>
     *          </ul>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update the common school record <code>s1</code> with new data.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>UpdateEnrichmentRecordAction: Update the common school record</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_UpdateRecord_WithNoSchoolEnrichments() throws Exception {
        MarcRecord commonRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( commonRecord );

        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SCHOOL_RECORD_RESOURCE );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( recordId ), eq( RawRepo.SCHOOL_COMMON_AGENCY ) ) ).thenReturn( true );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );

        UpdateSchoolCommonRecord instance = new UpdateSchoolCommonRecord( rawRepo, record );
        instance.setRecordsHandler( recordsHandler );
        instance.setHoldingsItems( holdingsItems );
        instance.setProviderId( "xxx" );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertUpdateEnrichmentRecordAction( iterator.next(), rawRepo, record, recordsHandler, holdingsItems );

        assertThat( iterator.hasNext(), is( false ) );
    }

    /**
     * Test performAction(): Create new single common school record with existing school
     * enrichments in rawrepo.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          Rawrepo:
     *          <ul>
     *              <li>Common record.</li>
     *              <li>Enrichment record for a school library.</li>
     *          </ul>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Create a common school record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>UpdateEnrichmentRecordAction: Update the common school record</li>
     *              <li>LinkRecordAction: Link the school enrichment record to the common school record.</li>
     *              <li>EnqueueRecordAction: Enqueue the school enrichment record.</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_CreateRecord_WithSchoolEnrichments() throws Exception {
        MarcRecord commonRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( commonRecord );

        MarcRecord schoolRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.SCHOOL_RECORD_RESOURCE );
        Integer schoolAgencyId = AssertActionsUtil.getAgencyId( schoolRecord );

        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SCHOOL_RECORD_RESOURCE );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( recordId ), eq( schoolAgencyId ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( recordId ), eq( RawRepo.SCHOOL_COMMON_AGENCY ) ) ).thenReturn( false );
        when( rawRepo.agenciesForRecord( eq( record ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet( schoolAgencyId ) );
        when( rawRepo.fetchRecord( eq( recordId ), eq( schoolAgencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( schoolRecord, MarcXChangeMimeType.ENRICHMENT ) );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );

        UpdateSchoolCommonRecord instance = new UpdateSchoolCommonRecord( rawRepo, record );
        instance.setRecordsHandler( recordsHandler );
        instance.setHoldingsItems( holdingsItems );
        instance.setProviderId( "xxx" );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertUpdateEnrichmentRecordAction( iterator.next(), rawRepo, record, recordsHandler, holdingsItems );
        AssertActionsUtil.assertLinkRecordAction( iterator.next(), rawRepo, schoolRecord, record );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, schoolRecord, "xxx", UpdateEnrichmentRecordAction.MIMETYPE );

        assertThat( iterator.hasNext(), is( false ) );
    }

    /**
     * Test performAction(): Delete a single common school record with no school
     * enrichments in rawrepo.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          Rawrepo with:
     *          <ul>
     *              <li>A common record <code>c1</code></li>
     *              <li>A common school record <code>s1</code></li>
     *          </ul>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Delete the common school record <code>s1</code> with new data.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>UpdateEnrichmentRecordAction: Update the common school record</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_DeleteRecord_WithNoSchoolEnrichments() throws Exception {
        MarcRecord commonRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( commonRecord );

        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SCHOOL_RECORD_RESOURCE );
        MarcRecordWriter writer = new MarcRecordWriter( record );
        writer.markForDeletion();

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( recordId ), eq( RawRepo.SCHOOL_COMMON_AGENCY ) ) ).thenReturn( true );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );

        UpdateSchoolCommonRecord instance = new UpdateSchoolCommonRecord( rawRepo, record );
        instance.setRecordsHandler( recordsHandler );
        instance.setHoldingsItems( holdingsItems );
        instance.setProviderId( "xxx" );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertUpdateEnrichmentRecordAction( iterator.next(), rawRepo, record, recordsHandler, holdingsItems );

        assertThat( iterator.hasNext(), is( false ) );
    }

    /**
     * Test performAction(): Delete a single common school record with existing school
     * enrichments in rawrepo.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          Rawrepo:
     *          <ul>
     *              <li>Common record.</li>
     *              <li>Common school record.</li>
     *              <li>Enrichment record for a school library.</li>
     *          </ul>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Delete the common school record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>LinkRecordAction: Link the school enrichment record to the common record.</li>
     *              <li>EnqueueRecordAction: Enqueue the school enrichment record.</li>
     *              <li>UpdateEnrichmentRecordAction: Update the common school record</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_DeleteRecord_WithSchoolEnrichments() throws Exception {
        MarcRecord commonRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( commonRecord );

        MarcRecord schoolRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.SCHOOL_RECORD_RESOURCE );
        Integer schoolAgencyId = AssertActionsUtil.getAgencyId( schoolRecord );

        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SCHOOL_RECORD_RESOURCE );
        MarcRecordWriter writer = new MarcRecordWriter( record );
        writer.markForDeletion();

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( recordId ), eq( schoolAgencyId ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( recordId ), eq( RawRepo.SCHOOL_COMMON_AGENCY ) ) ).thenReturn( true );
        when( rawRepo.agenciesForRecord( eq( record ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet( schoolAgencyId ) );
        when( rawRepo.fetchRecord( eq( recordId ), eq( schoolAgencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( schoolRecord, MarcXChangeMimeType.ENRICHMENT ) );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );

        UpdateSchoolCommonRecord instance = new UpdateSchoolCommonRecord( rawRepo, record );
        instance.setRecordsHandler( recordsHandler );
        instance.setHoldingsItems( holdingsItems );
        instance.setProviderId( "xxx" );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertLinkRecordAction( iterator.next(), rawRepo, schoolRecord, commonRecord );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, schoolRecord, "xxx", UpdateEnrichmentRecordAction.MIMETYPE );
        AssertActionsUtil.assertUpdateEnrichmentRecordAction( iterator.next(), rawRepo, record, recordsHandler, holdingsItems );

        assertThat( iterator.hasNext(), is( false ) );
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private ResourceBundle messages;
}
