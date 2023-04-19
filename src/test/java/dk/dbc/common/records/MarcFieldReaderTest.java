package dk.dbc.common.records;

import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcFieldReader;
import dk.dbc.common.records.MarcSubField;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNull.nullValue;

public class MarcFieldReaderTest {
    @Test
    public void testGetValue() {
        MarcField field = new MarcField("245", "00");
        field.getSubfields().add(new MarcSubField("a", "v"));
        field.getSubfields().add(new MarcSubField("x", "x1"));
        field.getSubfields().add(new MarcSubField("x", "x2"));

        MarcFieldReader instance = new MarcFieldReader(field);
        assertThat(instance.getValue("z"), nullValue());
        assertThat(instance.getValue("a"), equalTo("v"));
        assertThat(instance.getValue("x"), equalTo("x1"));
    }

    @Test
    public void testGetValues() {
        MarcField field = new MarcField("245", "00");
        field.getSubfields().add(new MarcSubField("a", "v"));
        field.getSubfields().add(new MarcSubField("x", "x1"));
        field.getSubfields().add(new MarcSubField("x", "x2"));

        List<String> expected;
        MarcFieldReader instance = new MarcFieldReader(field);

        expected = new ArrayList<>();
        assertThat(instance.getValues("z"), equalTo(expected));

        expected = Arrays.asList("v");
        assertThat(instance.getValues("a"), equalTo(expected));

        expected = Arrays.asList("x1", "x2");
        assertThat(instance.getValues("x"), equalTo(expected));
    }

    @Test
    public void testHasValue() {
        MarcField field = new MarcField("245", "00");
        field.getSubfields().add(new MarcSubField("0", ""));
        field.getSubfields().add(new MarcSubField("a", "v"));
        field.getSubfields().add(new MarcSubField("x", "x1"));
        field.getSubfields().add(new MarcSubField("x", "x2"));

        MarcFieldReader instance = new MarcFieldReader(field);
        assertThat(instance.hasValue("z", null), is(false));
        assertThat(instance.hasValue("a", null), is(false));
        assertThat(instance.hasValue("a", "v"), is(true));
        assertThat(instance.hasValue("a", "V"), is(false));
        assertThat(instance.hasValue("x", "x2"), is(true));
    }

    @Test
    public void testHasSubfield() {
        MarcField field = new MarcField("245", "00");
        field.getSubfields().add(new MarcSubField("a", "v"));
        field.getSubfields().add(new MarcSubField("x", "x1"));
        field.getSubfields().add(new MarcSubField("x", "x2"));

        MarcFieldReader instance = new MarcFieldReader(field);
        assertThat(instance.hasSubfield("z"), equalTo(false));
        assertThat(instance.hasSubfield("a"), equalTo(true));
        assertThat(instance.hasSubfield("x"), equalTo(true));
    }
}
