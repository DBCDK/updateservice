package dk.dbc.updateservice.dto;

public class UpdateServiceRequestDto {
    private AuthenticationDTO authenticationDTO;
    private String schemaName;
    private BibliographicRecordDTO bibliographicRecordDTO;
    private OptionsDto optionsDto;
    private String trackingId;
    private String doubleRecordKey;

    public AuthenticationDTO getAuthenticationDTO() {
        return authenticationDTO;
    }

    public void setAuthenticationDTO(AuthenticationDTO authenticationDTO) {
        this.authenticationDTO = authenticationDTO;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public BibliographicRecordDTO getBibliographicRecordDTO() {
        return bibliographicRecordDTO;
    }

    public void setBibliographicRecordDTO(BibliographicRecordDTO bibliographicRecordDTO) {
        this.bibliographicRecordDTO = bibliographicRecordDTO;
    }

    public OptionsDto getOptionsDto() {
        return optionsDto;
    }

    public void setOptionsDto(OptionsDto optionsDto) {
        this.optionsDto = optionsDto;
    }

    public String getTrackingId() {
        return trackingId;
    }

    public void setTrackingId(String trackingId) {
        this.trackingId = trackingId;
    }

    public String getDoubleRecordKey() {
        return doubleRecordKey;
    }

    public void setDoubleRecordKey(String doubleRecordKey) {
        this.doubleRecordKey = doubleRecordKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UpdateServiceRequestDto that = (UpdateServiceRequestDto) o;

        if (authenticationDTO != null ? !authenticationDTO.equals(that.authenticationDTO) : that.authenticationDTO != null)
            return false;
        if (schemaName != null ? !schemaName.equals(that.schemaName) : that.schemaName != null) return false;
        if (bibliographicRecordDTO != null ? !bibliographicRecordDTO.equals(that.bibliographicRecordDTO) : that.bibliographicRecordDTO != null)
            return false;
        if (optionsDto != null ? !optionsDto.equals(that.optionsDto) : that.optionsDto != null) return false;
        if (trackingId != null ? !trackingId.equals(that.trackingId) : that.trackingId != null) return false;
        return doubleRecordKey != null ? doubleRecordKey.equals(that.doubleRecordKey) : that.doubleRecordKey == null;

    }

    @Override
    public int hashCode() {
        int result = authenticationDTO != null ? authenticationDTO.hashCode() : 0;
        result = 31 * result + (schemaName != null ? schemaName.hashCode() : 0);
        result = 31 * result + (bibliographicRecordDTO != null ? bibliographicRecordDTO.hashCode() : 0);
        result = 31 * result + (optionsDto != null ? optionsDto.hashCode() : 0);
        result = 31 * result + (trackingId != null ? trackingId.hashCode() : 0);
        result = 31 * result + (doubleRecordKey != null ? doubleRecordKey.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "UpdateServiceRequestDto{" +
                "authenticationDTO=" + authenticationDTO +
                ", schemaName='" + schemaName + '\'' +
                ", bibliographicRecordDTO=" + bibliographicRecordDTO +
                ", optionsDto=" + optionsDto +
                ", trackingId='" + trackingId + '\'' +
                ", doubleRecordKey='" + doubleRecordKey + '\'' +
                '}';
    }
}
