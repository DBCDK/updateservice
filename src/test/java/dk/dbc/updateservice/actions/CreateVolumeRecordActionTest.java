package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.LibraryGroup;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import dk.dbc.updateservice.update.UpdateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.ListIterator;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class CreateVolumeRecordActionTest {
    private GlobalActionState state;
    private Properties settings;
    LibraryGroup libraryGroup = LibraryGroup.FBS;

    @BeforeEach
    public void before() throws IOException, UpdateException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
        state.setLibraryGroup(libraryGroup);
        settings = new UpdateTestUtils().getSettings();
    }

    /**
     * Test performAction(): Create new volume common record with no local records for the
     * same faust-id.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with a main record, <code>m1</code>.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update a volume common record, that points to <code>m1</code> in
     * <code>014a</code>.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>StoreRecordAction: Store the record</li>
     * <li>RemoveLinksAction: Remove existing links to other records</li>
     * <li>LinkRecord: Link record to parent record (<code>m1</code>)</li>
     * <li>EnqueueRecordAction: Put the record in queue</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_NoLocals_No002Links() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String mainRecordId = AssertActionsUtil.getBibliographicRecordId(mainRecord);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(mainRecord);
        MarcRecord volumeRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        String volumeRecordId = AssertActionsUtil.getBibliographicRecordId(volumeRecord);

        when(state.getRawRepo().recordExists(eq(mainRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExistsMaybeDeleted(eq(volumeRecordId), eq(agencyId))).thenReturn(false);
        when(state.getRawRepo().agenciesForRecord(eq(volumeRecordId))).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesWithHoldings(mainRecordId)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesWithHoldings(volumeRecordId)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getSolrFBS().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", volumeRecordId)))).thenReturn(false);

        CreateVolumeRecordAction createVolumeRecordAction = new CreateVolumeRecordAction(state, settings, volumeRecord);
        assertThat(createVolumeRecordAction.performAction(), is(ServiceResult.newOkResult()));

        List<ServiceAction> children = createVolumeRecordAction.children();
        assertThat(children.size(), is(5));

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertLinkRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, mainRecord);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
    }

    /**
     * Test performAction(): Create new volume common record with no local records for the
     * same faust-id with 002 links in other records.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with a main record, <code>m1</code>.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update a volume common record, that points to <code>m1</code> in
     * <code>014a</code>.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return status: FAILED_UPDATE_INTERNAL_ERROR
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_NoLocals_With002Links() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String mainRecordId = AssertActionsUtil.getBibliographicRecordId(mainRecord);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(mainRecord);

        MarcRecord volumeRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        String volumeRecordId = AssertActionsUtil.getBibliographicRecordId(volumeRecord);

        when(state.getRawRepo().recordExists(eq(mainRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExistsMaybeDeleted(eq(volumeRecordId), eq(agencyId))).thenReturn(false);
        when(state.getRawRepo().agenciesForRecord(eq(volumeRecordId))).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesWithHoldings(mainRecordId)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesWithHoldings(volumeRecordId)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getSolrFBS().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", volumeRecordId)))).thenReturn(true);

        CreateVolumeRecordAction createVolumeRecordAction = new CreateVolumeRecordAction(state, settings, volumeRecord);
        String message = state.getMessages().getString("update.record.with.002.links");
        assertThat(createVolumeRecordAction.performAction(), is(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message)));
        assertThat(createVolumeRecordAction.children().isEmpty(), is(true));
    }

    /**
     * Test performAction(): Create new volume common record with no local records for the
     * same faust-id.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with a main record, <code>m1</code>.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update a volume common record, that points to <code>m1</code> in
     * <code>014a</code>.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return status: FAILED_UPDATE_INTERNAL_ERROR
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_WithLocals() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String mainRecordId = AssertActionsUtil.getBibliographicRecordId(mainRecord);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(mainRecord);

        MarcRecord volumeRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        String volumeRecordId = AssertActionsUtil.getBibliographicRecordId(volumeRecord);

        when(state.getRawRepo().recordExists(eq(mainRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExistsMaybeDeleted(eq(volumeRecordId), eq(agencyId))).thenReturn(false);
        when(state.getRawRepo().agenciesForRecordAll(eq(volumeRecordId))).thenReturn(AssertActionsUtil.createAgenciesSet(700300));
        when(state.getRawRepo().fetchRecord(volumeRecordId, 700300)).thenReturn(AssertActionsUtil.createRawRepoRecord(volumeRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getHoldingsItems().getAgenciesWithHoldings(mainRecordId)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesWithHoldings(volumeRecordId)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getSolrFBS().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", volumeRecordId)))).thenReturn(false);

        CreateVolumeRecordAction createVolumeRecordAction = new CreateVolumeRecordAction(state, settings, volumeRecord);
        String message = state.getMessages().getString("create.record.with.locals");
        assertThat(createVolumeRecordAction.performAction(), is(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message)));
        assertThat(createVolumeRecordAction.children().isEmpty(), is(true));
    }

    /**
     * Test performAction(): Create new volume common record that points to itself as parent.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * An empty rawrepo.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update a volume common record with <code>001a == 014a</code>.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return status: FAILED_UPDATE_INTERNAL_ERROR
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_PointToItselfAsParent() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String mainRecordId = AssertActionsUtil.getBibliographicRecordId(mainRecord);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(mainRecord);

        MarcRecord volumeRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        String volumeRecordId = AssertActionsUtil.getBibliographicRecordId(volumeRecord);
        new MarcRecordWriter(volumeRecord).addOrReplaceSubField("014", 'a', volumeRecordId);

        when(state.getRawRepo().recordExists(eq(mainRecordId), eq(agencyId))).thenReturn(false);
        when(state.getRawRepo().recordExistsMaybeDeleted(eq(volumeRecordId), eq(agencyId))).thenReturn(false);
        when(state.getRawRepo().agenciesForRecord(eq(volumeRecordId))).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesWithHoldings(mainRecordId)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesWithHoldings(volumeRecordId)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getSolrFBS().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", volumeRecordId)))).thenReturn(false);

        CreateVolumeRecordAction createVolumeRecordAction = new CreateVolumeRecordAction(state, settings, volumeRecord);
        String message = String.format(state.getMessages().getString("parent.point.to.itself"), volumeRecordId, agencyId);
        assertThat(createVolumeRecordAction.performAction(), is(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message)));
        assertThat(createVolumeRecordAction.children().isEmpty(), is(true));
    }

    /**
     * Test performAction(): Create new volume common record with unknown parent.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * An empty rawrepo.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update a volume common record, that points to some record in
     * <code>014a</code>.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return status: FAILED_UPDATE_INTERNAL_ERROR
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_NoParent() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String mainRecordId = AssertActionsUtil.getBibliographicRecordId(mainRecord);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(mainRecord);

        MarcRecord volumeRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        String volumeRecordId = AssertActionsUtil.getBibliographicRecordId(volumeRecord);

        when(state.getRawRepo().recordExists(eq(mainRecordId), eq(agencyId))).thenReturn(false);
        when(state.getRawRepo().recordExistsMaybeDeleted(eq(volumeRecordId), eq(agencyId))).thenReturn(false);
        when(state.getRawRepo().agenciesForRecord(eq(volumeRecordId))).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesWithHoldings(mainRecordId)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesWithHoldings(volumeRecordId)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getSolrFBS().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", volumeRecordId)))).thenReturn(false);

        CreateVolumeRecordAction instance = new CreateVolumeRecordAction(state, settings, volumeRecord);
        String message = String.format(state.getMessages().getString("reference.record.not.exist"), volumeRecordId, agencyId, mainRecordId, agencyId);
        assertThat(instance.performAction(), is(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message)));
        assertThat(instance.children().isEmpty(), is(true));
    }
}
