/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.rest;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

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
        final UpdateServiceClient updateServiceClient = new UpdateServiceClient();
        final boolean updateServiceClientReady = updateServiceClient.isReady();
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
