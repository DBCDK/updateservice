/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

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
        logger.entry();
        boolean res = false;
        try {
            res = scripterPool.isAllEnviromentsLoaded();
            if (res) {
                return Response.ok("All environments are initialized").build();
            }
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
