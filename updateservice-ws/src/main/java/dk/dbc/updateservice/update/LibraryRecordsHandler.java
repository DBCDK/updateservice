//-----------------------------------------------------------------------------
package dk.dbc.updateservice.update;

//-----------------------------------------------------------------------------
import com.google.gson.Gson;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrumjs.ejb.JSEngine;
import dk.dbc.iscrumjs.ejb.JavaScriptException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

//-----------------------------------------------------------------------------
/**
 * Class to manipulate library records for a local library. Local records and
 * local extended records.
 * <p>
 *
 * @author stp
 */
public class LibraryRecordsHandler {
    //-------------------------------------------------------------------------
    //              Constructors
    //-------------------------------------------------------------------------

    public LibraryRecordsHandler( JSEngine jsEngine ) {
        this.jsEngine = jsEngine;
    }

    //-------------------------------------------------------------------------
    //              Library records
    //-------------------------------------------------------------------------

    /**
     * Tests if a record contains any classification data.
     *
     * @param record The record.
     *
     * @return <code>true</code> if classifications where found,
     *         <code>false</code> otherwise.
     *
     * @throws dk.dbc.iscrumjs.ejb.JavaScriptException dk.dbc.iscrumjs.ejb.JavaScriptException
     */
    public boolean hasClassificationData( MarcRecord record ) throws JavaScriptException {
        logger.entry( record );
        Object jsResult;
        try {
            Gson gson = new Gson();
            jsResult = jsEngine.callEntryPoint( "hasClassificationData", gson.toJson( record ) );
        } catch ( IllegalStateException ex ) {
            logger.error( "Error when executing JavaScript function: hasClassificationData", ex );
            jsResult = false;
        }

        logger.trace( "Result from JS ({}): {}", jsResult.getClass().getName(), jsResult );

        if ( jsResult instanceof Boolean ) {
            logger.exit();
            return ( ( Boolean ) jsResult );
        }

        throw new JavaScriptException( String.format( "The JavaScript function %s must return a boolean value.", "hasClassificationData" ) );
    }

    /**
     * Tests if the classifications has changed between 2 records.
     * <p>
     * This method is mainly used to checks for changes between 2 versions of
     * the same record.
     *
     * @param oldRecord The old record.
     * @param newRecord The new record.
     *
     * @return <code>true</code> if there is changes in the classifications,
     *         <code>false</code> otherwise.
     *
     * @throws dk.dbc.iscrumjs.ejb.JavaScriptException dk.dbc.iscrumjs.ejb.JavaScriptException
     */
    public boolean hasClassificationsChanged( MarcRecord oldRecord, MarcRecord newRecord ) throws JavaScriptException {
        logger.entry( oldRecord, newRecord );
        Object jsResult;
        try {
            Gson gson = new Gson();
            jsResult = jsEngine.callEntryPoint( "hasClassificationsChanged", gson.toJson( oldRecord ), gson.toJson( newRecord ) );
        } catch ( IllegalStateException ex ) {
            logger.error( "Error when executing JavaScript function: hasClassificationsChanged", ex );
            jsResult = false;
        }

        logger.trace( "Result from JS ({}): {}", jsResult.getClass().getName(), jsResult );

        if ( jsResult instanceof Boolean ) {
            logger.exit();
            return ( ( Boolean ) jsResult );
        }

        throw new JavaScriptException( String.format( "The JavaScript function %s must return a boolean value.", "hasClassificationsChanged" ) );
    }

    /**
     * Creates an extended library record based on the bibliographic
     * classification elements of the record from DBC
     *
     * @param dbcRecord The record from DBC.
     * @param libraryId The library id for the library, that the extended
     *                  record will be created for.
     *
     * @return Returns the library record after it has been updated.
     *         <code>libraryRecord</code> may have changed.
     * @throws dk.dbc.iscrumjs.ejb.JavaScriptException dk.dbc.iscrumjs.ejb.JavaScriptException
     */
    public MarcRecord createLibraryExtendedRecord( MarcRecord dbcRecord, int libraryId ) throws JavaScriptException {
        logger.entry( dbcRecord, libraryId );

        Object jsResult;
        Gson gson = new Gson();

        try {
            jsResult = jsEngine.callEntryPoint( "createLibraryExtendedRecord", gson.toJson( dbcRecord ), libraryId );
        } catch ( IllegalStateException ex ) {
            logger.error( "Error when executing JavaScript function: createLibraryExtendedRecord", ex );
            jsResult = false;
        }

        logger.trace( "Result from JS ({}): {}", jsResult.getClass().getName(), jsResult );

        if ( jsResult instanceof String ) {
            logger.exit();
            return ( gson.fromJson( jsResult.toString(), MarcRecord.class ) );
        }

        throw new JavaScriptException( String.format( "The JavaScript function %s must return a boolean value.", "createLibraryExtendedRecord" ) );
    }

    /**
     * This method updates an extended library record based on the bibliographic
     * classification elements of the record from DBC
     *
     * @param dbcRecord     The record from DBC.
     * @param libraryRecord The library extended record.
     *
     * @return Returns the library record after it has been updated.
     *         <code>libraryRecord</code> may have changed.
     * @throws dk.dbc.iscrumjs.ejb.JavaScriptException dk.dbc.iscrumjs.ejb.JavaScriptException
     */
    public MarcRecord updateLibraryExtendedRecord( MarcRecord dbcRecord, MarcRecord libraryRecord ) throws JavaScriptException {
        logger.entry( dbcRecord, libraryRecord );

        Object jsResult;
        Gson gson = new Gson();

        try {
            jsResult = jsEngine.callEntryPoint( "updateLibraryExtendedRecord", gson.toJson( dbcRecord ), gson.toJson( libraryRecord ) );
        } catch ( IllegalStateException ex ) {
            logger.error( "Error when executing JavaScript function: updateLibraryExtendedRecord", ex );
            jsResult = false;
        }

        logger.trace( "Result from JS ({}): {}", jsResult.getClass().getName(), jsResult );

        if ( jsResult instanceof String ) {
            logger.exit();
            return ( gson.fromJson( jsResult.toString(), MarcRecord.class ) );
        }

        throw new JavaScriptException( String.format( "The JavaScript function %s must return a boolean value.", "updateLibraryExtendedRecord" ) );
    }

    public MarcRecord correctLibraryExtendedRecord( MarcRecord dbcRecord, MarcRecord libraryRecord ) throws JavaScriptException {
        logger.entry( dbcRecord, libraryRecord );

        Object jsResult;
        Gson gson = new Gson();

        try {
            jsResult = jsEngine.callEntryPoint( "correctLibraryExtendedRecord", gson.toJson( dbcRecord ), gson.toJson( libraryRecord ) );
        } catch ( IllegalStateException ex ) {
            logger.error( "Error when executing JavaScript function: correctLibraryExtendedRecord", ex );
            jsResult = false;
        }

        logger.trace( "Result from JS ({}): {}", jsResult.getClass().getName(), jsResult );

        if ( jsResult instanceof String ) {
            logger.exit();
            return ( gson.fromJson( jsResult.toString(), MarcRecord.class ) );
        }

        throw new JavaScriptException( String.format( "The JavaScript function %s must return a boolean value.", "correctLibraryExtendedRecord" ) );
    }

    public MarcRecord updateRecordForUpdate( MarcRecord dbcRecord, String userId, String groupId ) throws JavaScriptException {
        logger.entry( dbcRecord );

        MarcRecord result = null;
        try {
            Gson gson = new Gson();
            Object jsResult;

            try {
                jsResult = jsEngine.callEntryPoint( "changeUpdateRecordForUpdate", gson.toJson( dbcRecord ), userId, groupId );
            } catch ( IllegalStateException ex ) {
                logger.error( "Error when executing JavaScript function: changeUpdateRecordForUpdate", ex );
                jsResult = false;
            }

            logger.debug("Result from JS ({}): {}", jsResult.getClass().getName(), jsResult);

            if ( jsResult instanceof String ) {
                result = ( gson.fromJson( jsResult.toString(), MarcRecord.class ) );
                return result;
            }

            throw new JavaScriptException( String.format( "The JavaScript function %s must return a boolean value.", "changeUpdateRecordForUpdate" ) );
        }
        finally {
            logger.exit( result );
        }
    }

    private final XLogger logger = XLoggerFactory.getXLogger( this.getClass() );

    private JSEngine jsEngine;
}
