//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcReader;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordFactory;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.update.RawRepo;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.InputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

//-----------------------------------------------------------------------------
public class EnqueueRecordActionTest {
    /**
     * Test EnqueueRecordAction.performAction() to enqueue a record in the rawrepo.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with records.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Enqueue a record by its id.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          The rawrepo is called to enqueued the record and all records that
     *          link to it.
     *      </dd>
     * </dl>
     */
    @Test
    public void testActionPerform() throws Exception {
        InputStream is = getClass().getResourceAsStream( BOOK_RECORD_RESOURCE );
        MarcRecord record = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );
        String recordId = MarcReader.getRecordValue( record, "001", "a" );
        Integer agencyId = Integer.valueOf( MarcReader.getRecordValue( record, "001", "b" ), 10 );

        RawRepo rawRepo = mock( RawRepo.class );
        EnqueueRecordAction instance = new EnqueueRecordAction( rawRepo, record );
        instance.setMimetype( MarcXChangeMimeType.MARCXCHANGE );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        ArgumentCaptor<String> argProvider = ArgumentCaptor.forClass( String.class );
        ArgumentCaptor<RecordId> argId = ArgumentCaptor.forClass( RecordId.class );
        ArgumentCaptor<String> argMimetype = ArgumentCaptor.forClass( String.class );

        verify( rawRepo ).changedRecord( argProvider.capture(), argId.capture(), argMimetype.capture() );
        assertThat( argProvider.getValue(), equalTo( EnqueueRecordAction.PROVIDER ) );
        assertThat( argId.getValue(), equalTo( new RecordId( recordId, agencyId ) ) );
        assertThat( argMimetype.getValue(), equalTo( instance.getMimetype() ) );
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final String BOOK_RECORD_RESOURCE = "/dk/dbc/updateservice/actions/book.marc";
}
