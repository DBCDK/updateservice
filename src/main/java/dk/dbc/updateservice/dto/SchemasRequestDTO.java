/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.dto;

public class SchemasRequestDTO {
    private AuthenticationDTO authenticationDTO;
    private String trackingId;

    public AuthenticationDTO getAuthenticationDTO() {
        return authenticationDTO;
    }

    public void setAuthenticationDTO(AuthenticationDTO authenticationDTO) {
        this.authenticationDTO = authenticationDTO;
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

        SchemasRequestDTO that = (SchemasRequestDTO) o;

        if (authenticationDTO != null ? !authenticationDTO.equals(that.authenticationDTO) : that.authenticationDTO != null)
            return false;
        return trackingId != null ? trackingId.equals(that.trackingId) : that.trackingId == null;

    }

    @Override
    public int hashCode() {
        int result = authenticationDTO != null ? authenticationDTO.hashCode() : 0;
        result = 31 * result + (trackingId != null ? trackingId.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "SchemasRequestDTO{" +
                "authenticationDTO=" + authenticationDTO +
                ", trackingId='" + trackingId + '\'' +
                '}';
    }
}
