/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.rest;

import dk.dbc.httpclient.FailSafeHttpClient;
import dk.dbc.httpclient.HttpClient;
import dk.dbc.httpclient.HttpPost;
import dk.dbc.httpclient.PathBuilder;
import dk.dbc.jsonb.JSONBContext;
import dk.dbc.updateservice.dto.AuthenticationDTO;
import dk.dbc.updateservice.dto.BibliographicRecordDTO;
import dk.dbc.updateservice.dto.RecordDataDTO;
import dk.dbc.updateservice.dto.UpdateRecordResponseDTO;
import dk.dbc.updateservice.dto.UpdateServiceRequestDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import net.jodah.failsafe.RetryPolicy;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class UpdateServiceClient {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(UpdateServiceClient.class);
    private static final String BASE_URL = "http://localhost:8080/UpdateService/rest";
    private static final String PATH_UPDATESERVICE = "/api/v1/updateservice";
    private static final RetryPolicy RETRY_POLICY = new RetryPolicy()
            .retryOn(Collections.singletonList(ProcessingException.class))
            .retryIf((Response response) -> response.getStatus() == 404)
            .withDelay(10, TimeUnit.SECONDS)
            .withMaxRetries(1);
    private static boolean isReady;
    private static final JSONBContext jsonbContext = new JSONBContext();

    public boolean isReady() {
        try {
            // This function will be called constantly by we only need to call updateservice once. In order to limit the
            // amount of webservice requests we use a static variable to prevent more calls after the first one
            if (!isReady) {
                final UpdateRecordResponseDTO updateRecordResponseDTO = callUpdate();

                isReady = updateRecordResponseDTO.getUpdateStatusEnumDTO() == UpdateStatusEnumDTO.OK;
            }

            return isReady;
        } catch (Exception e) {
            LOGGER.error("Caught exception during UpdateServiceClient", e);
            return false;
        }
    }

    private UpdateRecordResponseDTO callUpdate() {
        final UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();

        final AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId("010100");
        authenticationDTO.setPassword("");
        authenticationDTO.setUserId("");

        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        updateServiceRequestDTO.setSchemaName("dbcautoritet");
        updateServiceRequestDTO.setTrackingId("update-warmup");

        final BibliographicRecordDTO bibliographicRecordDTO = new BibliographicRecordDTO();
        bibliographicRecordDTO.setRecordSchema("info:lc/xmlns/marcxchange-v1");
        bibliographicRecordDTO.setRecordPacking("xml");

        final RecordDataDTO recordDataDTO = new RecordDataDTO();
        final List<Object> content = Collections.singletonList("<record xmlns=\"info:lc/xmlns/marcxchange-v1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"info:lc/xmlns/marcxchange-v1 http://www.loc.gov/standards/iso25577/marcxchange-1-1.xsd\">" +
                "<leader>00000n    2200000   4500</leader>" +
                "<datafield ind1=\"0\" ind2=\"0\" tag=\"001\"><subfield code=\"a\">44304937</subfield><subfield code=\"b\">870970</subfield><subfield code=\"c\">20170607113521</subfield><subfield code=\"d\">20090618</subfield><subfield code=\"f\">a</subfield></datafield>" +
                "<datafield ind1=\"0\" ind2=\"0\" tag=\"002\"><subfield code=\"b\">725900</subfield><subfield code=\"c\">92686132</subfield><subfield code=\"x\">71010092686132</subfield></datafield>" +
                "<datafield ind1=\"0\" ind2=\"0\" tag=\"004\"><subfield code=\"r\">c</subfield><subfield code=\"a\">h</subfield></datafield>" +
                "<datafield ind1=\"0\" ind2=\"0\" tag=\"008\"><subfield code=\"b\">us</subfield><subfield code=\"d\">1</subfield><subfield code=\"l\">eng</subfield><subfield code=\"v\">0</subfield></datafield>" +
                "<datafield ind1=\"0\" ind2=\"0\" tag=\"009\"><subfield code=\"a\">a</subfield><subfield code=\"g\">xx</subfield></datafield>" +
                "<datafield ind1=\"0\" ind2=\"0\" tag=\"245\"><subfield code=\"a\">Bleach</subfield><subfield code=\"e\">story and art by Tite Kubo</subfield><subfield code=\"e\">English adaptation Lance Caselman</subfield><subfield code=\"f\">translation Joe Yamazaki</subfield></datafield>" +
                "<datafield ind1=\"0\" ind2=\"0\" tag=\"260\"><subfield code=\"a\">San Francisco, Calif.</subfield><subfield code=\"b\">VIZ Media</subfield></datafield>" +
                "<datafield ind1=\"0\" ind2=\"0\" tag=\"300\"><subfield code=\"a\">bind</subfield><subfield code=\"b\">alle ill.</subfield></datafield>" +
                "<datafield ind1=\"0\" ind2=\"0\" tag=\"652\"><subfield code=\"m\">83</subfield></datafield>" +
                "<datafield ind1=\"0\" ind2=\"0\" tag=\"996\"><subfield code=\"a\">725900</subfield></datafield>" +
                "</record>");

        recordDataDTO.setContent(content);
        bibliographicRecordDTO.setRecordDataDTO(recordDataDTO);
        updateServiceRequestDTO.setBibliographicRecordDTO(bibliographicRecordDTO);

        final Client client = HttpClient.newClient(new ClientConfig().register(new JacksonFeature()));
        final FailSafeHttpClient failSafeHttpClient = FailSafeHttpClient.create(client, RETRY_POLICY);
        final PathBuilder path = new PathBuilder(PATH_UPDATESERVICE);
        try {
            final HttpPost post = new HttpPost(failSafeHttpClient)
                    .withBaseUrl(BASE_URL)
                    .withData(jsonbContext.marshall(updateServiceRequestDTO), "application/json")
                    .withHeader("Accept", "application/json")
                    .withPathElements(path.build());

            final Response response = post.execute();
            assertResponseStatus(response);
            return readResponseEntity(response, UpdateRecordResponseDTO.class);
        } catch (Exception e) {
            final UpdateRecordResponseDTO updateRecordResponseDTO = new UpdateRecordResponseDTO();
            updateRecordResponseDTO.setUpdateStatusEnumDTO(UpdateStatusEnumDTO.FAILED);

            return updateRecordResponseDTO;
        }
    }

    private <T> T readResponseEntity(Response response, Class<T> type)
            throws Exception {
        final T entity = response.readEntity(type);
        if (entity == null) {
            throw new Exception(
                    String.format("Update returned with null-valued %s entity",
                            type.getName()));
        }
        return entity;
    }

    private void assertResponseStatus(Response response)
            throws Exception {
        final Response.Status actualStatus =
                Response.Status.fromStatusCode(response.getStatus());
        if (actualStatus != Response.Status.OK) {
            throw new Exception(
                    String.format("Update returned with '%s' status code: %s",
                            actualStatus,
                            actualStatus.getStatusCode()));
        }
    }

}
