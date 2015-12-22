//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordFactory;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.records.MarcRecordWriter;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.HoldingsItems;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.SolrService;
import dk.dbc.updateservice.update.SolrServiceIndexer;
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
        InputStream is = getClass().getResourceAsStream( BOOK_RECORD_RESOURCE );
        MarcRecord record = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );
        RawRepo rawRepo = mock( RawRepo.class );

        assertThat( new UpdateLocalRecordAction( rawRepo, record ), notNullValue() );
    }

    /**
     * Test UpdateLocalRecordAction.performAction(): Create single record with no 002 links from
     * other records.
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
    public void testPerformAction_CreateSingleRecord_No002Links() throws Exception {
        InputStream is = getClass().getResourceAsStream( BOOK_RECORD_RESOURCE );
        MarcRecord record = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );

        RawRepo rawRepo = mock( RawRepo.class );

        SolrService solrService = mock( SolrService.class );
        when( solrService.hasDocuments( eq( SolrServiceIndexer.createSubfieldQuery( "002a", AssertActionsUtil.getRecordId( record ) ) ) ) ).thenReturn( false );

        UpdateLocalRecordAction instance = new UpdateLocalRecordAction( rawRepo, record );
        instance.setSolrService( solrService );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 3 ) );

        ServiceAction child = children.get( 0 );
        assertTrue( child.getClass() == StoreRecordAction.class );

        StoreRecordAction storeRecordAction = (StoreRecordAction)child;
        assertThat( storeRecordAction.getRawRepo(), is( rawRepo ) );
        assertThat( storeRecordAction.getRecord(), is( record ) );
        assertThat( storeRecordAction.getMimetype(), equalTo( UpdateLocalRecordAction.MIMETYPE ) );

        child = children.get( 1 );
        assertTrue( child.getClass() == RemoveLinksAction.class );

        RemoveLinksAction removeLinksAction = (RemoveLinksAction)child;
        assertThat( removeLinksAction.getRawRepo(), is( rawRepo ) );
        assertThat( removeLinksAction.getRecord(), is( record ) );

        child = children.get( 2 );
        assertTrue( child.getClass() == EnqueueRecordAction.class );

        EnqueueRecordAction enqueueRecordAction = (EnqueueRecordAction)child;
        assertThat( enqueueRecordAction.getRawRepo(), is( rawRepo ) );
        assertThat( enqueueRecordAction.getRecord(), is( record ) );
        assertThat( enqueueRecordAction.getMimetype(), equalTo( UpdateLocalRecordAction.MIMETYPE ) );
    }

    /**
     * Test UpdateLocalRecordAction.performAction(): Create single record with 002 links from
     * other records.
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
     *          Return status: FAILED_UPDATE_INTERNAL_ERROR
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_CreateSingleRecord_With002Links() throws Exception {
        InputStream is = getClass().getResourceAsStream( BOOK_RECORD_RESOURCE );
        MarcRecord record = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );

        RawRepo rawRepo = mock( RawRepo.class );

        SolrService solrService = mock( SolrService.class );
        when( solrService.hasDocuments( eq( SolrServiceIndexer.createSubfieldQuery( "002a", AssertActionsUtil.getRecordId( record ) ) ) ) ).thenReturn( true );

        UpdateLocalRecordAction instance = new UpdateLocalRecordAction( rawRepo, record );
        instance.setSolrService( solrService );

        String message = messages.getString( "create.record.with.002.links" );
        assertThat( instance.performAction(), equalTo( ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message ) ) );
        assertThat( instance.children().isEmpty(), is( true ) );
    }

    /**
     * Test UpdateLocalRecordAction.performAction(): Create volume record with no 002 links from
     * other records.
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
    public void testPerformAction_CreateVolumeRecord_No002Links() throws Exception {
        InputStream is = getClass().getResourceAsStream( VOLUME_RECORD_RESOURCE );
        MarcRecord record = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );

        MarcRecordReader reader = new MarcRecordReader( record );
        Integer agencyId = reader.agencyIdAsInteger();
        String parentId = reader.parentId();

        RawRepo rawRepo = mock( RawRepo.class );

        SolrService solrService = mock( SolrService.class );
        when( solrService.hasDocuments( eq( SolrServiceIndexer.createSubfieldQuery( "002a", AssertActionsUtil.getRecordId( record ) ) ) ) ).thenReturn( false );

        UpdateLocalRecordAction instance = new UpdateLocalRecordAction( rawRepo, record );
        instance.setSolrService( solrService );

        when( rawRepo.recordExists( eq( parentId ), eq( agencyId ) ) ).thenReturn( true );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 3 ) );

        ServiceAction child = children.get( 0 );
        assertTrue( child.getClass() == StoreRecordAction.class );

        StoreRecordAction storeRecordAction = (StoreRecordAction)child;
        assertThat( storeRecordAction.getRawRepo(), is( rawRepo ) );
        assertThat( storeRecordAction.getRecord(), is( record ) );
        assertThat( storeRecordAction.getMimetype(), equalTo( UpdateLocalRecordAction.MIMETYPE ) );

        child = children.get( 1 );
        assertTrue( child.getClass() == LinkRecordAction.class );

        LinkRecordAction linkRecordAction = (LinkRecordAction)child;
        assertThat( linkRecordAction.getRawRepo(), is( rawRepo ) );
        assertThat( linkRecordAction.getRecord(), is( record ) );
        assertThat( linkRecordAction.getLinkToRecordId(), equalTo( new RecordId( parentId, agencyId ) ) );

        child = children.get( 2 );
        assertTrue( child.getClass() == EnqueueRecordAction.class );

        EnqueueRecordAction enqueueRecordAction = (EnqueueRecordAction)child;
        assertThat( enqueueRecordAction.getRawRepo(), is( rawRepo ) );
        assertThat( enqueueRecordAction.getRecord(), is( record ) );
        assertThat( enqueueRecordAction.getMimetype(), equalTo( UpdateLocalRecordAction.MIMETYPE ) );
    }

    /**
     * Test UpdateLocalRecordAction.performAction(): Create volume record with 002 links from
     * other records.
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
     *          Return status: FAILED_UPDATE_INTERNAL_ERROR
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_CreateVolumeRecord_With002Links() throws Exception {
        InputStream is = getClass().getResourceAsStream( VOLUME_RECORD_RESOURCE );
        MarcRecord record = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );

        MarcRecordReader reader = new MarcRecordReader( record );
        Integer agencyId = reader.agencyIdAsInteger();
        String parentId = reader.parentId();

        RawRepo rawRepo = mock( RawRepo.class );

        SolrService solrService = mock( SolrService.class );
        when( solrService.hasDocuments( eq( SolrServiceIndexer.createSubfieldQuery( "002a", AssertActionsUtil.getRecordId( record ) ) ) ) ).thenReturn( true );

        UpdateLocalRecordAction instance = new UpdateLocalRecordAction( rawRepo, record );
        instance.setSolrService( solrService );

        when( rawRepo.recordExists( eq( parentId ), eq( agencyId ) ) ).thenReturn( true );

        String message = messages.getString( "create.record.with.002.links" );
        assertThat( instance.performAction(), equalTo( ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message ) ) );
        assertThat( instance.children().isEmpty(), is( true ) );
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
        InputStream is = getClass().getResourceAsStream( VOLUME_RECORD_RESOURCE );
        MarcRecord record = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );

        MarcRecordReader reader = new MarcRecordReader( record );
        String recordId = reader.recordId();
        Integer agencyId = reader.agencyIdAsInteger();
        String parentId = reader.parentId();

        RawRepo rawRepo = mock( RawRepo.class );
        UpdateLocalRecordAction instance = new UpdateLocalRecordAction( rawRepo, record );

        when( rawRepo.recordExists( eq( parentId ), eq( agencyId ) ) ).thenReturn( false );

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
        InputStream is = getClass().getResourceAsStream( VOLUME_RECORD_RESOURCE );
        MarcRecord record = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );

        MarcRecordReader reader = new MarcRecordReader( record );
        String recordId = reader.recordId();
        Integer agencyId = reader.agencyIdAsInteger();
        new MarcRecordWriter( record ).addOrReplaceSubfield( "014", "a", recordId );

        RawRepo rawRepo = mock( RawRepo.class );
        UpdateLocalRecordAction instance = new UpdateLocalRecordAction( rawRepo, record );

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
        InputStream is = getClass().getResourceAsStream( VOLUME_RECORD_RESOURCE );
        MarcRecord record = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );
        new MarcRecordWriter( record ).markForDeletion();

        String recordId = new MarcRecordReader( record ).recordId();

        RawRepo rawRepo = mock( RawRepo.class );

        Set<RecordId> children = new HashSet<>();
        children.add( new RecordId( "xxx", 101010 ) );
        when( rawRepo.children( eq( record ) ) ).thenReturn( children );

        UpdateLocalRecordAction instance = new UpdateLocalRecordAction( rawRepo, record );

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
        InputStream is = getClass().getResourceAsStream( VOLUME_RECORD_RESOURCE );
        MarcRecord record = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );
        new MarcRecordWriter( record ).markForDeletion();

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.children( eq( record ) ) ).thenReturn( new HashSet<RecordId>() );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( record ) ).thenReturn( new HashSet<Integer>() );

        SolrService solrService = mock( SolrService.class );
        when( solrService.hasDocuments( eq( SolrServiceIndexer.createSubfieldQuery( "002a", AssertActionsUtil.getRecordId( record ) ) ) ) ).thenReturn( false );

        UpdateLocalRecordAction instance = new UpdateLocalRecordAction( rawRepo, record );
        instance.setHoldingsItems( holdingsItems );
        instance.setSolrService( solrService );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 3 ) );

        ServiceAction child = children.get( 0 );
        assertTrue( child.getClass() == RemoveLinksAction.class );

        RemoveLinksAction removeLinksAction = (RemoveLinksAction)child;
        assertThat( removeLinksAction.getRawRepo(), is( rawRepo ) );
        assertThat( removeLinksAction.getRecord(), is( record ) );

        child = children.get( 1 );
        assertTrue( child.getClass() == DeleteRecordAction.class );

        DeleteRecordAction deleteRecordAction = (DeleteRecordAction)child;
        assertThat( deleteRecordAction.getRawRepo(), is( rawRepo ) );
        assertThat( deleteRecordAction.getRecord(), is( record ) );
        assertThat( deleteRecordAction.getMimetype(), equalTo( UpdateLocalRecordAction.MIMETYPE ) );

        child = children.get( 2 );
        assertTrue( child.getClass() == EnqueueRecordAction.class );

        EnqueueRecordAction enqueueRecordAction = (EnqueueRecordAction)child;
        assertThat( enqueueRecordAction.getRawRepo(), is( rawRepo ) );
        assertThat( enqueueRecordAction.getRecord(), is( record ) );
        assertThat( enqueueRecordAction.getMimetype(), equalTo( UpdateLocalRecordAction.MIMETYPE ) );
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

        SolrService solrService = mock( SolrService.class );
        when( solrService.hasDocuments( eq( SolrServiceIndexer.createSubfieldQuery( "002a", AssertActionsUtil.getRecordId( record ) ) ) ) ).thenReturn( false );

        UpdateLocalRecordAction instance = new UpdateLocalRecordAction( rawRepo, record );
        instance.setHoldingsItems( holdingsItems );
        instance.setSolrService( solrService );

        String providerId = "xxx";
        instance.setProviderId( providerId );

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
        InputStream is = getClass().getResourceAsStream( VOLUME_RECORD_RESOURCE );
        MarcRecord record = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );

        Integer agencyId = new MarcRecordReader( record ).agencyIdAsInteger();
        new MarcRecordWriter( record ).markForDeletion();

        RawRepo rawRepo = mock( RawRepo.class );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        Set<Integer> holdings = new HashSet<>();
        holdings.add( agencyId );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( eq( record ) ) ).thenReturn( holdings );

        UpdateLocalRecordAction instance = new UpdateLocalRecordAction( rawRepo, record );
        instance.setHoldingsItems( holdingsItems );

        when( rawRepo.children( eq( record ) ) ).thenReturn( new HashSet<RecordId>() );

        String message = messages.getString( "delete.local.with.holdings.error" );
        assertThat( instance.performAction(), equalTo( ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message ) ) );
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
        InputStream is = getClass().getResourceAsStream( BOOK_RECORD_RESOURCE );
        MarcRecord record = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );
        new MarcRecordWriter( record ).markForDeletion();

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.children( eq( record ) ) ).thenReturn( new HashSet<RecordId>() );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( record ) ).thenReturn( new HashSet<Integer>() );

        UpdateLocalRecordAction instance = new UpdateLocalRecordAction( rawRepo, record );
        instance.setHoldingsItems( holdingsItems );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 3 ) );

        ServiceAction child = children.get( 0 );
        assertTrue( child.getClass() == RemoveLinksAction.class );

        RemoveLinksAction removeLinksAction = (RemoveLinksAction)child;
        assertThat( removeLinksAction.getRawRepo(), is( rawRepo ) );
        assertThat( removeLinksAction.getRecord(), is( record ) );

        child = children.get( 1 );
        assertTrue( child.getClass() == DeleteRecordAction.class );

        DeleteRecordAction deleteRecordAction = (DeleteRecordAction)child;
        assertThat( deleteRecordAction.getRawRepo(), is( rawRepo ) );
        assertThat( deleteRecordAction.getRecord(), is( record ) );
        assertThat( deleteRecordAction.getMimetype(), equalTo( UpdateLocalRecordAction.MIMETYPE ) );

        child = children.get( 2 );
        assertTrue( child.getClass() == EnqueueRecordAction.class );

        EnqueueRecordAction enqueueRecordAction = (EnqueueRecordAction)child;
        assertThat( enqueueRecordAction.getRawRepo(), is( rawRepo ) );
        assertThat( enqueueRecordAction.getRecord(), is( record ) );
        assertThat( enqueueRecordAction.getMimetype(), equalTo( UpdateLocalRecordAction.MIMETYPE ) );
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
        InputStream is = getClass().getResourceAsStream( BOOK_RECORD_RESOURCE );
        MarcRecord record = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );
        Integer agencyId = new MarcRecordReader( record ).agencyIdAsInteger();
        new MarcRecordWriter( record ).markForDeletion();

        RawRepo rawRepo = mock( RawRepo.class );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        Set<Integer> holdings = new HashSet<>();
        holdings.add( agencyId );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( record ) ).thenReturn( holdings );

        UpdateLocalRecordAction instance = new UpdateLocalRecordAction( rawRepo, record );
        instance.setHoldingsItems( holdingsItems );

        when( rawRepo.children( eq( record ) ) ).thenReturn( new HashSet<RecordId>() );

        String message = messages.getString( "delete.local.with.holdings.error" );
        assertThat( instance.performAction(), equalTo( ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message ) ) );
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final String BOOK_RECORD_RESOURCE = "/dk/dbc/updateservice/actions/book.marc";
    private static final String VOLUME_RECORD_RESOURCE = "/dk/dbc/updateservice/actions/volume.marc";

    private ResourceBundle messages;
}
