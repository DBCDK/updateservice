package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordWriter;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Properties;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.when;

public class UpdateClassificationsInEnrichmentRecordActionTest {
    private GlobalActionState state;
    private Properties settings;

    @Before
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
        settings = new UpdateTestUtils().getSettings();
    }

    /**
     * Test UpdateClassificationsInEnrichmentRecordAction.createRecord(): Update enrichment record
     * with wrong state.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * Missing common record.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update enrichment record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Throw exception: IllegalStateException
     * </dd>
     * </dl>
     */
    @Test(expected = IllegalStateException.class)
    public void testCreateRecord_CommonRecordIsNull() throws Exception {
        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        UpdateClassificationsInEnrichmentRecordAction updateClassificationsInEnrichmentRecordAction = new UpdateClassificationsInEnrichmentRecordAction(state, settings, UpdateTestUtils.GROUP_ID);
        updateClassificationsInEnrichmentRecordAction.setCurrentCommonRecord(null);
        updateClassificationsInEnrichmentRecordAction.setUpdatingCommonRecord(null);
        updateClassificationsInEnrichmentRecordAction.setEnrichmentRecord(enrichmentRecord);
        updateClassificationsInEnrichmentRecordAction.createRecord();
    }

    /**
     * Test UpdateClassificationsInEnrichmentRecordAction.createRecord(): Update enrichment record
     * with wrong state.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * Missing enrichment record.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update enrichment record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Throw exception: IllegalStateException
     * </dd>
     * </dl>
     */
    @Test(expected = IllegalStateException.class)
    public void testCreateRecord_EnrichmentRecordIsNull() throws Exception {
        MarcRecord commonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        UpdateClassificationsInEnrichmentRecordAction updateClassificationsInEnrichmentRecordAction = new UpdateClassificationsInEnrichmentRecordAction(state, settings, UpdateTestUtils.GROUP_ID);
        updateClassificationsInEnrichmentRecordAction.setCurrentCommonRecord(null);
        updateClassificationsInEnrichmentRecordAction.setUpdatingCommonRecord(commonRecord);
        updateClassificationsInEnrichmentRecordAction.setEnrichmentRecord(null);
        updateClassificationsInEnrichmentRecordAction.createRecord();
    }

    /**
     * Test UpdateClassificationsInEnrichmentRecordAction.createRecord(): Update enrichment record
     * with wrong state.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * Missing records handler.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update enrichment record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Throw exception: IllegalStateException
     * </dd>
     * </dl>
     */
    @Test(expected = IllegalStateException.class)
    public void testCreateRecord_RecordsHandlerIsNull() throws Exception {
        state.setLibraryRecordsHandler(null);
        MarcRecord commonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        UpdateClassificationsInEnrichmentRecordAction updateClassificationsInEnrichmentRecordAction = new UpdateClassificationsInEnrichmentRecordAction(state, settings, UpdateTestUtils.GROUP_ID);
        updateClassificationsInEnrichmentRecordAction.setCurrentCommonRecord(null);
        updateClassificationsInEnrichmentRecordAction.setUpdatingCommonRecord(commonRecord);
        updateClassificationsInEnrichmentRecordAction.setEnrichmentRecord(enrichmentRecord);
        updateClassificationsInEnrichmentRecordAction.createRecord();
    }

    /**
     * Test UpdateClassificationsInEnrichmentRecordAction.createRecord(): Update enrichment record
     * succesfully.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * Common record and enrichment record.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update classifications in enrichment record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Returns a new enrichment record with extra 504 field.
     * </dd>
     * </dl>
     */
    @Test
    public void testCreateRecord() throws Exception {
        MarcRecord commonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        MarcRecord newEnrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(newEnrichmentRecord).addOrReplaceSubfield("504", "a", "Ny Note");

        when(state.getLibraryRecordsHandler().updateLibraryExtendedRecord(isNull(MarcRecord.class), eq(commonRecord), eq(enrichmentRecord))).thenReturn(newEnrichmentRecord);

        UpdateClassificationsInEnrichmentRecordAction updateClassificationsInEnrichmentRecordAction = new UpdateClassificationsInEnrichmentRecordAction(state, settings, UpdateTestUtils.GROUP_ID);
        updateClassificationsInEnrichmentRecordAction.setCurrentCommonRecord(null);
        updateClassificationsInEnrichmentRecordAction.setUpdatingCommonRecord(commonRecord);
        updateClassificationsInEnrichmentRecordAction.setEnrichmentRecord(enrichmentRecord);
        assertThat(updateClassificationsInEnrichmentRecordAction.createRecord(), equalTo(newEnrichmentRecord));
    }
}
