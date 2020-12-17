/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcSubField;
import dk.dbc.commons.metricshandler.MetricsHandlerBean;
import dk.dbc.holdingsitems.HoldingsItemsDAO;
import dk.dbc.holdingsitems.HoldingsItemsException;
import org.eclipse.microprofile.metrics.Tag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HoldingsItemsTest {
    @Mock
    MetricsHandlerBean mockedMetricsHandlerBean;

    @Mock
    DataSource dataSource;

    @Mock
    HoldingsItemsDAO holdingsItemsDAO;

    private AutoCloseable closeable;

    @BeforeEach
    void openMocks() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void releaseMocks() throws Exception {
        closeable.close();
    }

    private class MockHoldingsItems extends HoldingsItems {
        public MockHoldingsItems() {
            super(dataSource);
            metricsHandlerBean = mockedMetricsHandlerBean;
        }

        @Override
        protected HoldingsItemsDAO createDAO(Connection conn) {
            return holdingsItemsDAO;
        }
    }

    @Test
    void test_agenciesThatHasHoldingsFor_String_DataSourceException() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("message"));

        HoldingsItems items = new MockHoldingsItems();
        assertThrows(UpdateException.class, () -> items.getAgenciesThatHasHoldingsForId("12345678"));
    }

    @Test
    void test_that_metrics_for_timer_and_error_counter_are_set_properly() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("message"));
        HoldingsItems items = new MockHoldingsItems();

        try {
            assertThrows(UpdateException.class, () -> items.getAgenciesThatHasHoldingsForId("12345678"));
        } finally {
            verify(items.metricsHandlerBean, times(1))
                    .increment(HoldingsItems.holdingsItemsErrorCounterMetrics,
                            new Tag(HoldingsItems.METHOD_NAME_KEY, "getAgenciesThatHasHoldingsForId"),
                            new Tag(HoldingsItems.ERROR_TYPE, "message"));
            verify(items.metricsHandlerBean, times(1))
                    .update(eq(HoldingsItems.holdingsItemsTimingMetrics),
                            any(Duration.class),
                            eq(new Tag(HoldingsItems.METHOD_NAME_KEY, "getAgenciesThatHasHoldingsForId")));
        }
    }

    @Test
    void test_agenciesThatHasHoldingsFor_String_HoldingItemsException() throws Exception {
        when(dataSource.getConnection()).thenReturn(null);
        when(holdingsItemsDAO.getAgenciesThatHasHoldingsFor(eq("12345678"))).thenThrow(new HoldingsItemsException("message"));

        HoldingsItems items = new MockHoldingsItems();
        assertThrows(UpdateException.class, () -> items.getAgenciesThatHasHoldingsForId("12345678"));
    }

    @Test
    void test_agenciesThatHasHoldingsFor_String_NotFound() throws Exception {
        when(dataSource.getConnection()).thenReturn(null);
        when(holdingsItemsDAO.getAgenciesThatHasHoldingsFor(eq("12345678"))).thenReturn(new HashSet<>());

        HoldingsItems items = new MockHoldingsItems();
        assertTrue(items.getAgenciesThatHasHoldingsForId("12345678").isEmpty());
    }

    @Test
    void test_agenciesThatHasHoldingsFor_String_Found() throws Exception {
        Set<Integer> libraries = new HashSet<>();
        libraries.add(700300);
        libraries.add(10100);

        when(dataSource.getConnection()).thenReturn(null);
        when(holdingsItemsDAO.getAgenciesThatHasHoldingsFor(eq("12345678"))).thenReturn(libraries);

        HoldingsItems items = new MockHoldingsItems();
        assertThat(items.getAgenciesThatHasHoldingsForId("12345678"), is(libraries));
    }

    @Test
    void test_agenciesThatHasHoldingsFor_MarcRecord_NoIdField() throws Exception {
        when(dataSource.getConnection()).thenReturn(null);
        when(holdingsItemsDAO.getAgenciesThatHasHoldingsFor(eq("12345678"))).thenReturn(new HashSet<>());

        HoldingsItems items = new MockHoldingsItems();
        assertTrue(items.getAgenciesThatHasHoldingsFor(new MarcRecord()).isEmpty());
    }

    @Test
    void test_agenciesThatHasHoldingsFor_MarcRecord_Found() throws Exception {
        Set<Integer> libraries = new HashSet<>();
        libraries.add(700300);
        libraries.add(10100);

        when(dataSource.getConnection()).thenReturn(null);
        when(holdingsItemsDAO.getAgenciesThatHasHoldingsFor(eq("12345678"))).thenReturn(libraries);

        HoldingsItems items = new MockHoldingsItems();

        MarcRecord record = new MarcRecord();
        MarcField field = new MarcField("001", "00");
        field.getSubfields().add(new MarcSubField("a", "12345678"));
        record.getFields().add(field);

        assertThat(items.getAgenciesThatHasHoldingsFor(record), is(libraries));
    }

    @Test
    void test_agenciesThatHasHoldingsFor_MarcRecord_002() throws Exception {
        Set<Integer> libraries = new HashSet<>();
        libraries.add(700300);
        libraries.add(10100);
        Set<Integer> libraries002 = new HashSet<>();
        libraries002.add(700400);
        libraries002.add(101009);
        Set<Integer> resultlibs = new HashSet<>();
        resultlibs.addAll(libraries);
        resultlibs.addAll(libraries002);

        when(dataSource.getConnection()).thenReturn(null);
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

        assertThat(items.getAgenciesThatHasHoldingsFor(record), is(resultlibs));
    }
}
