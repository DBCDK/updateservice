package dk.dbc.common.records;

import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcFieldWriter;
import dk.dbc.common.records.MarcSubField;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

public class MarcFieldWriterTest {

    private MarcField createMarcField() {
        MarcField result = new MarcField();

        result.getSubfields().add(new MarcSubField("a", "value a"));
        result.getSubfields().add(new MarcSubField("b", "value b"));
        result.getSubfields().add(new MarcSubField("c", "value c"));

        return result;
    }

    @Test
    public void testAddOrReplaceSubfield1() throws Exception {
        MarcField field = createMarcField();

        MarcFieldWriter instance = new MarcFieldWriter(field);
        instance.addOrReplaceSubfield("a", "b");

        MarcField expected = new MarcField();

        expected.getSubfields().add(new MarcSubField("a", "b"));
        expected.getSubfields().add(new MarcSubField("b", "value b"));
        expected.getSubfields().add(new MarcSubField("c", "value c"));

        assertThat(field, equalTo(expected));
    }

    @Test
    public void testAddOrReplaceSubfield2() throws Exception {
        MarcField field = createMarcField();

        MarcFieldWriter instance = new MarcFieldWriter(field);
        instance.addOrReplaceSubfield("d", "d");

        MarcField expected = createMarcField();
        expected.getSubfields().add(new MarcSubField("d", "d"));

        assertThat(field, equalTo(expected));
    }

    @Test
    public void testAddOrReplaceSubfield3() throws Exception {
        MarcField field = createMarcField();
        field.getSubfields().add(new MarcSubField("a", "xxx"));

        MarcFieldWriter instance = new MarcFieldWriter(field);
        instance.addOrReplaceSubfield("a", "d");

        MarcField expected = new MarcField();

        expected.getSubfields().add(new MarcSubField("a", "d"));
        expected.getSubfields().add(new MarcSubField("b", "value b"));
        expected.getSubfields().add(new MarcSubField("c", "value c"));
        expected.getSubfields().add(new MarcSubField("a", "xxx"));

        assertThat(field, equalTo(expected));
    }

    @Test
    public void testRemoveFieldNone() throws Exception {
        MarcField field = createMarcField();

        MarcFieldWriter instance = new MarcFieldWriter(field);
        instance.removeSubfield("m");

        MarcField expected = createMarcField();

        assertThat(field, equalTo(expected));
    }

    @Test
    public void testRemoveFieldSingle() throws Exception {
        MarcField field = createMarcField();
        field.getSubfields().add(new MarcSubField("m", "1"));

        MarcFieldWriter instance = new MarcFieldWriter(field);
        instance.removeSubfield("m");

        MarcField expected = createMarcField();

        assertThat(field, equalTo(expected));
    }

    @Test
    public void testRemoveFieldMultiple() throws Exception {
        MarcField field = createMarcField();
        field.getSubfields().add(new MarcSubField("m", "1"));
        field.getSubfields().add(new MarcSubField("m", "2"));

        MarcFieldWriter instance = new MarcFieldWriter(field);
        instance.removeSubfield("m");

        MarcField expected = createMarcField();

        assertThat(field, equalTo(expected));
    }

}
