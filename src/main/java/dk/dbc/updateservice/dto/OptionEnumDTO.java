package dk.dbc.updateservice.dto;

public enum OptionEnumDTO {
    VALIDATE_ONLY("VALIDATE_ONLY");

    private final String value;

    OptionEnumDTO(String v) {
        this.value = v;
    }

    public String getValue() {
        return value;
    }

    public static OptionEnumDTO fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (OptionEnumDTO optionEnumDTO : OptionEnumDTO.values()) {
            if (optionEnumDTO.value.equals(value.toUpperCase())) {
                return optionEnumDTO;
            }
        }
        throw new IllegalArgumentException(value);
    }

    @Override
    public String toString() {
        return "OptionEnumDTO{" +
                "value='" + value + '\'' +
                "} " + super.toString();
    }
}
