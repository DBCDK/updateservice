/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.ws;

import dk.dbc.updateservice.actions.GlobalActionState;
import dk.dbc.updateservice.dto.SchemasRequestDTO;
import dk.dbc.updateservice.dto.SchemasResponseDTO;
import dk.dbc.updateservice.dto.UpdateRecordResponseDTO;
import dk.dbc.updateservice.dto.UpdateServiceRequestDTO;
import dk.dbc.updateservice.json.JsonMapper;
import dk.dbc.updateservice.update.UpdateServiceCore;
import dk.dbc.updateservice.service.api.GetSchemasRequest;
import dk.dbc.updateservice.service.api.GetSchemasResult;
import dk.dbc.updateservice.service.api.UpdateRecordRequest;
import dk.dbc.updateservice.service.api.UpdateRecordResult;
import dk.dbc.updateservice.validate.Validator;
import dk.dbc.updateservice.ws.marshall.GetSchemasRequestMarshaller;
import dk.dbc.updateservice.ws.marshall.GetSchemasResultMarshaller;
import dk.dbc.updateservice.ws.marshall.UpdateRecordRequestMarshaller;
import dk.dbc.updateservice.ws.marshall.UpdateRecordResultMarshaller;
import java.io.IOException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;

/**
 * UpdateService web service.
 * <p>
 * Validates a record by using an JavaScript engine. This EJB also has
 * the responsibility to return a list of valid validation schemes.
 */
@Stateless
public class UpdateService {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(UpdateService.class);
    public static final String MARSHALLING_ERROR_MSG = "Got an error while marshalling input request, using reflection instead.";
    public static final String MDC_TRACKING_ID_LOG_CONTEXT = "trackingId";
    public static final String UPDATE_SERVICE_VERSION = "2.0";

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
    public GetSchemasResult getSchemas(GetSchemasRequest getSchemasRequest) throws IOException {
        GetSchemasResult getSchemasResult;
        final GetSchemasRequestReader getSchemasRequestReader = new GetSchemasRequestReader(getSchemasRequest);
        final SchemasRequestDTO schemasRequestDTO = getSchemasRequestReader.getSchemasRequestDTO();
        final GetSchemasRequestMarshaller getSchemasRequestMarshaller = new GetSchemasRequestMarshaller(getSchemasRequest);
        LOGGER.info("Entering getSchemas, marshal(schemasRequestDTO):\n{}",getSchemasRequestMarshaller);

        SchemasResponseDTO schemasResponseDTO = updateServiceCore.getSchemas(schemasRequestDTO);

        final GetSchemasResponseWriter getSchemasResponseWriter = new GetSchemasResponseWriter(schemasResponseDTO);
        getSchemasResult = getSchemasResponseWriter.getGetSchemasResult();

        final GetSchemasResultMarshaller getSchemasResultMarshaller = new GetSchemasResultMarshaller(getSchemasResult);
        LOGGER.info("getSchemas returning getSchemasResult:\n{}", JsonMapper.encodePretty(getSchemasResult));
        LOGGER.info("Leaving getSchemas, marshal(getSchemasResult):\n{}", getSchemasResultMarshaller);

        return getSchemasResult;
    }
}
