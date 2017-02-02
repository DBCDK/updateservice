package dk.dbc.updateservice.actions;

import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.ws.MDCUtil;

import java.util.Properties;

/**
 * Action that setup actions to validate a record.
 */
public class ValidateOperationAction extends AbstractAction {
    Properties settings;

    public ValidateOperationAction(GlobalActionState globalActionState, Properties properties) {
        super(ValidateOperationAction.class.getSimpleName(), globalActionState);
        settings = properties;
    }

    /**
     * Performs this actions and may create any child actions.
     *
     * @return A list of ValidationError to be reported in the web service response.
     * @throws UpdateException In case of an error.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        AuthenticateUserAction authenticateUserAction = new AuthenticateUserAction(state);
        children.add(authenticateUserAction);

        ValidateSchemaAction validateSchemaAction = new ValidateSchemaAction(state, settings);
        children.add(validateSchemaAction);

        if (state.isDoubleRecordPossible() && state.getLibraryGroup().isFBS() && state.getUpdateServiceRequestDTO().getDoubleRecordKey() == null) {
            DoubleRecordFrontendAndValidateAction doubleRecordFrontendAndValidateAction = new DoubleRecordFrontendAndValidateAction(state, settings);
            children.add(doubleRecordFrontendAndValidateAction);
        } else {
            ValidateRecordAction validateRecordAction = new ValidateRecordAction(state, settings);
            children.add(validateRecordAction);
        }
        return ServiceResult.newOkResult();
    }

    @Override
    public void setupMDCContext() {
        MDCUtil.setupContextForRecord(state.readRecord());
    }
}
