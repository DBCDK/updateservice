package dk.dbc.updateservice.rest;

import jakarta.ejb.Stateless;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Stateless
@Path("/api")
public class HowruService {

    @GET
    @Path("howru")
    @Produces({MediaType.APPLICATION_JSON})
    public Response howru() {
        return Response.ok().build();
    }

}
