//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.updateservice.auth.Authenticator;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.service.api.Authentication;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.ws.MDCUtil;

import javax.xml.ws.WebServiceContext;
import java.util.Properties;

//-----------------------------------------------------------------------------
/**
 * Action that setup actions to validate a record.
 */
public class ValidateOperationAction extends AbstractAction {
    public ValidateOperationAction() {
        super( "ValidateOperationAction" );

        this.authenticator = null;
        this.authentication = null;
        this.webServiceContext = null;

        this.validateSchema = null;
        this.record = null;
        this.okStatus = null;
        this.scripter = null;
        this.settings = null;
    }

    public Authenticator getAuthenticator() {
        return authenticator;
    }

    public void setAuthenticator( Authenticator authenticator ) {
        this.authenticator = authenticator;
    }

    public Authentication getAuthentication() {
        return authentication;
    }

    public void setAuthentication( Authentication authentication ) {
        this.authentication = authentication;
    }

    public WebServiceContext getWebServiceContext() {
        return webServiceContext;
    }

    public void setWebServiceContext( WebServiceContext webServiceContext ) {
        this.webServiceContext = webServiceContext;
    }

    public String getValidateSchema() {
        return validateSchema;
    }

    public void setValidateSchema( String validateSchema ) {
        this.validateSchema = validateSchema;
    }

    public MarcRecord getRecord() {
        return record;
    }

    public void setRecord( MarcRecord record ) {
        this.record = record;
    }

    public UpdateStatusEnum getOkStatus() {
        return okStatus;
    }

    public void setOkStatus( UpdateStatusEnum okStatus ) {
        this.okStatus = okStatus;
    }

    public Scripter getScripter() {
        return scripter;
    }

    public void setScripter( Scripter scripter ) {
        this.scripter = scripter;
    }

    public Properties getSettings() {
        return settings;
    }

    public void setSettings( Properties settings ) {
        this.settings = settings;
    }

    /**
     * Performs this actions and may create any child actions.
     *
     * @return A list of ValidationError to be reported in the web service response.
     *
     * @throws UpdateException In case of an error.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        children.add( new AuthenticateUserAction( this.authenticator, this.authentication, this.webServiceContext ) );
        children.add( new ValidateSchemaAction( this.validateSchema, this.scripter, this.settings ) );

        ValidateRecordAction validateRecordAction = new ValidateRecordAction( this.validateSchema, this.record, this.okStatus );
        validateRecordAction.setScripter( this.scripter );
        validateRecordAction.setSettings( this.settings );
        children.add( validateRecordAction );

        return ServiceResult.newStatusResult( this.okStatus );
    }

    @Override
    public void setupMDCContext() {
        MDCUtil.setupContextForRecord( record );
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    /**
     * Class to authenticate the user against the forsrights web service.
     */
    private Authenticator authenticator;

    /**
     * Authentication members from the request.
     * <p/>
     * Group, username and password.
     */
    private Authentication authentication;
    private WebServiceContext webServiceContext;

    /**
     * Schema name to use to validate the record.
     */
    private String validateSchema;

    /**
     * The record to validate.
     */
    private MarcRecord record;

    /**
     * Status to use if the validation succeed without no errors.
     * <p/>
     * We use two different statuses for success:
     * <ul>
     *     <li>UpdateStatusEnum.VALIDATE_ONLY: If we only validates the record.</li>
     *     <li>UpdateStatusEnum.OK: If we updates the record.</li>
     * </ul>
     */
    private UpdateStatusEnum okStatus;

    /**
     * JavaScript engine to execute the validation rules on the record.
     */
    private Scripter scripter;

    /**
     * Settings that is required by the JavaScript implementation.
     */
    private Properties settings;
}
