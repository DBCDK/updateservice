/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.ws;

import com.sun.xml.ws.developer.SchemaValidation;
import dk.dbc.updateservice.actions.GlobalActionState;
import dk.dbc.updateservice.actions.ServiceResult;
import dk.dbc.updateservice.client.BibliographicRecordExtraData;
import dk.dbc.updateservice.dto.SchemasRequestDTO;
import dk.dbc.updateservice.dto.SchemasResponseDTO;
import dk.dbc.updateservice.dto.UpdateRecordResponseDTO;
import dk.dbc.updateservice.dto.UpdateServiceRequestDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.service.api.CatalogingUpdatePortType;
import dk.dbc.updateservice.service.api.GetSchemasRequest;
import dk.dbc.updateservice.service.api.GetSchemasResult;
import dk.dbc.updateservice.service.api.UpdateRecordRequest;
import dk.dbc.updateservice.service.api.UpdateRecordResult;
import dk.dbc.updateservice.update.UpdateServiceCore;
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

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.jws.WebService;
import javax.xml.ws.WebServiceContext;

import static dk.dbc.updateservice.utils.MDCUtil.MDC_TRACKING_ID_LOG_CONTEXT;

@SchemaValidation(outbound = false)
@WebService(
        serviceName = "UpdateService",
        portName = "CatalogingUpdatePort",
        endpointInterface = "dk.dbc.updateservice.service.api.CatalogingUpdatePortType",
        targetNamespace = "http://oss.dbc.dk/ns/catalogingUpdate",
        wsdlLocation = "WEB-INF/classes/META-INF/wsdl/update/catalogingUpdate.wsdl",
        name = UpdateServiceEndpoint.UPDATE_SERVICE_VERSION)
@Stateless
public class UpdateServiceEndpoint implements CatalogingUpdatePortType {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(UpdateServiceEndpoint.class);
    public static final String MARSHALLING_ERROR_MSG = "Got an error while marshalling input request, using reflection instead.";
    static final String UPDATE_SERVICE_VERSION = "2.0";

    private GlobalActionState globalActionState;

    @Resource
    private WebServiceContext wsContext;

    @EJB
    UpdateServiceCore updateServiceCore;

    @PostConstruct
    protected void init() {
        globalActionState = new GlobalActionState();
        globalActionState.setWsContext(wsContext);
    }

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
     */
    @Override
    public UpdateRecordResult updateRecord(UpdateRecordRequest updateRecordRequest) {
        LOGGER.entry();
        StopWatch watch = new Log4JStopWatch();
        MDC.put(MDC_TRACKING_ID_LOG_CONTEXT, updateRecordRequest.getTrackingId());
        UpdateResponseWriter updateResponseWriter;
        UpdateRecordResult updateRecordResult = null;
        try {
            if (!updateServiceCore.isServiceReady(globalActionState)) {
                LOGGER.info("Updateservice not ready yet, leaving");
                return null;
            }

            final UpdateRequestReader updateRequestReader = new UpdateRequestReader(updateRecordRequest);
            final UpdateServiceRequestDTO updateServiceRequestDTO = updateRequestReader.getUpdateServiceRequestDTO();
            final UpdateRecordRequestMarshaller updateRecordRequestMarshaller = new UpdateRecordRequestMarshaller(updateRecordRequest);
            LOGGER.info("updateRecord SOAP received: {}", updateRecordRequestMarshaller);

            UpdateRecordResponseDTO updateRecordResponseDTO = updateServiceCore.updateRecord(updateServiceRequestDTO, globalActionState);
            updateResponseWriter = new UpdateResponseWriter(updateRecordResponseDTO);

            updateRecordResult = updateResponseWriter.getResponse();
            return updateRecordResult;
        } catch (Throwable e) {
            LOGGER.error("Caught unexpected exception during updateRecord", e);
            final ServiceResult serviceResult = ServiceResult.newFatalResult(UpdateStatusEnumDTO.FAILED, "Caught unexpected exception");
            updateResponseWriter = new UpdateResponseWriter();
            updateResponseWriter.setServiceResult(serviceResult);
            return updateResponseWriter.getResponse();
        } finally {
            final UpdateRecordResultMarshaller updateRecordResultMarshaller = new UpdateRecordResultMarshaller(updateRecordResult);
            LOGGER.info("updateRecord SOAP returns: {}", updateRecordResultMarshaller.toString());
            watch.stop(UpdateServiceCore.UPDATERECORD_STOPWATCH);
            LOGGER.exit();
            MDC.clear();
        }
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
     */
    @Override
    public GetSchemasResult getSchemas(GetSchemasRequest getSchemasRequest) {
        LOGGER.entry();
        StopWatch watch = new Log4JStopWatch();
        MDC.put(MDC_TRACKING_ID_LOG_CONTEXT, getSchemasRequest.getTrackingId());
        GetSchemasResult getSchemasResult = null;
        try {
            if (!updateServiceCore.isServiceReady(globalActionState)) {
                LOGGER.info("Updateservice not ready yet, leaving");
                return null;
            }

            final GetSchemasRequestReader getSchemasRequestReader = new GetSchemasRequestReader(getSchemasRequest);
            final SchemasRequestDTO schemasRequestDTO = getSchemasRequestReader.getSchemasRequestDTO();
            final GetSchemasRequestMarshaller getSchemasRequestMarshaller = new GetSchemasRequestMarshaller(getSchemasRequest);
            LOGGER.info("getSchemas SOAP received: {}", getSchemasRequestMarshaller);

            SchemasResponseDTO schemasResponseDTO = updateServiceCore.getSchemas(schemasRequestDTO);

            final GetSchemasResponseWriter getSchemasResponseWriter = new GetSchemasResponseWriter(schemasResponseDTO);
            getSchemasResult = getSchemasResponseWriter.getGetSchemasResult();

            return getSchemasResult;
        } catch (Throwable e) {
            LOGGER.error("Caught unexpected exception during getSchemas", e);
            final SchemasResponseDTO schemasResponseDTO = new SchemasResponseDTO();
            schemasResponseDTO.setUpdateStatusEnumDTO(UpdateStatusEnumDTO.FAILED);
            schemasResponseDTO.setErrorMessage("Caught unexpected exception");
            schemasResponseDTO.setError(true);
            final GetSchemasResponseWriter getSchemasResponseWriter = new GetSchemasResponseWriter(schemasResponseDTO);
            return getSchemasResponseWriter.getGetSchemasResult();
        } finally {
            final GetSchemasResultMarshaller getSchemasResultMarshaller = new GetSchemasResultMarshaller(getSchemasResult);
            LOGGER.info("getSchemas SOAP returns: {}", getSchemasResultMarshaller.toString());
            watch.stop(UpdateServiceCore.GET_SCHEMAS_STOPWATCH);
            LOGGER.exit();
            MDC.clear();
        }
    }

}
