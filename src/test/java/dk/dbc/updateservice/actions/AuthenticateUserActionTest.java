/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.updateservice.auth.AuthenticatorException;
import dk.dbc.updateservice.dto.AuthenticationDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class AuthenticateUserActionTest {
    private GlobalActionState state;

    @BeforeEach
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
    }

    @Test
    void testNullValues() {
        AuthenticateUserAction authenticateUserAction = new AuthenticateUserAction(null);
        assertThrows(IllegalArgumentException.class, authenticateUserAction::performAction);
    }

    @Test
    void testAuthenticationIsNull() throws Exception {
        AuthenticateUserAction authenticateUserAction = new AuthenticateUserAction(state);
        assertThat(authenticateUserAction.performAction(), is(ServiceResult.newAuthErrorResult()));
    }

    @Test
    void testAuthentication_UserIsNull() throws Exception {
        AuthenticationDTO AuthenticationDTO = new AuthenticationDTO();
        AuthenticationDTO.setGroupId("group");
        AuthenticationDTO.setPassword("passwd");
        state.getUpdateServiceRequestDTO().setAuthenticationDTO(AuthenticationDTO);
        AuthenticateUserAction authenticateUserAction = new AuthenticateUserAction(state);
        assertThat(authenticateUserAction.performAction(), is(ServiceResult.newAuthErrorResult("Brugernavn mangler i autentificeringsparameterne")));
    }

    @Test
    void testAuthentication_GroupIsNull() throws Exception {
        AuthenticationDTO AuthenticationDTO = new AuthenticationDTO();
        AuthenticationDTO.setUserId("user");
        AuthenticationDTO.setPassword("passwd");
        state.getUpdateServiceRequestDTO().setAuthenticationDTO(AuthenticationDTO);
        AuthenticateUserAction authenticateUserAction = new AuthenticateUserAction(state);
        assertThat(authenticateUserAction.performAction(), is(ServiceResult.newAuthErrorResult("Gruppenavn mangler i autentificeringsparameterne")));
    }

    @Test
    void testAuthentication_PasswordIsNull() throws Exception {
        AuthenticationDTO AuthenticationDTO = new AuthenticationDTO();
        AuthenticationDTO.setUserId("user");
        AuthenticationDTO.setGroupId("group");
        state.getUpdateServiceRequestDTO().setAuthenticationDTO(AuthenticationDTO);
        AuthenticateUserAction authenticateUserAction = new AuthenticateUserAction(state);
        assertThat(authenticateUserAction.performAction(), is(ServiceResult.newAuthErrorResult("Kodeord mangler i autentificeringsparameterne")));
    }

    @Test
    void testAuthentication_AuthOk() throws Exception {
        when(state.getAuthenticator().authenticateUser(any(AuthenticationDTO.class))).thenReturn(true);
        AuthenticateUserAction authenticateUserAction = new AuthenticateUserAction(state);
        assertThat(authenticateUserAction.performAction(), is(ServiceResult.newOkResult()));
    }

    @Test
    void testAuthentication_AuthFailure() throws Exception {
        when(state.getAuthenticator().authenticateUser(any(AuthenticationDTO.class))).thenReturn(false);
        AuthenticateUserAction authenticateUserAction = new AuthenticateUserAction(state);
        assertThat(authenticateUserAction.performAction(), is(ServiceResult.newAuthErrorResult()));
    }

    @Test
    void testAuthentication_AuthThrowsException() throws Exception {
        when(state.getAuthenticator().authenticateUser(any(AuthenticationDTO.class))).thenThrow(new AuthenticatorException("message", null));
        AuthenticateUserAction authenticateUserAction = new AuthenticateUserAction(state);
        assertThat(authenticateUserAction.performAction(), is(ServiceResult.newAuthErrorResult()));
    }

    @Test
    void testOverwriteAuthenticationDTO_netpunkt() throws Exception {
        final AuthenticationDTO netpunkt = new AuthenticationDTO();
        netpunkt.setUserId("netpunkt-DATAIO");
        netpunkt.setGroupId("010100");
        netpunkt.setPassword("password");

        when(state.getAuthenticator().authenticateUser(any(AuthenticationDTO.class))).thenReturn(false);
        when(state.getAuthenticator().authenticateUser(netpunkt)).thenReturn(true);

        // Test with "correct" group id
        state.getUpdateServiceRequestDTO().setAuthenticationDTO(netpunkt);
        AuthenticateUserAction authenticateUserAction = new AuthenticateUserAction(state);
        assertThat(authenticateUserAction.performAction(), is(ServiceResult.newOkResult()));

        // Test with netpunkt-DATAIO for a different group id
        final AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setUserId("netpunkt-DATAIO");
        authenticationDTO.setGroupId("710010");
        authenticationDTO.setPassword("password");

        state.getUpdateServiceRequestDTO().setAuthenticationDTO(authenticationDTO);
        assertThat(authenticateUserAction.performAction(), is(ServiceResult.newOkResult()));
    }

    @Test
    void testOverwriteAuthenticationDTO_dbc() throws Exception {
        final AuthenticationDTO dbc = new AuthenticationDTO();
        dbc.setUserId("abc");
        dbc.setGroupId("dbc");
        dbc.setPassword("password");

        when(state.getAuthenticator().authenticateUser(any(AuthenticationDTO.class))).thenReturn(false);
        when(state.getAuthenticator().authenticateUser(dbc)).thenReturn(true);

        // Test with "correct" group id
        state.getUpdateServiceRequestDTO().setAuthenticationDTO(dbc);
        AuthenticateUserAction authenticateUserAction = new AuthenticateUserAction(state);
        assertThat(authenticateUserAction.performAction(), is(ServiceResult.newOkResult()));

        // Test with dbc group id
        final AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setUserId("abc");
        authenticationDTO.setGroupId("010100");
        authenticationDTO.setPassword("password");

        state.getUpdateServiceRequestDTO().setAuthenticationDTO(authenticationDTO);
        assertThat(authenticateUserAction.performAction(), is(ServiceResult.newOkResult()));
    }
}
