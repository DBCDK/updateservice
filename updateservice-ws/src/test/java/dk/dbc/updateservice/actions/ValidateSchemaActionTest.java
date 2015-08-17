//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import org.junit.Test;

import java.util.Properties;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

//-----------------------------------------------------------------------------
public class ValidateSchemaActionTest {
    @Test( expected = IllegalArgumentException.class )
    public void testValidateSchemaIsNull() throws Exception {
        Scripter scripter = mock( Scripter.class );
        Properties settings = new Properties();
        ValidateSchemaAction instance = new ValidateSchemaAction( null, scripter, settings );

        instance.performAction();
    }

    @Test( expected = IllegalArgumentException.class )
    public void testScripterIsNull() throws Exception {
        Properties settings = new Properties();
        ValidateSchemaAction instance = new ValidateSchemaAction( "name", null, settings );

        instance.performAction();
    }

    @Test( expected = IllegalArgumentException.class )
    public void testSettingsIsNull() throws Exception {
        Scripter scripter = mock( Scripter.class );
        ValidateSchemaAction instance = new ValidateSchemaAction( "name", scripter, null );

        instance.performAction();
    }

    @Test
    public void testScripterException() throws Exception {
        Scripter scripter = mock( Scripter.class );
        Properties settings = new Properties();
        ValidateSchemaAction instance = new ValidateSchemaAction( "book", scripter, settings );

        ScripterException ex = new ScripterException( "message" );
        when( scripter.callMethod( anyString(), anyString(), anyString(), eq( settings ) ) ).thenThrow( ex );

        assertThat( instance.performAction(), equalTo( ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_INVALID_SCHEMA, ex.getMessage() ) ) );
    }

    @Test
    public void testScripterWrongDatatype() throws Exception {
        Scripter scripter = mock( Scripter.class );
        Properties settings = new Properties();
        ValidateSchemaAction instance = new ValidateSchemaAction( "book", scripter, settings );

        when( scripter.callMethod( anyString(), anyString(), anyString(), eq( settings ) ) ).thenReturn( 27 );

        assertThat( instance.performAction(), equalTo( ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_INVALID_SCHEMA, "The JavaScript function checkTemplate must return a boolean value." ) ) );
    }

    @Test
    public void testSchemaFound() throws Exception {
        Scripter scripter = mock( Scripter.class );
        Properties settings = new Properties();
        ValidateSchemaAction instance = new ValidateSchemaAction( "book", scripter, settings );

        when( scripter.callMethod( eq( "validator.js" ), eq( "checkTemplate" ), eq( "book" ), eq( settings ) ) ).thenReturn( true );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );
    }

    @Test
    public void testSchemaNotFound() throws Exception {
        Scripter scripter = mock( Scripter.class );
        Properties settings = new Properties();
        ValidateSchemaAction instance = new ValidateSchemaAction( "book", scripter, settings );

        when( scripter.callMethod( eq( "validator.js" ), eq( "checkTemplate" ), eq( "book" ), eq( settings ) ) ).thenReturn( false );

        assertThat( instance.performAction(), equalTo( ServiceResult.newStatusResult( UpdateStatusEnum.FAILED_INVALID_SCHEMA ) ) );
    }
}
