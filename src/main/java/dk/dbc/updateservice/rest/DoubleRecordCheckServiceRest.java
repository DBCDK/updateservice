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
public class DoubleRecordCheckServiceRest {
    private static final DeferredLogger LOGGER = new DeferredLogger(DoubleRecordCheckServiceRest.class);
    @EJB
    UpdateServiceCore updateServiceCore;

    @POST
    @Path("v2/doublerecordcheck")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Timed
    public UpdateRecordResponseDTO doubleRecordCheck(BibliographicRecordDTO bibliographicRecordDTO) throws JSONBException {
        return LOGGER.callChecked(log -> {
            if (log.isInfoEnabled()) {
                log.info("doubleRecordCheck - rest. Incoming: {}", new JSONBContext().marshall(bibliographicRecordDTO));
            }
            final UpdateRecordResponseDTO updateRecordResponseDTO = updateServiceCore.doubleRecordCheck(bibliographicRecordDTO);
            log.info("doubleRecordCheck result is: {}", updateRecordResponseDTO);
            return updateRecordResponseDTO;
        });
    }
}
