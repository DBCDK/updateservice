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
        when(state.getAuthenticator().authenticateUser(state)).thenReturn(true);
        AuthenticateUserAction authenticateUserAction = new AuthenticateUserAction(state);
        assertThat(authenticateUserAction.performAction(), is(ServiceResult.newOkResult()));
    }

    @Test
    void testAuthentication_AuthFailure() throws Exception {
        when(state.getAuthenticator().authenticateUser(state)).thenReturn(false);
        AuthenticateUserAction authenticateUserAction = new AuthenticateUserAction(state);
        assertThat(authenticateUserAction.performAction(), is(ServiceResult.newAuthErrorResult()));
    }

    @Test
    void testAuthentication_AuthThrowsException() throws Exception {
        when(state.getAuthenticator().authenticateUser(state)).thenThrow(new AuthenticatorException("message", null));
        AuthenticateUserAction authenticateUserAction = new AuthenticateUserAction(state);
        assertThat(authenticateUserAction.performAction(), is(ServiceResult.newAuthErrorResult()));
    }
}
