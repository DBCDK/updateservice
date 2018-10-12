/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.json.JsonMapper;
import dk.dbc.updateservice.update.OpenAgencyService;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.RawRepoRecordMock;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Properties;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StoreRecordActionTest {
    private GlobalActionState state;
    private Properties settings;
    private static final String ENTRY_POINT = "sortRecord";
    OpenAgencyService.LibraryGroup libraryGroup = OpenAgencyService.LibraryGroup.DBC;

    @Before
    public void before() throws IOException {
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
    public void testPerformAction_StoreRecordOk() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.getRecordId();
        int agencyId = reader.getAgencyIdAsInt();
        state.setLibraryGroup(libraryGroup);
        StoreRecordAction storeRecordAction = new StoreRecordAction(state, settings, record);
        storeRecordAction.setMimetype(MarcXChangeMimeType.MARCXCHANGE);

        when(state.getScripter().callMethod(ENTRY_POINT, state.getSchemaName(), JsonMapper.encode(record), settings)).thenReturn(JsonMapper.encode(record));
        when(state.getRawRepo().fetchRecord(eq(recordId), eq(agencyId))).thenReturn(new RawRepoRecordMock(recordId, agencyId));

        assertThat(storeRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        ArgumentCaptor<Record> recordArgument = ArgumentCaptor.forClass(Record.class);
        verify(state.getRawRepo()).saveRecord(recordArgument.capture());
        assertThat(recordArgument.getValue().getId(), equalTo(new RecordId(recordId, agencyId)));
        assertThat(recordArgument.getValue().getMimeType(), equalTo(storeRecordAction.getMimetype()));
        assertThat(recordArgument.getValue().isDeleted(), equalTo(storeRecordAction.deletionMarkToStore()));
        assertThat(recordArgument.getValue().getContent(), equalTo(new RawRepo().encodeRecord(storeRecordAction.recordToStore())));
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
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.getRecordId();
        int agencyId = reader.getAgencyIdAsInt();
        state.setLibraryGroup(libraryGroup);
        StoreRecordAction storeRecordAction = new StoreRecordAction(state, settings, record);
        storeRecordAction.setMimetype(MarcXChangeMimeType.MARCXCHANGE);
        storeRecordAction.encoder = mock(StoreRecordAction.Encoder.class);

        when(state.getScripter().callMethod(ENTRY_POINT, state.getSchemaName(), JsonMapper.encode(state.readRecord()), settings)).thenReturn(JsonMapper.encode(record));
        when(state.getRawRepo().fetchRecord(eq(recordId), eq(agencyId))).thenReturn(new RawRepoRecordMock(recordId, agencyId));
        when(storeRecordAction.encoder.encodeRecord(eq(record))).thenThrow(new UnsupportedEncodingException("error"));

        assertThat(storeRecordAction.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, "error", state)));
        verify(state.getRawRepo(), never()).saveRecord(any(Record.class));
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
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.getRecordId();
        int agencyId = reader.getAgencyIdAsInt();
        StoreRecordAction.Encoder encoder = mock(StoreRecordAction.Encoder.class);
        state.setLibraryGroup(libraryGroup);
        StoreRecordAction storeRecordAction = new StoreRecordAction(state, settings, record);
        storeRecordAction.setMimetype(MarcXChangeMimeType.MARCXCHANGE);
        storeRecordAction.encoder = encoder;

        when(state.getScripter().callMethod(ENTRY_POINT, state.getSchemaName(), JsonMapper.encode(state.readRecord()), settings)).thenReturn(JsonMapper.encode(record));
        when(state.getRawRepo().fetchRecord(eq(recordId), eq(agencyId))).thenReturn(new RawRepoRecordMock(recordId, agencyId));
        when(encoder.encodeRecord(eq(record))).thenThrow(new JAXBException("error"));

        ServiceResult serviceResult = storeRecordAction.performAction();
        assertThat(serviceResult, equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, "error", state)));
        verify(state.getRawRepo(), never()).saveRecord(any(Record.class));
    }

    /**
     * Test StoreRecordAction.deletionMarkToStore() for store in RawRepo for a record.
     * <p>
     * The result should always be <code>true</code>.
     * </p>
     */
    @Test
    public void testDeletionMarkToStore() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        StoreRecordAction storeRecordAction = new StoreRecordAction(state, settings, record);
        storeRecordAction.setMimetype(MarcXChangeMimeType.MARCXCHANGE);
        assertThat(storeRecordAction.deletionMarkToStore(), equalTo(false));
    }

    /**
     * Test StoreRecordAction.recordToStore() to store a deleted record.
     */
    @Test
    public void testRecordToStore() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        StoreRecordAction instance = new StoreRecordAction(state, settings, record);
        instance.setMimetype(MarcXChangeMimeType.MARCXCHANGE);
        assertThat(instance.recordToStore(), equalTo(record));
    }

    private class StoreRecordActionMock extends StoreRecordAction {
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
    public void testUpdateModifiedDateDBC() throws Exception {
        // 001 00 *a 20611529 *b 870970 *c 19971020 *d 19940516 *f a
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.VOLUME_RECORD_RESOURCE);
        final StoreRecordActionMock instance = new StoreRecordActionMock(state, settings, record);

        final String modified = "20181008151342";

        instance.modified = modified;
        instance.updateModifiedDate(record);

        final MarcRecordReader modifiedReader = new MarcRecordReader(record);

        assertThat(modifiedReader.getValue("001", "c"), equalTo(modified));
    }

    @Test
    public void testUpdateModifiedDateNotDBC() throws Exception {
        // 001 00 *a 20611529 *b 700400 *c 19971020 *d 19940516 *f a
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        final StoreRecordActionMock instance = new StoreRecordActionMock(state, settings, record);

        final String modified = "20181008151342";

        instance.modified = modified;
        instance.updateModifiedDate(record);

        final MarcRecordReader modifiedReader = new MarcRecordReader(record);

        assertThat(modifiedReader.getValue("001", "c"), equalTo("19971020"));
    }
}
