/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.rest;

import dk.dbc.serviceutils.ServiceStatus;
import dk.dbc.updateservice.ws.JNDIResources;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.util.Properties;

@Stateless
@Path("/api")
public class HowruService implements ServiceStatus {

    @Resource(lookup = JNDIResources.JNDI_NAME_UPDATESERVICE)
    private Properties settings;

    private String dependencies = null;

    private String getDependencies() {
        if (dependencies == null) {
            // Creates a string that can be parsed as JSON
            dependencies = "[" +
                    "\"" + settings.getProperty(JNDIResources.OPENAGENCY_URL_KEY) + "?wsdl" + "\"" +
                    "," +
                    "\"" + settings.getProperty(JNDIResources.FORSRIGHTS_URL_KEY) + "forsrights.wsdl" + "\"" +
                    "]";
        }

        return dependencies;
    }

    @Override
    public Response howru() {
        return ServiceStatus.super.howru(getDependencies());
    }

}
