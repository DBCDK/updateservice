package dk.dbc.updateservice.actions;

import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Properties;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.when;


class UpdateSchoolEnrichmentRecordActionTest {
    private GlobalActionState state;
    private Properties settings;

    @BeforeEach
    public void before() throws IOException, UpdateException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
        settings = new UpdateTestUtils().getSettings();
    }

    @Test
    void testConstructor_NoCommonSchoolRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.SCHOOL_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getBibliographicRecordId(record);

        when(state.getRawRepo().recordExists(recordId, RawRepo.SCHOOL_COMMON_AGENCY)).thenReturn(false);

        UpdateSchoolEnrichmentRecordAction instance = new UpdateSchoolEnrichmentRecordAction(state, settings, record);
        assertThat(instance.getParentAgencyId(), is(RawRepo.COMMON_AGENCY));
    }

    @Test
    void testConstructor_WithCommonSchoolRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.SCHOOL_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getBibliographicRecordId(record);

        when(state.getRawRepo().recordExists(recordId, RawRepo.SCHOOL_COMMON_AGENCY)).thenReturn(true);

        UpdateSchoolEnrichmentRecordAction instance = new UpdateSchoolEnrichmentRecordAction(state, settings, record);
        assertThat(instance.getParentAgencyId(), is(RawRepo.SCHOOL_COMMON_AGENCY));
    }
}
