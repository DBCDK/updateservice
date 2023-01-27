package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.utils.LogUtils;
import dk.dbc.jsonb.JSONBException;
import dk.dbc.opencat.connector.OpencatBusinessConnectorException;
import dk.dbc.updateservice.dto.MessageEntryDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.utils.DeferredLogger;
import dk.dbc.updateservice.utils.MDCUtil;
import dk.dbc.vipcore.exception.VipCoreException;
import dk.dbc.vipcore.libraryrules.VipCoreLibraryRulesConnector;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.MDC;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import static dk.dbc.updateservice.utils.MDCUtil.MDC_TRACKING_ID_LOG_CONTEXT;

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
 * </ol>
 */
public class ValidateRecordAction extends AbstractAction {
    private static final DeferredLogger LOGGER = new DeferredLogger(ValidateRecordAction.class);

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
        return LOGGER.callChecked(log -> {
            final StopWatch watch = new Log4JStopWatch("opencatBusiness.validateRecord");
            try {
                final String trackingId = MDC.get(MDC_TRACKING_ID_LOG_CONTEXT);
                final MarcRecordReader reader = new MarcRecordReader(state.readRecord());
                final String recordId = reader.getRecordId();
                final String agencyId = reader.getAgencyId();

                if (log.isInfoEnabled()) {
                    log.debug("Handling record: {}", LogUtils.base64Encode(state.readRecord()));
                }

                final ServiceResult result = new ServiceResult();

                // If the record is marked for deletion the validateRecord function will only check if the schema supports
                // deleting records. If so, ok is returned without further validation.
                // If we want to check if the groupId has permission to delete a record we have to check it before validateRecord
                if (reader.markedForDeletion()) {
                    final String owner = reader.getAgencyId();
                    if (RawRepo.DBC_AGENCY_ALL.contains(owner)) {
                        final String groupId = state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId();
                        if (!state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_DBC_RECORDS)) {
                            final String message = state.getMessages().getString("delete.record.common.record.missing.rights");
                            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
                        }
                    }
                }

                if (!state.getIsTemplateOverwrite()) {
                    final List<MessageEntryDTO> errors = state.getOpencatBusiness().validateRecord(state.getSchemaName(), state.getMarcRecord(), trackingId);
                    result.addMessageEntryDtos(errors);
                }


                if (result.hasErrors()) {
                    log.info("Record {{}:{}} contains validation errors.", recordId, agencyId);
                    result.setStatus(UpdateStatusEnumDTO.FAILED);
                } else {
                    log.info("Record {{}:{}} has validated successfully.", recordId, agencyId);
                    result.setStatus(UpdateStatusEnumDTO.OK);
                }
                return result;
            } catch (IOException | JSONBException | JAXBException | OpencatBusinessConnectorException |
                     VipCoreException ex) {
                String message = String.format(state.getMessages().getString("internal.validate.record.error"), ex.getMessage());
                log.error(message, ex);
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
            } finally {
                watch.stop();
            }
        });
    }

    @Override
    public void setupMDCContext() {
        MDCUtil.setupContextForRecord(state.readRecord());
    }
}
