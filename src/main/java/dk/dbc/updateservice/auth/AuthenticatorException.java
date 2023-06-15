package dk.dbc.updateservice.auth;

import jakarta.ejb.ApplicationException;

/**
 * Created by stp on 01/12/14.
 */
@ApplicationException
public class AuthenticatorException extends Exception {
    public AuthenticatorException(String message, Throwable ex) {
        super(message, ex);
    }
}
