package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.marc.binding.DataField;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.marc.binding.SubField;
import dk.dbc.updateservice.update.UpdateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

class UpdateClassificationsInEnrichmentRecordActionTest {
    private GlobalActionState state;
    private Properties settings;

    @BeforeEach
    public void before() throws IOException, UpdateException {
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
    @Test
    void testCreateRecord_CommonRecordIsNull() throws Exception {
        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        UpdateClassificationsInEnrichmentRecordAction updateClassificationsInEnrichmentRecordAction = new UpdateClassificationsInEnrichmentRecordAction(state, settings, UpdateTestUtils.GROUP_ID);
        updateClassificationsInEnrichmentRecordAction.setCurrentCommonRecord(null);
        updateClassificationsInEnrichmentRecordAction.setUpdatingCommonRecord(null);
        updateClassificationsInEnrichmentRecordAction.setEnrichmentRecord(enrichmentRecord);
        assertThrows(IllegalStateException.class, updateClassificationsInEnrichmentRecordAction::createRecord);
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
    @Test
    void testCreateRecord_EnrichmentRecordIsNull() throws Exception {
        MarcRecord commonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        UpdateClassificationsInEnrichmentRecordAction updateClassificationsInEnrichmentRecordAction = new UpdateClassificationsInEnrichmentRecordAction(state, settings, UpdateTestUtils.GROUP_ID);
        updateClassificationsInEnrichmentRecordAction.setCurrentCommonRecord(null);
        updateClassificationsInEnrichmentRecordAction.setUpdatingCommonRecord(commonRecord);
        updateClassificationsInEnrichmentRecordAction.setEnrichmentRecord(null);
        assertThrows(IllegalStateException.class, updateClassificationsInEnrichmentRecordAction::createRecord);
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
    @Test
    void testCreateRecord_RecordsHandlerIsNull() throws Exception {
        state.setLibraryRecordsHandler(null);
        MarcRecord commonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        UpdateClassificationsInEnrichmentRecordAction updateClassificationsInEnrichmentRecordAction = new UpdateClassificationsInEnrichmentRecordAction(state, settings, UpdateTestUtils.GROUP_ID);
        updateClassificationsInEnrichmentRecordAction.setCurrentCommonRecord(null);
        updateClassificationsInEnrichmentRecordAction.setUpdatingCommonRecord(commonRecord);
        updateClassificationsInEnrichmentRecordAction.setEnrichmentRecord(enrichmentRecord);
        assertThrows(IllegalStateException.class, updateClassificationsInEnrichmentRecordAction::createRecord);
    }

    /**
     * Test UpdateClassificationsInEnrichmentRecordAction.createRecord(): Update enrichment record
     * successfully.
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
    void testCreateRecord() throws Exception {
        MarcRecord commonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        MarcRecord newEnrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(newEnrichmentRecord).addOrReplaceSubField("504", 'a', "Ny Note");

        when(state.getLibraryRecordsHandler().updateLibraryExtendedRecord(isNull(), eq(commonRecord), eq(enrichmentRecord))).thenReturn(newEnrichmentRecord);

        UpdateClassificationsInEnrichmentRecordAction updateClassificationsInEnrichmentRecordAction = new UpdateClassificationsInEnrichmentRecordAction(state, settings, UpdateTestUtils.GROUP_ID);
        updateClassificationsInEnrichmentRecordAction.setCurrentCommonRecord(null);
        updateClassificationsInEnrichmentRecordAction.setUpdatingCommonRecord(commonRecord);
        updateClassificationsInEnrichmentRecordAction.setEnrichmentRecord(enrichmentRecord);
        assertThat(updateClassificationsInEnrichmentRecordAction.createRecord(), is(newEnrichmentRecord));
    }

    @Test
    void testModifyEnrichment() throws Exception {
        MarcRecord commonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        MarcRecord newEnrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(newEnrichmentRecord).addOrReplaceSubField("y08", 'a', "Ny Note");

        MarcRecord expected = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        DataField y08 = new DataField("y08", "00");
        y08.getSubFields().add(new SubField('a', "Ny Note"));
        y08.getSubFields().add(new SubField('a', "UPDATE opstillingsændring"));
        expected.getFields().add(y08);

        when(state.getLibraryRecordsHandler().updateLibraryExtendedRecord(isNull(), eq(commonRecord), eq(enrichmentRecord))).thenReturn(newEnrichmentRecord);

        UpdateClassificationsInEnrichmentRecordAction updateClassificationsInEnrichmentRecordAction = new UpdateClassificationsInEnrichmentRecordAction(state, settings, UpdateTestUtils.GROUP_ID);
        updateClassificationsInEnrichmentRecordAction.setCurrentCommonRecord(null);
        updateClassificationsInEnrichmentRecordAction.setUpdatingCommonRecord(commonRecord);
        updateClassificationsInEnrichmentRecordAction.setEnrichmentRecord(enrichmentRecord);

        MarcRecord actual = updateClassificationsInEnrichmentRecordAction.createRecord();

        // Subfield 001 *c should have a new value, so the records should not be identical
        assertThat(actual, not(expected));

        // Check that subfield 001 *c has a value in both records
        assertNotNull(new MarcRecordReader(expected).getValue("001", 'c'));
        assertNotNull(new MarcRecordReader(actual).getValue("001", 'c'));

        // Verify that the records are identical without 001 *c
        new MarcRecordWriter(expected).removeSubfield("001", 'c');
        new MarcRecordWriter(actual).removeSubfield("001", 'c');
        assertThat(actual, is(expected));
    }

}
