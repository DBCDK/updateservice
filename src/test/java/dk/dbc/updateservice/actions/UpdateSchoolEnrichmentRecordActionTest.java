/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.updateservice.update.RawRepo;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Properties;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;


public class UpdateSchoolEnrichmentRecordActionTest {
    private GlobalActionState state;
    private Properties settings;

    @Before
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
        settings = new UpdateTestUtils().getSettings();
    }

    @Test
    public void testConstructor_NoCommonSchoolRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.SCHOOL_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);

        when(state.getRawRepo().recordExists(eq(recordId), eq(RawRepo.SCHOOL_COMMON_AGENCY))).thenReturn(false);

        UpdateSchoolEnrichmentRecordAction instance = new UpdateSchoolEnrichmentRecordAction(state, settings, record);
        assertThat(instance.getParentAgencyId(), is(RawRepo.COMMON_AGENCY));
    }

    @Test
    public void testConstructor_WithCommonSchoolRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.SCHOOL_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);

        when(state.getRawRepo().recordExists(eq(recordId), eq(RawRepo.SCHOOL_COMMON_AGENCY))).thenReturn(true);

        UpdateSchoolEnrichmentRecordAction instance = new UpdateSchoolEnrichmentRecordAction(state, settings, record);
        assertThat(instance.getParentAgencyId(), is(RawRepo.SCHOOL_COMMON_AGENCY));
    }
}
