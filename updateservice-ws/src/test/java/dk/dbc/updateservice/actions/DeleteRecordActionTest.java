//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordFactory;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.updateservice.update.RawRepo;
import org.junit.Test;

import java.io.InputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Mockito.mock;

//-----------------------------------------------------------------------------
public class DeleteRecordActionTest {
    /**
     * Test DeleteRecordAction.deletionMarkToStore() for store in RawRepo for a record.
     * <p>
     *     The result should always be <code>true</code>.
     * </p>
     */
    @Test
    public void testDeletionMarkToStore() throws Exception {
        InputStream is = getClass().getResourceAsStream( BOOK_RECORD_RESOURCE );
        MarcRecord record = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );

        RawRepo rawRepo = mock( RawRepo.class );
        DeleteRecordAction instance = new DeleteRecordAction( rawRepo, record );
        instance.setMimetype( MarcXChangeMimeType.MARCXCHANGE );

        assertThat( instance.deletionMarkToStore(), equalTo( true ) );
    }

    /**
     * Test DeleteRecordAction.recordToStore() to store a deleted record.
     */
    @Test
    public void testRecordToStore() throws Exception {
        InputStream is = getClass().getResourceAsStream( BOOK_RECORD_RESOURCE );
        MarcRecord record = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );

        RawRepo rawRepo = mock( RawRepo.class );
        DeleteRecordAction instance = new DeleteRecordAction( rawRepo, record );
        instance.setMimetype( MarcXChangeMimeType.MARCXCHANGE );

        is = getClass().getResourceAsStream( DELETED_BOOK_TO_STORE_RESOURCE );
        MarcRecord expected = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );
        assertThat( instance.recordToStore(), equalTo( expected ) );
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final String BOOK_RECORD_RESOURCE = "/dk/dbc/updateservice/actions/book.marc";
    private static final String DELETED_BOOK_TO_STORE_RESOURCE = "/dk/dbc/updateservice/actions/deleted_book_to_store.marc";
}
