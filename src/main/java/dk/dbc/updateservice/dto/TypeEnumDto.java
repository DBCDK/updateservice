package dk.dbc.updateservice.dto;

public enum TypeEnumDto {
    WARNING("warning"),
    ERROR("error"),
    FATAL("fatal");
    private final String value;

    TypeEnumDto(String v) {
        this.value = v;
    }

    public static TypeEnumDto fromValue(String value) {
        for (TypeEnumDto typeEnumDto : TypeEnumDto.values()) {
            if (typeEnumDto.value.equals(value)) {
                return typeEnumDto;
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
