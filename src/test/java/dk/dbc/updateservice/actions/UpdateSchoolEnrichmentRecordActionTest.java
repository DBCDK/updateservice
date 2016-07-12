package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.updateservice.update.RawRepo;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by stp on 14/12/15.
 */
public class UpdateSchoolEnrichmentRecordActionTest {
    @Test
    public void testConstructor_NoCommonSchoolRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.SCHOOL_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);

        RawRepo rawRepo = mock(RawRepo.class);
        when(rawRepo.recordExists(eq(recordId), eq(RawRepo.SCHOOL_COMMON_AGENCY))).thenReturn(false);

        UpdateSchoolEnrichmentRecordAction instance = new UpdateSchoolEnrichmentRecordAction(rawRepo, record);
        assertThat(instance.commonRecordAgencyId(), is(RawRepo.RAWREPO_COMMON_LIBRARY));
    }

    @Test
    public void testConstructor_WithCommonSchoolRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.SCHOOL_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);

        RawRepo rawRepo = mock(RawRepo.class);
        when(rawRepo.recordExists(eq(recordId), eq(RawRepo.SCHOOL_COMMON_AGENCY))).thenReturn(true);

        UpdateSchoolEnrichmentRecordAction instance = new UpdateSchoolEnrichmentRecordAction(rawRepo, record);
        assertThat(instance.commonRecordAgencyId(), is(RawRepo.SCHOOL_COMMON_AGENCY));
    }
}
