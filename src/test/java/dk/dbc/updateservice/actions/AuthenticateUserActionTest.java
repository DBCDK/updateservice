package dk.dbc.updateservice.actions;

import dk.dbc.updateservice.auth.AuthenticatorException;
import dk.dbc.updateservice.dto.AuthenticationDto;
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
        AuthenticationDto authenticationDto = new AuthenticationDto();
        authenticationDto.setGroupId("group");
        authenticationDto.setPassword("passwd");
        state.getUpdateServiceRequestDto().setAuthenticationDto(authenticationDto);
        AuthenticateUserAction authenticateUserAction = new AuthenticateUserAction(state);
        assertThat(authenticateUserAction.performAction(), equalTo(ServiceResult.newAuthErrorResult(state, "User name is missing in authentication arguments in the request")));
    }

    @Test
    public void testAuthentication_GroupIsNull() throws Exception {
        AuthenticationDto authenticationDto = new AuthenticationDto();
        authenticationDto.setUserId("user");
        authenticationDto.setPassword("passwd");
        state.getUpdateServiceRequestDto().setAuthenticationDto(authenticationDto);
        AuthenticateUserAction authenticateUserAction = new AuthenticateUserAction(state);
        assertThat(authenticateUserAction.performAction(), equalTo(ServiceResult.newAuthErrorResult(state, "Group name is missing in authentication arguments in the request")));
    }

    @Test
    public void testAuthentication_PasswordIsNull() throws Exception {
        AuthenticationDto authenticationDto = new AuthenticationDto();
        authenticationDto.setUserId("user");
        authenticationDto.setGroupId("group");
        state.getUpdateServiceRequestDto().setAuthenticationDto(authenticationDto);
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
