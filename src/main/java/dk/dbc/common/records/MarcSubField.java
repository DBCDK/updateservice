package dk.dbc.common.records;

/**
 * This class represents a sub field in a marc record.
 */
public class MarcSubField {
    private String name;
    private String value;

    public MarcSubField() {
        this("", "");
    }

    /**
     * @param name  Name of this sub field.
     * @param value Its value.
     * Constructs a new sub field.
     */
    public MarcSubField(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public MarcSubField(MarcSubField other) {
        this.name = other.name;
        this.value = other.value;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MarcSubField that = (MarcSubField) o;

        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }
        return !(value != null ? !value.equals(that.value) : that.value != null);

    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return String.format("*%s %s", this.name, this.value);
    }

}
