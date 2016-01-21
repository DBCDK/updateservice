//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.HoldingsItems;
import dk.dbc.updateservice.update.LibraryRecordsHandler;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.SolrService;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.ResourceBundle;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

//-----------------------------------------------------------------------------
public class DeleteCommonRecordActionTest {
    public DeleteCommonRecordActionTest() {
        this.messages = ResourceBundles.getBundle( this, "actions" );
    }

    /**
     * Test performAction(): Delete single common record
     * with no enrichments and no children.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with a common record and no enrichments or children.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update a common record that is marked for deletion.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>RemoveLinksAction: Remove any existing links to other records</li>
     *              <li>DeleteRecordAction: Deletes the record</li>
     *              <li>EnqueueRecordAction: Put the record in queue</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_NoChildren_NoEnrichments() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecordAndMarkForDeletion( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( record );
        Integer agencyId = AssertActionsUtil.getAgencyId( record );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExistsMaybeDeleted( eq( recordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.children( eq( record ) ) ).thenReturn( new HashSet<RecordId>() );
        when( rawRepo.enrichments( eq( record ) ) ).thenReturn( new HashSet<RecordId>() );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( record ) ).thenReturn( new HashSet<Integer>() );

        DeleteCommonRecordAction instance = new DeleteCommonRecordAction( rawRepo, record );
        instance.setRecordsHandler( recordsHandler );
        instance.setHoldingsItems( holdingsItems );
        instance.setProviderId( "xxx" );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 3 ) );

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertRemoveLinksAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertDeleteRecordAction( iterator.next(), rawRepo, record, instance.MIMETYPE );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, record, instance.getProviderId(), instance.MIMETYPE );
    }

    /**
     * Test performAction(): Delete single common record with enrichments, but no children.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with a common record and one enrichment and no children.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update a common record that is marked for deletion.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>UpdateEnrichmentRecordAction: Delete enrichment record.</li>
     *              <li>RemoveLinksAction: Remove any existing links to other records</li>
     *              <li>DeleteRecordAction: Deletes the record</li>
     *              <li>EnqueueRecordAction: Put the record in queue</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_NoChildren_WithEnrichments() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecordAndMarkForDeletion( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( record );
        Integer agencyId = AssertActionsUtil.getAgencyId( record );

        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE );
        Integer enrichmentAgencyId = AssertActionsUtil.getAgencyId( enrichmentRecord );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExistsMaybeDeleted( eq( recordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.children( eq( record ) ) ).thenReturn( new HashSet<RecordId>() );
        when( rawRepo.enrichments( eq( record ) ) ).thenReturn( AssertActionsUtil.createRecordSet( enrichmentRecord ) );
        when( rawRepo.fetchRecord( eq( recordId ), eq( enrichmentAgencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( enrichmentRecord, MarcXChangeMimeType.ENRICHMENT ) );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( record ) ).thenReturn( new HashSet<Integer>() );

        DeleteCommonRecordAction instance = new DeleteCommonRecordAction( rawRepo, record );
        instance.setSolrService( mock( SolrService.class ) );
        instance.setRecordsHandler( recordsHandler );
        instance.setHoldingsItems( holdingsItems );
        instance.setProviderId( "xxx" );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        MarcRecord expectedEnrichmentRecord = AssertActionsUtil.loadRecordAndMarkForDeletion( AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 4 ) );

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertUpdateEnrichmentRecordAction( iterator.next(), rawRepo, expectedEnrichmentRecord, recordsHandler, holdingsItems );
        AssertActionsUtil.assertRemoveLinksAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertDeleteRecordAction( iterator.next(), rawRepo, record, instance.MIMETYPE );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, record, instance.getProviderId(), instance.MIMETYPE );
    }

    /**
     * Test performAction(): Delete single common record with children, but no enrichments.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with a common record and one children and no enrichment.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update a common record that is marked for deletion.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Return status: FAILED_UPDATE_INTERNAL_ERROR
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_WithChildren_NoEnrichments() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecordAndMarkForDeletion( AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( record );
        Integer agencyId = AssertActionsUtil.getAgencyId( record );

        MarcRecord volumeRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExistsMaybeDeleted( eq( recordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.children( eq( record ) ) ).thenReturn( AssertActionsUtil.createRecordSet( volumeRecord ) );
        when( rawRepo.enrichments( eq( record ) ) ).thenReturn( new HashSet<RecordId>() );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( record ) ).thenReturn( new HashSet<Integer>() );

        DeleteCommonRecordAction instance = new DeleteCommonRecordAction( rawRepo, record );
        instance.setHoldingsItems( holdingsItems );

        String message = String.format( messages.getString( "delete.record.children.error" ), recordId );
        assertThat( instance.performAction(), equalTo( ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message ) ) );
        assertThat( instance.children().isEmpty(), is( true ) );
    }

    /**
     * Test performAction(): Delete single common record with enrichments and
     * children.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with a common record and one enrichment and one children.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update a common record that is marked for deletion.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Return status: FAILED_UPDATE_INTERNAL_ERROR
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_WithChildren_WithEnrichments() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecordAndMarkForDeletion( AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( record );
        Integer agencyId = AssertActionsUtil.getAgencyId( record );

        MarcRecord volumeRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE );
        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_MAIN_ENRICHMENT_RECORD_RESOURCE );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExistsMaybeDeleted( eq( recordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.children( eq( record ) ) ).thenReturn( AssertActionsUtil.createRecordSet( volumeRecord ) );
        when( rawRepo.enrichments( eq( record ) ) ).thenReturn( AssertActionsUtil.createRecordSet( enrichmentRecord ) );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( record ) ).thenReturn( new HashSet<Integer>() );

        DeleteCommonRecordAction instance = new DeleteCommonRecordAction( rawRepo, record );
        instance.setHoldingsItems( holdingsItems );

        String message = String.format( messages.getString( "delete.record.children.error" ), recordId );
        assertThat( instance.performAction(), equalTo( ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message ) ) );
        assertThat( instance.children().isEmpty(), is( true ) );
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private ResourceBundle messages;
}
