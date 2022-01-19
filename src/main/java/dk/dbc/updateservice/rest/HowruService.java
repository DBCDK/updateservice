package dk.dbc.updateservice.rest;

import dk.dbc.serviceutils.ServiceStatus;
import dk.dbc.updateservice.update.JNDIResources;

import javax.ejb.Stateless;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.util.Properties;

@Stateless
@Path("/api")
public class HowruService implements ServiceStatus {

    private final Properties settings = JNDIResources.getProperties();

    private String dependencies = null;

    private String getDependencies() {
        if (dependencies == null) {
            // Creates a string that can be parsed as JSON
            dependencies = "[" +
                    "\"" + settings.getProperty("VIPCORE_ENDPOINT") + "\"," +
                    "\"" + settings.getProperty("IDP_SERVICE_URL") + "\"" +
                    "]";
        }

        return dependencies;
    }

    @Override
    public Response howru() {
        return ServiceStatus.super.howru(getDependencies());
    }

}
