/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.updateservice.update.OpenAgencyService;
import dk.dbc.updateservice.update.RawRepo;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ListIterator;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class MoveEnrichmentRecordActionTest {
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
     * Test MoveEnrichmentRecordAction.performAction(): Move enrichment to new common
     * record that exists.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * <ol>
     * <li>A common record <code>c1</code> that is published</li>
     * <li>A common record <code>c2</code> that is published</li>
     * <li>An enrichment record <code>e1</code> to <code>c1</code></li>
     * </ol>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Perform actions to move <code>e1</code> from <code>c1</code> to <code>c2</code>.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * The enrichment record is moved to <code>c2</code> by creating the following child actions:
     * <ol>
     * <li>UpdateEnrichmentRecordAction: To delete the old enrichment record in rawrepo.</li>
     * <li>UpdateClassificationsInEnrichmentRecordAction: Store the enrichment record and associate it with <code>c2</code></li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_CommonRecordPublished() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";
        MarcRecord c1 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId);
        MarcRecord e1 = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE, c1RecordId);
        String e1RecordId = AssertActionsUtil.getRecordId(e1);
        int e1AgencyId = AssertActionsUtil.getAgencyIdAsInt(e1);
        new MarcRecordWriter(c1).addOrReplaceSubfield("z98", "a", "testPerformAction_CommonRecordPublished");

        when(state.getRawRepo().recordExists(eq(c1RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(c2RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(e1RecordId), eq(e1AgencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(c1RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(c1, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getLibraryRecordsHandler().isRecordInProduction(eq(c1))).thenReturn(true);

        MoveEnrichmentRecordAction moveEnrichmentRecordAction = new MoveEnrichmentRecordAction(state, settings, e1, true, true);
        moveEnrichmentRecordAction.setTargetRecordId(c2RecordId);
        assertThat(moveEnrichmentRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));
        ListIterator<ServiceAction> iterator = moveEnrichmentRecordAction.children.listIterator();

        MarcRecord e1Deleted = AssertActionsUtil.loadRecordAndMarkForDeletion(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE, c1RecordId);
        AssertActionsUtil.assertUpdateEnrichmentRecordAction(iterator.next(), state.getRawRepo(), e1Deleted, state.getLibraryRecordsHandler(), state.getHoldingsItems(), settings.getProperty(state.getRawRepoProviderId()));

        MarcRecord e1Moved = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE, c2RecordId);
        AssertActionsUtil.assertUpdateEnrichmentRecordAction(iterator.next(), state.getRawRepo(), e1Moved, state.getLibraryRecordsHandler(), state.getHoldingsItems(), settings.getProperty(state.getRawRepoProviderId()));
        assertThat(iterator.hasNext(), is(false));
    }

    /**
     * Test MoveEnrichmentRecordAction.performAction(): Move enrichment to new common
     * record that exists.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * <ol>
     * <li>A common record <code>c1</code> that is published</li>
     * <li>A common record <code>c2</code> that is not published</li>
     * <li>An enrichment record <code>e1</code> to <code>c1</code></li>
     * </ol>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Perform actions to move <code>e1</code> from <code>c1</code> to <code>c2</code>.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * The enrichment record is moved to <code>c2</code> by creating the following child actions:
     * <ol>
     * <li>UpdateEnrichmentRecordAction: To delete the old enrichment record in rawrepo.</li>
     * <li>UpdateClassificationsInEnrichmentRecordAction: Store the enrichment record and associate it with <code>c2</code></li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_CommonRecordNotPublished() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";
        MarcRecord c1 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId);
        MarcRecord e1 = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE, c1RecordId);
        String e1RecordId = AssertActionsUtil.getRecordId(e1);
        int e1AgencyId = AssertActionsUtil.getAgencyIdAsInt(e1);

        when(state.getRawRepo().recordExists(eq(c1RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(c2RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(e1RecordId), eq(e1AgencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(c1RecordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(c1, MarcXChangeMimeType.MARCXCHANGE));

        MoveEnrichmentRecordAction instance = new MoveEnrichmentRecordAction(state, settings, e1, false, false);
        instance.setTargetRecordId(c2RecordId);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));
        ListIterator<ServiceAction> iterator = instance.children.listIterator();

        MarcRecord e1Deleted = AssertActionsUtil.loadRecordAndMarkForDeletion(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE, c1RecordId);
        AssertActionsUtil.assertUpdateEnrichmentRecordAction(iterator.next(), state.getRawRepo(), e1Deleted, state.getLibraryRecordsHandler(), state.getHoldingsItems(), settings.getProperty(state.getRawRepoProviderId()));

        MarcRecord e1Moved = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE, c2RecordId);
        AssertActionsUtil.assertUpdateEnrichmentRecordAction(iterator.next(), state.getRawRepo(), e1Moved, state.getLibraryRecordsHandler(), state.getHoldingsItems(), settings.getProperty(state.getRawRepoProviderId()));
        assertThat(iterator.hasNext(), is(false));
    }
}
