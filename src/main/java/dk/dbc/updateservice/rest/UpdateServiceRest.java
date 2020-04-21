package dk.dbc.updateservice.rest;

import dk.dbc.jsonb.JSONBException;
import dk.dbc.updateservice.actions.GlobalActionState;
import dk.dbc.updateservice.actions.ServiceResult;
import dk.dbc.updateservice.dto.UpdateServiceRequestDTO;
import dk.dbc.updateservice.service.api.UpdateRecordResult;
import dk.dbc.util.Timed;
import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.servlet.ServletContext;
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
    dk.dbc.updateservice.ws.UpdateService updateService;

    @Context
    private WebServiceContext wsContext;

    @Context
    private HttpServletRequest request;

    @Context
    private ServletContext servletContext;


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
     * @return Returns an instance of UpdateRecordResult with the status of the
     * status and result of the update.
     * @throws EJBException in the case of an error.
     */
    @POST
    @Path("v1/updateservice")
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces ({MediaType.APPLICATION_JSON})
    @Timed
    public ServiceResult updateRecord(UpdateServiceRequestDTO updateRecordRequest) {
        LOGGER.info("Incoming record is:{}",updateRecordRequest.getBibliographicRecordDTO().getRecordDataDTO().toString());
        LOGGER.info("Remote address is: {}", request.getRemoteAddr());

        ServiceResult serviceResult = null;
        try {
            if (!updateService.isServiceReady(globalActionState)) {
                LOGGER.info("Updateservice not ready yet, leaving");
                return null;
            }
            UpdateRecordResult updateRecordResult = updateService.updateRecord(updateRecordRequest, globalActionState);
            /* todo:
            Handle updaterecord result
             */
        } finally {
            LOGGER.info("Update exited with result: {}", serviceResult);
        }
        return serviceResult;
    }
}
