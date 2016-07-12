package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordFactory;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.RawRepoEncoder;
import dk.dbc.updateservice.update.RawRepoRecordMock;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.xml.bind.JAXBException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StoreRecordActionTest {
    private static final String BOOK_RECORD_RESOURCE = "/dk/dbc/updateservice/actions/book.marc";

    /**
     * Test StoreRecordAction.StoreRecordAction() constructor.
     */
    @Test
    public void testConstructor() throws Exception {
        InputStream is = getClass().getResourceAsStream(BOOK_RECORD_RESOURCE);
        MarcRecord record = MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));

        RawRepo rawRepo = mock(RawRepo.class);
        StoreRecordAction instance = new StoreRecordAction(rawRepo, record);

        assertThat(instance, notNullValue());
    }

    /**
     * Test StoreRecordAction.performAction() for store a record in RawRepo.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A RawRepo and a record.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Store the record in rawrepo.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * The record is stored in rawrepo and a ServiceResult with status OK
     * is returned.
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_StoreRecordOk() throws Exception {
        InputStream is = getClass().getResourceAsStream(BOOK_RECORD_RESOURCE);
        MarcRecord record = MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));

        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.recordId();
        Integer agencyId = reader.agencyIdAsInteger();

        RawRepo rawRepo = mock(RawRepo.class);
        StoreRecordAction instance = new StoreRecordAction(rawRepo, record);
        instance.setMimetype(MarcXChangeMimeType.MARCXCHANGE);

        when(rawRepo.fetchRecord(eq(recordId), eq(agencyId))).thenReturn(new RawRepoRecordMock(recordId, agencyId));

        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        ArgumentCaptor<Record> recordArgument = ArgumentCaptor.forClass(Record.class);

        verify(rawRepo).saveRecord(recordArgument.capture());
        assertThat(recordArgument.getValue().getId(), equalTo(new RecordId(recordId, agencyId)));
        assertThat(recordArgument.getValue().getMimeType(), equalTo(instance.getMimetype()));
        assertThat(recordArgument.getValue().isDeleted(), equalTo(instance.deletionMarkToStore()));
        assertThat(recordArgument.getValue().getContent(), equalTo(new RawRepo().encodeRecord(instance.recordToStore())));
    }

    /**
     * Test StoreRecordAction.performAction() to store a record in RawRepo.
     * <p>
     * This test checks the behaviour if the RawRepoEncoder throws a
     * UnsupportedEncodingException exception
     * </p>
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * An empty rawrepo.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Store a record in rawrepo.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * The rawrepo is unchanged and a ServiceResult with
     * status FAILED_UPDATE_INTERNAL_ERROR is returned.
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_UnsupportedEncoding() throws Exception {
        InputStream is = getClass().getResourceAsStream(BOOK_RECORD_RESOURCE);
        MarcRecord record = MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));

        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.recordId();
        Integer agencyId = reader.agencyIdAsInteger();

        RawRepo rawRepo = mock(RawRepo.class);
        RawRepoEncoder encoder = mock(RawRepoEncoder.class);
        StoreRecordAction instance = new StoreRecordAction(rawRepo, record);
        instance.setEncoder(encoder);
        instance.setMimetype(MarcXChangeMimeType.MARCXCHANGE);

        when(rawRepo.fetchRecord(eq(recordId), eq(agencyId))).thenReturn(new RawRepoRecordMock(recordId, agencyId));
        when(encoder.encodeRecord(eq(record))).thenThrow(new UnsupportedEncodingException("error"));

        assertThat(instance.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, "error")));

        verify(rawRepo, never()).saveRecord(any(Record.class));
    }

    /**
     * Test StoreRecordAction.performAction() to store a record in RawRepo.
     * <p>
     * This test checks the behaviour if the RawRepoEncoder throws a
     * JAXBException exception
     * </p>
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * An empty rawrepo.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Store a record in rawrepo.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * The rawrepo is unchanged and a ServiceResult with
     * status FAILED_UPDATE_INTERNAL_ERROR is returned.
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_JAXBException() throws Exception {
        InputStream is = getClass().getResourceAsStream(BOOK_RECORD_RESOURCE);
        MarcRecord record = MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));

        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.recordId();
        Integer agencyId = reader.agencyIdAsInteger();

        RawRepo rawRepo = mock(RawRepo.class);
        RawRepoEncoder encoder = mock(RawRepoEncoder.class);
        StoreRecordAction instance = new StoreRecordAction(rawRepo, record);
        instance.setEncoder(encoder);
        instance.setMimetype(MarcXChangeMimeType.MARCXCHANGE);

        when(rawRepo.fetchRecord(eq(recordId), eq(agencyId))).thenReturn(new RawRepoRecordMock(recordId, agencyId));
        when(encoder.encodeRecord(eq(record))).thenThrow(new JAXBException("error"));

        assertThat(instance.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, "error")));

        verify(rawRepo, never()).saveRecord(any(Record.class));
    }

    /**
     * Test StoreRecordAction.deletionMarkToStore() for store in RawRepo for a record.
     * <p>
     * The result should always be <code>true</code>.
     * </p>
     */
    @Test
    public void testDeletionMarkToStore() throws Exception {
        InputStream is = getClass().getResourceAsStream(BOOK_RECORD_RESOURCE);
        MarcRecord record = MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));

        RawRepo rawRepo = mock(RawRepo.class);
        StoreRecordAction instance = new StoreRecordAction(rawRepo, record);
        instance.setMimetype(MarcXChangeMimeType.MARCXCHANGE);

        assertThat(instance.deletionMarkToStore(), equalTo(false));
    }

    /**
     * Test StoreRecordAction.recordToStore() to store a deleted record.
     */
    @Test
    public void testRecordToStore() throws Exception {
        InputStream is = getClass().getResourceAsStream(BOOK_RECORD_RESOURCE);
        MarcRecord record = MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));

        RawRepo rawRepo = mock(RawRepo.class);
        StoreRecordAction instance = new StoreRecordAction(rawRepo, record);
        instance.setMimetype(MarcXChangeMimeType.MARCXCHANGE);

        assertThat(instance.recordToStore(), equalTo(record));
    }
}
