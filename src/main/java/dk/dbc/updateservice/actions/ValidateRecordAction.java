package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.utils.json.Json;
import dk.dbc.updateservice.dto.MessageEntryDto;
import dk.dbc.updateservice.dto.UpdateStatusEnumDto;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.ws.MDCUtil;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

/**
 * Action to validate a record.
 * <p/>
 * This action needs the following to be able to validate a record:
 * <ol>
 * <li>The record to validate, <code>record</code></li>
 * <li>
 * The name of the template that contains the validation rules to check against the record,
 * <code>schemaName</code>
 * </li>
 * <li>
 * A JavaScript environment, <code>scripter</code>.
 * </li>
 * <li>
 * The JavaScript logic need some settings as a set of Properties to work properly.
 * These settings can be set thought <code>settings</code>. This class does not use these
 * settings by itself.
 * </li>
 * </ol>
 */
public class ValidateRecordAction extends AbstractAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(ValidateRecordAction.class);

    Properties settings;
    UpdateStatusEnumDto okStatus;

    /**
     * Constructs an instance with a template name and a record.
     * <p/>
     * The JavaScript logic needs an JavaScript environment and a set of
     * settings to work properly. These can be set though the properties <code>scripter</code>
     * and <code>settings</code>. This constructor initialize these to null.
     *
     * @param globalActionState State object containing data with data from request.
     */
    public ValidateRecordAction(GlobalActionState globalActionState, Properties properties) {
        super(ValidateRecordAction.class.getSimpleName(), globalActionState);
        settings = properties;
    }

    public void setOkStatus(UpdateStatusEnumDto okStatus) {
        this.okStatus = okStatus;
    }

    /**
     * Validates the record against the JavaScript logic.
     * <p/>
     * If the JavaScript logic returns any validation errors they are converted to
     * validation entries in the ServiceResult with the status
     * <code>UpdateStatusEnum.VALIDATION_ERROR</code>. If no errors are returned
     * we use the status from <code>okStatus</code>.
     * <p/>
     * Exceptions from the JavaScript logic is converted to a ServiceResult with the
     * status <code>UpdateStatusEnum.FAILED_VALIDATION_INTERNAL_ERROR</code>. The actual
     * exception message returned as a validation entry in the ServiceResult.
     *
     * @return The constructed ServiceResult.
     * @throws UpdateException Never thrown.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        logger.entry();
        ServiceResult result = null;
        try {
            logger.info("Handling record:\n{}", state.readRecord());
            Object jsResult = state.getScripter().callMethod("validateRecord", state.getSchemaName(), Json.encode(state.readRecord()), settings);
            logger.debug("Result from validateRecord JS (" + jsResult.getClass().getName() + "): " + jsResult);

            List<MessageEntryDto> errors = Json.decodeArray(jsResult.toString(), MessageEntryDto.class);
            result = new ServiceResult();
            result.addMessageEntryDtos(errors);

            //TODO: VERSION2: det her ligner spildt arbejde
            MarcRecordReader reader = new MarcRecordReader(state.readRecord());
            String recordId = reader.recordId();
            String agencyId = reader.agencyId();

            if (result.hasErrors()) {
                logger.error("Record {{}:{}} contains validation errors.", recordId, agencyId);
                result.setStatus(UpdateStatusEnumDto.FAILED);
            } else {
                logger.info("Record {{}:{}} has validated successfully.", recordId, agencyId);
                result.setStatus(okStatus);
            }
            return result;
        } catch (IOException | ScripterException ex) {
            String message = String.format(state.getMessages().getString("internal.validate.record.error"), ex.getMessage());
            logger.error(message, ex);
            return result = ServiceResult.newErrorResult(UpdateStatusEnumDto.FAILED, message, state);
        } finally {
            logger.exit(result);
        }
    }

    @Override
    public void setupMDCContext() {
        MDCUtil.setupContextForRecord(state.readRecord());
    }
}
