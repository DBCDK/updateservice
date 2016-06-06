package dk.dbc.updateservice.rest;

import dk.dbc.updateservice.javascript.MvsScripterPool;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * REST webservice to invoke a very basic JavaScript.
 */
@Stateless
@Path("/status")
public class StatusService {
    private static final XLogger logger = XLoggerFactory.getXLogger(StatusService.class);

    @SuppressWarnings("EjbEnvironmentInspection")
    @EJB
    MvsScripterPool scripterPool;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response get() throws Exception {
        logger.entry();
        String res = null;
        try {
            res = scripterPool.getStatus().toString();
            return Response.ok(res, MediaType.TEXT_PLAIN).build();
        } finally {
            logger.exit(res);
        }
    }
}
