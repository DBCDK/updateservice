//-----------------------------------------------------------------------------
package dk.dbc.updateservice.update;

//-----------------------------------------------------------------------------

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.javascript.ScripterException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.type.TypeFactory;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.IOException;
import java.util.List;

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

    public LibraryRecordsHandler( Scripter scripter, String fileName ) {
        this.scripter = scripter;
        this.fileName = fileName;
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
     * @throws ScripterException
     */
    public boolean hasClassificationData( MarcRecord record ) throws ScripterException {
        logger.entry( record );

        Object jsResult = null;
        try {
            try {
                ObjectMapper mapper = new ObjectMapper();
                String json = mapper.writeValueAsString( record );
                jsResult = scripter.callMethod( fileName, "hasClassificationData", json );
            }
            catch ( IOException ex ) {
                throw new ScripterException( "Error when executing JavaScript function: hasClassificationData", ex );
            }

            logger.trace("Result from JS ({}): {}", jsResult.getClass().getName(), jsResult);

            if (jsResult instanceof Boolean) {
                logger.exit();
                return ((Boolean) jsResult);
            }

            throw new ScripterException("The JavaScript function %s must return a boolean value.", "hasClassificationData");
        }
        finally {
            logger.exit(jsResult);
        }
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
     * @throws ScripterException
     */
    public boolean hasClassificationsChanged( MarcRecord oldRecord, MarcRecord newRecord ) throws ScripterException {
        logger.entry( oldRecord, newRecord );

        Object jsResult = null;
        try {
            try {
                ObjectMapper mapper = new ObjectMapper();
                String jsonOldRecord = mapper.writeValueAsString( oldRecord );
                String jsonNewRecord = mapper.writeValueAsString( newRecord );

                jsResult = scripter.callMethod(fileName, "hasClassificationsChanged", jsonOldRecord, jsonNewRecord);
            }
            catch( IOException ex ) {
                throw new ScripterException("Error when executing JavaScript function: hasClassificationsChanged", ex);
            }

            logger.trace("Result from JS ({}): {}", jsResult.getClass().getName(), jsResult);

            if (jsResult instanceof Boolean) {
                return ((Boolean) jsResult);
            }

            throw new ScripterException("The JavaScript function %s must return a boolean value.", "hasClassificationsChanged");
        }
        finally {
            logger.exit( jsResult );
        }
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
     * @throws ScripterException
     */
    public MarcRecord createLibraryExtendedRecord( MarcRecord dbcRecord, int libraryId ) throws ScripterException {
        logger.entry( dbcRecord, libraryId );

        Object jsResult = null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            String jsonRecord = mapper.writeValueAsString( dbcRecord );

            jsResult = scripter.callMethod( fileName, "createLibraryExtendedRecord", jsonRecord, libraryId );

            logger.trace("Result from JS ({}): {}", jsResult.getClass().getName(), jsResult);

            if (jsResult instanceof String) {
                return mapper.readValue( jsResult.toString(), MarcRecord.class );
            }

            throw new ScripterException(String.format("The JavaScript function %s must return a String value.", "createLibraryExtendedRecord"));
        }
        catch( IOException ex ) {
            throw new ScripterException( "Error when executing JavaScript function: createLibraryExtendedRecord", ex );
        }
        finally {
            logger.exit( jsResult );
        }
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
     * @throws ScripterException
     */
    public MarcRecord updateLibraryExtendedRecord( MarcRecord dbcRecord, MarcRecord libraryRecord ) throws ScripterException {
        logger.entry( dbcRecord, libraryRecord );

        Object jsResult = null;

        try {
            ObjectMapper mapper = new ObjectMapper();
            String jsonDbcRecord = mapper.writeValueAsString( dbcRecord );
            String jsonLibraryRecord = mapper.writeValueAsString( libraryRecord );

            jsResult = scripter.callMethod(fileName, "updateLibraryExtendedRecord", jsonDbcRecord, jsonLibraryRecord);

            logger.trace("Result from JS ({}): {}", jsResult.getClass().getName(), jsResult);

            if (jsResult instanceof String) {
                return mapper.readValue( jsResult.toString(), MarcRecord.class );
            }

            throw new ScripterException(String.format("The JavaScript function %s must return a String value.", "updateLibraryExtendedRecord"));
        }
        catch( IOException ex ) {
            throw new ScripterException( "Error when executing JavaScript function: createLibraryExtendedRecord", ex );
        }
        finally {
            logger.exit( jsResult );
        }
    }
    
    public MarcRecord correctLibraryExtendedRecord( MarcRecord dbcRecord, MarcRecord libraryRecord ) throws ScripterException {
        logger.entry( dbcRecord, libraryRecord );

        Object jsResult = null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            String jsonDbcRecord = mapper.writeValueAsString( dbcRecord );
            String jsonLibraryRecord = mapper.writeValueAsString( libraryRecord );

            jsResult = scripter.callMethod(fileName, "correctLibraryExtendedRecord", jsonDbcRecord, jsonLibraryRecord);

            logger.trace("Result from JS ({}): {}", jsResult.getClass().getName(), jsResult);

            if (jsResult instanceof String) {
                return mapper.readValue( jsResult.toString(), MarcRecord.class );
            }

            throw new ScripterException( "The JavaScript function %s must return a String value.", "correctLibraryExtendedRecord" );
        }
        catch( IOException ex ) {
            throw new ScripterException( "Error when executing JavaScript function: createLibraryExtendedRecord", ex );
        }
        finally {
            logger.exit( jsResult );
        }
    }

    public List<MarcRecord> recordDataForRawRepo( MarcRecord dbcRecord, String userId, String groupId ) throws ScripterException {
        logger.entry( dbcRecord );

        List<MarcRecord> result = null;
        try {
            Object jsResult;
            ObjectMapper mapper = new ObjectMapper();
            String jsonDbcRecord = mapper.writeValueAsString( dbcRecord );

            try {
                jsResult = scripter.callMethod( fileName, "recordDataForRawRepo", jsonDbcRecord, userId, groupId );
            } catch ( IllegalStateException ex ) {
                logger.error( "Error when executing JavaScript function: recordDataForRawRepo", ex );
                jsResult = false;
            }

            logger.debug("Result from JS ({}): {}", jsResult.getClass().getName(), jsResult);

            if ( jsResult instanceof String ) {
                result = mapper.readValue( jsResult.toString(), TypeFactory.defaultInstance().constructCollectionType( List.class, MarcRecord.class ) );
                return result;
            }

            throw new ScripterException( "The JavaScript function %s must return a String value.", "recordDataForRawRepo" );
        }
        catch( IOException ex ) {
            throw new ScripterException( "Error when executing JavaScript function: changeUpdateRecordForUpdate", ex );
        }
        finally {
            logger.exit( result );
        }
    }
    
    private final XLogger logger = XLoggerFactory.getXLogger( this.getClass() );

    private Scripter scripter;
    private String fileName;
}
