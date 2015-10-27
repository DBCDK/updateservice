//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcWriter;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.HoldingsItems;
import dk.dbc.updateservice.update.LibraryRecordsHandler;
import dk.dbc.updateservice.update.OpenAgencyService;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.ws.JNDIResources;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.ListIterator;
import java.util.Properties;
import java.util.ResourceBundle;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

//-----------------------------------------------------------------------------
public class OverwriteSingleRecordActionTest {
    public OverwriteSingleRecordActionTest() {
        this.messages = ResourceBundles.getBundle( this, "actions" );
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
     *              <li>StoreRecordAction: Store the record</li>
     *              <li>RemoveLinksAction: Remove any existing links to other records</li>
     *              <li>EnqueueRecordAction: Put the record in queue</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_NoClassifications() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( record );
        Integer agencyId = AssertActionsUtil.getAgencyId( record );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( recordId ), eq( agencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( record, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.agenciesForRecord( eq( record ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( record ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( agencyId.toString(), LibraryRuleHandler.Rule.USE_ENRICHMENTS ) ).thenReturn( true );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( record ) ).thenReturn( false );

        OverwriteSingleRecordAction instance = new OverwriteSingleRecordAction( rawRepo, record );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setRecordsHandler( recordsHandler );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 3 ) );

        AssertActionsUtil.assertStoreRecordAction( children.get( 0 ), rawRepo, record );
        AssertActionsUtil.assertRemoveLinksAction( children.get( 1 ), rawRepo, record );
        AssertActionsUtil.assertEnqueueRecordAction( children.get( 2 ), rawRepo, record, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), OverwriteSingleRecordAction.MIMETYPE );
    }

    /**
     * Test performAction(): Update single common record without changes to
     * its current classifications.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with a single common record and a DBC enrichment
     *          record. The common record has classifications.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update the common record, with changed data except for
     *          classifications.
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
    public void testPerformAction_SameClassifications() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( record );
        Integer agencyId = AssertActionsUtil.getAgencyId( record );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( recordId ), eq( agencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( record, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.agenciesForRecord( eq( record ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( record ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( agencyId.toString(), LibraryRuleHandler.Rule.USE_ENRICHMENTS ) ).thenReturn( true );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( eq( record ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( eq( record ), eq( record ) ) ).thenReturn( false );

        OverwriteSingleRecordAction instance = new OverwriteSingleRecordAction( rawRepo, record );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setRecordsHandler( recordsHandler );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 3 ) );

        AssertActionsUtil.assertStoreRecordAction( children.get( 0 ), rawRepo, record );
        AssertActionsUtil.assertRemoveLinksAction( children.get( 1 ), rawRepo, record );
        AssertActionsUtil.assertEnqueueRecordAction( children.get( 2 ), rawRepo, record, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), OverwriteSingleRecordAction.MIMETYPE );
    }

    /**
     * Test performAction(): Update single common record with changes to
     * its current classifications, but with no holdings for local libraries.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with a single common record and a DBC enrichment
     *          record. The common record has classifications.
     *          <p>
     *              No holdings for local libraries.
     *          </p>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update the common record, with changed classifications.
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
    public void testPerformAction_ChangedClassifications_NoHoldings() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( record );
        Integer agencyId = AssertActionsUtil.getAgencyId( record );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( recordId ), eq( agencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( record, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.agenciesForRecord( eq( record ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( record ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( agencyId.toString(), LibraryRuleHandler.Rule.USE_ENRICHMENTS ) ).thenReturn( true );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( eq( record ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( eq( record ), eq( record ) ) ).thenReturn( true );

        OverwriteSingleRecordAction instance = new OverwriteSingleRecordAction( rawRepo, record );
        instance.setGroupId( 700000 );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setRecordsHandler( recordsHandler );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 3 ) );

        AssertActionsUtil.assertStoreRecordAction( children.get( 0 ), rawRepo, record );
        AssertActionsUtil.assertRemoveLinksAction( children.get( 1 ), rawRepo, record );
        AssertActionsUtil.assertEnqueueRecordAction( children.get( 2 ), rawRepo, record, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), OverwriteSingleRecordAction.MIMETYPE );
    }

    /**
     * Test performAction(): Update single common record with changes to
     * its current classifications, and with holdings for a local library.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with a single common record and a DBC enrichment
     *          record. The common record has classifications.
     *          <p>
     *              Holdings for a local library.
     *          </p>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update the common record, with changed classifications.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>StoreRecordAction: Store the record</li>
     *              <li>RemoveLinksAction: Remove any existing links to other records</li>
     *              <li>
     *                  CreateEnrichmentRecordWithClassificationsAction: Create enrichment record for
     *                  the local library with holdings.
     *              </li>
     *              <li>EnqueueRecordAction: Put the record in queue</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_ChangedClassifications_Holdings_CreateEnrichment() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( record );
        Integer agencyId = AssertActionsUtil.getAgencyId( record );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( recordId ), eq( agencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( record, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.agenciesForRecord( eq( record ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( eq( recordId ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet( 700100 ) );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( eq( "700100" ), eq( LibraryRuleHandler.Rule.USE_ENRICHMENTS ) ) ).thenReturn( true );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( eq( record ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( eq( record ), eq( record ) ) ).thenReturn( true );
        when( recordsHandler.shouldCreateEnrichmentRecords( eq( settings ), eq( record ), eq( record ) ) ).thenReturn( ServiceResult.newOkResult() );

        OverwriteSingleRecordAction instance = new OverwriteSingleRecordAction( rawRepo, record );
        instance.setGroupId( 700000 );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setRecordsHandler( recordsHandler );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 4 ) );

        AssertActionsUtil.assertStoreRecordAction( children.get( 0 ), rawRepo, record );
        AssertActionsUtil.assertRemoveLinksAction( children.get( 1 ), rawRepo, record );
        AssertActionsUtil.assertCreateEnrichmentAction( children.get( 2 ), rawRepo, record, 700100, null );
        AssertActionsUtil.assertEnqueueRecordAction( children.get( 3 ), rawRepo, record, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), OverwriteSingleRecordAction.MIMETYPE );
    }

    /**
     * Test performAction(): Update single common record with changes to
     * its current classifications, and with holdings for a local library.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with a single common record and a DBC enrichment
     *          record. The common record has classifications.
     *          <p>
     *              Holdings for a local library.
     *          </p>
     *          <p>
     *              Local agency does not has the feature 'use_enrichments'
     *          </p>
     *      </dd>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update the common record, with changed classifications.
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
    public void testPerformAction_ChangedClassifications_Holdings_CreateEnrichment_LocalAgencyNoEnrichments() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( record );
        Integer agencyId = AssertActionsUtil.getAgencyId( record );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( recordId ), eq( agencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( record, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.agenciesForRecord( eq( record ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( eq( recordId ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet( 700100 ) );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( eq( "700100" ), eq( LibraryRuleHandler.Rule.USE_ENRICHMENTS ) ) ).thenReturn( false );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( eq( record ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( eq( record ), eq( record ) ) ).thenReturn( true );
        when( recordsHandler.shouldCreateEnrichmentRecords( eq( settings ), eq( record ), eq( record ) ) ).thenReturn( ServiceResult.newOkResult() );

        OverwriteSingleRecordAction instance = new OverwriteSingleRecordAction( rawRepo, record );
        instance.setGroupId( 700000 );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setRecordsHandler( recordsHandler );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 3 ) );

        AssertActionsUtil.assertStoreRecordAction( children.get( 0 ), rawRepo, record );
        AssertActionsUtil.assertRemoveLinksAction( children.get( 1 ), rawRepo, record );
        AssertActionsUtil.assertEnqueueRecordAction( children.get( 2 ), rawRepo, record, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), OverwriteSingleRecordAction.MIMETYPE );
    }

    /**
     * Test performAction(): Update single common record with changes to
     * its current classifications, and with holdings for a local library.
     * <p>
     *     LibraryRecordsHandler.shouldCreateEnrichmentRecords returns UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR
     *     so new enrichment records should not be created.
     * </p>
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with a single common record and a DBC enrichment
     *          record. The common record has classifications.
     *          <p>
     *              Holdings for a local library.
     *          </p>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update the common record, with changed classifications.
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
    public void testPerformAction_ChangedClassifications_Holdings_ShouldNotCreateEnrichment() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( record );
        Integer agencyId = AssertActionsUtil.getAgencyId( record );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( recordId ), eq( agencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( record, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.agenciesForRecord( eq( record ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( eq( recordId ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet( 700100 ) );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( agencyId.toString(), LibraryRuleHandler.Rule.USE_ENRICHMENTS ) ).thenReturn( true );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( eq( record ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( eq( record ), eq( record ) ) ).thenReturn( true );
        when( recordsHandler.shouldCreateEnrichmentRecords( eq( settings ), eq( record ), eq( record ) ) ).thenReturn( ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, "reason" ) );

        OverwriteSingleRecordAction instance = new OverwriteSingleRecordAction( rawRepo, record );
        instance.setGroupId( 700000 );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setRecordsHandler( recordsHandler );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 3 ) );

        AssertActionsUtil.assertStoreRecordAction( children.get( 0 ), rawRepo, record );
        AssertActionsUtil.assertRemoveLinksAction( children.get( 1 ), rawRepo, record );
        AssertActionsUtil.assertEnqueueRecordAction( children.get( 2 ), rawRepo, record, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), OverwriteSingleRecordAction.MIMETYPE );
    }

    /**
     * Test performAction(): Update single common record with changes to
     * its current classifications, and with enrichment for a local library.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with a single common record and a DBC enrichment
     *          record. The common record has classifications.
     *          <p>
     *              Enrichment record for a local library.
     *          </p>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update the common record, with changed classifications.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>StoreRecordAction: Store the record</li>
     *              <li>RemoveLinksAction: Remove any existing links to other records</li>
     *              <li>
     *                  UpdateClassificationsInEnrichmentRecordAction: Update enrichment record with
     *                  new classifications.
     *              </li>
     *              <li>EnqueueRecordAction: Put the record in queue</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_ChangedClassifications_Holdings_UpdateEnrichment() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( record );
        Integer agencyId = AssertActionsUtil.getAgencyId( record );

        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE );
        Integer enrichmentAgencyId = AssertActionsUtil.getAgencyId( enrichmentRecord );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( recordId ), eq( agencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( record, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.agenciesForRecord( eq( record ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet( enrichmentAgencyId ) );

        when( rawRepo.recordExists( eq( recordId ), eq( enrichmentAgencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( recordId ), eq( enrichmentAgencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( enrichmentRecord, MarcXChangeMimeType.ENRICHMENT ) );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( eq( recordId ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet( enrichmentAgencyId ) );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( eq( enrichmentAgencyId.toString() ), eq( LibraryRuleHandler.Rule.USE_ENRICHMENTS ) ) ).thenReturn( true );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( eq( record ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( eq( record ), eq( record ) ) ).thenReturn( true );

        OverwriteSingleRecordAction instance = new OverwriteSingleRecordAction( rawRepo, record );
        instance.setGroupId( 700000 );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setRecordsHandler( recordsHandler );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 4 ) );

        AssertActionsUtil.assertStoreRecordAction( children.get( 0 ), rawRepo, record );
        AssertActionsUtil.assertRemoveLinksAction( children.get( 1 ), rawRepo, record );
        AssertActionsUtil.assertUpdateClassificationsInEnrichmentRecordAction( children.get( 2 ), rawRepo, record, enrichmentRecord );
        AssertActionsUtil.assertEnqueueRecordAction( children.get( 3 ), rawRepo, record, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), OverwriteSingleRecordAction.MIMETYPE );
    }

    /**
     * Test performAction(): Update single common record with changes to
     * its current classifications, and with enrichment for a local library.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with a single common record and a DBC enrichment
     *          record. The common record has classifications.
     *          <p>
     *              Enrichment record for a local library.
     *          </p>
     *          <p>
     *              Local agency does not has the feature 'use_enrichments'
     *          </p>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update the common record, with changed classifications.
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
    public void testPerformAction_ChangedClassifications_Holdings_UpdateEnrichment_LocalAgencyNoEnrichments() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( record );
        Integer agencyId = AssertActionsUtil.getAgencyId( record );

        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE );
        Integer enrichmentAgencyId = AssertActionsUtil.getAgencyId( enrichmentRecord );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( recordId ), eq( agencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( record, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.agenciesForRecord( eq( record ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet( enrichmentAgencyId ) );

        when( rawRepo.recordExists( eq( recordId ), eq( enrichmentAgencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( recordId ), eq( enrichmentAgencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( enrichmentRecord, MarcXChangeMimeType.ENRICHMENT ) );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( eq( recordId ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet( enrichmentAgencyId ) );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( eq( enrichmentAgencyId.toString() ), eq( LibraryRuleHandler.Rule.USE_ENRICHMENTS ) ) ).thenReturn( true );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( eq( record ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( eq( record ), eq( record ) ) ).thenReturn( true );

        OverwriteSingleRecordAction instance = new OverwriteSingleRecordAction( rawRepo, record );
        instance.setGroupId( 700000 );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setRecordsHandler( recordsHandler );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 4 ) );

        AssertActionsUtil.assertStoreRecordAction( children.get( 0 ), rawRepo, record );
        AssertActionsUtil.assertRemoveLinksAction( children.get( 1 ), rawRepo, record );
        AssertActionsUtil.assertEnqueueRecordAction( children.get( 3 ), rawRepo, record, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), OverwriteSingleRecordAction.MIMETYPE );
    }

    /**
     * Test performAction(): Update single common record with changes to
     * its current classifications, and creation/update of enrichments.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          <ol>
     *              <li>
     *                  A rawrepo with a single common record with classifications
     *              </li>
     *              <li>A DBC enrichment</li>
     *              <li>
     *                  Holdings for a local library, <code>l1</code>, with no enrichment for
     *                  the updated record.
     *              </li>
     *              <li>
     *                  Enrichment for another library, <code>l2</code>.
     *              </li>
     *          </ol>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update the common record, with changed classifications.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>StoreRecordAction: Store the record</li>
     *              <li>RemoveLinksAction: Remove any existing links to other records</li>
     *              <li>
     *                  CreateEnrichmentRecordWithClassificationsAction: Create enrichment record with
     *                  new classifications for library <code>l1</code>.
     *              </li>
     *              <li>
     *                  UpdateClassificationsInEnrichmentRecordAction: Update enrichment record with
     *                  new classifications for library <code>l2</code>.
     *              </li>
     *              <li>EnqueueRecordAction: Put the record in queue</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_ChangedClassifications_Holdings_CreateAndUpdateEnrichment() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( record );
        Integer agencyId = AssertActionsUtil.getAgencyId( record );

        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE );
        Integer enrichmentAgencyId = AssertActionsUtil.getAgencyId( enrichmentRecord );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( recordId ), eq( agencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( record, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.agenciesForRecord( eq( record ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet( enrichmentAgencyId ) );

        when( rawRepo.recordExists( eq( recordId ), eq( enrichmentAgencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( recordId ), eq( enrichmentAgencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( enrichmentRecord, MarcXChangeMimeType.ENRICHMENT ) );

        Integer newEnrichmentAgencyId = enrichmentAgencyId + 100;

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( eq( recordId ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet( enrichmentAgencyId, newEnrichmentAgencyId ) );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( eq( enrichmentAgencyId.toString() ), eq( LibraryRuleHandler.Rule.USE_ENRICHMENTS ) ) ).thenReturn( true );
        when( openAgencyService.hasFeature( eq( newEnrichmentAgencyId.toString() ), eq( LibraryRuleHandler.Rule.USE_ENRICHMENTS ) ) ).thenReturn( true );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( eq( record ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( eq( record ), eq( record ) ) ).thenReturn( true );
        when( recordsHandler.shouldCreateEnrichmentRecords( eq( settings ), eq( record ), eq( record ) ) ).thenReturn( ServiceResult.newOkResult() );

        OverwriteSingleRecordAction instance = new OverwriteSingleRecordAction( rawRepo, record );
        instance.setGroupId( 700000 );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setRecordsHandler( recordsHandler );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 5 ) );

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertRemoveLinksAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertUpdateClassificationsInEnrichmentRecordAction( iterator.next(), rawRepo, record, enrichmentRecord );
        AssertActionsUtil.assertCreateEnrichmentAction( iterator.next(), rawRepo, record, newEnrichmentAgencyId, null );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, record, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), OverwriteSingleRecordAction.MIMETYPE );
    }

    /**
     * Test performAction(): Update single common record with changes to
     * its current classifications, and creation/update of enrichments.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          <ol>
     *              <li>
     *                  A rawrepo with a single common record with classifications
     *              </li>
     *              <li>A DBC enrichment</li>
     *              <li>
     *                  Holdings for a local library, <code>l1</code>, with no enrichment for
     *                  the updated record.
     *              </li>
     *              <li>
     *                  Enrichment for another library, <code>l2</code>.
     *              </li>
     *              <li>
     *                  Local agency does not has the feature 'use_enrichments'
     *              </li>
     *          </ol>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update the common record, with changed classifications.
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
    public void testPerformAction_ChangedClassifications_Holdings_CreateAndUpdateEnrichment_LocalAgencyNoEnrichments() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( record );
        Integer agencyId = AssertActionsUtil.getAgencyId( record );

        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE );
        Integer enrichmentAgencyId = AssertActionsUtil.getAgencyId( enrichmentRecord );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( recordId ), eq( agencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( record, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.agenciesForRecord( eq( record ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet( enrichmentAgencyId ) );

        when( rawRepo.recordExists( eq( recordId ), eq( enrichmentAgencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( recordId ), eq( enrichmentAgencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( enrichmentRecord, MarcXChangeMimeType.ENRICHMENT ) );

        Integer newEnrichmentAgencyId = enrichmentAgencyId + 100;

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( eq( recordId ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet( enrichmentAgencyId, newEnrichmentAgencyId ) );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( eq( enrichmentAgencyId.toString() ), eq( LibraryRuleHandler.Rule.USE_ENRICHMENTS ) ) ).thenReturn( false );
        when( openAgencyService.hasFeature( eq( newEnrichmentAgencyId.toString() ), eq( LibraryRuleHandler.Rule.USE_ENRICHMENTS ) ) ).thenReturn( false );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( eq( record ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( eq( record ), eq( record ) ) ).thenReturn( true );
        when( recordsHandler.shouldCreateEnrichmentRecords( eq( settings ), eq( record ), eq( record ) ) ).thenReturn( ServiceResult.newOkResult() );

        OverwriteSingleRecordAction instance = new OverwriteSingleRecordAction( rawRepo, record );
        instance.setGroupId( 700000 );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setRecordsHandler( recordsHandler );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 3 ) );

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertRemoveLinksAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, record, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), OverwriteSingleRecordAction.MIMETYPE );
    }

    /**
     * Test performAction(): Update single common record with changes to
     * its current classifications, and creation/update of enrichments.
     * <p>
     *     LibraryRecordsHandler.shouldCreateEnrichmentRecords returns UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR
     *     so new enrichment records should not be created.
     * </p>
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          <ol>
     *              <li>
     *                  A rawrepo with a single common record with classifications
     *              </li>
     *              <li>A DBC enrichment</li>
     *              <li>
     *                  Holdings for a local library, <code>l1</code>, with no enrichment for
     *                  the updated record.
     *              </li>
     *              <li>
     *                  Enrichment for another library, <code>l2</code>.
     *              </li>
     *          </ol>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update the common record, with changed classifications.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>StoreRecordAction: Store the record</li>
     *              <li>RemoveLinksAction: Remove any existing links to other records</li>
     *              <li>
     *                  UpdateClassificationsInEnrichmentRecordAction: Update enrichment record with
     *                  new classifications for library <code>l2</code>.
     *              </li>
     *              <li>EnqueueRecordAction: Put the record in queue</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_ChangedClassifications_Holdings_ShouldNotCreateButUpdateEnrichment() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( record );
        Integer agencyId = AssertActionsUtil.getAgencyId( record );

        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE );
        Integer enrichmentAgencyId = AssertActionsUtil.getAgencyId( enrichmentRecord );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( recordId ), eq( agencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( record, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.agenciesForRecord( eq( record ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet( enrichmentAgencyId ) );

        when( rawRepo.recordExists( eq( recordId ), eq( enrichmentAgencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( recordId ), eq( enrichmentAgencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( enrichmentRecord, MarcXChangeMimeType.ENRICHMENT ) );

        Integer newEnrichmentAgencyId = enrichmentAgencyId + 100;

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( eq( recordId ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet( enrichmentAgencyId, newEnrichmentAgencyId ) );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( eq( enrichmentAgencyId.toString() ), eq( LibraryRuleHandler.Rule.USE_ENRICHMENTS ) ) ).thenReturn( true );
        when( openAgencyService.hasFeature( eq( newEnrichmentAgencyId.toString() ), eq( LibraryRuleHandler.Rule.USE_ENRICHMENTS ) ) ).thenReturn( true );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( eq( record ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( eq( record ), eq( record ) ) ).thenReturn( true );
        when( recordsHandler.shouldCreateEnrichmentRecords( eq( settings ), eq( record ), eq( record ) ) ).thenReturn( ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, "reason" ) );

        OverwriteSingleRecordAction instance = new OverwriteSingleRecordAction( rawRepo, record );
        instance.setGroupId( 700000 );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setRecordsHandler( recordsHandler );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 4 ) );

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertRemoveLinksAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertUpdateClassificationsInEnrichmentRecordAction( iterator.next(), rawRepo, record, enrichmentRecord );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, record, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), OverwriteSingleRecordAction.MIMETYPE );
    }

    /**
     * Test performAction(): Update single common record with changes to
     * its current classifications, and creation/update of enrichments.
     * <p>
     *     LibraryRecordsHandler.shouldCreateEnrichmentRecords returns UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR
     *     so new enrichment records should not be created.
     * </p>
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          <ol>
     *              <li>
     *                  A rawrepo with a single common record with classifications
     *              </li>
     *              <li>A DBC enrichment</li>
     *              <li>
     *                  Holdings for a local library, <code>l1</code>, with no enrichment for
     *                  the updated record.
     *              </li>
     *              <li>
     *                  Enrichment for another library, <code>l2</code>.
     *              </li>
     *              <li>
     *                  Local agency does not has the feature 'use_enrichments'
     *              </li>
     *          </ol>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update the common record, with changed classifications.
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
    public void testPerformAction_ChangedClassifications_Holdings_LocalAgencyNoEnrichments() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( record );
        Integer agencyId = AssertActionsUtil.getAgencyId( record );

        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE );
        Integer enrichmentAgencyId = AssertActionsUtil.getAgencyId( enrichmentRecord );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( recordId ), eq( agencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( record, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.agenciesForRecord( eq( record ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet( enrichmentAgencyId ) );

        when( rawRepo.recordExists( eq( recordId ), eq( enrichmentAgencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( recordId ), eq( enrichmentAgencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( enrichmentRecord, MarcXChangeMimeType.ENRICHMENT ) );

        Integer newEnrichmentAgencyId = enrichmentAgencyId + 100;

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( eq( recordId ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet( enrichmentAgencyId, newEnrichmentAgencyId ) );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( eq( enrichmentAgencyId.toString() ), eq( LibraryRuleHandler.Rule.USE_ENRICHMENTS ) ) ).thenReturn( false );
        when( openAgencyService.hasFeature( eq( newEnrichmentAgencyId.toString() ), eq( LibraryRuleHandler.Rule.USE_ENRICHMENTS ) ) ).thenReturn( false );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( eq( record ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( eq( record ), eq( record ) ) ).thenReturn( true );
        when( recordsHandler.shouldCreateEnrichmentRecords( eq( settings ), eq( record ), eq( record ) ) ).thenReturn( ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, "reason" ) );

        OverwriteSingleRecordAction instance = new OverwriteSingleRecordAction( rawRepo, record );
        instance.setGroupId( 700000 );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setRecordsHandler( recordsHandler );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 3 ) );

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertRemoveLinksAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, record, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), OverwriteSingleRecordAction.MIMETYPE );
    }

    /**
     * Test performAction(): Update single common record with no changes to
     * its current classifications and with new 002 to an existing common
     * record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with this content:
     *          <ol>
     *              <li>
     *                  A common record <code>c1</code>. This record is to be updated.
     *              </li>
     *              <li>
     *                  A common record <code>c2</code>. This record is a duplicate of
     *                  <code>c1</code> but with a different faust-id.
     *              </li>
     *              <li>
     *                  An enrichment record <code>e1</code>, that points to <code>c2</code>.
     *              </li>
     *          </ol>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update record <code>c1</code> with no changes in classifications and a new 002 field that
     *          points to <code>c2</code>.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>StoreRecordAction: Store the record</li>
     *              <li>RemoveLinksAction: Remove any existing links to other records</li>
     *              <li>
     *                  MoveEnrichmentRecordAction: Move enrichment record <code>e1</code> from
     *                  <code>c2</code> to <code>c1</code>.
     *              </li>
     *              <li>EnqueueRecordAction: Put the record in queue</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_SameClassifications_MoveEnrichments() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";

        MarcRecord c1 = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId );
        MarcRecord c2 = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c2RecordId );
        MarcRecord e1 = AssertActionsUtil.loadRecord( AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE, c2RecordId );

        MarcRecord record = new MarcRecord( c1 );
        MarcWriter.addOrReplaceSubfield( record, "002", "a", c2RecordId );

        String e1RecordId = AssertActionsUtil.getRecordId( e1 );
        Integer e1AgencyId = AssertActionsUtil.getAgencyId( e1 );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( c1RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( c2RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( e1RecordId ), eq( e1AgencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( c1RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( c1, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.fetchRecord( eq( c2RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( c2, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.fetchRecord( eq( e1RecordId ), eq( e1AgencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( e1, MarcXChangeMimeType.ENRICHMENT ) );
        when( rawRepo.enrichments( eq( new RecordId( c2RecordId, RawRepo.RAWREPO_COMMON_LIBRARY ) ) ) ).thenReturn( AssertActionsUtil.createRecordSet( e1 ) );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( eq( e1RecordId ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet( e1AgencyId ) );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( e1AgencyId.toString(), LibraryRuleHandler.Rule.USE_ENRICHMENTS ) ).thenReturn( true );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( eq( c1 ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationData( eq( record ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( eq( c1 ), eq( record ) ) ).thenReturn( false );

        OverwriteSingleRecordAction instance = new OverwriteSingleRecordAction( rawRepo, record );
        instance.setGroupId( 700000 );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setRecordsHandler( recordsHandler );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertRemoveLinksAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertMoveEnrichmentRecordAction( iterator.next(), rawRepo, e1, record, settings );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, record, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), OverwriteSingleRecordAction.MIMETYPE );

        assertThat( iterator.hasNext(), is( false ) );
    }

    /**
     * Test performAction(): Update single common record with no changes to
     * its current classifications and with new 002 to an existing common
     * record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with this content:
     *          <ol>
     *              <li>
     *                  A common record <code>c1</code>. This record is to be updated.
     *              </li>
     *              <li>
     *                  A common record <code>c2</code>. This record is a duplicate of
     *                  <code>c1</code> but with a different faust-id.
     *              </li>
     *              <li>
     *                  An enrichment record <code>e1</code>, that points to <code>c2</code>.
     *              </li>
     *              <li>
     *                  Local agency does not has the feature 'use_enrichments'
     *              </li>
     *          </ol>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update record <code>c1</code> with no changes in classifications and a new 002 field that
     *          points to <code>c2</code>.
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
    public void testPerformAction_SameClassifications_MoveEnrichments_LocalAgencyNoEnrichments() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";

        MarcRecord c1 = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId );
        MarcRecord c2 = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c2RecordId );
        MarcRecord e1 = AssertActionsUtil.loadRecord( AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE, c2RecordId );

        MarcRecord record = new MarcRecord( c1 );
        MarcWriter.addOrReplaceSubfield( record, "002", "a", c2RecordId );

        String e1RecordId = AssertActionsUtil.getRecordId( e1 );
        Integer e1AgencyId = AssertActionsUtil.getAgencyId( e1 );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( c1RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( c2RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( e1RecordId ), eq( e1AgencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( c1RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( c1, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.fetchRecord( eq( c2RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( c2, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.fetchRecord( eq( e1RecordId ), eq( e1AgencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( e1, MarcXChangeMimeType.ENRICHMENT ) );
        when( rawRepo.enrichments( eq( new RecordId( c2RecordId, RawRepo.RAWREPO_COMMON_LIBRARY ) ) ) ).thenReturn( AssertActionsUtil.createRecordSet( e1 ) );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( eq( e1RecordId ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet( e1AgencyId ) );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( eq( e1AgencyId.toString() ), eq( LibraryRuleHandler.Rule.USE_ENRICHMENTS ) ) ).thenReturn( false );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( eq( c1 ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationData( eq( record ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( eq( c1 ), eq( record ) ) ).thenReturn( false );

        OverwriteSingleRecordAction instance = new OverwriteSingleRecordAction( rawRepo, record );
        instance.setGroupId( 700000 );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setRecordsHandler( recordsHandler );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertRemoveLinksAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, record, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), OverwriteSingleRecordAction.MIMETYPE );

        assertThat( iterator.hasNext(), is( false ) );
    }

    /**
     * Test performAction(): Update single common record with no changes to
     * its current classifications and with existing 002 in the existing common
     * record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with this content:
     *          <ol>
     *              <li>
     *                  A common record <code>c1</code>. This record is to be updated.
     *              </li>
     *              <li>
     *                  A common record <code>c2</code>. This record is a duplicate of
     *                  <code>c1</code> but with a different faust-id.
     *              </li>
     *              <li>
     *                  An enrichment record <code>e1</code>, that points to <code>c2</code>.
     *              </li>
     *          </ol>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update record <code>c1</code> with no changes classifications and with no change to the
     *          002 field that points to <code>c2</code>.
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
    public void testPerformAction_SameClassifications_NoChangeIn002Links() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";

        MarcRecord c1 = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId );
        MarcWriter.addOrReplaceSubfield( c1, "002", "a", c2RecordId );
        MarcRecord c2 = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c2RecordId );
        MarcRecord e1 = AssertActionsUtil.loadRecord( AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE, c2RecordId );

        MarcRecord record = new MarcRecord( c1 );

        String e1RecordId = AssertActionsUtil.getRecordId( e1 );
        Integer e1AgencyId = AssertActionsUtil.getAgencyId( e1 );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( c1RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( c2RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( e1RecordId ), eq( e1AgencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( c1RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( c1, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.fetchRecord( eq( e1RecordId ), eq( e1AgencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( e1, MarcXChangeMimeType.ENRICHMENT ) );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( eq( e1RecordId ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet( e1AgencyId ) );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( e1AgencyId.toString(), LibraryRuleHandler.Rule.USE_ENRICHMENTS ) ).thenReturn( true );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( eq( c1 ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationData( eq( record ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( eq( c1 ), eq( record ) ) ).thenReturn( false );

        OverwriteSingleRecordAction instance = new OverwriteSingleRecordAction( rawRepo, record );
        instance.setGroupId( 700000 );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setRecordsHandler( recordsHandler );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertRemoveLinksAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, record, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), OverwriteSingleRecordAction.MIMETYPE );

        assertThat( iterator.hasNext(), is( false ) );
    }

    /**
     * Test performAction(): Update single common record with changes to
     * its current classifications and with new 002 to an existing common
     * record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with this content:
     *          <ol>
     *              <li>
     *                  A common record <code>c1</code>. This record is to be updated.
     *              </li>
     *              <li>
     *                  A common record <code>c2</code>. This record is a duplicate of
     *                  <code>c1</code> but with a different faust-id.
     *              </li>
     *              <li>
     *                  A library has holdings for <code>c2</code> but no enrichment records.
     *              </li>
     *          </ol>
     *          Both common records are published.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update record <code>c1</code> with no changes to classifications and a new 002 field that
     *          points to <code>c2</code>.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>StoreRecordAction: Store the record</li>
     *              <li>RemoveLinksAction: Remove any existing links to other records</li>
     *              <li>CreateEnrichmentRecordWithClassificationsAction: Create enrichment record for the local agency with holdings.</li>
     *              <li>EnqueueRecordAction: Put the record in queue</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_ChangedClassifications_002Links_HoldingsButNoEnrichments_SourceIsPublished_DestinationIsPublished() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";
        final Integer localAgencyId = 700400;

        MarcRecord c1 = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId );
        MarcRecord c2 = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c2RecordId );

        MarcRecord record = new MarcRecord( c1 );
        MarcWriter.addOrReplaceSubfield( record, "002", "a", c2RecordId );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( c1RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( c2RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( c1RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( c1, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.fetchRecord( eq( c2RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( c2, MarcXChangeMimeType.MARCXCHANGE ) );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( eq( c2RecordId ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet( localAgencyId ) );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( localAgencyId.toString(), LibraryRuleHandler.Rule.USE_ENRICHMENTS ) ).thenReturn( true );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( eq( c1 ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationData( eq( record ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( eq( c1 ), eq( record ) ) ).thenReturn( true );
        when( recordsHandler.shouldCreateEnrichmentRecords( eq( settings ), eq( c2 ), eq( c2 ) ) ).thenReturn( ServiceResult.newOkResult() );
        when( recordsHandler.shouldCreateEnrichmentRecords( eq( settings ), eq( record ), eq( record ) ) ).thenReturn( ServiceResult.newOkResult() );

        OverwriteSingleRecordAction instance = new OverwriteSingleRecordAction( rawRepo, record );
        instance.setGroupId( localAgencyId );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setRecordsHandler( recordsHandler );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertRemoveLinksAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertCreateEnrichmentAction( iterator.next(), rawRepo, c2, localAgencyId, c1RecordId );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, record, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), OverwriteSingleRecordAction.MIMETYPE );

        assertThat( iterator.hasNext(), is( false ) );
    }

    /**
     * Test performAction(): Update single common record with changes to
     * its current classifications and with new 002 to an existing common
     * record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with this content:
     *          <ol>
     *              <li>
     *                  A common record <code>c1</code>. This record is to be updated.
     *              </li>
     *              <li>
     *                  A common record <code>c2</code>. This record is a duplicate of
     *                  <code>c1</code> but with a different faust-id.
     *              </li>
     *              <li>
     *                  A library has holdings for <code>c2</code> but no enrichment records.
     *              </li>
     *          </ol>
     *          Only <code>c1</code> is published.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update record <code>c1</code> with changes to classifications and a new 002 field that
     *          points to <code>c2</code>.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>StoreRecordAction: Store the record</li>
     *              <li>RemoveLinksAction: Remove any existing links to other records</li>
     *              <li>CreateEnrichmentRecordWithClassificationsAction: Create enrichment record for the local agency with holdings.</li>
     *              <li>EnqueueRecordAction: Put the record in queue</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_ChangedClassifications_002Links_HoldingsButNoEnrichments_SourceIsNotPublished_DestinationIsPublished() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";
        final Integer localAgencyId = 700400;

        MarcRecord c1 = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId );
        MarcRecord c2 = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c2RecordId );

        MarcRecord record = new MarcRecord( c1 );
        MarcWriter.addOrReplaceSubfield( record, "002", "a", c2RecordId );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( c1RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( c2RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( c1RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( c1, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.fetchRecord( eq( c2RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( c2, MarcXChangeMimeType.MARCXCHANGE ) );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( eq( c2RecordId ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet( localAgencyId ) );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( localAgencyId.toString(), LibraryRuleHandler.Rule.USE_ENRICHMENTS ) ).thenReturn( true );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( eq( c1 ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationData( eq( record ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( eq( c1 ), eq( record ) ) ).thenReturn( true );
        when( recordsHandler.shouldCreateEnrichmentRecords( eq( settings ), eq( c2 ), eq( c2 ) ) ).thenReturn( ServiceResult.newStatusResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR ) );
        when( recordsHandler.shouldCreateEnrichmentRecords( eq( settings ), eq( record ), eq( record ) ) ).thenReturn( ServiceResult.newOkResult() );

        OverwriteSingleRecordAction instance = new OverwriteSingleRecordAction( rawRepo, record );
        instance.setGroupId( localAgencyId );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setRecordsHandler( recordsHandler );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertRemoveLinksAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertCreateEnrichmentAction( iterator.next(), rawRepo, c2, localAgencyId, c1RecordId );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, record, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), OverwriteSingleRecordAction.MIMETYPE );

        assertThat( iterator.hasNext(), is( false ) );
    }

    /**
     * Test performAction(): Update single common record with changes to
     * its current classifications and with new 002 to an existing common
     * record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with this content:
     *          <ol>
     *              <li>
     *                  A common record <code>c1</code>. This record is to be updated.
     *              </li>
     *              <li>
     *                  A common record <code>c2</code>. This record is a duplicate of
     *                  <code>c1</code> but with a different faust-id.
     *              </li>
     *              <li>
     *                  A library has holdings for <code>c2</code> but no enrichment records.
     *              </li>
     *          </ol>
     *          Only <code>c2</code> is published.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update record <code>c1</code> with changes to classifications and a new 002 field that
     *          points to <code>c2</code>.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>StoreRecordAction: Store the record</li>
     *              <li>RemoveLinksAction: Remove any existing links to other records</li>
     *              <li>CreateEnrichmentRecordWithClassificationsAction: Create enrichment record for the local agency with holdings.</li>
     *              <li>EnqueueRecordAction: Put the record in queue</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_ChangedClassifications_002Links_HoldingsButNoEnrichments_SourceIsPublished_DestinationIsNotPublished() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";
        final Integer localAgencyId = 700400;

        MarcRecord c1 = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId );
        MarcRecord c2 = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c2RecordId );

        MarcRecord record = new MarcRecord( c1 );
        MarcWriter.addOrReplaceSubfield( record, "002", "a", c2RecordId );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( c1RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( c2RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( c1RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( c1, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.fetchRecord( eq( c2RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( c2, MarcXChangeMimeType.MARCXCHANGE ) );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( eq( c2RecordId ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet( localAgencyId ) );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( localAgencyId.toString(), LibraryRuleHandler.Rule.USE_ENRICHMENTS ) ).thenReturn( true );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( eq( c1 ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationData( eq( record ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( eq( c1 ), eq( record ) ) ).thenReturn( true );
        when( recordsHandler.shouldCreateEnrichmentRecords( eq( settings ), eq( c2 ), eq( c2 ) ) ).thenReturn( ServiceResult.newOkResult() );
        when( recordsHandler.shouldCreateEnrichmentRecords( eq( settings ), eq( record ), eq( record ) ) ).thenReturn( ServiceResult.newStatusResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR ) );

        OverwriteSingleRecordAction instance = new OverwriteSingleRecordAction( rawRepo, record );
        instance.setGroupId( localAgencyId );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setRecordsHandler( recordsHandler );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertRemoveLinksAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertCreateEnrichmentAction( iterator.next(), rawRepo, c2, localAgencyId, c1RecordId );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, record, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), OverwriteSingleRecordAction.MIMETYPE );

        assertThat( iterator.hasNext(), is( false ) );
    }

    /**
     * Test performAction(): Update single common record with changes to
     * its current classifications and with new 002 to an existing common
     * record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with this content:
     *          <ol>
     *              <li>
     *                  A common record <code>c1</code>. This record is to be updated.
     *              </li>
     *              <li>
     *                  A common record <code>c2</code>. This record is a duplicate of
     *                  <code>c1</code> but with a different faust-id.
     *              </li>
     *              <li>
     *                  A library has holdings for <code>c2</code> but no enrichment records.
     *              </li>
     *          </ol>
     *          None of the common records are published.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update record <code>c1</code> with changes to classifications and a new 002 field that
     *          points to <code>c2</code>.
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
    public void testPerformAction_ChangedClassifications_002Links_HoldingsButNoEnrichments_SourceIsNotPublished_DestinationIsNotPublished() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";
        final Integer localAgencyId = 700400;

        MarcRecord c1 = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId );
        MarcRecord c2 = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c2RecordId );

        MarcRecord record = new MarcRecord( c1 );
        MarcWriter.addOrReplaceSubfield( record, "002", "a", c2RecordId );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( c1RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( c2RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( c1RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( c1, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.fetchRecord( eq( c2RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( c2, MarcXChangeMimeType.MARCXCHANGE ) );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( eq( c2RecordId ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet( localAgencyId ) );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( localAgencyId.toString(), LibraryRuleHandler.Rule.USE_ENRICHMENTS ) ).thenReturn( true );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( eq( c1 ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationData( eq( record ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( eq( c1 ), eq( record ) ) ).thenReturn( true );
        when( recordsHandler.shouldCreateEnrichmentRecords( eq( settings ), eq( c2 ), eq( c2 ) ) ).thenReturn( ServiceResult.newStatusResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR ) );
        when( recordsHandler.shouldCreateEnrichmentRecords( eq( settings ), eq( record ), eq( record ) ) ).thenReturn( ServiceResult.newStatusResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR ) );

        OverwriteSingleRecordAction instance = new OverwriteSingleRecordAction( rawRepo, record );
        instance.setGroupId( localAgencyId );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setRecordsHandler( recordsHandler );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertRemoveLinksAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, record, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), OverwriteSingleRecordAction.MIMETYPE );

        assertThat( iterator.hasNext(), is( false ) );
    }

    /**
     * Test performAction(): Update single common record with changes to
     * its current classifications and with new 002 to an existing common
     * record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with this content:
     *          <ol>
     *              <li>
     *                  A common record <code>c1</code>. This record is to be updated.
     *              </li>
     *              <li>
     *                  A common record <code>c2</code>. This record is a duplicate of
     *                  <code>c1</code> but with a different faust-id.
     *              </li>
     *              <li>
     *                  A library has holdings for <code>c2</code> but no enrichment records.
     *              </li>
     *              <li>
     *                  Local agency does not has the feature 'use_enrichments'
     *              </li>
     *          </ol>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update record <code>c1</code> with no changes to classifications and a new 002 field that
     *          points to <code>c2</code>.
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
    public void testPerformAction_ChangedClassifications_002Links_HoldingsButNoEnrichments_LocalAgencyNoEnrichments() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";
        final Integer localAgencyId = 700400;

        MarcRecord c1 = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId );
        MarcRecord c2 = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c2RecordId );

        MarcRecord record = new MarcRecord( c1 );
        MarcWriter.addOrReplaceSubfield( record, "002", "a", c2RecordId );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( c1RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( c2RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( c1RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( c1, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.fetchRecord( eq( c2RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( c2, MarcXChangeMimeType.MARCXCHANGE ) );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( eq( c2RecordId ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet( localAgencyId ) );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( eq( localAgencyId.toString() ), eq( LibraryRuleHandler.Rule.USE_ENRICHMENTS ) ) ).thenReturn( false );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( eq( c1 ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationData( eq( record ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( eq( c1 ), eq( record ) ) ).thenReturn( true );

        OverwriteSingleRecordAction instance = new OverwriteSingleRecordAction( rawRepo, record );
        instance.setGroupId( localAgencyId );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setRecordsHandler( recordsHandler );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertRemoveLinksAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, record, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), OverwriteSingleRecordAction.MIMETYPE );

        assertThat( iterator.hasNext(), is( false ) );
    }

    /**
     * Test performAction(): Update single common record with changes to
     * its current classifications and with new 002 to an existing common
     * record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with this content:
     *          <ol>
     *              <li>
     *                  A common record <code>c1</code>. This record is to be updated.
     *              </li>
     *              <li>
     *                  A common record <code>c2</code>. This record is a duplicate of
     *                  <code>c1</code> but with a different faust-id.
     *              </li>
     *              <li>
     *                  An enrichment record to <code>c2</code> exists.
     *              </li>
     *          </ol>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update record <code>c1</code> with changes to classifications and a new 002 field that
     *          points to <code>c2</code>.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>StoreRecordAction: Store the record</li>
     *              <li>RemoveLinksAction: Remove any existing links to other records</li>
     *              <li>MoveEnrichmentRecordAction: Move the enrichment record to <code>c1</code></li>
     *              <li>EnqueueRecordAction: Put the record in queue</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_ChangedClassifications_002Links_MoveEnrichments() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";
        final Integer localAgencyId = 700400;

        MarcRecord c1 = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId );
        MarcRecord c2 = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c2RecordId );
        MarcRecord e1 = AssertActionsUtil.loadRecord( AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE, c2RecordId );

        MarcRecord record = new MarcRecord( c1 );
        MarcWriter.addOrReplaceSubfield( record, "002", "a", c2RecordId );

        String e1RecordId = AssertActionsUtil.getRecordId( e1 );
        Integer e1AgencyId = AssertActionsUtil.getAgencyId( e1 );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( c1RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( c2RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( e1RecordId ), eq( e1AgencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( c1RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( c1, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.fetchRecord( eq( c2RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( c2, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.fetchRecord( eq( e1RecordId ), eq( e1AgencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( e1, MarcXChangeMimeType.ENRICHMENT ) );
        when( rawRepo.enrichments( eq( new RecordId( c2RecordId, RawRepo.RAWREPO_COMMON_LIBRARY ) ) ) ).thenReturn( AssertActionsUtil.createRecordSet( e1 ) );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( eq( e1RecordId ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet( e1AgencyId ) );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( eq( e1AgencyId.toString() ), eq( LibraryRuleHandler.Rule.USE_ENRICHMENTS ) ) ).thenReturn( true );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( eq( c1 ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationData( eq( record ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( eq( c1 ), eq( record ) ) ).thenReturn( true );

        OverwriteSingleRecordAction instance = new OverwriteSingleRecordAction( rawRepo, record );
        instance.setGroupId( localAgencyId );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setRecordsHandler( recordsHandler );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertRemoveLinksAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertMoveEnrichmentRecordAction( iterator.next(), rawRepo, e1, record, settings );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, record, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), OverwriteSingleRecordAction.MIMETYPE );

        assertThat( iterator.hasNext(), is( false ) );
    }

    /**
     * Test performAction(): Update single common record with changes to
     * its current classifications and with new 002 to an existing common
     * record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with this content:
     *          <ol>
     *              <li>
     *                  A common record <code>c1</code>. This record is to be updated.
     *              </li>
     *              <li>
     *                  A common record <code>c2</code>. This record is a duplicate of
     *                  <code>c1</code> but with a different faust-id.
     *              </li>
     *              <li>
     *                  An enrichment record to <code>c2</code> exists.
     *              </li>
     *              <li>
     *                  Local agency does not has the feature 'use_enrichments'
     *              </li>
     *          </ol>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update record <code>c1</code> with changes to classifications and a new 002 field that
     *          points to <code>c2</code>.
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
    public void testPerformAction_ChangedClassifications_002Links_LocalAgencyEnrichments() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";
        final Integer localAgencyId = 700400;

        MarcRecord c1 = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId );
        MarcRecord c2 = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c2RecordId );
        MarcRecord e1 = AssertActionsUtil.loadRecord( AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE, c2RecordId );

        MarcRecord record = new MarcRecord( c1 );
        MarcWriter.addOrReplaceSubfield( record, "002", "a", c2RecordId );

        String e1RecordId = AssertActionsUtil.getRecordId( e1 );
        Integer e1AgencyId = AssertActionsUtil.getAgencyId( e1 );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( c1RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( c2RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( e1RecordId ), eq( e1AgencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( c1RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( c1, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.fetchRecord( eq( c2RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( c2, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.fetchRecord( eq( e1RecordId ), eq( e1AgencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( e1, MarcXChangeMimeType.ENRICHMENT ) );
        when( rawRepo.enrichments( eq( new RecordId( c2RecordId, RawRepo.RAWREPO_COMMON_LIBRARY ) ) ) ).thenReturn( AssertActionsUtil.createRecordSet( e1 ) );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( eq( e1RecordId ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet( e1AgencyId ) );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( eq( e1AgencyId.toString() ), eq( LibraryRuleHandler.Rule.USE_ENRICHMENTS ) ) ).thenReturn( false );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( eq( c1 ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationData( eq( record ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( eq( c1 ), eq( record ) ) ).thenReturn( true );

        OverwriteSingleRecordAction instance = new OverwriteSingleRecordAction( rawRepo, record );
        instance.setGroupId( localAgencyId );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setRecordsHandler( recordsHandler );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertRemoveLinksAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, record, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), OverwriteSingleRecordAction.MIMETYPE );

        assertThat( iterator.hasNext(), is( false ) );
    }

    /**
     * Test performAction(): Update single common record with no changes to
     * its current classifications and with new 002 to a common record that
     * does not exist.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with this content:
     *          <ol>
     *              <li>
     *                  A common record <code>c1</code>. This record is to be updated.
     *              </li>
     *          </ol>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update record <code>c1</code> with no changes to classifications and a new 002 field that
     *          points to a record that does not exist.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Return status: FAILED_UPDATE_INTERNAL_ERROR
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_002Link_DoNotExist() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";

        MarcRecord c1 = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId );

        MarcRecord record = new MarcRecord( c1 );
        MarcWriter.addOrReplaceSubfield( record, "002", "a", c2RecordId );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( c1RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( c2RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( false );
        when( rawRepo.fetchRecord( eq( c1RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( c1, MarcXChangeMimeType.MARCXCHANGE ) );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( eq( c1 ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationData( eq( record ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( eq( c1 ), eq( record ) ) ).thenReturn( false );

        OverwriteSingleRecordAction instance = new OverwriteSingleRecordAction( rawRepo, record );
        instance.setGroupId( 700000 );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setRecordsHandler( recordsHandler );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertRemoveLinksAction( iterator.next(), rawRepo, record );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, record, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), OverwriteSingleRecordAction.MIMETYPE );

        assertThat( iterator.hasNext(), is( false ) );
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private ResourceBundle messages;
}
