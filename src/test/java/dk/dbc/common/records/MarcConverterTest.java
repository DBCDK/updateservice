package dk.dbc.common.records;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dk.dbc.common.records.MarcConverter;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.marcxchange.CollectionType;
import dk.dbc.common.records.utils.IOUtils;
import org.junit.Test;

import javax.xml.bind.DataBindingException;
import javax.xml.bind.JAXB;
import java.io.InputStream;
import java.io.InputStreamReader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;


public class MarcConverterTest {

    /**
     * @test Case where the input file is not valid xml.
     */
    @Test(expected = DataBindingException.class)
    public void testCreateFromMarcXChange_InvalidXml() throws Exception {
        System.out.println("testCreateFromMarcXChange_InvalidXml()");
        InputStream in = getClass().getResourceAsStream("invalid_xml.xml");
        MarcConverter.convertFromMarcXChange(IOUtils.readAll(in, "UTF-8"));
    }

    /**
     * @test Tests valid xml, that does not apply to the xsd.
     */
    @Test
    public void testCreateFromMarcXChange_Wrong_Record_Xsd() throws Exception {
        System.out.println("testCreateFromMarcXChange_Wrong_Record_Xsd()");

        InputStream in = getClass().getResourceAsStream("wrong_record_xsd.xml");
        MarcConverter.convertFromMarcXChange(IOUtils.readAll(in, "UTF-8"));

        in = getClass().getResourceAsStream("wrong_record_xsd.xml");
        CollectionType collection = JAXB.unmarshal(new InputStreamReader(in), CollectionType.class);
        assertNotNull(collection);
        assertEquals(0, collection.getRecord().size());
    }

    /**
     * @test Tests valid xml, that contains a single valid record.
     */
    @Test
    public void testCreateFromMarcXChange_Single_Record() throws Exception {
        System.out.println("testCreateFromMarcXChange_Single_Record()");

        InputStream in = getClass().getResourceAsStream("single_record.xml");
        MarcRecord rec = MarcConverter.convertFromMarcXChange(IOUtils.readAll(in, "UTF-8"));

        assertEquals(2, rec.getFields().size());

        assertEquals("001", rec.getFields().get(0).getName());
        assertEquals("10", rec.getFields().get(0).getIndicator());
        assertEquals(5, rec.getFields().get(0).getSubfields().size());
        assertEquals("a", rec.getFields().get(0).getSubfields().get(0).getName());
        assertEquals("1 234 567 8", rec.getFields().get(0).getSubfields().get(0).getValue());
        assertEquals("b", rec.getFields().get(0).getSubfields().get(1).getName());
        assertEquals("870970", rec.getFields().get(0).getSubfields().get(1).getValue());
        assertEquals("c", rec.getFields().get(0).getSubfields().get(2).getName());
        assertEquals("12072013", rec.getFields().get(0).getSubfields().get(2).getValue());
        assertEquals("d", rec.getFields().get(0).getSubfields().get(3).getName());
        assertEquals("12072013101735", rec.getFields().get(0).getSubfields().get(3).getValue());
        assertEquals("f", rec.getFields().get(0).getSubfields().get(4).getName());
        assertEquals("a", rec.getFields().get(0).getSubfields().get(4).getValue());

        assertEquals("004", rec.getFields().get(1).getName());
        assertEquals("10", rec.getFields().get(1).getIndicator());
        assertEquals(2, rec.getFields().get(1).getSubfields().size());
        assertEquals("a", rec.getFields().get(1).getSubfields().get(0).getName());
        assertEquals("e", rec.getFields().get(1).getSubfields().get(0).getValue());
        assertEquals("r", rec.getFields().get(1).getSubfields().get(1).getName());
        assertEquals("n", rec.getFields().get(1).getSubfields().get(1).getValue());
    }

    /**
     * @test Tests valid xml, that contains a single valid record.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testCreateFromMarcXChange_Single_Record_with_spaces() throws Exception {
        System.out.println("testCreateFromMarcXChange_Single_Record_with_spaces()");
        String in = "<ns1:collection xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:ns1=\"info:lc/xmlns/marcxchange-v1\" xsi:schemaLocation=\"info:lc/xmlns/marcxchange-v1\">" +
                "<ns1:record>" +
                "<ns1:datafield tag=\"001\" ind1=\"1\" ind2=\"0\">" +
                "<ns1:subfield code=\" a \">1 234 567 8</ns1:subfield>" +
                "<ns1:subfield code=\" b \">870970</ns1:subfield>" +
                "<ns1:subfield code=\" c \">12072013</ns1:subfield>" +
                "<ns1:subfield code=\"d\">12072013101735</ns1:subfield>" +
                "</ns1:datafield>" +
                "</ns1:record>" +
                "</ns1:collection>";
        MarcConverter.convertFromMarcXChange(in);
    }

    @Test
    public void createJsonFromMarcXChange() throws Exception {
        InputStream in = getClass().getResourceAsStream("single_record.xml");
        MarcRecord rec = MarcConverter.convertFromMarcXChange(IOUtils.readAll(in, "UTF-8"));
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        assertNotEquals("", gson.toJson(rec));
    }
}
