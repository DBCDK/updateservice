/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.client;

import dk.dbc.common.records.MarcConverter;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.updateservice.service.api.BibliographicRecord;
import dk.dbc.updateservice.service.api.ExtraRecordData;
import dk.dbc.updateservice.service.api.RecordData;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBException;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

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
