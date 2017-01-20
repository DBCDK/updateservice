package dk.dbc.updateservice.actions;

import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.ws.MDCUtil;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.Properties;

public class DoubleRecordFrontendAndValidateAction extends AbstractAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(UpdateRequestAction.class);

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
        logger.entry();
        ServiceResult result = new ServiceResult();
        try {
            result.addServiceResult(doubleRecordFrontendAction.performAction());
            result.addServiceResult(validateRecordAction.performAction());
            return result;
        } finally {
            logger.exit(result);
        }
    }

    @Override
    public void setupMDCContext() {
        MDCUtil.setupContextForRecord(state.readRecord());
    }
}
