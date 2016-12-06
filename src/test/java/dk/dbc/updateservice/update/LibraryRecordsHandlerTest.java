package dk.dbc.updateservice.update;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.records.MarcRecordWriter;
import dk.dbc.iscrum.utils.json.Json;
import dk.dbc.updateservice.actions.AssertActionsUtil;
import dk.dbc.updateservice.actions.GlobalActionState;
import dk.dbc.updateservice.actions.ServiceResult;
import dk.dbc.updateservice.actions.UpdateTestUtils;
import dk.dbc.updateservice.dto.UpdateStatusEnumDto;
import dk.dbc.updateservice.javascript.Scripter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.when;

public class LibraryRecordsHandlerTest {
    private GlobalActionState state;

    @Before
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();

        MockitoAnnotations.initMocks(this);
    }

    @Mock
    private Scripter scripter;

    private class MockLibraryRecordsHandler extends LibraryRecordsHandler {
        MockLibraryRecordsHandler() {
            super(scripter);
        }
    }

    @Test
    public void testCreateEnrichmentRecords_True() throws Exception {
        MarcRecord currentCommonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecord updatingCommonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);

        String currentCommonRecordArgument = Json.encode(currentCommonRecord);
        String updatingCommonRecordArgument = Json.encode(updatingCommonRecord);
        String scripterResult = Json.encode(ServiceResult.newOkResult());
        when(scripter.callMethod(eq(LibraryRecordsHandler.CREATE_ENRICHMENT_RECORDS_FUNCTION_NAME),
                isNull(Properties.class),
                eq(currentCommonRecordArgument),
                eq(updatingCommonRecordArgument))).thenReturn(scripterResult);

        LibraryRecordsHandler instance = new MockLibraryRecordsHandler();
        assertThat(instance.shouldCreateEnrichmentRecords(null, currentCommonRecord, updatingCommonRecord), equalTo(ServiceResult.newOkResult()));
    }

    @Test
    public void testCreateEnrichmentRecords_False() throws Exception {
        MarcRecord currentCommonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecord updatingCommonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);

        String currentCommonRecordArgument = Json.encode(currentCommonRecord);
        String updatingCommonRecordArgument = Json.encode(updatingCommonRecord);
        ServiceResult scripterReason = ServiceResult.newErrorResult(UpdateStatusEnumDto.FAILED, "reason", state);
        String scripterResult = Json.encode(scripterReason);
        when(scripter.callMethod(eq(LibraryRecordsHandler.CREATE_ENRICHMENT_RECORDS_FUNCTION_NAME),
                isNull(Properties.class),
                eq(currentCommonRecordArgument),
                eq(updatingCommonRecordArgument))).thenReturn(scripterResult);

        LibraryRecordsHandler instance = new MockLibraryRecordsHandler();
        assertThat(instance.shouldCreateEnrichmentRecords(null, currentCommonRecord, updatingCommonRecord), equalTo(scripterReason));
    }

    @Test
    public void testsplitCompleteBasisRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecord expectedCommonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecord expectedDBCRecord = new MarcRecord();

        MarcRecordReader reader = new MarcRecordReader(record);
        MarcRecordWriter writer = new MarcRecordWriter(record);

        writer.addOrReplaceSubfield("aaa", "a", "b");
        writer.addOrReplaceSubfield("bbb", "a", "b");

        expectedDBCRecord.getFields().add(reader.getField("001"));
        expectedDBCRecord.getFields().add(reader.getField("004"));
        expectedDBCRecord.getFields().add(reader.getField("aaa"));
        expectedDBCRecord.getFields().add(reader.getField("bbb"));
        MarcRecordWriter expectedDBCRecordWriter = new MarcRecordWriter(expectedDBCRecord);
        expectedDBCRecordWriter.addOrReplaceSubfield("001", "b", RawRepo.COMMON_LIBRARY.toString());

        MarcRecordWriter expectedCommonRecordWriter = new MarcRecordWriter(expectedCommonRecord);
        expectedCommonRecordWriter.addOrReplaceSubfield("001", "b", RawRepo.RAWREPO_COMMON_LIBRARY.toString());

        List<MarcRecord> expectedList = Arrays.asList(expectedCommonRecord, expectedDBCRecord);

        LibraryRecordsHandler instance = new MockLibraryRecordsHandler();
        assertThat(instance.splitRecordDataIO(record), equalTo(expectedList));
    }
}
