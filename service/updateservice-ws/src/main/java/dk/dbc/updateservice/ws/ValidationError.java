//-----------------------------------------------------------------------------
package dk.dbc.updateservice.ws;

//-----------------------------------------------------------------------------

//import dk.dbc.oss.ns.opencataloging.datacontracts.CDTOValidateErrorEntry;
//import dk.dbc.oss.ns.opencataloging.datacontracts.CDTOValidateErrorEntryCollectionRecordFieldSubFieldUnstructuredValidateError;
//import dk.dbc.oss.ns.opencataloging.datacontracts.CDTOValidateErrorEntryCollectionRecordFieldUnstructuredValidateError;
//import dk.dbc.oss.ns.opencataloging.datacontracts.CDTOValidateErrorEntryCollectionUnstructuredValidateError;
import java.util.HashMap;
import java.util.Objects;

/**
 *
 * @author stp
 */
public class ValidationError {
    //-------------------------------------------------------------------------
    //              Constructors
    //-------------------------------------------------------------------------
    
    //!\name Constructors
    //@{
    public ValidationError() {
        this.type = "";
        this.params = new HashMap<>();
    }

    //@}
    //-------------------------------------------------------------------------
    //              Public
    //-------------------------------------------------------------------------
    //!\name Properties
    //@{
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public HashMap<String, Object> getParams() {
        return params;
    }

    public void setParams(HashMap<String, Object> params) {
        this.params = params;
    }    

    //@}
    //!\name Object
    //@{
    @Override
    public int hashCode() {
        int hash = 7;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ValidationError other = (ValidationError) obj;
        if (!Objects.equals(this.type, other.type)) {
            return false;
        }
        if (!Objects.equals(this.params, other.params)) {
            return false;
        }
        return true;
    }    
    //@}
    
    /**
     * @brief Convert of this validation error to one of the validation error 
     *        types that is used in the response of the service.
     * 
     * @return 
     *      A service validation error.
     */
    /*
    public CDTOValidateErrorEntry convertToServiceError() {
        switch( this.type ) {
            case "CDTOValidationResponseSuccessEntryCollectionRecordUnstructuredValidationError": {
                return convertToRecordError();
            }            
            case "CDTOValidationResponseSuccessEntryCollectionRecordFieldUnstructuredValidationError": {
                return convertToFieldError();
            }            
            case "CDTOValidationResponseSuccessEntryCollectionRecordFieldSubFieldUnstructuredValidationError": {
                return convertToSubfieldError();
            }            
        }
        
        return null;
    }
    */

    /**
     * @brief Convert of this validation error to the one that is used for 
     *        general record errors in the response of the service.
     * 
     * @return 
     *      An instance of CDTOValidateErrorEntryCollectionUnstructuredValidateError.
     */
    /*
    public CDTOValidateErrorEntry convertToRecordError() {
        CDTOValidateErrorEntryCollectionUnstructuredValidateError err;
        err = new CDTOValidateErrorEntryCollectionUnstructuredValidateError();

        if( this.params == null ) {
            return err;
        }
        
        if( this.params.containsKey( "message" ) ) {
            err.setValidateError( params.get( "message" ).toString() );
        }

        if( this.params.containsKey( "url" ) ) {
            err.setUrlForDocumentation( params.get( "url" ).toString() );
        }
        
        return err;
    }
    */
    
    /**
     * @brief Convert of this validation field error to the one that is used for 
     *        general record errors in the response of the service.
     * 
     * @return 
     *      An instance of CDTOValidateErrorEntryCollectionRecordFieldUnstructuredValidateError.
     */
    /*
    public CDTOValidateErrorEntry convertToFieldError() {
        CDTOValidateErrorEntryCollectionRecordFieldUnstructuredValidateError err;
        err = new CDTOValidateErrorEntryCollectionRecordFieldUnstructuredValidateError();

        if( this.params == null ) {
            return err;
        }
        
        if( this.params.containsKey( "message" ) ) {
            err.setValidateError( params.get( "message" ).toString() );
        }

        if( this.params.containsKey( "url" ) ) {
            err.setUrlForDocumentation( params.get( "url" ).toString() );
        }
        
        if( this.params.containsKey( "fieldno" ) ) {
            err.setOrdinalPositionOfField( Double.valueOf( params.get( "fieldno" ).toString() ).longValue() );
        }

        return err;
    }
    */

    /**
     * @brief Convert of this validation subfield error to the one that is used for 
     *        general record errors in the response of the service.
     * 
     * @return 
     *      An instance of CDTOValidationResponseSuccessEntryCollectionRecordFieldUnstructuredValidationError.
     */
    /*
    public CDTOValidateErrorEntry convertToSubfieldError() {
        CDTOValidateErrorEntryCollectionRecordFieldSubFieldUnstructuredValidateError err;
        err = new CDTOValidateErrorEntryCollectionRecordFieldSubFieldUnstructuredValidateError();

        if( this.params == null ) {
            return err;
        }
        
        if( this.params.containsKey( "message" ) ) {
            err.setValidateError( params.get( "message" ).toString() );
        }

        if( this.params.containsKey( "url" ) ) {
            err.setUrlForDocumentation( params.get( "url" ).toString() );
        }
        
        if( this.params.containsKey( "fieldno" ) ) {
            err.setOrdinalPositionOfField( Double.valueOf( params.get( "fieldno" ).toString() ).longValue() );
        }

        if( this.params.containsKey( "subfieldno" ) ) {
            err.setOrdinalPositionOfSubField( Double.valueOf( params.get( "subfieldno" ).toString() ).longValue() );
        }

        return err;
    }
    */

    //-------------------------------------------------------------------------
    //              Private
    //-------------------------------------------------------------------------

    private String type;
    
    /**
     * @brief Map of extra parameters to the validation type.
     */
    private HashMap<String, Object> params;
}
