/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dk.dbc.common.records.MarcField;

import java.util.List;

/**
 * This class is a companion to the Sink DTO class.
 *
 * Think of this as a way to keep the DTO class "jackson-free" by mixing in annotations
 * to the DTO class during runtime.
 *
 * Method implementations of a MixIn class are ignored.
 */
public abstract class MarcRecordMixIn {
    /**
     * Makes jackson runtime aware of non-default constructor.
     *
     * @param content List&lt;MarcField&gt;
     */
    @JsonCreator
    public MarcRecordMixIn( @JsonProperty( "fields" ) List<MarcField> content ) {
    }
}
