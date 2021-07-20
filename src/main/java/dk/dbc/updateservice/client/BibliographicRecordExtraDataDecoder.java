/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.client;

import javax.xml.bind.JAXB;
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
