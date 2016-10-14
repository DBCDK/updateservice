package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.updateservice.dto.UpdateStatusEnumDto;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import dk.dbc.updateservice.ws.JNDIResources;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

public class CreateSingleRecordActionTest {
    private GlobalActionState state;
    private Properties settings;

    @Before
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
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
        when(state.getSolrService().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(false);

        CreateSingleRecordAction createSingleRecordAction = new CreateSingleRecordAction(state, settings, record);
        assertThat(createSingleRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = createSingleRecordAction.children();
        Assert.assertThat(children.size(), is(2));
        AssertActionsUtil.assertStoreRecordAction(children.get(0), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(children.get(1), state.getRawRepo(), record, settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID), MarcXChangeMimeType.MARCXCHANGE);
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
        when(state.getSolrService().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(true);

        CreateSingleRecordAction createSingleRecordAction = new CreateSingleRecordAction(state, settings, record);
        String message = state.getMessages().getString("update.record.with.002.links");
        assertThat(createSingleRecordAction.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDto.FAILED, message, state)));
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
    public void testPerformAction_WithLocal() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);

        when(state.getRawRepo().agenciesForRecord(eq(record))).thenReturn(AssertActionsUtil.createAgenciesSet(700300));
        when(state.getSolrService().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(false);

        CreateSingleRecordAction createSingleRecordAction = new CreateSingleRecordAction(state, settings, record);
        String message = state.getMessages().getString("create.record.with.locals");
        assertThat(createSingleRecordAction.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDto.FAILED, message, state)));
        assertThat(createSingleRecordAction.children().isEmpty(), is(true));
    }
}
