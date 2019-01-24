package dk.dbc.updateservice.update;

import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.common.records.MarcSubField;
import dk.dbc.updateservice.actions.AssertActionsUtil;
import org.junit.Test;

import java.util.Arrays;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class MetakompasHandlerTest {

    @Test
    public void testMetaCompassCopy_New() throws Exception {
        MarcRecord actual = AssertActionsUtil.loadRecord("actions/metacompass-copy-test-1-input.marc");
        MarcRecord expected = AssertActionsUtil.loadRecord("actions/metacompass-copy-test-1-expected.marc");

        MetakompasHandler.copyMetakompasFields(actual);

        new MarcRecordWriter(actual).sort();

        assertThat(actual, is(expected));
    }

    @Test
    public void testMetaCompassCopy_Update() throws Exception {
        MarcRecord actual = AssertActionsUtil.loadRecord("actions/metacompass-copy-test-2-input.marc");
        MarcRecord expected = AssertActionsUtil.loadRecord("actions/metacompass-copy-test-2-expected.marc");

        MetakompasHandler.copyMetakompasFields(actual);

        new MarcRecordWriter(actual).sort();

        assertThat(actual, is(expected));
    }

    @Test
    public void testMetaCompassCopy_ACC() throws Exception {
        MarcRecord actual = AssertActionsUtil.loadRecord("actions/metacompass-copy-test-3-input.marc");
        MarcRecord expected = AssertActionsUtil.loadRecord("actions/metacompass-copy-test-3-expected.marc");

        MetakompasHandler.copyMetakompasFields(actual);

        new MarcRecordWriter(actual).sort();

        assertThat(actual, is(expected));
    }

    @Test
    public void test_isYearInterval() throws Exception {
        assertThat(MetakompasHandler.isYearInterval("999-0"), is(true));
        assertThat(MetakompasHandler.isYearInterval("0-10"), is(true));
        assertThat(MetakompasHandler.isYearInterval("10-90"), is(true));
        assertThat(MetakompasHandler.isYearInterval("500-600"), is(true));
        assertThat(MetakompasHandler.isYearInterval("1990-2000"), is(true));
        assertThat(MetakompasHandler.isYearInterval("9990-10000"), is(true));

        assertThat(MetakompasHandler.isYearInterval("2400 BC - 23500"), is(false));
        assertThat(MetakompasHandler.isYearInterval("år 1900 til år 2000"), is(false));
        assertThat(MetakompasHandler.isYearInterval(""), is(false));
        assertThat(MetakompasHandler.isYearInterval("-"), is(false));
        assertThat(MetakompasHandler.isYearInterval("1942-"), is(false));
        assertThat(MetakompasHandler.isYearInterval("not a year interval"), is(false));
    }

    @Test
    public void test_addMinusProofPrinting() throws Exception {
        MarcField field = new MarcField("001", "00", Arrays.asList(
                new MarcSubField("a", "12345678"),
                new MarcSubField("b", "870970")));
        MarcRecord record = new MarcRecord(Arrays.asList(field));

        MarcRecord actual = new MarcRecord(record);
        MarcRecord expected = new MarcRecord(record);
        new MarcRecordWriter(expected).addOrReplaceSubfield("z98", "a", "Minus korrekturprint");

        MetakompasHandler.addMinusProofPrinting(actual);

        assertThat(actual, is(expected));
    }
}
