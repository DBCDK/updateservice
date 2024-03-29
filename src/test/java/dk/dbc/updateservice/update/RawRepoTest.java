package dk.dbc.updateservice.update;

import dk.dbc.commons.metricshandler.MetricsHandlerBean;
import dk.dbc.rawrepo.RawRepoDAO;
import dk.dbc.rawrepo.RecordId;
import org.eclipse.microprofile.metrics.Tag;
import org.junit.jupiter.api.AfterEach;
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
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        assertThrows(IllegalArgumentException.class, () -> rawRepo.agenciesForRecord((String) (null)));

    }


    @Test
    void test_agenciesForRecord_MarcRecord_RecordIsNotFound() {
        RawRepo rawRepo = new MockRawRepo();
        assertThrows(IllegalArgumentException.class, () -> rawRepo.agenciesForRecord(""));
    }

    @Test
    void test_errormetrics_and_invocation_timers() {
        RawRepo rawRepo = new MockRawRepo();
        try {
            rawRepo.agenciesForRecord(null);
        } catch (IllegalArgumentException | UpdateException ignored) {

        } finally {
            verify(rawRepo.metricsHandler, times(1))
                    .increment(rawrepoErrorCounterMetrics,
                            new Tag(METHOD_NAME_KEY, "allAgenciesForBibliographicRecordId"),
                            new Tag(ERROR_TYPE, "recordid can not be null"));
        }
    }

    @Test
    void test_agenciesForRecord_MarcRecord_RecordIdIsFound() throws Exception {
        final String recId = "12346578";
        final Set<Integer> daoAgencies = new HashSet<>();
        daoAgencies.add(700400);
        daoAgencies.add(RawRepo.COMMON_AGENCY);

        when(dataSource.getConnection()).thenReturn(null);
        when(rawRepoDAO.allAgenciesForBibliographicRecordId(eq(recId))).thenReturn(daoAgencies);

        final RawRepo rawRepo = new MockRawRepo();
        final Set<Integer> agencies = rawRepo.agenciesForRecord(recId);
        assertThat(agencies.size(), is(1));
        assertTrue(agencies.contains(700400));
    }

    @Test
    void test_agenciesForRecord_String_RecordIdIsNull() {
        final RawRepo rawRepo = new MockRawRepo();
        assertThrows(IllegalArgumentException.class, () -> rawRepo.agenciesForRecord((String) (null)));
    }

    @Test
    void test_agenciesForRecord_String_RecordIdIsEmpty() {
        final RawRepo rawRepo = new MockRawRepo();
        assertThrows(IllegalArgumentException.class, () -> rawRepo.agenciesForRecord(""));
    }

    @Test
    void test_linkRecordAppend_noExistingLinks() throws Exception {
        final RecordId linkFrom = new RecordId("12345678", 870970);
        final RecordId linkTo = new RecordId("87654321", 870979);

        when(rawRepoDAO.getRelationsFrom(eq(linkFrom))).thenReturn(new HashSet<>());

        final RawRepo rawRepo = new MockRawRepo();

        rawRepo.linkRecordAppend(linkFrom, linkTo);

        ArgumentCaptor<RecordId> fromProvider = ArgumentCaptor.forClass(RecordId.class);
        Class<Set<RecordId>> listClass = (Class<Set<RecordId>>) (Class) Set.class;
        ArgumentCaptor<Set<RecordId>> toProvider = ArgumentCaptor.forClass(listClass);
        
        verify(rawRepoDAO, times(1)).setRelationsFrom(fromProvider.capture(), toProvider.capture());

        assertThat(fromProvider.getValue().getAgencyId(), is(870970));
        assertThat(fromProvider.getValue().getBibliographicRecordId(), is("12345678"));

        assertThat(toProvider.getValue().size(), is(1));
        assertThat(((RecordId) toProvider.getValue().toArray()[0]).getAgencyId(), is(870979));
        assertThat(((RecordId) toProvider.getValue().toArray()[0]).getBibliographicRecordId(), is("87654321"));
    }

    @Test
    void test_linkRecordAppend_ExistingLinks() throws Exception {
        final RecordId linkFrom = new RecordId("12345678", 870970);
        final RecordId linkTo = new RecordId("22222222", 870979);

        final Set<RecordId> existingLinks = new HashSet<>();
        existingLinks.add(new RecordId("11111111", 870979));

        when(rawRepoDAO.getRelationsFrom(eq(linkFrom))).thenReturn(existingLinks);

        final RawRepo rawRepo = new MockRawRepo();

        rawRepo.linkRecordAppend(linkFrom, linkTo);

        ArgumentCaptor<RecordId> fromProvider = ArgumentCaptor.forClass(RecordId.class);
        Class<Set<RecordId>> listClass = (Class<Set<RecordId>>) (Class) Set.class;
        ArgumentCaptor<Set<RecordId>> toProvider = ArgumentCaptor.forClass(listClass);

        verify(rawRepoDAO, times(1)).setRelationsFrom(fromProvider.capture(), toProvider.capture());

        assertThat(fromProvider.getValue().getAgencyId(), is(870970));
        assertThat(fromProvider.getValue().getBibliographicRecordId(), is("12345678"));

        assertThat(toProvider.getValue().size(), is(2));
        assertThat(((RecordId) toProvider.getValue().toArray()[0]).getAgencyId(), is(870979));
        assertThat(((RecordId) toProvider.getValue().toArray()[0]).getBibliographicRecordId(), is("11111111"));
        assertThat(((RecordId) toProvider.getValue().toArray()[1]).getAgencyId(), is(870979));
        assertThat(((RecordId) toProvider.getValue().toArray()[1]).getBibliographicRecordId(), is("22222222"));
    }

    @Test
    void test_checkProvider() throws Exception {
        when(rawRepoDAO.checkProvider(anyString())).thenReturn(false);
        when(rawRepoDAO.checkProvider(eq("found"))).thenReturn(true);

        RawRepo rawRepo = new MockRawRepo();

        assertThat(rawRepo.checkProvider("found"), is(true));
        assertThat(rawRepo.checkProvider("not-found"), is(false));
    }

}
