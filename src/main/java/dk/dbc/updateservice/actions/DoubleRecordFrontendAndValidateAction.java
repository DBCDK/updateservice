package dk.dbc.updateservice.actions;

import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.utils.MDCUtil;

import java.util.Properties;

public class DoubleRecordFrontendAndValidateAction extends AbstractAction {
    Properties settings;
    DoubleRecordFrontendAction doubleRecordFrontendAction;
    ValidateRecordAction validateRecordAction;

    public DoubleRecordFrontendAndValidateAction(GlobalActionState globalActionState, Properties settings) {
        super(DoubleRecordFrontendAndValidateAction.class.getSimpleName(), globalActionState);
        this.settings = settings;
        doubleRecordFrontendAction = new DoubleRecordFrontendAction(globalActionState, settings);
        validateRecordAction = new ValidateRecordAction(globalActionState, settings);
    }

    @Override
    public ServiceResult performAction() throws UpdateException {
        final ServiceResult result = new ServiceResult();

        if (state.isDoubleRecordPossible() && state.getLibraryGroup().isFBS() && state.getUpdateServiceRequestDTO().getDoubleRecordKey() == null) {
            result.addServiceResult(doubleRecordFrontendAction.performAction());
        }

        result.addServiceResult(validateRecordAction.performAction());

        return result;
    }

    @Override
    public void setupMDCContext() {
        MDCUtil.setupContextForRecord(state.readRecord());
    }
}
