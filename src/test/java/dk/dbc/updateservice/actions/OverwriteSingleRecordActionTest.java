package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.update.LibraryGroup;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.update.UpdateRecordContentTransformer;
import dk.dbc.vipcore.libraryrules.VipCoreLibraryRulesConnector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

class OverwriteSingleRecordActionTest {
    private GlobalActionState state;
    private Properties settings;
    LibraryGroup libraryGroup = LibraryGroup.FBS;

    @BeforeEach
    public void before() throws IOException, UpdateException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId("700000");
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
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getBibliographicRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        Map<String, MarcRecord> recordCollection = new HashMap<>();
        recordCollection.put(recordId, record);

        state.setMarcRecord(record);
        when(state.getRawRepo().recordExists(recordId, agencyId)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(recordId, agencyId)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(recordId)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getRawRepo().fetchRecordCollection(recordId, agencyId)).thenReturn(recordCollection);
        when(state.getHoldingsItems().getAgenciesWithHoldings(recordId)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getVipCoreService().hasFeature(Integer.toString(agencyId), VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(record)).thenReturn(false);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), is(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteSingleRecordAction.children();
        assertThat(children.size(), is(4));

        AssertActionsUtil.assertStoreRecordAction(children.get(0), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(children.get(1), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(children.get(2), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(children.get(3), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()));
    }

    @Test
    void testPerformAction_MatVurd() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.MATVURD_1);
        // The MATVURD_1 record contains r01 and r02 fields.
        // But when the record is send to updateservice the record is split into common part and enrichment part.
        // Only the common part (without letter fields) are passed to the OverwriteSingleRecordAction.
        // In order to test that the LinkMatVurdRecordsAction is given the original record we have to remove the letter
        // fields from the original record first and use that record as input to OverwriteSingleRecordAction
        final MarcRecord recordWithoutEnrichmentFields = new MarcRecord(record);
        final MarcRecordWriter writer = new MarcRecordWriter(recordWithoutEnrichmentFields);
        writer.removeFields(Arrays.asList("r01", "r02"));

        final String recordId = AssertActionsUtil.getBibliographicRecordId(record);
        final int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        final Map<String, MarcRecord> recordCollection = new HashMap<>();
        recordCollection.put(recordId, record);

        state.setMarcRecord(record); // <- Important! The original record is set on the state object
        when(state.getRawRepo().recordExists(recordId, agencyId)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(recordId, agencyId)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(recordId)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getRawRepo().fetchRecordCollection(recordId, agencyId)).thenReturn(recordCollection);
        when(state.getHoldingsItems().getAgenciesWithHoldings(recordId)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getVipCoreService().hasFeature(Integer.toString(agencyId), VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(record)).thenReturn(false);

        final OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, recordWithoutEnrichmentFields);
        assertThat(overwriteSingleRecordAction.performAction(), is(ServiceResult.newOkResult()));

        final List<ServiceAction> children = overwriteSingleRecordAction.children();
        assertThat(children.size(), is(5));

        AssertActionsUtil.assertStoreRecordAction(children.get(0), state.getRawRepo(), recordWithoutEnrichmentFields, MarcXChangeMimeType.MATVURD);
        AssertActionsUtil.assertRemoveLinksAction(children.get(1), state.getRawRepo(), recordWithoutEnrichmentFields);
        AssertActionsUtil.assertLinkMatVurdRecordsAction(children.get(2), state.getRawRepo(), record); // <- Here we assert the correct record is used by LinkMatVurdRecordsAction
        AssertActionsUtil.assertLinkAuthorityRecordsAction(children.get(3), state.getRawRepo(), recordWithoutEnrichmentFields);
        AssertActionsUtil.assertEnqueueRecordAction(children.get(4), state.getRawRepo(), recordWithoutEnrichmentFields, settings.getProperty(state.getRawRepoProviderId()));
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
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getBibliographicRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        Map<String, MarcRecord> recordCollection = new HashMap<>();
        recordCollection.put(recordId, record);

        state.setMarcRecord(record);
        when(state.getRawRepo().recordExists(recordId, agencyId)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(recordId, agencyId)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(recordId)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getRawRepo().fetchRecordCollection(recordId, agencyId)).thenReturn(recordCollection);
        when(state.getHoldingsItems().getAgenciesWithHoldings(recordId)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getVipCoreService().hasFeature(Integer.toString(agencyId), VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(record)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(record, record, new ArrayList<>())).thenReturn(false);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), is(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteSingleRecordAction.children();
        assertThat(children.size(), is(4));

        AssertActionsUtil.assertStoreRecordAction(children.get(0), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(children.get(1), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(children.get(2), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(children.get(3), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()));
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
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getBibliographicRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        Map<String, MarcRecord> recordCollection = new HashMap<>();
        recordCollection.put(recordId, record);

        state.setMarcRecord(record);
        when(state.getRawRepo().recordExists(recordId, agencyId)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(recordId, agencyId)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(recordId)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getRawRepo().fetchRecordCollection(recordId, agencyId)).thenReturn(recordCollection);
        when(state.getHoldingsItems().getAgenciesWithHoldings(recordId)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getVipCoreService().hasFeature(Integer.toString(agencyId), VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(record)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(record, record, new ArrayList<>())).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), is(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteSingleRecordAction.children();
        assertThat(children.size(), is(4));

        AssertActionsUtil.assertStoreRecordAction(children.get(0), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(children.get(1), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(children.get(2), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(children.get(3), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()));
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
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getBibliographicRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        Map<String, MarcRecord> recordCollection = new HashMap<>();
        recordCollection.put(recordId, record);

        state.setMarcRecord(record);
        when(state.getRawRepo().recordExists(recordId, agencyId)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(recordId, agencyId)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(recordId)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getRawRepo().fetchRecordCollection(recordId, agencyId)).thenReturn(recordCollection);
        when(state.getHoldingsItems().getAgenciesWithHoldings(recordId)).thenReturn(AssertActionsUtil.createAgenciesSet(700100));
        when(state.getVipCoreService().hasFeature("700100", VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(record)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(record, record, new ArrayList<>())).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), is(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteSingleRecordAction.children();
        assertThat(children.size(), is(5));

        AssertActionsUtil.assertStoreRecordAction(children.get(0), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(children.get(1), state.getRawRepo(), record);
        AssertActionsUtil.assertCreateEnrichmentAction(children.get(2), state.getRawRepo(), record, "700100", null);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(children.get(3), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(children.get(4), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()));
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
     * <p>
     * Local agency does not has the feature 'use_enrichments'
     * </p>
     * </dd>
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
    void testPerformAction_ChangedClassifications_Holdings_CreateEnrichment_LocalAgencyNoEnrichments() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getBibliographicRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        Map<String, MarcRecord> recordCollection = new HashMap<>();
        recordCollection.put(recordId, record);

        state.setMarcRecord(record);
        when(state.getRawRepo().recordExists(recordId, agencyId)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(recordId, agencyId)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(recordId)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getRawRepo().fetchRecordCollection(recordId, agencyId)).thenReturn(recordCollection);
        when(state.getHoldingsItems().getAgenciesWithHoldings(recordId)).thenReturn(AssertActionsUtil.createAgenciesSet(700100));
        when(state.getVipCoreService().hasFeature("700100", VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)).thenReturn(false);
        when(state.getLibraryRecordsHandler().hasClassificationData(record)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(record, record, new ArrayList<>())).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), is(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteSingleRecordAction.children();
        assertThat(children.size(), is(4));

        AssertActionsUtil.assertStoreRecordAction(children.get(0), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(children.get(1), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(children.get(2), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(children.get(3), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()));
    }

    /**
     * Test performAction(): Update single common record with changes to
     * its current classifications, and with holdings for a local library.
     * <p>
     * LibraryRecordsHandler.valueIsDifferent returns UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR
     * so new enrichment records should not be created.
     * </p>
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
     * <li>EnqueueRecordAction: Put the record in queue</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_ChangedClassifications_Holdings_ShouldNotCreateEnrichment() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getBibliographicRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);
        Map<String, MarcRecord> recordCollection = new HashMap<>();
        recordCollection.put(recordId, record);

        state.setMarcRecord(record);
        when(state.getRawRepo().recordExists(recordId, agencyId)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(recordId, agencyId)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(recordId)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getRawRepo().fetchRecordCollection(recordId, agencyId)).thenReturn(recordCollection);
        when(state.getHoldingsItems().getAgenciesWithHoldings(recordId)).thenReturn(AssertActionsUtil.createAgenciesSet(700100));
        when(state.getVipCoreService().hasFeature(Integer.toString(agencyId), VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(record)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(record, record, new ArrayList<>())).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), is(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteSingleRecordAction.children();
        assertThat(children.size(), is(4));

        AssertActionsUtil.assertStoreRecordAction(children.get(0), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(children.get(1), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(children.get(2), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(children.get(3), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()));
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
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getBibliographicRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);
        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        int enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInt(enrichmentRecord);

        Map<String, MarcRecord> recordCollection = new HashMap<>();
        recordCollection.put(recordId, record);

        state.setMarcRecord(record);
        when(state.getRawRepo().recordExists(recordId, agencyId)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(recordId, agencyId)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(recordId)).thenReturn(AssertActionsUtil.createAgenciesSet(enrichmentAgencyId));
        when(state.getRawRepo().recordExists(recordId, enrichmentAgencyId)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(recordId, enrichmentAgencyId)).thenReturn(AssertActionsUtil.createRawRepoRecord(enrichmentRecord, MarcXChangeMimeType.ENRICHMENT));
        when(state.getRawRepo().fetchRecordCollection(recordId, agencyId)).thenReturn(recordCollection);
        when(state.getHoldingsItems().getAgenciesWithHoldings(recordId)).thenReturn(AssertActionsUtil.createAgenciesSet(enrichmentAgencyId));
        when(state.getVipCoreService().hasFeature(Integer.toString(enrichmentAgencyId), VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(record)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(record, record, new ArrayList<>())).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), is(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteSingleRecordAction.children();
        assertThat(children.size(), is(5));

        AssertActionsUtil.assertStoreRecordAction(children.get(0), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(children.get(1), state.getRawRepo(), record);
        AssertActionsUtil.assertUpdateClassificationsInEnrichmentRecordAction(children.get(2), state.getRawRepo(), record, enrichmentRecord);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(children.get(3), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(children.get(4), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()));
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
     * <p>
     * Local agency does not has the feature 'use_enrichments'
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
    void testPerformAction_ChangedClassifications_Holdings_UpdateEnrichment_LocalAgencyNoEnrichments() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getBibliographicRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);
        Map<String, MarcRecord> recordCollection = new HashMap<>();
        recordCollection.put(recordId, record);

        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        int enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInt(enrichmentRecord);

        state.setMarcRecord(record);
        when(state.getRawRepo().recordExists(recordId, agencyId)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(recordId, agencyId)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(recordId)).thenReturn(AssertActionsUtil.createAgenciesSet(enrichmentAgencyId));
        when(state.getRawRepo().fetchRecordCollection(recordId, agencyId)).thenReturn(recordCollection);
        when(state.getRawRepo().recordExists(recordId, enrichmentAgencyId)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(recordId, enrichmentAgencyId)).thenReturn(AssertActionsUtil.createRawRepoRecord(enrichmentRecord, MarcXChangeMimeType.ENRICHMENT));
        when(state.getHoldingsItems().getAgenciesWithHoldings(recordId)).thenReturn(AssertActionsUtil.createAgenciesSet(enrichmentAgencyId));
        when(state.getVipCoreService().hasFeature(Integer.toString(enrichmentAgencyId), VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(record)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(record, record, new ArrayList<>())).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), is(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteSingleRecordAction.children();
        assertThat(children.size(), is(5));

        AssertActionsUtil.assertStoreRecordAction(children.get(0), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(children.get(1), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(children.get(3), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(children.get(4), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()));
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
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getBibliographicRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        Map<String, MarcRecord> recordCollection = new HashMap<>();
        recordCollection.put(recordId, record);

        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        int enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInt(enrichmentRecord);

        state.setMarcRecord(record);
        when(state.getRawRepo().recordExists(recordId, agencyId)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(recordId, agencyId)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(recordId)).thenReturn(AssertActionsUtil.createAgenciesSet(enrichmentAgencyId));
        when(state.getRawRepo().recordExists(recordId, enrichmentAgencyId)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(recordId, enrichmentAgencyId)).thenReturn(AssertActionsUtil.createRawRepoRecord(enrichmentRecord, MarcXChangeMimeType.ENRICHMENT));
        when(state.getRawRepo().fetchRecordCollection(recordId, agencyId)).thenReturn(recordCollection);
        int newEnrichmentAgencyId = enrichmentAgencyId + 100;
        when(state.getHoldingsItems().getAgenciesWithHoldings(recordId)).thenReturn(AssertActionsUtil.createAgenciesSet(enrichmentAgencyId, newEnrichmentAgencyId));
        when(state.getVipCoreService().hasFeature(Integer.toString(enrichmentAgencyId), VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)).thenReturn(true);
        when(state.getVipCoreService().hasFeature(Integer.toString(newEnrichmentAgencyId), VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(record)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(record, record, new ArrayList<>())).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), is(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteSingleRecordAction.children();
        assertThat(children.size(), is(6));

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertUpdateClassificationsInEnrichmentRecordAction(iterator.next(), state.getRawRepo(), record, enrichmentRecord);
        AssertActionsUtil.assertCreateEnrichmentAction(iterator.next(), state.getRawRepo(), record, Integer.toString(newEnrichmentAgencyId), null);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()));
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
     * <li>
     * Local agency does not has the feature 'use_enrichments'
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
     * <li>EnqueueRecordAction: Put the record in queue</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_ChangedClassifications_Holdings_CreateAndUpdateEnrichment_LocalAgencyNoEnrichments() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getBibliographicRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        Map<String, MarcRecord> recordCollection = new HashMap<>();
        recordCollection.put(recordId, record);

        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        int enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInt(enrichmentRecord);

        state.setMarcRecord(record);
        when(state.getRawRepo().recordExists(recordId, agencyId)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(recordId, agencyId)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(recordId)).thenReturn(AssertActionsUtil.createAgenciesSet(enrichmentAgencyId));
        when(state.getRawRepo().recordExists(recordId, enrichmentAgencyId)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(recordId, enrichmentAgencyId)).thenReturn(AssertActionsUtil.createRawRepoRecord(enrichmentRecord, MarcXChangeMimeType.ENRICHMENT));
        when(state.getRawRepo().fetchRecordCollection(recordId, agencyId)).thenReturn(recordCollection);
        int newEnrichmentAgencyId = enrichmentAgencyId + 100;
        when(state.getHoldingsItems().getAgenciesWithHoldings(recordId)).thenReturn(AssertActionsUtil.createAgenciesSet(enrichmentAgencyId, newEnrichmentAgencyId));
        when(state.getVipCoreService().hasFeature(Integer.toString(enrichmentAgencyId), VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)).thenReturn(false);
        when(state.getVipCoreService().hasFeature(Integer.toString(newEnrichmentAgencyId), VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)).thenReturn(false);
        when(state.getLibraryRecordsHandler().hasClassificationData(record)).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), is(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteSingleRecordAction.children();
        assertThat(children.size(), is(4));

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()));
    }

    /**
     * Test performAction(): Update single common record with changes to
     * its current classifications, and creation/update of enrichments.
     * <p>
     * LibraryRecordsHandler.valueIsDifferent returns UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR
     * so new enrichment records should not be created.
     * </p>
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
    void testPerformAction_ChangedClassifications_Holdings_ShouldNotCreateButUpdateEnrichment() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(record).addOrReplaceSubField("032", 'a', "DBI999999");
        record = state.getRecordSorter().sortRecord(record);
        String recordId = AssertActionsUtil.getBibliographicRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        Map<String, MarcRecord> recordCollection = new HashMap<>();
        recordCollection.put(recordId, record);

        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        int enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInt(enrichmentRecord);

        state.setMarcRecord(record);
        when(state.getRawRepo().recordExists(recordId, agencyId)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(recordId, agencyId)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecordCollection(recordId, agencyId)).thenReturn(recordCollection);
        when(state.getRawRepo().agenciesForRecord(recordId)).thenReturn(AssertActionsUtil.createAgenciesSet(enrichmentAgencyId));

        when(state.getRawRepo().recordExists(recordId, enrichmentAgencyId)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(recordId, enrichmentAgencyId)).thenReturn(AssertActionsUtil.createRawRepoRecord(enrichmentRecord, MarcXChangeMimeType.ENRICHMENT));
        int newEnrichmentAgencyId = enrichmentAgencyId + 100;

        when(state.getHoldingsItems().getAgenciesWithHoldings(recordId)).thenReturn(AssertActionsUtil.createAgenciesSet(enrichmentAgencyId, newEnrichmentAgencyId));
        when(state.getVipCoreService().hasFeature(Integer.toString(enrichmentAgencyId), VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)).thenReturn(true);
        when(state.getVipCoreService().hasFeature(Integer.toString(newEnrichmentAgencyId), VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(record)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(record, record, new ArrayList<>())).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), is(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteSingleRecordAction.children();
        //assertThat(children.size(), is(5));

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertUpdateClassificationsInEnrichmentRecordAction(iterator.next(), state.getRawRepo(), record, enrichmentRecord);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()));
    }

    /**
     * Test performAction(): Update single common record with changes to
     * its current classifications, and creation/update of enrichments.
     * <p>
     * LibraryRecordsHandler.valueIsDifferent returns UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR
     * so new enrichment records should not be created.
     * </p>
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
     * <li>
     * Local agency does not has the feature 'use_enrichments'
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
     * <li>EnqueueRecordAction: Put the record in queue</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_ChangedClassifications_Holdings_LocalAgencyNoEnrichments() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getBibliographicRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        Map<String, MarcRecord> recordCollection = new HashMap<>();
        recordCollection.put(recordId, record);

        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        int enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInt(enrichmentRecord);

        state.setMarcRecord(record);
        when(state.getRawRepo().recordExists(recordId, agencyId)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(recordId, agencyId)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(recordId)).thenReturn(AssertActionsUtil.createAgenciesSet(enrichmentAgencyId));
        when(state.getRawRepo().recordExists(recordId, enrichmentAgencyId)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(recordId, enrichmentAgencyId)).thenReturn(AssertActionsUtil.createRawRepoRecord(enrichmentRecord, MarcXChangeMimeType.ENRICHMENT));
        when(state.getRawRepo().fetchRecordCollection(recordId, RawRepo.COMMON_AGENCY)).thenReturn(recordCollection);
        int newEnrichmentAgencyId = enrichmentAgencyId + 100;
        when(state.getHoldingsItems().getAgenciesWithHoldings(recordId)).thenReturn(AssertActionsUtil.createAgenciesSet(enrichmentAgencyId, newEnrichmentAgencyId));
        when(state.getVipCoreService().hasFeature(Integer.toString(enrichmentAgencyId), VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)).thenReturn(false);
        when(state.getVipCoreService().hasFeature(Integer.toString(newEnrichmentAgencyId), VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)).thenReturn(false);
        when(state.getLibraryRecordsHandler().hasClassificationData(record)).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), is(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteSingleRecordAction.children();
        assertThat(children.size(), is(4));

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()));
    }

    /**
     * Test performAction(): Update single common record with no changes to
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
     * Update record <code>c1</code> with no changes in classifications and a new 002 field that
     * points to <code>c2</code>.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>StoreRecordAction: Store the record</li>
     * <li>RemoveLinksAction: Remove any existing links to other records</li>
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
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";
        MarcRecord c1 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId);
        MarcRecord c2 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c2RecordId);
        MarcRecord e1 = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE, c2RecordId);

        MarcRecord record = new MarcRecord(c1);
        new MarcRecordWriter(record).addOrReplaceSubField("002", 'a', c2RecordId);
        String e1RecordId = AssertActionsUtil.getBibliographicRecordId(e1);
        int e1AgencyId = AssertActionsUtil.getAgencyIdAsInt(e1);

        Map<String, MarcRecord> recordCollection = new HashMap<>();
        recordCollection.put(c1RecordId, record);

        state.setMarcRecord(record);
        when(state.getRawRepo().recordExists(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(state.getRawRepo().recordExists(c2RecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(state.getRawRepo().recordExists(e1RecordId, e1AgencyId)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(c1, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecord(c2RecordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(c2, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecord(e1RecordId, e1AgencyId)).thenReturn(AssertActionsUtil.createRawRepoRecord(e1, MarcXChangeMimeType.ENRICHMENT));
        when(state.getRawRepo().enrichments(new RecordId(c2RecordId, RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRecordSet(e1));
        when(state.getRawRepo().fetchRecordCollection(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(recordCollection);
        when(state.getHoldingsItems().getAgenciesWithHoldings(e1RecordId)).thenReturn(AssertActionsUtil.createAgenciesSet(e1AgencyId));
        when(state.getVipCoreService().hasFeature(Integer.toString(e1AgencyId), VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(c1)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(record)).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), is(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = overwriteSingleRecordAction.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()));
        assertThat(iterator.hasNext(), is(false));
    }

    /**
     * Test performAction(): Update single common record with no changes to
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
     * </li>
     * <li>
     * A common record <code>c2</code>. This record is a duplicate of
     * <code>c1</code> but with a different faust-id.
     * </li>
     * <li>
     * An enrichment record <code>e1</code>, that points to <code>c2</code>.
     * </li>
     * <li>
     * Local agency does not has the feature 'use_enrichments'
     * </li>
     * </ol>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update record <code>c1</code> with no changes in classifications and a new 002 field that
     * points to <code>c2</code>.
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
    void testPerformAction_SameClassifications_MoveEnrichments_LocalAgencyNoEnrichments() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";
        MarcRecord c1 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId);
        MarcRecord c2 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c2RecordId);
        MarcRecord e1 = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE, c2RecordId);

        MarcRecord record = new MarcRecord(c1);
        new MarcRecordWriter(record).addOrReplaceSubField("002", 'a', c2RecordId);

        Map<String, MarcRecord> recordCollection = new HashMap<>();
        recordCollection.put(c1RecordId, record);

        String e1RecordId = AssertActionsUtil.getBibliographicRecordId(e1);
        int e1AgencyId = AssertActionsUtil.getAgencyIdAsInt(e1);

        state.setMarcRecord(record);
        when(state.getRawRepo().recordExists(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(state.getRawRepo().recordExists(c2RecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(state.getRawRepo().recordExists(e1RecordId, e1AgencyId)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(c1, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecord(c2RecordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(c2, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecord(e1RecordId, e1AgencyId)).thenReturn(AssertActionsUtil.createRawRepoRecord(e1, MarcXChangeMimeType.ENRICHMENT));
        when(state.getRawRepo().enrichments(new RecordId(c2RecordId, RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRecordSet(e1));
        when(state.getRawRepo().fetchRecordCollection(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(recordCollection);
        when(state.getHoldingsItems().getAgenciesWithHoldings(e1RecordId)).thenReturn(AssertActionsUtil.createAgenciesSet(e1AgencyId));
        when(state.getVipCoreService().hasFeature(Integer.toString(e1AgencyId), VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)).thenReturn(false);
        when(state.getLibraryRecordsHandler().hasClassificationData(c1)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(record)).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), is(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = overwriteSingleRecordAction.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()));
        assertThat(iterator.hasNext(), is(false));
    }

    /**
     * Test performAction(): Update single common record with no changes to
     * its current classifications and with existing 002 in the existing common
     * record.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with this content:
     * <ol>
     * <li>
     * A common record <code>c1</code>. This record is to be updated.
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
     * Update record <code>c1</code> with no changes classifications and with no change to the
     * 002 field that points to <code>c2</code>.
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
    void testPerformAction_SameClassifications_NoChangeIn002Links() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";
        MarcRecord c1 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId);
        new MarcRecordWriter(c1).addOrReplaceSubField("002", 'a', c2RecordId);
        MarcRecord e1 = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE, c2RecordId);
        MarcRecord record = new MarcRecord(c1);
        String e1RecordId = AssertActionsUtil.getBibliographicRecordId(e1);
        int e1AgencyId = AssertActionsUtil.getAgencyIdAsInt(e1);

        Map<String, MarcRecord> recordCollection = new HashMap<>();
        recordCollection.put(c1RecordId, record);

        state.setMarcRecord(record);
        when(state.getRawRepo().recordExists(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(state.getRawRepo().recordExists(c2RecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(state.getRawRepo().recordExists(e1RecordId, e1AgencyId)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(c1, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecord(e1RecordId, e1AgencyId)).thenReturn(AssertActionsUtil.createRawRepoRecord(e1, MarcXChangeMimeType.ENRICHMENT));
        when(state.getRawRepo().fetchRecordCollection(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(recordCollection);
        when(state.getHoldingsItems().getAgenciesWithHoldings(e1RecordId)).thenReturn(AssertActionsUtil.createAgenciesSet(e1AgencyId));
        when(state.getVipCoreService().hasFeature(Integer.toString(e1AgencyId), VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(c1)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(record)).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), is(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = overwriteSingleRecordAction.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()));
        assertThat(iterator.hasNext(), is(false));
    }

    /**
     * Test performAction(): Update single common record with changes to
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
     * </li>
     * <li>
     * A common record <code>c2</code>. This record is a duplicate of
     * <code>c1</code> but with a different faust-id.
     * </li>
     * <li>
     * A library has holdings for <code>c2</code> but no enrichment records.
     * </li>
     * </ol>
     * Both common records are published.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update record <code>c1</code> with no changes to classifications and a new 002 field that
     * points to <code>c2</code>.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>StoreRecordAction: Store the record</li>
     * <li>RemoveLinksAction: Remove any existing links to other records</li>
     * <li>CreateEnrichmentRecordWithClassificationsAction: Create enrichment record for the local agency with holdings.</li>
     * <li>EnqueueRecordAction: Put the record in queue</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_ChangedClassifications_002Links_HoldingsButNoEnrichments_SourceIsPublished_DestinationIsPublished() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";
        final int localAgencyId = 700400;
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId(Integer.toString(localAgencyId));
        MarcRecord c1 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId);
        MarcRecord c2 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c2RecordId);
        MarcRecord record = new MarcRecord(c1);
        new MarcRecordWriter(record).addOrReplaceSubField("002", 'a', c2RecordId);

        Map<String, MarcRecord> recordCollection = new HashMap<>();
        recordCollection.put(c1RecordId, record);

        state.setMarcRecord(record);
        when(state.getRawRepo().recordExists(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(state.getRawRepo().recordExists(c2RecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(c1, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecord(c2RecordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(c2, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecordCollection(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(recordCollection);
        when(state.getHoldingsItems().getAgenciesWithHoldings(c2RecordId)).thenReturn(AssertActionsUtil.createAgenciesSet(localAgencyId));
        when(state.getVipCoreService().hasFeature(Integer.toString(localAgencyId), VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(c1)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(record)).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), is(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = overwriteSingleRecordAction.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()));
        assertThat(iterator.hasNext(), is(false));
    }

    /**
     * Test performAction(): Update single common record with changes to
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
     * </li>
     * <li>
     * A common record <code>c2</code>. This record is a duplicate of
     * <code>c1</code> but with a different faust-id.
     * </li>
     * <li>
     * A library has holdings for <code>c2</code> but no enrichment records.
     * </li>
     * </ol>
     * Only <code>c1</code> is published.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update record <code>c1</code> with changes to classifications and a new 002 field that
     * points to <code>c2</code>.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>StoreRecordAction: Store the record</li>
     * <li>RemoveLinksAction: Remove any existing links to other records</li>
     * <li>CreateEnrichmentRecordWithClassificationsAction: Create enrichment record for the local agency with holdings.</li>
     * <li>EnqueueRecordAction: Put the record in queue</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_ChangedClassifications_002Links_HoldingsButNoEnrichments_SourceIsNotPublished_DestinationIsPublished() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";
        final int localAgencyId = 700400;
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId(Integer.toString(localAgencyId));
        MarcRecord c1 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId);
        MarcRecord c2 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c2RecordId);
        MarcRecord record = new MarcRecord(c1);
        new MarcRecordWriter(record).addOrReplaceSubField("002", 'a', c2RecordId);
        new MarcRecordWriter(c2).addOrReplaceSubField("032", 'a', "DBI999999");

        Map<String, MarcRecord> recordCollection = new HashMap<>();
        recordCollection.put(c1RecordId, record);

        state.setMarcRecord(record);
        when(state.getRawRepo().recordExists(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(state.getRawRepo().recordExists(c2RecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(c1, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecord(c2RecordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(c2, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecordCollection(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(recordCollection);
        when(state.getHoldingsItems().getAgenciesWithHoldings(c2RecordId)).thenReturn(AssertActionsUtil.createAgenciesSet(localAgencyId));
        when(state.getVipCoreService().hasFeature(Integer.toString(localAgencyId), VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(c1)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(record)).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), is(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = overwriteSingleRecordAction.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()));
        assertThat(iterator.hasNext(), is(false));
    }

    /**
     * Test performAction(): Update single common record with changes to
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
     * </li>
     * <li>
     * A common record <code>c2</code>. This record is a duplicate of
     * <code>c1</code> but with a different faust-id.
     * </li>
     * <li>
     * A library has holdings for <code>c2</code> but no enrichment records.
     * </li>
     * </ol>
     * None of the common records are published.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update record <code>c1</code> with changes to classifications and a new 002 field that
     * points to <code>c2</code>.
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
    void testPerformAction_ChangedClassifications_002Links_HoldingsButNoEnrichments_SourceIsNotPublished_DestinationIsNotPublished() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";
        final int localAgencyId = 700400;
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId(Integer.toString(localAgencyId));
        MarcRecord c1 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId);
        MarcRecord c2 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c2RecordId);
        MarcRecord record = new MarcRecord(c1);
        new MarcRecordWriter(record).addOrReplaceSubField("002", 'a', c2RecordId);

        Map<String, MarcRecord> recordCollection = new HashMap<>();
        recordCollection.put(c1RecordId, record);

        state.setMarcRecord(record);
        when(state.getRawRepo().recordExists(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(state.getRawRepo().recordExists(c2RecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(c1, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecord(c2RecordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(c2, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecordCollection(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(recordCollection);
        when(state.getHoldingsItems().getAgenciesWithHoldings(c2RecordId)).thenReturn(AssertActionsUtil.createAgenciesSet(localAgencyId));
        when(state.getVipCoreService().hasFeature(Integer.toString(localAgencyId), VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(c1)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(record)).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), is(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = overwriteSingleRecordAction.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()));
        assertThat(iterator.hasNext(), is(false));
    }

    /**
     * Test performAction(): Update single common record with changes to
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
     * </li>
     * <li>
     * A common record <code>c2</code>. This record is a duplicate of
     * <code>c1</code> but with a different faust-id.
     * </li>
     * <li>
     * A library has holdings for <code>c2</code> but no enrichment records.
     * </li>
     * <li>
     * Local agency does not has the feature 'use_enrichments'
     * </li>
     * </ol>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update record <code>c1</code> with no changes to classifications and a new 002 field that
     * points to <code>c2</code>.
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
    void testPerformAction_ChangedClassifications_002Links_HoldingsButNoEnrichments_LocalAgencyNoEnrichments() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";
        final int localAgencyId = 700400;
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId(Integer.toString(localAgencyId));
        MarcRecord c1 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId);
        MarcRecord c2 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c2RecordId);
        MarcRecord record = new MarcRecord(c1);
        new MarcRecordWriter(record).addOrReplaceSubField("002", 'a', c2RecordId);

        Map<String, MarcRecord> recordCollection = new HashMap<>();
        recordCollection.put(c1RecordId, record);

        state.setMarcRecord(record);
        when(state.getRawRepo().recordExists(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(state.getRawRepo().recordExists(c2RecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(c1, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecord(c2RecordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(c2, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecordCollection(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(recordCollection);
        when(state.getHoldingsItems().getAgenciesWithHoldings(c2RecordId)).thenReturn(AssertActionsUtil.createAgenciesSet(localAgencyId));
        when(state.getVipCoreService().hasFeature(Integer.toString(localAgencyId), VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)).thenReturn(false);
        when(state.getLibraryRecordsHandler().hasClassificationData(c1)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(record)).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), is(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = overwriteSingleRecordAction.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()));
        assertThat(iterator.hasNext(), is(false));
    }

    /**
     * Test performAction(): Update single common record with changes to
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
     * </li>
     * <li>
     * A common record <code>c2</code>. This record is a duplicate of
     * <code>c1</code> but with a different faust-id.
     * </li>
     * <li>
     * An enrichment record to <code>c2</code> exists.
     * </li>
     * </ol>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update record <code>c1</code> with changes to classifications and a new 002 field that
     * points to <code>c2</code>.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>StoreRecordAction: Store the record</li>
     * <li>RemoveLinksAction: Remove any existing links to other records</li>
     * <li>MoveEnrichmentRecordAction: Move the enrichment record to <code>c1</code></li>
     * <li>EnqueueRecordAction: Put the record in queue</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_ChangedClassifications_002Links_MoveEnrichments() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";
        final int localAgencyId = 700400;
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId(Integer.toString(localAgencyId));
        MarcRecord c1 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId);
        MarcRecord c2 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c2RecordId);
        MarcRecord e1 = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE, c2RecordId);
        MarcRecord record = new MarcRecord(c1);
        new MarcRecordWriter(record).addOrReplaceSubField("002", 'a', c2RecordId);
        String e1RecordId = AssertActionsUtil.getBibliographicRecordId(e1);
        int e1AgencyId = AssertActionsUtil.getAgencyIdAsInt(e1);

        Map<String, MarcRecord> recordCollection = new HashMap<>();
        recordCollection.put(c1RecordId, record);

        state.setMarcRecord(record);
        when(state.getRawRepo().recordExists(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(state.getRawRepo().recordExists(c2RecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(state.getRawRepo().recordExists(e1RecordId, e1AgencyId)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(c1, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecord(c2RecordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(c2, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecord(e1RecordId, e1AgencyId)).thenReturn(AssertActionsUtil.createRawRepoRecord(e1, MarcXChangeMimeType.ENRICHMENT));
        when(state.getRawRepo().enrichments(new RecordId(c2RecordId, RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRecordSet(e1));
        when(state.getRawRepo().fetchRecordCollection(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(recordCollection);
        when(state.getHoldingsItems().getAgenciesWithHoldings(e1RecordId)).thenReturn(AssertActionsUtil.createAgenciesSet(e1AgencyId));
        when(state.getVipCoreService().hasFeature(Integer.toString(e1AgencyId), VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(c1)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(record)).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), is(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = overwriteSingleRecordAction.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()));
        assertThat(iterator.hasNext(), is(false));
    }

    /**
     * Test performAction(): Update single common record with changes to
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
     * </li>
     * <li>
     * A common record <code>c2</code>. This record is a duplicate of
     * <code>c1</code> but with a different faust-id.
     * </li>
     * <li>
     * An enrichment record to <code>c2</code> exists.
     * </li>
     * <li>
     * Local agency does not has the feature 'use_enrichments'
     * </li>
     * </ol>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update record <code>c1</code> with changes to classifications and a new 002 field that
     * points to <code>c2</code>.
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
    void testPerformAction_ChangedClassifications_002Links_LocalAgencyEnrichments() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";
        final int localAgencyId = 700400;
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId(Integer.toString(localAgencyId));
        MarcRecord c1 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId);
        MarcRecord c2 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c2RecordId);
        MarcRecord e1 = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE, c2RecordId);
        MarcRecord record = new MarcRecord(c1);
        new MarcRecordWriter(record).addOrReplaceSubField("002", 'a', c2RecordId);
        String e1RecordId = AssertActionsUtil.getBibliographicRecordId(e1);
        int e1AgencyId = AssertActionsUtil.getAgencyIdAsInt(e1);

        Map<String, MarcRecord> recordCollection = new HashMap<>();
        recordCollection.put(c2RecordId, record);

        state.setMarcRecord(record);
        when(state.getRawRepo().recordExists(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(state.getRawRepo().recordExists(c2RecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(state.getRawRepo().recordExists(e1RecordId, e1AgencyId)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(c1, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecord(c2RecordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(c2, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecord(e1RecordId, e1AgencyId)).thenReturn(AssertActionsUtil.createRawRepoRecord(e1, MarcXChangeMimeType.ENRICHMENT));
        when(state.getRawRepo().enrichments(new RecordId(c2RecordId, RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRecordSet(e1));
        when(state.getRawRepo().fetchRecordCollection(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(recordCollection);
        when(state.getHoldingsItems().getAgenciesWithHoldings(e1RecordId)).thenReturn(AssertActionsUtil.createAgenciesSet(e1AgencyId));
        when(state.getVipCoreService().hasFeature(Integer.toString(e1AgencyId), VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)).thenReturn(false);
        when(state.getLibraryRecordsHandler().hasClassificationData(c1)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(record)).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), is(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = overwriteSingleRecordAction.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()));
        assertThat(iterator.hasNext(), is(false));
    }

    /**
     * Test performAction(): Update single common record with no changes to
     * its current classifications and with new 002 to a common record that
     * does not exist.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with this content:
     * <ol>
     * <li>
     * A common record <code>c1</code>. This record is to be updated.
     * </li>
     * </ol>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update record <code>c1</code> with no changes to classifications and a new 002 field that
     * points to a record that does not exist.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return status: FAILED_UPDATE_INTERNAL_ERROR
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_002Link_DoNotExist() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";
        MarcRecord c1 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId);
        MarcRecord record = new MarcRecord(c1);
        new MarcRecordWriter(record).addOrReplaceSubField("002", 'a', c2RecordId);

        Map<String, MarcRecord> recordCollection = new HashMap<>();
        recordCollection.put(c1RecordId, record);

        state.setMarcRecord(record);
        when(state.getRawRepo().recordExists(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(state.getRawRepo().recordExists(c2RecordId, RawRepo.COMMON_AGENCY)).thenReturn(false);
        when(state.getRawRepo().fetchRecord(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(c1, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecordCollection(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(recordCollection);
        when(state.getLibraryRecordsHandler().hasClassificationData(c1)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(record)).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), is(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = overwriteSingleRecordAction.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()));
        assertThat(iterator.hasNext(), is(false));
    }

    @Test
    void testAuthorityRecordShouldUpdate133() throws Exception {
        String existing = "001 00 *a 68058309 *b 870979 *c 20160617172909 *d 20131129 *f a *t faust\n" +
                "004 00 *r n *a e *x n\n" +
                "133 00 *a Andersen";

        String input = "001 00 *a 68058309 *b 870979 *c 20181211090242 *d 20131129 *f a *t faust\n" +
                "004 00 *r n *a e *x n\n" +
                "133 00 *a Andersen";

        MarcRecord existingAutRecord = UpdateRecordContentTransformer.readRecordFromString(existing);
        MarcRecord inputAutRecord = UpdateRecordContentTransformer.readRecordFromString(input);

        state.setMarcRecord(inputAutRecord);
        when(state.getRawRepo().recordExists("68058309", RawRepo.AUTHORITY_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord("68058309",
                RawRepo.AUTHORITY_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingAutRecord,
                MarcXChangeMimeType.AUTHORITY));

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, inputAutRecord);
        assertThat(overwriteSingleRecordAction.shouldUpdateChildrenModifiedDate(inputAutRecord), is(false));
        MarcRecordWriter inwriter = new MarcRecordWriter(inputAutRecord);
        inwriter.removeField("133");
        inwriter.addFieldSubfield("133", 'a', "Hansen");
        assertThat(overwriteSingleRecordAction.shouldUpdateChildrenModifiedDate(inputAutRecord), is(true));
    }

    @Test
    void testAuthorityRecordShouldUpdate134() throws Exception {
        String existing = "001 00 *a 68058309 *b 870979 *c 20160617172909 *d 20131129 *f a *t faust\n" +
                "004 00 *r n *a e *x n\n" +
                "134 00 *a Andersen";

        String input = "001 00 *a 68058309 *b 870979 *c 20181211090242 *d 20131129 *f a *t faust\n" +
                "004 00 *r n *a e *x n\n" +
                "134 00 *a Andersen";

        MarcRecord existingAutRecord = UpdateRecordContentTransformer.readRecordFromString(existing);
        MarcRecord inputAutRecord = UpdateRecordContentTransformer.readRecordFromString(input);

        state.setMarcRecord(inputAutRecord);
        when(state.getRawRepo().recordExists("68058309", RawRepo.AUTHORITY_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord("68058309",
                RawRepo.AUTHORITY_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingAutRecord,
                MarcXChangeMimeType.AUTHORITY));

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, inputAutRecord);
        assertThat(overwriteSingleRecordAction.shouldUpdateChildrenModifiedDate(inputAutRecord), is(false));
        MarcRecordWriter inwriter = new MarcRecordWriter(inputAutRecord);
        inwriter.removeField("134");
        inwriter.addFieldSubfield("134", 'a', "Hansen");
        assertThat(overwriteSingleRecordAction.shouldUpdateChildrenModifiedDate(inputAutRecord), is(true));
    }

    @Test
    void testAuthorityRecordShouldUpdate433() throws Exception {
        String existing = "001 00 *a 68058309 *b 870979 *c 20160617172909 *d 20131129 *f a *t faust\n" +
                "004 00 *r n *a e *x n\n" +
                "433 00 *a Andersen";

        String input = "001 00 *a 68058309 *b 870979 *c 20181211090242 *d 20131129 *f a *t faust\n" +
                "004 00 *r n *a e *x n\n" +
                "433 00 *a Andersen";

        MarcRecord existingAutRecord = UpdateRecordContentTransformer.readRecordFromString(existing);
        MarcRecord inputAutRecord = UpdateRecordContentTransformer.readRecordFromString(input);

        state.setMarcRecord(inputAutRecord);
        when(state.getRawRepo().recordExists("68058309", RawRepo.AUTHORITY_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord("68058309",
                RawRepo.AUTHORITY_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingAutRecord,
                MarcXChangeMimeType.AUTHORITY));

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, inputAutRecord);
        assertThat(overwriteSingleRecordAction.shouldUpdateChildrenModifiedDate(inputAutRecord), is(false));
        MarcRecordWriter inwriter = new MarcRecordWriter(inputAutRecord);
        inwriter.removeField("433");
        inwriter.addFieldSubfield("433", 'a', "Hansen");
        assertThat(overwriteSingleRecordAction.shouldUpdateChildrenModifiedDate(inputAutRecord), is(true));
    }

    @Test
    void testAuthorityRecordShouldUpdate434() throws Exception {
        String existing = "001 00 *a 68058309 *b 870979 *c 20160617172909 *d 20131129 *f a *t faust\n" +
                "004 00 *r n *a e *x n\n" +
                "434 00 *a Andersen";

        String input = "001 00 *a 68058309 *b 870979 *c 20181211090242 *d 20131129 *f a *t faust\n" +
                "004 00 *r n *a e *x n\n" +
                "434 00 *a Andersen";

        MarcRecord existingAutRecord = UpdateRecordContentTransformer.readRecordFromString(existing);
        MarcRecord inputAutRecord = UpdateRecordContentTransformer.readRecordFromString(input);

        state.setMarcRecord(inputAutRecord);
        when(state.getRawRepo().recordExists("68058309", RawRepo.AUTHORITY_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord("68058309",
                RawRepo.AUTHORITY_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingAutRecord,
                MarcXChangeMimeType.AUTHORITY));

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, inputAutRecord);
        assertThat(overwriteSingleRecordAction.shouldUpdateChildrenModifiedDate(inputAutRecord), is(false));
        MarcRecordWriter inwriter = new MarcRecordWriter(inputAutRecord);
        inwriter.removeField("434");
        inwriter.addFieldSubfield("434", 'a', "Hansen");
        assertThat(overwriteSingleRecordAction.shouldUpdateChildrenModifiedDate(inputAutRecord), is(true));
    }

    @Test
    void testAuthorityRecordHasProofPrintingDiffSame110() throws Exception {
        String existing = "001 00 *a 68058309 *b 870979 *c 20160617172909 *d 20131129 *f a *t faust\n" +
                "004 00 *r n *a e *x n\n" +
                "110 00 *a Andersen *h Flemming *c f. 1961-08-24";

        String input = "001 00 *a 68058309 *b 870979 *c 20181211090242 *d 20131129 *f a *t faust\n" +
                "004 00 *r n *a e *x n\n" +
                "110 00 *a Andersen *h Flemming *c f. 1961-08-24";

        MarcRecord existingAutRecord = UpdateRecordContentTransformer.readRecordFromString(existing);
        MarcRecord inputAutRecord = UpdateRecordContentTransformer.readRecordFromString(input);

        state.setMarcRecord(inputAutRecord);
        when(state.getRawRepo().recordExists("68058309", RawRepo.AUTHORITY_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord("68058309", RawRepo.AUTHORITY_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingAutRecord, MarcXChangeMimeType.AUTHORITY));

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, inputAutRecord);
        assertThat(overwriteSingleRecordAction.shouldUpdateChildrenModifiedDate(inputAutRecord), is(false));
    }

    @Test
    void testAuthorityRecordHasProofPrintingDiffSame110410() throws Exception {
        String existing = "001 00 *a 69022804 *b 870979 *c 20181210135157 *d 20131129 *f a *t faust\n" +
                "110 00 *a Thulstrup *h Thomas C.\n" +
                "410 00 *a Thulstrup *h Thomas";

        String input = "001 00 *a 69022804 *b 870979 *c 20181211090242 *d 20131129 *f a *t faust\n" +
                "110 00 *a Thulstrup *h Thomas C.\n" +
                "375 00 *a 1 *2 iso5218 *& VIAF\n" +
                "410 00 *a Thulstrup *h Thomas";

        MarcRecord existingAutRecord = UpdateRecordContentTransformer.readRecordFromString(existing);
        MarcRecord inputAutRecord = UpdateRecordContentTransformer.readRecordFromString(input);

        state.setMarcRecord(inputAutRecord);
        when(state.getRawRepo().recordExists("69022804", RawRepo.AUTHORITY_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord("69022804", RawRepo.AUTHORITY_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingAutRecord, MarcXChangeMimeType.AUTHORITY));

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, inputAutRecord);
        assertThat(overwriteSingleRecordAction.shouldUpdateChildrenModifiedDate(inputAutRecord), is(false));
    }

    @Test
    void testAuthorityRecordHasProofPrintingDiffSame110Missing410() throws Exception {
        String existing = "001 00 *a 69022804 *b 870979 *c 20181210135157 *d 20131129 *f a *t faust\n" +
                "110 00 *a Thulstrup *h Thomas C.\n" +
                "410 00 *a Thulstrup *h Thomas";

        String input = "001 00 *a 69022804 *b 870979 *c 20181211090242 *d 20131129 *f a *t faust\n" +
                "110 00 *a Thulstrup *h Thomas C.";

        MarcRecord existingAutRecord = UpdateRecordContentTransformer.readRecordFromString(existing);
        MarcRecord inputAutRecord = UpdateRecordContentTransformer.readRecordFromString(input);

        state.setMarcRecord(inputAutRecord);
        when(state.getRawRepo().recordExists("69022804", RawRepo.AUTHORITY_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord("69022804", RawRepo.AUTHORITY_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingAutRecord, MarcXChangeMimeType.AUTHORITY));

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, inputAutRecord);
        assertThat(overwriteSingleRecordAction.shouldUpdateChildrenModifiedDate(inputAutRecord), is(true));
    }

    @Test
    void testAuthorityRecordHasProofPrintingDiffSame110410510() throws Exception {
        String existing = "001 00 *a 19257355 *b 870979 *c 20181210134226 *d 20171102 *f a\n" +
                "110 00 *a Mernild *h Sebastian H.\n" +
                "410 00 *a Mernild *h Sebastian\n" +
                "510 00 *a Mernild *h Sebastian I.\n" +
                "996 00 *a DBCAUT";

        String input = "001 00 *a 19257355 *b 870979 *c 20181211090242 *d 20171102 *f a\n" +
                "110 00 *a Mernild *h Sebastian H.\n" +
                "410 00 *a Mernild *h Sebastian\n" +
                "510 00 *a Mernild *h Sebastian I.\n" +
                "996 00 *a DBCAUT";

        MarcRecord existingAutRecord = UpdateRecordContentTransformer.readRecordFromString(existing);
        MarcRecord inputAutRecord = UpdateRecordContentTransformer.readRecordFromString(input);

        state.setMarcRecord(inputAutRecord);
        when(state.getRawRepo().recordExists("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingAutRecord, MarcXChangeMimeType.AUTHORITY));

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, inputAutRecord);
        assertThat(overwriteSingleRecordAction.shouldUpdateChildrenModifiedDate(inputAutRecord), is(false));
    }

    @Test
    void testAuthorityRecordHasProofPrintingDiffChanged110410510() throws Exception {
        String existing = "001 00 *a 19257355 *b 870979 *c 20181210134226 *d 20171102 *f a\n" +
                "110 00 *a Mernild *h Sebastian H.\n" +
                "410 00 *a Mernild *h Sebastian\n" +
                "510 00 *a Mernild *h Sebastian I.\n" +
                "996 00 *a DBCAUT";

        String input = "001 00 *a 19257355 *b 870979 *c 20181211090242 *d 20171102 *f a\n" +
                "110 00 *a Mernild *h Sebastian H.\n" +
                "410 00 *a Mernild *h Sebastian\n" +
                "510 00 *a Mernild *h Sebastian I .\n" + // Space after I
                "996 00 *a DBCAUT";

        MarcRecord existingAutRecord = UpdateRecordContentTransformer.readRecordFromString(existing);
        MarcRecord inputAutRecord = UpdateRecordContentTransformer.readRecordFromString(input);

        state.setMarcRecord(inputAutRecord);
        when(state.getRawRepo().recordExists("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingAutRecord, MarcXChangeMimeType.AUTHORITY));

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, inputAutRecord);
        assertThat(overwriteSingleRecordAction.shouldUpdateChildrenModifiedDate(inputAutRecord), is(true));
    }

    @Test
    void testAuthorityRecordHasProofPrintingDiffSame110410510Repeated() throws Exception {
        String existing = "001 00 *a 19257355 *b 870979 *c 20181210134226 *d 20171102 *f a\n" +
                "110 00 *a Mernild *h Sebastian H.\n" +
                "410 00 *a Mernild *h Sebastian\n" +
                "410 00 *a Mernild *h Sebastian A.\n" +
                "510 00 *a Mernild *h Sebastian I.\n" +
                "510 00 *a Mernild *h Sebastian F.\n" +
                "996 00 *a DBCAUT";

        String input = "001 00 *a 19257355 *b 870979 *c 20181211090242 *d 20171102 *f a\n" +
                "110 00 *a Mernild *h Sebastian H.\n" +
                "410 00 *a Mernild *h Sebastian\n" +
                "410 00 *a Mernild *h Sebastian A.\n" +
                "510 00 *a Mernild *h Sebastian I.\n" +
                "510 00 *a Mernild *h Sebastian F.\n" +
                "996 00 *a DBCAUT";

        MarcRecord existingAutRecord = UpdateRecordContentTransformer.readRecordFromString(existing);
        MarcRecord inputAutRecord = UpdateRecordContentTransformer.readRecordFromString(input);

        state.setMarcRecord(inputAutRecord);
        when(state.getRawRepo().recordExists("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingAutRecord, MarcXChangeMimeType.AUTHORITY));

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, inputAutRecord);
        assertThat(overwriteSingleRecordAction.shouldUpdateChildrenModifiedDate(inputAutRecord), is(false));
    }

    @Test
    void testAuthorityRecordHasProofPrintingDiffRemoved510() throws Exception {
        String existing = "001 00 *a 19257355 *b 870979 *c 20181210134226 *d 20171102 *f a\n" +
                "110 00 *a Mernild *h Sebastian H.\n" +
                "410 00 *a Mernild *h Sebastian\n" +
                "410 00 *a Mernild *h Sebastian A.\n" +
                "510 00 *a Mernild *h Sebastian F.\n" +
                "996 00 *a DBCAUT";

        String input = "001 00 *a 19257355 *b 870979 *c 20181211090242 *d 20171102 *f a\n" +
                "110 00 *a Mernild *h Sebastian H.\n" +
                "410 00 *a Mernild *h Sebastian\n" +
                "410 00 *a Mernild *h Sebastian A.\n" +
                "510 00 *a Mernild *h Sebastian I.\n" +
                "510 00 *a Mernild *h Sebastian F.\n" +
                "996 00 *a DBCAUT";

        MarcRecord existingAutRecord = UpdateRecordContentTransformer.readRecordFromString(existing);
        MarcRecord inputAutRecord = UpdateRecordContentTransformer.readRecordFromString(input);

        state.setMarcRecord(inputAutRecord);
        when(state.getRawRepo().recordExists("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingAutRecord, MarcXChangeMimeType.AUTHORITY));

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, inputAutRecord);
        assertThat(overwriteSingleRecordAction.shouldUpdateChildrenModifiedDate(inputAutRecord), is(true));
    }

    @Test
    void testAuthorityRecordHasProofPrintingDiffAdded510() throws Exception {
        String existing = "001 00 *a 19257355 *b 870979 *c 20181210134226 *d 20171102 *f a\n" +
                "110 00 *a Mernild *h Sebastian H.\n" +
                "410 00 *a Mernild *h Sebastian\n" +
                "410 00 *a Mernild *h Sebastian A.\n" +
                "510 00 *a Mernild *h Sebastian F.\n" +
                "996 00 *a DBCAUT";

        String input = "001 00 *a 19257355 *b 870979 *c 20181211090242 *d 20171102 *f a\n" +
                "110 00 *a Mernild *h Sebastian H.\n" +
                "410 00 *a Mernild *h Sebastian\n" +
                "410 00 *a Mernild *h Sebastian A.\n" +
                "510 00 *a Mernild *h Sebastian F.\n" +
                "510 00 *a Mernild *h Sebastian I.\n" +
                "996 00 *a DBCAUT";

        MarcRecord existingAutRecord = UpdateRecordContentTransformer.readRecordFromString(existing);
        MarcRecord inputAutRecord = UpdateRecordContentTransformer.readRecordFromString(input);

        state.setMarcRecord(inputAutRecord);
        when(state.getRawRepo().recordExists("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingAutRecord, MarcXChangeMimeType.AUTHORITY));

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, inputAutRecord);
        assertThat(overwriteSingleRecordAction.shouldUpdateChildrenModifiedDate(inputAutRecord), is(true));
    }

    @Test
    void testAuthorityRecordHasProofPrintingDiffMinusAdjourCorp() throws Exception {
        String existingRecord = "001 00 *a 19257355 *b 870979 *c 20181210134226 *d 20171102 *f a\n" +
                "110 00 *a Mernild *h Sebastian H.\n" +
                "410 00 *a Mernild *h Sebastian\n" +
                "410 00 *a Mernild *h Sebastian A.\n" +
                "510 00 *a Mernild *h Sebastian F.\n" +
                "996 00 *a DBCAUT";


        String stateRecord = "001 00 *a 19257355 *b 870979 *c 20181211090242 *d 20171102 *f a\n" +
                "110 00 *a Mernild *h Sebastian H.\n" +
                "410 00 *a Mernild *h Sebastian\n" +
                "410 00 *a Mernild *h Sebastian A.\n" +
                "510 00 *a Mernild *h Sebastian F.\n" +
                "510 00 *a Mernild *h Sebastian I.\n" +
                "996 00 *a DBCAUT \n" +
                "s13 00 *a minusAJOUR";

        String record = "001 00 *a 19257355 *b 870979 *c 20181211090242 *d 20171102 *f a\n" +
                "100 00 *a Mernild *h Sebastian H.\n" +
                "400 00 *a Mernild *h Sebastian\n" +
                "400 00 *a Mernild *h Sebastian A.\n" +
                "500 00 *a Mernild *h Sebastian F.\n" +
                "500 00 *a Mernild *h Sebastian I.\n" +
                "996 00 *a DBCAUT";

        MarcRecord existingAutRecord = UpdateRecordContentTransformer.readRecordFromString(existingRecord);
        MarcRecord stateAutRecord = UpdateRecordContentTransformer.readRecordFromString(stateRecord);
        MarcRecord inputAutRecord = UpdateRecordContentTransformer.readRecordFromString(record);

        state.setMarcRecord(stateAutRecord);
        when(state.getRawRepo().recordExists("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingAutRecord, MarcXChangeMimeType.AUTHORITY));

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, inputAutRecord);
        assertThat(overwriteSingleRecordAction.shouldUpdateChildrenModifiedDate(inputAutRecord), is(false));
    }

    @Test
    void testAuthorityRecordHasProofPrintingDiffSame100() throws Exception {
        String existing = "001 00 *a 68058309 *b 870979 *c 20160617172909 *d 20131129 *f a *t faust\n" +
                "004 00 *r n *a e *x n\n" +
                "100 00 *a Andersen *h Flemming *c f. 1961-08-24";

        String input = "001 00 *a 68058309 *b 870979 *c 20181211090242 *d 20131129 *f a *t faust\n" +
                "004 00 *r n *a e *x n\n" +
                "100 00 *a Andersen *h Flemming *c f. 1961-08-24";

        MarcRecord existingAutRecord = UpdateRecordContentTransformer.readRecordFromString(existing);
        MarcRecord inputAutRecord = UpdateRecordContentTransformer.readRecordFromString(input);

        state.setMarcRecord(inputAutRecord);
        when(state.getRawRepo().recordExists("68058309", RawRepo.AUTHORITY_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord("68058309", RawRepo.AUTHORITY_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingAutRecord, MarcXChangeMimeType.AUTHORITY));

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, inputAutRecord);
        assertThat(overwriteSingleRecordAction.shouldUpdateChildrenModifiedDate(inputAutRecord), is(false));
    }

    @Test
    void testAuthorityRecordHasProofPrintingDiffSame100400() throws Exception {
        String existing = "001 00 *a 69022804 *b 870979 *c 20181210135157 *d 20131129 *f a *t faust\n" +
                "100 00 *a Thulstrup *h Thomas C.\n" +
                "400 00 *a Thulstrup *h Thomas";

        String input = "001 00 *a 69022804 *b 870979 *c 20181211090242 *d 20131129 *f a *t faust\n" +
                "100 00 *a Thulstrup *h Thomas C.\n" +
                "375 00 *a 1 *2 iso5218 *& VIAF\n" +
                "400 00 *a Thulstrup *h Thomas";

        MarcRecord existingAutRecord = UpdateRecordContentTransformer.readRecordFromString(existing);
        MarcRecord inputAutRecord = UpdateRecordContentTransformer.readRecordFromString(input);

        state.setMarcRecord(inputAutRecord);
        when(state.getRawRepo().recordExists("69022804", RawRepo.AUTHORITY_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord("69022804", RawRepo.AUTHORITY_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingAutRecord, MarcXChangeMimeType.AUTHORITY));

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, inputAutRecord);
        assertThat(overwriteSingleRecordAction.shouldUpdateChildrenModifiedDate(inputAutRecord), is(false));
    }

    @Test
    void testAuthorityRecordHasProofPrintingDiffSame100Missing400() throws Exception {
        String existing = "001 00 *a 69022804 *b 870979 *c 20181210135157 *d 20131129 *f a *t faust\n" +
                "100 00 *a Thulstrup *h Thomas C.\n" +
                "400 00 *a Thulstrup *h Thomas";

        String input = "001 00 *a 69022804 *b 870979 *c 20181211090242 *d 20131129 *f a *t faust\n" +
                "100 00 *a Thulstrup *h Thomas C.";

        MarcRecord existingAutRecord = UpdateRecordContentTransformer.readRecordFromString(existing);
        MarcRecord inputAutRecord = UpdateRecordContentTransformer.readRecordFromString(input);

        state.setMarcRecord(inputAutRecord);
        when(state.getRawRepo().recordExists("69022804", RawRepo.AUTHORITY_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord("69022804", RawRepo.AUTHORITY_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingAutRecord, MarcXChangeMimeType.AUTHORITY));

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, inputAutRecord);
        assertThat(overwriteSingleRecordAction.shouldUpdateChildrenModifiedDate(inputAutRecord), is(true));
    }

    @Test
    void testAuthorityRecordHasProofPrintingDiffSame100400500() throws Exception {
        String existing = "001 00 *a 19257355 *b 870979 *c 20181210134226 *d 20171102 *f a\n" +
                "100 00 *a Mernild *h Sebastian H.\n" +
                "400 00 *a Mernild *h Sebastian\n" +
                "500 00 *a Mernild *h Sebastian I.\n" +
                "996 00 *a DBCAUT";

        String input = "001 00 *a 19257355 *b 870979 *c 20181211090242 *d 20171102 *f a\n" +
                "100 00 *a Mernild *h Sebastian H.\n" +
                "400 00 *a Mernild *h Sebastian\n" +
                "500 00 *a Mernild *h Sebastian I.\n" +
                "996 00 *a DBCAUT";

        MarcRecord existingAutRecord = UpdateRecordContentTransformer.readRecordFromString(existing);
        MarcRecord inputAutRecord = UpdateRecordContentTransformer.readRecordFromString(input);

        state.setMarcRecord(inputAutRecord);
        when(state.getRawRepo().recordExists("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingAutRecord, MarcXChangeMimeType.AUTHORITY));

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, inputAutRecord);
        assertThat(overwriteSingleRecordAction.shouldUpdateChildrenModifiedDate(inputAutRecord), is(false));
    }

    @Test
    void testAuthorityRecordHasProofPrintingDiffChanged100400500() throws Exception {
        String existing = "001 00 *a 19257355 *b 870979 *c 20181210134226 *d 20171102 *f a\n" +
                "100 00 *a Mernild *h Sebastian H.\n" +
                "400 00 *a Mernild *h Sebastian\n" +
                "500 00 *a Mernild *h Sebastian I.\n" +
                "996 00 *a DBCAUT";

        String input = "001 00 *a 19257355 *b 870979 *c 20181211090242 *d 20171102 *f a\n" +
                "100 00 *a Mernild *h Sebastian H.\n" +
                "400 00 *a Mernild *h Sebastian\n" +
                "500 00 *a Mernild *h Sebastian I .\n" + // Space after I
                "996 00 *a DBCAUT";

        MarcRecord existingAutRecord = UpdateRecordContentTransformer.readRecordFromString(existing);
        MarcRecord inputAutRecord = UpdateRecordContentTransformer.readRecordFromString(input);

        state.setMarcRecord(inputAutRecord);
        when(state.getRawRepo().recordExists("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingAutRecord, MarcXChangeMimeType.AUTHORITY));

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, inputAutRecord);
        assertThat(overwriteSingleRecordAction.shouldUpdateChildrenModifiedDate(inputAutRecord), is(true));
    }

    @Test
    void testAuthorityRecordHasProofPrintingDiffSame100400500Repeated() throws Exception {
        String existing = "001 00 *a 19257355 *b 870979 *c 20181210134226 *d 20171102 *f a\n" +
                "100 00 *a Mernild *h Sebastian H.\n" +
                "400 00 *a Mernild *h Sebastian\n" +
                "400 00 *a Mernild *h Sebastian A.\n" +
                "500 00 *a Mernild *h Sebastian I.\n" +
                "500 00 *a Mernild *h Sebastian F.\n" +
                "996 00 *a DBCAUT";

        String input = "001 00 *a 19257355 *b 870979 *c 20181211090242 *d 20171102 *f a\n" +
                "100 00 *a Mernild *h Sebastian H.\n" +
                "400 00 *a Mernild *h Sebastian\n" +
                "400 00 *a Mernild *h Sebastian A.\n" +
                "500 00 *a Mernild *h Sebastian I.\n" +
                "500 00 *a Mernild *h Sebastian F.\n" +
                "996 00 *a DBCAUT";

        MarcRecord existingAutRecord = UpdateRecordContentTransformer.readRecordFromString(existing);
        MarcRecord inputAutRecord = UpdateRecordContentTransformer.readRecordFromString(input);

        state.setMarcRecord(inputAutRecord);
        when(state.getRawRepo().recordExists("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingAutRecord, MarcXChangeMimeType.AUTHORITY));

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, inputAutRecord);
        assertThat(overwriteSingleRecordAction.shouldUpdateChildrenModifiedDate(inputAutRecord), is(false));
    }

    @Test
    void testAuthorityRecordHasProofPrintingDiffRemoved500() throws Exception {
        String existing = "001 00 *a 19257355 *b 870979 *c 20181210134226 *d 20171102 *f a\n" +
                "100 00 *a Mernild *h Sebastian H.\n" +
                "400 00 *a Mernild *h Sebastian\n" +
                "400 00 *a Mernild *h Sebastian A.\n" +
                "500 00 *a Mernild *h Sebastian F.\n" +
                "996 00 *a DBCAUT";

        String input = "001 00 *a 19257355 *b 870979 *c 20181211090242 *d 20171102 *f a\n" +
                "100 00 *a Mernild *h Sebastian H.\n" +
                "400 00 *a Mernild *h Sebastian\n" +
                "400 00 *a Mernild *h Sebastian A.\n" +
                "500 00 *a Mernild *h Sebastian I.\n" +
                "500 00 *a Mernild *h Sebastian F.\n" +
                "996 00 *a DBCAUT";

        MarcRecord existingAutRecord = UpdateRecordContentTransformer.readRecordFromString(existing);
        MarcRecord inputAutRecord = UpdateRecordContentTransformer.readRecordFromString(input);

        state.setMarcRecord(inputAutRecord);
        when(state.getRawRepo().recordExists("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingAutRecord, MarcXChangeMimeType.AUTHORITY));

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, inputAutRecord);
        assertThat(overwriteSingleRecordAction.shouldUpdateChildrenModifiedDate(inputAutRecord), is(true));
    }

    @Test
    void testAuthorityRecordHasProofPrintingDiffAdded500() throws Exception {
        String existing = "001 00 *a 19257355 *b 870979 *c 20181210134226 *d 20171102 *f a\n" +
                "100 00 *a Mernild *h Sebastian H.\n" +
                "400 00 *a Mernild *h Sebastian\n" +
                "400 00 *a Mernild *h Sebastian A.\n" +
                "500 00 *a Mernild *h Sebastian F.\n" +
                "996 00 *a DBCAUT";

        String input = "001 00 *a 19257355 *b 870979 *c 20181211090242 *d 20171102 *f a\n" +
                "100 00 *a Mernild *h Sebastian H.\n" +
                "400 00 *a Mernild *h Sebastian\n" +
                "400 00 *a Mernild *h Sebastian A.\n" +
                "500 00 *a Mernild *h Sebastian F.\n" +
                "500 00 *a Mernild *h Sebastian I.\n" +
                "996 00 *a DBCAUT";

        MarcRecord existingAutRecord = UpdateRecordContentTransformer.readRecordFromString(existing);
        MarcRecord inputAutRecord = UpdateRecordContentTransformer.readRecordFromString(input);

        state.setMarcRecord(inputAutRecord);
        when(state.getRawRepo().recordExists("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingAutRecord, MarcXChangeMimeType.AUTHORITY));

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, inputAutRecord);
        assertThat(overwriteSingleRecordAction.shouldUpdateChildrenModifiedDate(inputAutRecord), is(true));
    }

    @Test
    void testAuthorityRecordHasProofPrintingDiffMinusAdjour() throws Exception {
        String existingRecord = "001 00 *a 19257355 *b 870979 *c 20181210134226 *d 20171102 *f a\n" +
                "100 00 *a Mernild *h Sebastian H.\n" +
                "400 00 *a Mernild *h Sebastian\n" +
                "400 00 *a Mernild *h Sebastian A.\n" +
                "500 00 *a Mernild *h Sebastian F.\n" +
                "996 00 *a DBCAUT";


        String stateRecord = "001 00 *a 19257355 *b 870979 *c 20181211090242 *d 20171102 *f a\n" +
                "100 00 *a Mernild *h Sebastian H.\n" +
                "400 00 *a Mernild *h Sebastian\n" +
                "400 00 *a Mernild *h Sebastian A.\n" +
                "500 00 *a Mernild *h Sebastian F.\n" +
                "500 00 *a Mernild *h Sebastian I.\n" +
                "996 00 *a DBCAUT \n" +
                "s13 00 *a minusAJOUR";

        String record = "001 00 *a 19257355 *b 870979 *c 20181211090242 *d 20171102 *f a\n" +
                "100 00 *a Mernild *h Sebastian H.\n" +
                "400 00 *a Mernild *h Sebastian\n" +
                "400 00 *a Mernild *h Sebastian A.\n" +
                "500 00 *a Mernild *h Sebastian F.\n" +
                "500 00 *a Mernild *h Sebastian I.\n" +
                "996 00 *a DBCAUT";

        MarcRecord existingAutRecord = UpdateRecordContentTransformer.readRecordFromString(existingRecord);
        MarcRecord stateAutRecord = UpdateRecordContentTransformer.readRecordFromString(stateRecord);
        MarcRecord inputAutRecord = UpdateRecordContentTransformer.readRecordFromString(record);

        state.setMarcRecord(stateAutRecord);
        when(state.getRawRepo().recordExists("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingAutRecord, MarcXChangeMimeType.AUTHORITY));

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, inputAutRecord);
        assertThat(overwriteSingleRecordAction.shouldUpdateChildrenModifiedDate(inputAutRecord), is(false));
    }

    @Test
    void testAuthorityRecordHasClassificationChange_Field100Identical() throws Exception {
        String existing = "001 00 *a 19257355 *b 870979 *c 20181210134226 *d 20171102 *f a\n" +
                "100 00 *a Mernild *h Sebastian H.";

        String input = "001 00 *a 19257355 *b 870979 *c 20181211090242 *d 20171102 *f a\n" +
                "100 00 *a Mernild *h Sebastian H.\n";

        MarcRecord existingAutRecord = UpdateRecordContentTransformer.readRecordFromString(existing);
        MarcRecord inputAutRecord = UpdateRecordContentTransformer.readRecordFromString(input);

        state.setMarcRecord(inputAutRecord);
        when(state.getRawRepo().recordExists("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingAutRecord, MarcXChangeMimeType.AUTHORITY));

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, inputAutRecord);
        assertThat(overwriteSingleRecordAction.authorityRecordHasClassificationChange(inputAutRecord), is(false));
    }

    @Test
    void testAuthorityRecordHasClassificationChange_Field100Missing() throws Exception {
        String existing = "001 00 *a 19257355 *b 870979 *c 20181210134226 *d 20171102 *f a\n" +
                "100 00 *a Mernild *h Sebastian H.";

        String input = "001 00 *a 19257355 *b 870979 *c 20181211090242 *d 20171102 *f a\n";

        MarcRecord existingAutRecord = UpdateRecordContentTransformer.readRecordFromString(existing);
        MarcRecord inputAutRecord = UpdateRecordContentTransformer.readRecordFromString(input);

        state.setMarcRecord(inputAutRecord);
        when(state.getRawRepo().recordExists("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingAutRecord, MarcXChangeMimeType.AUTHORITY));

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, inputAutRecord);
        assertThat(overwriteSingleRecordAction.authorityRecordHasClassificationChange(inputAutRecord), is(true));
    }

    @Test
    void testAuthorityRecordHasClassificationChange_Field100MissingReverse() throws Exception {
        String existing = "001 00 *a 19257355 *b 870979 *c 20181210134226 *d 20171102 *f a\n";

        String input = "001 00 *a 19257355 *b 870979 *c 20181211090242 *d 20171102 *f a\n" +
                "100 00 *a Mernild *h Sebastian H.";

        MarcRecord existingAutRecord = UpdateRecordContentTransformer.readRecordFromString(existing);
        MarcRecord inputAutRecord = UpdateRecordContentTransformer.readRecordFromString(input);

        state.setMarcRecord(inputAutRecord);
        when(state.getRawRepo().recordExists("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingAutRecord, MarcXChangeMimeType.AUTHORITY));

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, inputAutRecord);
        assertThat(overwriteSingleRecordAction.authorityRecordHasClassificationChange(inputAutRecord), is(true));
    }

    @Test
    void testAuthorityRecordHasClassificationChange_FieldMissingBoth() throws Exception {
        String existing = "001 00 *a 19257355 *b 870979 *c 20181210134226 *d 20171102 *f a\n";

        String input = "001 00 *a 19257355 *b 870979 *c 20181211090242 *d 20171102 *f a\n";

        MarcRecord existingAutRecord = UpdateRecordContentTransformer.readRecordFromString(existing);
        MarcRecord inputAutRecord = UpdateRecordContentTransformer.readRecordFromString(input);

        state.setMarcRecord(inputAutRecord);
        when(state.getRawRepo().recordExists("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingAutRecord, MarcXChangeMimeType.AUTHORITY));

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, inputAutRecord);
        assertThat(overwriteSingleRecordAction.authorityRecordHasClassificationChange(inputAutRecord), is(false));
    }

    @Test
    void testAuthorityRecordHasClassificationChange_Field100IDiff() throws Exception {
        String existing = "001 00 *a 19257355 *b 870979 *c 20181210134226 *d 20171102 *f a\n" +
                "100 00 *a Mernild *h Sebastian H.";

        String input = "001 00 *a 19257355 *b 870979 *c 20181211090242 *d 20171102 *f a\n" +
                "100 00 *a Mernild *h Sebastian J.\n";

        MarcRecord existingAutRecord = UpdateRecordContentTransformer.readRecordFromString(existing);
        MarcRecord inputAutRecord = UpdateRecordContentTransformer.readRecordFromString(input);

        state.setMarcRecord(inputAutRecord);
        when(state.getRawRepo().recordExists("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingAutRecord, MarcXChangeMimeType.AUTHORITY));

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, inputAutRecord);
        assertThat(overwriteSingleRecordAction.authorityRecordHasClassificationChange(inputAutRecord), is(true));
    }

    @Test
    void testAuthorityRecordHasClassificationChange_Field100IDiffReverse() throws Exception {
        String existing = "001 00 *a 19257355 *b 870979 *c 20181210134226 *d 20171102 *f a\n" +
                "100 00 *a Mernild *h Sebastian J.";

        String input = "001 00 *a 19257355 *b 870979 *c 20181211090242 *d 20171102 *f a\n" +
                "100 00 *a Mernild *h Sebastian H.\n";

        MarcRecord existingAutRecord = UpdateRecordContentTransformer.readRecordFromString(existing);
        MarcRecord inputAutRecord = UpdateRecordContentTransformer.readRecordFromString(input);

        state.setMarcRecord(inputAutRecord);
        when(state.getRawRepo().recordExists("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingAutRecord, MarcXChangeMimeType.AUTHORITY));

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, inputAutRecord);
        assertThat(overwriteSingleRecordAction.authorityRecordHasClassificationChange(inputAutRecord), is(true));
    }

    @Test
    void testAuthorityRecordHasClassificationChange_Field110IDiff() throws Exception {
        String existing = "001 00 *a 19257355 *b 870979 *c 20181210134226 *d 20171102 *f a\n" +
                "110 00 *a Mernild *h Sebastian H.";

        String input = "001 00 *a 19257355 *b 870979 *c 20181211090242 *d 20171102 *f a\n" +
                "110 00 *a Mernild *h Sebastian J.\n";

        MarcRecord existingAutRecord = UpdateRecordContentTransformer.readRecordFromString(existing);
        MarcRecord inputAutRecord = UpdateRecordContentTransformer.readRecordFromString(input);

        state.setMarcRecord(inputAutRecord);
        when(state.getRawRepo().recordExists("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingAutRecord, MarcXChangeMimeType.AUTHORITY));

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, inputAutRecord);
        assertThat(overwriteSingleRecordAction.authorityRecordHasClassificationChange(inputAutRecord), is(true));
    }

    @Test
    void testAuthorityRecordHasClassificationChange_Field100110IDiff() throws Exception {
        String existing = "001 00 *a 19257355 *b 870979 *c 20181210134226 *d 20171102 *f a\n" +
                "110 00 *a Mernild *h Sebastian H.";

        String input = "001 00 *a 19257355 *b 870979 *c 20181211090242 *d 20171102 *f a\n" +
                "100 00 *a Mernild *h Sebastian H.\n";

        MarcRecord existingAutRecord = UpdateRecordContentTransformer.readRecordFromString(existing);
        MarcRecord inputAutRecord = UpdateRecordContentTransformer.readRecordFromString(input);

        state.setMarcRecord(inputAutRecord);
        when(state.getRawRepo().recordExists("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord("19257355", RawRepo.AUTHORITY_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingAutRecord, MarcXChangeMimeType.AUTHORITY));

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, inputAutRecord);
        assertThat(overwriteSingleRecordAction.authorityRecordHasClassificationChange(inputAutRecord), is(true));
    }

}
