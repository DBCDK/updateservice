package dk.dbc.updateservice.dto;

import java.util.ArrayList;
import java.util.List;

public class OptionsDto {
    private List<OptionEnumDto> option;

    public List<OptionEnumDto> getOption() {
        if (option == null) {
            option = new ArrayList<>();
        }
        return option;
    }

    public void setOption(List<OptionEnumDto> option) {
        this.option = option;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        OptionsDto that = (OptionsDto) o;

        return option != null ? option.equals(that.option) : that.option == null;

    }

    @Override
    public int hashCode() {
        return option != null ? option.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "OptionsDto{" +
                "option=" + option +
                '}';
    }
}
