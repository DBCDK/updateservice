package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordWriter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.ws.JNDIResources;
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

public class OverwriteVolumeRecordActionTest {
    private GlobalActionState state;
    private Properties settings;

    @Before
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
        settings = new UpdateTestUtils().getSettings();
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
     * <li>StoreRecordAction: Store the record</li>
     * <li>RemoveLinksAction: Remove any existing links to other records</li>
     * <li>EnqueueRecordAction: Put the record in queue</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_NoClassifications() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String mainRecordId = AssertActionsUtil.getRecordId(mainRecord);
        Integer agencyId = AssertActionsUtil.getAgencyIdAsInteger(mainRecord);

        MarcRecord volumeRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        String volumeRecordId = AssertActionsUtil.getRecordId(volumeRecord);

        when(state.getRawRepo().recordExists(eq(mainRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(volumeRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(volumeRecordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(volumeRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(eq(volumeRecord))).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(mainRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(volumeRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getLibraryRecordsHandler().hasClassificationData(volumeRecord)).thenReturn(false);

        OverwriteVolumeRecordAction overwriteVolumeRecordAction = new OverwriteVolumeRecordAction(state, settings, volumeRecord);
        assertThat(overwriteVolumeRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteVolumeRecordAction.children();
        Assert.assertThat(children.size(), is(4));

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertLinkRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, mainRecord);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID), MarcXChangeMimeType.MARCXCHANGE);
    }

    /**
     * Test performAction(): Update single common record without changes to
     * its current classifications.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with a single common record and a DBC enrichment
     * record. The common record has classifications.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update the common record, with changed data except for
     * classifications.
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
    public void testPerformAction_SameClassifications() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String mainRecordId = AssertActionsUtil.getRecordId(mainRecord);
        Integer agencyId = AssertActionsUtil.getAgencyIdAsInteger(mainRecord);

        MarcRecord volumeRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        String volumeRecordId = AssertActionsUtil.getRecordId(volumeRecord);

        when(state.getRawRepo().recordExists(eq(mainRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(volumeRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(volumeRecordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(volumeRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(eq(volumeRecord))).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(mainRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(volumeRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(volumeRecord))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(volumeRecord), eq(volumeRecord))).thenReturn(false);

        OverwriteVolumeRecordAction overwriteVolumeRecordAction = new OverwriteVolumeRecordAction(state, settings, volumeRecord);
        assertThat(overwriteVolumeRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteVolumeRecordAction.children();
        Assert.assertThat(children.size(), is(4));

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertLinkRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, mainRecord);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID), MarcXChangeMimeType.MARCXCHANGE);
    }

    /**
     * Test performAction(): Update single common record with changes to
     * its current classifications, but with no holdings for local libraries.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with a single common record and a DBC enrichment
     * record. The common record has classifications.
     * <p>
     * No holdings for local libraries.
     * </p>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update the common record, with changed classifications.
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
    public void testPerformAction_ChangedClassifications_NoHoldings() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String mainRecordId = AssertActionsUtil.getRecordId(mainRecord);
        Integer agencyId = AssertActionsUtil.getAgencyIdAsInteger(mainRecord);


        MarcRecord volumeRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        String volumeRecordId = AssertActionsUtil.getRecordId(volumeRecord);

        when(state.getRawRepo().recordExists(eq(mainRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(volumeRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(volumeRecordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(volumeRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(eq(volumeRecord))).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(mainRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(volumeRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(volumeRecord))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(volumeRecord), eq(volumeRecord))).thenReturn(true);

        OverwriteVolumeRecordAction overwriteVolumeRecordAction = new OverwriteVolumeRecordAction(state, settings, volumeRecord);
        assertThat(overwriteVolumeRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteVolumeRecordAction.children();
        Assert.assertThat(children.size(), is(4));

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertLinkRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, mainRecord);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID), MarcXChangeMimeType.MARCXCHANGE);
    }

    /**
     * Test performAction(): Update single common record with changes to
     * its current classifications, and with holdings for a local library.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with a single common record and a DBC enrichment
     * record. The common record has classifications.
     * <p>
     * Holdings for a local library.
     * </p>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update the common record, with changed classifications.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>StoreRecordAction: Store the record</li>
     * <li>RemoveLinksAction: Remove any existing links to other records</li>
     * <li>
     * CreateEnrichmentRecordWithClassificationsAction: Create enrichment record for
     * the local library with holdings.
     * </li>
     * <li>EnqueueRecordAction: Put the record in queue</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_ChangedClassifications_Holdings_CreateEnrichment() throws Exception {
        String groupId = "700100";
        state.getUpdateServiceRequestDto().getAuthenticationDto().setGroupId("700000");
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String mainRecordId = AssertActionsUtil.getRecordId(mainRecord);
        Integer agencyId = AssertActionsUtil.getAgencyIdAsInteger(mainRecord);
        MarcRecord volumeRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        String volumeRecordId = AssertActionsUtil.getRecordId(volumeRecord);
        when(state.getRawRepo().recordExists(eq(mainRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(volumeRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(volumeRecordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(volumeRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(eq(volumeRecord))).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(mainRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(volumeRecord)).thenReturn(AssertActionsUtil.createAgenciesSet(Integer.valueOf(groupId)));
        when(state.getOpenAgencyService().hasFeature(eq(groupId), eq(LibraryRuleHandler.Rule.USE_ENRICHMENTS))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(volumeRecord))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(volumeRecord), eq(volumeRecord))).thenReturn(true);

        OverwriteVolumeRecordAction overwriteVolumeRecordAction = new OverwriteVolumeRecordAction(state, settings, volumeRecord);
        assertThat(overwriteVolumeRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteVolumeRecordAction.children();
        Assert.assertThat(children.size(), is(5));

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertLinkRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, mainRecord);
        AssertActionsUtil.assertCreateEnrichmentAction(iterator.next(), state.getRawRepo(), volumeRecord, groupId, null);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID), MarcXChangeMimeType.MARCXCHANGE);
    }

    /**
     * Test performAction(): Update single common record with changes to
     * its current classifications, and with enrichment for a local library.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with a single common record and a DBC enrichment
     * record. The common record has classifications.
     * <p>
     * Enrichment record for a local library.
     * </p>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update the common record, with changed classifications.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>StoreRecordAction: Store the record</li>
     * <li>RemoveLinksAction: Remove any existing links to other records</li>
     * <li>
     * UpdateClassificationsInEnrichmentRecordAction: Update enrichment record with
     * new classifications.
     * </li>
     * <li>EnqueueRecordAction: Put the record in queue</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_ChangedClassifications_Holdings_UpdateEnrichment() throws Exception {
        state.getUpdateServiceRequestDto().getAuthenticationDto().setGroupId("700000");
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String mainRecordId = AssertActionsUtil.getRecordId(mainRecord);
        Integer agencyId = AssertActionsUtil.getAgencyIdAsInteger(mainRecord);
        MarcRecord volumeRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        String volumeRecordId = AssertActionsUtil.getRecordId(volumeRecord);
        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(enrichmentRecord).addOrReplaceSubfield("001", "a", volumeRecordId);
        Integer enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInteger(enrichmentRecord);

        when(state.getRawRepo().recordExists(eq(mainRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(volumeRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(volumeRecordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(volumeRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(eq(volumeRecord))).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getRawRepo().recordExists(eq(volumeRecordId), eq(enrichmentAgencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(volumeRecordId), eq(enrichmentAgencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(enrichmentRecord, MarcXChangeMimeType.ENRICHMENT));
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(mainRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(volumeRecord)).thenReturn(AssertActionsUtil.createAgenciesSet(enrichmentAgencyId));
        when(state.getOpenAgencyService().hasFeature(eq(enrichmentAgencyId.toString()), eq(LibraryRuleHandler.Rule.USE_ENRICHMENTS))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(volumeRecord))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(volumeRecord), eq(volumeRecord))).thenReturn(true);

        OverwriteVolumeRecordAction overwriteVolumeRecordAction = new OverwriteVolumeRecordAction(state, settings, volumeRecord);
        assertThat(overwriteVolumeRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteVolumeRecordAction.children();
        Assert.assertThat(children.size(), is(5));

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertLinkRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, mainRecord);
        AssertActionsUtil.assertUpdateClassificationsInEnrichmentRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, enrichmentRecord);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID), MarcXChangeMimeType.MARCXCHANGE);
    }

    /**
     * Test performAction(): Update single common record with changes to
     * its current classifications, and creation/update of enrichments.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * <ol>
     * <li>
     * A rawrepo with a single common record with classifications
     * </li>
     * <li>A DBC enrichment</li>
     * <li>
     * Holdings for a local library, <code>l1</code>, with no enrichment for
     * the updated record.
     * </li>
     * <li>
     * Enrichment for another library, <code>l2</code>.
     * </li>
     * </ol>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update the common record, with changed classifications.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>StoreRecordAction: Store the record</li>
     * <li>RemoveLinksAction: Remove any existing links to other records</li>
     * <li>
     * CreateEnrichmentRecordWithClassificationsAction: Create enrichment record with
     * new classifications for library <code>l1</code>.
     * </li>
     * <li>
     * UpdateClassificationsInEnrichmentRecordAction: Update enrichment record with
     * new classifications for library <code>l2</code>.
     * </li>
     * <li>EnqueueRecordAction: Put the record in queue</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_ChangedClassifications_Holdings_CreateAndUpdateEnrichment() throws Exception {
        state.getUpdateServiceRequestDto().getAuthenticationDto().setGroupId("700000");
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String mainRecordId = AssertActionsUtil.getRecordId(mainRecord);
        Integer agencyId = AssertActionsUtil.getAgencyIdAsInteger(mainRecord);
        MarcRecord volumeRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        String volumeRecordId = AssertActionsUtil.getRecordId(volumeRecord);
        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(enrichmentRecord).addOrReplaceSubfield("001", "a", volumeRecordId);
        Integer enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInteger(enrichmentRecord);

        when(state.getRawRepo().recordExists(eq(mainRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(volumeRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(volumeRecordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(volumeRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(eq(volumeRecord))).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getRawRepo().recordExists(eq(volumeRecordId), eq(enrichmentAgencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(volumeRecordId), eq(enrichmentAgencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(enrichmentRecord, MarcXChangeMimeType.ENRICHMENT));
        Integer newEnrichmentAgencyId = enrichmentAgencyId + 100;
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(mainRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(volumeRecord)).thenReturn(AssertActionsUtil.createAgenciesSet(enrichmentAgencyId, newEnrichmentAgencyId));
        when(state.getOpenAgencyService().hasFeature(eq(enrichmentAgencyId.toString()), eq(LibraryRuleHandler.Rule.USE_ENRICHMENTS))).thenReturn(true);
        when(state.getOpenAgencyService().hasFeature(eq(newEnrichmentAgencyId.toString()), eq(LibraryRuleHandler.Rule.USE_ENRICHMENTS))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(volumeRecord))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(volumeRecord), eq(volumeRecord))).thenReturn(true);

        OverwriteVolumeRecordAction overwriteVolumeRecordAction = new OverwriteVolumeRecordAction(state, settings, volumeRecord);
        assertThat(overwriteVolumeRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteVolumeRecordAction.children();
        Assert.assertThat(children.size(), is(6));

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertLinkRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, mainRecord);
        AssertActionsUtil.assertUpdateClassificationsInEnrichmentRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, enrichmentRecord);
        AssertActionsUtil.assertCreateEnrichmentAction(iterator.next(), state.getRawRepo(), volumeRecord, newEnrichmentAgencyId.toString(), null);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID), MarcXChangeMimeType.MARCXCHANGE);
    }

    /**
     * Test performAction(): Update volume common record with no changes to
     * its current classifications and with new 002 to an existing common
     * record.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with this content:
     * <ol>
     * <li>
     * A common record <code>c1</code>. This record is to be updated.
     * <p>
     * No holdings to local libraries.
     * </p>
     * </li>
     * <li>
     * A common record <code>c2</code>. This record is a duplicate of
     * <code>c1</code> but with a different faust-id.
     * </li>
     * <li>
     * An enrichment record <code>e1</code>, that points to <code>c2</code>.
     * </li>
     * </ol>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update the common record, with changed classifications.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>StoreRecordAction: Store the record</li>
     * <li>RemoveLinksAction: Remove any existing links to other records</li>
     * <li>LinkRecordAction: Create link to parent record</li>
     * <li>
     * MoveEnrichmentRecordAction: Move enrichment record <code>e1</code> from
     * <code>c2</code> to <code>c1</code>.
     * </li>
     * <li>EnqueueRecordAction: Put the record in queue</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_SameClassifications_MoveEnrichments() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String mainRecordId = AssertActionsUtil.getRecordId(mainRecord);
        final String v1RecordId = "1 234 567 8";
        final String v2RecordId = "2 345 678 9";
        MarcRecord v1 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE, v1RecordId);
        MarcRecord v2 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE, v2RecordId);
        MarcRecord e1 = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE, v2RecordId);
        String e1RecordId = AssertActionsUtil.getRecordId(e1);
        Integer e1AgencyId = AssertActionsUtil.getAgencyIdAsInteger(e1);

        MarcRecord record = new MarcRecord(v1);
        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.addOrReplaceSubfield("002", "a", v2RecordId);
        writer.sort();

        when(state.getRawRepo().recordExists(eq(mainRecordId), eq(RawRepo.RAWREPO_COMMON_LIBRARY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(v1RecordId), eq(RawRepo.RAWREPO_COMMON_LIBRARY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(v2RecordId), eq(RawRepo.RAWREPO_COMMON_LIBRARY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(e1RecordId), eq(e1AgencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(v1RecordId), eq(RawRepo.RAWREPO_COMMON_LIBRARY))).thenReturn(AssertActionsUtil.createRawRepoRecord(v1, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecord(eq(v2RecordId), eq(RawRepo.RAWREPO_COMMON_LIBRARY))).thenReturn(AssertActionsUtil.createRawRepoRecord(v2, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecord(eq(e1RecordId), eq(e1AgencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(e1, MarcXChangeMimeType.ENRICHMENT));
        when(state.getRawRepo().agenciesForRecord(eq(v1))).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getRawRepo().enrichments(eq(new RecordId(v2RecordId, RawRepo.RAWREPO_COMMON_LIBRARY)))).thenReturn(AssertActionsUtil.createRecordSet(e1));
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(mainRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(v1)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getOpenAgencyService().hasFeature(eq(e1AgencyId.toString()), eq(LibraryRuleHandler.Rule.USE_ENRICHMENTS))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(v1))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(record))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(v1), eq(record))).thenReturn(false);

        OverwriteVolumeRecordAction overwriteVolumeRecordAction = new OverwriteVolumeRecordAction(state, settings, record);
        assertThat(overwriteVolumeRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = overwriteVolumeRecordAction.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkRecordAction(iterator.next(), state.getRawRepo(), record, mainRecord);
        AssertActionsUtil.assertMoveEnrichmentRecordAction(iterator.next(), state.getRawRepo(), e1, record, settings);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID), MarcXChangeMimeType.MARCXCHANGE);
        assertThat(iterator.hasNext(), is(false));
    }
}
