package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.LibraryGroup;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.RawRepoRecordMock;
import dk.dbc.updateservice.update.UpdateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoreRecordActionTest {
    private GlobalActionState state;
    private Properties settings;
    LibraryGroup libraryGroup = LibraryGroup.DBC;

    @BeforeEach
    public void before() throws IOException, UpdateException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
        settings = new UpdateTestUtils().getSettings();
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
    void testPerformAction_StoreRecordOk() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.getRecordId();
        int agencyId = reader.getAgencyIdAsInt();
        state.setLibraryGroup(libraryGroup);
        StoreRecordAction storeRecordAction = new StoreRecordAction(state, settings, record);
        storeRecordAction.setMimetype(MarcXChangeMimeType.MARCXCHANGE);

        when(state.getRawRepo().fetchRecord(eq(recordId), eq(agencyId))).thenReturn(new RawRepoRecordMock(recordId, agencyId));

        assertThat(storeRecordAction.performAction(), is(ServiceResult.newOkResult()));

        ArgumentCaptor<Record> recordArgument = ArgumentCaptor.forClass(Record.class);
        verify(state.getRawRepo()).saveRecord(recordArgument.capture());
        assertThat(recordArgument.getValue().getId(), is(new RecordId(recordId, agencyId)));
        assertThat(recordArgument.getValue().getMimeType(), is(storeRecordAction.getMimetype()));
        assertThat(recordArgument.getValue().isDeleted(), is(storeRecordAction.deletionMarkToStore()));
        assertThat(recordArgument.getValue().getContent(), is(new RawRepo().encodeRecord(storeRecordAction.recordToStore())));
    }

    @Test
    void testPerformAction_MatVurd() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.MATVURD_1);
        final String recordId = "12345678";
        final int agencyId = 870976;
        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.removeFields(Arrays.asList("r01", "r02")); // These fields will only be present on the enrichment record

        StoreRecordAction storeRecordAction = StoreRecordAction.newStoreMarcXChangeAction(state, settings, record);

        assertThat(storeRecordAction.getMimetype(), is(MarcXChangeMimeType.MATVURD));

        when(state.getRawRepo().fetchRecord(eq(recordId), eq(agencyId))).thenReturn(new RawRepoRecordMock(recordId, agencyId));

        assertThat(storeRecordAction.performAction(), is(ServiceResult.newOkResult()));

        ArgumentCaptor<Record> recordArgument = ArgumentCaptor.forClass(Record.class);
        verify(state.getRawRepo()).saveRecord(recordArgument.capture());
        assertThat(recordArgument.getValue().getId(), is(new RecordId(recordId, agencyId)));
        assertThat(recordArgument.getValue().getMimeType(), is(storeRecordAction.getMimetype()));
        assertThat(recordArgument.getValue().isDeleted(), is(storeRecordAction.deletionMarkToStore()));
        assertThat(recordArgument.getValue().getContent(), is(new RawRepo().encodeRecord(storeRecordAction.recordToStore())));
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
    void testPerformAction_JAXBException() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.getRecordId();
        int agencyId = reader.getAgencyIdAsInt();
        StoreRecordAction.Encoder encoder = mock(StoreRecordAction.Encoder.class);
        state.setLibraryGroup(libraryGroup);
        StoreRecordAction storeRecordAction = new StoreRecordAction(state, settings, record);
        storeRecordAction.setMimetype(MarcXChangeMimeType.MARCXCHANGE);
        storeRecordAction.encoder = encoder;

        when(state.getRawRepo().fetchRecord(eq(recordId), eq(agencyId))).thenReturn(new RawRepoRecordMock(recordId, agencyId));
        when(encoder.encodeRecord(eq(record))).thenThrow(new JAXBException("error"));

        ServiceResult serviceResult = storeRecordAction.performAction();
        assertThat(serviceResult, is(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, "error")));
        verify(state.getRawRepo(), never()).saveRecord(any(Record.class));
    }

    /**
     * Test StoreRecordAction.deletionMarkToStore() for store in RawRepo for a record.
     * <p>
     * The result should always be <code>true</code>.
     * </p>
     */
    @Test
    void testDeletionMarkToStore() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        StoreRecordAction storeRecordAction = new StoreRecordAction(state, settings, record);
        storeRecordAction.setMimetype(MarcXChangeMimeType.MARCXCHANGE);
        assertThat(storeRecordAction.deletionMarkToStore(), is(false));
    }

    /**
     * Test StoreRecordAction.recordToStore() to store a deleted record.
     */
    @Test
    void testRecordToStore() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        StoreRecordAction instance = new StoreRecordAction(state, settings, record);
        instance.setMimetype(MarcXChangeMimeType.MARCXCHANGE);
        assertThat(instance.recordToStore(), is(record));
    }

    private static class StoreRecordActionMock extends StoreRecordAction {
        public StoreRecordActionMock(GlobalActionState globalActionState, Properties properties, MarcRecord record) {
            super(globalActionState, properties, record);
        }

        public String modified;

        @Override
        protected String getModifiedDate() {
            return modified;
        }
    }

    @Test
    void testUpdateModifiedDateDBC() throws Exception {
        // 001 00 *a 20611529 *b 870970 *c 19971020 *d 19940516 *f a
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.VOLUME_RECORD_RESOURCE);
        final StoreRecordActionMock instance = new StoreRecordActionMock(state, settings, record);

        final String modified = "20181008151342";

        instance.modified = modified;
        instance.updateModifiedDate(record);

        final MarcRecordReader modifiedReader = new MarcRecordReader(record);

        assertThat(modifiedReader.getValue("001", 'c'), is(modified));
    }

    @Test
    void testUpdateModifiedDateNotDBC() throws Exception {
        // 001 00 *a 20611529 *b 700400 *c 19971020 *d 19940516 *f a
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        final StoreRecordActionMock instance = new StoreRecordActionMock(state, settings, record);

        instance.modified = "20181008151342";
        instance.updateModifiedDate(record);

        final MarcRecordReader modifiedReader = new MarcRecordReader(record);

        assertThat(modifiedReader.getValue("001", 'c'), is("19971020"));
    }
}
