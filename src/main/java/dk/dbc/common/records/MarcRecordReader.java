package dk.dbc.common.records;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * This class can read values from a MarcRecord.
 */
public class MarcRecordReader {
    private static final List<String> AGENCIES_WITH_OTHER_RELATIONS = Arrays.asList("870974", "870975");
    private final MarcRecord record;

    public MarcRecordReader(MarcRecord record) {
        this.record = record;
    }

    /**
     * Returns the first occurrence of the value of a field and subfield.
     * <p>
     *
     * @param fieldName    The name of the field.
     * @param subfieldName Name of the subfield.
     * @return The value of the subfield if found, <code>null</code> otherwise.
     */
    public String getValue(String fieldName, String subfieldName) {
        String result = null;
        final List<MarcField> fields = getFieldStream(fieldName);

        for (MarcField field : fields) {
            if (field.getName().equals(fieldName)) {
                result = new MarcFieldReader(field).getValue(subfieldName);
                if (result != null) {
                    return result;
                }
            }
        }

        return result;
    }

    public boolean hasField(String fieldName) {
        final List<MarcField> fields = getFieldStream(fieldName);

        return !fields.isEmpty();
    }

    public MarcField getField(String fieldName) {
        MarcField result = null;

        final List<MarcField> fields = getFieldStream(fieldName);

        if (fields != null && !fields.isEmpty()) {
            result = fields.get(0);
        }

        return result;
    }

    public List<MarcField> getFieldAll(String fieldName) {
        return getFieldStream(fieldName);
    }

    /**
     * Private helper function for iterating over the fields in the record and only returning the matching fields
     *
     * @param fieldName Name of the fields with want to retrieve
     * @return List of fields with the fieldName
     */
    private List<MarcField> getFieldStream(String fieldName) {
        return record.getFields().stream()
                .filter(f -> fieldName.equals(f.getName()))
                .collect(Collectors.toList());
    }

    /**
     * Return true if the field and subfield exist on the record.
     * Otherwise returns false
     *
     * @param fieldName    The name of the field.
     * @param subfieldName Name of the subfield.
     * @return True if fieldName and subfieldName exists at least once in the record
     */
    public boolean hasSubfield(String fieldName, String subfieldName) {
        for (MarcField field : getFieldStream(fieldName)) {
            for (MarcSubField subfield : field.getSubfields()) {
                if (subfield.getName().equals(subfieldName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the content of all subfields that match a subfield name.
     *
     * @param fieldName    The name of the field.
     * @param subfieldName Name of the subfield.
     * @return The values as a List that was found. An empty list is returned
     * if no field or subfield matches the arguments.
     */
    public List<String> getValues(String fieldName, String subfieldName) {
        final List<String> result = new ArrayList<>();

        for (MarcField field : getFieldStream(fieldName)) {
            result.addAll(new MarcFieldReader(field).getValues(subfieldName));
        }

        return result;
    }

    /**
     * Checks if a subfield in the record contains a value.
     * <p>
     * The value of a subfield is matched with <code>equals</code>, so it will have to
     * match exactly.
     * </p>
     *
     * @param fieldName    The name of the field.
     * @param subfieldName Name of the subfield.
     * @param value        The value to check for.
     * @return <code>true</code> if a subfield with name <code>subfieldName</code> in a field
     * with <code>fieldName</code> contains the value <code>value</code>. <code>false</code>
     * otherwise.
     */
    public boolean hasValue(String fieldName, String subfieldName, String value) {
        for (MarcField field : getFieldStream(fieldName)) {
            if (new MarcFieldReader(field).hasValue(subfieldName, value)) {
                return true;
            }
        }

        return false;
    }

    public boolean matchValue(String fieldName, String subfieldName, String value) {
        for (MarcField field : getFieldStream(fieldName)) {
            for (MarcSubField subfield : field.getSubfields()) {
                if (subfield.getName().equals(subfieldName) && subfield.getValue().matches(value)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * This function looks for field and subfield with the given names, and if subfield is found then pattern matching
     * is perform. All matching subfields are returned as a list - if no matches then empty list is returned
     *
     * @param fieldName    The field name
     * @param subfieldName The subfield name
     * @param p            Pattern
     * @return List of matchers (empty if no matches)
     */
    public List<Matcher> getSubfieldValueMatchers(String fieldName, String subfieldName, Pattern p) {
        final List<Matcher> result = new ArrayList<>();

        for (MarcField field : getFieldStream(fieldName)) {
            for (MarcSubField subfield : field.getSubfields()) {
                if (subfield.getName().equals(subfieldName)) {
                    Matcher m = p.matcher(subfield.getValue());
                    if (m.find()) {
                        result.add(m);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Returns id of an alias record.
     * <p>
     * The id in <code>002a</code>.
     * </p>
     *
     * @return If an alias id is found it is returned, <code>null</code> otherwise.
     */
    public List<String> getCentralAliasIds() {
        return getValues("002", "a");
    }

    /**
     * Return a list of pairs of 002 b and c fields
     * <p>
     * Used for getting all sets of 002 b and c values from the current record
     * </p>
     *
     * @return list of pairs of 002 b and c values
     */
    public List<HashMap<String, String>> getDecentralAliasIds() {
        final List<HashMap<String, String>> result = new ArrayList<>();
        for (MarcField field : getFieldStream("002")) {
            String bValue = null;
            String cValue = null;

            for (MarcSubField subfield : field.getSubfields()) {
                if (subfield.getName().equals("b")) {
                    bValue = subfield.getValue();
                }

                if (subfield.getName().equals("c")) {
                    cValue = subfield.getValue();
                }

                if (bValue != null && cValue != null) {
                    HashMap<String, String> bcValues = new HashMap<>();
                    bcValues.put("b", bValue);
                    bcValues.put("c", cValue);

                    result.add(bcValues);
                }
            }
        }
        return result;
    }

    /**
     * Returns true if the record are owned by DBC otherwise false
     *
     * @return If content of 996*a is DBC or RET, true is returned, false otherwise.
     */
    public boolean isDBCRecord() {
        final String result = getValue("996", "a");
        if (result != null) {
            return result.equals("DBC") || result.equals("RET");
        }
        return false;
    }

    /**
     * Returns id of this record.
     * <p>
     * The id in <code>001a</code>.
     * </p>
     *
     * @return If an id is found it is returned, <code>null</code> otherwise.
     */
    public String getRecordId() {
        return getValue("001", "a");
    }

    /**
     * Returns agency id of this record.
     * <p>
     * The id in <code>001b</code>.
     * </p>
     *
     * @return If a id is found it is returned, <code>null</code> otherwise.
     */
    public String getAgencyId() {
        return getValue("001", "b");
    }

    /**
     * Returns agency id of this record.
     * <p>
     * The id in <code>001b</code>.
     * </p>
     *
     * @return If an id is found it is returned, <code>null</code> otherwise.
     */
    @Deprecated
    public Integer getAgencyIdAsInteger() {
        final String id = getAgencyId();
        return Integer.valueOf(id, 10);
    }

    /**
     * Returns agency id of this record.
     * <p>
     * The id in <code>001b</code>.
     * </p>
     *
     * @return The value of 001 *b
     */
    public int getAgencyIdAsInt() {
        int result = 0;
        final String id = getAgencyId();
        if (id != null) {
            result = Integer.valueOf(id, 10);
        }

        return result;
    }

    /**
     * Checks if this record is marked for deletion.
     * <p>
     * A deletion mark satisfies: 004r = d
     * </p>
     */
    public boolean markedForDeletion() {
        return "d".equals(getValue("004", "r"));
    }

    /**
     * Returns id of a parent record, that a record points to.
     * <p>
     * The parent id to located in <code>014 *a</code>, <code>016 *a</code> or <code>018 *a</code> depending on the record type.
     * <p>
     * If there is a field 014 either without a subfield x or if the content of subfield x is ANM
     * then the record is part of a volume/section/head structure.
     * </p>
     *
     * @return If a parent id is found it is returned, <code>null</code>
     * otherwise.
     */
    public String getParentRecordId() {
        String field014x;

        if (hasSubfield("014", "a") && getValue("014", "a") != null) {
            field014x = getValue("014", "x");

            if (field014x == null || "ANM".equals(field014x) || "DEB".equals(field014x)) {
                return getValue("014", "a");
            }
        } else if (AGENCIES_WITH_OTHER_RELATIONS.contains(getAgencyId())) {
            if (hasSubfield("016", "a")) {
                return getValue("016", "a");
            } else if (hasSubfield("018", "a")) {
                return getValue("018", "a");
            }
        }

        return null;
    }

    /**
     * If 014 *x = ANM then 014 *a record has agency 870970. If 014 *x is either missing or has another value than ANM,
     * then the record in 014 *a has the same agency as this record (001 *b)
     * <p>
     * Note: ANM = Anmeldelse = Review
     * Note: An article of type review always point to a common record
     * <p>
     * For 870974 records field 016 and 018 the parent agency id is defined by *5 (if present)
     *
     * @return The agencyId of the 014 *a record
     */
    public String getParentAgencyId() {
        String result = null;
        if (hasSubfield("014", "x") && "ANM".equals(getValue("014", "x"))) {
            result = "870970";
        } else if (AGENCIES_WITH_OTHER_RELATIONS.contains(getAgencyId())) {
            if (hasSubfield("016", "5")) {
                result = getValue("016", "5");
            } else if (hasSubfield("018", "5")) {
                result = getValue("018", "5");
            }
        }

        if (result == null) {
            result = getAgencyId();
        }

        return result;
    }

    @Deprecated
    public Integer getParentAgencyIdAsInteger() {
        final String id = getParentAgencyId();
        return Integer.valueOf(id, 10);
    }

    public int getParentAgencyIdAsInt() {
        final String id = getParentAgencyId();
        if (id != null) {
            return Integer.valueOf(id, 10);
        } else {
            return 0;
        }
    }
}
