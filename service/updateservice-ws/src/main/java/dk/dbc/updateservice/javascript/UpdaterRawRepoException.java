//-----------------------------------------------------------------------------
package dk.dbc.updateservice.javascript;

//-----------------------------------------------------------------------------
public class UpdaterRawRepoException extends Exception {
	/**
     * Creates a new instance of <code>UpdaterRawRepoException</code> without detail
     * message.
     */
    public UpdaterRawRepoException() {
    }

    /**
     * Constructs an instance of <code>UpdaterRawRepoException</code> with the specified
     * detail message.
     *
     * @param msg the detail message.
     */
    public UpdaterRawRepoException( String msg ) {
        super( msg );
    }

    /**
     * Constructs an instance of <code>UpdaterRawRepoException</code> with the specified
     * detail message.
     *
     * @param msg the detail message.
     * @param cause the exception that causes this exception.
     */
    public UpdaterRawRepoException( String msg, Throwable cause ) {
        super( msg, cause );
    }
	
}
