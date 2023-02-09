package dk.dbc.updateservice.rest;

import dk.dbc.jsonb.JSONBContext;
import dk.dbc.jsonb.JSONBException;
import dk.dbc.updateservice.dto.BibliographicRecordDTO;
import dk.dbc.updateservice.dto.UpdateRecordResponseDTO;
import dk.dbc.updateservice.update.UpdateServiceCore;
import dk.dbc.updateservice.utils.DeferredLogger;
import dk.dbc.util.Timed;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

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
