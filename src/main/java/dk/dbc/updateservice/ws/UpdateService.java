/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.ws;

import dk.dbc.openagency.client.OpenAgencyException;
import dk.dbc.updateservice.actions.GlobalActionState;
import dk.dbc.updateservice.dto.SchemaDTO;
import dk.dbc.updateservice.dto.SchemasRequestDTO;
import dk.dbc.updateservice.dto.SchemasResponseDTO;
import dk.dbc.updateservice.dto.UpdateRecordResponseDTO;
import dk.dbc.updateservice.dto.UpdateServiceRequestDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.json.JsonMapper;
import dk.dbc.updateservice.update.UpdateServiceCore;
import dk.dbc.updateservice.service.api.GetSchemasRequest;
import dk.dbc.updateservice.service.api.GetSchemasResult;
import dk.dbc.updateservice.service.api.UpdateRecordRequest;
import dk.dbc.updateservice.service.api.UpdateRecordResult;
import dk.dbc.updateservice.update.OpenAgencyService;
import dk.dbc.updateservice.validate.Validator;
import dk.dbc.updateservice.ws.marshall.GetSchemasRequestMarshaller;
import dk.dbc.updateservice.ws.marshall.GetSchemasResultMarshaller;
import dk.dbc.updateservice.ws.marshall.UpdateRecordRequestMarshaller;
import dk.dbc.updateservice.ws.marshall.UpdateRecordResultMarshaller;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.MDC;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import java.util.List;

/**
 * UpdateService web service.
 * <p>
 * Validates a record by using an JavaScript engine. This EJB also has
 * the responsibility to return a list of valid validation schemes.
 */
@Stateless
public class UpdateService {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(UpdateService.class);
    private static final String GET_SCHEMAS_WATCHTAG = "request.getSchemas";
    public static final String MARSHALLING_ERROR_MSG = "Got an error while marshalling input request, using reflection instead.";
    public static final String MDC_TRACKING_ID_LOG_CONTEXT = "trackingId";
    public static final String UPDATE_SERVICE_VERSION = "2.0";

    @EJB
    private OpenAgencyService openAgencyService;

    @EJB
    private Validator validator;

    @EJB
    private UpdateServiceCore updateServiceCore;

    /**
     * Update or validate a bibliographic record to the rawrepo.
     * Request is in external from ws schema generated format
     * <p>
     * This operation has 2 uses:
     * <ol>
     * <li>Validation of the record only.</li>
     * <li>Validation and update of the record</li>
     * </ol>
     * The actual operation is specified in the request by Options object
     *
     * @param updateRecordRequest The request.
     * @return Returns an instance of UpdateRecordResult with the status of the
     * status and result of the update.
     * @throws EJBException in the case of an error.
     */
    public UpdateRecordResult updateRecord(UpdateRecordRequest updateRecordRequest, GlobalActionState globalActionState) {
        final UpdateRequestReader updateRequestReader = new UpdateRequestReader(updateRecordRequest);
        final UpdateServiceRequestDTO updateServiceRequestDTO = updateRequestReader.getUpdateServiceRequestDTO();
        final UpdateRecordRequestMarshaller updateRecordRequestMarshaller = new UpdateRecordRequestMarshaller(updateRecordRequest);
        final UpdateResponseWriter updateResponseWriter = new UpdateResponseWriter();
        LOGGER.info("Entering Updateservice, marshal(updateServiceRequestDto):\n{}", updateRecordRequestMarshaller);

        UpdateRecordResponseDTO updateRecordResponseDTO = updateServiceCore.updateRecord(updateServiceRequestDTO, globalActionState);

        final UpdateRecordResultMarshaller updateRecordResultMarshaller = new UpdateRecordResultMarshaller(updateResponseWriter.getResponse());
        LOGGER.info("Leaving UpdateService, marshal(updateRecordResult):\n{}", updateRecordResultMarshaller);
        return new UpdateResponseWriter(updateRecordResponseDTO).getResponse();
    }

    /**
     * WS operation to return a list of validation schemes.
     * <p>
     * The actual lookup of validation schemes is done by the Validator EJB
     * ({@link Validator#getValidateSchemas ()})
     *
     * @param getSchemasRequest The request.
     * @return Returns an instance of GetValidateSchemasResult with the list of
     * validation schemes.
     * @throws EJBException In case of an error.
     */
    public GetSchemasResult getSchemas(GetSchemasRequest getSchemasRequest) {
        LOGGER.entry();

        StopWatch watch = new Log4JStopWatch();
        SchemasResponseDTO schemasResponseDTO;
        GetSchemasResult getSchemasResult;
        final GetSchemasRequestReader getSchemasRequestReader = new GetSchemasRequestReader(getSchemasRequest);
        final SchemasRequestDTO schemasRequestDTO = getSchemasRequestReader.getSchemasRequestDTO();
        try {
            MDC.put(MDC_TRACKING_ID_LOG_CONTEXT, schemasRequestDTO.getTrackingId());

            final GetSchemasRequestMarshaller getSchemasRequestMarshaller = new GetSchemasRequestMarshaller(getSchemasRequest);
            LOGGER.info("Entering getSchemas, marshal(schemasRequestDTO):\n{}",getSchemasRequestMarshaller);

            if (schemasRequestDTO.getAuthenticationDTO() != null &&
                    schemasRequestDTO.getAuthenticationDTO().getGroupId() != null) {
                if (schemasRequestDTO.getTrackingId() != null) {
                    LOGGER.info("getSchemas request from {} with tracking id {}", schemasRequestDTO.getAuthenticationDTO().getGroupId(), schemasRequestDTO.getTrackingId());
                } else {
                    LOGGER.info("getSchemas request from {}", schemasRequestDTO.getAuthenticationDTO().getGroupId());
                }
            }

            final String groupId = schemasRequestDTO.getAuthenticationDTO().getGroupId();
            final String templateGroup = openAgencyService.getTemplateGroup(groupId);
            final List<SchemaDTO> schemaDTOList = validator.getValidateSchemas(groupId, templateGroup);

            schemasResponseDTO = new SchemasResponseDTO();
            schemasResponseDTO.getSchemaDTOList().addAll(schemaDTOList);
            schemasResponseDTO.setUpdateStatusEnumDTO(UpdateStatusEnumDTO.OK);
            schemasResponseDTO.setError(false);

            final GetSchemasResponseWriter getSchemasResponseWriter = new GetSchemasResponseWriter(schemasResponseDTO);
            getSchemasResult = getSchemasResponseWriter.getGetSchemasResult();

            final GetSchemasResultMarshaller getSchemasResultMarshaller = new GetSchemasResultMarshaller(getSchemasResult);
            LOGGER.info("getSchemas returning getSchemasResult:\n{}", JsonMapper.encodePretty(getSchemasResult));
            LOGGER.info("Leaving getSchemas, marshal(getSchemasResult):\n{}", getSchemasResultMarshaller);

            return getSchemasResult;
        } catch (ScripterException ex) {
            LOGGER.error("Caught JavaScript exception", ex);
            schemasResponseDTO = new SchemasResponseDTO();
            schemasResponseDTO.setErrorMessage(ex.getMessage());
            schemasResponseDTO.setUpdateStatusEnumDTO(UpdateStatusEnumDTO.FAILED);
            schemasResponseDTO.setError(true);
            final GetSchemasResponseWriter getSchemasResponseWriter = new GetSchemasResponseWriter(schemasResponseDTO);
            getSchemasResult = getSchemasResponseWriter.getGetSchemasResult();

            return getSchemasResult;
        } catch (OpenAgencyException ex) {
            LOGGER.error("Caught OpenAgencyException exception", ex);
            schemasResponseDTO = new SchemasResponseDTO();
            schemasResponseDTO.setErrorMessage(ex.getMessage());
            schemasResponseDTO.setUpdateStatusEnumDTO(UpdateStatusEnumDTO.FAILED);
            schemasResponseDTO.setError(true);
            final GetSchemasResponseWriter getSchemasResponseWriter = new GetSchemasResponseWriter(schemasResponseDTO);
            getSchemasResult = getSchemasResponseWriter.getGetSchemasResult();

            return getSchemasResult;
        } catch (Throwable ex) {
            // TODO: returner ordentlig fejl her
            LOGGER.error("Caught Throwable", ex);
            schemasResponseDTO = new SchemasResponseDTO();
            schemasResponseDTO.setErrorMessage(ex.getMessage());
            schemasResponseDTO.setUpdateStatusEnumDTO(UpdateStatusEnumDTO.FAILED);
            schemasResponseDTO.setError(true);
            final GetSchemasResponseWriter getSchemasResponseWriter = new GetSchemasResponseWriter(schemasResponseDTO);
            getSchemasResult = getSchemasResponseWriter.getGetSchemasResult();

            return getSchemasResult;
        } finally {
            watch.stop(GET_SCHEMAS_WATCHTAG);
            LOGGER.exit();
            MDC.remove(MDC_TRACKING_ID_LOG_CONTEXT);
        }
    }

}
