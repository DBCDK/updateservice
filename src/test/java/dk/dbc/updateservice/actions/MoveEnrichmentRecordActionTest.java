package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.updateservice.update.LibraryGroup;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ListIterator;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

class MoveEnrichmentRecordActionTest {
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
    void testPerformAction_CommonRecordPublished() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";
        MarcRecord c1 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId);
        MarcRecord e1 = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE, c1RecordId);
        String e1RecordId = AssertActionsUtil.getBibliographicRecordId(e1);
        int e1AgencyId = AssertActionsUtil.getAgencyIdAsInt(e1);
        new MarcRecordWriter(c1).addOrReplaceSubField("z98", 'a', "testPerformAction_CommonRecordPublished");

        when(state.getRawRepo().recordExists(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(state.getRawRepo().recordExists(c2RecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(state.getRawRepo().recordExists(e1RecordId, e1AgencyId)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(c1, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getLibraryRecordsHandler().isRecordInProduction(c1)).thenReturn(true);

        MoveEnrichmentRecordAction moveEnrichmentRecordAction = new MoveEnrichmentRecordAction(state, settings, e1, true, true);
        moveEnrichmentRecordAction.setTargetRecordId(c2RecordId);
        assertThat(moveEnrichmentRecordAction.performAction(), is(ServiceResult.newOkResult()));
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
    void testPerformAction_CommonRecordNotPublished() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";
        MarcRecord c1 = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId);
        MarcRecord e1 = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE, c1RecordId);
        String e1RecordId = AssertActionsUtil.getBibliographicRecordId(e1);
        int e1AgencyId = AssertActionsUtil.getAgencyIdAsInt(e1);

        when(state.getRawRepo().recordExists(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(state.getRawRepo().recordExists(c2RecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(state.getRawRepo().recordExists(e1RecordId, e1AgencyId)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(c1RecordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(c1, MarcXChangeMimeType.MARCXCHANGE));

        MoveEnrichmentRecordAction instance = new MoveEnrichmentRecordAction(state, settings, e1, false, false);
        instance.setTargetRecordId(c2RecordId);
        assertThat(instance.performAction(), is(ServiceResult.newOkResult()));
        ListIterator<ServiceAction> iterator = instance.children.listIterator();

        MarcRecord e1Deleted = AssertActionsUtil.loadRecordAndMarkForDeletion(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE, c1RecordId);
        AssertActionsUtil.assertUpdateEnrichmentRecordAction(iterator.next(), state.getRawRepo(), e1Deleted, state.getLibraryRecordsHandler(), state.getHoldingsItems(), settings.getProperty(state.getRawRepoProviderId()));

        MarcRecord e1Moved = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE, c2RecordId);
        AssertActionsUtil.assertUpdateEnrichmentRecordAction(iterator.next(), state.getRawRepo(), e1Moved, state.getLibraryRecordsHandler(), state.getHoldingsItems(), settings.getProperty(state.getRawRepoProviderId()));
        assertThat(iterator.hasNext(), is(false));
    }
}
