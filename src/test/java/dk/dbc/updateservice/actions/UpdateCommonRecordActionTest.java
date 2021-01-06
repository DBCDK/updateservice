/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.LibraryGroup;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class UpdateCommonRecordActionTest {
    private GlobalActionState state;
    private Properties settings;
    private static final String GROUP_ID = "700000";

    @BeforeEach
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
    void testPerformAction_CreateSingleRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(false);
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getLibraryRecordsHandler().hasClassificationData(record)).thenReturn(false);
        state.setLibraryGroup(LibraryGroup.DBC);

        UpdateCommonRecordAction updateCommonRecordAction = new UpdateCommonRecordAction(state, settings, record);
        assertThat(updateCommonRecordAction.performAction(), is(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateCommonRecordAction.children();
        assertThat(children.size(), is(1));

        UpdateSingleRecord updateSingleRecord = (UpdateSingleRecord) children.get(0);
        assertThat(updateSingleRecord, notNullValue());
        assertThat(updateSingleRecord.getRawRepo(), is(state.getRawRepo()));
        assertThat(updateSingleRecord.getRecord(), is(record));
        assertThat(updateSingleRecord.state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), is(GROUP_ID));
        assertThat(updateSingleRecord.state.getHoldingsItems(), is(state.getHoldingsItems()));
        assertThat(updateSingleRecord.state.getVipCoreService(), is(state.getVipCoreService()));
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
    void testPerformAction_CreateSingleRecord_WithDoubleAlias() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(false);
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getLibraryRecordsHandler().hasClassificationData(record)).thenReturn(false);
        when(state.getSolrFBS().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(true);

        UpdateCommonRecordAction updateCommonRecordAction = new UpdateCommonRecordAction(state, settings, record);
        String message = state.getMessages().getString("update.record.with.002.links");
        assertThat(updateCommonRecordAction.performAction(), is(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message)));
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
    void testPerformAction_DeleteSingleRecord_WithDoubleAlias() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);
        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.markForDeletion();

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(false);
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getLibraryRecordsHandler().hasClassificationData(record)).thenReturn(false);
        state.setLibraryGroup(LibraryGroup.DBC);

        UpdateCommonRecordAction updateCommonRecordAction = new UpdateCommonRecordAction(state, settings, record);
        assertThat(updateCommonRecordAction.performAction(), is(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateCommonRecordAction.children();
        assertThat(children.size(), is(1));

        UpdateSingleRecord updateSingleRecord = (UpdateSingleRecord) children.get(0);
        assertThat(updateSingleRecord, notNullValue());
        assertThat(updateSingleRecord.getRawRepo(), is(state.getRawRepo()));
        assertThat(updateSingleRecord.getRecord(), is(record));
        assertThat(updateSingleRecord.state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), is(GROUP_ID));
        assertThat(updateSingleRecord.state.getHoldingsItems(), is(state.getHoldingsItems()));
        assertThat(updateSingleRecord.state.getVipCoreService(), is(state.getVipCoreService()));
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
    void testPerformAction_CreateVolumeRecord() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String mainRecordId = AssertActionsUtil.getRecordId(mainRecord);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(mainRecord);
        MarcRecord volumeRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        String volumeRecordId = AssertActionsUtil.getRecordId(volumeRecord);

        when(state.getRawRepo().recordExists(eq(mainRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(volumeRecordId), eq(agencyId))).thenReturn(false);
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(volumeRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getLibraryRecordsHandler().hasClassificationData(volumeRecord)).thenReturn(false);
        state.setLibraryGroup(LibraryGroup.DBC);

        UpdateCommonRecordAction updateCommonRecordAction = new UpdateCommonRecordAction(state, settings, volumeRecord);
        assertThat(updateCommonRecordAction.performAction(), is(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateCommonRecordAction.children();
        assertThat(children.size(), is(1));

        UpdateVolumeRecord updateVolumeRecord = (UpdateVolumeRecord) children.get(0);
        assertThat(updateVolumeRecord, notNullValue());
        assertThat(updateVolumeRecord.getRawRepo(), is(state.getRawRepo()));
        assertThat(updateVolumeRecord.getRecord(), is(volumeRecord));
        assertThat(updateVolumeRecord.state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), is(GROUP_ID));
        assertThat(updateVolumeRecord.state.getHoldingsItems(), is(state.getHoldingsItems()));
        assertThat(updateVolumeRecord.state.getVipCoreService(), is(state.getVipCoreService()));
        assertThat(updateVolumeRecord.state.getLibraryRecordsHandler(), is(state.getLibraryRecordsHandler()));
    }

    @Test
    void testPerformAction_CreateSingleRecordFBS() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);
        String groupId = state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId();

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getLibraryRecordsHandler().hasClassificationData(record)).thenReturn(true);
        when(state.getNoteAndSubjectExtensionsHandler().isNationalCommonRecord(record)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(recordId, agencyId)).thenReturn(AssertActionsUtil.createRawRepoRecord(new MarcRecord(record), MarcXChangeMimeType.MARCXCHANGE));
        when(state.getNoteAndSubjectExtensionsHandler().collapse(record, new MarcRecord(record), groupId, true)).thenReturn(record);

        state.setLibraryGroup(LibraryGroup.FBS);

        UpdateCommonRecordAction updateCommonRecordAction = new UpdateCommonRecordAction(state, settings, record);
        assertThat(updateCommonRecordAction.performAction(), is(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateCommonRecordAction.children();
        assertThat(children.size(), is(1));

        UpdateSingleRecord updateSingleRecord = (UpdateSingleRecord) children.get(0);
        assertThat(updateSingleRecord, notNullValue());
        assertThat(updateSingleRecord.getRawRepo(), is(state.getRawRepo()));
        assertThat(updateSingleRecord.getRecord(), is(record));
        assertThat(updateSingleRecord.state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), is(GROUP_ID));
        assertThat(updateSingleRecord.state.getHoldingsItems(), is(state.getHoldingsItems()));
        assertThat(updateSingleRecord.state.getVipCoreService(), is(state.getVipCoreService()));
        assertThat(updateSingleRecord.state.getLibraryRecordsHandler(), is(state.getLibraryRecordsHandler()));
    }

}
