package dk.dbc.updateservice.dto;

public class SchemasRequestDto {
    private AuthenticationDto authenticationDto;
    private String trackingId;

    public AuthenticationDto getAuthenticationDto() {
        return authenticationDto;
    }

    public void setAuthenticationDto(AuthenticationDto authenticationDto) {
        this.authenticationDto = authenticationDto;
    }

    public String getTrackingId() {
        return trackingId;
    }

    public void setTrackingId(String trackingId) {
        this.trackingId = trackingId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SchemasRequestDto that = (SchemasRequestDto) o;

        if (authenticationDto != null ? !authenticationDto.equals(that.authenticationDto) : that.authenticationDto != null)
            return false;
        return trackingId != null ? trackingId.equals(that.trackingId) : that.trackingId == null;

    }

    @Override
    public int hashCode() {
        int result = authenticationDto != null ? authenticationDto.hashCode() : 0;
        result = 31 * result + (trackingId != null ? trackingId.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "SchemasRequestDto{" +
                "authenticationDto=" + authenticationDto +
                ", trackingId='" + trackingId + '\'' +
                '}';
    }
}
