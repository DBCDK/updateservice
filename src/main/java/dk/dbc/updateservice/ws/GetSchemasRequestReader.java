package dk.dbc.updateservice.ws;

import dk.dbc.updateservice.dto.AuthenticationDTO;
import dk.dbc.updateservice.dto.SchemasRequestDto;
import dk.dbc.updateservice.service.api.GetSchemasRequest;

public class GetSchemasRequestReader extends CommonReader {
    private SchemasRequestDto schemasRequestDto;

    public GetSchemasRequestReader(GetSchemasRequest getSchemasRequest) {
        schemasRequestDto = convertRequestFromExternalFormatToInternalFormat(getSchemasRequest);
    }

    public SchemasRequestDto getSchemasRequestDto() {
        return schemasRequestDto;
    }

    @SuppressWarnings("Duplicates")
    private SchemasRequestDto convertRequestFromExternalFormatToInternalFormat(GetSchemasRequest getSchemasRequest) {
        SchemasRequestDto schemasRequestDto = null;
        if (getSchemasRequest != null) {
            schemasRequestDto = new SchemasRequestDto();
            AuthenticationDTO AuthenticationDTO = convertExternalAuthenticationToInternalAuthenticationDto(getSchemasRequest.getAuthentication());
            schemasRequestDto.setAuthenticationDTO(AuthenticationDTO);
            schemasRequestDto.setTrackingId(getSchemasRequest.getTrackingId());
        }
        return schemasRequestDto;
    }
}
