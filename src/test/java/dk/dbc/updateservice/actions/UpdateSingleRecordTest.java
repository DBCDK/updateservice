/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.OpenAgencyService;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import dk.dbc.updateservice.ws.JNDIResources;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

public class UpdateSingleRecordTest {
    private GlobalActionState state;
    private Properties settings;
    private static final String GROUP_ID = "700000";
    OpenAgencyService.LibraryGroup libraryGroup = OpenAgencyService.LibraryGroup.FBS;

    @Before
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId(GROUP_ID);
        state.setLibraryGroup(libraryGroup);
        settings = new UpdateTestUtils().getSettings();
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
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(false);

        UpdateSingleRecord updateSingleRecord = new UpdateSingleRecord(state, settings, record);
        assertThat(updateSingleRecord.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = updateSingleRecord.children().listIterator();
        AssertActionsUtil.assertCreateSingleRecordAction(iterator.next(), state.getRawRepo(), record, state.getSolrFBS(), settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_FBS));
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
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(recordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getLibraryRecordsHandler().hasClassificationData(record)).thenReturn(false);

        UpdateSingleRecord updateSingleRecord = new UpdateSingleRecord(state, settings, record);
        assertThat(updateSingleRecord.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateSingleRecord.children();
        Assert.assertThat(children.size(), is(1));

        OverwriteSingleRecordAction overwriteSingleRecordAction = (OverwriteSingleRecordAction) children.get(0);
        assertThat(overwriteSingleRecordAction, notNullValue());
        assertThat(overwriteSingleRecordAction.getRawRepo(), is(state.getRawRepo()));
        assertThat(overwriteSingleRecordAction.record, is(record));
        assertThat(overwriteSingleRecordAction.state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), equalTo(GROUP_ID));
        assertThat(overwriteSingleRecordAction.state.getHoldingsItems(), is(state.getHoldingsItems()));
        assertThat(overwriteSingleRecordAction.state.getOpenAgencyService(), is(state.getOpenAgencyService()));
        assertThat(overwriteSingleRecordAction.state.getLibraryRecordsHandler(), is(state.getLibraryRecordsHandler()));
        assertThat(overwriteSingleRecordAction.settings, equalTo(settings));
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
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(recordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record)).thenReturn(new HashSet<>());
        when(state.getOpenAgencyService().hasFeature(GROUP_ID, LibraryRuleHandler.Rule.AUTH_EXPORT_HOLDINGS)).thenReturn(true);
        when(state.getSolrFBS().getOwnerOf002(SolrServiceIndexer.createGetOwnerOf002QueryDBCOnly("002a", recordId))).thenReturn("");

        UpdateSingleRecord updateSingleRecord = new UpdateSingleRecord(state, settings, record);
        assertThat(updateSingleRecord.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = updateSingleRecord.children().listIterator();
        AssertActionsUtil.assertCommonDeleteRecordAction(iterator.next(), state.getRawRepo(), record, state.getLibraryRecordsHandler(), state.getHoldingsItems(), settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_FBS));
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
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(recordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record)).thenReturn(AssertActionsUtil.createAgenciesSet(710100));
        when(state.getOpenAgencyService().hasFeature(GROUP_ID, LibraryRuleHandler.Rule.AUTH_EXPORT_HOLDINGS)).thenReturn(false);
        when(state.getSolrFBS().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(false);
        when(state.getSolrFBS().getOwnerOf002(SolrServiceIndexer.createGetOwnerOf002QueryDBCOnly("002a", recordId))).thenReturn("");

        UpdateSingleRecord updateSingleRecord = new UpdateSingleRecord(state, settings, record);
        assertThat(updateSingleRecord.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = updateSingleRecord.children().listIterator();
        AssertActionsUtil.assertCommonDeleteRecordAction(iterator.next(), state.getRawRepo(), record, state.getLibraryRecordsHandler(), state.getHoldingsItems(), settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_FBS));
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
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(recordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record)).thenReturn(AssertActionsUtil.createAgenciesSet(710100));
        when(state.getOpenAgencyService().hasFeature("710100", LibraryRuleHandler.Rule.AUTH_EXPORT_HOLDINGS)).thenReturn(true);
        when(state.getSolrFBS().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(false);

        UpdateSingleRecord updateSingleRecord = new UpdateSingleRecord(state, settings, record);
        String message = String.format(state.getMessages().getString("delete.common.with.holdings.error"), recordId, agencyId, "710100");
        assertThat(updateSingleRecord.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state)));
        assertTrue(updateSingleRecord.children().isEmpty());
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
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(recordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record)).thenReturn(AssertActionsUtil.createAgenciesSet(710100));
        when(state.getOpenAgencyService().hasFeature(GROUP_ID, LibraryRuleHandler.Rule.AUTH_EXPORT_HOLDINGS)).thenReturn(true);
        when(state.getSolrFBS().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(true);
        when(state.getSolrFBS().getOwnerOf002(SolrServiceIndexer.createGetOwnerOf002QueryDBCOnly("002a", recordId))).thenReturn("");

        UpdateSingleRecord updateSingleRecord = new UpdateSingleRecord(state, settings, record);
        assertThat(updateSingleRecord.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = updateSingleRecord.children().listIterator();
        AssertActionsUtil.assertCommonDeleteRecordAction(iterator.next(), state.getRawRepo(), record, state.getLibraryRecordsHandler(), state.getHoldingsItems(), settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_FBS));
        assertThat(iterator.hasNext(), is(false));
    }

}
