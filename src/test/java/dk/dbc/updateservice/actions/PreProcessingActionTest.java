package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

public class PreProcessingActionTest {
    private GlobalActionState state;

    private static final String INPUT_52793351 = "preprocessing/52793351-input.marc";
    private static final String INPUT_53575153 = "preprocessing/53575153-input.marc";
    private static final String INPUT_53911005 = "preprocessing/53911005-input.marc";
    private static final String INPUT_54016247 = "preprocessing/54016247-input.marc";
    private static final String INPUT_54018207 = "preprocessing/54018207-input.marc";

    private static final String EXPECTED_52793351 = "preprocessing/52793351-expected.marc";
    private static final String EXPECTED_53575153 = "preprocessing/53575153-expected.marc";
    private static final String EXPECTED_53911005 = "preprocessing/53911005-expected.marc";
    private static final String EXPECTED_54016247 = "preprocessing/54016247-expected.marc";
    private static final String EXPECTED_54018207 = "preprocessing/54018207-expected.marc";

    @Before
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
    }

    @Test
    public void testNoMatch() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        final MarcRecord expected = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);

        state.setMarcRecord(record);

        final PreProcessingAction instance = new PreProcessingAction(state);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));
        assertThat(state.getMarcRecord(), equalTo(expected));
    }

    @Test
    public void testMatchInterval_52793351() throws Exception {
        testExample(INPUT_52793351, EXPECTED_52793351);
    }

    @Test
    public void testMatchInterval_53575153() throws Exception {
        testExample(INPUT_53575153, EXPECTED_53575153);
    }

    @Test
    public void testMatchInterval_53911005() throws Exception {
        testExample(INPUT_53911005, EXPECTED_53911005);
    }

    @Test
    public void testMatchInterval_54016247() throws Exception {
        testExample(INPUT_54016247, EXPECTED_54016247);
    }

    @Test
    public void testMatchInterval_54018207() throws Exception {
        testExample(INPUT_54018207, EXPECTED_54018207);
    }

    private void testExample(String inputFileName, String expectedFileName) throws Exception {
        final MarcRecord actual = AssertActionsUtil.loadRecord(inputFileName);
        final MarcRecord expected = AssertActionsUtil.loadRecord(expectedFileName);

        state.setMarcRecord(actual);

        final PreProcessingAction instance = new PreProcessingAction(state);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));
        assertThat(state.getMarcRecord(), equalTo(expected));
    }
}
