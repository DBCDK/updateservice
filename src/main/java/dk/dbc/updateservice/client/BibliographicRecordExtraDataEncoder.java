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
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;

/**
 * Encoder to encode a BibliographicRecordExtraData to XML.
 */
public class BibliographicRecordExtraDataEncoder {
    private static final XLogger logger = XLoggerFactory.getXLogger(BibliographicRecordExtraDataEncoder.class);

    public BibliographicRecordExtraDataEncoder() {
    }

    public static Document toXmlDocument(BibliographicRecordExtraData data) throws JAXBException, ParserConfigurationException {
        logger.entry();

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
            logger.exit(result);
        }
    }

    public static String toXmlString(BibliographicRecordExtraData data) throws JAXBException, ParserConfigurationException, TransformerException {
        logger.entry();

        String result = null;
        try {
            Document document = toXmlDocument(data);

            StringWriter sw = new StringWriter();
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            transformer.transform(new DOMSource(document), new StreamResult(sw));
            return result = sw.toString();
        } finally {
            logger.exit();
        }
    }
}
