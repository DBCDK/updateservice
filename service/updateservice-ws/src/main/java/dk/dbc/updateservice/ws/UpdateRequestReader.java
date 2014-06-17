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
 *
 * @author stp
 */
public class UpdateRequestReader {
    //!\name Constructors
    //@{
    public UpdateRequestReader( UpdateRecordRequest request ) {
        logger.entry( request );
        this.request = request;
        logger.exit();
    }
    //@}
        
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
     * @brief Defines SRU constant for RecordSchema tag to accept marcXChange
     *        1.1.
     */
    private static final String RECORD_SCHEMA_MARCXCHANGE_1_1 = "info:lc/xmlns/marcxchange-v1";

    /**
     * @brief Defines SRU constant for RecordPacking tag to accept xml.
     */
    private static final String RECORD_PACKING_XML = "xml";
    private final XLogger logger = XLoggerFactory.getXLogger( this.getClass() );
    
    private final UpdateRecordRequest request;
}
