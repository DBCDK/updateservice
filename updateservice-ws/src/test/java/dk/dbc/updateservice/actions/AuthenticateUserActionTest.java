//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------
import dk.dbc.updateservice.auth.Authenticator;
import dk.dbc.updateservice.auth.AuthenticatorException;
import dk.dbc.updateservice.service.api.*;
import org.junit.Test;

import javax.xml.ws.WebServiceContext;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

//-----------------------------------------------------------------------------
public class AuthenticateUserActionTest {
    @Test( expected = IllegalArgumentException.class )
    public void testNullValues() throws Exception {
        AuthenticateUserAction action = new AuthenticateUserAction( null, null, null );
        action.performAction();
    }

    @Test
    public void testAuthenticationIsNull() throws Exception {
        AuthenticateUserAction action = new AuthenticateUserAction( new Authenticator(), null, mock( WebServiceContext.class ) );

        assertThat( action.performAction(), equalTo( ServiceResult.newAuthErrorResult() ) );
    }

    @Test
    public void testAuthentication_UserIsNull() throws Exception {
        Authentication auth = new Authentication();
        auth.setGroupIdAut( "group" );
        auth.setPasswordAut( "passwd" );

        AuthenticateUserAction action = new AuthenticateUserAction( new Authenticator(), auth, mock( WebServiceContext.class ) );

        assertThat( action.performAction(), equalTo( ServiceResult.newAuthErrorResult() ) );
    }

    @Test
    public void testAuthentication_GroupIsNull() throws Exception {
        Authentication auth = new Authentication();
        auth.setUserIdAut( "user" );
        auth.setPasswordAut( "passwd" );

        AuthenticateUserAction action = new AuthenticateUserAction( new Authenticator(), auth, mock( WebServiceContext.class ) );

        assertThat( action.performAction(), equalTo( ServiceResult.newAuthErrorResult() ) );
    }

    @Test
    public void testAuthentication_PasswordIsNull() throws Exception {
        Authentication auth = new Authentication();
        auth.setGroupIdAut( "group" );
        auth.setUserIdAut( "user" );

        AuthenticateUserAction action = new AuthenticateUserAction( new Authenticator(), auth, mock( WebServiceContext.class ) );

        assertThat( action.performAction(), equalTo( ServiceResult.newAuthErrorResult() ) );
    }

    @Test
    public void testAuthentication_AuthOk() throws Exception {
        Authentication auth = new Authentication();
        auth.setGroupIdAut( "group" );
        auth.setUserIdAut( "user" );
        auth.setPasswordAut( "passwd" );

        WebServiceContext wsContext = mock( WebServiceContext.class );
        Authenticator authenticator = mock( Authenticator.class );
        when( authenticator.authenticateUser( wsContext, "user", "group", "passwd" ) ).thenReturn( true );

        AuthenticateUserAction action = new AuthenticateUserAction( authenticator, auth, wsContext );

        assertThat( action.performAction(), equalTo( ServiceResult.newOkResult() ) );
    }

    @Test
    public void testAuthentication_AuthFailure() throws Exception {
        Authentication auth = new Authentication();
        auth.setGroupIdAut( "group" );
        auth.setUserIdAut( "user" );
        auth.setPasswordAut( "passwd" );

        WebServiceContext wsContext = mock( WebServiceContext.class );
        Authenticator authenticator = mock( Authenticator.class );
        when( authenticator.authenticateUser( wsContext, "user", "group", "passwd" ) ).thenReturn( false );

        AuthenticateUserAction action = new AuthenticateUserAction( authenticator, auth, wsContext );

        assertThat( action.performAction(), equalTo( ServiceResult.newAuthErrorResult() ) );
    }

    @Test
    public void testAuthentication_AuthThrowsException() throws Exception {
        Authentication auth = new Authentication();
        auth.setGroupIdAut( "group" );
        auth.setUserIdAut( "user" );
        auth.setPasswordAut( "passwd" );

        WebServiceContext wsContext = mock( WebServiceContext.class );
        Authenticator authenticator = mock( Authenticator.class );
        when( authenticator.authenticateUser( wsContext, "user", "group", "passwd" ) ).thenThrow( new AuthenticatorException( "message", null ) );

        AuthenticateUserAction action = new AuthenticateUserAction( authenticator, auth, wsContext );

        assertThat( action.performAction(), equalTo( ServiceResult.newAuthErrorResult() ) );
    }
}
