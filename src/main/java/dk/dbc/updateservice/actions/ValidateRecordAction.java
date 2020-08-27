/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.utils.LogUtils;
import dk.dbc.jsonb.JSONBException;
import dk.dbc.opencat.connector.OpencatBusinessConnectorException;
import dk.dbc.updateservice.dto.MessageEntryDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.utils.MDCUtil;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

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
            logger.info("Handling record: {}", LogUtils.base64Encode(state.readRecord()));
            logger.info("state.getLibraryGroup().toString()");
            final List<MessageEntryDTO> errors = state.getOpencatBusiness().validateRecord(state.getSchemaName(), state.readRecord());

            result = new ServiceResult();
            result.addMessageEntryDtos(errors);

            //TODO: VERSION2: det her ligner spildt arbejde
            MarcRecordReader reader = new MarcRecordReader(state.readRecord());
            String recordId = reader.getRecordId();
            String agencyId = reader.getAgencyId();

            if (result.hasErrors()) {
                logger.info("Record {{}:{}} contains validation errors.", recordId, agencyId);
                result.setStatus(UpdateStatusEnumDTO.FAILED);
            } else {
                logger.info("Record {{}:{}} has validated successfully.", recordId, agencyId);
                result.setStatus(UpdateStatusEnumDTO.OK);
            }
            return result;
        } catch (OpencatBusinessConnectorException | JSONBException ex) {
            String message = String.format(state.getMessages().getString("internal.validate.record.error"), ex.getMessage());
            logger.error(message, ex);
            return result = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
        } finally {
            logger.exit(result);
        }
    }

    @Override
    public void setupMDCContext() {
        MDCUtil.setupContextForRecord(state.readRecord());
    }
}
