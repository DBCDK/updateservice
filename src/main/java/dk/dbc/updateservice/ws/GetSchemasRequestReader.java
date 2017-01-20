package dk.dbc.updateservice.ws;

import dk.dbc.updateservice.dto.AuthenticationDTO;
import dk.dbc.updateservice.dto.SchemasRequestDTO;
import dk.dbc.updateservice.service.api.GetSchemasRequest;

public class GetSchemasRequestReader extends CommonReader {
    private SchemasRequestDTO schemasRequestDTO;

    public GetSchemasRequestReader(GetSchemasRequest getSchemasRequest) {
        schemasRequestDTO = convertRequestFromExternalFormatToInternalFormat(getSchemasRequest);
    }

    public SchemasRequestDTO getSchemasRequestDTO() {
        return schemasRequestDTO;
    }

    @SuppressWarnings("Duplicates")
    private SchemasRequestDTO convertRequestFromExternalFormatToInternalFormat(GetSchemasRequest getSchemasRequest) {
        SchemasRequestDTO schemasRequestDTO = null;
        if (getSchemasRequest != null) {
            schemasRequestDTO = new SchemasRequestDTO();
            AuthenticationDTO AuthenticationDTO = convertExternalAuthenticationToInternalAuthenticationDto(getSchemasRequest.getAuthentication());
            schemasRequestDTO.setAuthenticationDTO(AuthenticationDTO);
            schemasRequestDTO.setTrackingId(getSchemasRequest.getTrackingId());
        }
        return schemasRequestDTO;
    }
}
