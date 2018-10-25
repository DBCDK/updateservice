/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

public class PreProcessingActionTest {
    private GlobalActionState state;

    @Before
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
    }

    @Test
    public void testIgnore1() throws Exception {
        testExample("preprocessing/ignore/ignore-1.marc",
                "preprocessing/ignore/ignore-1.marc");
    }

    @Test
    public void testIgnore2() throws Exception {
        testExample("preprocessing/ignore/ignore-2.marc",
                "preprocessing/ignore/ignore-2.marc");
    }

    @Test
    public void testIgnore3() throws Exception {
        testExample("preprocessing/ignore/ignore-3.marc",
                "preprocessing/ignore/ignore-3.marc");
    }

    @Test
    public void testIgnore4() throws Exception {
        testExample("preprocessing/ignore/ignore-4.marc",
                "preprocessing/ignore/ignore-4.marc");
    }

    @Test
    public void testEbook1() throws Exception {
        testExample("preprocessing/ebook/ebook-1-input.marc",
                "preprocessing/ebook/ebook-1-output.marc");
    }

    @Test
    public void testEbook2() throws Exception {
        testExample("preprocessing/ebook/ebook-2-input.marc",
                "preprocessing/ebook/ebook-2-output.marc");
    }

    @Test
    public void testEbook3() throws Exception {
        testExample("preprocessing/ebook/ebook-3-input.marc",
                "preprocessing/ebook/ebook-3-output.marc");
    }

    @Test
    public void testEbook4() throws Exception {
        testExample("preprocessing/ebook/ebook-4-input.marc",
                "preprocessing/ebook/ebook-4-output.marc");
    }

    @Test
    public void testEbook5() throws Exception {
        testExample("preprocessing/ebook/ebook-5-input.marc",
                "preprocessing/ebook/ebook-5-output.marc");
    }

    @Test
    public void testEbook6() throws Exception {
        testExample("preprocessing/ebook/ebook-6-input.marc",
                "preprocessing/ebook/ebook-6-output.marc");
    }

    @Test
    public void testEbook7() throws Exception {
        testExample("preprocessing/ebook/ebook-7-input.marc",
                "preprocessing/ebook/ebook-7-output.marc");
    }

    @Test
    public void testEbook8() throws Exception {
        testExample("preprocessing/ebook/ebook-8-input.marc",
                "preprocessing/ebook/ebook-8-output.marc");
    }

    @Test
    public void testEbook9() throws Exception {
        testExample("preprocessing/ebook/ebook-9-input.marc",
                "preprocessing/ebook/ebook-9-output.marc");
    }

    @Test
    public void testEbook10() throws Exception {
        testExample("preprocessing/ebook/ebook-10-input.marc",
                "preprocessing/ebook/ebook-10-output.marc");
    }

    @Test
    public void testEbook11() throws Exception {
        testExample("preprocessing/ebook/ebook-11-input.marc",
                "preprocessing/ebook/ebook-11-output.marc");
    }

    @Test
    public void testEbook12() throws Exception {
        testExample("preprocessing/ebook/ebook-12-input.marc",
                "preprocessing/ebook/ebook-12-output.marc");
    }

    @Test
    public void testEbook13() throws Exception {
        testExample("preprocessing/ebook/ebook-13-input.marc",
                "preprocessing/ebook/ebook-13-output.marc");
    }

    @Test
    public void testEbook14() throws Exception {
        testExample("preprocessing/ebook/ebook-14-input.marc",
                "preprocessing/ebook/ebook-14-output.marc");
    }

    @Test
    public void testFirstEdition1() throws Exception {
        testExampleNotExistingRecord("preprocessing/first-edition/first-edition-1-input.marc",
                "preprocessing/first-edition/first-edition-1-output.marc");
    }

    @Test
    public void testFirstEdition2() throws Exception {
        testExampleNotExistingRecord("preprocessing/first-edition/first-edition-2-input.marc",
                "preprocessing/first-edition/first-edition-2-output.marc");
    }

    @Test
    public void testNewEdition1() throws Exception {
        testExample("preprocessing/new-edition/new-edition-1-input.marc",
                "preprocessing/new-edition/new-edition-1-output.marc",
                "preprocessing/new-edition/new-edition-1-existing.marc");
    }

    @Test
    public void testNewEdition2() throws Exception {
        testExample("preprocessing/new-edition/new-edition-2-input.marc",
                "preprocessing/new-edition/new-edition-2-output.marc",
                "preprocessing/new-edition/new-edition-2-existing.marc");
    }

    @Test
    public void testNewEdition3() throws Exception {
        testExample("preprocessing/new-edition/new-edition-3-input.marc",
                "preprocessing/new-edition/new-edition-3-output.marc",
                "preprocessing/new-edition/new-edition-3-existing.marc");
    }

    @Test
    public void testNewEdition4() throws Exception {
        testExample("preprocessing/new-edition/new-edition-4-input.marc",
                "preprocessing/new-edition/new-edition-4-output.marc",
                "preprocessing/new-edition/new-edition-4-existing.marc");
    }

    @Test
    public void testNewEdition5() throws Exception {
        testExample("preprocessing/new-edition/new-edition-5-input.marc",
                "preprocessing/new-edition/new-edition-5-output.marc",
                "preprocessing/new-edition/new-edition-5-existing.marc");
    }

    @Test
    public void testNewEdition5Null() throws Exception {
        testExampleNotExistingRecord("preprocessing/new-edition/new-edition-5-input.marc",
                "preprocessing/new-edition/new-edition-5-input.marc");
    }

    @Test
    public void testAgeInterval1() throws Exception {
        testExample("preprocessing/age-interval/age-interval-1-input.marc",
                "preprocessing/age-interval/age-interval-1-output.marc");
    }

    @Test
    public void testAgeInterval2() throws Exception {
        testExample("preprocessing/age-interval/age-interval-2-input.marc",
                "preprocessing/age-interval/age-interval-2-output.marc");
    }

    @Test
    public void testAgeInterval3() throws Exception {
        testExample("preprocessing/age-interval/age-interval-3-input.marc",
                "preprocessing/age-interval/age-interval-3-output.marc");
    }

    @Test
    public void testAgeInterval4() throws Exception {
        testExample("preprocessing/age-interval/age-interval-4-input.marc",
                "preprocessing/age-interval/age-interval-4-output.marc");
    }

    @Test
    public void testAgeInterval5() throws Exception {
        testExample("preprocessing/age-interval/age-interval-5-input.marc",
                "preprocessing/age-interval/age-interval-5-output.marc");
    }

    @Test
    public void testAgeInterval6() throws Exception {
        testExample("preprocessing/age-interval/age-interval-6-input.marc",
                "preprocessing/age-interval/age-interval-6-output.marc");
    }

    @Test
    public void testAgeInterval7() throws Exception {
        testExample("preprocessing/age-interval/age-interval-7-input.marc",
                "preprocessing/age-interval/age-interval-7-output.marc");
    }

    @Test
    public void testAgeInterval8() throws Exception {
        testExample("preprocessing/age-interval/age-interval-8-input.marc",
                "preprocessing/age-interval/age-interval-8-output.marc");
    }

    @Test
    public void testPreviousISBN1() throws Exception {
        final MarcRecord request = AssertActionsUtil.loadRecord("preprocessing/isbn-previous-version/test-1-request.marc");
        final MarcRecord expected = AssertActionsUtil.loadRecord("preprocessing/isbn-previous-version/test-1-expected.marc");
        final MarcRecord previous = AssertActionsUtil.loadRecord("preprocessing/isbn-previous-version/test-1-rawrepo-29469237-870970.marc");

        state.setMarcRecord(request);
        when(state.getRawRepo().recordExists(eq("29469237"), eq(870970))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq("29469237"), eq(870970))).thenReturn(AssertActionsUtil.createRawRepoRecord(previous, MarcXChangeMimeType.MARCXCHANGE));

        final PreProcessingAction instance = new PreProcessingAction(state);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));
        new MarcRecordWriter(state.getMarcRecord()).sort();
        assertThat(state.getMarcRecord(), equalTo(expected));
    }

    @Test
    public void testPreviousISBN2() throws Exception {
        final MarcRecord request = AssertActionsUtil.loadRecord("preprocessing/isbn-previous-version/test-2-request.marc");
        final MarcRecord expected = AssertActionsUtil.loadRecord("preprocessing/isbn-previous-version/test-2-expected.marc");
        final MarcRecord previous1 = AssertActionsUtil.loadRecord("preprocessing/isbn-previous-version/test-2-rawrepo-52079020-870970.marc");
        final MarcRecord previous2 = AssertActionsUtil.loadRecord("preprocessing/isbn-previous-version/test-2-rawrepo-52106249-870970.marc");

        state.setMarcRecord(request);
        when(state.getRawRepo().recordExists(eq("52079020"), eq(870970))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq("52079020"), eq(870970))).thenReturn(AssertActionsUtil.createRawRepoRecord(previous1, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().recordExists(eq("52106249"), eq(870970))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq("52106249"), eq(870970))).thenReturn(AssertActionsUtil.createRawRepoRecord(previous2, MarcXChangeMimeType.MARCXCHANGE));

        final PreProcessingAction instance = new PreProcessingAction(state);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));
        new MarcRecordWriter(state.getMarcRecord()).sort();
        assertThat(state.getMarcRecord(), equalTo(expected));
    }

    private void testExample(String inputFileName, String expectedFileName) throws Exception {
        final MarcRecord actual = AssertActionsUtil.loadRecord(inputFileName);
        final MarcRecord expected = AssertActionsUtil.loadRecord(expectedFileName);

        state.setMarcRecord(actual);

        final PreProcessingAction instance = new PreProcessingAction(state);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));
        assertThat(state.getMarcRecord(), equalTo(expected));
    }

    private void testExample(String inputFileName, String expectedFileName, String existingFileName) throws Exception {
        final MarcRecord actual = AssertActionsUtil.loadRecord(inputFileName);
        final MarcRecord expected = AssertActionsUtil.loadRecord(expectedFileName);
        final MarcRecord existing = existingFileName != null ? AssertActionsUtil.loadRecord(existingFileName) : null;

        state.setMarcRecord(actual);
        when(state.getRawRepo().recordExistsMaybeDeleted(anyString(), anyInt())).thenReturn(existingFileName != null);
        when(state.getRawRepo().fetchRecord(anyString(), anyInt())).thenReturn(AssertActionsUtil.createRawRepoRecord(existing, MarcXChangeMimeType.MARCXCHANGE));

        final PreProcessingAction instance = new PreProcessingAction(state);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));
        assertThat(state.getMarcRecord(), equalTo(expected));
    }

    private void testExampleNotExistingRecord(String inputFileName, String expectedFileName) throws Exception {
        final MarcRecord actual = AssertActionsUtil.loadRecord(inputFileName);
        final MarcRecord expected = AssertActionsUtil.loadRecord(expectedFileName);

        state.setMarcRecord(actual);
        when(state.getRawRepo().recordExists(anyString(), anyInt())).thenReturn(false);

        final PreProcessingAction instance = new PreProcessingAction(state);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));
        assertThat(state.getMarcRecord(), equalTo(expected));
    }
}
