/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.iscrum.records.MarcConverter;
import dk.dbc.iscrum.records.MarcRecord;

import java.io.UnsupportedEncodingException;

/**
 * Utility class to decode a record stored in the rawrepo.
 */
public class RawRepoDecoder {
    static final String ENCODING = "UTF-8";

    public RawRepoDecoder() {
    }

    public MarcRecord decodeRecord(byte[] bytes) throws UnsupportedEncodingException {
        return MarcConverter.convertFromMarcXChange(new String(bytes, ENCODING));
    }
}
