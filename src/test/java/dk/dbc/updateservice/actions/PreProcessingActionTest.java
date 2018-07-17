/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

public class PreProcessingActionTest {
    private GlobalActionState state;

    @Before
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
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
        testExample("preprocessing/first-edition/first-edition-1-input.marc",
                "preprocessing/first-edition/first-edition-1-output.marc");
    }

    @Test
    public void testFirstEdition2() throws Exception {
        testExample("preprocessing/first-edition/first-edition-2-input.marc",
                "preprocessing/first-edition/first-edition-2-output.marc");
    }

    @Test
    public void testNewEdition1() throws Exception {
        testExample("preprocessing/new-edition/new-edition-1-input.marc",
                "preprocessing/new-edition/new-edition-1-output.marc");
    }

    @Test
    public void testNewEdition2() throws Exception {
        testExample("preprocessing/new-edition/new-edition-2-input.marc",
                "preprocessing/new-edition/new-edition-2-output.marc");
    }

    @Test
    public void testNewEdition3() throws Exception {
        testExample("preprocessing/new-edition/new-edition-3-input.marc",
                "preprocessing/new-edition/new-edition-3-output.marc");
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

    private void testExample(String inputFileName, String expectedFileName) throws Exception {
        final MarcRecord actual = AssertActionsUtil.loadRecord(inputFileName);
        final MarcRecord expected = AssertActionsUtil.loadRecord(expectedFileName);

        state.setMarcRecord(actual);

        final PreProcessingAction instance = new PreProcessingAction(state);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));
        assertThat(state.getMarcRecord(), equalTo(expected));
    }
}
