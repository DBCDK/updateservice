package dk.dbc.updateservice.update;

/**
 * Exception type to report update errors back to the ws ejb.
 * <p>
 * The purpose of this exception is to convert update errors to a valid
 * SOAP response. This is done by catching this exception type in
 * UpdateService.
 *
 * @author stp
 */
public class UpdateException extends Exception {

    private static final long serialVersionUID = 1L;

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
    public UpdateException(String msg) {
        super(msg);
    }

    /**
     * Constructs an instance of <code>UpdateException</code> with the specified
     * detail message.
     *
     * @param msg   the detail message.
     * @param cause the exception that causes this exception.
     */
    public UpdateException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
