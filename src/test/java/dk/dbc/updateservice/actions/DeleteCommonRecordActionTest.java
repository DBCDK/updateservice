/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.JNDIResources;
import dk.dbc.updateservice.update.LibraryGroup;
import dk.dbc.updateservice.update.RawRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class DeleteCommonRecordActionTest {
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
     * Test performAction(): Delete single common record
     * with no enrichments and no children.
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
     * <li>RemoveLinksAction: Remove any existing links to other records</li>
     * <li>DeleteRecordAction: Deletes the record</li>
     * <li>EnqueueRecordAction: Put the record in queue</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_NoChildren_NoEnrichments() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecordAndMarkForDeletion(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        when(state.getRawRepo().recordExistsMaybeDeleted(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().children(eq(record))).thenReturn(new HashSet<>());
        when(state.getRawRepo().enrichments(eq(record))).thenReturn(new HashSet<>());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record)).thenReturn(new HashSet<>());

        DeleteCommonRecordAction deleteCommonRecordAction = new DeleteCommonRecordAction(state, settings, record);
        assertThat(deleteCommonRecordAction.performAction(), is(ServiceResult.newOkResult()));

        List<ServiceAction> children = deleteCommonRecordAction.children();
        assertThat(children.size(), is(3));

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_FBS), MarcXChangeMimeType.MARCXCHANGE);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertDeleteRecordAction(iterator.next(), state.getRawRepo(), record, MarcXChangeMimeType.MARCXCHANGE);
    }

    /**
     * Test performAction(): Delete single common record with enrichments, but no children.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with a common record and one enrichment and no children.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update a common record that is marked for deletion.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>UpdateEnrichmentRecordAction: Delete enrichment record.</li>
     * <li>RemoveLinksAction: Remove any existing links to other records</li>
     * <li>DeleteRecordAction: Deletes the record</li>
     * <li>EnqueueRecordAction: Put the record in queue</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_NoChildren_WithEnrichments() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecordAndMarkForDeletion(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        int enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInt(enrichmentRecord);

        when(state.getRawRepo().recordExistsMaybeDeleted(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().children(eq(record))).thenReturn(new HashSet<>());
        when(state.getRawRepo().enrichments(eq(record))).thenReturn(AssertActionsUtil.createRecordSet(enrichmentRecord));
        when(state.getRawRepo().fetchRecord(eq(recordId), eq(enrichmentAgencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(enrichmentRecord, MarcXChangeMimeType.ENRICHMENT));
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record)).thenReturn(new HashSet<>());

        DeleteCommonRecordAction deleteCommonRecordAction = new DeleteCommonRecordAction(state, settings, record);
        assertThat(deleteCommonRecordAction.performAction(), is(ServiceResult.newOkResult()));

        MarcRecord expectedEnrichmentRecord = AssertActionsUtil.loadRecordAndMarkForDeletion(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        List<ServiceAction> children = deleteCommonRecordAction.children();
        assertThat(children.size(), is(4));

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertUpdateEnrichmentRecordAction(iterator.next(), state.getRawRepo(), expectedEnrichmentRecord, state.getLibraryRecordsHandler(), state.getHoldingsItems());
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_FBS), MarcXChangeMimeType.MARCXCHANGE);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertDeleteRecordAction(iterator.next(), state.getRawRepo(), record, MarcXChangeMimeType.MARCXCHANGE);
    }

    /**
     * Test performAction(): Delete single common record with children, but no enrichments.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with a common record and one children and no enrichment.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update a common record that is marked for deletion.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return status: FAILED_UPDATE_INTERNAL_ERROR
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_WithChildren_NoEnrichments() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecordAndMarkForDeletion(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);
        MarcRecord volumeRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);

        when(state.getRawRepo().recordExistsMaybeDeleted(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().children(eq(record))).thenReturn(AssertActionsUtil.createRecordSet(volumeRecord));
        when(state.getRawRepo().enrichments(eq(record))).thenReturn(new HashSet<>());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record)).thenReturn(new HashSet<>());

        DeleteCommonRecordAction deleteCommonRecordAction = new DeleteCommonRecordAction(state, settings, record);
        String message = String.format(state.getMessages().getString("delete.record.children.error"), recordId);
        assertThat(deleteCommonRecordAction.performAction(), is(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message)));
        assertThat(deleteCommonRecordAction.children().isEmpty(), is(true));
    }

    /**
     * Test performAction(): Delete single common record with enrichments and
     * children.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with a common record and one enrichment and one children.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update a common record that is marked for deletion.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return status: FAILED_UPDATE_INTERNAL_ERROR
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_WithChildren_WithEnrichments() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecordAndMarkForDeletion(AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);
        MarcRecord volumeRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE);
        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_MAIN_ENRICHMENT_RECORD_RESOURCE);

        when(state.getRawRepo().recordExistsMaybeDeleted(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().children(eq(record))).thenReturn(AssertActionsUtil.createRecordSet(volumeRecord));
        when(state.getRawRepo().enrichments(eq(record))).thenReturn(AssertActionsUtil.createRecordSet(enrichmentRecord));
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record)).thenReturn(new HashSet<>());

        DeleteCommonRecordAction deleteCommonRecordAction = new DeleteCommonRecordAction(state, settings, record);
        String message = String.format(state.getMessages().getString("delete.record.children.error"), recordId);
        assertThat(deleteCommonRecordAction.performAction(), is(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message)));
        assertThat(deleteCommonRecordAction.children().isEmpty(), is(true));
    }

    @Test
    void testPerformAction_DeleteLittolkChildren() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecordAndMarkForDeletion(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecord littolkEnrichment = AssertActionsUtil.loadRecordAndMarkForDeletion(AssertActionsUtil.LITTOLK_ENRICHMENT);
        MarcRecord littolkRecord = AssertActionsUtil.loadRecordAndMarkForDeletion(AssertActionsUtil.LITTOLK_COMMON);

        String recordId = AssertActionsUtil.getRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        String littolkRecordId = AssertActionsUtil.getRecordId(littolkRecord);

        when(state.getRawRepo().recordExistsMaybeDeleted(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExistsMaybeDeleted(eq(littolkRecordId), eq(RawRepo.LITTOLK_AGENCY))).thenReturn(true);
        when(state.getRawRepo().recordExistsMaybeDeleted(eq(littolkRecordId), eq(RawRepo.DBC_ENRICHMENT))).thenReturn(true);
        when(state.getRawRepo().children(eq(record))).thenReturn(AssertActionsUtil.createRecordSet(littolkRecord));
        when(state.getRawRepo().enrichments(eq(record))).thenReturn(new HashSet<>());
        when(state.getRawRepo().enrichments(eq(littolkRecord))).thenReturn(new HashSet<>());
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record)).thenReturn(new HashSet<>());
        when(state.getRawRepo().fetchRecord(eq(littolkRecordId), eq(RawRepo.DBC_ENRICHMENT))).thenReturn(AssertActionsUtil.createRawRepoRecord(littolkEnrichment, MarcXChangeMimeType.ENRICHMENT));
        when(state.getRawRepo().fetchRecord(eq(littolkRecordId), eq(RawRepo.LITTOLK_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(littolkRecord, MarcXChangeMimeType.MARCXCHANGE));

        MarcRecord littolkEnrichmentRecordMarkedForDeletion = AssertActionsUtil.loadRecord(AssertActionsUtil.LITTOLK_ENRICHMENT);
        new MarcRecordWriter(littolkEnrichmentRecordMarkedForDeletion).markForDeletion();

        DeleteCommonRecordAction deleteCommonRecordAction = new DeleteCommonRecordAction(state, settings, record);
        assertThat(deleteCommonRecordAction.performAction(), is(ServiceResult.newOkResult()));

        List<ServiceAction> children = deleteCommonRecordAction.children();
        assertThat(children.size(), is(5));

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertUpdateEnrichmentRecordAction(iterator.next(), state.getRawRepo(), littolkEnrichmentRecordMarkedForDeletion, state.getLibraryRecordsHandler(), state.getHoldingsItems());
        AssertActionsUtil.assertDeleteCommonRecordAction(iterator.next(), state.getRawRepo(), littolkRecord, state.getLibraryRecordsHandler(), state.getHoldingsItems(), settings.getProperty(state.getRawRepoProviderId()));
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_FBS), MarcXChangeMimeType.MARCXCHANGE);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertDeleteRecordAction(iterator.next(), state.getRawRepo(), record, MarcXChangeMimeType.MARCXCHANGE);
    }
}
