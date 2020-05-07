package dk.dbc.updateservice.rest;

import dk.dbc.jsonb.JSONBContext;
import dk.dbc.jsonb.JSONBException;
import dk.dbc.updateservice.actions.GlobalActionState;
import dk.dbc.updateservice.dto.BuildRequestDTO;
import dk.dbc.updateservice.dto.BuildResponseDTO;
import dk.dbc.updateservice.update.OpenBuildCore;
import dk.dbc.updateservice.update.UpdateServiceCore;
import dk.dbc.util.Timed;
import javax.annotation.PostConstruct;
import javax.ejb.EJB;
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
public class OpenBuildRest {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenBuildRest.class);
    private GlobalActionState globalActionState;
    JSONBContext jsonbContext = new JSONBContext();

    @EJB
    OpenBuildCore openBuildCore;

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


    @POST
    @Path("v1/openbuildservice")
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_JSON})
    @Timed
    public String build(BuildRequestDTO buildRequestDTO) throws JSONBException {
        LOGGER.info("Incoming request is: {}", buildRequestDTO);
        if (!updateServiceCore.isServiceReady(globalActionState)) {
            LOGGER.info("Updateservice not ready yet, leaving");
            return null;
        }
        BuildResponseDTO buildResponseDTO = openBuildCore.build(buildRequestDTO);
        LOGGER.info("Build response is:{}", buildRequestDTO);
        return jsonbContext.marshall(buildResponseDTO);
    }

}
