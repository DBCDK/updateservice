/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.auth;

import javax.ejb.ApplicationException;

/**
 * Created by stp on 01/12/14.
 */
@ApplicationException
public class AuthenticatorException extends Exception {
    public AuthenticatorException(String message, Throwable ex) {
        super(message, ex);
    }
}
