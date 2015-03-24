//-----------------------------------------------------------------------------
package dk.dbc.updateservice.ws;

//-----------------------------------------------------------------------------

import dk.dbc.updateservice.service.api.ValidateWarningOrErrorEnum;
import org.slf4j.ext.XLogger;

import java.util.HashMap;
import java.util.Objects;

//-----------------------------------------------------------------------------
/**
 * ValidationError represents an validate error.
 * <p>
 * Validation errors are returned by the JavaScript layer in the form of a json
 * string that contains an array of objects. An item in that array is represented
 * by this class.
 * 
 * <h3>Properties</h3>
 * 
 * <dl>
 *      <dt>type</dt>
 *      <dd>
 *          For historical reasons each validation error was associated with a
 *          type identifier. It was used to map the validation error with the
 *          exact class type for that kind of errors.
 *          <p>
 *          We do not used it anymore. But the JavaScript layer still returns it,
 *          so to avoid any misunderstanding, we have leaved it here. It can be 
 *          safely removed, ones the JavaScript has been refactored.
 *      </dd>
 *      <dt>params</dt>
 *      <dd>
 *          <code>params</code> is a map that contains the actual values of 
 *          the validation error.
 *          The following values are used be the web service:
 *          <dl>
 *              <dt>message</dt>
 *              <dd>
 *                  A string with a human description of what caused the error.
 *              </dd>
 *              <dt>url</dt>
 *              <dd>
 *                  A complete URL that points to the documentation of the 
 *                  field/subfield in which the error occurred.
 *              </dd>
 *              <dt>fieldno</dt>
 *              <dd>
 *                  An 0-based integer to number of field in the record where 
 *                  the error occurred. If the error can not be associated with 
 *                  a field then set it to -1 or just exclude it from 
 *                  <code>params</code>
 *              </dd>
 *              <dt>subfieldno</dt>
 *              <dd>
 *                  An 0-based integer to number the subfield in the field where 
 *                  the error occurred. If the error can not be associated with 
 *                  a subfield then set it to -1 or just exclude it from 
 *                  <code>params</code>
 *              </dd>
 *          </dl>          
 *      </dd>
 * </dl>
 * 
 * @author stp
 */
public class ValidationError {
    //-------------------------------------------------------------------------
    //              Constructors
    //-------------------------------------------------------------------------
   
    /**
     * Constructs an empty ValidationError
     * <p>
     * The properties are initialized with "empty" values.
     */
    public ValidationError() {
        this.type = ValidateWarningOrErrorEnum.ERROR;
        this.params = new HashMap<>();
    }

    //-------------------------------------------------------------------------
    //              Public
    //-------------------------------------------------------------------------

    public ValidateWarningOrErrorEnum getType() {
        return type;
    }

    public void setType( ValidateWarningOrErrorEnum type ) {
        this.type = type;
    }

    public HashMap<String, Object> getParams() {
        return params;
    }

    public void setParams(HashMap<String, Object> params) {
        this.params = params;
    }    

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

    public void writeLog( XLogger logger ) {
        if( getType() == ValidateWarningOrErrorEnum.WARNING ) {
            logger.warn( getParams().toString() );
        }
        else {
            logger.error( getParams().toString() );
        }
    }

    //-------------------------------------------------------------------------
    //              Private
    //-------------------------------------------------------------------------

    /**
     * Contains the type of this validation error.
     * 
     * For historical reasons, a type is a classification of an validation error.
     */
    private ValidateWarningOrErrorEnum type;
    
    /**
     * Map of extra parameters to the validation type.
     */
    private HashMap<String, Object> params;
}
