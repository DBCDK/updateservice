/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.ws;

import com.sun.xml.ws.developer.SchemaValidation;
import dk.dbc.updateservice.actions.GlobalActionState;
import dk.dbc.updateservice.actions.ServiceResult;
import dk.dbc.updateservice.dto.SchemasResponseDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.UpdateServiceCore;
import dk.dbc.updateservice.service.api.CatalogingUpdatePortType;
import dk.dbc.updateservice.service.api.GetSchemasRequest;
import dk.dbc.updateservice.service.api.GetSchemasResult;
import dk.dbc.updateservice.service.api.UpdateRecordRequest;
import dk.dbc.updateservice.service.api.UpdateRecordResult;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.jws.WebService;
import javax.xml.ws.WebServiceContext;

@SchemaValidation(outbound = false)
@WebService(
        serviceName = "UpdateService",
        portName = "CatalogingUpdatePort",
        endpointInterface = "dk.dbc.updateservice.service.api.CatalogingUpdatePortType",
        targetNamespace = "http://oss.dbc.dk/ns/catalogingUpdate",
        wsdlLocation = "WEB-INF/classes/META-INF/wsdl/update/catalogingUpdate.wsdl",
        name = UpdateService.UPDATE_SERVICE_VERSION)
@Stateless
public class UpdateServiceEndpoint implements CatalogingUpdatePortType {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(UpdateServiceEndpoint.class);
    private static final String UPDATERECORD_STOPWATCH = "UpdateService";
    private static final String GET_SCHEMAS_STOPWATCH = "GetSchemas";

    private GlobalActionState globalActionState;

    @Resource
    private WebServiceContext wsContext;

    @EJB
    UpdateService updateService;

    @EJB
    UpdateServiceCore updateServiceCore;


    @PostConstruct
    protected void init() {
        globalActionState = new GlobalActionState();
        globalActionState.setWsContext(wsContext);
    }

    @Override
    public UpdateRecordResult updateRecord(UpdateRecordRequest updateRecordRequest) {
        LOGGER.entry();
        StopWatch watch = new Log4JStopWatch();
        final UpdateResponseWriter updateResponseWriter = new UpdateResponseWriter();

        try {
            if (!updateServiceCore.isServiceReady(globalActionState)) {
                LOGGER.info("Updateservice not ready yet, leaving");
                return null;
            }

            return updateService.updateRecord(updateRecordRequest, globalActionState);
        } catch (Throwable e) {
            LOGGER.error("Caught unexpected exception during updateRecord", e);
            final ServiceResult serviceResult = ServiceResult.newFatalResult(UpdateStatusEnumDTO.FAILED, "Caught unexpected exception");
            updateResponseWriter.setServiceResult(serviceResult);
            return updateResponseWriter.getResponse();
        } finally {
            watch.stop(UPDATERECORD_STOPWATCH);
            LOGGER.exit();
        }
    }

    @Override
    public GetSchemasResult getSchemas(GetSchemasRequest getSchemasRequest) {
        LOGGER.entry();
        StopWatch watch = new Log4JStopWatch();

        try {
            if (!updateServiceCore.isServiceReady(globalActionState)) {
                LOGGER.info("Updateservice not ready yet, leaving");
                return null;
            }

            return updateService.getSchemas(getSchemasRequest);
        } catch (Throwable e) {
            LOGGER.error("Caught unexpected exception during getSchemas", e);
            final SchemasResponseDTO schemasResponseDTO = new SchemasResponseDTO();
            schemasResponseDTO.setUpdateStatusEnumDTO(UpdateStatusEnumDTO.FAILED);
            schemasResponseDTO.setErrorMessage("Caught unexpected exception");
            schemasResponseDTO.setError(true);
            final GetSchemasResponseWriter getSchemasResponseWriter = new GetSchemasResponseWriter(schemasResponseDTO);
            return getSchemasResponseWriter.getGetSchemasResult();
        } finally {
            watch.stop(GET_SCHEMAS_STOPWATCH);
            LOGGER.exit();
        }
    }


}
