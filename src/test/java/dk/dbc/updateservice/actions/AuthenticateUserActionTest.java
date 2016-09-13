package dk.dbc.updateservice.actions;

import dk.dbc.updateservice.auth.AuthenticatorException;
import dk.dbc.updateservice.service.api.Authentication;
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
        Authentication authentication = new Authentication();
        authentication.setGroupIdAut("group");
        authentication.setPasswordAut("passwd");
        state.getUpdateRecordRequest().setAuthentication(authentication);
        AuthenticateUserAction authenticateUserAction = new AuthenticateUserAction(state);
        assertThat(authenticateUserAction.performAction(), equalTo(ServiceResult.newAuthErrorResult(state)));
    }

    @Test
    public void testAuthentication_GroupIsNull() throws Exception {
        Authentication authentication = new Authentication();
        authentication.setUserIdAut("user");
        authentication.setPasswordAut("passwd");
        state.getUpdateRecordRequest().setAuthentication(authentication);
        AuthenticateUserAction authenticateUserAction = new AuthenticateUserAction(state);
        assertThat(authenticateUserAction.performAction(), equalTo(ServiceResult.newAuthErrorResult(state)));
    }

    @Test
    public void testAuthentication_PasswordIsNull() throws Exception {
        Authentication authentication = new Authentication();
        authentication.setGroupIdAut("group");
        authentication.setUserIdAut("user");
        state.getUpdateRecordRequest().setAuthentication(authentication);
        AuthenticateUserAction authenticateUserAction = new AuthenticateUserAction(state);
        assertThat(authenticateUserAction.performAction(), equalTo(ServiceResult.newAuthErrorResult(state)));
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
