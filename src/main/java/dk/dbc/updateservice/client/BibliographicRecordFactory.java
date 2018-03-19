package dk.dbc.updateservice.client;

import dk.dbc.common.records.MarcConverter;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordFactory;
import dk.dbc.updateservice.service.api.BibliographicRecord;
import dk.dbc.updateservice.service.api.ExtraRecordData;
import dk.dbc.updateservice.service.api.RecordData;
import dk.dbc.updateservice.utils.IOUtils;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Factory class to construct BibliographicRecord instances.
 * <p>
 * <h3>Examples</h3>
 * <p>
 * To create a <code>BibliographicRecord</code> from an input stream with an XML record and with extra data:
 * <pre>
 * <code>
 *  InputStream in = ...
 *
 *  BibliographicRecordExtraData bibliographicRecordExtraData = new BibliographicRecordExtraData();
 *  bibliographicRecordExtraData.setProviderName( "some-provider-name" );
 *
 *  BibliographicRecord bibRecord = BibliographicRecordFactory.loadResource( in, bibliographicRecordExtraData );
 * </code>
 * </pre>
 * To create a <code>BibliographicRecord</code> from a record and with extra data:
 * <pre>
 * <code>
 *  MarcRecord record = ...
 *
 *  BibliographicRecordExtraData bibliographicRecordExtraData = new BibliographicRecordExtraData();
 *  bibliographicRecordExtraData.setProviderName( "some-provider-name" );
 *
 *  BibliographicRecord bibRecord = BibliographicRecordFactory.loadResource( record, bibliographicRecordExtraData );
 *
 * </code>
 * </pre>
 */
public class BibliographicRecordFactory {
    private static final XLogger logger = XLoggerFactory.getXLogger(BibliographicRecordFactory.class);

    /**
     * Constructs a BibliographicRecord from a XML resource with no extra record data.
     * <p>
     * The resource is assumed to be a marcxchange record.
     * </p>
     *
     * @param resourceName Resource name.
     * @return A new BibliographicRecord with the record from the the resource encoded as marxxchange.
     * @throws ParserConfigurationException In case of parse error on the xml resource.
     * @throws SAXException                 In case of sax error on the xml resource.
     * @throws IOException                  In case of IO error on the xml resource.
     * @throws JAXBException                In case of an JAXB error on the xml resource.
     */
    public static BibliographicRecord loadResource(String resourceName) throws ParserConfigurationException, SAXException, IOException, JAXBException {
        return loadResource(resourceName, null);
    }

    /**
     * Constructs a BibliographicRecord from an InputStream with no extra record data.
     * <p>
     * The input stream is assumed to be a marcxchange record.
     * </p>
     *
     * @param in Input Stream.
     * @return A new BibliographicRecord with the record from the the resource encoded as marxxchange.
     * @throws ParserConfigurationException In case of parse error on the xml resource.
     * @throws SAXException                 In case of sax error on the xml resource.
     * @throws IOException                  In case of IO error on the xml resource.
     * @throws JAXBException                In case of an JAXB error on the xml resource.
     */
    public static BibliographicRecord loadResource(InputStream in) throws ParserConfigurationException, SAXException, IOException, JAXBException {
        return loadResource(in, null);
    }

    /**
     * Constructs a BibliographicRecord from a XML resource with extra record data.
     * <p>
     * The resource is assumed to be a marcxchange record.
     * </p>
     *
     * @param resourceName Resource name.
     * @param data         Extra record data.
     * @return A new BibliographicRecord with the record from the the resource encoded as marxxchange.
     * @throws ParserConfigurationException In case of parse error on the xml resource.
     * @throws SAXException                 In case of sax error on the xml resource.
     * @throws IOException                  In case of IO error on the xml resource.
     * @throws JAXBException                In case of an JAXB error on the xml resource.
     */
    public static BibliographicRecord loadResource(String resourceName, BibliographicRecordExtraData data) throws ParserConfigurationException, SAXException, IOException, JAXBException {
        return loadResource(BibliographicRecordFactory.class.getResourceAsStream(resourceName), data);
    }

    /**
     * Constructs a BibliographicRecord from a XML resource with extra record data.
     * <p>
     * The resource is assumed to be a marcxchange record.
     * </p>
     *
     * @param in   Input Stream.
     * @param data Extra record data.
     * @return A new BibliographicRecord with the record from the the resource encoded as marxxchange.
     * @throws ParserConfigurationException In case of parse error on the xml resource.
     * @throws SAXException                 In case of sax error on the xml resource.
     * @throws IOException                  In case of IO error on the xml resource.
     * @throws JAXBException                In case of an JAXB error on the xml resource.
     */
    public static BibliographicRecord loadResource(InputStream in, BibliographicRecordExtraData data) throws ParserConfigurationException, SAXException, IOException, JAXBException {
        DocumentBuilderFactory xmlFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder xmlBuilder = xmlFactory.newDocumentBuilder();
        Document doc = xmlBuilder.parse(in);

        BibliographicRecord record = new BibliographicRecord();
        record.setRecordSchema("info:lc/xmlns/marcxchange-v1");
        record.setRecordPacking("xml");

        RecordData recData = new RecordData();
        recData.getContent().add("\n");
        recData.getContent().add(doc.getDocumentElement());
        recData.getContent().add("\n");
        record.setRecordData(recData);

        record.setExtraRecordData(createExtraRecordData(data));
        return record;
    }

    /**
     * Constructs a BibliographicRecord from a resource of a record in line format.
     * <p>
     * The resource is assumed to be in line format of a record.
     * </p>
     *
     * @param resourceName Resource name.
     * @return A new BibliographicRecord with the record from the the resource encoded in line format.
     * @throws ParserConfigurationException In case of parse error on the xml resource.
     * @throws SAXException                 In case of sax error on the xml resource.
     * @throws IOException                  In case of IO error on the xml resource.
     * @throws JAXBException                In case of an JAXB error on the xml resource.
     */
    public static BibliographicRecord loadMarcRecordInLineFormat(String resourceName) throws ParserConfigurationException, SAXException, IOException, JAXBException {
        return loadMarcRecordInLineFormat(resourceName, null);
    }

    /**
     * Constructs a BibliographicRecord from a resource of a record in line format with extra record data.
     * <p>
     * The resource is assumed to be in line format of a record.
     * </p>
     *
     * @param resourceName Resource name.
     * @param data         Extra record data.
     * @return A new BibliographicRecord with the record from the the resource encoded in line format.
     * @throws ParserConfigurationException In case of parse error on the xml resource.
     * @throws SAXException                 In case of sax error on the xml resource.
     * @throws IOException                  In case of IO error on the xml resource.
     * @throws JAXBException                In case of an JAXB error on the xml resource.
     */
    public static BibliographicRecord loadMarcRecordInLineFormat(String resourceName, BibliographicRecordExtraData data) throws ParserConfigurationException, SAXException, IOException, JAXBException {
        return loadMarcRecordInLineFormat(BibliographicRecordFactory.class.getResourceAsStream(resourceName), data);
    }

    /**
     * Constructs a BibliographicRecord from a file of a record in line format.
     * <p>
     * The resource is assumed to be in line format of a record.
     * </p>
     *
     * @param file The file to use.
     * @return A new BibliographicRecord with the record from the the resource encoded in line format.
     * @throws ParserConfigurationException In case of parse error on the xml resource.
     * @throws SAXException                 In case of sax error on the xml resource.
     * @throws IOException                  In case of IO error on the xml resource.
     * @throws JAXBException                In case of an JAXB error on the xml resource.
     */
    public static BibliographicRecord loadMarcRecordInLineFormat(File file) throws ParserConfigurationException, SAXException, IOException, JAXBException {
        return loadMarcRecordInLineFormat(new FileInputStream(file), null);
    }

    /**
     * Constructs a BibliographicRecord from a file of a record in line format with extra record data.
     * <p>
     * The resource is assumed to be in line format of a record.
     * </p>
     *
     * @param file The file to use.
     * @param data Extra record data.
     * @return A new BibliographicRecord with the record from the the resource encoded in line format.
     * @throws ParserConfigurationException In case of parse error on the xml resource.
     * @throws SAXException                 In case of sax error on the xml resource.
     * @throws IOException                  In case of IO error on the xml resource.
     * @throws JAXBException                In case of an JAXB error on the xml resource.
     */
    public static BibliographicRecord loadMarcRecordInLineFormat(File file, BibliographicRecordExtraData data) throws ParserConfigurationException, SAXException, IOException, JAXBException {
        return loadMarcRecordInLineFormat(new FileInputStream(file), data);
    }

    /**
     * Constructs a BibliographicRecord from an InputStream of a record in line format with extra record data.
     * <p>
     * The input stream is assumed to be in line format of a record.
     * </p>
     *
     * @param inputStream Input Stream.
     * @param data        Extra record data.
     * @return A new BibliographicRecord with the record from the the resource encoded in line format.
     * @throws ParserConfigurationException In case of parse error on the xml resource.
     * @throws SAXException                 In case of sax error on the xml resource.
     * @throws IOException                  In case of IO error on the xml resource.
     * @throws JAXBException                In case of an JAXB error on the xml resource.
     */
    public static BibliographicRecord loadMarcRecordInLineFormat(InputStream inputStream, BibliographicRecordExtraData data) throws ParserConfigurationException, SAXException, IOException, JAXBException {
        return newMarcRecord(MarcRecordFactory.readRecord(IOUtils.readAll(inputStream, "UTF-8")), data);
    }

    /**
     * Constructs a BibliographicRecord from a MarcRecord with no extra record data.
     *
     * @param record The record to use.
     * @return A new BibliographicRecord with the record from the the resource encoded in line format.
     * @throws ParserConfigurationException In case of parse error on the xml resource.
     * @throws SAXException                 In case of sax error on the xml resource.
     * @throws IOException                  In case of IO error on the xml resource.
     * @throws JAXBException                In case of an JAXB error on the xml resource.
     */
    public static BibliographicRecord newMarcRecord(MarcRecord record) throws ParserConfigurationException, SAXException, IOException, JAXBException {
        return newMarcRecord(record, null);
    }

    /**
     * Constructs a BibliographicRecord from a MarcRecord with extra record data.
     *
     * @param record The record to use.
     * @param data   Extra record data.
     * @return A new BibliographicRecord with the record from the the resource encoded in line format.
     * @throws ParserConfigurationException In case of parse error on the xml resource.
     * @throws SAXException                 In case of sax error on the xml resource.
     * @throws IOException                  In case of IO error on the xml resource.
     * @throws JAXBException                In case of an JAXB error on the xml resource.
     */
    public static BibliographicRecord newMarcRecord(MarcRecord record, BibliographicRecordExtraData data) throws ParserConfigurationException, SAXException, IOException, JAXBException {
        BibliographicRecord bibRecord = new BibliographicRecord();
        bibRecord.setRecordSchema("info:lc/xmlns/marcxchange-v1");
        bibRecord.setRecordPacking("xml");

        RecordData recData = new RecordData();
        recData.getContent().add("\n");
        recData.getContent().add(MarcConverter.convertToMarcXChangeAsDocument(record).getDocumentElement());
        recData.getContent().add("\n");
        bibRecord.setRecordData(recData);
        bibRecord.setExtraRecordData(createExtraRecordData(data));
        return bibRecord;
    }

    /**
     * Constructs ExtraRecordData structure from some data.
     *
     * @param data The data to encode as XML.
     * @return ExtraRecordData with the extra data.
     * @throws JAXBException                In case of JAXB errors.
     * @throws ParserConfigurationException In case of parse errors.
     */
    private static ExtraRecordData createExtraRecordData(BibliographicRecordExtraData data) throws JAXBException, ParserConfigurationException {
        ExtraRecordData extraRecordData = new ExtraRecordData();
        if (data != null) {
            Document extraRecordDocument = BibliographicRecordExtraDataEncoder.toXmlDocument(data);
            extraRecordData.getContent().add("\n");
            extraRecordData.getContent().add(extraRecordDocument.getDocumentElement());
            extraRecordData.getContent().add("\n");
        }
        return extraRecordData;
    }
}
