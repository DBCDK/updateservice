//-----------------------------------------------------------------------------
package dk.dbc.updateservice.update;

//-----------------------------------------------------------------------------
/**
 * @brief Exception type to report update errors back to the ws ejb.
 * 
 * The purpose of this exception is to convert update errors to a valid
 * SOAP response. This is done by catching this exception type in 
 * UpdateService.
 * 
 * @author stp
 */
public class UpdateException extends Exception {

    /**
     * Creates a new instance of <code>UpdateException</code> without detail
     * message.
     */
    public UpdateException() {
    }

    /**
     * Constructs an instance of <code>UpdateException</code> with the specified
     * detail message.
     *
     * @param msg the detail message.
     */
    public UpdateException( String msg ) {
        super( msg );
    }
}
