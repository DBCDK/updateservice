//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.SolrService;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.ResourceBundle;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

//-----------------------------------------------------------------------------
public class CreateSingleRecordActionTest {
    public CreateSingleRecordActionTest() {
        this.messages = ResourceBundles.getBundle( this, "actions" );
    }

    /**
     * Test performAction(): Create new single common record with no local records for the
     * same faust-id.
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
     *              <li>StoreRecordAction: Store the record</li>
     *              <li>EnqueueRecordAction: Put the record in queue</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_NoLocals_No002Links() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( record );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.agenciesForRecord( eq( record ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        SolrService solrService = mock( SolrService.class );
        when( solrService.hasDocuments( eq( SolrServiceIndexer.createSubfieldQuery( "002a", recordId ) ) ) ).thenReturn( false );

        CreateSingleRecordAction instance = new CreateSingleRecordAction( rawRepo, record );
        instance.setSolrService( solrService );
        instance.setProviderId( "xxx" );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 2 ) );

        AssertActionsUtil.assertStoreRecordAction( children.get( 0 ), rawRepo, record );
        AssertActionsUtil.assertEnqueueRecordAction( children.get( 1 ), rawRepo, record, instance.getProviderId(), instance.MIMETYPE );
    }

    /**
     * Test performAction(): Create new single common record with no local records for the
     * same faust-id, but with 002 links from other records.
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
     *          Return status: FAILED_UPDATE_INTERNAL_ERROR
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_NoLocals_With002Links() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( record );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.agenciesForRecord( eq( record ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        SolrService solrService = mock( SolrService.class );
        when( solrService.hasDocuments( eq( SolrServiceIndexer.createSubfieldQuery( "002a", recordId ) ) ) ).thenReturn( true );

        CreateSingleRecordAction instance = new CreateSingleRecordAction( rawRepo, record );
        instance.setSolrService( solrService );
        instance.setProviderId( "xxx" );

        String message = messages.getString( "update.record.with.002.links" );
        assertThat( instance.performAction(), equalTo( ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message ) ) );
        assertThat( instance.children().isEmpty(), is( true ) );
    }

    /**
     * Test performAction(): Create new single common record with a local record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with a local record with faust-id <code>faust</code>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update a common record with faust-id <code>faust</code>
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Return status: FAILED_UPDATE_INTERNAL_ERROR
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_WithLocal() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( record );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.agenciesForRecord( eq( record ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet( 700300 ) );

        SolrService solrService = mock( SolrService.class );
        when( solrService.hasDocuments( eq( SolrServiceIndexer.createSubfieldQuery( "002a", recordId ) ) ) ).thenReturn( false );

        CreateSingleRecordAction instance = new CreateSingleRecordAction( rawRepo, record );
        instance.setSolrService( solrService );
        instance.setProviderId( "xxx" );

        String message = messages.getString( "create.record.with.locals" );
        assertThat( instance.performAction(), equalTo( ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message ) ) );
        assertThat( instance.children().isEmpty(), is( true ) );
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private ResourceBundle messages;
}
