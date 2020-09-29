/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.rest;

import dk.dbc.jsonb.JSONBContext;
import dk.dbc.jsonb.JSONBException;
import dk.dbc.updateservice.dto.BibliographicRecordDTO;
import dk.dbc.updateservice.dto.UpdateRecordResponseDTO;
import dk.dbc.updateservice.update.UpdateServiceCore;
import dk.dbc.util.Timed;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

@Stateless
@Path("/api")
public class ClassificationCheckServiceRest {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(ClassificationCheckServiceRest.class);

    @EJB
    UpdateServiceCore updateServiceCore;

    @POST
    @Path("v2/classificationcheck")
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces(MediaType.APPLICATION_JSON)
    @Timed
    public UpdateRecordResponseDTO classificationCheck(BibliographicRecordDTO bibliographicRecordDTO) throws JSONBException {
        LOGGER.info("classificationCheck - rest. Incoming: {}", new JSONBContext().marshall(bibliographicRecordDTO));
        UpdateRecordResponseDTO updateRecordResponseDTO = updateServiceCore.classificationCheck(bibliographicRecordDTO);
        LOGGER.info("classificationCheck result is: {}", updateRecordResponseDTO.toString());
        return updateRecordResponseDTO;
    }
}
