package dk.dbc.updateservice.actions;

import dk.dbc.updateservice.auth.AuthenticatorException;
import dk.dbc.updateservice.dto.AuthenticationDTO;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

public class AuthenticateUserActionTest {
    private GlobalActionState state;

    @Before
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullValues() throws Exception {
        AuthenticateUserAction authenticateUserAction = new AuthenticateUserAction(null);
        authenticateUserAction.performAction();
    }

    @Test
    public void testAuthenticationIsNull() throws Exception {
        AuthenticateUserAction authenticateUserAction = new AuthenticateUserAction(state);
        assertThat(authenticateUserAction.performAction(), equalTo(ServiceResult.newAuthErrorResult(state)));
    }

    @Test
    public void testAuthentication_UserIsNull() throws Exception {
        AuthenticationDTO AuthenticationDTO = new AuthenticationDTO();
        AuthenticationDTO.setGroupId("group");
        AuthenticationDTO.setPassword("passwd");
        state.getUpdateServiceRequestDto().setAuthenticationDTO(AuthenticationDTO);
        AuthenticateUserAction authenticateUserAction = new AuthenticateUserAction(state);
        assertThat(authenticateUserAction.performAction(), equalTo(ServiceResult.newAuthErrorResult(state, "User name is missing in authentication arguments in the request")));
    }

    @Test
    public void testAuthentication_GroupIsNull() throws Exception {
        AuthenticationDTO AuthenticationDTO = new AuthenticationDTO();
        AuthenticationDTO.setUserId("user");
        AuthenticationDTO.setPassword("passwd");
        state.getUpdateServiceRequestDto().setAuthenticationDTO(AuthenticationDTO);
        AuthenticateUserAction authenticateUserAction = new AuthenticateUserAction(state);
        assertThat(authenticateUserAction.performAction(), equalTo(ServiceResult.newAuthErrorResult(state, "Group name is missing in authentication arguments in the request")));
    }

    @Test
    public void testAuthentication_PasswordIsNull() throws Exception {
        AuthenticationDTO AuthenticationDTO = new AuthenticationDTO();
        AuthenticationDTO.setUserId("user");
        AuthenticationDTO.setGroupId("group");
        state.getUpdateServiceRequestDto().setAuthenticationDTO(AuthenticationDTO);
        AuthenticateUserAction authenticateUserAction = new AuthenticateUserAction(state);
        assertThat(authenticateUserAction.performAction(), equalTo(ServiceResult.newAuthErrorResult(state, "Password is missing in authentication arguments in the request")));
    }

    @Test
    public void testAuthentication_AuthOk() throws Exception {
        when(state.getAuthenticator().authenticateUser(state)).thenReturn(true);
        AuthenticateUserAction authenticateUserAction = new AuthenticateUserAction(state);
        assertThat(authenticateUserAction.performAction(), equalTo(ServiceResult.newOkResult()));
    }

    @Test
    public void testAuthentication_AuthFailure() throws Exception {
        when(state.getAuthenticator().authenticateUser(state)).thenReturn(false);
        AuthenticateUserAction authenticateUserAction = new AuthenticateUserAction(state);
        assertThat(authenticateUserAction.performAction(), equalTo(ServiceResult.newAuthErrorResult(state)));
    }

    @Test
    public void testAuthentication_AuthThrowsException() throws Exception {
        when(state.getAuthenticator().authenticateUser(state)).thenThrow(new AuthenticatorException("message", null));
        AuthenticateUserAction authenticateUserAction = new AuthenticateUserAction(state);
        assertThat(authenticateUserAction.performAction(), equalTo(ServiceResult.newAuthErrorResult(state)));
    }
}
