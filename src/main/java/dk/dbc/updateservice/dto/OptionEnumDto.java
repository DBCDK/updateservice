package dk.dbc.updateservice.dto;

public enum OptionEnumDto {
    VALIDATE_ONLY("VALIDATE_ONLY");

    private final String value;

    OptionEnumDto(String v) {
        this.value = v;
    }

    public String getValue() {
        return value;
    }

    public static OptionEnumDto fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (OptionEnumDto optionEnumDto : OptionEnumDto.values()) {
            if (optionEnumDto.value.equals(value.toUpperCase())) {
                return optionEnumDto;
            }
        }
        throw new IllegalArgumentException(value);
    }

    @Override
    public String toString() {
        return "OptionEnumDto{" +
                "value='" + value + '\'' +
                "} " + super.toString();
    }
}
