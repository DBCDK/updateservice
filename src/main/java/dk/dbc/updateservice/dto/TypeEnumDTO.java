package dk.dbc.updateservice.dto;

public enum TypeEnumDTO {
    WARNING("warning"),
    ERROR("error"),
    FATAL("fatal");
    private final String value;

    TypeEnumDTO(String v) {
        this.value = v;
    }

    public static TypeEnumDTO fromValue(String value) {
        for (TypeEnumDTO typeEnumDTO : TypeEnumDTO.values()) {
            if (typeEnumDTO.value.equals(value)) {
                return typeEnumDTO;
            }
        }
        throw new IllegalArgumentException(value);
    }

    @Override
    public String toString() {
        return "UpdateStatusTypeEnumDto{" +
                "value='" + value + '\'' +
                "} " + super.toString();
    }
}
