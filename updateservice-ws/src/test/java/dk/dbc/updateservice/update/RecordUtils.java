//-----------------------------------------------------------------------------
package dk.dbc.updateservice.update;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcConverter;
import dk.dbc.iscrum.records.MarcField;
import dk.dbc.iscrum.records.MarcReader;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.rawrepo.Record;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Calendar;

//-----------------------------------------------------------------------------
/**
 *
 * @author stp
 */
public class RecordUtils {
    public static MarcRecord createMarcRecord() {
        MarcRecord record = new MarcRecord();
        record.setFields( new ArrayList<MarcField>() );

        return record;
    }

    /*
     * Loads a MarcRecord from a file resource.
     * <p>
     * The file is assumed to be a marcxchange XML file.
     *
     * @param resName The name of the file resource.
     *
     * @return
     *      The record from the marcxchange document, if the document contains
     *      exactly one record. The function returns null otherwise.
     *
     * @throws IOException
     * @throws UnsupportedEncodingException
     */
    public static MarcRecord loadMarcRecord( String resName ) throws UnsupportedEncodingException, IOException {
        return loadMarcRecord( RecordUtils.class.getResourceAsStream( resName ) );
    }

    /*
     * Loads a MarcRecord from an InputStream of a marcxchange document.
     *
     * @param is The InputStream
     *
     * @return
     *      The record from the marcxchange document, if the document contains
     *      exactly one record. The function returns null otherwise.
     *
     * @throws IOException
     * @throws UnsupportedEncodingException
     */
    public static MarcRecord loadMarcRecord( InputStream is ) throws UnsupportedEncodingException, IOException {
        return MarcConverter.convertFromMarcXChange( IOUtils.readAll( is, "UTF-8" ) );
    }

    public static Record createRawRecord( String recId, int libraryId ) {
        Record result = new RawRepoRecordMock( recId, libraryId );
        result.setCreated( Calendar.getInstance().getTime() );
        result.setModified( result.getCreated() );
        result.setContent( null );

        return result;
    }

    public static Record createRawRecord( String recId, String libraryId ) {
        return createRawRecord( recId, Integer.parseInt( libraryId ) );
    }

    public static Record createRawRecord( MarcRecord record ) throws JAXBException, UnsupportedEncodingException {
        Updater updater = new Updater( null, null, null );
        updater.init();

        String recId = MarcReader.getRecordValue( record, "001", "a" );
        String libraryId = MarcReader.getRecordValue( record, "001", "b" );

        Record result = createRawRecord( recId, libraryId );
        result.setContent( updater.encodeRecord( record ) );

        return result;
    }

    public static Record createRawRecord( String resName ) throws JAXBException, IOException {
        return createRawRecord( loadMarcRecord( resName ) );
    }

}
