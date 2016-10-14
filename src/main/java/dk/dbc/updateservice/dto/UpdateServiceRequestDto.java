package dk.dbc.updateservice.dto;

public class UpdateServiceRequestDto {
    private AuthenticationDto authenticationDto;
    private String schemaName;
    private BibliographicRecordDto bibliographicRecordDto;
    private OptionsDto optionsDto;
    private String trackingId;
    private String doubleRecordKey;

    public AuthenticationDto getAuthenticationDto() {
        return authenticationDto;
    }

    public void setAuthenticationDto(AuthenticationDto authenticationDto) {
        this.authenticationDto = authenticationDto;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public BibliographicRecordDto getBibliographicRecordDto() {
        return bibliographicRecordDto;
    }

    public void setBibliographicRecordDto(BibliographicRecordDto bibliographicRecordDto) {
        this.bibliographicRecordDto = bibliographicRecordDto;
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

        if (authenticationDto != null ? !authenticationDto.equals(that.authenticationDto) : that.authenticationDto != null)
            return false;
        if (schemaName != null ? !schemaName.equals(that.schemaName) : that.schemaName != null) return false;
        if (bibliographicRecordDto != null ? !bibliographicRecordDto.equals(that.bibliographicRecordDto) : that.bibliographicRecordDto != null)
            return false;
        if (optionsDto != null ? !optionsDto.equals(that.optionsDto) : that.optionsDto != null) return false;
        if (trackingId != null ? !trackingId.equals(that.trackingId) : that.trackingId != null) return false;
        return doubleRecordKey != null ? doubleRecordKey.equals(that.doubleRecordKey) : that.doubleRecordKey == null;

    }

    @Override
    public int hashCode() {
        int result = authenticationDto != null ? authenticationDto.hashCode() : 0;
        result = 31 * result + (schemaName != null ? schemaName.hashCode() : 0);
        result = 31 * result + (bibliographicRecordDto != null ? bibliographicRecordDto.hashCode() : 0);
        result = 31 * result + (optionsDto != null ? optionsDto.hashCode() : 0);
        result = 31 * result + (trackingId != null ? trackingId.hashCode() : 0);
        result = 31 * result + (doubleRecordKey != null ? doubleRecordKey.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "UpdateServiceRequestDto{" +
                "authenticationDto=" + authenticationDto +
                ", schemaName='" + schemaName + '\'' +
                ", bibliographicRecordDto=" + bibliographicRecordDto +
                ", optionsDto=" + optionsDto +
                ", trackingId='" + trackingId + '\'' +
                ", doubleRecordKey='" + doubleRecordKey + '\'' +
                '}';
    }
}
