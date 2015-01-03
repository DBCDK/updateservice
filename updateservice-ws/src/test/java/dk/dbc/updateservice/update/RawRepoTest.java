//-----------------------------------------------------------------------------
package dk.dbc.updateservice.update;

//-----------------------------------------------------------------------------

import dk.dbc.iscrum.records.MarcField;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcSubField;
import dk.dbc.rawrepo.RawRepoDAO;
import dk.dbc.rawrepo.RawRepoException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

/**
 * Created by stp on 18/12/14.
 */
public class RawRepoTest {
    //-------------------------------------------------------------------------
    //              Mocking
    //-------------------------------------------------------------------------

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
            super( dataSourceReader, dataSourceWriter );
        }

        @Override
        protected RawRepoDAO createDAO( Connection conn ) throws RawRepoException {
            return rawRepoDAO;
        }
    }

    //-------------------------------------------------------------------------
    //              Tests of RawRepo.agenciesForRecord
    //-------------------------------------------------------------------------

    @Test( expected = IllegalArgumentException.class )
    public void test_agenciesForRecord_MarcRecord_RecordIsNull() throws Exception {
        RawRepo rawRepo = new MockRawRepo();
        rawRepo.agenciesForRecord( (MarcRecord) ( null ) );
    }

    @Test( expected = IllegalArgumentException.class )
    public void test_agenciesForRecord_MarcRecord_RecordIsNotFound() throws Exception {
        RawRepo rawRepo = new MockRawRepo();
        rawRepo.agenciesForRecord( new MarcRecord() );
    }

    @Test
    public void test_agenciesForRecord_MarcRecord_RecordIdIsFound() throws Exception {
        String recId = "12346578";
        MarcRecord record = createRecord( recId, "700400" );

        Set<Integer> daoAgencies = new HashSet<>();
        daoAgencies.add( 700400 );
        daoAgencies.add( rawRepoDAO.COMMON_LIBRARY );

        when( dataSourceReader.getConnection() ).thenReturn( null );
        when( rawRepoDAO.allAgenciesForBibliographicRecordId( eq( recId ) ) ).thenReturn( daoAgencies );

        RawRepo rawRepo = new MockRawRepo();
        Set<Integer> agencies = rawRepo.agenciesForRecord( record );
        assertEquals( 2, agencies.size() );
        assertTrue( agencies.contains( 870970 ) );
        assertTrue( agencies.contains( 700400 ) );
    }

    @Test( expected = IllegalArgumentException.class )
    public void test_agenciesForRecord_String_RecordIdIsNull() throws Exception {
        RawRepo rawRepo = new MockRawRepo();
        rawRepo.agenciesForRecord( (String)(null) );
    }

    @Test( expected = IllegalArgumentException.class )
    public void test_agenciesForRecord_String_RecordIdIsEmpty() throws Exception {
        RawRepo rawRepo = new MockRawRepo();
        rawRepo.agenciesForRecord( "" );
    }

    //-------------------------------------------------------------------------
    //              Helpers
    //-------------------------------------------------------------------------

    private MarcRecord createRecord( String id, String agencyId ) {
        MarcRecord record = new MarcRecord();

        MarcField field = new MarcField( "001", "00" );

        if( id != null ) {
            field.getSubfields().add( new MarcSubField( "a", id ) );
        }
        if( agencyId != null ) {
            field.getSubfields().add( new MarcSubField( "b", agencyId ) );
        }
        record.getFields().add( field );

        return record;
    }
}
