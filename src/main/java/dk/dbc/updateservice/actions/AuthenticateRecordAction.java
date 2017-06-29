/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.openagency.client.OpenAgencyException;
import dk.dbc.updateservice.dto.MessageEntryDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.update.NoteAndSubjectExtensionsHandler;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.ws.MDCUtil;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.type.TypeFactory;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.ejb.EJBException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.ResourceBundle;

/**
 * Action to authenticate a record.
 * <p/>
 * This action needs the following to be able to authenticate a record:
 * <ol>
 * <li>The record to authenticate, <code>record</code></li>
 * <li>
 * The name of the template that contains the validation rules to check against the record,
 * <code>schemaName</code>
 * </li>
 * <li>
 * An Authenticator that do the actual authentication, <code>authenticator</code>.
 * </li>
 * <li>
 * Login information to be parsed to <code>authenticator</code>.
 * </li>
 * </ol>
 */
public class AuthenticateRecordAction extends AbstractRawRepoAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(AuthenticateRecordAction.class);
    private Properties settings;

    /**
     * Constructs an instance with a template name and a record.
     * <p/>
     * The JavaScript logic needs an JavaScript environment and a set of
     * settings to work properly. These can be set though the properties <code>scripter</code>
     * and <code>settings</code>. This constructor initialize these to null.
     *
     * @param globalActionState State object containing data with data from request.
     */
    public AuthenticateRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord record) {
        super(AuthenticateRecordAction.class.getSimpleName(), globalActionState, record);
        this.settings = properties;

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
            logger.info("Login user: {}/{}", state.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId(), state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId());
            logger.info("Handling record:\n{}", state.readRecord());

            List<MessageEntryDTO> errors = authenticateRecord();
            result = new ServiceResult();
            result.addMessageEntryDtos(errors);

            MarcRecordReader reader = new MarcRecordReader(record);
            String recordId = reader.recordId();
            String agencyId = reader.agencyId();
            if (result.hasErrors()) {
                logger.warn("Authenticating of record {{}:{}} with user {}/{} failed", recordId, agencyId, state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), state.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId());
                result.setStatus(UpdateStatusEnumDTO.FAILED);
            } else {
                logger.info("Authenticating record {{}:{}} with user {}/{} successfully", recordId, agencyId, state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), state.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId());
                result.setStatus(UpdateStatusEnumDTO.OK);
            }
            return result;
        } catch (EJBException | ScripterException ex) {
            Throwable businessException = findServiceException(ex);
            String message = String.format(state.getMessages().getString("internal.authenticate.record.error"), businessException.getMessage());
            logger.error(message);
            logger.warn("Exception doing authentication: ", businessException);
            return result = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
        } finally {
            logger.exit(result);
        }
    }

    private List<MessageEntryDTO> authenticateRecord() throws ScripterException, UpdateException {
        logger.entry(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId(), state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId());
        List<MessageEntryDTO> result = new ArrayList<>();
        try {
            String groupId = state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId();
            MarcRecordReader reader = new MarcRecordReader(record);

            // First check if the group is "root" - if so just return as no further validation is necessary
            try {
                if (state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_ROOT)) {
                    logger.info("Group is AUTH_ROOT -> exit OK");
                    return result;
                }
            } catch (OpenAgencyException e) {
                throw new UpdateException("Caught OpenAgencyException", e);
            }

            // If the group is identical to the agency of the record then authenticate OK
            if (groupId.equals(reader.agencyId())) {
                logger.info("Group is identical with agencyId -> exit OK");
                return result;
            }

            if (RawRepo.COMMON_AGENCY.equals(reader.agencyIdAsInteger())) {
                logger.info("Record belongs to 870970");
                NoteAndSubjectExtensionsHandler noteAndSubjectExtensionsHandler = state.getNoteAndSubjectExtensionsHandler();

                if (noteAndSubjectExtensionsHandler.isNationalCommonRecord(record)) {
                    logger.info("Record is national common record");
                    List<MessageEntryDTO> validationErrors = noteAndSubjectExtensionsHandler.authenticateCommonRecordExtraFields(record, groupId);
                    if (validationErrors.size() > 0) {
                        logger.info("Validation errors!");
                        result.addAll(validationErrors);
                        logger.info("Number of errors: {}", result.size());
                    }

                    return result;
                }
                logger.info("Record is not national common record");
                ObjectMapper mapper = new ObjectMapper();
                Object jsResult = state.getScripter().callMethod("authenticateRecord", mapper.writeValueAsString(record), state.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId(), groupId, settings);
                logger.debug("Result from authenticateRecord JS ({}): {}", jsResult.getClass().getName(), jsResult);
                if (jsResult instanceof String) {
                    // TODO: HUST RET JAVASCRIPT OGSÅ
                    List<MessageEntryDTO> validationErrors = mapper.readValue(jsResult.toString(), TypeFactory.defaultInstance().constructCollectionType(List.class, MessageEntryDTO.class));
                    result.addAll(validationErrors);
                    logger.info("Number of errors: {}", result.size());
                    return result;
                }
                throw new ScripterException(String.format("The JavaScript function %s must return a String value.", "authenticateRecord"));
            }

            Integer groupIdAsInteger = Integer.parseInt(groupId);
            if (300000 <= groupIdAsInteger && groupIdAsInteger <= 399999) {
                if (reader.agencyIdAsInteger().equals(RawRepo.SCHOOL_COMMON_AGENCY)) {
                    logger.info("Group is school agency and record is owner by 300000 -> exit OK");
                    return result;
                }
            }

            ResourceBundle resourceBundle = ResourceBundles.getBundle("messages");
            String message = String.format(resourceBundle.getString("edit.record.other.library.error"), reader.recordId());
            MessageEntryDTO messageEntryDTO = new MessageEntryDTO();
            messageEntryDTO.setMessage(message);
            result.add(messageEntryDTO);

            return result;
        } catch (IOException ex) {
            throw new ScripterException(ex.getMessage(), ex);
        } finally {
            logger.exit(result);
        }
    }

    @Override
    public void setupMDCContext() {
        MDCUtil.setupContextForRecord(state.readRecord());
    }
}
