package dk.dbc.updateservice.ws;

import dk.dbc.updateservice.dto.AuthenticationDto;
import dk.dbc.updateservice.service.api.Authentication;

public class DBCCommonReader {
    protected AuthenticationDto convertExternalAuthenticationToInternalAuthenticationDto(Authentication authentication) {
        AuthenticationDto res = null;
        if (authentication != null) {
            res = new AuthenticationDto();
            res.setGroupId(authentication.getGroupIdAut());
            res.setPassword(authentication.getPasswordAut());
            res.setUserId(authentication.getUserIdAut());
        }
        return res;
    }
}
