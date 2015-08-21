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
     * @param currentCommonRecord The record from DBC.
     * @param agencyId The library id for the library, that the extended
     *                  record will be created for.
     * 
     * @return Returns the library record after it has been updated. 
     *         <code>libraryRecord</code> may have changed.
     * @throws ScripterException
     */
    public MarcRecord createLibraryExtendedRecord( MarcRecord currentCommonRecord, MarcRecord updatingCommonRecord, int agencyId ) throws ScripterException {
        logger.entry( currentCommonRecord, updatingCommonRecord, agencyId );

        Object jsResult = null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            String jsonCurrentCommonRecord = mapper.writeValueAsString( currentCommonRecord );
            String jsonUpdatingCommonRecord = mapper.writeValueAsString( updatingCommonRecord );

            jsResult = scripter.callMethod( fileName, "createLibraryExtendedRecord", jsonCurrentCommonRecord, jsonUpdatingCommonRecord, agencyId );

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
     * @param currentCommonRecord  The record from DBC.
     * @param updatingCommonRecord The record that is being updated.
     * @param enrichmentRecord     The library extended record.
     * 
     * @return Returns the library record after it has been updated. 
     *         <code>enrichmentRecord</code> may have changed.
     * @throws ScripterException
     */
    public MarcRecord updateLibraryExtendedRecord( MarcRecord currentCommonRecord, MarcRecord updatingCommonRecord, MarcRecord enrichmentRecord ) throws ScripterException {
        logger.entry( currentCommonRecord, updatingCommonRecord, enrichmentRecord );

        Object jsResult = null;

        try {
            ObjectMapper mapper = new ObjectMapper();
            String jsonCurrentCommonRecord = mapper.writeValueAsString( currentCommonRecord );
            String jsonUpdatingCommonRecord = mapper.writeValueAsString( updatingCommonRecord );
            String jsonEnrichmentRecord = mapper.writeValueAsString( enrichmentRecord );

            jsResult = scripter.callMethod(fileName, "updateLibraryExtendedRecord", jsonCurrentCommonRecord, jsonUpdatingCommonRecord, jsonEnrichmentRecord);

            logger.trace("Result from JS ({}): {}", jsResult.getClass().getName(), jsResult);

            if (jsResult instanceof String) {
                return mapper.readValue( jsResult.toString(), MarcRecord.class );
            }

            throw new ScripterException(String.format("The JavaScript function %s must return a String value.", "updateLibraryExtendedRecord"));
        }
        catch( IOException ex ) {
            throw new ScripterException( "Error when executing JavaScript function: updateLibraryExtendedRecord", ex );
        }
        finally {
            logger.exit( jsResult );
        }
    }
    
    public MarcRecord correctLibraryExtendedRecord( MarcRecord commonRecord, MarcRecord enrichmentRecord ) throws ScripterException {
        logger.entry( commonRecord, enrichmentRecord );

        Object jsResult = null;

        try {
            ObjectMapper mapper = new ObjectMapper();
            String jsonCommonRecord = mapper.writeValueAsString( commonRecord );
            String jsonEnrichmentRecord = mapper.writeValueAsString( enrichmentRecord );

            jsResult = scripter.callMethod(fileName, "correctLibraryExtendedRecord", jsonCommonRecord, jsonEnrichmentRecord);

            logger.trace("Result from JS ({}): {}", jsResult.getClass().getName(), jsResult);

            if (jsResult instanceof String) {
                return mapper.readValue( jsResult.toString(), MarcRecord.class );
            }

            throw new ScripterException(String.format("The JavaScript function %s must return a String value.", "correctLibraryExtendedRecord"));
        }
        catch( IOException ex ) {
            throw new ScripterException( "Error when executing JavaScript function: correctLibraryExtendedRecord", ex );
        }
        finally {
            logger.exit( jsResult );
        }
    }

    public List<MarcRecord> recordDataForRawRepo( MarcRecord record, String userId, String groupId ) throws ScripterException {
        logger.entry( record );

        List<MarcRecord> result = null;
        try {
            Object jsResult;
            ObjectMapper mapper = new ObjectMapper();
            String jsonRecord = mapper.writeValueAsString( record );

            try {
                jsResult = scripter.callMethod( fileName, "recordDataForRawRepo", jsonRecord, userId, groupId );
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
