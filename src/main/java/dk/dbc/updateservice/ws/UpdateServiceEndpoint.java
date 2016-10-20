package dk.dbc.updateservice.ws;

import com.sun.xml.ws.developer.SchemaValidation;
import dk.dbc.iscrum.utils.json.Json;
import dk.dbc.updateservice.actions.GlobalActionState;
import dk.dbc.updateservice.actions.ServiceResult;
import dk.dbc.updateservice.dto.SchemasResponseDto;
import dk.dbc.updateservice.dto.UpdateStatusEnumDto;
import dk.dbc.updateservice.service.api.CatalogingUpdatePortType;
import dk.dbc.updateservice.service.api.GetSchemasRequest;
import dk.dbc.updateservice.service.api.GetSchemasResult;
import dk.dbc.updateservice.service.api.ObjectFactory;
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
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.ws.WebServiceContext;
import java.io.IOException;
import java.io.StringWriter;

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
    private static final String UPDATERECORD_STOPWATCH = "UpdateServiceDBC";
    private static final String GET_SCHEMAS_STOPWATCH = "GetSchemasDBC";

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
        DBCUpdateResponseWriter dbcUpdateResponseWriter;
        try {
            if (!updateService.isServiceReady(globalActionState)) {
                LOGGER.info("UpdateserviceDBC not ready yet, leaving");
                watch.stop(UpdateService.UPDATE_WATCHTAG);
                return null;
            }
            LOGGER.info("Entering UpdateserviceDBC, marshal(updateServiceRequestDto):\n" + marshal(updateRecordRequest));
            DBCUpdateRequestReader dbcUpdateRequestReader = new DBCUpdateRequestReader(updateRecordRequest);
            serviceResult = updateService.updateRecord(dbcUpdateRequestReader.getUpdateServiceRequestDto(), globalActionState);
            dbcUpdateResponseWriter = new DBCUpdateResponseWriter();
            dbcUpdateResponseWriter.setServiceResult(serviceResult);
            updateRecordResult = dbcUpdateResponseWriter.getResponse();
            LOGGER.info("UpdateServiceDBC returning updateRecordResult:\n" + Json.encodePretty(updateRecordResult));
            LOGGER.info("Leaving UpdateServiceDBC, marshal(updateRecordResult):\n" + marshal(updateRecordResult));
            return updateRecordResult;
        } catch (IOException e) {
            LOGGER.catching(e);
            serviceResult = ServiceResult.newFatalResult(UpdateStatusEnumDto.FAILED, e.getMessage(), globalActionState);
            dbcUpdateResponseWriter = new DBCUpdateResponseWriter();
            dbcUpdateResponseWriter.setServiceResult(serviceResult);
            return dbcUpdateResponseWriter.getResponse();
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
            DBCGetSchemasRequestReader dbcGetSchemasRequestReader = new DBCGetSchemasRequestReader(getSchemasRequest);
            SchemasResponseDto schemasResponseDto = updateService.getSchemas(dbcGetSchemasRequestReader.getSchemasRequestDto());
            DBCGetSchemasResponseWriter dbcGetSchemasResponseWriter = new DBCGetSchemasResponseWriter(schemasResponseDto);
            getSchemasResult = dbcGetSchemasResponseWriter.getGetSchemasResult();
            return getSchemasResult;
        } finally {
            watch.stop(GET_SCHEMAS_STOPWATCH);
            LOGGER.exit();
        }
    }

    @SuppressWarnings("Duplicates")
    private String marshal(UpdateRecordRequest updateRecordRequest) {
        try {
            ObjectFactory objectFactory = new ObjectFactory();
            JAXBElement<UpdateRecordRequest> jAXBElement = objectFactory.createUpdateRecordRequest(updateRecordRequest);
            StringWriter stringWriter = new StringWriter();
            JAXBContext jaxbContext = JAXBContext.newInstance(UpdateRecordRequest.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.marshal(jAXBElement, stringWriter);
            return stringWriter.toString();
        } catch (JAXBException e) {
            LOGGER.catching(e);
            LOGGER.warn(UpdateService.MARSHALLING_ERROR_MSG);
            return updateService.objectToStringReflection(updateRecordRequest);
        }
    }

    @SuppressWarnings("Duplicates")
    private String marshal(UpdateRecordResult updateRecordResult) {
        try {
            ObjectFactory objectFactory = new ObjectFactory();
            JAXBElement<UpdateRecordResult> jAXBElement = objectFactory.createUpdateRecordResult(updateRecordResult);
            StringWriter stringWriter = new StringWriter();
            JAXBContext jaxbContext = JAXBContext.newInstance(UpdateRecordResult.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.marshal(jAXBElement, stringWriter);
            return stringWriter.toString();
        } catch (JAXBException e) {
            LOGGER.catching(e);
            LOGGER.warn(UpdateService.MARSHALLING_ERROR_MSG);
            return updateService.objectToStringReflection(updateRecordResult);
        }
    }
}