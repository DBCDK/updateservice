package dk.dbc.updateservice.rest;

import dk.dbc.common.records.MarcConverter;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.updateservice.actions.ServiceResult;
import dk.dbc.updateservice.dto.BibliographicRecordDTO;
import dk.dbc.updateservice.dto.DoubleRecordFrontendDTO;
import dk.dbc.updateservice.dto.DoubleRecordFrontendStatusDTO;
import dk.dbc.updateservice.dto.RecordDataDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.json.JsonMapper;
import dk.dbc.updateservice.service.api.BibliographicRecord;
import dk.dbc.updateservice.service.api.ObjectFactory;
import dk.dbc.updateservice.service.api.UpdateRecordResult;
import dk.dbc.updateservice.update.UpdateStore;
import dk.dbc.updateservice.ws.JNDIResources;
import dk.dbc.updateservice.ws.UpdateRequestReader;
import dk.dbc.updateservice.ws.UpdateResponseWriter;
import dk.dbc.updateservice.ws.UpdateService;
import dk.dbc.util.Timed;
import org.apache.commons.lang3.builder.RecursiveToStringStyle;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.w3c.dom.Node;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.transform.dom.DOMSource;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

@Stateless
@Path("/api")
public class DoubleRecordCheckService {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(DoubleRecordCheckService.class);
    private static final String ENTRY_POINT = "checkDoubleRecordFrontend";

    @EJB
    private Scripter scripter;

    @EJB
    private UpdateStore updateStore;

    @POST
    @Path("v1/doublerecordcheck")
    @Consumes({MediaType.APPLICATION_XML})
    @Produces(MediaType.APPLICATION_XML)
    @Timed
    public Response doubleRecordCheck(BibliographicRecord bibliographicRecord) {
        UpdateResponseWriter updateResponseWriter;
        UpdateRecordResult updateRecordResult;

        try {
            BibliographicRecordDTO bibliographicRecordDTO = UpdateRequestReader.convertExternalBibliographicRecordToInternalBibliographicRecordDto(bibliographicRecord);
            final RecordDataDTO recordDataDTO = bibliographicRecordDTO.getRecordDataDTO();
            MarcRecord record = null;

            if (recordDataDTO != null) {
                List<Object> list = recordDataDTO.getContent();
                for (Object o : list) {
                    if (o instanceof Node) {
                        record = MarcConverter.createFromMarcXChange(new DOMSource((Node) o));
                        break;
                    }
                }
            }

            ServiceResult serviceResult;
            if (record != null) {
                final Object jsResult = scripter.callMethod(ENTRY_POINT, JsonMapper.encode(record), JNDIResources.getProperties());
                serviceResult = parseJavascript(jsResult);
            } else {
                serviceResult = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, "No record data found in request");
            }

            updateResponseWriter = new UpdateResponseWriter();
            updateResponseWriter.setServiceResult(serviceResult);
            updateRecordResult = updateResponseWriter.getResponse();

            return Response.ok(marshal(updateRecordResult), MediaType.APPLICATION_XML).build();
        } catch (Exception ex) {
            LOGGER.error("Exception during doubleRecordCheck", ex);

            ServiceResult serviceResult = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, "Please see the log for more information");

            updateResponseWriter = new UpdateResponseWriter();
            updateResponseWriter.setServiceResult(serviceResult);
            updateRecordResult = updateResponseWriter.getResponse();

            return Response.ok(marshal(updateRecordResult), MediaType.APPLICATION_XML).build();
        }
    }

    ServiceResult parseJavascript(Object o) throws IOException {
        ServiceResult result;
        DoubleRecordFrontendStatusDTO doubleRecordFrontendStatusDTO = JsonMapper.decode(o.toString(), DoubleRecordFrontendStatusDTO.class);
        if ("ok".equals(doubleRecordFrontendStatusDTO.getStatus())) {
            result = ServiceResult.newOkResult();
        } else if ("doublerecord".equals(doubleRecordFrontendStatusDTO.getStatus())) {
            result = new ServiceResult();
            for (DoubleRecordFrontendDTO doubleRecordFrontendDTO : doubleRecordFrontendStatusDTO.getDoubleRecordFrontendDTOs()) {
                result.addServiceResult(ServiceResult.newDoubleRecordErrorResult(UpdateStatusEnumDTO.FAILED, doubleRecordFrontendDTO));
            }
            result.setDoubleRecordKey(updateStore.getNewDoubleRecordKey());
        } else {
            String msg = "Unknown error";
            if (doubleRecordFrontendStatusDTO.getDoubleRecordFrontendDTOs() != null && !doubleRecordFrontendStatusDTO.getDoubleRecordFrontendDTOs().isEmpty()) {
                msg = doubleRecordFrontendStatusDTO.getDoubleRecordFrontendDTOs().get(0).getMessage();
            }
            result = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, msg);
        }
        return result;
    }

    String marshal(UpdateRecordResult updateRecordResult) {
        try {
            ObjectFactory objectFactory = new ObjectFactory();
            JAXBElement<UpdateRecordResult> jAXBElement = objectFactory.createUpdateRecordResult(updateRecordResult);
            StringWriter stringWriter = new StringWriter();
            JAXBContext jaxbContext = JAXBContext.newInstance(UpdateRecordResult.class);
            Marshaller marshaller;
            marshaller = jaxbContext.createMarshaller();
            marshaller.marshal(jAXBElement, stringWriter);
            return stringWriter.toString();
        } catch (JAXBException e) {
            LOGGER.catching(e);
            LOGGER.warn(UpdateService.MARSHALLING_ERROR_MSG);
            return new ReflectionToStringBuilder(updateRecordResult, new RecursiveToStringStyle()).toString();
        }
    }

}
