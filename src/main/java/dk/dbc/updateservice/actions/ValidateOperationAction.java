/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.utils.MDCUtil;

import java.util.Properties;

/**
 * Action that setup actions to validate a record.
 */
public class ValidateOperationAction extends AbstractAction {
    Properties properties;

    public ValidateOperationAction(GlobalActionState globalActionState, Properties properties) {
        super(ValidateOperationAction.class.getSimpleName(), globalActionState);
        this.properties = properties;
    }

    /**
     * Performs this actions and may create any child actions.
     *
     * @return A list of ValidationError to be reported in the web service response.
     * @throws UpdateException In case of an error.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        final AuthenticateUserAction authenticateUserAction = new AuthenticateUserAction(state, properties);
        children.add(authenticateUserAction);

        final ValidateSchemaAction validateSchemaAction = new ValidateSchemaAction(state, properties);
        children.add(validateSchemaAction);

        if (state.isDoubleRecordPossible() && state.getLibraryGroup().isFBS() && state.getUpdateServiceRequestDTO().getDoubleRecordKey() == null) {
            final DoubleRecordFrontendAndValidateAction doubleRecordFrontendAndValidateAction = new DoubleRecordFrontendAndValidateAction(state, properties);
            children.add(doubleRecordFrontendAndValidateAction);
        } else {
            final ValidateRecordAction validateRecordAction = new ValidateRecordAction(state, properties);
            children.add(validateRecordAction);
        }
        return ServiceResult.newOkResult();
    }

    @Override
    public void setupMDCContext() {
        MDCUtil.setupContextForRecord(state.readRecord());
    }
}
