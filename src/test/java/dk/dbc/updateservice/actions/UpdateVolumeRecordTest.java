package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import dk.dbc.updateservice.ws.JNDIResources;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.ListIterator;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

public class UpdateVolumeRecordTest {
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
     * <li>CreateSingleRecordAction: Creates the new record</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_CreateRecord() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String mainRecordId = AssertActionsUtil.getRecordId(mainRecord);
        Integer agencyId = AssertActionsUtil.getAgencyIdAsInteger(mainRecord);
        MarcRecord volumeRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        String volumeRecordId = AssertActionsUtil.getRecordId(volumeRecord);

        when(state.getRawRepo().recordExists(eq(mainRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(volumeRecordId), eq(agencyId))).thenReturn(false);
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(volumeRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());

        UpdateVolumeRecord updateVolumeRecord = new UpdateVolumeRecord(state, settings, volumeRecord);
        assertThat(updateVolumeRecord.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = updateVolumeRecord.children().listIterator();
        AssertActionsUtil.assertCreateVolumeRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, state.getHoldingsItems(), state.getSolrService(), settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID));
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
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String mainRecordId = AssertActionsUtil.getRecordId(mainRecord);
        Integer agencyId = AssertActionsUtil.getAgencyIdAsInteger(mainRecord);
        MarcRecord volumeRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        String volumeRecordId = AssertActionsUtil.getRecordId(volumeRecord);

        when(state.getRawRepo().recordExists(eq(mainRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(volumeRecordId), eq(agencyId))).thenReturn(true);
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(volumeRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getLibraryRecordsHandler().hasClassificationData(volumeRecord)).thenReturn(false);

        UpdateVolumeRecord instance = new UpdateVolumeRecord(state, settings, volumeRecord);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertOverwriteVolumeRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, GROUP_ID, state.getLibraryRecordsHandler(), state.getHoldingsItems());
        assertThat(iterator.hasNext(), is(false));
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
    public void testPerformAction_DeleteRecord() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String mainRecordId = AssertActionsUtil.getRecordId(mainRecord);
        Integer agencyId = AssertActionsUtil.getAgencyIdAsInteger(mainRecord);
        MarcRecord volumeRecord = AssertActionsUtil.loadRecordAndMarkForDeletion(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        String volumeRecordId = AssertActionsUtil.getRecordId(volumeRecord);

        when(state.getRawRepo().recordExists(eq(mainRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(volumeRecordId), eq(agencyId))).thenReturn(true);
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(volumeRecord)).thenReturn(new HashSet<>());
        when(state.getSolrService().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", volumeRecordId)))).thenReturn(false);

        UpdateVolumeRecord instance = new UpdateVolumeRecord(state, settings, volumeRecord);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertCommonDeleteRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, state.getLibraryRecordsHandler(), state.getHoldingsItems(), settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID));
        assertThat(iterator.hasNext(), is(false));
    }
}
