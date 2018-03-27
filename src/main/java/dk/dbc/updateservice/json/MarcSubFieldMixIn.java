/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This class is a companion to the Sink DTO class.
 * <p>
 * Think of this as a way to keep the DTO class "jackson-free" by mixing in annotations
 * to the DTO class during runtime.
 * <p>
 * Method implementations of a MixIn class are ignored.
 */
public class MarcSubFieldMixIn {
    /**
     * Makes jackson runtime aware of non-default constructor.
     *
     * @param name  String
     * @param value String
     */
    @SuppressWarnings("PMD.UnusedFormalParameter")
    @JsonCreator
    public MarcSubFieldMixIn(@JsonProperty("name") String name,
                             @JsonProperty("value") String value) {
    }
}
