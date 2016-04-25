//-----------------------------------------------------------------------------
package dk.dbc.updateservice.rest;

//-----------------------------------------------------------------------------
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

//-----------------------------------------------------------------------------
/**
 * REST webservice to invoke a very basic JavaScript.
 */
@Stateless
@Path( "/status" )
public class StatusService {
    @GET
    @Produces( MediaType.TEXT_PLAIN )
    public Response get() throws Exception {
        logger.entry();

        try {
            return Response.ok( pool.getStatus().toString(), MediaType.TEXT_PLAIN ).build();
        }
        finally {
            logger.exit();
        }
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final XLogger logger = XLoggerFactory.getXLogger( StatusService.class );

    @SuppressWarnings( "EjbEnvironmentInspection" )
    @EJB
    ScripterPool pool;
}
