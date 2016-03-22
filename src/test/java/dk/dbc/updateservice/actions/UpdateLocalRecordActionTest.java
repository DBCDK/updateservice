//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.*;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.*;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.util.*;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

//-----------------------------------------------------------------------------
public class UpdateLocalRecordActionTest {
    public UpdateLocalRecordActionTest() {
        this.messages = ResourceBundles.getBundle( this, "actions" );
    }

    /**
     * Test UpdateLocalRecordAction constructor.
     */
    @Test
    public void testConstructor() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        RawRepo rawRepo = mock( RawRepo.class );

        assertThat( new UpdateLocalRecordAction( rawRepo, record ), notNullValue() );
    }

    /**
     * Test UpdateLocalRecordAction.performAction(): Create single record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A single record.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update the record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>StoreRecordAction: Store the record</li>
     *              <li>RemoveLinksAction: Remove any existing links to other records</li>
     *              <li>EnqueueRecordAction: Put the record in queue</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_CreateSingleRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );

        String providerId = "xxx";

        RawRepo rawRepo = mock( RawRepo.class );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );

        SolrService solrService = mock( SolrService.class );
        when( solrService.hasDocuments( eq( SolrServiceIndexer.createSubfieldQuery( "002a", AssertActionsUtil.getRecordId( record ) ) ) ) ).thenReturn( false );

        UpdateLocalRecordAction instance = new UpdateLocalRecordAction( rawRepo, record );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setSolrService( solrService );
        instance.setProviderId( providerId );

        instance.checkState();
        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertRemoveLinksAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, record, providerId, UpdateLocalRecordAction.MIMETYPE );

        assertThat( iterator.hasNext(), is( false ) );
    }

    /**
     * Test UpdateLocalRecordAction.performAction(): Create volume record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo containing a main record m1.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update a record that points to <code>m1</code> in <code>014a</code>.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>StoreRecordAction: Store the record</li>
     *              <li>LinkRecord: Link record to parent record (<code>m1</code>)</li>
     *              <li>EnqueueRecordAction: Put the record in queue</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_CreateVolumeRecord() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE );
        MarcRecord volumeRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE );

        MarcRecordReader reader = new MarcRecordReader( volumeRecord );
        Integer agencyId = reader.agencyIdAsInteger();
        String parentId = reader.parentId();

        String providerId = "xxx";

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( parentId ), eq( agencyId ) ) ).thenReturn( true );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );

        SolrService solrService = mock( SolrService.class );
        when( solrService.hasDocuments( eq( SolrServiceIndexer.createSubfieldQuery( "002a", AssertActionsUtil.getRecordId( volumeRecord ) ) ) ) ).thenReturn( false );

        UpdateLocalRecordAction instance = new UpdateLocalRecordAction( rawRepo, volumeRecord );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setSolrService( solrService );
        instance.setProviderId( providerId );

        instance.checkState();

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction( iterator.next(), rawRepo, volumeRecord );
        AssertActionsUtil.assertLinkRecordAction( iterator.next(), rawRepo, volumeRecord, mainRecord );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, volumeRecord, providerId, UpdateLocalRecordAction.MIMETYPE );

        assertThat( iterator.hasNext(), is( false ) );
    }

    /**
     * Test UpdateLocalRecordAction.performAction(): Update volume record that points to unknown
     * main record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A empty rawrepo.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update a record that points an unknown record in <code>014a</code>.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Return status: FAILED_VALIDATION_INTERNAL_ERROR.
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_CreateVolumeRecord_UnknownParent() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE );

        MarcRecordReader reader = new MarcRecordReader( record );
        String recordId = reader.recordId();
        Integer agencyId = reader.agencyIdAsInteger();
        String parentId = reader.parentId();

        String providerId = "xxx";

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( parentId ), eq( agencyId ) ) ).thenReturn( false );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        SolrService solrService = mock( SolrService.class );

        UpdateLocalRecordAction instance = new UpdateLocalRecordAction( rawRepo, record );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setSolrService( solrService );
        instance.setProviderId( providerId );

        instance.checkState();

        String message = String.format( messages.getString( "reference.record.not.exist" ), recordId, agencyId, parentId, agencyId );
        assertThat( instance.performAction(), equalTo( ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message ) ) );
    }

    /**
     * Test UpdateLocalRecordAction.performAction(): Update volume record that points to itself
     * in <code>014a</code>
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A empty rawrepo.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update a record that points to itself in <code>014a</code>.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Return status: FAILED_VALIDATION_INTERNAL_ERROR.
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_CreateVolumeRecord_Itself() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE );

        MarcRecordReader reader = new MarcRecordReader( record );
        String recordId = reader.recordId();
        Integer agencyId = reader.agencyIdAsInteger();
        new MarcRecordWriter( record ).addOrReplaceSubfield( "014", "a", recordId );

        String providerId = "xxx";

        RawRepo rawRepo = mock( RawRepo.class );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        SolrService solrService = mock( SolrService.class );

        UpdateLocalRecordAction instance = new UpdateLocalRecordAction( rawRepo, record );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setSolrService( solrService );
        instance.setProviderId( providerId );

        instance.checkState();

        String message = String.format( messages.getString( "parent.point.to.itself" ), recordId, agencyId );
        assertThat( instance.performAction(), equalTo( ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message ) ) );

        verify( rawRepo, never() ).recordExists( anyString(), any( Integer.class ) );
    }

    /**
     * Test UpdateLocalRecordAction.performAction(): Delete main record that has children.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A empty rawrepo.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Delete a record that has children.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Return status: FAILED_VALIDATION_INTERNAL_ERROR.
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_DeleteRecord_WithChildren() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE );
        new MarcRecordWriter( record ).markForDeletion();

        String recordId = new MarcRecordReader( record ).recordId();

        String providerId = "xxx";

        RawRepo rawRepo = mock( RawRepo.class );

        Set<RecordId> children = new HashSet<>();
        children.add( new RecordId( "xxx", 101010 ) );
        when( rawRepo.children( eq( record ) ) ).thenReturn( children );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        SolrService solrService = mock( SolrService.class );

        UpdateLocalRecordAction instance = new UpdateLocalRecordAction( rawRepo, record );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setSolrService( solrService );
        instance.setProviderId( providerId );

        instance.checkState();

        String message = String.format( messages.getString( "delete.record.children.error" ), recordId );
        assertThat( instance.performAction(), equalTo( ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message ) ) );

        verify( rawRepo, never() ).recordExists( anyString(), any( Integer.class ) );
    }

    /**
     * Test UpdateLocalRecordAction.performAction(): Delete volume record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A empty rawrepo.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Delete a record that has children.
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
    public void testPerformAction_DeleteVolumeRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE );
        new MarcRecordWriter( record ).markForDeletion();

        String providerId = "xxx";

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.children( eq( record ) ) ).thenReturn( new HashSet<>() );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( record ) ).thenReturn( new HashSet<>() );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );

        SolrService solrService = mock( SolrService.class );
        when( solrService.hasDocuments( eq( SolrServiceIndexer.createSubfieldQuery( "002a", AssertActionsUtil.getRecordId( record ) ) ) ) ).thenReturn( false );

        UpdateLocalRecordAction instance = new UpdateLocalRecordAction( rawRepo, record );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setSolrService( solrService );
        instance.setProviderId( providerId );

        instance.checkState();

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertRemoveLinksAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertDeleteRecordAction( iterator.next(), rawRepo, record, UpdateLocalRecordAction.MIMETYPE );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, record, providerId, UpdateLocalRecordAction.MIMETYPE );

        assertThat( iterator.hasNext(), is( false ) );
    }

    /**
     * Test UpdateLocalRecordAction.performAction(): Delete the last volume record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A raw repo with two records:
     *          <ol>
     *              <li>Main record <code>m</code></li>
     *              <li>Volume record <code>v</code></li>
     *          </ol>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Delete the record <code>v</code>
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>RemoveLinksAction: Remove any existing links to other records</li>
     *              <li>DeleteRecordAction: Deletes the record</li>
     *              <li>EnqueueRecordAction: Put the record in queue</li>
     *              <li>UpdateLocalRecordAction: Delete the main record <code>m</code></li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_DeleteLastVolumeRecord() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.LOCAL_MAIN_RECORD_RESOURCE );
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.LOCAL_VOLUME_RECORD_RESOURCE );
        new MarcRecordWriter( record ).markForDeletion();

        String mainRecordId = AssertActionsUtil.getRecordId( mainRecord );
        String recordId = AssertActionsUtil.getRecordId( record );
        Integer agencyId = AssertActionsUtil.getAgencyId( record );

        String providerId = "xxx";

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( mainRecordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( recordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( mainRecordId ), eq( agencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( mainRecord, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.fetchRecord( eq( recordId ), eq( agencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( record, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.children( eq( new RecordId( mainRecordId, agencyId ) ) ) ).thenReturn( AssertActionsUtil.createRecordSet( record ) );
        when( rawRepo.children( eq( record ) ) ).thenReturn( AssertActionsUtil.createRecordSet() );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( mainRecord ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( record ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );

        SolrService solrService = mock( SolrService.class );
        when( solrService.hasDocuments( eq( SolrServiceIndexer.createSubfieldQuery( "002a", AssertActionsUtil.getRecordId( record ) ) ) ) ).thenReturn( false );

        UpdateLocalRecordAction instance = new UpdateLocalRecordAction( rawRepo, record );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setSolrService( solrService );
        instance.setProviderId( providerId );

        instance.checkState();

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertRemoveLinksAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertDeleteRecordAction( iterator.next(), rawRepo, record, UpdateLocalRecordAction.MIMETYPE );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, record, providerId, OverwriteSingleRecordAction.MIMETYPE );

        new MarcRecordWriter( mainRecord ).markForDeletion();
        AssertActionsUtil.assertUpdateLocalRecordAction( iterator.next(), rawRepo, mainRecord, holdingsItems );

        assertThat( iterator.hasNext(), is( false ) );
    }

    /**
     * Test UpdateLocalRecordAction.performAction(): Delete volume record that has holdings.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          Holdings for the updated volume record.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Delete the record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Return status: FAILED_VALIDATION_INTERNAL_ERROR.
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_DeleteVolumeRecord_WithHoldings() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE );

        AgencyNumber agencyId = new AgencyNumber( new MarcRecordReader( record ).agencyIdAsInteger() );
        new MarcRecordWriter( record ).markForDeletion();

        String providerId = "xxx";

        RawRepo rawRepo = mock( RawRepo.class );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        Set<Integer> holdings = new HashSet<>();
        holdings.add( agencyId.getAgencyId() );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( eq( record ) ) ).thenReturn( holdings );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( agencyId.toString(), LibraryRuleHandler.Rule.AUTH_EXPORT_HOLDINGS ) ).thenReturn( true );

        SolrService solrService = mock( SolrService.class );

        UpdateLocalRecordAction instance = new UpdateLocalRecordAction( rawRepo, record );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setSolrService( solrService );
        instance.setProviderId( providerId );

        instance.checkState();

        when( rawRepo.children( eq( record ) ) ).thenReturn( new HashSet<RecordId>() );

        String message = messages.getString( "delete.local.with.holdings.error" );
        assertThat( instance.performAction(), equalTo( ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message ) ) );
    }

    /**
     * Test UpdateLocalRecordAction.performAction(): Delete volume record that has holdings and
     * with no export of holdings the agency base.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          <ol>
     *              <li>Holdings for the updated volume record.</li>
     *              <li>Agency base has the setting "export of holdings" to false.</li>
     *          </ol>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Delete the record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>RemoveLinksAction: Remove any existing links to other records</li>
     *              <li>DeleteRecordAction: Deletes the record</li>
     *              <li>EnqueueRecordAction: Put the record in queue</li>
     *              <li>UpdateLocalRecordAction: Delete the main record <code>m</code></li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_DeleteVolumeRecord_WithHoldings_DoesNotExportHoldings() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE );

        AgencyNumber agencyId = new AgencyNumber( new MarcRecordReader( record ).agencyIdAsInteger() );
        new MarcRecordWriter( record ).markForDeletion();

        String providerId = "xxx";

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.children( eq( record ) ) ).thenReturn( new HashSet<>() );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        Set<Integer> holdings = new HashSet<>();
        holdings.add( agencyId.getAgencyId() );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( eq( record ) ) ).thenReturn( holdings );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( agencyId.toString(), LibraryRuleHandler.Rule.AUTH_EXPORT_HOLDINGS ) ).thenReturn( false );

        SolrService solrService = mock( SolrService.class );

        UpdateLocalRecordAction instance = new UpdateLocalRecordAction( rawRepo, record );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setSolrService( solrService );
        instance.setProviderId( providerId );

        instance.checkState();

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertRemoveLinksAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertDeleteRecordAction( iterator.next(), rawRepo, record, UpdateLocalRecordAction.MIMETYPE );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, record, providerId, OverwriteSingleRecordAction.MIMETYPE );
    }

    /**
     * Test UpdateLocalRecordAction.performAction(): Delete single record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          Nothing.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Delete a single record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>StoreRecordAction: Store the record</li>
     *              <li>RemoveLinksAction: Remove any existing links to other records</li>
     *              <li>EnqueueRecordAction: Put the record in queue</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_DeleteSingleRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        new MarcRecordWriter( record ).markForDeletion();

        String providerId = "xxx";

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.children( eq( record ) ) ).thenReturn( new HashSet<>() );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( record ) ).thenReturn( new HashSet<>() );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );

        SolrService solrService = mock( SolrService.class );

        UpdateLocalRecordAction instance = new UpdateLocalRecordAction( rawRepo, record );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setSolrService( solrService );
        instance.setProviderId( providerId );

        instance.checkState();

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertRemoveLinksAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertDeleteRecordAction( iterator.next(), rawRepo, record, UpdateLocalRecordAction.MIMETYPE );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, record, providerId, OverwriteSingleRecordAction.MIMETYPE );
    }

    /**
     * Test UpdateLocalRecordAction.performAction(): Delete single record that has holdings.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A record with holdings.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Delete the record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Return status: FAILED_UPDATE_INTERNAL_ERROR
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_DeleteSingleRecord_WithHoldings() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );

        AgencyNumber agencyId = new AgencyNumber( new MarcRecordReader( record ).agencyIdAsInteger() );
        new MarcRecordWriter( record ).markForDeletion();

        String providerId = "xxx";

        RawRepo rawRepo = mock( RawRepo.class );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        Set<Integer> holdings = new HashSet<>();
        holdings.add( agencyId.getAgencyId() );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( record ) ).thenReturn( holdings );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( agencyId.toString(), LibraryRuleHandler.Rule.AUTH_EXPORT_HOLDINGS ) ).thenReturn( true );

        SolrService solrService = mock( SolrService.class );

        UpdateLocalRecordAction instance = new UpdateLocalRecordAction( rawRepo, record );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setSolrService( solrService );
        instance.setProviderId( providerId );

        instance.checkState();

        when( rawRepo.children( eq( record ) ) ).thenReturn( new HashSet<RecordId>() );

        String message = messages.getString( "delete.local.with.holdings.error" );
        assertThat( instance.performAction(), equalTo( ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message ) ) );
    }

    /**
     * Test UpdateLocalRecordAction.performAction(): Delete record that has holdings and
     * with no export of holdings the agency base.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          <ol>
     *              <li>Holdings for the updated record.</li>
     *              <li>Agency base has the setting "export of holdings" to false.</li>
     *          </ol>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Delete the record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>StoreRecordAction: Store the record</li>
     *              <li>RemoveLinksAction: Remove any existing links to other records</li>
     *              <li>EnqueueRecordAction: Put the record in queue</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_DeleteSingleRecord_WithHoldings_DoesNotExportHoldings() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );

        AgencyNumber agencyId = new AgencyNumber( new MarcRecordReader( record ).agencyIdAsInteger() );
        new MarcRecordWriter( record ).markForDeletion();

        String providerId = "xxx";

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.children( eq( record ) ) ).thenReturn( new HashSet<>() );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        Set<Integer> holdings = new HashSet<>();
        holdings.add( agencyId.getAgencyId() );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( record ) ).thenReturn( holdings );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( agencyId.toString(), LibraryRuleHandler.Rule.AUTH_EXPORT_HOLDINGS ) ).thenReturn( false );

        SolrService solrService = mock( SolrService.class );

        UpdateLocalRecordAction instance = new UpdateLocalRecordAction( rawRepo, record );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setSolrService( solrService );
        instance.setProviderId( providerId );

        instance.checkState();

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertRemoveLinksAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertDeleteRecordAction( iterator.next(), rawRepo, record, UpdateLocalRecordAction.MIMETYPE );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, record, providerId, OverwriteSingleRecordAction.MIMETYPE );
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private ResourceBundle messages;
}
