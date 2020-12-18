/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.utils.LogUtils;
import dk.dbc.common.records.utils.RecordContentTransformer;
import dk.dbc.updateservice.dto.MessageEntryDTO;
import dk.dbc.updateservice.dto.TypeEnumDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.NoteAndSubjectExtensionsHandler;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.utils.MDCUtil;
import dk.dbc.updateservice.utils.ResourceBundles;
import dk.dbc.vipcore.exception.VipCoreException;
import dk.dbc.vipcore.libraryrules.VipCoreLibraryRulesConnector;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.ejb.EJBException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

    /**
     * Constructs an instance with a template name and a record.
     * <p/>
     * The JavaScript logic needs an JavaScript environment and a set of
     * settings to work properly. These can be set though the properties <code>scripter</code>
     * and <code>settings</code>. This constructor initialize these to null.
     *
     * @param globalActionState State object containing data with data from request.
     */
    public AuthenticateRecordAction(GlobalActionState globalActionState, MarcRecord record) {
        super(AuthenticateRecordAction.class.getSimpleName(), globalActionState, record);
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
            if (logger.isInfoEnabled()) {
                logger.info("Handling record: {}", LogUtils.base64Encode(state.readRecord()));
            }

            List<MessageEntryDTO> errors = authenticateRecord();
            result = new ServiceResult();
            result.addMessageEntryDtos(errors);

            MarcRecordReader reader = new MarcRecordReader(record);
            String recordId = reader.getRecordId();
            String agencyId = reader.getAgencyId();
            if (result.hasErrors()) {
                logger.warn("Authenticating of record {{}:{}} with user {}/{} failed", recordId, agencyId, state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), state.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId());
                result.setStatus(UpdateStatusEnumDTO.FAILED);
            } else {
                logger.info("Authenticating record {{}:{}} with user {}/{} successfully", recordId, agencyId, state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), state.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId());
                result.setStatus(UpdateStatusEnumDTO.OK);
            }
            return result;
        } catch (EJBException | VipCoreException ex) {
            Throwable businessException = findServiceException(ex);
            String message = String.format(state.getMessages().getString("internal.authenticate.record.error"), businessException.getMessage());
            logger.error(message);
            logger.warn("Exception doing authentication: ", businessException);
            return result = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
        } finally {
            logger.exit(result);
        }
    }

    private List<MessageEntryDTO> authenticateRecord() throws UpdateException, VipCoreException {
        logger.entry();
        List<MessageEntryDTO> result = new ArrayList<>();
        try {
            String groupId = state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId();
            MarcRecordReader reader = new MarcRecordReader(record);

            // First check if the group is "root" - if so just return as no further validation is necessary
            try {
                if (state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_ROOT)) {
                    logger.info("Group is AUTH_ROOT -> exit OK");
                    return result;
                }
            } catch (VipCoreException e) {
                throw new UpdateException("Caught VipCoreException", e);
            }

            // If the group is identical to the agency of the record then authenticate OK
            if (groupId.equals(reader.getAgencyId())) {
                logger.info("Group is identical with agencyId -> exit OK");
                return result;
            }

            if (RawRepo.COMMON_AGENCY == reader.getAgencyIdAsInt()) {
                logger.info("Record belongs to 870970");
                NoteAndSubjectExtensionsHandler noteAndSubjectExtensionsHandler = state.getNoteAndSubjectExtensionsHandler();
                List<MessageEntryDTO> validationErrors;

                if (noteAndSubjectExtensionsHandler.isNationalCommonRecord(record)) {
                    logger.info("Record is national common record");
                    validationErrors = noteAndSubjectExtensionsHandler.authenticateCommonRecordExtraFields(record, groupId);
                } else {
                    logger.info("Record is not national common record");
                    validationErrors = authenticateCommonRecord();
                }

                validationErrors.addAll(authenticateMetaCompassField());

                if (!validationErrors.isEmpty()) {
                    logger.info("Validation errors!");
                    result.addAll(validationErrors);
                    logger.info("Number of errors: {}", result.size());
                }

                return result;
            }

            int groupIdAsInt = Integer.parseInt(groupId);
            if (300000 <= groupIdAsInt && groupIdAsInt <= 399999 &&
                    reader.getAgencyIdAsInt() == RawRepo.SCHOOL_COMMON_AGENCY) {
                logger.info("Group is school agency and record is owner by 300000 -> exit OK");
                return result;
            }

            ResourceBundle resourceBundle = ResourceBundles.getBundle("messages");
            String message = String.format(resourceBundle.getString("edit.record.other.library.error"), reader.getRecordId());
            MessageEntryDTO messageEntryDTO = new MessageEntryDTO();
            messageEntryDTO.setMessage(message);
            messageEntryDTO.setType(TypeEnumDTO.ERROR);
            result.add(messageEntryDTO);

            return result;
        } finally {
            logger.exit(result);
        }
    }

    private List<MessageEntryDTO> authenticateCommonRecord() throws UpdateException, VipCoreException {
        logger.entry();

        try {
            MarcRecordReader reader = new MarcRecordReader(record);
            ResourceBundle resourceBundle = ResourceBundles.getBundle("messages");

            String recordId = reader.getRecordId();
            int agencyId = reader.getAgencyIdAsInt();
            String groupId = state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId();
            String owner = reader.getValue("996", "a");

            logger.info("Record agency: {}", agencyId);
            logger.info("New owner: {}", owner);

            if (!state.getRawRepo().recordExists(recordId, agencyId)) {
                logger.debug("Checking authentication for new common record.");

                if (owner == null || owner.isEmpty()) {
                    return createErrorReply(resourceBundle.getString("create.common.record.error"));
                }

                if (!owner.equals(groupId)) {
                    return createErrorReply(resourceBundle.getString("create.common.record.other.library.error"));
                }

                return createOkReply();
            }

            logger.debug("Checking authentication for updating existing common record.");
            MarcRecord curRecord = RecordContentTransformer.decodeRecord(state.getRawRepo().fetchRecord(recordId, RawRepo.COMMON_AGENCY).getContent());
            MarcRecordReader curReader = new MarcRecordReader(curRecord);
            String curOwner = curReader.getValue("996", "a");

            logger.info("Current owner: {}", curOwner);

            if ("DBC".equals(curOwner)) {
                logger.info("Owner is DBC");
                if (!state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_DBC_RECORDS)) {
                    return createErrorReply(resourceBundle.getString("update.common.record.owner.dbc.error"));
                }
                return createOkReply();
            }

            if ("RET".equals(curOwner)) {
                logger.info("Owner is RET");
                if (!state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_RET_RECORD)) {
                    return createErrorReply(resourceBundle.getString("update.common.record.error"));
                }
                logger.info("New value of 008 *v is {}", reader.getValue("008", "v"));
                logger.info("Current value of 008 *v is {}", curReader.getValue("008", "v"));
                if ("4".equals(curReader.getValue("008", "v"))) {
                    final List<String> allowedKatValues = Arrays.asList("0", "1", "5");
                    if (!allowedKatValues.contains(reader.getValue("008", "v"))) {
                        return createErrorReply(resourceBundle.getString("update.common.record.katalogiseringsniveau.error"));
                    }
                }
                return createOkReply();
            }

            if (owner == null || owner.isEmpty()) {
                logger.info("Owner is empty");
                return createErrorReply(resourceBundle.getString("update.common.record.error"));
            }

            if ("700300".equals(curOwner) &&
                    !("700300".equals(groupId) && "700300".equals(owner))) {
                return createErrorReply(resourceBundle.getString("update.common.record.change.record.700300"));
            }

             /*
                AUTH_PUBLIC_LIB_COMMON_RECORD er i vip : Ret fællespost - Har ret til at rette og overtage en folkebiblioteksejet fællesskabspost
                Hvis ejer af eksisterende post har sat AUTH_PUBLIC_LIB_COMMON_RECORD så :
                hvis ejer(996) af indsendt post er forskellig fra groupId så fejl : Du har ikke ret til at give ejerskabet for en folkebiblioteksejet fællesskabspost til et andet bibliotek
                hvis bibliotek groupId ikke har sat AUTH_PUBLIC_LIB_COMMON_RECORD så fejl : Du har ikke ret til at overtage ejerskabet for en folkebiblioteksejet fællesskabspost
                ellers retur ok
             */
            if (state.getVipCoreService().hasFeature(curOwner, VipCoreLibraryRulesConnector.Rule.AUTH_PUBLIC_LIB_COMMON_RECORD)) {
                logger.info("Owner has AUTH_PUBLIC_LIB_COMMON_RECORD permission");
                if (!owner.equals(groupId)) {
                    return createErrorReply(resourceBundle.getString("update.common.record.give.public.library.error"));
                }

                if (!state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_PUBLIC_LIB_COMMON_RECORD)) {
                    return createErrorReply(resourceBundle.getString("update.common.record.take.public.library.error"));
                }
                return createOkReply();
            }

            if (!(owner.equals(groupId) && groupId.equals(curOwner))) {
                return createErrorReply(resourceBundle.getString("update.common.record.other.library.error"));
            }

            return createOkReply();
        } catch (UnsupportedEncodingException ex) {
            throw new UpdateException(ex.getMessage(), ex);
        } finally {
            logger.exit();
        }
    }

    List<MessageEntryDTO> authenticateMetaCompassField() throws UpdateException, VipCoreException {
        logger.entry();

        try {
            String groupId = state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId();

            ResourceBundle resourceBundle = ResourceBundles.getBundle("messages");

            MarcRecordReader recordReader = new MarcRecordReader(this.getRecord());
            MarcField field665 = recordReader.getField("665");

            if (state.getRawRepo().recordExists(recordReader.getRecordId(), recordReader.getAgencyIdAsInt())) {
                MarcRecord curRecord = RecordContentTransformer.decodeRecord(state.getRawRepo().fetchRecord(recordReader.getRecordId(), RawRepo.COMMON_AGENCY).getContent());
                MarcRecordReader curRecordReader = new MarcRecordReader(curRecord);
                MarcField curField665 = curRecordReader.getField("665");

                if (field665 != null && curField665 == null ||
                        field665 == null && curField665 != null ||
                        field665 != null && !field665.equals(curField665)) {
                    logger.info("Found a change in field 665 - checking if {} has permission to change field 665", groupId);
                    boolean canChangeMetaCompassRule = state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_METACOMPASS);

                    if (!canChangeMetaCompassRule) {
                        logger.info("Groupid {} does not have permission to change field 665, so returning error");
                        return createErrorReply(resourceBundle.getString("missing.auth.meta.compass"));
                    }
                }
            } else {
                if (field665 != null) {
                    logger.info("Field 665 is present in new record - chcking if {} has permission to use field 665", groupId);
                    boolean canChangeMetaCompassRule = state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_METACOMPASS);

                    if (!canChangeMetaCompassRule) {
                        logger.info("Groupid {} does not have permission to use field 665, so returning error");
                        return createErrorReply(resourceBundle.getString("missing.auth.meta.compass"));
                    }
                }
            }

            return createOkReply();
        } catch (UnsupportedEncodingException ex) {
            throw new UpdateException(ex.getMessage(), ex);
        } finally {
            logger.exit();
        }
    }

    private List<MessageEntryDTO> createErrorReply(String message) {
        MessageEntryDTO result = new MessageEntryDTO();

        result.setMessage(message);
        result.setUrlForDocumentation("");
        result.setType(TypeEnumDTO.ERROR);

        return Collections.singletonList(result);
    }

    private List<MessageEntryDTO> createOkReply() {
        return new ArrayList<>();
    }

    @Override
    public void setupMDCContext() {
        MDCUtil.setupContextForRecord(state.readRecord());
    }
}
