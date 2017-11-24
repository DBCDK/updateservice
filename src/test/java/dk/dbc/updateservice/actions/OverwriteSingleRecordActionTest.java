/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.update.OpenAgencyService;
import dk.dbc.updateservice.update.RawRepo;
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

public class OverwriteSingleRecordActionTest {
    private GlobalActionState state;
    private Properties settings;
    OpenAgencyService.LibraryGroup libraryGroup = OpenAgencyService.LibraryGroup.FBS;

    @Before
    public void before() throws IOException {
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
    public void testPerformAction_NoClassifications() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(recordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(eq(record))).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getOpenAgencyService().hasFeature(Integer.toString(agencyId), LibraryRuleHandler.Rule.USE_ENRICHMENTS)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(record)).thenReturn(false);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteSingleRecordAction.children();
        Assert.assertThat(children.size(), is(4));

        AssertActionsUtil.assertStoreRecordAction(children.get(0), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(children.get(1), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(children.get(2), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(children.get(3), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
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
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(recordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(eq(record))).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getOpenAgencyService().hasFeature(Integer.toString(agencyId), LibraryRuleHandler.Rule.USE_ENRICHMENTS)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(record))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(record), eq(record))).thenReturn(false);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteSingleRecordAction.children();
        Assert.assertThat(children.size(), is(4));

        AssertActionsUtil.assertStoreRecordAction(children.get(0), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(children.get(1), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(children.get(2), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(children.get(3), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
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
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(recordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(eq(record))).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record)).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getOpenAgencyService().hasFeature(Integer.toString(agencyId), LibraryRuleHandler.Rule.USE_ENRICHMENTS)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(record))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(record), eq(record))).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteSingleRecordAction.children();
        Assert.assertThat(children.size(), is(4));

        AssertActionsUtil.assertStoreRecordAction(children.get(0), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(children.get(1), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(children.get(2), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(children.get(3), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
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
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(recordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(eq(record))).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(eq(record))).thenReturn(AssertActionsUtil.createAgenciesSet(700100));
        when(state.getOpenAgencyService().hasFeature(eq("700100"), eq(LibraryRuleHandler.Rule.USE_ENRICHMENTS))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(record))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(record), eq(record))).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteSingleRecordAction.children();
        Assert.assertThat(children.size(), is(5));

        AssertActionsUtil.assertStoreRecordAction(children.get(0), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(children.get(1), state.getRawRepo(), record);
        AssertActionsUtil.assertCreateEnrichmentAction(children.get(2), state.getRawRepo(), record, "700100", null);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(children.get(3), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(children.get(4), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
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
    public void testPerformAction_ChangedClassifications_Holdings_CreateEnrichment_LocalAgencyNoEnrichments() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(recordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(eq(record))).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsForId(eq(recordId))).thenReturn(AssertActionsUtil.createAgenciesSet(700100));
        when(state.getOpenAgencyService().hasFeature(eq("700100"), eq(LibraryRuleHandler.Rule.USE_ENRICHMENTS))).thenReturn(false);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(record))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(record), eq(record))).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteSingleRecordAction.children();
        Assert.assertThat(children.size(), is(4));

        AssertActionsUtil.assertStoreRecordAction(children.get(0), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(children.get(1), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(children.get(2), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(children.get(3), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
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
    public void testPerformAction_ChangedClassifications_Holdings_ShouldNotCreateEnrichment() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(recordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(eq(record))).thenReturn(AssertActionsUtil.createAgenciesSet());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsForId(eq(recordId))).thenReturn(AssertActionsUtil.createAgenciesSet(700100));
        when(state.getOpenAgencyService().hasFeature(Integer.toString(agencyId), LibraryRuleHandler.Rule.USE_ENRICHMENTS)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(record))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(record), eq(record))).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteSingleRecordAction.children();
        Assert.assertThat(children.size(), is(4));

        AssertActionsUtil.assertStoreRecordAction(children.get(0), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(children.get(1), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(children.get(2), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(children.get(3), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
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
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);
        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        int enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInt(enrichmentRecord);

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(recordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(eq(record))).thenReturn(AssertActionsUtil.createAgenciesSet(enrichmentAgencyId));
        when(state.getRawRepo().recordExists(eq(recordId), eq(enrichmentAgencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(recordId), eq(enrichmentAgencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(enrichmentRecord, MarcXChangeMimeType.ENRICHMENT));
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(eq(record))).thenReturn(AssertActionsUtil.createAgenciesSet(enrichmentAgencyId));
        when(state.getOpenAgencyService().hasFeature(eq(Integer.toString(enrichmentAgencyId)), eq(LibraryRuleHandler.Rule.USE_ENRICHMENTS))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(record))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(record), eq(record))).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteSingleRecordAction.children();
        Assert.assertThat(children.size(), is(5));

        AssertActionsUtil.assertStoreRecordAction(children.get(0), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(children.get(1), state.getRawRepo(), record);
        AssertActionsUtil.assertUpdateClassificationsInEnrichmentRecordAction(children.get(2), state.getRawRepo(), record, enrichmentRecord);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(children.get(3), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(children.get(4), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
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
    public void testPerformAction_ChangedClassifications_Holdings_UpdateEnrichment_LocalAgencyNoEnrichments() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        int enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInt(enrichmentRecord);

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(recordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(eq(record))).thenReturn(AssertActionsUtil.createAgenciesSet(enrichmentAgencyId));
        when(state.getRawRepo().recordExists(eq(recordId), eq(enrichmentAgencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(recordId), eq(enrichmentAgencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(enrichmentRecord, MarcXChangeMimeType.ENRICHMENT));
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(eq(record))).thenReturn(AssertActionsUtil.createAgenciesSet(enrichmentAgencyId));
        when(state.getOpenAgencyService().hasFeature(eq(Integer.toString(enrichmentAgencyId)), eq(LibraryRuleHandler.Rule.USE_ENRICHMENTS))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(record))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(record), eq(record))).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteSingleRecordAction.children();
        Assert.assertThat(children.size(), is(5));

        AssertActionsUtil.assertStoreRecordAction(children.get(0), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(children.get(1), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(children.get(3), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(children.get(4), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
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
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        int enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInt(enrichmentRecord);

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(recordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(eq(record))).thenReturn(AssertActionsUtil.createAgenciesSet(enrichmentAgencyId));
        when(state.getRawRepo().recordExists(eq(recordId), eq(enrichmentAgencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(recordId), eq(enrichmentAgencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(enrichmentRecord, MarcXChangeMimeType.ENRICHMENT));
        int newEnrichmentAgencyId = enrichmentAgencyId + 100;
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(eq(record))).thenReturn(AssertActionsUtil.createAgenciesSet(enrichmentAgencyId, newEnrichmentAgencyId));
        when(state.getOpenAgencyService().hasFeature(eq(Integer.toString(enrichmentAgencyId)), eq(LibraryRuleHandler.Rule.USE_ENRICHMENTS))).thenReturn(true);
        when(state.getOpenAgencyService().hasFeature(eq(Integer.toString(newEnrichmentAgencyId)), eq(LibraryRuleHandler.Rule.USE_ENRICHMENTS))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(record))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(record), eq(record))).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteSingleRecordAction.children();
        Assert.assertThat(children.size(), is(6));

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertUpdateClassificationsInEnrichmentRecordAction(iterator.next(), state.getRawRepo(), record, enrichmentRecord);
        AssertActionsUtil.assertCreateEnrichmentAction(iterator.next(), state.getRawRepo(), record, Integer.toString(newEnrichmentAgencyId), null);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
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
    public void testPerformAction_ChangedClassifications_Holdings_CreateAndUpdateEnrichment_LocalAgencyNoEnrichments() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        int enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInt(enrichmentRecord);

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(recordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(eq(record))).thenReturn(AssertActionsUtil.createAgenciesSet(enrichmentAgencyId));
        when(state.getRawRepo().recordExists(eq(recordId), eq(enrichmentAgencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(recordId), eq(enrichmentAgencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(enrichmentRecord, MarcXChangeMimeType.ENRICHMENT));
        int newEnrichmentAgencyId = enrichmentAgencyId + 100;
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsForId(eq(recordId))).thenReturn(AssertActionsUtil.createAgenciesSet(enrichmentAgencyId, newEnrichmentAgencyId));
        when(state.getOpenAgencyService().hasFeature(eq(Integer.toString(enrichmentAgencyId)), eq(LibraryRuleHandler.Rule.USE_ENRICHMENTS))).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature(eq(Integer.toString(newEnrichmentAgencyId)), eq(LibraryRuleHandler.Rule.USE_ENRICHMENTS))).thenReturn(false);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(record))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(record), eq(record))).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteSingleRecordAction.children();
        Assert.assertThat(children.size(), is(4));

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
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
    public void testPerformAction_ChangedClassifications_Holdings_ShouldNotCreateButUpdateEnrichment() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(record).addOrReplaceSubfield("032", "a", "DBI999999");
        String recordId = AssertActionsUtil.getRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        int enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInt(enrichmentRecord);

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(recordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(eq(record))).thenReturn(AssertActionsUtil.createAgenciesSet(enrichmentAgencyId));
        when(state.getRawRepo().recordExists(eq(recordId), eq(enrichmentAgencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(recordId), eq(enrichmentAgencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(enrichmentRecord, MarcXChangeMimeType.ENRICHMENT));
        int newEnrichmentAgencyId = enrichmentAgencyId + 100;
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(eq(record))).thenReturn(AssertActionsUtil.createAgenciesSet(enrichmentAgencyId, newEnrichmentAgencyId));
        when(state.getOpenAgencyService().hasFeature(eq(Integer.toString(enrichmentAgencyId)), eq(LibraryRuleHandler.Rule.USE_ENRICHMENTS))).thenReturn(true);
        when(state.getOpenAgencyService().hasFeature(eq(Integer.toString(newEnrichmentAgencyId)), eq(LibraryRuleHandler.Rule.USE_ENRICHMENTS))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(record))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(record), eq(record))).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteSingleRecordAction.children();
        Assert.assertThat(children.size(), is(5));

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertUpdateClassificationsInEnrichmentRecordAction(iterator.next(), state.getRawRepo(), record, enrichmentRecord);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
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
    public void testPerformAction_ChangedClassifications_Holdings_LocalAgencyNoEnrichments() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        int enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInt(enrichmentRecord);

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(recordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().agenciesForRecord(eq(record))).thenReturn(AssertActionsUtil.createAgenciesSet(enrichmentAgencyId));
        when(state.getRawRepo().recordExists(eq(recordId), eq(enrichmentAgencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(recordId), eq(enrichmentAgencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(enrichmentRecord, MarcXChangeMimeType.ENRICHMENT));
        int newEnrichmentAgencyId = enrichmentAgencyId + 100;
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsForId(eq(recordId))).thenReturn(AssertActionsUtil.createAgenciesSet(enrichmentAgencyId, newEnrichmentAgencyId));
        when(state.getOpenAgencyService().hasFeature(eq(Integer.toString(enrichmentAgencyId)), eq(LibraryRuleHandler.Rule.USE_ENRICHMENTS))).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature(eq(Integer.toString(newEnrichmentAgencyId)), eq(LibraryRuleHandler.Rule.USE_ENRICHMENTS))).thenReturn(false);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(record))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(record), eq(record))).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = overwriteSingleRecordAction.children();
        Assert.assertThat(children.size(), is(4));

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
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
    public void testPerformAction_SameClassifications_MoveEnrichments() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";
        MarcRecord c1 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId);
        MarcRecord c2 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c2RecordId);
        MarcRecord e1 = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE, c2RecordId);

        MarcRecord record = new MarcRecord(c1);
        new MarcRecordWriter(record).addOrReplaceSubfield("002", "a", c2RecordId);
        String e1RecordId = AssertActionsUtil.getRecordId(e1);
        int e1AgencyId = AssertActionsUtil.getAgencyIdAsInt(e1);

        when(state.getRawRepo().recordExists(eq(c1RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(c2RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(e1RecordId), eq(e1AgencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(c1RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(c1, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecord(eq(c2RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(c2, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecord(eq(e1RecordId), eq(e1AgencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(e1, MarcXChangeMimeType.ENRICHMENT));
        when(state.getRawRepo().enrichments(eq(new RecordId(c2RecordId, RawRepo.COMMON_AGENCY)))).thenReturn(AssertActionsUtil.createRecordSet(e1));
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsForId(eq(e1RecordId))).thenReturn(AssertActionsUtil.createAgenciesSet(e1AgencyId));
        when(state.getOpenAgencyService().hasFeature(Integer.toString(e1AgencyId), LibraryRuleHandler.Rule.USE_ENRICHMENTS)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(c1))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(record))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(c1), eq(record))).thenReturn(false);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = overwriteSingleRecordAction.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertMoveEnrichmentRecordAction(iterator.next(), state.getRawRepo(), e1, record, settings);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
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
    public void testPerformAction_SameClassifications_MoveEnrichments_LocalAgencyNoEnrichments() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";
        MarcRecord c1 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId);
        MarcRecord c2 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c2RecordId);
        MarcRecord e1 = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE, c2RecordId);

        MarcRecord record = new MarcRecord(c1);
        new MarcRecordWriter(record).addOrReplaceSubfield("002", "a", c2RecordId);

        String e1RecordId = AssertActionsUtil.getRecordId(e1);
        int e1AgencyId = AssertActionsUtil.getAgencyIdAsInt(e1);

        when(state.getRawRepo().recordExists(eq(c1RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(c2RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(e1RecordId), eq(e1AgencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(c1RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(c1, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecord(eq(c2RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(c2, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecord(eq(e1RecordId), eq(e1AgencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(e1, MarcXChangeMimeType.ENRICHMENT));
        when(state.getRawRepo().enrichments(eq(new RecordId(c2RecordId, RawRepo.COMMON_AGENCY)))).thenReturn(AssertActionsUtil.createRecordSet(e1));
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsForId(eq(e1RecordId))).thenReturn(AssertActionsUtil.createAgenciesSet(e1AgencyId));
        when(state.getOpenAgencyService().hasFeature(eq(Integer.toString(e1AgencyId)), eq(LibraryRuleHandler.Rule.USE_ENRICHMENTS))).thenReturn(false);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(c1))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(record))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(c1), eq(record))).thenReturn(false);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = overwriteSingleRecordAction.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
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
    public void testPerformAction_SameClassifications_NoChangeIn002Links() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";
        MarcRecord c1 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId);
        new MarcRecordWriter(c1).addOrReplaceSubfield("002", "a", c2RecordId);
        MarcRecord e1 = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE, c2RecordId);
        MarcRecord record = new MarcRecord(c1);
        String e1RecordId = AssertActionsUtil.getRecordId(e1);
        int e1AgencyId = AssertActionsUtil.getAgencyIdAsInt(e1);

        when(state.getRawRepo().recordExists(eq(c1RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(c2RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(e1RecordId), eq(e1AgencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(c1RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(c1, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecord(eq(e1RecordId), eq(e1AgencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(e1, MarcXChangeMimeType.ENRICHMENT));
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsForId(eq(e1RecordId))).thenReturn(AssertActionsUtil.createAgenciesSet(e1AgencyId));
        when(state.getOpenAgencyService().hasFeature(Integer.toString(e1AgencyId), LibraryRuleHandler.Rule.USE_ENRICHMENTS)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(c1))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(record))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(c1), eq(record))).thenReturn(false);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = overwriteSingleRecordAction.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
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
    public void testPerformAction_ChangedClassifications_002Links_HoldingsButNoEnrichments_SourceIsPublished_DestinationIsPublished() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";
        final int localAgencyId = 700400;
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId(Integer.toString(localAgencyId));
        MarcRecord c1 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId);
        MarcRecord c2 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c2RecordId);
        MarcRecord record = new MarcRecord(c1);
        new MarcRecordWriter(record).addOrReplaceSubfield("002", "a", c2RecordId);

        when(state.getRawRepo().recordExists(eq(c1RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(c2RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(c1RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(c1, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecord(eq(c2RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(c2, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsForId(eq(c2RecordId))).thenReturn(AssertActionsUtil.createAgenciesSet(localAgencyId));
        when(state.getOpenAgencyService().hasFeature(Integer.toString(localAgencyId), LibraryRuleHandler.Rule.USE_ENRICHMENTS)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(c1))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(record))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(c1), eq(record))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(c2), eq(record))).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = overwriteSingleRecordAction.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertCreateEnrichmentAction(iterator.next(), state.getRawRepo(), c2, Integer.toString(localAgencyId), c1RecordId);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
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
    public void testPerformAction_ChangedClassifications_002Links_HoldingsButNoEnrichments_SourceIsNotPublished_DestinationIsPublished() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";
        final int localAgencyId = 700400;
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId(Integer.toString(localAgencyId));
        MarcRecord c1 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId);
        MarcRecord c2 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c2RecordId);
        MarcRecord record = new MarcRecord(c1);
        new MarcRecordWriter(record).addOrReplaceSubfield("002", "a", c2RecordId);
        new MarcRecordWriter(c2).addOrReplaceSubfield("032", "a", "DBI999999");

        when(state.getRawRepo().recordExists(eq(c1RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(c2RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(c1RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(c1, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecord(eq(c2RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(c2, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsForId(eq(c2RecordId))).thenReturn(AssertActionsUtil.createAgenciesSet(localAgencyId));
        when(state.getOpenAgencyService().hasFeature(Integer.toString(localAgencyId), LibraryRuleHandler.Rule.USE_ENRICHMENTS)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(c1))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(record))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(c1), eq(record))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(record), eq(c2))).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = overwriteSingleRecordAction.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
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
    public void testPerformAction_ChangedClassifications_002Links_HoldingsButNoEnrichments_SourceIsNotPublished_DestinationIsNotPublished() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";
        final int localAgencyId = 700400;
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId(Integer.toString(localAgencyId));
        MarcRecord c1 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId);
        MarcRecord c2 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c2RecordId);
        MarcRecord record = new MarcRecord(c1);
        new MarcRecordWriter(record).addOrReplaceSubfield("002", "a", c2RecordId);

        when(state.getRawRepo().recordExists(eq(c1RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(c2RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(c1RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(c1, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecord(eq(c2RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(c2, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsForId(eq(c2RecordId))).thenReturn(AssertActionsUtil.createAgenciesSet(localAgencyId));
        when(state.getOpenAgencyService().hasFeature(Integer.toString(localAgencyId), LibraryRuleHandler.Rule.USE_ENRICHMENTS)).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(c1))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(record))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(c1), eq(record))).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = overwriteSingleRecordAction.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
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
    public void testPerformAction_ChangedClassifications_002Links_HoldingsButNoEnrichments_LocalAgencyNoEnrichments() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";
        final int localAgencyId = 700400;
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId(Integer.toString(localAgencyId));
        MarcRecord c1 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId);
        MarcRecord c2 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c2RecordId);
        MarcRecord record = new MarcRecord(c1);
        new MarcRecordWriter(record).addOrReplaceSubfield("002", "a", c2RecordId);

        when(state.getRawRepo().recordExists(eq(c1RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(c2RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(c1RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(c1, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecord(eq(c2RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(c2, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsForId(eq(c2RecordId))).thenReturn(AssertActionsUtil.createAgenciesSet(localAgencyId));
        when(state.getOpenAgencyService().hasFeature(eq(Integer.toString(localAgencyId)), eq(LibraryRuleHandler.Rule.USE_ENRICHMENTS))).thenReturn(false);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(c1))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(record))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(c1), eq(record))).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = overwriteSingleRecordAction.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
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
    public void testPerformAction_ChangedClassifications_002Links_MoveEnrichments() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";
        final int localAgencyId = 700400;
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId(Integer.toString(localAgencyId));
        MarcRecord c1 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId);
        MarcRecord c2 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c2RecordId);
        MarcRecord e1 = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE, c2RecordId);
        MarcRecord record = new MarcRecord(c1);
        new MarcRecordWriter(record).addOrReplaceSubfield("002", "a", c2RecordId);
        String e1RecordId = AssertActionsUtil.getRecordId(e1);
        int e1AgencyId = AssertActionsUtil.getAgencyIdAsInt(e1);

        when(state.getRawRepo().recordExists(eq(c1RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(c2RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(e1RecordId), eq(e1AgencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(c1RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(c1, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecord(eq(c2RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(c2, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecord(eq(e1RecordId), eq(e1AgencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(e1, MarcXChangeMimeType.ENRICHMENT));
        when(state.getRawRepo().enrichments(eq(new RecordId(c2RecordId, RawRepo.COMMON_AGENCY)))).thenReturn(AssertActionsUtil.createRecordSet(e1));
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsForId(eq(e1RecordId))).thenReturn(AssertActionsUtil.createAgenciesSet(e1AgencyId));
        when(state.getOpenAgencyService().hasFeature(eq(Integer.toString(e1AgencyId)), eq(LibraryRuleHandler.Rule.USE_ENRICHMENTS))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(c1))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(record))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(c1), eq(record))).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = overwriteSingleRecordAction.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertMoveEnrichmentRecordAction(iterator.next(), state.getRawRepo(), e1, record, settings);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
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
    public void testPerformAction_ChangedClassifications_002Links_LocalAgencyEnrichments() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";
        final int localAgencyId = 700400;
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId(Integer.toString(localAgencyId));
        MarcRecord c1 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId);
        MarcRecord c2 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c2RecordId);
        MarcRecord e1 = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE, c2RecordId);
        MarcRecord record = new MarcRecord(c1);
        new MarcRecordWriter(record).addOrReplaceSubfield("002", "a", c2RecordId);
        String e1RecordId = AssertActionsUtil.getRecordId(e1);
        int e1AgencyId = AssertActionsUtil.getAgencyIdAsInt(e1);

        when(state.getRawRepo().recordExists(eq(c1RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(c2RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(e1RecordId), eq(e1AgencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(c1RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(c1, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecord(eq(c2RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(c2, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecord(eq(e1RecordId), eq(e1AgencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(e1, MarcXChangeMimeType.ENRICHMENT));
        when(state.getRawRepo().enrichments(eq(new RecordId(c2RecordId, RawRepo.COMMON_AGENCY)))).thenReturn(AssertActionsUtil.createRecordSet(e1));
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsForId(eq(e1RecordId))).thenReturn(AssertActionsUtil.createAgenciesSet(e1AgencyId));
        when(state.getOpenAgencyService().hasFeature(eq(Integer.toString(e1AgencyId)), eq(LibraryRuleHandler.Rule.USE_ENRICHMENTS))).thenReturn(false);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(c1))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(record))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(c1), eq(record))).thenReturn(true);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = overwriteSingleRecordAction.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
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
    public void testPerformAction_002Link_DoNotExist() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";
        MarcRecord c1 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId);
        MarcRecord record = new MarcRecord(c1);
        new MarcRecordWriter(record).addOrReplaceSubfield("002", "a", c2RecordId);

        when(state.getRawRepo().recordExists(eq(c1RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(c2RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(false);
        when(state.getRawRepo().fetchRecord(eq(c1RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(c1, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(c1))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationData(eq(record))).thenReturn(true);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(eq(c1), eq(record))).thenReturn(false);

        OverwriteSingleRecordAction overwriteSingleRecordAction = new OverwriteSingleRecordAction(state, settings, record);
        assertThat(overwriteSingleRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = overwriteSingleRecordAction.children().listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertLinkAuthorityRecordsAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.MARCXCHANGE);
        assertThat(iterator.hasNext(), is(false));
    }
}
