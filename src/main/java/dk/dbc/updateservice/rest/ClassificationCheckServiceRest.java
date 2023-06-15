package dk.dbc.updateservice.rest;

import dk.dbc.commons.jsonb.JSONBContext;
import dk.dbc.commons.jsonb.JSONBException;
import dk.dbc.updateservice.dto.BibliographicRecordDTO;
import dk.dbc.updateservice.dto.UpdateRecordResponseDTO;
import dk.dbc.updateservice.update.UpdateServiceCore;
import dk.dbc.updateservice.utils.DeferredLogger;
import dk.dbc.util.Timed;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Stateless
@Path("/api")
public class ClassificationCheckServiceRest {
    private static final DeferredLogger LOGGER = new DeferredLogger(ClassificationCheckServiceRest.class);

    @EJB
    UpdateServiceCore updateServiceCore;

    @POST
    @Path("v2/classificationcheck")
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces(MediaType.APPLICATION_JSON)
    @Timed
    public UpdateRecordResponseDTO classificationCheck(BibliographicRecordDTO bibliographicRecordDTO) throws JSONBException {
        return LOGGER.callChecked(log -> {
            if (log.isInfoEnabled()) {
                log.info("classificationCheck - rest. Incoming: {}", new JSONBContext().marshall(bibliographicRecordDTO));
            }
            final UpdateRecordResponseDTO updateRecordResponseDTO = updateServiceCore.classificationCheck(bibliographicRecordDTO);
            if (log.isInfoEnabled()) {
                log.info("classificationCheck result is: {}", updateRecordResponseDTO.toString());
            }
            return updateRecordResponseDTO;
        });
    }
}
