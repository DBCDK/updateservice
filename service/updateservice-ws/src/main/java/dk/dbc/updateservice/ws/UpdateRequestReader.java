//-----------------------------------------------------------------------------
package dk.dbc.updateservice.ws;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcFactory;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.oss.ns.catalogingupdate.UpdateRecordRequest;
import java.util.ArrayList;
import java.util.List;
import javax.xml.transform.dom.DOMSource;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.w3c.dom.Node;

//-----------------------------------------------------------------------------
/**
 * Helper class to read the contents of an {@link UpdateRecordRequest}
 * <p>
 * The {@link UpdateRecordRequest} contains the arguments of an updateRecord 
 * request of the web service {@link UpdateService#updateRecord(UpdateRecordRequest)}.
 * <p>
 * This class provides helper functions to read informations from the request
 * and will include checks to ensure the information is valid. It can be used 
 * like this:
 * <pre>
 *  UpdateRecordRequest request = ...
 * 
 *  UpdateRequestReader reader = new UpdateRequestReader( request );
 *  if( !reader.isRecordSchemaValid() ) {
 *      throw EJBException( ... );
 *  }
 * </pre>
 * 
 * 
 * @author stp
 */
public class UpdateRequestReader {
    //-------------------------------------------------------------------------
    //              Constructors
    //-------------------------------------------------------------------------

    /**
     * Constructs an instance with a {@link UpdateRecordRequest}
     * 
     * @param request The request structure to read from.
     */    
    public UpdateRequestReader( UpdateRecordRequest request ) {
        logger.entry( request );
        this.request = request;
        logger.exit();
    }
        
    //-------------------------------------------------------------------------
    //              Public interface
    //-------------------------------------------------------------------------
    
    /**
     * Checks if the request contains a valid record packing.
     * <p>
     * The valid record packing is defined by the contant 
     * {@link #RECORD_PACKING_XML}
     * 
     * @return Returns <code>true</code> if the record packing is equal to 
     *         {@link #RECORD_PACKING_XML}, <code>false</code> otherwise.
     */
    public boolean isRecordPackingValid() {
        logger.entry();
        
        boolean result = false;        
        if( request != null && request.getBibliographicRecord() != null && request.getBibliographicRecord().getRecordPacking() != null ) {
            result = request.getBibliographicRecord().getRecordPacking().equals( RECORD_PACKING_XML );
        }
        else {
            logger.warn( "Unable to record packing from request: {}", request.toString() );
        }
        
        return result;
    }
    
    /**
     * Checks if the request contains a valid record scheme.
     * <p>
     * The valid record scheme is defined by the contant 
     * {@link #RECORD_SCHEMA_MARCXCHANGE_1_1}
     * 
     * @return Returns <code>true</code> if the record scheme is equal to 
     *         {@link #RECORD_SCHEMA_MARCXCHANGE_1_1}, <code>false</code> otherwise.
     */
    public boolean isRecordSchemaValid() {
        logger.entry();
        
        boolean result = false;
        if( request != null && request.getBibliographicRecord() != null && request.getBibliographicRecord().getRecordSchema() != null ) {
            result = request.getBibliographicRecord().getRecordSchema().equals( RECORD_SCHEMA_MARCXCHANGE_1_1 );
        }
        else {
            logger.warn( "Unable to record schema from request: {}", request.toString() );
        }
        
        return result;
    }

    /**
     * Reads the validation scheme, also known as the template name, of the 
     * request.
     * 
     * @return The validation scheme if it can be read from the request, the 
     *         empty string otherwise.
     */
    public String readValidateSchema() {
        logger.entry();
        
        String result = "";
        if( request != null ) {
            result = request.getValidateSchema();
        }
        else {
            logger.warn( "Unable to validate schema from request: {}", request.toString() );
        }
        
        return result;
    }

    /**
     * Reads the SRU record from the request and returns it.
     * <p>
     * If the request contains more than one record, then <code>null</code> is 
     * returned.
     * 
     * @return The found record as a {@link MarcRecord} or <code>null</code>
     *         if the can not be converted or if no records exists.
     */
    public MarcRecord readRecord() {
        logger.entry();
        MarcRecord result = null;
        List<Object> list = null;
        
        if( request != null && request.getBibliographicRecord() != null && request.getBibliographicRecord().getRecordData() != null ) {
            list = request.getBibliographicRecord().getRecordData().getContent();
        }
        else {
            logger.warn(  "Unable to read record from request: {}", request.toString() );
        }
        
        if( list != null ) {
            MarcFactory marcFactory = new MarcFactory();
            List<MarcRecord> records = new ArrayList<>();
            for ( Object o : list ) {
                if ( o instanceof Node ) {
                    records.addAll( marcFactory.createFromMarcXChange( new DOMSource( ( Node ) o ) ) );
                }
            }

            if ( records.size() == 1 ) {
                result = records.get( 0 );
            }
        }
        
        logger.exit( result );
        return result;
    }
    
    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    /**
     * Defines SRU constant for the RecordSchema tag to accept marcxchange
     * 1.1.
     */
    private static final String RECORD_SCHEMA_MARCXCHANGE_1_1 = "info:lc/xmlns/marcxchange-v1";

    /**
     * Defines SRU constant for the RecordPacking tag to accept xml.
     */
    private static final String RECORD_PACKING_XML = "xml";
    
    /**
     * Logger instance to write entries to the log files.
     */
    private final XLogger logger = XLoggerFactory.getXLogger( this.getClass() );
    
    /**
     * Request instance to read informations from.
     */
    private final UpdateRecordRequest request;
}
