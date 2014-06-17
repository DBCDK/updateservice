//-----------------------------------------------------------------------------
package dk.dbc.updateservice.ws;

//-----------------------------------------------------------------------------

import dk.dbc.oss.ns.catalogingupdate.UpdateRecordResult;
import dk.dbc.oss.ns.catalogingupdate.UpdateStatusEnum;
import dk.dbc.oss.ns.catalogingupdate.ValidateEntry;
import dk.dbc.oss.ns.catalogingupdate.ValidateInstance;
import dk.dbc.oss.ns.catalogingupdate.ValidateWarningOrErrorEnum;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

/**
 * @brief Class to generate a complete response.
 * 
 * @author stp
 */
public class UpdateResponseWriter {
    public UpdateResponseWriter() {
        this.response = new UpdateRecordResult();
    }
    
    public UpdateRecordResult getResponse() {
        return response;
    }
    
    public void addValidateResults( List<ValidationError> valErrors ) {
        logger.entry( valErrors );
        
        if( !valErrors.isEmpty() ) {
            ValidateInstance instance = new ValidateInstance();
            for( ValidationError err : valErrors ) {
                ValidateEntry entry = new ValidateEntry();
                
                HashMap<String, Object> params = err.getParams();
                Object value;
                
                entry.setWarningOrError( ValidateWarningOrErrorEnum.ERROR );
                
                value = params.get( "url" );
                if( value != null ) {
                    entry.setUrlForDocumentation( value.toString() );
                }
                
                value = params.get( "message" );
                if( value != null ) {
                    entry.setMessage( value.toString() );
                }
                
                value = params.get( "fieldno" );
                if( value != null ) {
                    entry.setOrdinalPositionOfField( new BigDecimal( value.toString() ).toBigInteger() );
                }
                
                value = params.get( "subfieldno" );
                if( value != null ) {
                    entry.setOrdinalPositionOfSubField( new BigDecimal( value.toString() ).toBigInteger() );
                }
                
                instance.getValidateEntry().add( entry );
            }
            
            this.response.setValidateInstance( instance );
        }
        
        logger.exit();
    }
    
    public void setUpdateStatus( UpdateStatusEnum value ) {
        this.response.setUpdateStatus( value );
    }
    
    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private final XLogger logger = XLoggerFactory.getXLogger( this.getClass() );
    private UpdateRecordResult response;    
}
