//-----------------------------------------------------------------------------
package dk.dbc.updateservice.update;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcConverter;
import dk.dbc.iscrum.records.MarcRecord;

import java.io.UnsupportedEncodingException;

//-----------------------------------------------------------------------------
/**
 * Utility class to decode a record stored in the rawrepo.
 */
public class RawRepoDecoder {
    public RawRepoDecoder() {
    }

    public MarcRecord decodeRecord( byte[] bytes ) throws UnsupportedEncodingException {
        return MarcConverter.convertFromMarcXChange( new String( bytes, ENCODING ) );
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    static final String ENCODING = "UTF-8";
}
