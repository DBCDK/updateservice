package dk.dbc.updateservice.client;

import jakarta.xml.bind.JAXB;
import javax.xml.transform.Source;

/**
 * Decoder to decode xml to a BibliographicRecordExtraData.
 */
public class BibliographicRecordExtraDataDecoder {

    private BibliographicRecordExtraDataDecoder() {

    }

    public static BibliographicRecordExtraData fromXml(Source xml) {
        return JAXB.unmarshal(xml, BibliographicRecordExtraData.class);
    }
}
