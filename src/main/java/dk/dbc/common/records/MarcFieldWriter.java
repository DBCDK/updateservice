package dk.dbc.common.records;


import java.util.List;

public class MarcFieldWriter {
    private final MarcField field;

    public MarcFieldWriter(MarcField marcField) {
        this.field = marcField;
    }

    public MarcField getField() {
        return field;
    }

    public void addOrReplaceSubfield(String subfieldname, String value) {
        for (MarcSubField subfield : field.getSubfields()) {
            if (subfield.getName().equals(subfieldname)) {
                subfield.setValue(value);
                return;
            }
        }

        field.getSubfields().add(new MarcSubField(subfieldname, value));
    }

    public void removeSubfield(String subfieldName) {
        final List<MarcSubField> subfieldList = field.getSubfields();
        subfieldList.removeIf(msf -> msf.getName().equals(subfieldName));
    }

}
