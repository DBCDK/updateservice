package dk.dbc.updateservice.rest;

import jakarta.ejb.Stateless;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.net.URL;
import java.util.Properties;

import static jakarta.ws.rs.core.Response.Status.SERVICE_UNAVAILABLE;

/**
 * REST webservice to invoke a very basic JavaScript.
 */
@Stateless
@Path("/")
public class StatusService {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(StatusService.class);

    @GET
    @Path("/status")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getStatus() {
        return Response.ok("ST_OK", MediaType.TEXT_PLAIN).build();
    }

    @GET
    @Path("/isready")
    @Produces(MediaType.TEXT_PLAIN)
    public Response isReady() {
        final boolean updateServiceClientReady = UpdateServiceClient.isReady();
        if (updateServiceClientReady) {
            return Response.ok("UpdateService is initialized").build();
        }
        return Response.status(SERVICE_UNAVAILABLE).build();
    }

    @GET
    @Path("/getrevision")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getRevision() {
        try {
            final URL path = StatusService.class.getClassLoader().getResource("build.properties");
            final Properties properties = new Properties();
            properties.load(path.openStream());
            if (properties.containsKey("revision")) {
                return Response.ok("Svn revision nr is " + properties.getProperty("revision"), MediaType.TEXT_PLAIN).build();
            }
            return Response.ok("Cannot resolve an svn revision number, the machine building the app might not have svn installed", MediaType.TEXT_PLAIN).build();
        } catch (Exception e) {
            LOGGER.catching(e);
            return Response.serverError().build();
        }
    }
}
