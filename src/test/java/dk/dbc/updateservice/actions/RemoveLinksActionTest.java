package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordFactory;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.update.RawRepo;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.InputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class RemoveLinksActionTest {
    private static final String BOOK_RECORD_RESOURCE = "/dk/dbc/updateservice/actions/book.marc";

    /**
     * Test RemovesLinksAction.performAction() to remove all links from a record.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Remove the links for a record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * The rawrepo is called to remove all links for the record.
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction() throws Exception {
        InputStream is = getClass().getResourceAsStream(BOOK_RECORD_RESOURCE);
        MarcRecord record = MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));

        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.recordId();
        Integer agencyId = reader.agencyIdAsInteger();

        RawRepo rawRepo = mock(RawRepo.class);
        RemoveLinksAction instance = new RemoveLinksAction(rawRepo, record);

        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        ArgumentCaptor<RecordId> arg = ArgumentCaptor.forClass(RecordId.class);

        verify(rawRepo).removeLinks(arg.capture());
        assertThat(arg.getValue(), equalTo(new RecordId(recordId, agencyId)));
    }
}
