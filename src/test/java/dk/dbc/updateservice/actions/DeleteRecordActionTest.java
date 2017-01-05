package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Properties;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

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
}
