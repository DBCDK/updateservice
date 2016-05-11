//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordWriter;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Properties;
import java.util.ResourceBundle;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

//-----------------------------------------------------------------------------
public class UpdateCommonRecordActionTest {
    public UpdateCommonRecordActionTest() {
        this.messages = ResourceBundles.getBundle( this, "actions" );
    }

    /**
     * Test performAction(): Create new single common record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          An empty rawrepo.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update a common record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>UpdateSingleRecordAction: Creates the new record</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_CreateSingleRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( record );
        Integer agencyId = AssertActionsUtil.getAgencyId( record );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( agencyId ) ) ).thenReturn( false );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( record ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( record ) ).thenReturn( false );

        SolrService solrService = mock( SolrService.class );
        Properties settings = mock( Properties.class );

        UpdateCommonRecordAction instance = new UpdateCommonRecordAction( rawRepo, record );
        instance.setGroupId( 700000 );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setSolrService( solrService );
        instance.setRecordsHandler( recordsHandler );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 1 ) );

        UpdateSingleRecord action = (UpdateSingleRecord)children.get( 0 );
        assertThat( action, notNullValue() );
        assertThat( action.getRawRepo(), is( rawRepo ) );
        assertThat( action.getRecord(), is( record ) );
        assertThat( action.getGroupId(), equalTo( 700000 ) );
        assertThat( action.getHoldingsItems(), is( holdingsItems ) );
        assertThat( action.getOpenAgencyService(), is( openAgencyService ) );
        assertThat( action.getRecordsHandler(), is( recordsHandler ) );
    }

    /**
     * Test performAction(): Create new single common record which is a double alias.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with a record with 002-link to record that is being updated.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update a common record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Return status: FAILED_UPDATE_INTERNAL_ERROR
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_CreateSingleRecord_WithDoubleAlias() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( record );
        Integer agencyId = AssertActionsUtil.getAgencyId( record );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( agencyId ) ) ).thenReturn( false );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( record ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( record ) ).thenReturn( false );

        SolrService solrService = mock( SolrService.class );
        when( solrService.hasDocuments( eq( SolrServiceIndexer.createSubfieldQueryDBCOnly( "002a", recordId ) ) ) ).thenReturn( true );
        Properties settings = mock( Properties.class );

        UpdateCommonRecordAction instance = new UpdateCommonRecordAction( rawRepo, record );
        instance.setGroupId( 700000 );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setSolrService( solrService );
        instance.setRecordsHandler( recordsHandler );
        instance.setSettings( settings );

        String message = messages.getString( "update.record.with.002.links" );
        assertThat( instance.performAction(), equalTo( ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message ) ) );
    }

    /**
     * Test performAction(): Delete new single common record which is a double alias.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with two records: <code>r1</code> is a double record to <code>r2</code>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Delete <code>r2</code>
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>UpdateSingleRecordAction: Update <code>r2</code></li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_DeleteSingleRecord_WithDoubleAlias() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( record );
        Integer agencyId = AssertActionsUtil.getAgencyId( record );

        MarcRecordWriter writer = new MarcRecordWriter( record );
        writer.markForDeletion();

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( agencyId ) ) ).thenReturn( false );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( record ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( record ) ).thenReturn( false );

        SolrService solrService = mock( SolrService.class );
        Properties settings = mock( Properties.class );

        UpdateCommonRecordAction instance = new UpdateCommonRecordAction( rawRepo, record );
        instance.setGroupId( 700000 );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setSolrService( solrService );
        instance.setRecordsHandler( recordsHandler );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 1 ) );

        UpdateSingleRecord action = (UpdateSingleRecord)children.get( 0 );
        assertThat( action, notNullValue() );
        assertThat( action.getRawRepo(), is( rawRepo ) );
        assertThat( action.getRecord(), is( record ) );
        assertThat( action.getGroupId(), equalTo( 700000 ) );
        assertThat( action.getHoldingsItems(), is( holdingsItems ) );
        assertThat( action.getOpenAgencyService(), is( openAgencyService ) );
        assertThat( action.getRecordsHandler(), is( recordsHandler ) );
    }

    /**
     * Test performAction(): Create new volume common record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          An empty rawrepo.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update a common record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>UpdateVolumeRecordAction: Creates the new record</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_CreateVolumeRecord() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE );
        String mainRecordId = AssertActionsUtil.getRecordId( mainRecord );
        Integer agencyId = AssertActionsUtil.getAgencyId( mainRecord );

        MarcRecord volumeRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE );
        String volumeRecordId = AssertActionsUtil.getRecordId( volumeRecord );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( mainRecordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( volumeRecordId ), eq( agencyId ) ) ).thenReturn( false );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( volumeRecord ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( volumeRecord ) ).thenReturn( false );

        SolrService solrService = mock( SolrService.class );
        Properties settings = mock( Properties.class );

        UpdateCommonRecordAction instance = new UpdateCommonRecordAction( rawRepo, volumeRecord );
        instance.setGroupId( 700000 );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setSolrService( solrService );
        instance.setRecordsHandler( recordsHandler );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 1 ) );

        UpdateVolumeRecord action = (UpdateVolumeRecord)children.get( 0 );
        assertThat( action, notNullValue() );
        assertThat( action.getRawRepo(), is( rawRepo ) );
        assertThat( action.getRecord(), is( volumeRecord ) );
        assertThat( action.getGroupId(), equalTo( 700000 ) );
        assertThat( action.getHoldingsItems(), is( holdingsItems ) );
        assertThat( action.getOpenAgencyService(), is( openAgencyService ) );
        assertThat( action.getRecordsHandler(), is( recordsHandler ) );
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private ResourceBundle messages;
}
