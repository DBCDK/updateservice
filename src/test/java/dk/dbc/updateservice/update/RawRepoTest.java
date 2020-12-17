/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcSubField;
import dk.dbc.commons.metricshandler.MetricsHandlerBean;
import dk.dbc.rawrepo.RawRepoDAO;
import dk.dbc.rawrepo.RecordId;
import org.eclipse.microprofile.metrics.Tag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashSet;
import java.util.Set;

import static dk.dbc.updateservice.update.RawRepo.ERROR_TYPE;
import static dk.dbc.updateservice.update.RawRepo.METHOD_NAME_KEY;
import static dk.dbc.updateservice.update.RawRepo.rawrepoErrorCounterMetrics;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RawRepoTest {

    @Mock
    DataSource dataSource;

    @Mock
    RawRepoDAO rawRepoDAO;

    private class MockRawRepo extends RawRepo {
        public MockRawRepo() {
            super(dataSource);
            metricsHandler = Mockito.mock(MetricsHandlerBean.class);
        }

        @Override
        protected RawRepoDAO createDAO(Connection conn) {
            return rawRepoDAO;
        }
    }

    private AutoCloseable closeable;

    @BeforeEach
    void openMocks() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void releaseMocks() throws Exception {
        closeable.close();
    }

    @Test
    void test_agenciesForRecord_MarcRecord_RecordIsNull() {
        RawRepo rawRepo = new MockRawRepo();
        Assertions.assertThrows(IllegalArgumentException.class, () -> rawRepo.agenciesForRecord((MarcRecord) (null)));

    }


    @Test
    void test_agenciesForRecord_MarcRecord_RecordIsNotFound() {
        RawRepo rawRepo = new MockRawRepo();
        Assertions.assertThrows(IllegalArgumentException.class, () -> rawRepo.agenciesForRecord(new MarcRecord()));
    }

    @Test
    void test_errormetrics_and_invocation_timers() {
        RawRepo rawRepo = new MockRawRepo();
        try {
            rawRepo.agenciesForRecord(new MarcRecord());
        } catch (Exception e) {
            System.out.println(e.getMessage().toLowerCase());
        } finally {
            verify(rawRepo.metricsHandler, times(1))
                    .increment(rawrepoErrorCounterMetrics,
                            new Tag(METHOD_NAME_KEY, "allAgenciesForBibliographicRecordId"),
                            new Tag(ERROR_TYPE, "recordid can not be null"));
        }
    }

    @Test
    void test_agenciesForRecord_MarcRecord_RecordIdIsFound() throws Exception {
        String recId = "12346578";
        MarcRecord record = createRecord(recId, "700400");

        Set<Integer> daoAgencies = new HashSet<>();
        daoAgencies.add(700400);
        daoAgencies.add(RawRepo.COMMON_AGENCY);

        when(dataSource.getConnection()).thenReturn(null);
        when(rawRepoDAO.allAgenciesForBibliographicRecordId(eq(recId))).thenReturn(daoAgencies);

        RawRepo rawRepo = new MockRawRepo();
        Set<Integer> agencies = rawRepo.agenciesForRecord(record);
        assertEquals(1, agencies.size());
        assertTrue(agencies.contains(700400));
    }

    @Test
    void test_agenciesForRecord_String_RecordIdIsNull() {
        RawRepo rawRepo = new MockRawRepo();
        Assertions.assertThrows(IllegalArgumentException.class, () -> rawRepo.agenciesForRecord((String) (null)));
    }

    @Test
    void test_agenciesForRecord_String_RecordIdIsEmpty() {
        RawRepo rawRepo = new MockRawRepo();
        Assertions.assertThrows(IllegalArgumentException.class, () -> rawRepo.agenciesForRecord(""));
    }

    private MarcRecord createRecord(String id, String agencyId) {
        MarcRecord record = new MarcRecord();

        MarcField field = new MarcField("001", "00");

        if (id != null) {
            field.getSubfields().add(new MarcSubField("a", id));
        }
        if (agencyId != null) {
            field.getSubfields().add(new MarcSubField("b", agencyId));
        }
        record.getFields().add(field);

        return record;
    }

    @Test
    void test_linkRecordAppend_noExistingLinks() throws Exception {
        RecordId linkFrom = new RecordId("12345678", 870970);
        RecordId linkTo = new RecordId("87654321", 870979);

        when(rawRepoDAO.getRelationsFrom(eq(linkFrom))).thenReturn(new HashSet<>());

        RawRepo rawRepo = new MockRawRepo();

        rawRepo.linkRecordAppend(linkFrom, linkTo);

        ArgumentCaptor<RecordId> fromProvider = ArgumentCaptor.forClass(RecordId.class);
        Class<Set<RecordId>> listClass = (Class<Set<RecordId>>) (Class) Set.class;
        ArgumentCaptor<Set<RecordId>> toProvider = ArgumentCaptor.forClass(listClass);

        verify(rawRepoDAO, times(1)).setRelationsFrom(fromProvider.capture(), toProvider.capture());

        assertThat(fromProvider.getValue().getAgencyId(), equalTo(870970));
        assertThat(fromProvider.getValue().getBibliographicRecordId(), equalTo("12345678"));

        assertThat(toProvider.getValue().size(), equalTo(1));
        assertThat(((RecordId) toProvider.getValue().toArray()[0]).getAgencyId(), equalTo(870979));
        assertThat(((RecordId) toProvider.getValue().toArray()[0]).getBibliographicRecordId(), equalTo("87654321"));
    }

    @Test
    void test_linkRecordAppend_ExistingLinks() throws Exception {
        RecordId linkFrom = new RecordId("12345678", 870970);
        RecordId linkTo = new RecordId("22222222", 870979);

        Set<RecordId> existingLinks = new HashSet<>();
        existingLinks.add(new RecordId("11111111", 870979));

        when(rawRepoDAO.getRelationsFrom(eq(linkFrom))).thenReturn(existingLinks);

        RawRepo rawRepo = new MockRawRepo();

        rawRepo.linkRecordAppend(linkFrom, linkTo);

        ArgumentCaptor<RecordId> fromProvider = ArgumentCaptor.forClass(RecordId.class);
        Class<Set<RecordId>> listClass = (Class<Set<RecordId>>) (Class) Set.class;
        ArgumentCaptor<Set<RecordId>> toProvider = ArgumentCaptor.forClass(listClass);

        verify(rawRepoDAO, times(1)).setRelationsFrom(fromProvider.capture(), toProvider.capture());

        assertThat(fromProvider.getValue().getAgencyId(), equalTo(870970));
        assertThat(fromProvider.getValue().getBibliographicRecordId(), equalTo("12345678"));

        assertThat(toProvider.getValue().size(), equalTo(2));
        assertThat(((RecordId) toProvider.getValue().toArray()[0]).getAgencyId(), equalTo(870979));
        assertThat(((RecordId) toProvider.getValue().toArray()[0]).getBibliographicRecordId(), equalTo("11111111"));
        assertThat(((RecordId) toProvider.getValue().toArray()[1]).getAgencyId(), equalTo(870979));
        assertThat(((RecordId) toProvider.getValue().toArray()[1]).getBibliographicRecordId(), equalTo("22222222"));
    }

    @Test
    void test_checkProvider() throws Exception {
        when(rawRepoDAO.checkProvider(anyString())).thenReturn(false);
        when(rawRepoDAO.checkProvider(eq("found"))).thenReturn(true);

        RawRepo rawRepo = new MockRawRepo();

        assertThat(rawRepo.checkProvider("found"), equalTo(true));
        assertThat(rawRepo.checkProvider("not-found"), equalTo(false));
    }

}
