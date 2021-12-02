/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.update.LibraryGroup;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.vipcore.libraryrules.VipCoreLibraryRulesConnector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class OverwriteVolumeRecordActionTest {
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
    void testPerformAction_NoClassifications() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String mainRecordId = AssertActionsUtil.getBibliographicRecordId(mainRecord);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(mainRecord);

        MarcRecord volumeRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        String volumeRecordId = AssertActionsUtil.getBibliographicRecordId(volumeRecord);

        Map<String, MarcRecord> recordCollection = new HashMap<>();
        recordCollection.put(volumeRecordId, volumeRecord);

        state.setMarcRecord(volumeRecord);
        when(state.getRawRepo().recordExists(eq(mainRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(volumeRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(volumeRecordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(volumeRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(eq(volumeRecordId))).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getRawRepo().fetchRecordCollection(eq(volumeRecordId), eq(agencyId))).thenReturn(recordCollection);
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(mainRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(volumeRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getLibraryRecordsHandler().hasClassificationData(volumeRecord)).thenReturn(false);

        OverwriteVolumeRecordAction overwriteVolumeRecordAction = new OverwriteVolumeRecordAction(state, settings, volumeRecord);
        assertThat(overwriteVolumeRecordAction.performAction(), is(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteVolumeRecordAction.children();
        assertThat(children.size(), is(5));

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertLinkRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, mainRecord);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
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
    void testPerformAction_SameClassifications() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String mainRecordId = AssertActionsUtil.getBibliographicRecordId(mainRecord);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(mainRecord);

        MarcRecord volumeRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        String volumeRecordId = AssertActionsUtil.getBibliographicRecordId(volumeRecord);

        Map<String, MarcRecord> recordCollection = new HashMap<>();
        recordCollection.put(volumeRecordId, volumeRecord);

        state.setMarcRecord(volumeRecord);
        when(state.getRawRepo().recordExists(eq(mainRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(volumeRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(volumeRecordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(volumeRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(eq(volumeRecordId))).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getRawRepo().fetchRecordCollection(eq(volumeRecordId), eq(agencyId))).thenReturn(recordCollection);
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(mainRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(volumeRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(volumeRecord))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(volumeRecord), eq(volumeRecord))).thenReturn(false);

        OverwriteVolumeRecordAction overwriteVolumeRecordAction = new OverwriteVolumeRecordAction(state, settings, volumeRecord);
        assertThat(overwriteVolumeRecordAction.performAction(), is(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteVolumeRecordAction.children();
        assertThat(children.size(), is(5));

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertLinkRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, mainRecord);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
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
    void testPerformAction_ChangedClassifications_NoHoldings() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String mainRecordId = AssertActionsUtil.getBibliographicRecordId(mainRecord);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(mainRecord);

        MarcRecord volumeRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        String volumeRecordId = AssertActionsUtil.getBibliographicRecordId(volumeRecord);

        Map<String, MarcRecord> recordCollection = new HashMap<>();
        recordCollection.put(volumeRecordId, volumeRecord);

        state.setMarcRecord(volumeRecord);
        when(state.getRawRepo().recordExists(eq(mainRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(volumeRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(volumeRecordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(volumeRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(eq(volumeRecordId))).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getRawRepo().fetchRecordCollection(eq(volumeRecordId), eq(agencyId))).thenReturn(recordCollection);
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(mainRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(volumeRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(volumeRecord))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(volumeRecord), eq(volumeRecord))).thenReturn(true);

        OverwriteVolumeRecordAction overwriteVolumeRecordAction = new OverwriteVolumeRecordAction(state, settings, volumeRecord);
        assertThat(overwriteVolumeRecordAction.performAction(), is(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteVolumeRecordAction.children();
        assertThat(children.size(), is(5));

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertLinkRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, mainRecord);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
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
    void testPerformAction_ChangedClassifications_Holdings_CreateEnrichment() throws Exception {
        String groupId = "700100";
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId("700000");
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String mainRecordId = AssertActionsUtil.getBibliographicRecordId(mainRecord);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(mainRecord);
        MarcRecord volumeRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        String volumeRecordId = AssertActionsUtil.getBibliographicRecordId(volumeRecord);

        Map<String, MarcRecord> recordCollection = new HashMap<>();
        recordCollection.put(volumeRecordId, volumeRecord);

        state.setMarcRecord(volumeRecord);
        when(state.getRawRepo().recordExists(eq(mainRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(volumeRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(volumeRecordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(volumeRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(eq(volumeRecordId))).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getRawRepo().fetchRecordCollection(eq(volumeRecordId), eq(agencyId))).thenReturn(recordCollection);
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(mainRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(volumeRecord)).thenReturn(AssertActionsUtil.createAgenciesSet(Integer.valueOf(groupId)));
        when(state.getVipCoreService().hasFeature(eq(groupId), eq(VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(volumeRecord))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(volumeRecord), eq(volumeRecord))).thenReturn(true);

        OverwriteVolumeRecordAction overwriteVolumeRecordAction = new OverwriteVolumeRecordAction(state, settings, volumeRecord);
        assertThat(overwriteVolumeRecordAction.performAction(), is(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteVolumeRecordAction.children();
        assertThat(children.size(), is(6));

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertLinkRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, mainRecord);
        AssertActionsUtil.assertCreateEnrichmentAction(iterator.next(), state.getRawRepo(), volumeRecord, groupId, null);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
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
    void testPerformAction_ChangedClassifications_Holdings_UpdateEnrichment() throws Exception {
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId("700000");
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String mainRecordId = AssertActionsUtil.getBibliographicRecordId(mainRecord);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(mainRecord);
        MarcRecord volumeRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        String volumeRecordId = AssertActionsUtil.getBibliographicRecordId(volumeRecord);
        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(enrichmentRecord).addOrReplaceSubfield("001", "a", volumeRecordId);
        int enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInt(enrichmentRecord);

        Map<String, MarcRecord> recordCollection = new HashMap<>();
        recordCollection.put(volumeRecordId, volumeRecord);

        state.setMarcRecord(volumeRecord);
        when(state.getRawRepo().recordExists(eq(mainRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(volumeRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(volumeRecordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(volumeRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(eq(volumeRecordId))).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getRawRepo().recordExists(eq(volumeRecordId), eq(enrichmentAgencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(volumeRecordId), eq(enrichmentAgencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(enrichmentRecord, MarcXChangeMimeType.ENRICHMENT));
        when(state.getRawRepo().fetchRecordCollection(eq(volumeRecordId), eq(agencyId))).thenReturn(recordCollection);
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(mainRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(volumeRecord)).thenReturn(AssertActionsUtil.createAgenciesSet(enrichmentAgencyId));
        when(state.getVipCoreService().hasFeature(eq(Integer.toString(enrichmentAgencyId)), eq(VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(volumeRecord))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(volumeRecord), eq(volumeRecord))).thenReturn(true);

        OverwriteVolumeRecordAction overwriteVolumeRecordAction = new OverwriteVolumeRecordAction(state, settings, volumeRecord);
        assertThat(overwriteVolumeRecordAction.performAction(), is(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteVolumeRecordAction.children();
        assertThat(children.size(), is(6));

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertLinkRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, mainRecord);
        AssertActionsUtil.assertUpdateClassificationsInEnrichmentRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, enrichmentRecord);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
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
    void testPerformAction_ChangedClassifications_Holdings_CreateAndUpdateEnrichment() throws Exception {
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId("700000");
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String mainRecordId = AssertActionsUtil.getBibliographicRecordId(mainRecord);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(mainRecord);
        MarcRecord volumeRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        String volumeRecordId = AssertActionsUtil.getBibliographicRecordId(volumeRecord);
        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(enrichmentRecord).addOrReplaceSubfield("001", "a", volumeRecordId);
        int enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInt(enrichmentRecord);

        Map<String, MarcRecord> recordCollection = new HashMap<>();
        recordCollection.put(volumeRecordId, volumeRecord);

        state.setMarcRecord(volumeRecord);
        when(state.getRawRepo().recordExists(eq(mainRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(volumeRecordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(volumeRecordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(volumeRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(eq(volumeRecordId))).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getRawRepo().recordExists(eq(volumeRecordId), eq(enrichmentAgencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(volumeRecordId), eq(enrichmentAgencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(enrichmentRecord, MarcXChangeMimeType.ENRICHMENT));
        when(state.getRawRepo().fetchRecordCollection(eq(volumeRecordId), eq(agencyId))).thenReturn(recordCollection);
        int newEnrichmentAgencyId = enrichmentAgencyId + 100;
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(mainRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(volumeRecord)).thenReturn(AssertActionsUtil.createAgenciesSet(enrichmentAgencyId, newEnrichmentAgencyId));
        when(state.getVipCoreService().hasFeature(eq(Integer.toString(enrichmentAgencyId)), eq(VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS))).thenReturn(true);
        when(state.getVipCoreService().hasFeature(eq(Integer.toString(newEnrichmentAgencyId)), eq(VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(volumeRecord))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(volumeRecord), eq(volumeRecord))).thenReturn(true);

        OverwriteVolumeRecordAction overwriteVolumeRecordAction = new OverwriteVolumeRecordAction(state, settings, volumeRecord);
        assertThat(overwriteVolumeRecordAction.performAction(), is(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteVolumeRecordAction.children();
        assertThat(children.size(), is(7));

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertLinkRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, mainRecord);
        AssertActionsUtil.assertUpdateClassificationsInEnrichmentRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, enrichmentRecord);
        AssertActionsUtil.assertCreateEnrichmentAction(iterator.next(), state.getRawRepo(), volumeRecord, Integer.toString(newEnrichmentAgencyId), null);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), volumeRecord);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), volumeRecord, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
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
    void testPerformAction_SameClassifications_MoveEnrichments() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String mainRecordId = AssertActionsUtil.getBibliographicRecordId(mainRecord);
        final String v1RecordId = "1 234 567 8";
        final String v2RecordId = "2 345 678 9";
        MarcRecord v1 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE, v1RecordId);
        MarcRecord v2 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE, v2RecordId);
        MarcRecord e1 = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE, v2RecordId);
        String e1RecordId = AssertActionsUtil.getBibliographicRecordId(e1);
        int e1AgencyId = AssertActionsUtil.getAgencyIdAsInt(e1);

        MarcRecord record = new MarcRecord(v1);
        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.addOrReplaceSubfield("002", "a", v2RecordId);
        writer.sort();

        Map<String, MarcRecord> recordCollection = new HashMap<>();
        recordCollection.put(v1RecordId, record);

        state.setMarcRecord(record);
        when(state.getRawRepo().recordExists(eq(mainRecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(v1RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(v2RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(e1RecordId), eq(e1AgencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(v1RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(v1, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecord(eq(v2RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(v2, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecord(eq(e1RecordId), eq(e1AgencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(e1, MarcXChangeMimeType.ENRICHMENT));
        when(state.getRawRepo().agenciesForRecord(eq(v1RecordId))).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getRawRepo().enrichments(eq(new RecordId(v2RecordId, RawRepo.COMMON_AGENCY)))).thenReturn(AssertActionsUtil.createRecordSet(e1));
        when(state.getRawRepo().fetchRecordCollection(eq(v1RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(recordCollection);
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(mainRecord)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(v1)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getVipCoreService().hasFeature(eq(Integer.toString(e1AgencyId)), eq(VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(v1))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(record))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(v1), eq(record))).thenReturn(false);

        OverwriteVolumeRecordAction overwriteVolumeRecordAction = new OverwriteVolumeRecordAction(state, settings, record);
        assertThat(overwriteVolumeRecordAction.performAction(), is(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = overwriteVolumeRecordAction.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkRecordAction(iterator.next(), state.getRawRepo(), record, mainRecord);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
        assertThat(iterator.hasNext(), is(false));
    }
}
