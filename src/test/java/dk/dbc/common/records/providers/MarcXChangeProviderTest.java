package dk.dbc.common.records.providers;

import dk.dbc.common.records.MarcRecord;
import org.junit.Test;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.xml.bind.DataBindingException;
import java.io.File;
import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MarcXChangeProviderTest {
    @Test(expected = DataBindingException.class)
    public void testUnknownFile() throws Exception {
        MarcXChangeProvider instance = new MarcXChangeProvider(new File("known-filename"));
    }

    @Test(expected = DataBindingException.class)
    public void testInvalidXml() throws Exception {
        File file = new File(getClass().getResource("invalid_xml.xml").toURI());
        MarcXChangeProvider instance = new MarcXChangeProvider(file);
    }

    @Test
    public void testRecord() throws Exception {
        File file = new File(getClass().getResource("record.xml").toURI());
        MarcXChangeProvider instance = new MarcXChangeProvider(file);
        assertFalse(instance.hasMultibleRecords());

        Iterator<MarcRecord> iterator = instance.iterator();

        assertTrue(iterator.hasNext());
        assertEquals("1 234 567 8", iterator.next().getFields().get(0).getSubfields().get(0).getValue());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testCollection() throws Exception {
        File file = new File(getClass().getResource("collection.xml").toURI());
        MarcXChangeProvider instance = new MarcXChangeProvider(file);
        assertTrue(instance.hasMultibleRecords());

        Iterator<MarcRecord> iterator = instance.iterator();

        assertTrue(iterator.hasNext());
        assertEquals("1 234 567 8", iterator.next().getFields().get(0).getSubfields().get(0).getValue());
        assertTrue(iterator.hasNext());
        assertEquals("2 234 567 8", iterator.next().getFields().get(0).getSubfields().get(0).getValue());
        assertTrue(iterator.hasNext());
        assertEquals("3 234 567 8", iterator.next().getFields().get(0).getSubfields().get(0).getValue());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testEmptyCollection() throws Exception {
        logger.entry();

        try {
            File file = new File(getClass().getResource("empty_collection.xml").toURI());

            MarcXChangeProvider instance = new MarcXChangeProvider(file);
            assertNotNull(instance);
            assertFalse(instance.hasMultibleRecords());

            Iterator<MarcRecord> iterator = instance.iterator();
            assertNotNull(iterator);

            assertTrue(iterator.hasNext());
            assertEquals("", iterator.next().toString());
        } finally {
            logger.exit();
        }
    }

    private static final XLogger logger = XLoggerFactory.getXLogger(MarcXChangeProviderTest.class);

}
