/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.rest;

import dk.dbc.updateservice.actions.ServiceResult;
import dk.dbc.updateservice.dto.DoubleRecordFrontendDTO;
import dk.dbc.updateservice.dto.DoubleRecordFrontendStatusDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.service.api.UpdateRecordResult;
import dk.dbc.updateservice.update.UpdateServiceCore;
import dk.dbc.updateservice.update.UpdateStore;
import dk.dbc.updateservice.ws.UpdateResponseWriter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DoubleRecordCheckServiceTest {

    private UpdateServiceCore updateServiceCore;
    private final DoubleRecordCheckService doubleRecordCheckService = new DoubleRecordCheckService();

    @Rule
    public final EnvironmentVariables environmentVariables
            = new EnvironmentVariables();

    @Before
    public void init() {
        environmentVariables.set("JAVASCRIPT_BASEDIR", ".");
        updateServiceCore = new UpdateServiceCore();
        updateServiceCore.updateStore = mock(UpdateStore.class);
    }

    @Test
    public void parseJavascriptTest_Ok() throws Exception {
        final DoubleRecordFrontendStatusDTO doubleRecordFrontendStatusDTO = new DoubleRecordFrontendStatusDTO();
        doubleRecordFrontendStatusDTO.setStatus("ok");

        final ServiceResult expectedServiceResult = new ServiceResult();
        expectedServiceResult.setStatus(UpdateStatusEnumDTO.OK);

        final String expectedXML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<updateRecordResult xmlns=\"http://oss.dbc.dk/ns/catalogingUpdate\">" +
                "<updateStatus>ok</updateStatus>" +
                "</updateRecordResult>";

        final ServiceResult actualServiceResult = updateServiceCore.DoubleRecordFrontendStatusDTOToServiceResult(doubleRecordFrontendStatusDTO);

        assertThat(actualServiceResult, is(expectedServiceResult));

        final UpdateResponseWriter updateResponseWriter;
        final UpdateRecordResult updateRecordResult;
        updateResponseWriter = new UpdateResponseWriter();
        updateResponseWriter.setServiceResult(actualServiceResult);
        updateRecordResult = updateResponseWriter.getResponse();

        assertThat(doubleRecordCheckService.marshal(updateRecordResult), is(expectedXML));
    }

    @Test
    public void parseJavascriptTest_DoubleRecord() throws Exception {
        final DoubleRecordFrontendDTO doubleRecordFrontendDTO = new DoubleRecordFrontendDTO();
        doubleRecordFrontendDTO.setMessage("Double records for record {111:222}: 333:444");
        doubleRecordFrontendDTO.setPid("333:444");

        final DoubleRecordFrontendStatusDTO doubleRecordFrontendStatusDTO = new DoubleRecordFrontendStatusDTO();
        doubleRecordFrontendStatusDTO.setStatus("doublerecord");
        doubleRecordFrontendStatusDTO.setDoubleRecordFrontendDTOs(Collections.singletonList(doubleRecordFrontendDTO));

        when(updateServiceCore.updateStore.getNewDoubleRecordKey()).thenReturn("abc123");

        final ServiceResult expectedServiceResult = new ServiceResult();
        expectedServiceResult.setStatus(UpdateStatusEnumDTO.FAILED);
        final DoubleRecordFrontendDTO expectedDoubleRecordFrontendDTO = new DoubleRecordFrontendDTO();
        expectedDoubleRecordFrontendDTO.setMessage("Double records for record {111:222}: 333:444");
        expectedDoubleRecordFrontendDTO.setPid("333:444");
        final List<DoubleRecordFrontendDTO> doubleRecordFrontendDTOs = Collections.singletonList(expectedDoubleRecordFrontendDTO);
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

        final ServiceResult actualServiceResult = updateServiceCore.DoubleRecordFrontendStatusDTOToServiceResult(doubleRecordFrontendStatusDTO);

        assertThat(actualServiceResult, is(expectedServiceResult));

        final UpdateResponseWriter updateResponseWriter;
        final UpdateRecordResult updateRecordResult;
        updateResponseWriter = new UpdateResponseWriter();
        updateResponseWriter.setServiceResult(actualServiceResult);
        updateRecordResult = updateResponseWriter.getResponse();

        assertThat(doubleRecordCheckService.marshal(updateRecordResult), is(expectedXML));
    }

}
