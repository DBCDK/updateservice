/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;
/**
 * Exception type to report update errors back to the ws ejb.
 */
public class SolrException extends Exception {
    public SolrException() {}
    public SolrException(String msg) {
        super(msg);
    }
    public SolrException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
