/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

public enum LibraryGroup {
    DBC("dbc"), FBS("fbs"), PH("ph"), SBCI("sbci");

    private final String value;

    LibraryGroup(final String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return this.getValue();
    }

    public boolean isDBC() {
        return DBC.getValue().equals(this.getValue());
    }

    // PH is also a FBS library
    public boolean isFBS() {
        return FBS.getValue().equals(this.getValue()) ||
                PH.getValue().equals(this.getValue()) ||
                SBCI.getValue().equals(this.getValue());
    }

    public boolean isPH() {
        return PH.getValue().equals(this.getValue());
    }

    public boolean isSBCI() {
        return SBCI.getValue().equals(this.getValue());
    }
}
