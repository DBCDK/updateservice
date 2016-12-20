package dk.dbc.updateservice.actions;

import dk.dbc.updateservice.dto.UpdateStatusEnumDto;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.ws.MDCUtil;

import java.util.Properties;

/**
 * Action that setup actions to validate a record.
 */
public class ValidateOperationAction extends AbstractAction {
    UpdateStatusEnumDto okStatus = null;
    Properties settings;

    public ValidateOperationAction(GlobalActionState globalActionState, Properties properties) {
        super(ValidateOperationAction.class.getSimpleName(), globalActionState);
        settings = properties;
    }

    public void setOkStatus(UpdateStatusEnumDto okStatus) {
        this.okStatus = okStatus;
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

        if (state.getUpdateMode().isFBSMode() && state.getUpdateServiceRequestDto().getDoubleRecordKey() == null) {
            DoubleRecordFrontendAction doubleRecordFrontendAction = new DoubleRecordFrontendAction(state, settings, state.readRecord());
            children.add(doubleRecordFrontendAction);
        }

        ValidateRecordAction validateRecordAction = new ValidateRecordAction(state, settings);
        validateRecordAction.setOkStatus(okStatus);
        children.add(validateRecordAction);

        return ServiceResult.newStatusResult(okStatus);
    }

    @Override
    public void setupMDCContext() {
        MDCUtil.setupContextForRecord(state.readRecord());
    }
}
