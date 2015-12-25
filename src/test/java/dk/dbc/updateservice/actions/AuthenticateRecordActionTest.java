//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordFactory;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.updateservice.auth.Authenticator;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.service.api.Authentication;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.service.api.ValidateWarningOrErrorEnum;
import dk.dbc.updateservice.ws.ValidationError;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;

//-----------------------------------------------------------------------------
public class AuthenticateRecordActionTest {
    public AuthenticateRecordActionTest() {
        this.messages = ResourceBundles.getBundle( this, "actions" );
    }

    /**
     * Test AuthenticateRecordAction.performAction() for authentication of a record no
     * errors or warnings.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A record.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Authenticate the record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Return ServiceResult with status OK.
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_OK() throws Exception {
        Authenticator authenticator = mock( Authenticator.class );

        InputStream is = getClass().getResourceAsStream( BOOK_RECORD_RESOURCE );
        MarcRecord record = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );

        Authentication auth = new Authentication();
        auth.setGroupIdAut( "700400" );
        auth.setUserIdAut( "netpunkt" );

        when( authenticator.authenticateRecord( record, auth.getUserIdAut(), auth.getGroupIdAut() ) ).thenReturn( new ArrayList<ValidationError>() );

        AuthenticateRecordAction instance = new AuthenticateRecordAction( record );
        instance.setAuthenticator( authenticator );
        instance.setAuthentication( auth );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        verify( authenticator ).authenticateRecord( record, auth.getUserIdAut(), auth.getGroupIdAut() );
    }

    /**
     * Test AuthenticateRecordAction.performAction() for authentication of a record with
     * authentication errors.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A record.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Authenticate the record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Return ServiceResult with status VALIDATION_ERROR and a list of authentication errors.
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_Errors() throws Exception {
        Authenticator authenticator = mock( Authenticator.class );

        InputStream is = getClass().getResourceAsStream( BOOK_RECORD_RESOURCE );
        MarcRecord record = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );

        Authentication auth = new Authentication();
        auth.setGroupIdAut( "700400" );
        auth.setUserIdAut( "netpunkt" );

        ValidationError err = ValidationError.newError( ValidateWarningOrErrorEnum.ERROR, "error" );
        List<ValidationError> errorList = Arrays.asList( err );
        when( authenticator.authenticateRecord( record, auth.getUserIdAut(), auth.getGroupIdAut() ) ).thenReturn( errorList );

        AuthenticateRecordAction instance = new AuthenticateRecordAction( record );
        instance.setAuthenticator( authenticator );
        instance.setAuthentication( auth );

        ServiceResult expected = ServiceResult.newStatusResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR );
        expected.addEntry( err );
        assertThat( instance.performAction(), equalTo( expected ) );

        verify( authenticator ).authenticateRecord( record, auth.getUserIdAut(), auth.getGroupIdAut() );
    }

    /**
     * Test AuthenticateRecordAction.performAction() for authentication of a record with
     * authentication warnings.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A record.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Authenticate the record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Return ServiceResult with status OK and a list of authentication warnings.
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_Warnings() throws Exception {
        Authenticator authenticator = mock( Authenticator.class );

        InputStream is = getClass().getResourceAsStream( BOOK_RECORD_RESOURCE );
        MarcRecord record = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );

        Authentication auth = new Authentication();
        auth.setGroupIdAut( "700400" );
        auth.setUserIdAut( "netpunkt" );

        ValidationError warn = ValidationError.newError( ValidateWarningOrErrorEnum.WARNING, "warning" );
        List<ValidationError> warningList = Arrays.asList( warn );
        when( authenticator.authenticateRecord( record, auth.getUserIdAut(), auth.getGroupIdAut() ) ).thenReturn( warningList );

        AuthenticateRecordAction instance = new AuthenticateRecordAction( record );
        instance.setAuthenticator( authenticator );
        instance.setAuthentication( auth );

        ServiceResult expected = ServiceResult.newStatusResult( UpdateStatusEnum.OK );
        expected.addEntry( warn );
        assertThat( instance.performAction(), equalTo( expected ) );

        verify( authenticator ).authenticateRecord( record, auth.getUserIdAut(), auth.getGroupIdAut() );
    }

    /**
     * Test AuthenticateRecordAction.performAction() for authentication of a record with
     * exception handling.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          Authenticate a record.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Authenticator throws an exception.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Return ServiceResult with status FAILED_VALIDATION_INTERNAL_ERROR and a list
     *          with the exception message.
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_Exception() throws Exception {
        Authenticator authenticator = mock( Authenticator.class );

        InputStream is = getClass().getResourceAsStream( BOOK_RECORD_RESOURCE );
        MarcRecord record = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );

        Authentication auth = new Authentication();
        auth.setGroupIdAut( "700400" );
        auth.setUserIdAut( "netpunkt" );

        ScripterException ex = new ScripterException( "error" );
        when( authenticator.authenticateRecord( record, auth.getUserIdAut(), auth.getGroupIdAut() ) ).thenThrow( ex );

        AuthenticateRecordAction instance = new AuthenticateRecordAction( record );
        instance.setAuthenticator( authenticator );
        instance.setAuthentication( auth );

        String message = String.format( messages.getString( "internal.authenticate.record.error" ), ex.getMessage() );
        ServiceResult expected = ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_VALIDATION_INTERNAL_ERROR, message );
        Assert.assertThat( instance.performAction(), equalTo( expected ) );

        verify( authenticator ).authenticateRecord( record, auth.getUserIdAut(), auth.getGroupIdAut() );
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final String BOOK_RECORD_RESOURCE = "/dk/dbc/updateservice/actions/book.marc";

    private ResourceBundle messages;
}
