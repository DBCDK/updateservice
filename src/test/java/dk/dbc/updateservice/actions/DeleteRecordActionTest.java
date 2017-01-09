package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcField;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordWriter;
import dk.dbc.iscrum.records.MarcSubField;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

public class DeleteRecordActionTest {
    private GlobalActionState state;
    private Properties properties;

    @Before
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
        properties = new UpdateTestUtils().getSettings();
    }

    /**
     * Test DeleteRecordAction.deletionMarkToStore() for store in RawRepo for a record.
     * <p>
     * The result should always be <code>true</code>.
     * </p>
     */
    @Test
    public void testDeletionMarkToStore() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        DeleteRecordAction deleteRecordAction = new DeleteRecordAction(state, properties, record);
        deleteRecordAction.setMimetype(MarcXChangeMimeType.MARCXCHANGE);
        assertThat(deleteRecordAction.deletionMarkToStore(), equalTo(true));
    }

    /**
     * Test DeleteRecordAction.recordToStore() to store a deleted record.
     */
    @Test
    public void testRecordToStore() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        DeleteRecordAction instance = new DeleteRecordAction(state, properties, record);
        instance.setMimetype(MarcXChangeMimeType.MARCXCHANGE);
        MarcRecord expected = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        assertThat(instance.recordToStore(), equalTo(expected));
    }

    @Test
    public void testRecordToStoreTwoFields() throws Exception {
        String recordId = "20611529";
        Integer agencyId = 700400;
        List<MarcSubField> field001 = new ArrayList<>();
        field001.add(new MarcSubField("a", recordId));
        field001.add(new MarcSubField("b", Integer.toString(agencyId)));

        List<MarcSubField> field004 = new ArrayList<>();
        field001.add(new MarcSubField("r", "d"));

        MarcRecord record = new MarcRecord();
        record.getFields().add(new MarcField("001", "00", field001));
        record.getFields().add(new MarcField("004", "00", field004));

        MarcRecord rr = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        when(state.getRawRepo().fetchRecord(eq(recordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(rr, MarcXChangeMimeType.MARCXCHANGE));

        DeleteRecordAction instance = new DeleteRecordAction(state, properties, record);
        instance.setMimetype(MarcXChangeMimeType.MARCXCHANGE);

        MarcRecord expected = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(expected).markForDeletion();

        assertThat(instance.recordToStore(), equalTo(expected));
    }
}
