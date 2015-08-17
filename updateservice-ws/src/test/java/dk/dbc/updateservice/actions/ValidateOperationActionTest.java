//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordFactory;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.updateservice.auth.Authenticator;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.service.api.Authentication;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import org.junit.Test;

import javax.xml.ws.WebServiceContext;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

//-----------------------------------------------------------------------------
public class ValidateOperationActionTest {
    @Test
    public void testPerformAction() throws Exception {
        Authenticator authenticator = mock( Authenticator.class );
        Authentication authentication = mock( Authentication.class );
        WebServiceContext wsContext = mock( WebServiceContext.class );

        InputStream is = getClass().getResourceAsStream( BOOK_RECORD_RESOURCE );
        MarcRecord record = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );

        Scripter scripter = mock( Scripter.class );
        Properties settings = new Properties();

        String schemaName = "book";

        ValidateOperationAction instance = new ValidateOperationAction();
        instance.setAuthenticator( authenticator );
        instance.setAuthentication( authentication );
        instance.setWebServiceContext( wsContext );
        instance.setValidateSchema( schemaName );
        instance.setRecord( record );
        instance.setOkStatus( UpdateStatusEnum.VALIDATE_ONLY );
        instance.setScripter( scripter );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newStatusResult( UpdateStatusEnum.VALIDATE_ONLY ) ) );

        List<ServiceAction> children = instance.children();
        assertThat( children.size(), is( 3 ) );

        ServiceAction child = children.get( 0 );
        assertTrue( child.getClass() == AuthenticateUserAction.class );

        AuthenticateUserAction authenticateUserAction = (AuthenticateUserAction)child;
        assertThat( authenticateUserAction.getAuthenticator(), is( authenticator ) );
        assertThat( authenticateUserAction.getAuthentication(), is( authentication ) );
        assertThat( authenticateUserAction.getWsContext(), is( wsContext ) );

        child = children.get( 1 );
        assertTrue( child.getClass() == ValidateSchemaAction.class );

        ValidateSchemaAction validateSchemaAction = (ValidateSchemaAction)child;
        assertThat( validateSchemaAction.getValidateSchema(), equalTo( schemaName ) );
        assertThat( validateSchemaAction.getScripter(), is( scripter ) );
        assertThat( validateSchemaAction.getSettings(), is( settings ) );

        child = children.get( 2 );
        assertTrue( child.getClass() == ValidateRecordAction.class );

        ValidateRecordAction validateRecordAction = (ValidateRecordAction)child;
        assertThat( validateRecordAction.getSchemaName(), equalTo( instance.getValidateSchema() ) );
        assertThat( validateRecordAction.getRecord(), is( instance.getRecord() ) );
        assertThat( validateRecordAction.getOkStatus(), is( instance.getOkStatus() ) );
        assertThat( validateRecordAction.getScripter(), is( scripter ) );
        assertThat( validateRecordAction.getSettings(), is( settings ) );
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final String BOOK_RECORD_RESOURCE = "/dk/dbc/updateservice/actions/book.marc";
}
