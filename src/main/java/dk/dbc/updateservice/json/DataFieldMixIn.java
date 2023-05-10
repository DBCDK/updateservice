package dk.dbc.updateservice.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dk.dbc.marc.binding.SubField;

import java.util.List;

/**
 * This class is a companion to the Sink DTO class.
 * <p>
 * Think of this as a way to keep the DTO class "jackson-free" by mixing in annotations
 * to the DTO class during runtime.
 * <p>
 * Method implementations of a MixIn class are ignored.
 */
public class DataFieldMixIn {
    /**
     * Makes jackson runtime aware of non-default constructor.
     *
     * @param name      String
     * @param indicator String
     * @param subfields List&lt;MarcSubField&gt;
     */
    @SuppressWarnings("PMD.UnusedFormalParameter")
    @JsonCreator
    public DataFieldMixIn(@JsonProperty("name") String name,
                          @JsonProperty("indicator") String indicator,
                          @JsonProperty("subfields") List<SubField> subfields) {
    }
}
