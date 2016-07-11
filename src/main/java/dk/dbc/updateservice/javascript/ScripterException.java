package dk.dbc.updateservice.javascript;

/**
 * Created by stp on 03/12/14.
 */
public class ScripterException extends Exception {
    public ScripterException(String format, Object... args) {
        super(String.format(format, args));
    }

    public ScripterException(String message, Throwable cause) {
        super(message, cause);
    }
}
