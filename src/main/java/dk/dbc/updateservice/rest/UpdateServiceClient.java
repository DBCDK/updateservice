package dk.dbc.updateservice.rest;

import dk.dbc.httpclient.FailSafeHttpClient;
import dk.dbc.httpclient.HttpClient;
import dk.dbc.httpclient.HttpPost;
import dk.dbc.httpclient.PathBuilder;
import dk.dbc.jsonb.JSONBContext;
import dk.dbc.updateservice.dto.AuthenticationDTO;
import dk.dbc.updateservice.dto.BibliographicRecordDTO;
import dk.dbc.updateservice.dto.MessageEntryDTO;
import dk.dbc.updateservice.dto.OptionEnumDTO;
import dk.dbc.updateservice.dto.OptionsDTO;
import dk.dbc.updateservice.dto.RecordDataDTO;
import dk.dbc.updateservice.dto.TypeEnumDTO;
import dk.dbc.updateservice.dto.UpdateRecordResponseDTO;
import dk.dbc.updateservice.dto.UpdateServiceRequestDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.UpdateException;
import net.jodah.failsafe.RetryPolicy;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

public class UpdateServiceClient {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(UpdateServiceClient.class);
    private static final String BASE_URL = "http://localhost:8080/UpdateService/rest";
    private static final String PATH_UPDATESERVICE = "/api/v1/updateservice";
    private static final RetryPolicy<Response> RETRY_POLICY = new RetryPolicy<Response>()
            .handle(ProcessingException.class)
            .handleResultIf(response -> response.getStatus() == 404)
            .withDelay(Duration.ofSeconds(10))
            .withMaxRetries(1);
    private static boolean isReady;
    private static final JSONBContext jsonbContext = new JSONBContext();
    private static final String EXPECTED_MESSAGE = "Authentication error";

    private UpdateServiceClient() {
        throw new IllegalStateException("Static class");
    }

    public static synchronized boolean isReady() {
        try {
            // This function will be called constantly by we only need to call updateservice once. In order to limit the
            // amount of webservice requests we use a static variable to prevent more calls after the first one
            if (!isReady) {
                final UpdateRecordResponseDTO updateRecordResponseDTO = callUpdate();

                isReady = assertUpdateRecordResponse(updateRecordResponseDTO);
            }

            return isReady;
        } catch (Exception e) {
            LOGGER.error("Caught exception during UpdateServiceClient", e);
            return false;
        }
    }

    private static UpdateRecordResponseDTO callUpdate() {
        final AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId("725900");
        authenticationDTO.setPassword("password");
        authenticationDTO.setUserId("user");

        final UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        updateServiceRequestDTO.setSchemaName("boghoved");
        updateServiceRequestDTO.setTrackingId("k8s-warm-up");

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

        final OptionsDTO optionsDTO = new OptionsDTO();
        optionsDTO.setOption(Collections.singletonList(OptionEnumDTO.VALIDATE_ONLY));
        updateServiceRequestDTO.setOptionsDTO(optionsDTO);

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

    private static <T> T readResponseEntity(Response response, Class<T> type)
            throws UpdateException {
        final T entity = response.readEntity(type);
        if (entity == null) {
            throw new UpdateException(
                    String.format("Update returned with null-valued %s entity",
                            type.getName()));
        }
        return entity;
    }

    private static void assertResponseStatus(Response response)
            throws UpdateException {
        final Response.Status actualStatus =
                Response.Status.fromStatusCode(response.getStatus());
        if (actualStatus != Response.Status.OK) {
            throw new UpdateException(
                    String.format("Update returned with '%s' status code: %s",
                            actualStatus,
                            actualStatus.getStatusCode()));
        }
    }

    /*
        We are not able to call update service with a proper request due to the username and password validation.

        But the purpose of this call is to initialize the bean dependency hierarchy and this it achieved even though
        the request doesn't return OK.
     */
    private static boolean assertUpdateRecordResponse(UpdateRecordResponseDTO dto) {
        if (dto.getUpdateStatusEnumDTO() == UpdateStatusEnumDTO.FAILED && dto.getMessageEntryDTOS().size() == 1) {
            final MessageEntryDTO messageEntryDTO = dto.getMessageEntryDTOS().get(0);
            return messageEntryDTO.getMessage().equals(EXPECTED_MESSAGE) && messageEntryDTO.getType() == TypeEnumDTO.ERROR;
        }

        return false;
    }

}
