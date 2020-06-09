/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.ws.marshall;

import dk.dbc.updateservice.service.api.GetSchemasRequest;
import dk.dbc.updateservice.service.api.ObjectFactory;
import dk.dbc.updateservice.ws.GetSchemasRequestReader;
import dk.dbc.updateservice.ws.UpdateServiceEndpoint;
import org.apache.commons.lang3.builder.RecursiveToStringStyle;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.StringWriter;

public class GetSchemasRequestMarshaller {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(GetSchemasRequestMarshaller.class);
    private static final ObjectFactory objectFactory = new ObjectFactory();
    private static JAXBContext jaxbContext = null;

    private final GetSchemasRequest getSchemasRequest;

    public GetSchemasRequestMarshaller(GetSchemasRequest getSchemasRequest) {
        this.getSchemasRequest = GetSchemasRequestReader.cloneWithoutPassword(getSchemasRequest);

        // Initializing JAXBContent is expensive so we only want to do it once
        if (jaxbContext == null) {
            try {
                jaxbContext = JAXBContext.newInstance(GetSchemasRequest.class);
            } catch (JAXBException e) {
                throw new RuntimeException("Could not create JAXBContext");
            }
        }
    }

    private String marshal() {
        try {
            final JAXBElement<GetSchemasRequest> jAXBElement = objectFactory.createGetSchemasRequest(getSchemasRequest);
            final StringWriter stringWriter = new StringWriter();
            final Marshaller marshaller = jaxbContext.createMarshaller();

            marshaller.marshal(jAXBElement, stringWriter);

            return stringWriter.toString();
        } catch (JAXBException e) {
            LOGGER.catching(e);
            LOGGER.warn(UpdateServiceEndpoint.MARSHALLING_ERROR_MSG);
            return new ReflectionToStringBuilder(getSchemasRequest, new RecursiveToStringStyle()).toString();
        }
    }

    @Override
    public String toString() {
        return marshal();
    }
}
