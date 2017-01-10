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

import java.net.URL;
import java.util.Properties;

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
        logger.debug("mvs debug entering  isReady : ");
        logger.debug("mvs debug #0");
        logger.entry();
        boolean res = false;
        try {
            logger.debug("mvs debug Statusservice : " + scripterPool);
            logger.debug("mvs debug #1");
            res = scripterPool.isAllEnviromentsLoaded();
            logger.debug("mvs debug #2");
            logger.debug("mvs debug Statusservice : " + scripterPool);
            if (res) {
                logger.debug("mvs debug #3");
                logger.debug("mvs debug Statusservice : " + scripterPool);
                return Response.ok("All enviroments are initialized").build();
            }
            logger.debug("mvs debug #4");
            return Response.status(SERVICE_UNAVAILABLE).build();
        } finally {
            logger.exit(res);
        }
    }

    @GET
    @Path("/getrevision")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getRevision() throws Exception {
        logger.entry();
        try {
            URL path = StatusService.class.getClassLoader().getResource("build.properties");
            Properties properties = new Properties();
            properties.load(path.openStream());
            if (properties.containsKey("revision")) {
                return Response.ok("Svn revision nr is " + properties.getProperty("revision"), MediaType.TEXT_PLAIN).build();
            }
            return Response.ok("Cannot resolve an svn revision number, the machine building the app might not have svn installed", MediaType.TEXT_PLAIN).build();
        } catch (Exception e) {
            logger.catching(e);
            return Response.serverError().build();
        } finally {
            logger.exit();
        }
    }
}
