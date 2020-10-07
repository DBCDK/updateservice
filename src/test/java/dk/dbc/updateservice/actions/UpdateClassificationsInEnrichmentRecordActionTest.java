/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.common.records.MarcSubField;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Properties;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertNotNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

        when(state.getLibraryRecordsHandler().updateLibraryExtendedRecord(isNull(), eq(commonRecord), eq(enrichmentRecord))).thenReturn(newEnrichmentRecord);

        UpdateClassificationsInEnrichmentRecordAction updateClassificationsInEnrichmentRecordAction = new UpdateClassificationsInEnrichmentRecordAction(state, settings, UpdateTestUtils.GROUP_ID);
        updateClassificationsInEnrichmentRecordAction.setCurrentCommonRecord(null);
        updateClassificationsInEnrichmentRecordAction.setUpdatingCommonRecord(commonRecord);
        updateClassificationsInEnrichmentRecordAction.setEnrichmentRecord(enrichmentRecord);
        assertThat(updateClassificationsInEnrichmentRecordAction.createRecord(), equalTo(newEnrichmentRecord));
    }

    @Test
    public void testModifyEnrichment() throws Exception {
        MarcRecord commonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        MarcRecord newEnrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(newEnrichmentRecord).addOrReplaceSubfield("y08", "a", "Ny Note");

        MarcRecord expected = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        MarcField y08 = new MarcField("y08", "00");
        y08.getSubfields().add(new MarcSubField("a", "Ny Note"));
        y08.getSubfields().add(new MarcSubField("a", "UPDATE opstillingsændring"));
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
        assertNotNull(new MarcRecordReader(expected).getValue("001", "c"));
        assertNotNull(new MarcRecordReader(actual).getValue("001", "c"));

        // Verify that the records are identical without 001 *c
        new MarcRecordWriter(expected).removeSubfield("001", "c");
        new MarcRecordWriter(actual).removeSubfield("001", "c");
        assertThat(actual, equalTo(expected));
    }

}
