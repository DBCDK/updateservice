package dk.dbc.updateservice.rest;

import dk.dbc.updateservice.actions.ServiceResult;
import dk.dbc.updateservice.dto.DoubleRecordFrontendDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.service.api.UpdateRecordResult;
import dk.dbc.updateservice.ws.UpdateResponseWriter;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class DoubleRecordCheckServiceTest {

    private final DoubleRecordCheckService service = new DoubleRecordCheckService();

    @Test
    public void parseJavascriptTest_Ok() throws Exception {
        final String json = "{\n" +
                "  \"status\": \"ok\"\n" +
                "}";

        final ServiceResult expectedServiceResult = new ServiceResult();
        expectedServiceResult.setStatus(UpdateStatusEnumDTO.OK);

        final String expectedXML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<updateRecordResult xmlns=\"http://oss.dbc.dk/ns/catalogingUpdate\">" +
                "<updateStatus>ok</updateStatus>" +
                "</updateRecordResult>";

        final ServiceResult actualServiceResult = service.parseJavascript(json);

        assertThat(actualServiceResult, is(expectedServiceResult));

        final UpdateResponseWriter updateResponseWriter;
        final UpdateRecordResult updateRecordResult;
        updateResponseWriter = new UpdateResponseWriter();
        updateResponseWriter.setServiceResult(actualServiceResult);
        updateRecordResult = updateResponseWriter.getResponse();

        assertThat(service.marshal(updateRecordResult), is(expectedXML));
    }

    @Test
    public void parseJavascriptTest_DoubleRecord() throws Exception {
        final String json = "{\n" +
                "  \"status\": \"doublerecord\",\n" +
                "  \"doubleRecordFrontendDTOs\": [\n" +
                "    {\n" +
                "      \"message\": \"Double records for record {111:222}: 333:444\",\n" +
                "      \"pid\": \"333:444\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        final ServiceResult expectedServiceResult = new ServiceResult();
        expectedServiceResult.setStatus(UpdateStatusEnumDTO.FAILED);
        final DoubleRecordFrontendDTO doubleRecordFrontendDTO = new DoubleRecordFrontendDTO();
        doubleRecordFrontendDTO.setMessage("Double records for record {111:222}: 333:444");
        doubleRecordFrontendDTO.setPid("333:444");
        final List<DoubleRecordFrontendDTO> doubleRecordFrontendDTOs = Arrays.asList(doubleRecordFrontendDTO);
        expectedServiceResult.setDoubleRecordFrontendDTOS(doubleRecordFrontendDTOs);

        final String expectedXML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<updateRecordResult xmlns=\"http://oss.dbc.dk/ns/catalogingUpdate\">" +
                "<updateStatus>failed</updateStatus>" +
                "</updateRecordResult>";

        final ServiceResult actualServiceResult = service.parseJavascript(json);

        assertThat(actualServiceResult, is(expectedServiceResult));

        final UpdateResponseWriter updateResponseWriter;
        final UpdateRecordResult updateRecordResult;
        updateResponseWriter = new UpdateResponseWriter();
        updateResponseWriter.setServiceResult(actualServiceResult);
        updateRecordResult = updateResponseWriter.getResponse();

        assertThat(service.marshal(updateRecordResult), is(expectedXML));
    }

}
