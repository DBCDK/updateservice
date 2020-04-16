/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcSubField;
import dk.dbc.holdingsitems.HoldingsItemsDAO;
import dk.dbc.holdingsitems.HoldingsItemsException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class HoldingsItemsTest {

    @Mock
    HoldingsItemsDAO holdingsItemsDAO;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    private class MockHoldingsItems extends HoldingsItems {
        public MockHoldingsItems() {
            super();
        }

        @Override
        protected HoldingsItemsDAO createDAO() {
            return holdingsItemsDAO;
        }
    }

    @Test(expected = UpdateException.class)
    public void test_agenciesThatHasHoldingsFor_String_HoldingItemsException() throws Exception {
        when(holdingsItemsDAO.getAgenciesThatHasHoldingsFor(eq("12345678"))).thenThrow(new HoldingsItemsException("message"));

        HoldingsItems items = new MockHoldingsItems();
        items.getAgenciesThatHasHoldingsForId("12345678");
    }

    @Test
    public void test_agenciesThatHasHoldingsFor_String_NotFound() throws Exception {
        when(holdingsItemsDAO.getAgenciesThatHasHoldingsFor(eq("12345678"))).thenReturn(new HashSet<Integer>());

        HoldingsItems items = new MockHoldingsItems();
        assertTrue(items.getAgenciesThatHasHoldingsForId("12345678").isEmpty());
    }

    @Test
    public void test_agenciesThatHasHoldingsFor_String_Found() throws Exception {
        Set<Integer> libraries = new HashSet<>();
        libraries.add(700300);
        libraries.add(10100);

        when(holdingsItemsDAO.getAgenciesThatHasHoldingsFor(eq("12345678"))).thenReturn(libraries);

        HoldingsItems items = new MockHoldingsItems();
        assertEquals(libraries, items.getAgenciesThatHasHoldingsForId("12345678"));
    }

    @Test
    public void test_agenciesThatHasHoldingsFor_MarcRecord_NoIdField() throws Exception {
        when(holdingsItemsDAO.getAgenciesThatHasHoldingsFor(eq("12345678"))).thenReturn(new HashSet<Integer>());

        HoldingsItems items = new MockHoldingsItems();
        assertTrue(items.getAgenciesThatHasHoldingsFor(new MarcRecord()).isEmpty());
    }

    @Test
    public void test_agenciesThatHasHoldingsFor_MarcRecord_Found() throws Exception {
        Set<Integer> libraries = new HashSet<>();
        libraries.add(700300);
        libraries.add(10100);

        when(holdingsItemsDAO.getAgenciesThatHasHoldingsFor(eq("12345678"))).thenReturn(libraries);

        HoldingsItems items = new MockHoldingsItems();

        MarcRecord record = new MarcRecord();
        MarcField field = new MarcField("001", "00");
        field.getSubfields().add(new MarcSubField("a", "12345678"));
        record.getFields().add(field);

        assertEquals(libraries, items.getAgenciesThatHasHoldingsFor(record));
    }

    @Test
    public void test_agenciesThatHasHoldingsFor_MarcRecord_002() throws Exception {
        Set<Integer> libraries = new HashSet<Integer>();
        libraries.add(700300);
        libraries.add(10100);
        Set<Integer> libraries002 = new HashSet<Integer>();
        libraries002.add(700400);
        libraries002.add(101009);
        Set<Integer> resultlibs = new HashSet<Integer>();
        resultlibs.addAll(libraries);
        resultlibs.addAll(libraries002);

        when(holdingsItemsDAO.getAgenciesThatHasHoldingsFor(eq("12345678"))).thenReturn(libraries);
        when(holdingsItemsDAO.getAgenciesThatHasHoldingsFor(eq("02345678"))).thenReturn(libraries002);

        HoldingsItems items = new MockHoldingsItems();

        MarcRecord record = new MarcRecord();
        MarcField field = new MarcField("001", "00");
        field.getSubfields().add(new MarcSubField("a", "12345678"));
        record.getFields().add(field);
        MarcField field002 = new MarcField("002", "00");
        field002.getSubfields().add(new MarcSubField("a", "02345678"));
        record.getFields().add(field002);

        assertEquals(resultlibs, items.getAgenciesThatHasHoldingsFor(record));
    }
}
