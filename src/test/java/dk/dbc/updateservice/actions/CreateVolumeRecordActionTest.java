/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.OpenAgencyService;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.ListIterator;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

public class CreateVolumeRecordActionTest {
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
    public void testPerformAction_NoLocals_No002Links() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String mainRecordId = AssertActionsUtil.getRecordId(mainRecord);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(mainRecord);
        MarcRecord volumeRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        String volumeRecordId = AssertActionsUtil.getRecordId(volumeRecord);

        when(state.getRawRepo().recordExists(eq(mainRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExistsMaybeDeleted(eq(volumeRecordId), eq(agencyId))).thenReturn(false);
        when(state.getRawRepo().agenciesForRecord(eq(volumeRecord))).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(mainRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(volumeRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getSolrService().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", volumeRecordId)))).thenReturn(false);

        CreateVolumeRecordAction createVolumeRecordAction = new CreateVolumeRecordAction(state, settings, volumeRecord);
        assertThat(createVolumeRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = createVolumeRecordAction.children();
        Assert.assertThat(children.size(), is(5));

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
    public void testPerformAction_NoLocals_With002Links() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String mainRecordId = AssertActionsUtil.getRecordId(mainRecord);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(mainRecord);

        MarcRecord volumeRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        String volumeRecordId = AssertActionsUtil.getRecordId(volumeRecord);

        when(state.getRawRepo().recordExists(eq(mainRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExistsMaybeDeleted(eq(volumeRecordId), eq(agencyId))).thenReturn(false);
        when(state.getRawRepo().agenciesForRecord(eq(volumeRecord))).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(mainRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(volumeRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getSolrService().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", volumeRecordId)))).thenReturn(true);

        CreateVolumeRecordAction createVolumeRecordAction = new CreateVolumeRecordAction(state, settings, volumeRecord);
        String message = state.getMessages().getString("update.record.with.002.links");
        assertThat(createVolumeRecordAction.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state)));
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
    public void testPerformAction_WithLocals() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String mainRecordId = AssertActionsUtil.getRecordId(mainRecord);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(mainRecord);

        MarcRecord volumeRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        String volumeRecordId = AssertActionsUtil.getRecordId(volumeRecord);

        when(state.getRawRepo().recordExists(eq(mainRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExistsMaybeDeleted(eq(volumeRecordId), eq(agencyId))).thenReturn(false);
        when(state.getRawRepo().agenciesForRecordAll(eq(volumeRecord))).thenReturn(AssertActionsUtil.createAgenciesSet(700300));
        when(state.getRawRepo().fetchRecord(volumeRecordId, 700300)).thenReturn(AssertActionsUtil.createRawRepoRecord(volumeRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(mainRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(volumeRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getSolrService().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", volumeRecordId)))).thenReturn(false);

        CreateVolumeRecordAction createVolumeRecordAction = new CreateVolumeRecordAction(state, settings, volumeRecord);
        String message = state.getMessages().getString("create.record.with.locals");
        assertThat(createVolumeRecordAction.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state)));
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
    public void testPerformAction_PointToItselfAsParent() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String mainRecordId = AssertActionsUtil.getRecordId(mainRecord);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(mainRecord);

        MarcRecord volumeRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        String volumeRecordId = AssertActionsUtil.getRecordId(volumeRecord);
        new MarcRecordWriter(volumeRecord).addOrReplaceSubfield("014", "a", volumeRecordId);

        when(state.getRawRepo().recordExists(eq(mainRecordId), eq(agencyId))).thenReturn(false);
        when(state.getRawRepo().recordExistsMaybeDeleted(eq(volumeRecordId), eq(agencyId))).thenReturn(false);
        when(state.getRawRepo().agenciesForRecord(eq(volumeRecord))).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(mainRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(volumeRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getSolrService().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", volumeRecordId)))).thenReturn(false);

        CreateVolumeRecordAction createVolumeRecordAction = new CreateVolumeRecordAction(state, settings, volumeRecord);
        String message = String.format(state.getMessages().getString("parent.point.to.itself"), volumeRecordId, agencyId);
        assertThat(createVolumeRecordAction.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state)));
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
    public void testPerformAction_NoParent() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String mainRecordId = AssertActionsUtil.getRecordId(mainRecord);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(mainRecord);

        MarcRecord volumeRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        String volumeRecordId = AssertActionsUtil.getRecordId(volumeRecord);

        when(state.getRawRepo().recordExists(eq(mainRecordId), eq(agencyId))).thenReturn(false);
        when(state.getRawRepo().recordExistsMaybeDeleted(eq(volumeRecordId), eq(agencyId))).thenReturn(false);
        when(state.getRawRepo().agenciesForRecord(eq(volumeRecord))).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(mainRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(volumeRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getSolrService().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", volumeRecordId)))).thenReturn(false);

        CreateVolumeRecordAction instance = new CreateVolumeRecordAction(state, settings, volumeRecord);
        String message = String.format(state.getMessages().getString("reference.record.not.exist"), volumeRecordId, agencyId, mainRecordId, agencyId);
        assertThat(instance.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state)));
        assertThat(instance.children().isEmpty(), is(true));
    }
}
