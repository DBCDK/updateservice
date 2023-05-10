package dk.dbc.updateservice.actions;

import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.updateservice.update.LibraryGroup;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import dk.dbc.updateservice.update.UpdateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.ListIterator;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

class UpdateVolumeRecordTest {
    private GlobalActionState state;
    private Properties settings;
    private static final String GROUP_ID = "700000";
    LibraryGroup libraryGroup = LibraryGroup.FBS;

    @BeforeEach
    public void before() throws IOException, UpdateException {
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
    void testPerformAction_CreateRecord() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String mainRecordId = AssertActionsUtil.getBibliographicRecordId(mainRecord);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(mainRecord);
        MarcRecord volumeRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        String volumeRecordId = AssertActionsUtil.getBibliographicRecordId(volumeRecord);

        when(state.getRawRepo().recordExists(mainRecordId, agencyId)).thenReturn(true);
        when(state.getRawRepo().recordExists(volumeRecordId, agencyId)).thenReturn(false);
        when(state.getHoldingsItems().getAgenciesWithHoldings(volumeRecordId)).thenReturn(AssertActionsUtil.createAgenciesSet());

        UpdateVolumeRecord updateVolumeRecord = new UpdateVolumeRecord(state, settings, volumeRecord);
        assertThat(updateVolumeRecord.performAction(), is(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = updateVolumeRecord.children().listIterator();
        AssertActionsUtil.assertCreateVolumeRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, state.getHoldingsItems(), state.getSolrFBS(), settings.getProperty(state.getRawRepoProviderId()));
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
    void testPerformAction_OverwriteRecord() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String mainRecordId = AssertActionsUtil.getBibliographicRecordId(mainRecord);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(mainRecord);
        MarcRecord volumeRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        String volumeRecordId = AssertActionsUtil.getBibliographicRecordId(volumeRecord);

        when(state.getRawRepo().recordExists(mainRecordId, agencyId)).thenReturn(true);
        when(state.getRawRepo().recordExists(volumeRecordId, agencyId)).thenReturn(true);
        when(state.getRawRepo().fetchMergedDBCRecord(volumeRecordId, RawRepo.DBC_ENRICHMENT)).thenReturn(AssertActionsUtil.createRawRepoRecord(volumeRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getHoldingsItems().getAgenciesWithHoldings(volumeRecordId)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getLibraryRecordsHandler().hasClassificationData(volumeRecord)).thenReturn(false);

        UpdateVolumeRecord instance = new UpdateVolumeRecord(state, settings, volumeRecord);
        assertThat(instance.performAction(), is(ServiceResult.newOkResult()));

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
    void testPerformAction_DeleteRecord() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String mainRecordId = AssertActionsUtil.getBibliographicRecordId(mainRecord);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(mainRecord);
        MarcRecord volumeRecord = AssertActionsUtil.loadRecordAndMarkForDeletion(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        String volumeRecordId = AssertActionsUtil.getBibliographicRecordId(volumeRecord);

        when(state.getRawRepo().recordExists(mainRecordId, agencyId)).thenReturn(true);
        when(state.getRawRepo().recordExists(volumeRecordId, agencyId)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(volumeRecordId, agencyId)).thenReturn(AssertActionsUtil.createRawRepoRecord(volumeRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getHoldingsItems().getAgenciesWithHoldings(volumeRecordId)).thenReturn(new HashSet<>());
        when(state.getSolrFBS().hasDocuments(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", volumeRecordId))).thenReturn(false);
        when(state.getSolrFBS().getOwnerOf002(SolrServiceIndexer.createGetOwnerOf002QueryDBCOnly("002a", volumeRecordId))).thenReturn("");

        UpdateVolumeRecord instance = new UpdateVolumeRecord(state, settings, volumeRecord);
        assertThat(instance.performAction(), is(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertDeleteCommonRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, state.getLibraryRecordsHandler(), state.getHoldingsItems(), settings.getProperty(state.getRawRepoProviderId()));
        assertThat(iterator.hasNext(), is(false));
    }
}
