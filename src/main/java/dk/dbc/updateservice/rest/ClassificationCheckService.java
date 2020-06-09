/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.rest;

import dk.dbc.common.records.MarcConverter;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.utils.RecordContentTransformer;
import dk.dbc.rawrepo.Record;
import dk.dbc.updateservice.actions.ServiceResult;
import dk.dbc.updateservice.dto.BibliographicRecordDTO;
import dk.dbc.updateservice.dto.MessageEntryDTO;
import dk.dbc.updateservice.dto.RecordDataDTO;
import dk.dbc.updateservice.dto.TypeEnumDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.service.api.BibliographicRecord;
import dk.dbc.updateservice.service.api.ObjectFactory;
import dk.dbc.updateservice.service.api.UpdateRecordResult;
import dk.dbc.updateservice.update.HoldingsItems;
import dk.dbc.updateservice.update.LibraryRecordsHandler;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.utils.ResourceBundles;
import dk.dbc.updateservice.ws.UpdateRequestReader;
import dk.dbc.updateservice.ws.UpdateResponseWriter;
import dk.dbc.updateservice.ws.UpdateServiceEndpoint;
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
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;

@Stateless
@Path("/api")
public class ClassificationCheckService {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(ClassificationCheckService.class);
    private static final ResourceBundle resourceBundle = ResourceBundles.getBundle("actions");

    @EJB
    private LibraryRecordsHandler libraryRecordsHandler;

    @EJB
    private RawRepo rawRepo;

    @EJB
    private HoldingsItems holdingsItems;

    @POST
    @Path("v1/classificationcheck")
    @Consumes({MediaType.APPLICATION_XML})
    @Produces(MediaType.APPLICATION_XML)
    @Timed
    public Response classificationCheck(BibliographicRecord bibliographicRecord) {
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

            ServiceResult serviceResult = ServiceResult.newOkResult();
            if (record != null) {
                final MarcRecordReader recordReader = new MarcRecordReader(record);
                final String recordId = recordReader.getValue("001", "a");
                final int agencyId = Integer.parseInt(recordReader.getValue("001", "b"));
                if (rawRepo.recordExists(recordId, agencyId)) {
                    final MarcRecord oldRecord = loadRecord(recordId, agencyId);
                    final Set<Integer> holdingAgencies = holdingsItems.getAgenciesThatHasHoldingsForId(recordId);
                    if (holdingAgencies.size() > 0) {
                        List<String> classificationsChangedMessages = new ArrayList<>();
                        if (libraryRecordsHandler.hasClassificationsChanged(oldRecord, record, classificationsChangedMessages)) {
                            final List<MessageEntryDTO> messageEntryDTOs = new ArrayList<>();

                            final MessageEntryDTO holdingsMessageEntryDTO = new MessageEntryDTO();
                            holdingsMessageEntryDTO.setType(TypeEnumDTO.WARNING);
                            holdingsMessageEntryDTO.setMessage("Count: " + holdingAgencies.size());
                            messageEntryDTOs.add(holdingsMessageEntryDTO);

                            for (String classificationsChangedMessage : classificationsChangedMessages) {
                                final MessageEntryDTO messageEntryDTO = new MessageEntryDTO();
                                messageEntryDTO.setType(TypeEnumDTO.WARNING);
                                messageEntryDTO.setMessage("Reason: " + resourceBundle.getString(classificationsChangedMessage));
                                messageEntryDTOs.add(messageEntryDTO);
                            }

                            serviceResult = new ServiceResult();
                            serviceResult.setStatus(UpdateStatusEnumDTO.FAILED);
                            serviceResult.setEntries(messageEntryDTOs);
                        }
                    }
                }
            } else {
                serviceResult = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, "No record data found in request");
            }

            updateResponseWriter = new UpdateResponseWriter();
            updateResponseWriter.setServiceResult(serviceResult);
            updateRecordResult = updateResponseWriter.getResponse();

            return Response.ok(marshal(updateRecordResult), MediaType.APPLICATION_XML).build();
        } catch (Exception ex) {
            LOGGER.error("Exception during classificationCheck", ex);

            ServiceResult serviceResult = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, "Please see the log for more information");

            updateResponseWriter = new UpdateResponseWriter();
            updateResponseWriter.setServiceResult(serviceResult);
            updateRecordResult = updateResponseWriter.getResponse();

            return Response.ok(marshal(updateRecordResult), MediaType.APPLICATION_XML).build();
        }
    }

    protected MarcRecord loadRecord(String recordId, Integer agencyId) throws UpdateException, UnsupportedEncodingException {
        LOGGER.entry(recordId, agencyId);
        MarcRecord result = null;
        try {
            Record record = rawRepo.fetchRecord(recordId, agencyId);
            return result = RecordContentTransformer.decodeRecord(record.getContent());
        } finally {
            LOGGER.exit(result);
        }
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
            LOGGER.warn(UpdateServiceEndpoint.MARSHALLING_ERROR_MSG);
            return new ReflectionToStringBuilder(updateRecordResult, new RecursiveToStringStyle()).toString();
        }
    }

}
