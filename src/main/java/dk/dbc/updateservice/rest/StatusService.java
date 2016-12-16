package dk.dbc.updateservice.rest;

import dk.dbc.updateservice.javascript.ScripterPool;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static javax.ws.rs.core.Response.Status.SERVICE_UNAVAILABLE;

/**
 * REST webservice to invoke a very basic JavaScript.
 */
@Stateless
@Path("/")
public class StatusService {
    private static final XLogger logger = XLoggerFactory.getXLogger(StatusService.class);

    @SuppressWarnings("EjbEnvironmentInspection")
    @EJB
    ScripterPool scripterPool;

    @GET
    @Path("/status")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getStatus() throws Exception {
        logger.entry();
        String res = null;
        try {
            res = scripterPool.getStatus().toString();
            return Response.ok(res, MediaType.TEXT_PLAIN).build();
        } finally {
            logger.exit(res);
        }
    }

    @GET
    @Path("/isready")
    @Produces(MediaType.TEXT_PLAIN)
    public Response isReady() throws Exception {
        logger.entry();
        boolean res = false;
        try {
            res = scripterPool.isAllEnviromentsLoaded();
            if (res) {
                return Response.ok("All enviroments are initialized").build();
            }
            return Response.status(SERVICE_UNAVAILABLE).build();
        } finally {
            logger.exit(res);
        }
    }
}
