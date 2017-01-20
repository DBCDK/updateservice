package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordWriter;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

public class UpdateCommonRecordActionTest {
    private GlobalActionState state;
    private Properties settings;
    private static final String GROUP_ID = "700000";

    @Before
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId(GROUP_ID);
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
     * <li>UpdateSingleRecordAction: Creates the new record</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_CreateSingleRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        Integer agencyId = AssertActionsUtil.getAgencyIdAsInteger(record);

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(false);
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getLibraryRecordsHandler().hasClassificationData(record)).thenReturn(false);

        UpdateCommonRecordAction updateCommonRecordAction = new UpdateCommonRecordAction(state, settings, record);
        assertThat(updateCommonRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateCommonRecordAction.children();
        Assert.assertThat(children.size(), is(1));

        UpdateSingleRecord updateSingleRecord = (UpdateSingleRecord) children.get(0);
        assertThat(updateSingleRecord, notNullValue());
        assertThat(updateSingleRecord.getRawRepo(), is(state.getRawRepo()));
        assertThat(updateSingleRecord.getRecord(), is(record));
        assertThat(updateSingleRecord.state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), equalTo(GROUP_ID));
        assertThat(updateSingleRecord.state.getHoldingsItems(), is(state.getHoldingsItems()));
        assertThat(updateSingleRecord.state.getOpenAgencyService(), is(state.getOpenAgencyService()));
        assertThat(updateSingleRecord.state.getLibraryRecordsHandler(), is(state.getLibraryRecordsHandler()));
    }

    /**
     * Test performAction(): Create new single common record which is a double alias.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with a record with 002-link to record that is being updated.
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
    public void testPerformAction_CreateSingleRecord_WithDoubleAlias() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        Integer agencyId = AssertActionsUtil.getAgencyIdAsInteger(record);

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(false);
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getLibraryRecordsHandler().hasClassificationData(record)).thenReturn(false);
        when(state.getSolrService().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(true);

        UpdateCommonRecordAction updateCommonRecordAction = new UpdateCommonRecordAction(state, settings, record);
        String message = state.getMessages().getString("update.record.with.002.links");
        assertThat(updateCommonRecordAction.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state)));
    }

    /**
     * Test performAction(): Delete new single common record which is a double alias.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with two records: <code>r1</code> is a double record to <code>r2</code>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Delete <code>r2</code>
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>UpdateSingleRecordAction: Update <code>r2</code></li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_DeleteSingleRecord_WithDoubleAlias() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        Integer agencyId = AssertActionsUtil.getAgencyIdAsInteger(record);
        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.markForDeletion();

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(false);
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getLibraryRecordsHandler().hasClassificationData(record)).thenReturn(false);

        UpdateCommonRecordAction updateCommonRecordAction = new UpdateCommonRecordAction(state, settings, record);
        assertThat(updateCommonRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateCommonRecordAction.children();
        Assert.assertThat(children.size(), is(1));

        UpdateSingleRecord updateSingleRecord = (UpdateSingleRecord) children.get(0);
        assertThat(updateSingleRecord, notNullValue());
        assertThat(updateSingleRecord.getRawRepo(), is(state.getRawRepo()));
        assertThat(updateSingleRecord.getRecord(), is(record));
        assertThat(updateSingleRecord.state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), equalTo(GROUP_ID));
        assertThat(updateSingleRecord.state.getHoldingsItems(), is(state.getHoldingsItems()));
        assertThat(updateSingleRecord.state.getOpenAgencyService(), is(state.getOpenAgencyService()));
        assertThat(updateSingleRecord.state.getLibraryRecordsHandler(), is(state.getLibraryRecordsHandler()));
    }

    /**
     * Test performAction(): Create new volume common record.
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
     * <li>UpdateVolumeRecordAction: Creates the new record</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_CreateVolumeRecord() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String mainRecordId = AssertActionsUtil.getRecordId(mainRecord);
        Integer agencyId = AssertActionsUtil.getAgencyIdAsInteger(mainRecord);
        MarcRecord volumeRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        String volumeRecordId = AssertActionsUtil.getRecordId(volumeRecord);

        when(state.getRawRepo().recordExists(eq(mainRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(volumeRecordId), eq(agencyId))).thenReturn(false);
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(volumeRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getLibraryRecordsHandler().hasClassificationData(volumeRecord)).thenReturn(false);

        UpdateCommonRecordAction updateCommonRecordAction = new UpdateCommonRecordAction(state, settings, volumeRecord);
        assertThat(updateCommonRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateCommonRecordAction.children();
        Assert.assertThat(children.size(), is(1));

        UpdateVolumeRecord updateVolumeRecord = (UpdateVolumeRecord) children.get(0);
        assertThat(updateVolumeRecord, notNullValue());
        assertThat(updateVolumeRecord.getRawRepo(), is(state.getRawRepo()));
        assertThat(updateVolumeRecord.getRecord(), is(volumeRecord));
        assertThat(updateVolumeRecord.state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), equalTo(GROUP_ID));
        assertThat(updateVolumeRecord.state.getHoldingsItems(), is(state.getHoldingsItems()));
        assertThat(updateVolumeRecord.state.getOpenAgencyService(), is(state.getOpenAgencyService()));
        assertThat(updateVolumeRecord.state.getLibraryRecordsHandler(), is(state.getLibraryRecordsHandler()));
    }
}
