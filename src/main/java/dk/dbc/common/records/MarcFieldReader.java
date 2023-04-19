package dk.dbc.common.records;

import java.util.ArrayList;
import java.util.List;

/**
 * This class can read values from a MarcField.
 */
public class MarcFieldReader {
    private final MarcField field;

    public MarcFieldReader(MarcField field) {
        this.field = field;
    }

    /**
     * Returns the first occurrence of the value of a subfield.
     *
     * @param subfieldName Name of the subfield.
     * @return The value of the subfield if found, <code>null</code> otherwise.
     */
    public String getValue(String subfieldName) {
        for (MarcSubField sf : this.field.getSubfields()) {
            if (sf.getName().equals(subfieldName)) {
                return sf.getValue();
            }
        }

        return null;
    }

    /**
     * Returns the content of all subfields that match a subfield name.
     *
     * @param subfieldName Name of the subfield.
     * @return The values as a List that was found. An empty list is returned
     * if no subfields matches the arguments.
     */
    List<String> getValues(String subfieldName) {
        final List<String> result = new ArrayList<>();
        for (MarcSubField sf : this.field.getSubfields()) {
            if (sf.getName().equals(subfieldName)) {
                result.add(sf.getValue());
            }
        }

        return result;
    }

    /**
     * Checks if a subfield in the field contains a value.
     * <p>
     * The value of a subfield is matched with <code>equals</code>, so it will have to
     * match exactly.
     * </p>
     *
     * @param subfieldName Name of the subfield.
     * @param value        The value to check for.
     * @return <code>true</code> if a subfield with name <code>subfieldName</code> contains the value
     * <code>value</code>. <code>false</code> otherwise.
     */
    boolean hasValue(String subfieldName, String value) {
        for (MarcSubField sf : this.field.getSubfields()) {
            if (sf.getName().equals(subfieldName) && sf.getValue().equals(value)) {
                return true;
            }
        }

        return false;
    }

    public boolean hasSubfield(String subfieldName) {
        for (MarcSubField sf : this.field.getSubfields()) {
            if (sf.getName().equals(subfieldName)) {
                return true;
            }
        }

        return false;
    }

}
