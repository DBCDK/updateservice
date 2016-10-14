package dk.dbc.updateservice.dto;

public enum UpdateStatusEnumDto {
    OK("ok"),
    FAILED("failed");
    private final String value;

    UpdateStatusEnumDto(String v) {
        this.value = v;
    }

    public static UpdateStatusEnumDto fromValue(String value) {
        for (UpdateStatusEnumDto updateStatusEnumDto : UpdateStatusEnumDto.values()) {
            if (updateStatusEnumDto.value.equals(value)) {
                return updateStatusEnumDto;
            }
        }
        throw new IllegalArgumentException(value);
    }

    @Override
    public String toString() {
        return "UpdateStatusEnumDto{" +
                "value='" + value + '\'' +
                "} " + super.toString();
    }
}
