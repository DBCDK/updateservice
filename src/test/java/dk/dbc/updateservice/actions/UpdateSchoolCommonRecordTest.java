/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.updateservice.update.LibraryGroup;
import dk.dbc.updateservice.update.RawRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ListIterator;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class UpdateSchoolCommonRecordTest {
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
     * Test performAction(): Create new single common school record with no school
     * enrichments in rawrepo.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with a common record.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update a common school record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>UpdateEnrichmentRecordAction: Update the common school record</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_CreateRecord_WithNoSchoolEnrichments() throws Exception {
        MarcRecord commonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String bibliographicRecordId = AssertActionsUtil.getBibliographicRecordId(commonRecord);
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SCHOOL_RECORD_RESOURCE);

        when(state.getRawRepo().recordExists(eq(bibliographicRecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(bibliographicRecordId), eq(RawRepo.SCHOOL_COMMON_AGENCY))).thenReturn(false);

        UpdateSchoolCommonRecord updateSchoolCommonRecord = new UpdateSchoolCommonRecord(state, settings, record);
        assertThat(updateSchoolCommonRecord.performAction(), is(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = updateSchoolCommonRecord.children().listIterator();
        AssertActionsUtil.assertUpdateEnrichmentRecordAction(iterator.next(), state.getRawRepo(), record, state.getLibraryRecordsHandler(), state.getHoldingsItems());
        assertThat(iterator.hasNext(), is(false));
    }

    /**
     * Test performAction(): Updates a single common school record with no school
     * enrichments in rawrepo.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * Rawrepo with:
     * <ul>
     * <li>A common record <code>c1</code></li>
     * <li>A common school record <code>s1</code></li>
     * </ul>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update the common school record <code>s1</code> with new data.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>UpdateEnrichmentRecordAction: Update the common school record</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_UpdateRecord_WithNoSchoolEnrichments() throws Exception {
        MarcRecord commonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String bibliographicRecordId = AssertActionsUtil.getBibliographicRecordId(commonRecord);
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SCHOOL_RECORD_RESOURCE);

        when(state.getRawRepo().recordExists(eq(bibliographicRecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(bibliographicRecordId), eq(RawRepo.SCHOOL_COMMON_AGENCY))).thenReturn(true);

        UpdateSchoolCommonRecord instance = new UpdateSchoolCommonRecord(state, settings, record);
        assertThat(instance.performAction(), is(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertUpdateEnrichmentRecordAction(iterator.next(), state.getRawRepo(), record, state.getLibraryRecordsHandler(), state.getHoldingsItems());
        assertThat(iterator.hasNext(), is(false));
    }

    /**
     * Test performAction(): Create new single common school record with existing school
     * enrichments in rawrepo.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * Rawrepo:
     * <ul>
     * <li>Common record.</li>
     * <li>Enrichment record for a school library.</li>
     * </ul>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Create a common school record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>UpdateEnrichmentRecordAction: Update the common school record</li>
     * <li>LinkRecordAction: Link the school enrichment record to the common school record.</li>
     * <li>EnqueueRecordAction: Enqueue the school enrichment record.</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_CreateRecord_WithSchoolEnrichments() throws Exception {
        MarcRecord commonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String bibliographicRecordId = AssertActionsUtil.getBibliographicRecordId(commonRecord);
        MarcRecord schoolRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.SCHOOL_RECORD_RESOURCE);
        int schoolAgencyId = AssertActionsUtil.getAgencyIdAsInt(schoolRecord);
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SCHOOL_RECORD_RESOURCE);

        when(state.getRawRepo().recordExists(eq(bibliographicRecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(bibliographicRecordId), eq(schoolAgencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(bibliographicRecordId), eq(RawRepo.SCHOOL_COMMON_AGENCY))).thenReturn(false);
        when(state.getRawRepo().agenciesForRecord(eq(bibliographicRecordId))).thenReturn(AssertActionsUtil.createAgenciesSet(schoolAgencyId));
        when(state.getRawRepo().fetchRecord(eq(bibliographicRecordId), eq(schoolAgencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(schoolRecord, MarcXChangeMimeType.ENRICHMENT));

        UpdateSchoolCommonRecord updateSchoolCommonRecord = new UpdateSchoolCommonRecord(state, settings, record);
        assertThat(updateSchoolCommonRecord.performAction(), is(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = updateSchoolCommonRecord.children().listIterator();
        AssertActionsUtil.assertUpdateEnrichmentRecordAction(iterator.next(), state.getRawRepo(), record, state.getLibraryRecordsHandler(), state.getHoldingsItems());
        AssertActionsUtil.assertLinkRecordAction(iterator.next(), state.getRawRepo(), schoolRecord, record);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), schoolRecord, "RAWREPO_PROVIDER_ID_FBS", MarcXChangeMimeType.ENRICHMENT);

        assertThat(iterator.hasNext(), is(false));
    }

    /**
     * Test performAction(): Delete a single common school record with no school
     * enrichments in rawrepo.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * Rawrepo with:
     * <ul>
     * <li>A common record <code>c1</code></li>
     * <li>A common school record <code>s1</code></li>
     * </ul>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Delete the common school record <code>s1</code> with new data.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>UpdateEnrichmentRecordAction: Update the common school record</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_DeleteRecord_WithNoSchoolEnrichments() throws Exception {
        MarcRecord commonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String bibliographicRecordId = AssertActionsUtil.getBibliographicRecordId(commonRecord);
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SCHOOL_RECORD_RESOURCE);
        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.markForDeletion();

        when(state.getRawRepo().recordExists(eq(bibliographicRecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(bibliographicRecordId), eq(RawRepo.SCHOOL_COMMON_AGENCY))).thenReturn(true);

        UpdateSchoolCommonRecord instance = new UpdateSchoolCommonRecord(state, settings, record);
        assertThat(instance.performAction(), is(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertUpdateEnrichmentRecordAction(iterator.next(), state.getRawRepo(), record, state.getLibraryRecordsHandler(), state.getHoldingsItems());
        assertThat(iterator.hasNext(), is(false));
    }

    /**
     * Test performAction(): Delete a single common school record with existing school
     * enrichments in rawrepo.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * Rawrepo:
     * <ul>
     * <li>Common record.</li>
     * <li>Common school record.</li>
     * <li>Enrichment record for a school library.</li>
     * </ul>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Delete the common school record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>LinkRecordAction: Link the school enrichment record to the common record.</li>
     * <li>EnqueueRecordAction: Enqueue the school enrichment record.</li>
     * <li>UpdateEnrichmentRecordAction: Update the common school record</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_DeleteRecord_WithSchoolEnrichments() throws Exception {
        MarcRecord commonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String bibliographicRecordId = AssertActionsUtil.getBibliographicRecordId(commonRecord);
        MarcRecord schoolRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.SCHOOL_RECORD_RESOURCE);
        int schoolAgencyId = AssertActionsUtil.getAgencyIdAsInt(schoolRecord);
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SCHOOL_RECORD_RESOURCE);
        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.markForDeletion();

        when(state.getRawRepo().recordExists(eq(bibliographicRecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(bibliographicRecordId), eq(schoolAgencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(bibliographicRecordId), eq(RawRepo.SCHOOL_COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().agenciesForRecord(eq(bibliographicRecordId))).thenReturn(AssertActionsUtil.createAgenciesSet(schoolAgencyId));
        when(state.getRawRepo().fetchRecord(eq(bibliographicRecordId), eq(schoolAgencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(schoolRecord, MarcXChangeMimeType.ENRICHMENT));

        UpdateSchoolCommonRecord instance = new UpdateSchoolCommonRecord(state, settings, record);
        assertThat(instance.performAction(), is(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertLinkRecordAction(iterator.next(), state.getRawRepo(), schoolRecord, commonRecord);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), schoolRecord, settings.getProperty(state.getRawRepoProviderId()), MarcXChangeMimeType.ENRICHMENT);
        AssertActionsUtil.assertUpdateEnrichmentRecordAction(iterator.next(), state.getRawRepo(), record, state.getLibraryRecordsHandler(), state.getHoldingsItems());
        assertThat(iterator.hasNext(), is(false));
    }
}
