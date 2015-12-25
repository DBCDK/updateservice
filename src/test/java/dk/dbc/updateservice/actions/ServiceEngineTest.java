//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------

import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.service.api.ValidateWarningOrErrorEnum;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.ws.UpdateResponseWriter;
import dk.dbc.updateservice.ws.ValidationError;
import org.junit.Test;

import java.util.Arrays;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

//-----------------------------------------------------------------------------
public class ServiceEngineTest {
    @Test( expected = IllegalArgumentException.class )
    public void testExecuteAction_ActionIsNull() throws UpdateException {
        ServiceEngine instance = new ServiceEngine();

        instance.executeAction( null );
    }

    @Test( expected = UpdateException.class )
    public void testExecuteAction_ActionThrows() throws UpdateException {
        ServiceEngine instance = new ServiceEngine();

        ServiceAction action = mock( ServiceAction.class );
        when( action.performAction() ).thenThrow( new UpdateException( "error" ) );

        instance.executeAction( action );
    }

    @Test( expected = IllegalArgumentException.class )
    public void testExecuteAction_ActionReturnsNull() throws UpdateException {
        ServiceEngine instance = new ServiceEngine();

        ServiceAction action = mock( ServiceAction.class );
        when( action.performAction() ).thenReturn( null );

        instance.executeAction( action );
    }

    @Test
    public void testExecuteAction_ActionReturnsErrors() throws UpdateException {
        ServiceEngine instance = new ServiceEngine();

        ServiceAction action = mock( ServiceAction.class );
        when( action.performAction() ).thenReturn( ServiceResult.newErrorResult( UpdateStatusEnum.VALIDATION_ERROR, "error" ) );

        assertThat( instance.executeAction( action ), equalTo( ServiceResult.newErrorResult( UpdateStatusEnum.VALIDATION_ERROR, "error" ) ) );
    }

    @Test
    public void testExecuteAction_ThreeChildrenNoErrors() throws UpdateException {
        ServiceEngine instance = new ServiceEngine();

        ServiceAction root = mock( ServiceAction.class );

        ServiceAction c1 = mock( ServiceAction.class );
        when( c1.performAction() ).thenReturn( ServiceResult.newOkResult() );
        when( c1.children() ).thenReturn( null );
        when( c1.parent() ).thenReturn( root );

        ServiceAction c2 = mock( ServiceAction.class );
        when( c2.performAction() ).thenReturn( ServiceResult.newOkResult() );
        when( c2.children() ).thenReturn( null );
        when( c2.parent() ).thenReturn( root );

        ServiceAction c3 = mock( ServiceAction.class );
        when( c3.performAction() ).thenReturn( ServiceResult.newOkResult() );
        when( c3.children() ).thenReturn( null );
        when( c3.parent() ).thenReturn( root );

        when( root.performAction() ).thenReturn( ServiceResult.newOkResult() );
        when( root.children() ).thenReturn( Arrays.asList( c1, c2, c3 ) );

        assertThat( instance.executeAction( root ), equalTo( ServiceResult.newOkResult() ) );

        verify( root ).performAction();
        verify( root ).children();
        verify( c1 ).performAction();
        verify( c1 ).children();
        verify( c2 ).performAction();
        verify( c2 ).children();
        verify( c3 ).performAction();
        verify( c3 ).children();
    }

    @Test
    public void testExecuteAction_ThreeChildren_RootHasErrors() throws UpdateException {
        ServiceEngine instance = new ServiceEngine();

        ServiceAction root = mock( ServiceAction.class );

        ServiceAction c1 = mock( ServiceAction.class );
        when( c1.performAction() ).thenReturn( ServiceResult.newOkResult() );
        when( c1.children() ).thenReturn( null );
        when( c1.parent() ).thenReturn( root );

        ServiceAction c2 = mock( ServiceAction.class );
        when( c2.performAction() ).thenReturn( ServiceResult.newOkResult() );
        when( c2.children() ).thenReturn( null );
        when( c2.parent() ).thenReturn( root );

        ServiceAction c3 = mock( ServiceAction.class );
        when( c3.performAction() ).thenReturn( ServiceResult.newOkResult() );
        when( c3.children() ).thenReturn( null );
        when( c3.parent() ).thenReturn( root );

        ServiceResult err = ServiceResult.newErrorResult( UpdateStatusEnum.VALIDATION_ERROR, "error" );
        when( root.performAction() ).thenReturn( err );
        when( root.children() ).thenReturn( Arrays.asList( c1, c2, c3 ) );

        assertThat( instance.executeAction( root ), equalTo( new ServiceResult( err ) ) );

        verify( root ).performAction();
        verify( root, never() ).children();
        verify( c1, never() ).performAction();
        verify( c1, never() ).children();
        verify( c2, never() ).performAction();
        verify( c2, never() ).children();
        verify( c3, never() ).performAction();
        verify( c3, never() ).children();

    }

    @Test
    public void testExecuteAction_ThreeChildren_RootHasWarnings() throws UpdateException {
        ServiceEngine instance = new ServiceEngine();

        ServiceAction root = mock( ServiceAction.class );

        ServiceAction c1 = mock( ServiceAction.class );
        when( c1.performAction() ).thenReturn( ServiceResult.newOkResult() );
        when( c1.children() ).thenReturn( null );
        when( c1.parent() ).thenReturn( root );

        ServiceAction c2 = mock( ServiceAction.class );
        when( c2.performAction() ).thenReturn( ServiceResult.newOkResult() );
        when( c2.children() ).thenReturn( null );
        when( c2.parent() ).thenReturn( root );

        ServiceAction c3 = mock( ServiceAction.class );
        when( c3.performAction() ).thenReturn( ServiceResult.newOkResult() );
        when( c3.children() ).thenReturn( null );
        when( c3.parent() ).thenReturn( root );

        ServiceResult warn = ServiceResult.newWarningResult( UpdateStatusEnum.VALIDATE_ONLY, "warning" );
        when( root.performAction() ).thenReturn( warn );
        when( root.children() ).thenReturn( Arrays.asList( c1, c2, c3 ) );

        assertThat( instance.executeAction( root ), equalTo( new ServiceResult( warn ) ) );

        verify( root ).performAction();
        verify( root ).children();
        verify( c1 ).performAction();
        verify( c1 ).children();
        verify( c2 ).performAction();
        verify( c2 ).children();
        verify( c3 ).performAction();
        verify( c3 ).children();

    }

    @Test
    public void testExecuteAction_ThreeChildren_MiddleChildHasErrors() throws UpdateException {
        ServiceEngine instance = new ServiceEngine();

        ServiceAction root = mock( ServiceAction.class );

        ServiceAction c1 = mock( ServiceAction.class );
        when( c1.performAction() ).thenReturn( ServiceResult.newOkResult() );
        when( c1.children() ).thenReturn( null );
        when( c1.parent() ).thenReturn( root );

        ServiceResult err = ServiceResult.newErrorResult( UpdateStatusEnum.VALIDATION_ERROR, "error" );

        ServiceAction c2 = mock( ServiceAction.class );
        when( c2.performAction() ).thenReturn( err );
        when( c2.children() ).thenReturn( null );
        when( c2.parent() ).thenReturn( root );

        ServiceAction c3 = mock( ServiceAction.class );
        when( c3.performAction() ).thenReturn( ServiceResult.newOkResult() );
        when( c3.children() ).thenReturn( null );
        when( c3.parent() ).thenReturn( root );

        when( root.performAction() ).thenReturn( ServiceResult.newOkResult() );
        when( root.children() ).thenReturn( Arrays.asList( c1, c2, c3 ) );

        assertThat( instance.executeAction( root ), equalTo( new ServiceResult( err ) ) );

        verify( root ).performAction();
        verify( root ).children();
        verify( c1 ).performAction();
        verify( c1 ).children();
        verify( c2 ).performAction();
        verify( c2, never() ).children();
        verify( c3, never() ).performAction();
        verify( c3, never() ).children();

    }

    @Test
    public void testExecuteAction_ThreeChildren_FirstChildHasWarnings_SecondChildHasErrors() throws UpdateException {
        ServiceEngine instance = new ServiceEngine();

        UpdateResponseWriter okResponse = new UpdateResponseWriter();

        ServiceAction root = mock( ServiceAction.class );

        ServiceResult warn = ServiceResult.newWarningResult( UpdateStatusEnum.VALIDATE_ONLY, "warning" );

        ServiceAction c1 = mock( ServiceAction.class );
        when( c1.performAction() ).thenReturn( warn );
        when( c1.children() ).thenReturn( null );
        when( c1.parent() ).thenReturn( root );

        ServiceResult err = ServiceResult.newErrorResult( UpdateStatusEnum.VALIDATION_ERROR, "error" );

        ServiceAction c2 = mock( ServiceAction.class );
        when( c2.performAction() ).thenReturn( err );
        when( c2.children() ).thenReturn( null );
        when( c2.parent() ).thenReturn( root );

        ServiceAction c3 = mock( ServiceAction.class );
        when( c3.performAction() ).thenReturn( ServiceResult.newOkResult() );
        when( c3.children() ).thenReturn( null );
        when( c3.parent() ).thenReturn( root );

        when( root.performAction() ).thenReturn( ServiceResult.newOkResult() );
        when( root.children() ).thenReturn( Arrays.asList( c1, c2, c3 ) );

        ServiceResult expected = new ServiceResult();
        expected.setStatus( UpdateStatusEnum.VALIDATION_ERROR );
        expected.addEntries( warn );
        expected.addEntries( err );
        assertThat( instance.executeAction( root ), equalTo( expected ) );

        verify( root ).performAction();
        verify( root ).children();
        verify( c1 ).performAction();
        verify( c1 ).children();
        verify( c2 ).performAction();
        verify( c2, never() ).children();
        verify( c3, never() ).performAction();
        verify( c3, never() ).children();
    }

    private ValidationError createValidationItem( ValidateWarningOrErrorEnum type, String message ) {
        ValidationError result = new ValidationError( type );
        result.getParams().put( "message", message );

        return result;
    }
}
