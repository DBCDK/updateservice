package dk.dbc.updateservice.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dk.dbc.marc.binding.Field;

import java.util.List;

/**
 * This class is a companion to the Sink DTO class.
 * <p>
 * Think of this as a way to keep the DTO class "jackson-free" by mixing in annotations
 * to the DTO class during runtime.
 * <p>
 * Method implementations of a MixIn class are ignored.
 */
public abstract class MarcRecordMixIn {
    /**
     * Makes jackson runtime aware of non-default constructor.
     *
     * @param content List&lt;DataField&gt;
     */
    @SuppressWarnings("PMD.UnusedFormalParameter")
    @JsonCreator
    public MarcRecordMixIn(@JsonProperty("fields") List<Field> content) {
    }
}
