package dk.dbc.updateservice.ws;

import dk.dbc.updateservice.dto.AuthenticationDto;
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
        SchemasRequestDto res = null;
        if (getSchemasRequest != null) {
            res = new SchemasRequestDto();
            AuthenticationDto authenticationDto = convertExternalAuthenticationToInternalAuthenticationDto(getSchemasRequest.getAuthentication());
            res.setAuthenticationDto(authenticationDto);
            res.setTrackingId(getSchemasRequest.getTrackingId());
        }
        return res;
    }
}
