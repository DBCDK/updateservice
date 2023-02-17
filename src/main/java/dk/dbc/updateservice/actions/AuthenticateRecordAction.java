package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.utils.RecordContentTransformer;
import dk.dbc.updateservice.dto.MessageEntryDTO;
import dk.dbc.updateservice.dto.TypeEnumDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.NoteAndSubjectExtensionsHandler;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.utils.DeferredLogger;
import dk.dbc.updateservice.utils.MDCUtil;
import dk.dbc.updateservice.utils.ResourceBundles;
import dk.dbc.vipcore.exception.VipCoreException;
import dk.dbc.vipcore.libraryrules.VipCoreLibraryRulesConnector;

import javax.ejb.EJBException;
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
    private static final DeferredLogger LOGGER = new DeferredLogger(AuthenticateRecordAction.class);

    /**
     * Constructs an instance for authentication of a record
     * @param globalActionState State object containing data with data from request.
     * @param marcRecord        The record to be authenticated
     */
    public AuthenticateRecordAction(GlobalActionState globalActionState, MarcRecord marcRecord) {
        super(AuthenticateRecordAction.class.getSimpleName(), globalActionState, marcRecord);
    }
    ResourceBundle resourceBundle;

    public void setResourceBundle() {
        this.resourceBundle = ResourceBundles.getBundle("messages");
    }

    @Override
    public ServiceResult performAction() throws UpdateException {
        return LOGGER.callChecked(log -> {
            try {
                setResourceBundle();
                log.info("Login user: {}/{}", state.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId(), state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId());

                final List<MessageEntryDTO> errors = authenticateRecord();
                final ServiceResult result = new ServiceResult();
                result.addMessageEntryDtos(errors);

                final MarcRecordReader reader = new MarcRecordReader(marcRecord);
                final String recordId = reader.getRecordId();
                final String agencyId = reader.getAgencyId();
                if (result.hasErrors()) {
                    log.warn("Authenticating of record {{}:{}} with user {}/{} failed", recordId, agencyId, state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), state.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId());
                    result.setStatus(UpdateStatusEnumDTO.FAILED);
                } else {
                    log.info("Authenticating record {{}:{}} with user {}/{} successfully", recordId, agencyId, state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), state.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId());
                    result.setStatus(UpdateStatusEnumDTO.OK);
                }
                return result;
            } catch (VipCoreException ex) {
                final String message = String.format(state.getMessages().getString("vipcore.authenticate.record.error"), ex.getMessage());
                log.error(message);
                log.warn("Exception doing authentication: ", ex);
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
            } catch (EJBException ex) {
                final Throwable businessException = findServiceException(ex);
                String message = String.format(state.getMessages().getString("internal.authenticate.record.error"), businessException.getMessage());
                log.error(message);
                log.warn("Exception doing authentication: ", businessException);
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
            }
        });
    }

    private List<MessageEntryDTO> authenticateRecord() throws UpdateException, VipCoreException {
        final List<MessageEntryDTO> result = new ArrayList<>();
        final String groupId = state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId();
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);
        return LOGGER.<List<MessageEntryDTO>, UpdateException, VipCoreException>callChecked2(log -> {
            // First check if the group is "root" - if so just return as no further validation is necessary
            try {
                if (state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_ROOT)) {
                    log.info("Group is AUTH_ROOT -> exit OK");
                    return result;
                }
            } catch (VipCoreException e) {
                throw new UpdateException("Caught VipCoreException", e);
            }

            // If the group is identical to the agency of the record then authenticate OK
            if (groupId.equals(reader.getAgencyId())) {
                log.info("Group is identical with agencyId so it's a local record-> exit OK");
                return result;
            }

            if (RawRepo.COMMON_AGENCY == reader.getAgencyIdAsInt()) {
                log.info("Record belongs to 870970");
                final NoteAndSubjectExtensionsHandler noteAndSubjectExtensionsHandler = state.getNoteAndSubjectExtensionsHandler();
                List<MessageEntryDTO> validationErrors;

                MarcRecord curRecord = new MarcRecord();
                if (rawRepo.recordExists(reader.getRecordId(), RawRepo.COMMON_AGENCY)) {
                    curRecord = RecordContentTransformer.decodeRecord(state.getRawRepo().fetchRecord(reader.getRecordId(), RawRepo.COMMON_AGENCY).getContent());
                }
                if (noteAndSubjectExtensionsHandler.isPublishedDBCRecord(curRecord)) {
                    log.info("Record is published national common record");
                    validationErrors = noteAndSubjectExtensionsHandler.authenticateCommonRecordExtraFields(marcRecord, groupId);
                } else {
                    log.info("Record is national common record still under production");
                    validationErrors = authenticateCommonRecord();
                }

                validationErrors.addAll(authenticateMetaCompassField());

                if (!validationErrors.isEmpty()) {
                    log.info("Validation errors!");
                    result.addAll(validationErrors);
                    log.info("Number of errors: {}", result.size());
                }

                return result;
            }

            int groupIdAsInt = Integer.parseInt(groupId);
            if (300000 <= groupIdAsInt && groupIdAsInt <= 399999 &&
                    reader.getAgencyIdAsInt() == RawRepo.SCHOOL_COMMON_AGENCY) {
                log.info("Group is school agency and record is owner by 300000 -> exit OK");
                return result;
            }

            final String message = String.format(resourceBundle.getString("edit.record.other.library.error"), reader.getRecordId());
            final MessageEntryDTO messageEntryDTO = new MessageEntryDTO();
            messageEntryDTO.setMessage(message);
            messageEntryDTO.setType(TypeEnumDTO.ERROR);
            result.add(messageEntryDTO);

            return result;
        });
    }

    private List<MessageEntryDTO> authenticateCommonRecord() throws UpdateException, VipCoreException {
        return LOGGER.<List<MessageEntryDTO>, UpdateException, VipCoreException>callChecked2(log -> {
            final MarcRecordReader reader = new MarcRecordReader(marcRecord);
            final String recordId = reader.getRecordId();
            final int agencyId = reader.getAgencyIdAsInt();
            final String groupId = state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId();
            final String owner = reader.getValue("996", "a");

            log.info("Record agency: {}", agencyId);
            log.info("New owner: {}", owner);

            if (!state.getRawRepo().recordExists(recordId, agencyId)) {
                log.debug("Checking authentication for new common record.");

                if (owner == null || owner.isEmpty()) {
                    return createErrorReply(resourceBundle.getString("create.common.record.error"));
                }

                if (!owner.equals(groupId)) {
                    return createErrorReply(resourceBundle.getString("create.common.record.other.library.error"));
                }

                return createOkReply();
            }

            log.debug("Checking authentication for updating existing common record.");
            final MarcRecord curRecord = RecordContentTransformer.decodeRecord(state.getRawRepo().fetchRecord(recordId, RawRepo.COMMON_AGENCY).getContent());
            final MarcRecordReader curReader = new MarcRecordReader(curRecord);
            final String curOwner = curReader.getValue("996", "a");

            log.info("Current owner: {}", curOwner);

            if ("DBC".equals(curOwner)) {
                log.info("Owner is DBC");
                if (!state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_DBC_RECORDS)) {
                    return createErrorReply(resourceBundle.getString("update.common.record.owner.dbc.error"));
                }
                return createOkReply();
            }

            if ("RET".equals(curOwner)) {
                log.info("Owner is RET");
                if (!state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_RET_RECORD)) {
                    return createErrorReply(resourceBundle.getString("update.common.record.error"));
                }
                log.info("New value of 008 *v is {}", reader.getValue("008", "v"));
                log.info("Current value of 008 *v is {}", curReader.getValue("008", "v"));
                if ("4".equals(curReader.getValue("008", "v"))) {
                    final List<String> allowedKatValues = Arrays.asList("0", "1", "5");
                    if (!allowedKatValues.contains(reader.getValue("008", "v"))) {
                        return createErrorReply(resourceBundle.getString("update.common.record.katalogiseringsniveau.error"));
                    }
                }
                return createOkReply();
            }

            if (owner == null || owner.isEmpty()) {
                log.info("Owner is empty");
                return createErrorReply(resourceBundle.getString("update.common.record.error"));
            }

        /*
            If a record is owned by SBCI then a non-SBCI agency can't change the record. In other words, if the
            current owner (996 *a) is SBCI then the agency updating the record and the owner of the updated record
            must both be SBCI.
         */
            if (state.getVipCoreService().getLibraryGroup(curOwner).isSBCI() &&
                    !(state.getVipCoreService().getLibraryGroup(groupId).isSBCI() &&
                            state.getVipCoreService().getLibraryGroup(owner).isSBCI())) {
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
                log.info("Owner has AUTH_PUBLIC_LIB_COMMON_RECORD permission");
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
        });
    }

    List<MessageEntryDTO> authenticateMetaCompassField() throws UpdateException, VipCoreException {
        return LOGGER.<List<MessageEntryDTO>, UpdateException, VipCoreException>callChecked2(log -> {
            final String groupId = state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId();

            final MarcRecordReader recordReader = new MarcRecordReader(this.getRecord());
            final MarcField field665 = recordReader.getField("665");

            if (state.getRawRepo().recordExists(recordReader.getRecordId(), recordReader.getAgencyIdAsInt())) {
                final MarcRecord curRecord = RecordContentTransformer.decodeRecord(state.getRawRepo().fetchRecord(recordReader.getRecordId(), RawRepo.COMMON_AGENCY).getContent());
                final MarcRecordReader curRecordReader = new MarcRecordReader(curRecord);
                final MarcField curField665 = curRecordReader.getField("665");

                if (field665 != null && curField665 == null ||
                        field665 == null && curField665 != null ||
                        field665 != null && !field665.equals(curField665)) {
                    log.info("Found a change in field 665 - checking if {} has permission to change field 665", groupId);
                    final boolean canChangeMetaCompassRule = state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_METACOMPASS);

                    if (!canChangeMetaCompassRule) {
                        log.info("GroupId {} does not have permission to change field 665, so returning error", groupId);
                        return createErrorReply(resourceBundle.getString("missing.auth.meta.compass"));
                    }
                }
            } else {
                if (field665 != null) {
                    log.info("Field 665 is present in new record - checking if {} has permission to use field 665", groupId);
                    final boolean canChangeMetaCompassRule = state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_METACOMPASS);

                    if (!canChangeMetaCompassRule) {
                        log.info("GroupId {} does not have permission to use field 665, so returning error", groupId);
                        return createErrorReply(resourceBundle.getString("missing.auth.meta.compass"));
                    }
                }
            }

            return createOkReply();
        });
    }

    private List<MessageEntryDTO> createErrorReply(String message) {
        final MessageEntryDTO result = new MessageEntryDTO();

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
