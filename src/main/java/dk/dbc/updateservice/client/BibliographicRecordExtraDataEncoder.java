/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.client;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.w3c.dom.Document;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Encoder to encode a BibliographicRecordExtraData to XML.
 */
public class BibliographicRecordExtraDataEncoder {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(BibliographicRecordExtraDataEncoder.class);

    public BibliographicRecordExtraDataEncoder() {
    }

    public static Document toXmlDocument(BibliographicRecordExtraData data) throws JAXBException, ParserConfigurationException {
        LOGGER.entry();

        Document result = null;
        try {
            JAXBContext jc = JAXBContext.newInstance(BibliographicRecordExtraData.class);
            Marshaller marshaller = jc.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, BibliographicRecordExtraData.NAMESPACE);

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);

            DocumentBuilder documentBuilder = dbf.newDocumentBuilder();
            Document document = documentBuilder.newDocument();

            marshaller.marshal(data, document);

            return result = document;
        } finally {
            LOGGER.exit(result);
        }
    }

}
