package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.marc.binding.DataField;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.updateservice.dto.OptionEnumDTO;
import dk.dbc.updateservice.dto.OptionsDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.JNDIResources;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.utils.DeferredLogger;
import dk.dbc.updateservice.utils.MDCUtil;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Action to handle a complete Update request.
 * <p/>
 * This action verifies the request and creates a new action:
 * <ol>
 * <li>ValidateOperationAction: To validate the record from the request.</li>
 * </ol>
 */
public class UpdateRequestAction extends AbstractAction {
    private static final DeferredLogger LOGGER = new DeferredLogger(UpdateRequestAction.class);
    private static final Pattern INDICATOR_PATTERN = Pattern.compile("0\\d");
    private final Properties settings;

    public UpdateRequestAction(GlobalActionState globalActionState, Properties properties) {
        super(UpdateRequestAction.class.getSimpleName(), globalActionState);
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
        final ServiceResult message = verifyData();
        if (message != null) {
            return message;
        }
        children.add(new PreProcessingAction(state, state.readRecord()));
        children.add(new ValidateOperationAction(state, settings));
        if (!hasValidateOnlyOption()) {
            final MarcRecordReader reader = new MarcRecordReader(state.readRecord());
            if (RawRepo.MATVURD_AGENCY == reader.getAgencyIdAsInt()) {
                // Since this can result in writing/creating the common part of the matvurd record even when there is an error
                // in the r01/r02 fields, it has to be done before any kind of writing in the records' table
                // Information that needs check is in the enrichment part, so we have to look at the full request record
                children.add(new MatVurdR01R02CheckRecordsAction(state, state.readRecord()));
            }
            children.add(new AuthenticateRecordAction(state, state.readRecord()));
            children.add(new UpdateOperationAction(state, settings));
        }

        return ServiceResult.newOkResult();
    }

    private ServiceResult verifyData() throws UpdateException {
        return LOGGER.callChecked(log -> {
            if (!isAgencyIdAllowedToUseUpdateOnThisInstance()) {
                String message = String.format(state.getMessages().getString("agency.is.not.allowed.for.this.instance"), state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId());
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
            }
            if (state.getUpdateServiceRequestDTO().getBibliographicRecordDTO() == null) {
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, state.getMessages().getString("request.record.is.missing"));
            }
            if (!state.isRecordSchemaValid()) {
                log.warn("Unknown record schema: {}", state.getUpdateServiceRequestDTO().getBibliographicRecordDTO().getRecordSchema());
                return ServiceResult.newStatusResult(UpdateStatusEnumDTO.FAILED);
            }
            if (!state.isRecordPackingValid()) {
                log.warn("Unknown record packing: {}", state.getUpdateServiceRequestDTO().getBibliographicRecordDTO().getRecordPacking());
                return ServiceResult.newStatusResult(UpdateStatusEnumDTO.FAILED);
            }
            String message = sanityCheckRecord();
            if (!message.isEmpty()) {
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
            }
            return null;
        });
    }

    @Override
    public void setupMDCContext() {
        MDCUtil.setupContextForRecord(state.readRecord());
    }

    /**
     * Checks if the request is a validation only request.
     * <p>
     * It is declared public so {@link dk.dbc.updateservice.update.UpdateServiceCore} can use it.
     * </p>
     *
     * @return boolean value.
     */
    public boolean hasValidateOnlyOption() {
        final OptionsDTO optionsDTO = state.getUpdateServiceRequestDTO().getOptionsDTO();
        return optionsDTO != null && optionsDTO.getOption() != null && optionsDTO.getOption().contains(OptionEnumDTO.VALIDATE_ONLY);
    }

    private boolean isAgencyIdAllowedToUseUpdateOnThisInstance() throws UpdateException {
        if (!settings.containsKey(JNDIResources.UPDATE_PROD_STATE) || settings.getProperty(JNDIResources.UPDATE_PROD_STATE) == null) {
            throw new UpdateException("Required property '" + JNDIResources.UPDATE_PROD_STATE + "' not found");
        }
        final boolean isProduction = Boolean.parseBoolean(settings.getProperty(JNDIResources.UPDATE_PROD_STATE));

        return !isProduction
                || state.getUpdateServiceRequestDTO() == null
                || state.getUpdateServiceRequestDTO().getAuthenticationDTO() == null
                || state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId() == null
                || !state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId().startsWith("13");
    }

    String sanityCheckRecord() {
        return LOGGER.call(log -> {
            String message = "";
            try {
                final MarcRecord marcRecord = state.readRecord();
                final MarcRecordReader reader = new MarcRecordReader(marcRecord);

                if (reader.hasField("001")) {
                    if (!(reader.hasSubfield("001", 'a') && !reader.getRecordId().isEmpty())) {
                        message = state.getMessages().getString("sanity.check.failed.empty.001");
                    }
                    if (reader.getRecordId().strip().contains(" ")) {
                        message = state.getMessages().getString("sanity.check.failed.spaces.001");
                    }
                    if (!(reader.hasSubfield("001", 'b') && reader.getAgencyId() != null && !reader.getAgencyId().isEmpty() && reader.getAgencyIdAsInt() > 0)) {
                        message = state.getMessages().getString("sanity.check.failed.libraryno.001");
                    }
                } else {
                    message = state.getMessages().getString("sanity.check.failed.no.001");
                }
                for (DataField marcField : marcRecord.getFields(DataField.class)) {
                    final Matcher matcher = INDICATOR_PATTERN.matcher(getIndicators(marcField));
                    if (!matcher.find()) {
                        message = String.format(state.getMessages().getString("invalid.indicator.in.field"), marcField.getTag());
                    }
                }
            } catch (Exception ex) {
                message = state.getMessages().getString("sanity.check.failed.exception");
                log.error("Caught exception during sanity check", ex);
            }

            return message;
        });
    }

    private String getIndicators(DataField field) {
        final StringBuilder sb = new StringBuilder();
        if (field.getInd1() == null) {
            sb.append(" ");
        } else {
            sb.append(field.getInd1());
        }

        if (field.getInd2() == null) {
            sb.append(" ");
        } else {
            sb.append(field.getInd2());
        }

        return sb.toString();
    }

}
