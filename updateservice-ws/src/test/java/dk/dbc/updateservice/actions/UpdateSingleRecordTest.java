//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.updateservice.update.HoldingsItems;
import dk.dbc.updateservice.update.LibraryRecordsHandler;
import dk.dbc.updateservice.update.RawRepo;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

//-----------------------------------------------------------------------------
public class UpdateSingleRecordTest {
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
     *              <li>CreateSingleRecordAction: Creates the new record</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_CreateRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( record );
        Integer agencyId = AssertActionsUtil.getAgencyId( record );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( agencyId ) ) ).thenReturn( false );

        UpdateSingleRecord instance = new UpdateSingleRecord( rawRepo, record );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 1 ) );

        CreateSingleRecordAction action = (CreateSingleRecordAction)children.get( 0 );
        assertThat( action, notNullValue() );
        assertThat( action.getRawRepo(), is( rawRepo ) );
        assertThat( action.getRecord(), is( record ) );
    }

    /**
     * Test performAction(): Update an existing single common record with no
     * classifications.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with a single common record and a DBC enrichment
     *          record.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update the common record, but without any classification data.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>OverwriteSingleRecordAction: Action to overwrite the record.</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_OverwriteRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( record );
        Integer agencyId = AssertActionsUtil.getAgencyId( record );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( agencyId ) ) ).thenReturn( true );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( record ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( record ) ).thenReturn( false );

        UpdateSingleRecord instance = new UpdateSingleRecord( rawRepo, record );
        instance.setGroupId( 700000 );
        instance.setHoldingsItems( holdingsItems );
        instance.setRecordsHandler( recordsHandler );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 1 ) );

        OverwriteSingleRecordAction action = (OverwriteSingleRecordAction)children.get( 0 );
        assertThat( action, notNullValue() );
        assertThat( action.getRawRepo(), is( rawRepo ) );
        assertThat( action.getRecord(), is( record ) );
        assertThat( action.getGroupId(), equalTo( 700000 ) );
        assertThat( action.getHoldingsItems(), is( holdingsItems ) );
        assertThat( action.getRecordsHandler(), is( recordsHandler ) );
    }

    /**
     * Test performAction(): Delete single common record with no enrichments and no children.
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
     *              <li>DeleteCommonRecordAction: Action to delete a common record</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_DeleteRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecordAndMarkForDeletion( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( record );
        Integer agencyId = AssertActionsUtil.getAgencyId( record );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( agencyId ) ) ).thenReturn( true );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( record ) ).thenReturn( new HashSet<Integer>() );

        UpdateSingleRecord instance = new UpdateSingleRecord( rawRepo, record );
        instance.setHoldingsItems( holdingsItems );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 1 ) );

        DeleteCommonRecordAction action = (DeleteCommonRecordAction)children.get( 0 );
        assertThat( action, notNullValue() );
        assertThat( action.getRawRepo(), is( rawRepo ) );
        assertThat( action.getRecord(), is( record ) );
        assertThat( action.getHoldingsItems(), is( holdingsItems ) );
    }
}
