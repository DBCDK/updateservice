/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.Record;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.OpenAgencyService;
import dk.dbc.updateservice.update.RawRepoRecordMock;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class CreateSingleRecordActionTest {
    private GlobalActionState state;
    private Properties settings;
    OpenAgencyService.LibraryGroup libraryGroup = OpenAgencyService.LibraryGroup.FBS;

    @Before
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
        state.setLibraryGroup(libraryGroup);
        settings = new UpdateTestUtils().getSettings();
    }

    /**
     * Test performAction(): Create new single common record with no local records for the
     * same faust-id.
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
     * <li>StoreRecordAction: Store the record</li>
     * <li>EnqueueRecordAction: Put the record in queue</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_NoLocals_No002Links() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SCHOOL_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);

        when(state.getRawRepo().agenciesForRecord(eq(record))).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getSolrFBS().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(false);

        CreateSingleRecordAction createSingleRecordAction = new CreateSingleRecordAction(state, settings, record);
        assertThat(createSingleRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = createSingleRecordAction.children();
        Assert.assertThat(children.size(), is(3));
        AssertActionsUtil.assertStoreRecordAction(children.get(0), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(children.get(1), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(children.get(2), state.getRawRepo(), record);
    }

    /**
     * Test performAction(): Create new single common record with no local records for the
     * same faust-id, but with 002 links from other records.
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
     * Return status: FAILED_UPDATE_INTERNAL_ERROR
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_NoLocals_With002Links() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);

        when(state.getRawRepo().agenciesForRecord(eq(record))).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getSolrFBS().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(true);

        CreateSingleRecordAction createSingleRecordAction = new CreateSingleRecordAction(state, settings, record);
        String message = state.getMessages().getString("update.record.with.002.links");
        assertThat(createSingleRecordAction.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state)));
        assertThat(createSingleRecordAction.children().isEmpty(), is(true));
    }

    /**
     * Test performAction(): Create new single common record with a local record.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with a local record with faust-id <code>faust</code>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update a common record with faust-id <code>faust</code>
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return status: FAILED_UPDATE_INTERNAL_ERROR
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_WithLocal_NotFFU() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        Set<String> ffuLibraries = new HashSet<>();

        Record rr1 = new RawRepoRecordMock(recordId, 700300);
        rr1.setMimeType(MarcXChangeMimeType.MARCXCHANGE);
        Record rr2 = new RawRepoRecordMock(recordId, 123456);
        rr2.setMimeType(MarcXChangeMimeType.ENRICHMENT);

        when(state.getRawRepo().agenciesForRecordAll(eq(record))).thenReturn(AssertActionsUtil.createAgenciesSet(700300, 123456));
        when(state.getSolrFBS().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(false);
        when(state.getOpenAgencyService().getFFULibraries()).thenReturn(ffuLibraries);
        when(state.getRawRepo().fetchRecord(recordId, 700300)).thenReturn(rr1);
        when(state.getRawRepo().fetchRecord(recordId, 123456)).thenReturn(rr2);

        CreateSingleRecordAction createSingleRecordAction = new CreateSingleRecordAction(state, settings, record);
        String message = state.getMessages().getString("create.record.with.locals");
        assertThat(createSingleRecordAction.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state)));
        assertThat(createSingleRecordAction.children().isEmpty(), is(true));
    }

    @Test
    public void testPerformAction_WithLocal_WithFFU() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        Set<String> ffuLibraries = new HashSet<>();
        ffuLibraries.add("700300");

        Record rr1 = new RawRepoRecordMock(recordId, 700300);
        rr1.setMimeType(MarcXChangeMimeType.MARCXCHANGE);

        when(state.getRawRepo().agenciesForRecordAll(eq(record))).thenReturn(AssertActionsUtil.createAgenciesSet(700300));
        when(state.getSolrFBS().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(false);
        when(state.getOpenAgencyService().getFFULibraries()).thenReturn(ffuLibraries);
        when(state.getRawRepo().fetchRecord(recordId, 700300)).thenReturn(rr1);

        CreateSingleRecordAction createSingleRecordAction = new CreateSingleRecordAction(state, settings, record);
        assertThat(createSingleRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));
        List<ServiceAction> children = createSingleRecordAction.children();
        Assert.assertThat(children.size(), is(3));
        AssertActionsUtil.assertStoreRecordAction(children.get(0), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(children.get(1), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(children.get(2), state.getRawRepo(), record);
    }

    @Test
    public void testPerformAction_WithLocal_WithFFUAndFBS() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        Set<String> ffuLibraries = new HashSet<>();
        ffuLibraries.add("700300");

        Record rr1 = new RawRepoRecordMock(recordId, 700300);
        rr1.setMimeType(MarcXChangeMimeType.MARCXCHANGE);
        Record rr2 = new RawRepoRecordMock(recordId, 800500);
        rr2.setMimeType(MarcXChangeMimeType.MARCXCHANGE);

        when(state.getRawRepo().agenciesForRecordAll(eq(record))).thenReturn(AssertActionsUtil.createAgenciesSet(700300, 800500));
        when(state.getSolrFBS().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(false);
        when(state.getOpenAgencyService().getFFULibraries()).thenReturn(ffuLibraries);
        when(state.getRawRepo().fetchRecord(recordId, 700300)).thenReturn(rr1);
        when(state.getRawRepo().fetchRecord(recordId, 800500)).thenReturn(rr2);


        CreateSingleRecordAction createSingleRecordAction = new CreateSingleRecordAction(state, settings, record);
        String message = state.getMessages().getString("create.record.with.locals");
        assertThat(createSingleRecordAction.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state)));
        assertThat(createSingleRecordAction.children().isEmpty(), is(true));
    }

    @Test
    public void testPerformAction_WithLocal_FromDifferentBase() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        Set<String> ffuLibraries = new HashSet<>();
        ffuLibraries.add("700300");

        Record rr = new RawRepoRecordMock(recordId, 870971);
        rr.setMimeType(MarcXChangeMimeType.ARTICLE);

        when(state.getRawRepo().agenciesForRecordAll(eq(record))).thenReturn(AssertActionsUtil.createAgenciesSet(870971));
        when(state.getSolrFBS().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(false);
        when(state.getOpenAgencyService().getFFULibraries()).thenReturn(ffuLibraries);
        when(state.getRawRepo().fetchRecord(recordId, 870971)).thenReturn(rr);

        CreateSingleRecordAction createSingleRecordAction = new CreateSingleRecordAction(state, settings, record);
        String message = state.getMessages().getString("create.record.with.locals");
        assertThat(createSingleRecordAction.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state)));
        assertThat(createSingleRecordAction.children().isEmpty(), is(true));
    }

    @Test
    public void testPerformAction_WithLocal_FBSEnrichments() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        Set<String> ffuLibraries = new HashSet<>();
        ffuLibraries.add("700300");

        Record rr1 = new RawRepoRecordMock(recordId, 870970);
        rr1.setMimeType(MarcXChangeMimeType.MARCXCHANGE);
        Record rr2 = new RawRepoRecordMock(recordId, 830010);
        rr2.setMimeType(MarcXChangeMimeType.ENRICHMENT);
        Record rr3 = new RawRepoRecordMock(recordId, 830020);
        rr3.setMimeType(MarcXChangeMimeType.ENRICHMENT);

        when(state.getRawRepo().agenciesForRecordAll(eq(record))).thenReturn(AssertActionsUtil.createAgenciesSet(870970, 830010, 830020));
        when(state.getSolrFBS().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(false);
        when(state.getOpenAgencyService().getFFULibraries()).thenReturn(ffuLibraries);
        when(state.getRawRepo().fetchRecord(recordId, 870970)).thenReturn(rr1);
        when(state.getRawRepo().fetchRecord(recordId, 830010)).thenReturn(rr2);
        when(state.getRawRepo().fetchRecord(recordId, 830020)).thenReturn(rr3);

        CreateSingleRecordAction createSingleRecordAction = new CreateSingleRecordAction(state, settings, record);
        assertThat(createSingleRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));
        List<ServiceAction> children = createSingleRecordAction.children();
        Assert.assertThat(children.size(), is(3));
        AssertActionsUtil.assertStoreRecordAction(children.get(0), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(children.get(1), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(children.get(2), state.getRawRepo(), record);
    }

}
