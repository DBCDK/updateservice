package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.marc.binding.DataField;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.marc.binding.SubField;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.updateservice.update.UpdateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class DeleteRecordActionTest {
    private GlobalActionState state;
    private Properties properties;
    private final String localSingleRecordId = "20611529";
    private final int localSingleAgencyId = 700400;

    @BeforeEach
    public void before() throws IOException, UpdateException {
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
    void testDeletionMarkToStore() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        DeleteRecordAction deleteRecordAction = new DeleteRecordAction(state, properties, record);
        deleteRecordAction.setMimetype(MarcXChangeMimeType.MARCXCHANGE);
        assertThat(deleteRecordAction.deletionMarkToStore(), is(true));
    }

    /**
     * Test DeleteRecordAction.recordToStore() to store a deleted record.
     */
    @Test
    void testRecordToStore() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        DeleteRecordAction instance = new DeleteRecordAction(state, properties, record);
        instance.setMimetype(MarcXChangeMimeType.MARCXCHANGE);

        MarcRecord rr = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        when(state.getRawRepo().fetchRecord(eq(localSingleRecordId), eq(localSingleAgencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(rr, MarcXChangeMimeType.MARCXCHANGE));

        MarcRecord expected = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(expected).markForDeletion();
        new MarcRecordWriter(expected).setChangedTimestamp();

        AssertActionsUtil.assertRecord(instance.recordToStore(), expected);
    }

    @Test
    void testRecordToStoreTwoFields() throws Exception {

        List<SubField> field001 = new ArrayList<>();
        field001.add(new SubField('a', localSingleRecordId));
        field001.add(new SubField('b', Integer.toString(localSingleAgencyId)));

        List<SubField> field004 = new ArrayList<>();
        field001.add(new SubField('r', "d"));

        MarcRecord record = new MarcRecord();
        record.getFields().add(new DataField("001", "00").addAllSubFields(field001));
        record.getFields().add(new DataField("004", "00").addAllSubFields(field004));

        MarcRecord rr = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        when(state.getRawRepo().fetchRecord(eq(localSingleRecordId), eq(localSingleAgencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(rr, MarcXChangeMimeType.MARCXCHANGE));

        DeleteRecordAction instance = new DeleteRecordAction(state, properties, record);
        instance.setMimetype(MarcXChangeMimeType.MARCXCHANGE);

        MarcRecord expected = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(expected).markForDeletion();
        new MarcRecordWriter(expected).setChangedTimestamp();

        AssertActionsUtil.assertRecord(instance.recordToStore(), expected);
    }
}
