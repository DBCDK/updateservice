package dk.dbc.common.records;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * This class represents a marc record.
 */
public class MarcRecord {
    private List<MarcField> fields;

    /**
     * Constructs a marc record.
     *
     * <p>The fields attribute is initialized to null.
     */
    public MarcRecord() {
        this(new ArrayList<>());
    }

    /**
     * Constructs a marc record.
     *
     * @param fields List of fields.
     */
    public MarcRecord(List<MarcField> fields) {
        this.fields = fields;
    }

    /**
     * Copy constructor.
     *
     * <p>Each field is deep copied to the new instance.
     */
    public MarcRecord(MarcRecord other) {
        this.fields = new ArrayList<>();
        for (MarcField field : other.fields) {
            this.fields.add(new MarcField(field));
        }
    }

    public List<MarcField> getFields() {
        return fields;
    }

    public void setFields(List<MarcField> fields) {
        this.fields = fields;
    }

    public boolean isEmpty() {
        return fields == null || fields.isEmpty();
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 79 * hash + (this.fields != null ? this.fields.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (getClass() != obj.getClass()) {
            return false;
        }

        final MarcRecord other = (MarcRecord) obj;
        return Objects.equals(this.fields, other.fields);
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();

        for (MarcField rf : this.fields) {
            result.append(rf.toString()).append("\n");
        }

        return result.toString();
    }

}
