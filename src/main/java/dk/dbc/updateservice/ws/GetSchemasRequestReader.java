/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.ws;

import dk.dbc.updateservice.dto.AuthenticationDTO;
import dk.dbc.updateservice.dto.SchemasRequestDTO;
import dk.dbc.updateservice.service.api.Authentication;
import dk.dbc.updateservice.service.api.GetSchemasRequest;

public class GetSchemasRequestReader extends CommonReader {
    private SchemasRequestDTO schemasRequestDTO;

    public GetSchemasRequestReader(GetSchemasRequest getSchemasRequest) {
        schemasRequestDTO = convertRequestFromExternalFormatToInternalFormat(getSchemasRequest);
    }

    public SchemasRequestDTO getSchemasRequestDTO() {
        return schemasRequestDTO;
    }

    public static GetSchemasRequest cloneWithoutPassword(GetSchemasRequest getSchemasRequest) {
        GetSchemasRequest res = null;
        if (getSchemasRequest != null) {
            res = new GetSchemasRequest();
            if (getSchemasRequest.getAuthentication() != null) {
                res.setAuthentication(new Authentication());
                res.getAuthentication().setGroupIdAut(getSchemasRequest.getAuthentication().getGroupIdAut());
                res.getAuthentication().setPasswordAut("***");
                res.getAuthentication().setUserIdAut(getSchemasRequest.getAuthentication().getUserIdAut());
            }
            res.setTrackingId(getSchemasRequest.getTrackingId());
        }
        return res;
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
