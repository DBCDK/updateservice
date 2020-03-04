package dk.dbc.updateservice.rest;

import dk.dbc.updateservice.service.api.BibliographicRecord;
import dk.dbc.updateservice.service.api.RecordData;
import dk.dbc.updateservice.service.api.UpdateRecordResult;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public class UpdateServiceClient {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(UpdateServiceClient.class);
    private static final OpenUpdateServiceConnector openUpdateServiceConnector = new OpenUpdateServiceConnector();
    private static DocumentBuilder documentBuilder;
    private static boolean isReady;

    public UpdateServiceClient() {
        final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        try {
            documentBuilder = documentBuilderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean isReady() {
        try {
            // This function will be called constantly by we only need to call updateservice once. In order to limit the
            // amount of webservice requests we use a static variable to prevent more calls after the first one
            if (!isReady) {
                final UpdateRecordResult updateRecordResult = callUpdate();

                isReady = updateRecordResult.getUpdateStatus() == UpdateStatusEnum.OK;
            }

            return isReady;
        } catch (Exception e) {
            LOGGER.error("Caught exception during UpdateServiceClient", e);
            return false;
        }
    }

    private UpdateRecordResult callUpdate() throws IOException, SAXException {
        final BibliographicRecord bibliographicRecord = new BibliographicRecord();
        bibliographicRecord.setRecordSchema("info:lc/xmlns/marcxchange-v1");
        bibliographicRecord.setRecordPacking("xml");

        final String recordString = "<record xmlns=\"info:lc/xmlns/marcxchange-v1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"info:lc/xmlns/marcxchange-v1 http://www.loc.gov/standards/iso25577/marcxchange-1-1.xsd\">" +
                "                <leader>00000n    2200000   4500</leader>" +
                "                <datafield ind1=\"0\" ind2=\"0\" tag=\"001\">" +
                "                    <subfield code=\"a\">44304937</subfield>" +
                "                    <subfield code=\"b\">870970</subfield>" +
                "                    <subfield code=\"c\">20170607113521</subfield>" +
                "                    <subfield code=\"d\">20090618</subfield>" +
                "                    <subfield code=\"f\">a</subfield>" +
                "                </datafield>" +
                "                <datafield ind1=\"0\" ind2=\"0\" tag=\"002\">" +
                "                    <subfield code=\"b\">725900</subfield>" +
                "                    <subfield code=\"c\">92686132</subfield>" +
                "                    <subfield code=\"x\">71010092686132</subfield>" +
                "                </datafield>" +
                "                <datafield ind1=\"0\" ind2=\"0\" tag=\"004\">" +
                "                    <subfield code=\"r\">c</subfield>" +
                "                    <subfield code=\"a\">h</subfield>" +
                "                </datafield>" +
                "                <datafield ind1=\"0\" ind2=\"0\" tag=\"008\">" +
                "                    <subfield code=\"b\">us</subfield>" +
                "                    <subfield code=\"d\">1</subfield>" +
                "                    <subfield code=\"l\">eng</subfield>" +
                "                    <subfield code=\"v\">0</subfield>" +
                "                </datafield>" +
                "                <datafield ind1=\"0\" ind2=\"0\" tag=\"009\">" +
                "                    <subfield code=\"a\">a</subfield>" +
                "                    <subfield code=\"g\">xx</subfield>" +
                "                </datafield>" +
                "                <datafield ind1=\"0\" ind2=\"0\" tag=\"245\">" +
                "                    <subfield code=\"a\">Bleach</subfield>" +
                "                    <subfield code=\"e\">story and art by Tite Kubo</subfield>" +
                "                    <subfield code=\"e\">English adaptation Lance Caselman</subfield>" +
                "                    <subfield code=\"f\">translation Joe Yamazaki</subfield>" +
                "                </datafield>" +
                "                <datafield ind1=\"0\" ind2=\"0\" tag=\"260\">" +
                "                    <subfield code=\"a\">San Francisco, Calif.</subfield>" +
                "                    <subfield code=\"b\">VIZ Media</subfield>" +
                "                </datafield>" +
                "                <datafield ind1=\"0\" ind2=\"0\" tag=\"300\">" +
                "                    <subfield code=\"a\">bind</subfield>" +
                "                    <subfield code=\"b\">alle ill.</subfield>" +
                "                </datafield>" +
                "                <datafield ind1=\"0\" ind2=\"0\" tag=\"652\">" +
                "                    <subfield code=\"m\">83</subfield>" +
                "                </datafield>" +
                "                <datafield ind1=\"0\" ind2=\"0\" tag=\"996\">" +
                "                    <subfield code=\"a\">725900</subfield>" +
                "                </datafield>" +
                "            </record>";

        final RecordData recordData = new RecordData();
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(recordString.getBytes());
        documentBuilder.reset();

        final Document document = documentBuilder.parse(byteArrayInputStream);
        recordData.getContent().add(document.getDocumentElement());
        bibliographicRecord.setRecordData(recordData);

        return openUpdateServiceConnector.updateRecord("725900", "boghoved", bibliographicRecord, "k8s-warm-up");
    }

}
