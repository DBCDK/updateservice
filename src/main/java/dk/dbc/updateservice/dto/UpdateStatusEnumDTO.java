package dk.dbc.updateservice.dto;

public enum UpdateStatusEnumDTO {
    OK("ok"),
    FAILED("failed");
    private final String value;

    UpdateStatusEnumDTO(String v) {
        this.value = v;
    }

    public static UpdateStatusEnumDTO fromValue(String value) {
        for (UpdateStatusEnumDTO updateStatusEnumDTO : UpdateStatusEnumDTO.values()) {
            if (updateStatusEnumDTO.value.equals(value)) {
                return updateStatusEnumDTO;
            }
        }
        throw new IllegalArgumentException(value);
    }

    @Override
    public String toString() {
        return "UpdateStatusEnumDTO{" +
                "value='" + value + '\'' +
                "} " + super.toString();
    }
}
