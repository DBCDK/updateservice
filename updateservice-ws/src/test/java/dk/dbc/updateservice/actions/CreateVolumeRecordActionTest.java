//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordWriter;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.HoldingsItems;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.SolrService;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import org.junit.Assert;
import org.junit.Test;

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
public class CreateVolumeRecordActionTest {
    public CreateVolumeRecordActionTest() {
        this.messages = ResourceBundles.getBundle( this, "actions" );
    }

    /**
     * Test performAction(): Create new volume common record with no local records for the
     * same faust-id.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with a main record, <code>m1</code>.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update a volume common record, that points to <code>m1</code> in
     *          <code>014a</code>.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>StoreRecordAction: Store the record</li>
     *              <li>RemoveLinksAction: Remove existing links to other records</li>
     *              <li>LinkRecord: Link record to parent record (<code>m1</code>)</li>
     *              <li>EnqueueRecordAction: Put the record in queue</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_NoLocals_No002Links() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE );
        String mainRecordId = AssertActionsUtil.getRecordId( mainRecord );
        Integer agencyId = AssertActionsUtil.getAgencyId( mainRecord );

        MarcRecord volumeRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE );
        String volumeRecordId = AssertActionsUtil.getRecordId( volumeRecord );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( mainRecordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.recordExistsMaybeDeleted( eq( volumeRecordId ), eq( agencyId ) ) ).thenReturn( false );
        when( rawRepo.agenciesForRecord( eq( volumeRecord ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( mainRecord ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( volumeRecord ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        SolrService solrService = mock( SolrService.class );
        when( solrService.hasDocuments( eq( SolrServiceIndexer.createSubfieldQuery( "002a", volumeRecordId ) ) ) ).thenReturn( false );

        CreateVolumeRecordAction instance = new CreateVolumeRecordAction( rawRepo, volumeRecord );
        instance.setHoldingsItems( holdingsItems );
        instance.setSolrService( solrService );
        instance.setProviderId( "xxx" );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 4 ) );

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction( iterator.next(), rawRepo, volumeRecord );
        AssertActionsUtil.assertRemoveLinksAction( iterator.next(), rawRepo, volumeRecord );
        AssertActionsUtil.assertLinkRecordAction( iterator.next(), rawRepo, volumeRecord, mainRecord );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, volumeRecord, instance.getProviderId(), instance.MIMETYPE );
    }

    /**
     * Test performAction(): Create new volume common record with no local records for the
     * same faust-id with 002 links in other records.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with a main record, <code>m1</code>.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update a volume common record, that points to <code>m1</code> in
     *          <code>014a</code>.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Return status: FAILED_UPDATE_INTERNAL_ERROR
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_NoLocals_With002Links() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE );
        String mainRecordId = AssertActionsUtil.getRecordId( mainRecord );
        Integer agencyId = AssertActionsUtil.getAgencyId( mainRecord );

        MarcRecord volumeRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE );
        String volumeRecordId = AssertActionsUtil.getRecordId( volumeRecord );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( mainRecordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.recordExistsMaybeDeleted( eq( volumeRecordId ), eq( agencyId ) ) ).thenReturn( false );
        when( rawRepo.agenciesForRecord( eq( volumeRecord ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( mainRecord ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( volumeRecord ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        SolrService solrService = mock( SolrService.class );
        when( solrService.hasDocuments( eq( SolrServiceIndexer.createSubfieldQuery( "002a", volumeRecordId ) ) ) ).thenReturn( true );

        CreateVolumeRecordAction instance = new CreateVolumeRecordAction( rawRepo, volumeRecord );
        instance.setHoldingsItems( holdingsItems );
        instance.setSolrService( solrService );
        instance.setProviderId( "xxx" );

        String message = messages.getString( "create.record.with.002.links" );
        assertThat( instance.performAction(), equalTo( ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message ) ) );
        assertThat( instance.children().isEmpty(), is( true ) );
    }

    /**
     * Test performAction(): Create new volume common record with no local records for the
     * same faust-id.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with a main record, <code>m1</code>.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update a volume common record, that points to <code>m1</code> in
     *          <code>014a</code>.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Return status: FAILED_UPDATE_INTERNAL_ERROR
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_WithLocals() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE );
        String mainRecordId = AssertActionsUtil.getRecordId( mainRecord );
        Integer agencyId = AssertActionsUtil.getAgencyId( mainRecord );

        MarcRecord volumeRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE );
        String volumeRecordId = AssertActionsUtil.getRecordId( volumeRecord );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( mainRecordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.recordExistsMaybeDeleted( eq( volumeRecordId ), eq( agencyId ) ) ).thenReturn( false );
        when( rawRepo.agenciesForRecord( eq( volumeRecord ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet( 700300 ) );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( mainRecord ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( volumeRecord ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        SolrService solrService = mock( SolrService.class );
        when( solrService.hasDocuments( eq( SolrServiceIndexer.createSubfieldQuery( "002a", volumeRecordId ) ) ) ).thenReturn( false );

        CreateVolumeRecordAction instance = new CreateVolumeRecordAction( rawRepo, volumeRecord );
        instance.setHoldingsItems( holdingsItems );
        instance.setSolrService( solrService );

        String message = messages.getString( "create.record.with.locals" );
        assertThat( instance.performAction(), equalTo( ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message ) ) );
        assertThat( instance.children().isEmpty(), is( true ) );
    }

    /**
     * Test performAction(): Create new volume common record that points to itself as parent.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          An empty rawrepo.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update a volume common record with <code>001a == 014a</code>.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Return status: FAILED_UPDATE_INTERNAL_ERROR
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_PointToItselfAsParent() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE );
        String mainRecordId = AssertActionsUtil.getRecordId( mainRecord );
        Integer agencyId = AssertActionsUtil.getAgencyId( mainRecord );

        MarcRecord volumeRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE );
        String volumeRecordId = AssertActionsUtil.getRecordId( volumeRecord );
        new MarcRecordWriter( volumeRecord ).addOrReplaceSubfield( "014", "a", volumeRecordId );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( mainRecordId ), eq( agencyId ) ) ).thenReturn( false );
        when( rawRepo.recordExistsMaybeDeleted( eq( volumeRecordId ), eq( agencyId ) ) ).thenReturn( false );
        when( rawRepo.agenciesForRecord( eq( volumeRecord ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( mainRecord ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( volumeRecord ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        SolrService solrService = mock( SolrService.class );
        when( solrService.hasDocuments( eq( SolrServiceIndexer.createSubfieldQuery( "002a", volumeRecordId ) ) ) ).thenReturn( false );

        CreateVolumeRecordAction instance = new CreateVolumeRecordAction( rawRepo, volumeRecord );
        instance.setHoldingsItems( holdingsItems );
        instance.setSolrService( solrService );

        String message = String.format( messages.getString( "parent.point.to.itself" ), volumeRecordId, agencyId );
        assertThat( instance.performAction(), equalTo( ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message ) ) );
        assertThat( instance.children().isEmpty(), is( true ) );
    }

    /**
     * Test performAction(): Create new volume common record with unknown parent.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          An empty rawrepo.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update a volume common record, that points to some record in
     *          <code>014a</code>.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Return status: FAILED_UPDATE_INTERNAL_ERROR
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_NoParent() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE );
        String mainRecordId = AssertActionsUtil.getRecordId( mainRecord );
        Integer agencyId = AssertActionsUtil.getAgencyId( mainRecord );

        MarcRecord volumeRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE );
        String volumeRecordId = AssertActionsUtil.getRecordId( volumeRecord );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( mainRecordId ), eq( agencyId ) ) ).thenReturn( false );
        when( rawRepo.recordExistsMaybeDeleted( eq( volumeRecordId ), eq( agencyId ) ) ).thenReturn( false );
        when( rawRepo.agenciesForRecord( eq( volumeRecord ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( mainRecord ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( volumeRecord ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        SolrService solrService = mock( SolrService.class );
        when( solrService.hasDocuments( eq( SolrServiceIndexer.createSubfieldQuery( "002a", volumeRecordId ) ) ) ).thenReturn( false );

        CreateVolumeRecordAction instance = new CreateVolumeRecordAction( rawRepo, volumeRecord );
        instance.setHoldingsItems( holdingsItems );
        instance.setSolrService( solrService );

        String message = messages.getString( "reference.record.not.exist" );
        assertThat( instance.performAction(), equalTo( ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message ) ) );
        assertThat( instance.children().isEmpty(), is( true ) );
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private ResourceBundle messages;
}
