package dk.dbc.updateservice.client;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.xml.bind.JAXB;
import javax.xml.transform.Source;

/**
 * Decoder to decode xml to a BibliographicRecordExtraData.
 */
public class BibliographicRecordExtraDataDecoder {
    private static final XLogger logger = XLoggerFactory.getXLogger(BibliographicRecordExtraDataDecoder.class);

    public BibliographicRecordExtraDataDecoder() {
    }

    public static BibliographicRecordExtraData fromXml(Source xml) {
        logger.entry();

        BibliographicRecordExtraData result = null;
        try {
            return result = JAXB.unmarshal(xml, BibliographicRecordExtraData.class);
        } finally {
            logger.exit(result);
        }
    }
}
