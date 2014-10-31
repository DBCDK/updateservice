//-----------------------------------------------------------------------------
package dk.dbc.updateservice.ws;

//-----------------------------------------------------------------------------
import dk.dbc.oss.ns.catalogingupdate.UpdateRecordResult;
import dk.dbc.oss.ns.catalogingupdate.UpdateStatusEnum;
import dk.dbc.oss.ns.catalogingupdate.ValidateEntry;
import dk.dbc.oss.ns.catalogingupdate.ValidateInstance;
import dk.dbc.oss.ns.catalogingupdate.ValidateWarningOrErrorEnum;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

//-----------------------------------------------------------------------------
/**
 * Class to generate a complete response.
 * <p>
 * Usage:
 * <pre>
 *  UpdateResponseWriter writer = new UpdateResponseWriter();
 *  writer.addValidateResults( valErrorsList );
 *  writer.setUpdateStatus( UpdateStatusEnum.VALIDATION_ERROR );
 * 
 *  UpdateRecordResult response = writer.getResponse();
 * </pre>
 * After the sequence the variable <code>response</code> will contain a 
 * complete valid response that can be returned thought the JavaEE container.
 * 
 * @author stp
 */
public class UpdateResponseWriter {
    //-------------------------------------------------------------------------
    //              Constructors
    //-------------------------------------------------------------------------

    /**
     * Basic constructor, that constructs the internal response.
     */
    public UpdateResponseWriter() {
        this.response = new UpdateRecordResult();
    }
    
    //-------------------------------------------------------------------------
    //              Properties
    //-------------------------------------------------------------------------
    
    public UpdateRecordResult getResponse() {
        return response;
    }
    
    //-------------------------------------------------------------------------
    //              Helper functions
    //-------------------------------------------------------------------------

    /**
     * Adds a list of validation errors to the response.
     * <p>
     * If the list is empty then the response is not changed. In this case the
     * function is a nop.
     * 
     * @param valErrors List of validation errors.
     */
    public void addValidateResults( List<ValidationError> valErrors ) {
        logger.entry( valErrors );

        try {
            if (!valErrors.isEmpty()) {
                ValidateInstance instance = new ValidateInstance();
                for (ValidationError err : valErrors) {
                    ValidateEntry entry = new ValidateEntry();

                    HashMap<String, Object> params = err.getParams();
                    Object value;

                    entry.setWarningOrError(ValidateWarningOrErrorEnum.ERROR);

                    value = params.get("url");
                    if (value != null) {
                        entry.setUrlForDocumentation(value.toString());
                    }

                    value = params.get("message");
                    if (value != null) {
                        entry.setMessage(value.toString());
                    }

                    value = params.get("fieldno");
                    if (value != null) {
                        entry.setOrdinalPositionOfField(new BigDecimal(value.toString()).toBigInteger());
                    }

                    value = params.get("subfieldno");
                    if (value != null) {
                        entry.setOrdinalPositionOfSubField(new BigDecimal(value.toString()).toBigInteger());
                    }

                    instance.getValidateEntry().add(entry);
                }

                this.response.setValidateInstance(instance);
            }
        }
        finally {
            logger.exit();
        }
    }
    
    /**
     * Sets the update status in the response.
     * 
     * @param value The update status.
     */
    public void setUpdateStatus( UpdateStatusEnum value ) {
        logger.entry();
        this.response.setUpdateStatus( value );
        logger.exit();
    }

    /**
     * Sets an error in the response.
     *
     * @param error The error to set in the response.
     */
    public void setError( dk.dbc.oss.ns.catalogingupdate.Error error ) {
        logger.entry();
        response.setError( error );
        logger.exit();
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    /**
     * Logger instance to write entries to the log files.
     */
    private final XLogger logger = XLoggerFactory.getXLogger( this.getClass() );
    
    /**
     * Response that each helper method writes to, to construct a complete response.
     */
    private UpdateRecordResult response;    
}
