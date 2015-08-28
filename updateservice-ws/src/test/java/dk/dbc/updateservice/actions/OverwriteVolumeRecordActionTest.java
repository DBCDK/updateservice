//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcWriter;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.updateservice.update.HoldingsItems;
import dk.dbc.updateservice.update.LibraryRecordsHandler;
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
public class OverwriteVolumeRecordActionTest {
    public OverwriteVolumeRecordActionTest() {
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
        MarcRecord mainRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE );
        String mainRecordId = AssertActionsUtil.getRecordId( mainRecord );
        Integer agencyId = AssertActionsUtil.getAgencyId( mainRecord );

        MarcRecord volumeRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE );
        String volumeRecordId = AssertActionsUtil.getRecordId( volumeRecord );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( mainRecordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( volumeRecordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( volumeRecordId ), eq( agencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( volumeRecord, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.agenciesForRecord( eq( volumeRecord ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( mainRecord ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( volumeRecord ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( volumeRecord ) ).thenReturn( false );

        OverwriteVolumeRecordAction instance = new OverwriteVolumeRecordAction( rawRepo, volumeRecord );
        instance.setHoldingsItems( holdingsItems );
        instance.setRecordsHandler( recordsHandler );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 4 ) );

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction( iterator.next(), rawRepo, volumeRecord );
        AssertActionsUtil.assertRemoveLinksAction( iterator.next(), rawRepo, volumeRecord );
        AssertActionsUtil.assertLinkRecordAction( iterator.next(), rawRepo, volumeRecord, mainRecord );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, volumeRecord, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), OverwriteVolumeRecordAction.MIMETYPE );
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
        MarcRecord mainRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE );
        String mainRecordId = AssertActionsUtil.getRecordId( mainRecord );
        Integer agencyId = AssertActionsUtil.getAgencyId( mainRecord );

        MarcRecord volumeRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE );
        String volumeRecordId = AssertActionsUtil.getRecordId( volumeRecord );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( mainRecordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( volumeRecordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( volumeRecordId ), eq( agencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( volumeRecord, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.agenciesForRecord( eq( volumeRecord ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( mainRecord ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( volumeRecord ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( eq( volumeRecord ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( eq( volumeRecord ), eq( volumeRecord ) ) ).thenReturn( false );

        OverwriteVolumeRecordAction instance = new OverwriteVolumeRecordAction( rawRepo, volumeRecord );
        instance.setHoldingsItems( holdingsItems );
        instance.setRecordsHandler( recordsHandler );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 4 ) );

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction( iterator.next(), rawRepo, volumeRecord );
        AssertActionsUtil.assertRemoveLinksAction( iterator.next(), rawRepo, volumeRecord );
        AssertActionsUtil.assertLinkRecordAction( iterator.next(), rawRepo, volumeRecord, mainRecord );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, volumeRecord, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), instance.MIMETYPE );
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
        MarcRecord mainRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE );
        String mainRecordId = AssertActionsUtil.getRecordId( mainRecord );
        Integer agencyId = AssertActionsUtil.getAgencyId( mainRecord );

        MarcRecord volumeRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE );
        String volumeRecordId = AssertActionsUtil.getRecordId( volumeRecord );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( mainRecordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( volumeRecordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( volumeRecordId ), eq( agencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( volumeRecord, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.agenciesForRecord( eq( volumeRecord ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( mainRecord ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( volumeRecord ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( eq( volumeRecord ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( eq( volumeRecord ), eq( volumeRecord ) ) ).thenReturn( true );

        OverwriteVolumeRecordAction instance = new OverwriteVolumeRecordAction( rawRepo, volumeRecord );
        instance.setGroupId( 700000 );
        instance.setHoldingsItems( holdingsItems );
        instance.setRecordsHandler( recordsHandler );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 4 ) );

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction( iterator.next(), rawRepo, volumeRecord );
        AssertActionsUtil.assertRemoveLinksAction( iterator.next(), rawRepo, volumeRecord );
        AssertActionsUtil.assertLinkRecordAction( iterator.next(), rawRepo, volumeRecord, mainRecord );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, volumeRecord, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), instance.MIMETYPE );
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
        MarcRecord mainRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE );
        String mainRecordId = AssertActionsUtil.getRecordId( mainRecord );
        Integer agencyId = AssertActionsUtil.getAgencyId( mainRecord );

        MarcRecord volumeRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE );
        String volumeRecordId = AssertActionsUtil.getRecordId( volumeRecord );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( mainRecordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( volumeRecordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( volumeRecordId ), eq( agencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( volumeRecord, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.agenciesForRecord( eq( volumeRecord ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( mainRecordId ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( volumeRecordId ) ).thenReturn( AssertActionsUtil.createAgenciesSet( 700100 ) );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( eq( volumeRecord ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( eq( volumeRecord ), eq( volumeRecord ) ) ).thenReturn( true );
        when( recordsHandler.shouldCreateEnrichmentRecords( eq( settings ), eq( volumeRecord ), eq( volumeRecord ) ) ).thenReturn( ServiceResult.newOkResult() );

        OverwriteVolumeRecordAction instance = new OverwriteVolumeRecordAction( rawRepo, volumeRecord );
        instance.setGroupId( 700000 );
        instance.setHoldingsItems( holdingsItems );
        instance.setRecordsHandler( recordsHandler );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 5 ) );

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction( iterator.next(), rawRepo, volumeRecord );
        AssertActionsUtil.assertRemoveLinksAction( iterator.next(), rawRepo, volumeRecord );
        AssertActionsUtil.assertLinkRecordAction( iterator.next(), rawRepo, volumeRecord, mainRecord );
        AssertActionsUtil.assertCreateEnrichmentAction( iterator.next(), rawRepo, volumeRecord, 700100 );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, volumeRecord, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), instance.MIMETYPE );
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
        MarcRecord mainRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE );
        String mainRecordId = AssertActionsUtil.getRecordId( mainRecord );
        Integer agencyId = AssertActionsUtil.getAgencyId( mainRecord );

        MarcRecord volumeRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE );
        String volumeRecordId = AssertActionsUtil.getRecordId( volumeRecord );

        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE );
        MarcWriter.addOrReplaceSubfield( enrichmentRecord, "001", "a", volumeRecordId );
        Integer enrichmentAgencyId = AssertActionsUtil.getAgencyId( enrichmentRecord );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( mainRecordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( volumeRecordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( volumeRecordId ), eq( agencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( volumeRecord, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.agenciesForRecord( eq( volumeRecord ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        when( rawRepo.recordExists( eq( volumeRecordId ), eq( enrichmentAgencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( volumeRecordId ), eq( enrichmentAgencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( enrichmentRecord, MarcXChangeMimeType.ENRICHMENT ) );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( mainRecordId ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( volumeRecordId ) ).thenReturn( AssertActionsUtil.createAgenciesSet( enrichmentAgencyId ) );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( eq( volumeRecord ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( eq( volumeRecord ), eq( volumeRecord ) ) ).thenReturn( true );

        OverwriteVolumeRecordAction instance = new OverwriteVolumeRecordAction( rawRepo, volumeRecord );
        instance.setGroupId( 700000 );
        instance.setHoldingsItems( holdingsItems );
        instance.setRecordsHandler( recordsHandler );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 5 ) );

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction( iterator.next(), rawRepo, volumeRecord );
        AssertActionsUtil.assertRemoveLinksAction( iterator.next(), rawRepo, volumeRecord );
        AssertActionsUtil.assertLinkRecordAction( iterator.next(), rawRepo, volumeRecord, mainRecord );
        AssertActionsUtil.assertUpdateClassificationsInEnrichmentRecordAction( iterator.next(), rawRepo, volumeRecord, enrichmentRecord );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, volumeRecord, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), instance.MIMETYPE );
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
        MarcRecord mainRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE );
        String mainRecordId = AssertActionsUtil.getRecordId( mainRecord );
        Integer agencyId = AssertActionsUtil.getAgencyId( mainRecord );

        MarcRecord volumeRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE );
        String volumeRecordId = AssertActionsUtil.getRecordId( volumeRecord );

        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE );
        MarcWriter.addOrReplaceSubfield( enrichmentRecord, "001", "a", volumeRecordId );
        Integer enrichmentAgencyId = AssertActionsUtil.getAgencyId( enrichmentRecord );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( mainRecordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( volumeRecordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( volumeRecordId ), eq( agencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( volumeRecord, MarcXChangeMimeType.MARCXCHANGE ) );
        when( rawRepo.agenciesForRecord( eq( volumeRecord ) ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        when( rawRepo.recordExists( eq( volumeRecordId ), eq( enrichmentAgencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( volumeRecordId ), eq( enrichmentAgencyId ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( enrichmentRecord, MarcXChangeMimeType.ENRICHMENT ) );

        Integer newEnrichmentAgencyId = enrichmentAgencyId + 100;

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( mainRecordId ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( volumeRecordId ) ).thenReturn( AssertActionsUtil.createAgenciesSet( enrichmentAgencyId, newEnrichmentAgencyId ) );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( eq( volumeRecord ) ) ).thenReturn( true );
        when( recordsHandler.hasClassificationsChanged( eq( volumeRecord ), eq( volumeRecord ) ) ).thenReturn( true );
        when( recordsHandler.shouldCreateEnrichmentRecords( eq( settings ), eq( volumeRecord ), eq( volumeRecord ) ) ).thenReturn( ServiceResult.newOkResult() );

        OverwriteVolumeRecordAction instance = new OverwriteVolumeRecordAction( rawRepo, volumeRecord );
        instance.setGroupId( 700000 );
        instance.setHoldingsItems( holdingsItems );
        instance.setRecordsHandler( recordsHandler );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 6 ) );

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction( iterator.next(), rawRepo, volumeRecord );
        AssertActionsUtil.assertRemoveLinksAction( iterator.next(), rawRepo, volumeRecord );
        AssertActionsUtil.assertLinkRecordAction( iterator.next(), rawRepo, volumeRecord, mainRecord );
        AssertActionsUtil.assertUpdateClassificationsInEnrichmentRecordAction( iterator.next(), rawRepo, volumeRecord, enrichmentRecord );
        AssertActionsUtil.assertCreateEnrichmentAction( iterator.next(), rawRepo, volumeRecord, newEnrichmentAgencyId );
        AssertActionsUtil.assertEnqueueRecordAction( iterator.next(), rawRepo, volumeRecord, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), instance.MIMETYPE );
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private ResourceBundle messages;
}