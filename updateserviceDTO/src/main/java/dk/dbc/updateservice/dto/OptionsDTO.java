/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.dto;

import java.util.ArrayList;
import java.util.List;

public class OptionsDTO {
    private List<OptionEnumDTO> option;

    public List<OptionEnumDTO> getOption() {
        if (option == null) {
            option = new ArrayList<>();
        }
        return option;
    }

    public void setOption(List<OptionEnumDTO> option) {
        this.option = option;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        OptionsDTO that = (OptionsDTO) o;

        return option != null ? option.equals(that.option) : that.option == null;

    }

    @Override
    public int hashCode() {
        return option != null ? option.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "OptionsDTO{" +
                "option=" + option +
                '}';
    }
}
