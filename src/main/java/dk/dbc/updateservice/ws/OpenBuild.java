/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.ws;

import dk.dbc.oss.ns.catalogingbuild.Build;
import dk.dbc.oss.ns.catalogingbuild.BuildPortType;
import dk.dbc.oss.ns.catalogingbuild.BuildRequest;
import dk.dbc.oss.ns.catalogingbuild.BuildResponse;
import dk.dbc.oss.ns.catalogingbuild.BuildResult;
import dk.dbc.updateservice.dto.BuildResponseDTO;
import dk.dbc.updateservice.update.OpenBuildCore;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.jws.WebService;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.StringWriter;

/**
 * Web service entry point for Open Build SOAP service.
 * <p/>
 * This class implements the SOAP operations for our web service.
 */
@WebService(
        serviceName = "CatalogingBuildServices",
        portName = "BuildPort",
        endpointInterface = "dk.dbc.oss.ns.catalogingbuild.BuildPortType",
        targetNamespace = "http://oss.dbc.dk/ns/catalogingBuild",
        wsdlLocation = "WEB-INF/classes/META-INF/wsdl/build/catalogingBuild.wsdl")
@Stateless
public class OpenBuild implements BuildPortType {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(OpenBuild.class);

    @EJB
    OpenBuildCore openBuildCore;

    /**
     * Build service web-service entrypoint.
     *
     * @param parameters The parameters to use for build.
     * @return BuildResult containing the new or converted template
     */
    @Override
    public BuildResult build(BuildRequest parameters) {
        LOGGER.entry();
        LOGGER.info("Build request: " + buildRequestToString(parameters));
        BuildResult result = null;
        BuildResponseDTO buildResponseDTO =  openBuildCore.build(OpenBuildRequestReader.getDTO(parameters));
        String resultOutput = buildResultToString(result);
        LOGGER.info("Build response: " + resultOutput);
        LOGGER.exit();
        return OpenBuildResultWriter.get(buildResponseDTO);
    }

    private String buildRequestToString(BuildRequest br) {
        String res;
        try {
            Build build = new Build();
            build.setBuildRequest(br);
            res = marshal(build, Build.class);
        } catch (JAXBException e) {
            LOGGER.catching(e);
            res = "<could not read input>";
        }
        return res;
    }

    private String buildResultToString(BuildResult br) {
        String res;
        try {
            BuildResponse buildResponse = new BuildResponse();
            buildResponse.setBuildResult(br);
            res = marshal(buildResponse, BuildResponse.class);
        } catch (JAXBException e) {
            LOGGER.catching(e);
            res = "<could not read output>";
        }
        return res;
    }

    private <T> String marshal(T data, Class<T> clazz) throws JAXBException {
        StringWriter stringWriter = new StringWriter();
        JAXBContext jaxbContext = JAXBContext.newInstance(clazz);
        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.marshal(data, stringWriter);
        return stringWriter.toString();
    }

}
