package dk.dbc.common.records;

import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordFactory;
import dk.dbc.common.records.MarcSubField;
import dk.dbc.common.records.utils.IOUtils;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MarcRecordFactoryTest {

    public MarcRecordFactoryTest() {
    }

    @Test
    public void testRegExps() {
        System.out.println("testRegExps");
        assertTrue("001 00".matches(MarcRecordFactory.FIELD_PATTERN));
        assertTrue("d01 00".matches(MarcRecordFactory.FIELD_PATTERN));
        assertTrue("001  7".matches(MarcRecordFactory.FIELD_PATTERN));
        assertTrue("001  7 *a".matches(MarcRecordFactory.FIELD_PATTERN));
    }

    /**
     * Test of readSubField method, of class MarcRecordFactory.
     */
    @Test
    public void testReadRecord() throws Exception {
        System.out.println("readRecord");
        MarcRecord rec;
        String data = IOUtils.readAll(getClass().getResourceAsStream("rec03034232.txt"), "UTF-8");
        rec = MarcRecordFactory.readRecord(data);
        assertNotNull(rec.getFields());
        assertFalse(rec.getFields().isEmpty());
        assertEquals("001 00 *a 0 303 423 2 *b 870970 *c 19910823 *d 19820601 *f a *o c", rec.getFields().get(0).toString());
        assertEquals("004 00 *r n *a e", rec.getFields().get(1).toString());
        assertEquals("008 00 *t s *v 1", rec.getFields().get(2).toString());
    }

    /**
     * Test of readSubField method, of class MarcRecordFactory.
     */
    @Test
    public void testReadField() {
        System.out.println("readField");
        assertNull(MarcRecordFactory.readField(null));
        assertNull(MarcRecordFactory.readField(""));

        assertEquals(new MarcField("100", "00", new ArrayList<>()), MarcRecordFactory.readField("100 00"));

        List<MarcSubField> subfields;
        subfields = Collections.singletonList(new MarcSubField("a", "xxx"));
        assertEquals(new MarcField("100", "00", subfields).toString(), MarcRecordFactory.readField("100 00 *a xxx").toString());

        subfields = Arrays.asList(new MarcSubField("a", "xxx"), new MarcSubField("b", "yyy"));
        assertEquals(new MarcField("100", "00", subfields).toString(), MarcRecordFactory.readField("100 00 *a xxx *b yyy").toString());

        subfields = Arrays.asList(new MarcSubField("a", "0 303 423 2"),
                new MarcSubField("b", "870970"),
                new MarcSubField("c", "19910823"),
                new MarcSubField("d", "19820601"),
                new MarcSubField("f", "a"),
                new MarcSubField("o", "c"));
        assertEquals(new MarcField("001", "00", subfields).toString(), MarcRecordFactory.readField("001 00*a0 303 423 2*b870970*c19910823*d19820601*fa*oc").toString());
    }

    /**
     * Test of readSubField method, of class MarcRecordFactory.
     */
    @Test
    public void testReadSubField() {
        System.out.println("readSubField");
        assertEquals(null, MarcRecordFactory.readSubField(""));
        assertEquals(null, MarcRecordFactory.readSubField("*"));
        assertEquals(new MarcSubField("*", ""), MarcRecordFactory.readSubField("**"));
        assertEquals(new MarcSubField("a", ""), MarcRecordFactory.readSubField("*a "));
        assertEquals(new MarcSubField("a", "Tekst"), MarcRecordFactory.readSubField("*a Tekst"));
        assertEquals(new MarcSubField("a", "Tekst"), MarcRecordFactory.readSubField("*a Tekst   "));
        assertEquals(new MarcSubField("a", "xxx *b yyy"), MarcRecordFactory.readSubField("*a xxx @*b yyy"));
    }
}
