/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.AgencyNumber;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.JNDIResources;
import dk.dbc.updateservice.update.LibraryGroup;
import dk.dbc.vipcore.libraryrules.VipCoreLibraryRulesConnector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.ListIterator;
import java.util.Properties;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateLocalRecordActionTest {
    private GlobalActionState state;
    private Properties settings;
    LibraryGroup libraryGroup = LibraryGroup.FBS;

    @BeforeEach
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
        state.setLibraryGroup(libraryGroup);
        settings = new UpdateTestUtils().getSettings();
    }

    /**
     * Test UpdateLocalRecordAction.performAction(): Create single record.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A single record.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update the record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>StoreRecordAction: Store the record</li>
     * <li>RemoveLinksAction: Remove any existing links to other records</li>
     * <li>EnqueueRecordAction: Put the record in queue</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_CreateSingleRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        UpdateLocalRecordAction updateLocalRecordAction = new UpdateLocalRecordAction(state, settings, record);
        assertThat(updateLocalRecordAction.performAction(), is(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = updateLocalRecordAction.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_FBS), MarcXChangeMimeType.MARCXCHANGE);
        assertThat(iterator.hasNext(), is(false));
    }

    /**
     * Test UpdateLocalRecordAction.performAction(): Create volume record.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo containing a main record m1.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update a record that points to <code>m1</code> in <code>014a</code>.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>StoreRecordAction: Store the record</li>
     * <li>LinkRecord: Link record to parent record (<code>m1</code>)</li>
     * <li>EnqueueRecordAction: Put the record in queue</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_CreateVolumeRecord() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        MarcRecord volumeRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(volumeRecord);
        int agencyId = reader.getAgencyIdAsInt();
        String parentId = reader.getParentRecordId();

        when(state.getRawRepo().recordExists(eq(parentId), eq(agencyId))).thenReturn(true);

        UpdateLocalRecordAction updateLocalRecordAction = new UpdateLocalRecordAction(state, settings, volumeRecord);
        assertThat(updateLocalRecordAction.performAction(), is(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = updateLocalRecordAction.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertLinkRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, mainRecord);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_FBS), MarcXChangeMimeType.MARCXCHANGE);
        assertThat(iterator.hasNext(), is(false));
    }

    /**
     * Test UpdateLocalRecordAction.performAction(): Update volume record that points to unknown
     * main record.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A empty rawrepo.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update a record that points an unknown record in <code>014a</code>.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return status: FAILED_VALIDATION_INTERNAL_ERROR.
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_CreateVolumeRecord_UnknownParent() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.getRecordId();
        int agencyId = reader.getAgencyIdAsInt();
        String parentId = reader.getParentRecordId();

        when(state.getRawRepo().recordExists(eq(parentId), eq(agencyId))).thenReturn(false);

        UpdateLocalRecordAction updateLocalRecordAction = new UpdateLocalRecordAction(state, settings, record);
        String message = String.format(state.getMessages().getString("reference.record.not.exist"), recordId, agencyId, parentId, agencyId);
        assertThat(updateLocalRecordAction.performAction(), is(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message)));
    }

    /**
     * Test UpdateLocalRecordAction.performAction(): Update volume record that points to itself
     * in <code>014a</code>
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A empty rawrepo.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update a record that points to itself in <code>014a</code>.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return status: FAILED_VALIDATION_INTERNAL_ERROR.
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_CreateVolumeRecord_Itself() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.getRecordId();
        int agencyId = reader.getAgencyIdAsInt();
        new MarcRecordWriter(record).addOrReplaceSubfield("014", "a", recordId);

        UpdateLocalRecordAction updateLocalRecordAction = new UpdateLocalRecordAction(state, settings, record);
        String message = String.format(state.getMessages().getString("parent.point.to.itself"), recordId, agencyId);
        assertThat(updateLocalRecordAction.performAction(), is(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message)));
        verify(state.getRawRepo(), never()).recordExists(anyString(), anyInt());
    }

    /**
     * Test UpdateLocalRecordAction.performAction(): Delete main record that has children.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A empty rawrepo.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Delete a record that has children.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return status: FAILED_VALIDATION_INTERNAL_ERROR.
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_DeleteRecord_WithChildren() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        new MarcRecordWriter(record).markForDeletion();
        final RecordId recordId = AssertActionsUtil.getRecordId(record);

        Set<RecordId> children = new HashSet<>();
        children.add(new RecordId("xxx", 101010));
        when(state.getRawRepo().children(eq(recordId))).thenReturn(children);

        UpdateLocalRecordAction updateLocalRecordAction = new UpdateLocalRecordAction(state, settings, record);
        String message = String.format(state.getMessages().getString("delete.record.children.error"), recordId.getBibliographicRecordId());
        assertThat(updateLocalRecordAction.performAction(), is(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message)));
        verify(state.getRawRepo(), never()).recordExists(anyString(), anyInt());
    }

    /**
     * Test UpdateLocalRecordAction.performAction(): Delete volume record.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A empty rawrepo.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Delete a record that has children.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>RemoveLinksAction: Remove any existing links to other records</li>
     * <li>DeleteRecordAction: Deletes the record</li>
     * <li>EnqueueRecordAction: Put the record in queue</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_DeleteVolumeRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        new MarcRecordWriter(record).markForDeletion();
        final RecordId recordId = AssertActionsUtil.getRecordId(record);

        when(state.getRawRepo().children(eq(recordId))).thenReturn(new HashSet<>());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record)).thenReturn(new HashSet<>());

        UpdateLocalRecordAction updateLocalRecordAction = new UpdateLocalRecordAction(state, settings, record);
        assertThat(updateLocalRecordAction.performAction(), is(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = updateLocalRecordAction.children().listIterator();
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertDeleteRecordAction(iterator.next(), state.getRawRepo(), record, MarcXChangeMimeType.MARCXCHANGE);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_FBS), MarcXChangeMimeType.MARCXCHANGE);
        assertThat(iterator.hasNext(), is(false));
    }

    /**
     * Test UpdateLocalRecordAction.performAction(): Delete the last volume record.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A raw repo with two records:
     * <ol>
     * <li>Main record <code>m</code></li>
     * <li>Volume record <code>v</code></li>
     * </ol>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Delete the record <code>v</code>
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>RemoveLinksAction: Remove any existing links to other records</li>
     * <li>DeleteRecordAction: Deletes the record</li>
     * <li>EnqueueRecordAction: Put the record in queue</li>
     * <li>UpdateLocalRecordAction: Delete the main record <code>m</code></li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_DeleteLastVolumeRecord() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_MAIN_RECORD_RESOURCE);
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_VOLUME_RECORD_RESOURCE);
        new MarcRecordWriter(record).markForDeletion();
        String mainRecordId = AssertActionsUtil.getBibliographicRecordId(mainRecord);
        String bibliographicRecordId = AssertActionsUtil.getBibliographicRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);
        final RecordId recordId = AssertActionsUtil.getRecordId(record);

        when(state.getRawRepo().recordExists(eq(mainRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(bibliographicRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(mainRecordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(mainRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecord(eq(bibliographicRecordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().children(eq(new RecordId(mainRecordId, agencyId)))).thenReturn(AssertActionsUtil.createRecordSet(record));
        when(state.getRawRepo().children(eq(recordId))).thenReturn(AssertActionsUtil.createRecordSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(mainRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record)).thenReturn(AssertActionsUtil.createAgenciesSet());

        UpdateLocalRecordAction updateLocalRecordAction = new UpdateLocalRecordAction(state, settings, record);
        assertThat(updateLocalRecordAction.performAction(), is(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = updateLocalRecordAction.children().listIterator();
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertDeleteRecordAction(iterator.next(), state.getRawRepo(), record, MarcXChangeMimeType.MARCXCHANGE);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_FBS), MarcXChangeMimeType.MARCXCHANGE);

        new MarcRecordWriter(mainRecord).markForDeletion();
        AssertActionsUtil.assertUpdateLocalRecordAction(iterator.next(), state.getRawRepo(), mainRecord, state.getHoldingsItems());
        assertThat(iterator.hasNext(), is(false));
    }

    /**
     * Test UpdateLocalRecordAction.performAction(): Delete volume record that has holdings.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * Holdings for the updated volume record.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Delete the record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return status: FAILED_VALIDATION_INTERNAL_ERROR.
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_DeleteVolumeRecord_WithHoldings() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        AgencyNumber agencyId = new AgencyNumber(new MarcRecordReader(record).getAgencyIdAsInt());
        new MarcRecordWriter(record).markForDeletion();
        final RecordId recordId = AssertActionsUtil.getRecordId(record);

        Set<Integer> holdings = new HashSet<>();
        holdings.add(agencyId.getAgencyId());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(eq(record))).thenReturn(holdings);
        when(state.getVipCoreService().hasFeature(agencyId.toString(), VipCoreLibraryRulesConnector.Rule.AUTH_EXPORT_HOLDINGS)).thenReturn(true);

        UpdateLocalRecordAction updateLocalRecordAction = new UpdateLocalRecordAction(state, settings, record);
        when(state.getRawRepo().children(eq(recordId))).thenReturn(new HashSet<>());
        String message = state.getMessages().getString("delete.local.with.holdings.error");
        assertThat(updateLocalRecordAction.performAction(), is(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message)));
    }

    /**
     * Test UpdateLocalRecordAction.performAction(): Delete volume record that has holdings and
     * with no export of holdings the agency base.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * <ol>
     * <li>Holdings for the updated volume record.</li>
     * <li>Agency base has the setting "export of holdings" to false.</li>
     * </ol>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Delete the record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>RemoveLinksAction: Remove any existing links to other records</li>
     * <li>DeleteRecordAction: Deletes the record</li>
     * <li>EnqueueRecordAction: Put the record in queue</li>
     * <li>UpdateLocalRecordAction: Delete the main record <code>m</code></li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_DeleteVolumeRecord_WithHoldings_DoesNotExportHoldings() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        AgencyNumber agencyId = new AgencyNumber(new MarcRecordReader(record).getAgencyIdAsInt());
        new MarcRecordWriter(record).markForDeletion();
        final RecordId recordId = AssertActionsUtil.getRecordId(record);

        when(state.getRawRepo().children(eq(recordId))).thenReturn(new HashSet<>());
        Set<Integer> holdings = new HashSet<>();
        holdings.add(agencyId.getAgencyId());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(eq(record))).thenReturn(holdings);
        when(state.getVipCoreService().hasFeature(agencyId.toString(), VipCoreLibraryRulesConnector.Rule.AUTH_EXPORT_HOLDINGS)).thenReturn(false);

        UpdateLocalRecordAction updateLocalRecordAction = new UpdateLocalRecordAction(state, settings, record);
        assertThat(updateLocalRecordAction.performAction(), is(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = updateLocalRecordAction.children().listIterator();
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertDeleteRecordAction(iterator.next(), state.getRawRepo(), record, MarcXChangeMimeType.MARCXCHANGE);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_FBS), MarcXChangeMimeType.MARCXCHANGE);
    }

    /**
     * Test UpdateLocalRecordAction.performAction(): Delete single record.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * Nothing.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Delete a single record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>StoreRecordAction: Store the record</li>
     * <li>RemoveLinksAction: Remove any existing links to other records</li>
     * <li>EnqueueRecordAction: Put the record in queue</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_DeleteSingleRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(record).markForDeletion();
        final RecordId recordId = AssertActionsUtil.getRecordId(record);

        when(state.getRawRepo().children(eq(recordId))).thenReturn(new HashSet<>());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record)).thenReturn(new HashSet<>());

        UpdateLocalRecordAction instance = new UpdateLocalRecordAction(state, settings, record);
        assertThat(instance.performAction(), is(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertDeleteRecordAction(iterator.next(), state.getRawRepo(), record, MarcXChangeMimeType.MARCXCHANGE);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_FBS), MarcXChangeMimeType.MARCXCHANGE);
    }

    /**
     * Test UpdateLocalRecordAction.performAction(): Delete single record that has holdings.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A record with holdings.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Delete the record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return status: FAILED_UPDATE_INTERNAL_ERROR
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_DeleteSingleRecord_WithHoldings() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        AgencyNumber agencyId = new AgencyNumber(new MarcRecordReader(record).getAgencyIdAsInt());
        new MarcRecordWriter(record).markForDeletion();
        final RecordId recordId = AssertActionsUtil.getRecordId(record);

        Set<Integer> holdings = new HashSet<>();
        holdings.add(agencyId.getAgencyId());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record)).thenReturn(holdings);
        when(state.getVipCoreService().hasFeature(agencyId.toString(), VipCoreLibraryRulesConnector.Rule.AUTH_EXPORT_HOLDINGS)).thenReturn(true);

        UpdateLocalRecordAction instance = new UpdateLocalRecordAction(state, settings, record);
        when(state.getRawRepo().children(eq(recordId))).thenReturn(new HashSet<>());
        String message = state.getMessages().getString("delete.local.with.holdings.error");
        assertThat(instance.performAction(), is(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message)));
    }

    /**
     * Test UpdateLocalRecordAction.performAction(): Delete record that has holdings and
     * with no export of holdings the agency base.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * <ol>
     * <li>Holdings for the updated record.</li>
     * <li>Agency base has the setting "export of holdings" to false.</li>
     * </ol>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Delete the record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>StoreRecordAction: Store the record</li>
     * <li>RemoveLinksAction: Remove any existing links to other records</li>
     * <li>EnqueueRecordAction: Put the record in queue</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_DeleteSingleRecord_WithHoldings_DoesNotExportHoldings() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        AgencyNumber agencyId = new AgencyNumber(new MarcRecordReader(record).getAgencyIdAsInt());
        new MarcRecordWriter(record).markForDeletion();
        final RecordId recordId = AssertActionsUtil.getRecordId(record);

        when(state.getRawRepo().children(eq(recordId))).thenReturn(new HashSet<>());
        Set<Integer> holdings = new HashSet<>();
        holdings.add(agencyId.getAgencyId());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record)).thenReturn(holdings);
        when(state.getVipCoreService().hasFeature(agencyId.toString(), VipCoreLibraryRulesConnector.Rule.AUTH_EXPORT_HOLDINGS)).thenReturn(false);

        UpdateLocalRecordAction instance = new UpdateLocalRecordAction(state, settings, record);
        assertThat(instance.performAction(), is(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertDeleteRecordAction(iterator.next(), state.getRawRepo(), record, MarcXChangeMimeType.MARCXCHANGE);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_FBS), MarcXChangeMimeType.MARCXCHANGE);
    }
}
