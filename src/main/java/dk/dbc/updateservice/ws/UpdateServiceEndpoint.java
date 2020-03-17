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
import dk.dbc.updateservice.service.api.CatalogingUpdatePortType;
import dk.dbc.updateservice.service.api.GetSchemasRequest;
import dk.dbc.updateservice.service.api.GetSchemasResult;
import dk.dbc.updateservice.service.api.UpdateRecordRequest;
import dk.dbc.updateservice.service.api.UpdateRecordResult;
import dk.dbc.updateservice.update.SolrException;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.jws.WebService;
import javax.servlet.http.HttpServletResponse;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.MessageContext;
import java.io.IOException;

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

    @SuppressWarnings("EjbEnvironmentInspection")
    @Resource
    private WebServiceContext wsContext;

    @EJB
    UpdateService updateService;


    @PostConstruct
    protected void init() {
        globalActionState = new GlobalActionState();
        globalActionState.setWsContext(wsContext);
    }

    @Override
    public UpdateRecordResult updateRecord(UpdateRecordRequest updateRecordRequest) {
        LOGGER.entry();
        StopWatch watch = new Log4JStopWatch();
        UpdateRecordResult updateRecordResult;
        ServiceResult serviceResult;
        final UpdateResponseWriter updateResponseWriter = new UpdateResponseWriter();

        try {
            if (!updateService.isServiceReady(globalActionState)) {
                LOGGER.info("Updateservice not ready yet, leaving");
                watch.stop(UpdateService.UPDATE_WATCHTAG);
                return null;
            }

            return updateService.updateRecord(updateRecordRequest, globalActionState);
        } catch (SolrException e) {
            LOGGER.catching(e);
            serviceResult = ServiceResult.newFatalResult(UpdateStatusEnumDTO.FAILED, e.getMessage());
            updateResponseWriter.setServiceResult(serviceResult);
            MessageContext ctx = wsContext.getMessageContext();
            HttpServletResponse response = (HttpServletResponse)
                    ctx.get(MessageContext.SERVLET_RESPONSE);
            try {
                response.sendError(500, "Solr connection failed");
            } catch (IOException e1) {
                LOGGER.error("Send error encountered an exception : ");
                LOGGER.catching(e1);
            }
            return updateResponseWriter.getResponse();
        } catch (Exception e) {
            LOGGER.catching(e);
            serviceResult = ServiceResult.newFatalResult(UpdateStatusEnumDTO.FAILED, e.getMessage());
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
        GetSchemasResult getSchemasResult;
        StopWatch watch = new Log4JStopWatch();
        try {
            GetSchemasRequestReader getSchemasRequestReader = new GetSchemasRequestReader(getSchemasRequest);
            SchemasResponseDTO schemasResponseDTO = updateService.getSchemas(getSchemasRequestReader.getSchemasRequestDTO());
            GetSchemasResponseWriter getSchemasResponseWriter = new GetSchemasResponseWriter(schemasResponseDTO);
            getSchemasResult = getSchemasResponseWriter.getGetSchemasResult();
            return getSchemasResult;
        } finally {
            watch.stop(GET_SCHEMAS_STOPWATCH);
            LOGGER.exit();
        }
    }


}
