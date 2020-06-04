/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.rest;

import dk.dbc.jsonb.JSONBException;
import dk.dbc.updateservice.dto.BibliographicRecordDTO;
import dk.dbc.updateservice.dto.UpdateRecordResponseDTO;
import dk.dbc.updateservice.service.api.BibliographicRecord;
import dk.dbc.updateservice.service.api.ObjectFactory;
import dk.dbc.updateservice.service.api.UpdateRecordResult;
import dk.dbc.updateservice.ws.UpdateRequestReader;
import dk.dbc.updateservice.ws.UpdateResponseWriter;
import dk.dbc.updateservice.ws.UpdateService;
import dk.dbc.util.Timed;
import org.apache.commons.lang3.builder.RecursiveToStringStyle;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

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
import java.io.StringWriter;

@Stateless
@Path("/api")
public class DoubleRecordCheckService {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(DoubleRecordCheckService.class);

    @EJB
    DoubleRecordCheckServiceRest doubleRecordCheckServiceRest;

    @POST
    @Path("v1/doublerecordcheck")
    @Consumes({MediaType.APPLICATION_XML})
    @Produces(MediaType.APPLICATION_XML)
    @Timed
    public Response doubleRecordCheck(BibliographicRecord bibliographicRecord) throws JSONBException {
        LOGGER.info("doubleRecordCheck - webservice. Incoming request is:{}", bibliographicRecord.toString());
        UpdateResponseWriter updateResponseWriter;
        UpdateRecordResult updateRecordResult;
        BibliographicRecordDTO bibliographicRecordDTO = UpdateRequestReader.convertExternalBibliographicRecordToInternalBibliographicRecordDto(bibliographicRecord);
        UpdateRecordResponseDTO updateRecordResponseDTO = doubleRecordCheckServiceRest.doubleRecordCheck(bibliographicRecordDTO);
        updateResponseWriter = new UpdateResponseWriter(updateRecordResponseDTO);
        updateRecordResult = updateResponseWriter.getResponse();
        return Response.ok(marshal(updateRecordResult), MediaType.APPLICATION_XML).build();
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
