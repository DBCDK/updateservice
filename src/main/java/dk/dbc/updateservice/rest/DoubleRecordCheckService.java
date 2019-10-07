package dk.dbc.updateservice.rest;

import dk.dbc.common.records.MarcConverter;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.jsonb.JSONBContext;
import dk.dbc.jsonb.JSONBException;
import dk.dbc.updateservice.actions.ServiceResult;
import dk.dbc.updateservice.dto.BibliographicRecordDTO;
import dk.dbc.updateservice.dto.DoubleRecordFrontendDTO;
import dk.dbc.updateservice.dto.DoubleRecordFrontendStatusDTO;
import dk.dbc.updateservice.dto.RecordDataDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.javascript.ScripterPool;
import dk.dbc.updateservice.json.JsonMapper;
import dk.dbc.updateservice.ws.JNDIResources;
import dk.dbc.util.Timed;
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
import javax.xml.transform.dom.DOMSource;
import java.io.IOException;
import java.util.List;

@Stateless
@Path("/api")
public class DoubleRecordCheckService {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(DoubleRecordCheckService.class);
    private static final String ENTRY_POINT = "checkDoubleRecordFrontend";
    private final JSONBContext jsonbContext = new JSONBContext();

    @EJB
    private ScripterPool scripterPool;

    @POST
    @Path("v1/doublerecordcheck")
    @Consumes({MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_XML})
    @Timed
    public Response doubleRecordCheck(String request) {
        String res;

        try {
            final BibliographicRecordDTO bibliographicRecordDTO = jsonbContext.unmarshall(request, BibliographicRecordDTO.class);
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

            if (record != null) {
                final Scripter scripter = new Scripter();
                final Object jsResult = scripter.callMethod(ENTRY_POINT, JsonMapper.encode(record), JNDIResources.getProperties());
                final ServiceResult serviceResult = parseJavascript(jsResult);

                res = jsonbContext.marshall(serviceResult);

                return Response.ok(res, MediaType.APPLICATION_JSON).build();
            } else {
            }


        } catch (IOException | ScripterException | JSONBException | InterruptedException ex) {
            LOGGER.error("Exception during doubleRecordCheck", ex);
            res = jsonbContext.marshall();

            return Response.ok(res, MediaType.APPLICATION_JSON).build();
        }
    }


    private ServiceResult parseJavascript(Object o) throws IOException {
        ServiceResult result;
        DoubleRecordFrontendStatusDTO doubleRecordFrontendStatusDTO = JsonMapper.decode(o.toString(), DoubleRecordFrontendStatusDTO.class);
        if ("ok".equals(doubleRecordFrontendStatusDTO.getStatus())) {
            result = ServiceResult.newOkResult();
        } else if ("doublerecord".equals(doubleRecordFrontendStatusDTO.getStatus())) {
            result = new ServiceResult();
            for (DoubleRecordFrontendDTO doubleRecordFrontendDTO : doubleRecordFrontendStatusDTO.getDoubleRecordFrontendDTOs()) {
                result.addServiceResult(ServiceResult.newDoubleRecordErrorResult(UpdateStatusEnumDTO.FAILED, doubleRecordFrontendDTO, state));
            }
            result.setDoubleRecordKey(state.getUpdateStore().getNewDoubleRecordKey());
        } else {
            String msg = "Unknown error";
            if (doubleRecordFrontendStatusDTO.getDoubleRecordFrontendDTOs() != null && !doubleRecordFrontendStatusDTO.getDoubleRecordFrontendDTOs().isEmpty()) {
                msg = doubleRecordFrontendStatusDTO.getDoubleRecordFrontendDTOs().get(0).getMessage();
            }
            result = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, msg, state);
        }
        return result;
    }

}
