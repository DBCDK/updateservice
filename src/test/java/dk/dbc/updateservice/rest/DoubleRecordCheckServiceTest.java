/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.rest;

import dk.dbc.updateservice.actions.ServiceResult;
import dk.dbc.updateservice.dto.DoubleRecordFrontendDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.service.api.UpdateRecordResult;
import dk.dbc.updateservice.update.UpdateStore;
import dk.dbc.updateservice.ws.UpdateResponseWriter;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DoubleRecordCheckServiceTest {

    private final DoubleRecordCheckService service = new DoubleRecordCheckService();

    @Before
    public void init() {
        service.updateStore = mock(UpdateStore.class);
    }

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

        when(service.updateStore.getNewDoubleRecordKey()).thenReturn("abc123");

        final ServiceResult expectedServiceResult = new ServiceResult();
        expectedServiceResult.setStatus(UpdateStatusEnumDTO.FAILED);
        final DoubleRecordFrontendDTO doubleRecordFrontendDTO = new DoubleRecordFrontendDTO();
        doubleRecordFrontendDTO.setMessage("Double records for record {111:222}: 333:444");
        doubleRecordFrontendDTO.setPid("333:444");
        final List<DoubleRecordFrontendDTO> doubleRecordFrontendDTOs = Arrays.asList(doubleRecordFrontendDTO);
        expectedServiceResult.setDoubleRecordFrontendDTOS(doubleRecordFrontendDTOs);
        expectedServiceResult.setDoubleRecordKey("abc123");

        final String expectedXML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<updateRecordResult xmlns=\"http://oss.dbc.dk/ns/catalogingUpdate\">" +
                    "<updateStatus>failed</updateStatus>" +
                    "<doubleRecordKey>abc123</doubleRecordKey>" +
                    "<doubleRecordEntries>" +
                        "<doubleRecordEntry>" +
                            "<pid>333:444</pid>" +
                            "<message>Double records for record {111:222}: 333:444</message>" +
                        "</doubleRecordEntry>" +
                    "</doubleRecordEntries>" +
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
