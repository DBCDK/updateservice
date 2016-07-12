package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.HoldingsItems;
import dk.dbc.updateservice.update.LibraryRecordsHandler;
import dk.dbc.updateservice.update.OpenAgencyService;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.SolrService;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import dk.dbc.updateservice.ws.JNDIResources;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Properties;
import java.util.ResourceBundle;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UpdateSingleRecordTest {
    private ResourceBundle messages;

    public UpdateSingleRecordTest() {
        this.messages = ResourceBundles.getBundle(this, "actions");
    }

    /**
     * Test performAction(): Create new single common record.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * An empty rawrepo.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update a common record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>CreateSingleRecordAction: Creates the new record</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_CreateRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        Integer agencyId = AssertActionsUtil.getAgencyId(record);

        RawRepo rawRepo = mock(RawRepo.class);
        when(rawRepo.recordExists(eq(recordId), eq(agencyId))).thenReturn(false);

        HoldingsItems holdingsItems = mock(HoldingsItems.class);
        OpenAgencyService openAgencyService = mock(OpenAgencyService.class);
        SolrService solrService = mock(SolrService.class);
        LibraryRecordsHandler recordsHandler = mock(LibraryRecordsHandler.class);

        UpdateSingleRecord instance = new UpdateSingleRecord(rawRepo, record);

        Properties settings = new Properties();
        settings.put(JNDIResources.RAWREPO_PROVIDER_ID, "xxx");
        instance.setGroupId(700000);
        instance.setHoldingsItems(holdingsItems);
        instance.setOpenAgencyService(openAgencyService);
        instance.setSolrService(solrService);
        instance.setRecordsHandler(recordsHandler);
        instance.setSettings(settings);

        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = instance.children().listIterator();

        AssertActionsUtil.assertCreateSingleRecordAction(iterator.next(), rawRepo, record, solrService, settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID));
        assertThat(iterator.hasNext(), is(false));
    }

    /**
     * Test performAction(): Update an existing single common record with no
     * classifications.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with a single common record and a DBC enrichment
     * record.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update the common record, but without any classification data.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>OverwriteSingleRecordAction: Action to overwrite the record.</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_OverwriteRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        Integer agencyId = AssertActionsUtil.getAgencyId(record);

        RawRepo rawRepo = mock(RawRepo.class);
        when(rawRepo.recordExists(eq(recordId), eq(agencyId))).thenReturn(true);

        HoldingsItems holdingsItems = mock(HoldingsItems.class);
        when(holdingsItems.getAgenciesThatHasHoldingsFor(record)).thenReturn(AssertActionsUtil.createAgenciesSet());

        OpenAgencyService openAgencyService = mock(OpenAgencyService.class);

        LibraryRecordsHandler recordsHandler = mock(LibraryRecordsHandler.class);
        when(recordsHandler.hasClassificationData(record)).thenReturn(false);

        UpdateSingleRecord instance = new UpdateSingleRecord(rawRepo, record);
        instance.setGroupId(700000);
        instance.setHoldingsItems(holdingsItems);
        instance.setOpenAgencyService(openAgencyService);
        instance.setSolrService(mock(SolrService.class));
        instance.setRecordsHandler(recordsHandler);

        Properties settings = new Properties();
        settings.put(JNDIResources.RAWREPO_PROVIDER_ID, "xxx");
        instance.setSettings(settings);

        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = instance.children();
        Assert.assertThat(children.size(), is(1));

        OverwriteSingleRecordAction action = (OverwriteSingleRecordAction) children.get(0);
        assertThat(action, notNullValue());
        assertThat(action.getRawRepo(), is(rawRepo));
        assertThat(action.getRecord(), is(record));
        assertThat(action.getGroupId(), equalTo(700000));
        assertThat(action.getHoldingsItems(), is(holdingsItems));
        assertThat(action.getOpenAgencyService(), is(openAgencyService));
        assertThat(action.getRecordsHandler(), is(recordsHandler));
        assertThat(action.getSettings(), equalTo(settings));
    }

    /**
     * Test performAction(): Delete single common record with no enrichments and no children.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with a common record and no enrichments or children.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update a common record that is marked for deletion.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>DeleteCommonRecordAction: Action to delete a common record</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_DeleteRecord_NoHoldings() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecordAndMarkForDeletion(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        Integer agencyId = AssertActionsUtil.getAgencyId(record);

        RawRepo rawRepo = mock(RawRepo.class);
        when(rawRepo.recordExists(eq(recordId), eq(agencyId))).thenReturn(true);

        LibraryRecordsHandler recordsHandler = mock(LibraryRecordsHandler.class);

        HoldingsItems holdingsItems = mock(HoldingsItems.class);
        when(holdingsItems.getAgenciesThatHasHoldingsFor(record)).thenReturn(new HashSet<Integer>());

        OpenAgencyService openAgencyService = mock(OpenAgencyService.class);
        when(openAgencyService.hasFeature("700000", LibraryRuleHandler.Rule.AUTH_EXPORT_HOLDINGS)).thenReturn(true);
        SolrService solrService = mock(SolrService.class);

        UpdateSingleRecord instance = new UpdateSingleRecord(rawRepo, record);
        instance.setGroupId(700000);
        instance.setHoldingsItems(holdingsItems);
        instance.setOpenAgencyService(openAgencyService);
        instance.setSolrService(solrService);
        instance.setRecordsHandler(recordsHandler);

        Properties settings = new Properties();
        settings.put(JNDIResources.RAWREPO_PROVIDER_ID, "xxx");
        instance.setSettings(settings);

        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertCommonDeleteRecordAction(iterator.next(), rawRepo, record, recordsHandler, holdingsItems, settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID));

        assertThat(iterator.hasNext(), is(false));
    }

    /**
     * Test performAction(): Delete single common record with with holdings and no auth to export holdings.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with a common record and no enrichments or children. The agency does not has auth
     * to export holdings.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update a common record that is marked for deletion.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>DeleteCommonRecordAction: Action to delete a common record</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_DeleteRecord_WithHoldings_NoAuthExportHoldings() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecordAndMarkForDeletion(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        Integer agencyId = AssertActionsUtil.getAgencyId(record);

        RawRepo rawRepo = mock(RawRepo.class);
        when(rawRepo.recordExists(eq(recordId), eq(agencyId))).thenReturn(true);

        LibraryRecordsHandler recordsHandler = mock(LibraryRecordsHandler.class);

        HoldingsItems holdingsItems = mock(HoldingsItems.class);
        when(holdingsItems.getAgenciesThatHasHoldingsFor(record)).thenReturn(AssertActionsUtil.createAgenciesSet(710100));

        OpenAgencyService openAgencyService = mock(OpenAgencyService.class);
        when(openAgencyService.hasFeature("700000", LibraryRuleHandler.Rule.AUTH_EXPORT_HOLDINGS)).thenReturn(false);

        SolrService solrService = mock(SolrService.class);
        when(solrService.hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(false);

        UpdateSingleRecord instance = new UpdateSingleRecord(rawRepo, record);
        instance.setGroupId(700000);
        instance.setHoldingsItems(holdingsItems);
        instance.setOpenAgencyService(openAgencyService);
        instance.setSolrService(solrService);
        instance.setRecordsHandler(recordsHandler);

        Properties settings = new Properties();
        settings.put(JNDIResources.RAWREPO_PROVIDER_ID, "xxx");
        instance.setSettings(settings);

        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertCommonDeleteRecordAction(iterator.next(), rawRepo, record, recordsHandler, holdingsItems, settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID));

        assertThat(iterator.hasNext(), is(false));
    }

    /**
     * Test performAction(): Delete single common record with with holdings.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with a common record and no enrichments or children. No other records
     * have 002 links to the common record.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update a common record that is marked for deletion.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return an update error that it is not allowed to delete records with holdings.
     * <p>
     * Return status: FAILED_UPDATE_INTERNAL_ERROR
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_DeleteRecord_WithHoldings_No002Links() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecordAndMarkForDeletion(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        Integer agencyId = AssertActionsUtil.getAgencyId(record);

        RawRepo rawRepo = mock(RawRepo.class);
        when(rawRepo.recordExists(eq(recordId), eq(agencyId))).thenReturn(true);

        LibraryRecordsHandler recordsHandler = mock(LibraryRecordsHandler.class);

        HoldingsItems holdingsItems = mock(HoldingsItems.class);
        when(holdingsItems.getAgenciesThatHasHoldingsFor(record)).thenReturn(AssertActionsUtil.createAgenciesSet(710100));

        OpenAgencyService openAgencyService = mock(OpenAgencyService.class);
        when(openAgencyService.hasFeature("700000", LibraryRuleHandler.Rule.AUTH_EXPORT_HOLDINGS)).thenReturn(true);

        SolrService solrService = mock(SolrService.class);
        when(solrService.hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(false);

        UpdateSingleRecord instance = new UpdateSingleRecord(rawRepo, record);
        instance.setGroupId(700000);
        instance.setHoldingsItems(holdingsItems);
        instance.setOpenAgencyService(openAgencyService);
        instance.setSolrService(solrService);
        instance.setRecordsHandler(recordsHandler);

        Properties settings = new Properties();
        settings.put(JNDIResources.RAWREPO_PROVIDER_ID, "xxx");
        instance.setSettings(settings);

        String message = messages.getString("delete.common.with.holdings.error");
        assertThat(instance.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message)));
        assertTrue(instance.children().isEmpty());
    }

    /**
     * Test performAction(): Delete single common record with holdings and 002 links
     * from another record.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with a common record and no enrichments or children and with holdings.
     * Solr contains documents for other records with 002 links to the common record.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update a common record that is marked for deletion.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>DeleteCommonRecordAction: Action to delete a common record</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_DeleteRecord_WithHoldings_With002Links() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecordAndMarkForDeletion(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        Integer agencyId = AssertActionsUtil.getAgencyId(record);

        RawRepo rawRepo = mock(RawRepo.class);
        when(rawRepo.recordExists(eq(recordId), eq(agencyId))).thenReturn(true);

        LibraryRecordsHandler recordsHandler = mock(LibraryRecordsHandler.class);

        HoldingsItems holdingsItems = mock(HoldingsItems.class);
        when(holdingsItems.getAgenciesThatHasHoldingsFor(record)).thenReturn(AssertActionsUtil.createAgenciesSet(710100));

        OpenAgencyService openAgencyService = mock(OpenAgencyService.class);
        when(openAgencyService.hasFeature("700000", LibraryRuleHandler.Rule.AUTH_EXPORT_HOLDINGS)).thenReturn(true);

        SolrService solrService = mock(SolrService.class);
        when(solrService.hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(true);

        UpdateSingleRecord instance = new UpdateSingleRecord(rawRepo, record);
        instance.setGroupId(700000);
        instance.setHoldingsItems(holdingsItems);
        instance.setOpenAgencyService(openAgencyService);
        instance.setSolrService(solrService);
        instance.setRecordsHandler(recordsHandler);

        Properties settings = new Properties();
        settings.put(JNDIResources.RAWREPO_PROVIDER_ID, "xxx");
        instance.setSettings(settings);

        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertCommonDeleteRecordAction(iterator.next(), rawRepo, record, recordsHandler, holdingsItems, settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID));

        assertThat(iterator.hasNext(), is(false));
    }
}
