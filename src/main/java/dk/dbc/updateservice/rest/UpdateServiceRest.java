package dk.dbc.updateservice.rest;

import dk.dbc.updateservice.actions.GlobalActionState;
import dk.dbc.updateservice.dto.UpdateRecordResponseDTO;
import dk.dbc.updateservice.dto.UpdateServiceRequestDTO;
import dk.dbc.updateservice.update.UpdateServiceCore;
import dk.dbc.util.Timed;
import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.xml.ws.WebServiceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Stateless
@Path("/api")
public class UpdateServiceRest {
    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateServiceRest.class);
    private GlobalActionState globalActionState;

    @EJB
    UpdateServiceCore updateServiceCore;

    @Context
    private WebServiceContext wsContext;

    @Context
    private HttpServletRequest request;

    @PostConstruct
    protected void init() {
        globalActionState = new GlobalActionState();
        globalActionState.setWsContext(wsContext);
        globalActionState.setRequest(request);
    }
    /**
     * Update or validate a bibliographic record to the rawrepo.
     * <p>
     * This operation has 2 uses:
     * <ol>
     * <li>Validation of the record only.</li>
     * <li>Validation and update of the record</li>
     * </ol>
     * The actual operation is specified in the request by Options object
     *
     * @param updateRecordRequest The request.
     * @return Returns an instance of UpdateRecordResponseDTO with the status and result of the update.
     * @throws EJBException in the case of an error.
     */
    @POST
    @Path("v1/updateservice")
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces ({MediaType.APPLICATION_JSON})
    @Timed
    public UpdateRecordResponseDTO updateRecord(UpdateServiceRequestDTO updateRecordRequest) {
        LOGGER.info("Incoming record is:{}",updateRecordRequest.getBibliographicRecordDTO().getRecordDataDTO().toString());

        if (!updateServiceCore.isServiceReady(globalActionState)) {
            LOGGER.info("Updateservice not ready yet, leaving");
            return null;
        }
        UpdateRecordResponseDTO updateRecordResponseDTO = updateServiceCore.updateRecord(updateRecordRequest, globalActionState);
        LOGGER.info("UpdateRecordResult:{}", updateRecordResponseDTO);
        return updateRecordResponseDTO;
    }

}
