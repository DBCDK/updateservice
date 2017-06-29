/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.iscrum.records.MarcField;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcSubField;
import dk.dbc.rawrepo.RawRepoDAO;
import dk.dbc.rawrepo.RawRepoException;
import dk.dbc.rawrepo.RecordId;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.*;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class RawRepoTest {

    @Mock
    DataSource dataSourceReader;

    @Mock
    DataSource dataSourceWriter;

    @Mock
    RawRepoDAO rawRepoDAO;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    private class MockRawRepo extends RawRepo {
        public MockRawRepo() {
            super(dataSourceReader, dataSourceWriter);
        }

        @Override
        protected RawRepoDAO createDAO(Connection conn) throws RawRepoException {
            return rawRepoDAO;
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_agenciesForRecord_MarcRecord_RecordIsNull() throws Exception {
        RawRepo rawRepo = new MockRawRepo();
        rawRepo.agenciesForRecord((MarcRecord) (null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_agenciesForRecord_MarcRecord_RecordIsNotFound() throws Exception {
        RawRepo rawRepo = new MockRawRepo();
        rawRepo.agenciesForRecord(new MarcRecord());
    }

    @Test
    public void test_agenciesForRecord_MarcRecord_RecordIdIsFound() throws Exception {
        String recId = "12346578";
        MarcRecord record = createRecord(recId, "700400");

        Set<Integer> daoAgencies = new HashSet<>();
        daoAgencies.add(700400);
        daoAgencies.add(RawRepo.COMMON_AGENCY);

        when(dataSourceReader.getConnection()).thenReturn(null);
        when(rawRepoDAO.allAgenciesForBibliographicRecordId(eq(recId))).thenReturn(daoAgencies);

        RawRepo rawRepo = new MockRawRepo();
        Set<Integer> agencies = rawRepo.agenciesForRecord(record);
        assertEquals(1, agencies.size());
        assertTrue(agencies.contains(700400));
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_agenciesForRecord_String_RecordIdIsNull() throws Exception {
        RawRepo rawRepo = new MockRawRepo();
        rawRepo.agenciesForRecord((String) (null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_agenciesForRecord_String_RecordIdIsEmpty() throws Exception {
        RawRepo rawRepo = new MockRawRepo();
        rawRepo.agenciesForRecord("");
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
    public void test_linkRecordAppend_noExistingLinks() throws Exception {
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
    public void test_linkRecordAppend_ExistingLinks() throws Exception {
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

}
