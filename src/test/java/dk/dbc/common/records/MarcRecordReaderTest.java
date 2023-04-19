package dk.dbc.common.records;

import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcFieldReader;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.common.records.MarcSubField;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNull.nullValue;

public class MarcRecordReaderTest {
    @Test
    public void testGetValue() {
        MarcRecord record = new MarcRecord();

        MarcField f1 = new MarcField("245", "00");
        f1.getSubfields().add(new MarcSubField("a", "v1"));
        f1.getSubfields().add(new MarcSubField("x", "x1_1"));
        f1.getSubfields().add(new MarcSubField("x", "x1_2"));
        record.getFields().add(f1);

        MarcField f2 = new MarcField("245", "00");
        f2.getSubfields().add(new MarcSubField("a", "v2"));
        f2.getSubfields().add(new MarcSubField("x", "x2_1"));
        f2.getSubfields().add(new MarcSubField("x", "x2_2"));
        record.getFields().add(f2);

        MarcRecordReader instance = new MarcRecordReader(record);
        MarcFieldReader firstFieldReader = new MarcFieldReader(f1);

        assertThat(instance.getValue("001", "z"), nullValue());
        assertThat(instance.getValue("245", "z"), equalTo(firstFieldReader.getValue("z")));
        assertThat(instance.getValue("245", "x"), equalTo(firstFieldReader.getValue("x")));
    }

    @Test
    public void testGetValues() {
        MarcRecord record = new MarcRecord();

        MarcField f1 = new MarcField("245", "00");
        f1.getSubfields().add(new MarcSubField("a", "v1"));
        f1.getSubfields().add(new MarcSubField("x", "x1_1"));
        f1.getSubfields().add(new MarcSubField("x", "x1_2"));
        record.getFields().add(f1);

        MarcField f2 = new MarcField("245", "00");
        f2.getSubfields().add(new MarcSubField("a", "v2"));
        f2.getSubfields().add(new MarcSubField("x", "x2_1"));
        f2.getSubfields().add(new MarcSubField("x", "x2_2"));
        record.getFields().add(f2);

        List<String> expected;
        MarcRecordReader instance = new MarcRecordReader(record);

        expected = new ArrayList<>();
        assertThat(instance.getValues("001", "z"), equalTo(expected));
        assertThat(instance.getValues("245", "z"), equalTo(expected));

        expected = Arrays.asList("x1_1", "x1_2", "x2_1", "x2_2");
        assertThat(instance.getValues("245", "x"), equalTo(expected));
    }

    @Test
    public void testHasField() {
        MarcRecord record = new MarcRecord();

        MarcField f1 = new MarcField("245", "00");
        f1.getSubfields().add(new MarcSubField("a", "v1"));
        record.getFields().add(f1);

        MarcRecordReader instance = new MarcRecordReader(record);
        assertThat(instance.hasField("245"), is(true));
        assertThat(instance.hasField("110"), is(false));
    }

    @Test
    public void testHasValue() {
        MarcRecord record = new MarcRecord();

        MarcField f1 = new MarcField("245", "00");
        f1.getSubfields().add(new MarcSubField("a", "v1"));
        f1.getSubfields().add(new MarcSubField("x", "x1_1"));
        f1.getSubfields().add(new MarcSubField("x", "x1_2"));
        record.getFields().add(f1);

        MarcField f2 = new MarcField("245", "00");
        f2.getSubfields().add(new MarcSubField("a", "v2"));
        f2.getSubfields().add(new MarcSubField("x", "x2_1"));
        f2.getSubfields().add(new MarcSubField("x", "x2_2"));
        record.getFields().add(f2);

        MarcRecordReader instance = new MarcRecordReader(record);
        MarcFieldReader firstFieldReader = new MarcFieldReader(f1);

        assertThat(instance.hasValue("001", "z", "xxx"), is(false));
        assertThat(instance.hasValue("245", "z", "xxx"), equalTo(firstFieldReader.hasValue("z", "xxx")));
        assertThat(instance.hasValue("245", "x", "x1_1"), equalTo(firstFieldReader.hasValue("x", "x1_1")));
        assertThat(instance.hasValue("245", "x", "x1_2"), equalTo(firstFieldReader.hasValue("x", "x1_2")));
    }

    @Test
    public void testRecordId() {
        MarcRecord record = new MarcRecord();

        MarcRecordWriter writer = new MarcRecordWriter(record);
        MarcRecordReader instance = new MarcRecordReader(record);

        writer.addOrReplaceSubfield("001", "b", "xxx");
        assertThat(instance.getRecordId(), nullValue());
        writer.addOrReplaceSubfield("001", "a", "xxx");
        assertThat(instance.getRecordId(), is("xxx"));
    }

    @Test
    public void testCentralAliasId() {
        MarcRecord record = new MarcRecord();

        MarcRecordWriter writer = new MarcRecordWriter(record);
        MarcRecordReader instance = new MarcRecordReader(record);

        writer.addFieldSubfield("002", "b", "yyy");
        assertThat(instance.getCentralAliasIds().isEmpty(), is(true));
        writer.addFieldSubfield("002", "a", "xxx");
        writer.addFieldSubfield("002", "a", "zzz");
        assertThat(instance.getCentralAliasIds(), equalTo(Arrays.asList("xxx", "zzz")));
    }

    @Test
    public void testDecentralAliasIdEmpty() {
        MarcRecord record = new MarcRecord();

        MarcRecordWriter writer = new MarcRecordWriter(record);
        MarcRecordReader instance = new MarcRecordReader(record);

        writer.addFieldSubfield("002", "b", "yyy");
        assertThat(instance.getDecentralAliasIds().isEmpty(), is(true));

        writer.addFieldSubfield("002", "c", "xxx");
        assertThat(instance.getDecentralAliasIds().isEmpty(), is(true));
    }

    @Test
    public void testDecentralAliasIdFound() {
        MarcRecord record = new MarcRecord();

        MarcRecordReader instance = new MarcRecordReader(record);

        MarcField field = new MarcField("002", "00");
        field.getSubfields().add(new MarcSubField("b", "yyy"));
        field.getSubfields().add(new MarcSubField("c", "xxx"));
        record.getFields().add(field);

        HashMap<String, String> expectedHash = new HashMap<>();
        expectedHash.put("b", "yyy");
        expectedHash.put("c", "xxx");
        List<HashMap<String, String>> expectedList = new ArrayList<>();
        expectedList.add(expectedHash);

        assertThat(instance.getDecentralAliasIds(), equalTo(expectedList));
    }

    @Test
    public void testIsDBCRecord() {
        MarcRecord record = new MarcRecord();

        MarcRecordWriter writer = new MarcRecordWriter(record);
        MarcRecordReader instance = new MarcRecordReader(record);

        // NOTE: s10*b must be at start since s10*a is added or replaced
        writer.addOrReplaceSubfield("996", "b", "xxx");
        assertThat(instance.isDBCRecord(), is(false));
        writer.addOrReplaceSubfield("996", "a", "810010");
        assertThat(instance.isDBCRecord(), is(false));
        writer.addOrReplaceSubfield("996", "a", "DBC");
        assertThat(instance.isDBCRecord(), is(true));
        writer.addOrReplaceSubfield("996", "a", "RET");
        assertThat(instance.isDBCRecord(), is(true));
    }

    @Test
    public void testAgencyId() {
        MarcRecord record = new MarcRecord();

        MarcRecordWriter writer = new MarcRecordWriter(record);
        MarcRecordReader instance = new MarcRecordReader(record);

        writer.addOrReplaceSubfield("001", "a", "xxx");
        assertThat(instance.getAgencyId(), nullValue());
        writer.addOrReplaceSubfield("001", "b", "xxx");
        assertThat(instance.getAgencyId(), is("xxx"));
        writer.addOrReplaceSubfield("001", "b", "127");
        assertThat(instance.getAgencyIdAsInt(), is(127));
    }

    @Test
    public void testMarkedForDeletion() {
        MarcRecord record = new MarcRecord();

        MarcRecordWriter writer = new MarcRecordWriter(record);
        MarcRecordReader instance = new MarcRecordReader(record);

        assertThat(instance.markedForDeletion(), is(false));

        writer.addOrReplaceSubfield("004", "r", "d");
        assertThat(instance.markedForDeletion(), is(true));

        writer.addOrReplaceSubfield("004", "r", "q");
        assertThat(instance.markedForDeletion(), is(false));
    }

    @Test
    public void testParentId() {
        MarcRecord record = new MarcRecord();

        MarcRecordWriter writer = new MarcRecordWriter(record);
        MarcRecordReader instance = new MarcRecordReader(record);

        writer.addOrReplaceSubfield("014", "a", "xxx");
        assertThat(instance.getParentRecordId(), equalTo(instance.getValue("014", "a")));
    }

    @Test
    public void testGetField() {
        MarcField commentField1 = new MarcField("666", "00", Arrays.asList(new MarcSubField("0",""), new MarcSubField("s", "Julemand")));
        MarcField commentField2 = new MarcField("666", "00", Arrays.asList(new MarcSubField("0",""), new MarcSubField("s", "Grønland")));

        MarcRecord record = new MarcRecord();
        record.getFields().add(commentField1);
        record.getFields().add(commentField2);

        MarcRecordReader instance = new MarcRecordReader(record);

        assertThat(instance.getField("666"), equalTo(commentField1));
    }

    @Test
    public void testGetFieldAll() {
        MarcField commentField1 = new MarcField("666", "00", Arrays.asList(new MarcSubField("0",""), new MarcSubField("s", "Julemand")));
        MarcField commentField2 = new MarcField("666", "00", Arrays.asList(new MarcSubField("0",""), new MarcSubField("s", "Grønland")));

        MarcRecord record = new MarcRecord();
        record.getFields().add(commentField1);
        record.getFields().add(commentField2);

        MarcRecordReader instance = new MarcRecordReader(record);

        assertThat(instance.getFieldAll("666"), equalTo(Arrays.asList(commentField1, commentField2)));
    }

    @Test
    public void testMatchValueTrueSingle() {
        MarcRecord record = new MarcRecord();
        record.getFields().add(new MarcField("001", "00", Arrays.asList(new MarcSubField("a", "12345678"), new MarcSubField("b", "870970"))));
        record.getFields().add(new MarcField("666", "00", Arrays.asList(new MarcSubField("0",""), new MarcSubField("a", "et emneord"))));
        record.getFields().add(new MarcField("666", "00", Arrays.asList(new MarcSubField("0",""), new MarcSubField("c", "Julemand"))));
        record.getFields().add(new MarcField("666", "00", Arrays.asList(new MarcSubField("0",""), new MarcSubField("d", ""))));

        MarcRecordReader instance = new MarcRecordReader(record);

        assertThat(instance.matchValue("666", "c", "(Julemand)"), equalTo(true));
    }

    @Test
    public void testMatchValueFalseHasSubfield() {
        MarcRecord record = new MarcRecord();
        record.getFields().add(new MarcField("001", "00", Arrays.asList(new MarcSubField("a", "12345678"), new MarcSubField("b", "870970"))));
        record.getFields().add(new MarcField("666", "00", Arrays.asList(new MarcSubField("0",""), new MarcSubField("c", "et emneord"))));

        MarcRecordReader instance = new MarcRecordReader(record);

        assertThat(instance.matchValue("666", "c", "(Julemand)"), equalTo(false));
    }

    @Test
    public void testMatchValueFalseDoesntHaveSubfield() {
        MarcRecord record = new MarcRecord();
        record.getFields().add(new MarcField("001", "00", Arrays.asList(new MarcSubField("a", "12345678"), new MarcSubField("b", "870970"))));

        MarcRecordReader instance = new MarcRecordReader(record);

        assertThat(instance.matchValue("666", "c", "(Julemand)"), equalTo(false));
    }

    @Test
    public void testMatchValueTrueMultiField() {
        MarcRecord record = new MarcRecord();
        record.getFields().add(new MarcField("001", "00", Arrays.asList(new MarcSubField("a", "12345678"), new MarcSubField("b", "870970"))));
        record.getFields().add(new MarcField("666", "00", Arrays.asList(new MarcSubField("0",""), new MarcSubField("c", "Julemand"))));
        record.getFields().add(new MarcField("666", "00", Arrays.asList(new MarcSubField("0",""), new MarcSubField("c", "Julemand"))));
        record.getFields().add(new MarcField("666", "00", Arrays.asList(new MarcSubField("0",""), new MarcSubField("d", ""))));

        MarcRecordReader instance = new MarcRecordReader(record);

        assertThat(instance.matchValue("666", "c", "(Julemand)"), equalTo(true));
    }

    @Test
    public void testMatchValueTrueMultiSubfield() {
        MarcRecord record = new MarcRecord();
        record.getFields().add(new MarcField("001", "00", Arrays.asList(new MarcSubField("a", "12345678"), new MarcSubField("b", "870970"))));
        record.getFields().add(new MarcField("666", "00", Arrays.asList(new MarcSubField("c", "Julemand"), new MarcSubField("c", "Julemand"))));
        record.getFields().add(new MarcField("666", "00", Arrays.asList(new MarcSubField("0",""), new MarcSubField("d", ""))));

        MarcRecordReader instance = new MarcRecordReader(record);

        assertThat(instance.matchValue("666", "c", "(Julemand)"), equalTo(true));
    }


    @Test
    public void testGetParentAgencyId_1() {
        MarcRecord record = new MarcRecord();

        MarcRecordReader instance = new MarcRecordReader(record);

        record.getFields().add(new MarcField("001", "00", Arrays.asList(new MarcSubField("a", "12345678"), new MarcSubField("b", "870971"))));
        record.getFields().add(new MarcField("014", "00", Arrays.asList(new MarcSubField("a", "87654321"), new MarcSubField("x", "Julemand"))));
        assertThat(instance.getParentAgencyId(), is("870971"));
    }

    @Test
    public void testGetParentAgencyId_2() {
        MarcRecord record = new MarcRecord();

        MarcRecordReader instance = new MarcRecordReader(record);

        record.getFields().add(new MarcField("001", "00", Arrays.asList(new MarcSubField("a", "12345678"), new MarcSubField("b", "870971"))));
        record.getFields().add(new MarcField("014", "00", Arrays.asList(new MarcSubField("A", "87654321"), new MarcSubField("a", "87654321"))));
        assertThat(instance.getParentAgencyId(), is("870971"));
        assertThat(instance.getParentRecordId(), is("87654321"));
    }

    @Test
    public void testGetParentAgencyId_3() {
        MarcRecord record = new MarcRecord();

        MarcRecordReader instance = new MarcRecordReader(record);

        record.getFields().add(new MarcField("001", "00", Arrays.asList(new MarcSubField("a", "12345678"), new MarcSubField("b", "870971"))));
        record.getFields().add(new MarcField("014", "00", Arrays.asList(new MarcSubField("a", "87654321"), new MarcSubField("x", "ANM"))));
        assertThat(instance.getParentAgencyId(), is("870970"));
        assertThat(instance.getParentRecordId(), is("87654321"));
    }

    @Test
    public void testGetParentAgencyId_DEB() {
        MarcRecord record = new MarcRecord();

        MarcRecordReader instance = new MarcRecordReader(record);

        record.getFields().add(new MarcField("001", "00", Arrays.asList(new MarcSubField("a", "12345678"), new MarcSubField("b", "870971"))));
        record.getFields().add(new MarcField("014", "00", Arrays.asList(new MarcSubField("a", "87654321"), new MarcSubField("x", "DEB"))));
        assertThat(instance.getParentAgencyId(), is("870971"));
        assertThat(instance.getParentRecordId(), is("87654321"));
    }

    @Test
    public void testGetParentAgencyId_016_WithAgency() {
        MarcRecord record = new MarcRecord();

        MarcRecordReader instance = new MarcRecordReader(record);

        record.getFields().add(new MarcField("001", "00", Arrays.asList(new MarcSubField("a", "12345678"), new MarcSubField("b", "870974"))));
        record.getFields().add(new MarcField("016", "00", Arrays.asList(new MarcSubField("a", "87654321"), new MarcSubField("5", "123456"))));
        assertThat(instance.getParentAgencyId(), is("123456"));
        assertThat(instance.getParentRecordId(), is("87654321"));
    }

    @Test
    public void testGetParentAgencyId_018_WithAgency() {
        MarcRecord record = new MarcRecord();

        MarcRecordReader instance = new MarcRecordReader(record);

        record.getFields().add(new MarcField("001", "00", Arrays.asList(new MarcSubField("a", "12345678"), new MarcSubField("b", "870974"))));
        record.getFields().add(new MarcField("018", "00", Arrays.asList(new MarcSubField("a", "87654321"), new MarcSubField("5", "123456"))));
        assertThat(instance.getParentAgencyId(), is("123456"));
        assertThat(instance.getParentRecordId(), is("87654321"));
    }

    @Test
    public void testGetParentAgencyId_VP_016_WithAgency() {
        MarcRecord record = new MarcRecord();

        MarcRecordReader instance = new MarcRecordReader(record);

        record.getFields().add(new MarcField("001", "00", Arrays.asList(new MarcSubField("a", "12345678"), new MarcSubField("b", "870975"))));
        record.getFields().add(new MarcField("016", "00", Arrays.asList(new MarcSubField("a", "87654321"), new MarcSubField("5", "123456"))));
        assertThat(instance.getParentAgencyId(), is("123456"));
        assertThat(instance.getParentRecordId(), is("87654321"));
    }

    @Test
    public void testGetParentAgencyId_VP_016_WithoutAgency() {
        MarcRecord record = new MarcRecord();

        MarcRecordReader instance = new MarcRecordReader(record);

        record.getFields().add(new MarcField("001", "00", Arrays.asList(new MarcSubField("a", "12345678"), new MarcSubField("b", "870975"))));
        record.getFields().add(new MarcField("016", "00", Arrays.asList(new MarcSubField("a", "87654321"))));
        assertThat(instance.getParentAgencyId(), is("870975"));
        assertThat(instance.getParentRecordId(), is("87654321"));
    }

    @Test
    public void testGetSubfieldValueMatchers_MutipleMatchesInSameField() {
        MarcRecord record = new MarcRecord();
        record.getFields().add(new MarcField("666", "00", Arrays.asList(new MarcSubField("u", "For 3-4 år"), new MarcSubField("u", "For 5-8 år"))));

        MarcRecordReader instance = new MarcRecordReader(record);

        String pattern = "^(For|for) ([0-9]+)-([0-9]+) (år)";
        Pattern p = Pattern.compile(pattern);
        List<Matcher> matchers = instance.getSubfieldValueMatchers("666", "u", p);
        assertThat(matchers.size(), is(2));
    }

    @Test
    public void testGetSubfieldValueMatchers_MutipleMatchesInMutipleFields() {
        MarcRecord record = new MarcRecord();
        record.getFields().add(new MarcField("666", "00", Arrays.asList(new MarcSubField("u", "For 3-4 år"))));
        record.getFields().add(new MarcField("666", "00", Arrays.asList(new MarcSubField("u", "For 5-8 år"))));

        MarcRecordReader instance = new MarcRecordReader(record);

        String pattern = "^(For|for) ([0-9]+)-([0-9]+) (år)";
        Pattern p = Pattern.compile(pattern);
        List<Matcher> matchers = instance.getSubfieldValueMatchers("666", "u", p);
        assertThat(matchers.size(), is(2));
    }

    @Test
    public void testGetSubfieldValueMatchers_SingleMatchInMutipleFields() {
        MarcRecord record = new MarcRecord();
        record.getFields().add(new MarcField("666", "00", Arrays.asList(new MarcSubField("u", "For 3-4 år"))));
        record.getFields().add(new MarcField("666", "00", Arrays.asList(new MarcSubField("u", "For 3 år"))));
        record.getFields().add(new MarcField("666", "00", Arrays.asList(new MarcSubField("u", "For 4 år"))));

        MarcRecordReader instance = new MarcRecordReader(record);

        String pattern = "^(For|for) ([0-9]+)-([0-9]+) (år)";
        Pattern p = Pattern.compile(pattern);
        List<Matcher> matchers = instance.getSubfieldValueMatchers("666", "u", p);
        assertThat(matchers.size(), is(1));
    }

    @Test
    public void testGetSubfieldValueMatchers_NoMatch() {
        MarcRecord record = new MarcRecord();
        record.getFields().add(new MarcField("666", "00", Arrays.asList(new MarcSubField("u", "For 3 år"))));
        record.getFields().add(new MarcField("666", "00", Arrays.asList(new MarcSubField("u", "For 4 år"))));

        MarcRecordReader instance = new MarcRecordReader(record);

        String pattern = "^(For|for) ([0-9]+)-([0-9]+) (år)";
        Pattern p = Pattern.compile(pattern);
        List<Matcher> matchers = instance.getSubfieldValueMatchers("666", "u", p);
        assertThat(matchers.size(), is(0));
    }
}
