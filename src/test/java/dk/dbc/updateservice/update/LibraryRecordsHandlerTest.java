package dk.dbc.updateservice.update;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.utils.json.Json;
import dk.dbc.updateservice.actions.AssertActionsUtil;
import dk.dbc.updateservice.actions.ServiceResult;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LibraryRecordsHandlerTest {
    private Scripter scripter;

    @Before
    public void setup() {
        this.scripter = mock(Scripter.class);
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

        LibraryRecordsHandler instance = new LibraryRecordsHandler(scripter);
        assertThat(instance.shouldCreateEnrichmentRecords(null, currentCommonRecord, updatingCommonRecord), equalTo(ServiceResult.newOkResult()));
    }

    @Test
    public void testCreateEnrichmentRecords_False() throws Exception {
        MarcRecord currentCommonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecord updatingCommonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);

        String currentCommonRecordArgument = Json.encode(currentCommonRecord);
        String updatingCommonRecordArgument = Json.encode(updatingCommonRecord);
        ServiceResult scripterReason = ServiceResult.newErrorResult(UpdateStatusEnum.FAILED, "reason");
        String scripterResult = Json.encode(scripterReason);
        when(scripter.callMethod(eq(LibraryRecordsHandler.CREATE_ENRICHMENT_RECORDS_FUNCTION_NAME),
                isNull(Properties.class),
                eq(currentCommonRecordArgument),
                eq(updatingCommonRecordArgument))).thenReturn(scripterResult);

        LibraryRecordsHandler instance = new LibraryRecordsHandler(scripter);
        assertThat(instance.shouldCreateEnrichmentRecords(null, currentCommonRecord, updatingCommonRecord), equalTo(scripterReason));
    }

    @Test
    public void testServiceResultAsJson() throws Exception {
        ServiceResult scripterReason = ServiceResult.newOkResult();
        assertThat(Json.encode(scripterReason), equalTo("{\"status\":\"OK\",\"entries\":[],\"doubleRecordKey\":null,\"serviceErrorList\":null}"));
        scripterReason = ServiceResult.newErrorResult(UpdateStatusEnum.FAILED, "reason");
        assertThat(Json.encode(scripterReason), equalTo("{\"status\":\"FAILED\",\"entries\":[{\"type\":\"ERROR\",\"code\":null,\"params\":{\"param\":[{\"value\":\"reason\",\"key\":\"message\"}]}}],\"doubleRecordKey\":null,\"serviceErrorList\":[{\"type\":\"ERROR\",\"code\":null,\"params\":{\"param\":[{\"value\":\"reason\",\"key\":\"message\"}]}}]}"));
    }
}
