package dk.dbc.updateservice.client;

import org.w3c.dom.Document;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Encoder to encode a BibliographicRecordExtraData to XML.
 */
public class BibliographicRecordExtraDataEncoder {

    private BibliographicRecordExtraDataEncoder() {

    }

    public static Document toXmlDocument(BibliographicRecordExtraData data) throws JAXBException, ParserConfigurationException {
        final JAXBContext jc = JAXBContext.newInstance(BibliographicRecordExtraData.class);
        final Marshaller marshaller = jc.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, BibliographicRecordExtraData.NAMESPACE);

        final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);

        final DocumentBuilder documentBuilder = dbf.newDocumentBuilder();
        final Document document = documentBuilder.newDocument();

        marshaller.marshal(data, document);

        return document;
    }

}
