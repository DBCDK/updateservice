
package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.common.records.MarcSubField;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.updateservice.dto.AuthenticationDTO;
import dk.dbc.updateservice.dto.MessageEntryDTO;
import dk.dbc.updateservice.dto.TypeEnumDTO;
import dk.dbc.updateservice.dto.UpdateServiceRequestDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.LibraryGroup;
import dk.dbc.updateservice.update.NoteAndSubjectExtensionsHandler;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.utils.ResourceBundles;
import dk.dbc.vipcore.libraryrules.VipCoreLibraryRulesConnector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

class AuthenticateRecordActionTest {
    GlobalActionState state;

    @BeforeEach
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId("700400");
    }

    private ServiceResult createExpectedErrorReply(String errorString) {
        ServiceResult expected = new ServiceResult();
        expected.setStatus(UpdateStatusEnumDTO.FAILED);

        List<MessageEntryDTO> messageEntryDTOList = new ArrayList<>();

        ResourceBundle resourceBundle = ResourceBundles.getBundle("messages");

        MessageEntryDTO message = new MessageEntryDTO();
        message.setMessage(resourceBundle.getString(errorString));
        message.setUrlForDocumentation("");
        message.setType(TypeEnumDTO.ERROR);

        messageEntryDTOList.add(message);

        expected.addMessageEntryDtos(messageEntryDTOList);

        return expected;
    }

    @Test
    void testPerformAction_OK_AUTH_ROOT() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        String groupId = "123456";

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_ROOT)).thenReturn(true);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, is(ServiceResult.newOkResult()));
    }

    @Test
    void testPerformAction_OK_SameAgency() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        String groupId = "700400";

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_ROOT)).thenReturn(false);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, is(ServiceResult.newOkResult()));
    }

    @Test
    void testPerformAction_OK_School() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "300000");
        String groupId = "312345";

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_ROOT)).thenReturn(false);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, is(ServiceResult.newOkResult()));
    }

    @Test
    void testPerformAction_Fail_DifferentGroupsIds() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        String groupId = "700300";

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_ROOT)).thenReturn(false);

        ServiceResult expected = new ServiceResult();
        expected.setStatus(UpdateStatusEnumDTO.FAILED);
        expected.setEntries(new ArrayList<>());
        MessageEntryDTO messageEntryDTO = new MessageEntryDTO();
        messageEntryDTO.setType(TypeEnumDTO.ERROR);
        messageEntryDTO.setMessage("Du har ikke ret til at rette posten '20611529' da den er ejet af et andet bibliotek");
        expected.getEntries().add(messageEntryDTO);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, is(expected));
    }

    @Test
    void testPerformAction_OK_IsCommonNationalRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        String groupId = "700400";

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        List<MessageEntryDTO> validationErrors = new ArrayList<>();
        when(state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isPublishedDBCRecord(record)).thenReturn(true);
        when(state.getNoteAndSubjectExtensionsHandler().authenticateCommonRecordExtraFields(record, groupId)).thenReturn(validationErrors);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, is(ServiceResult.newOkResult()));
    }

    @Test
    void testPerformAction_OK_NotCommonNationalRecord_NewRecord_NoOwner() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).removeField("996");
        String groupId = "830010";

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isPublishedDBCRecord(record)).thenReturn(false);
        when(state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(false);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, is(createExpectedErrorReply("create.common.record.error")));
    }

    @Test
    void testPerformAction_OK_NotCommonNationalRecord_NewRecord_DifferentOwner() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("996", "a", "700400");
        String groupId = "830010";

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isPublishedDBCRecord(record)).thenReturn(false);
        when(state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(false);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, is(createExpectedErrorReply("create.common.record.other.library.error")));
    }

    @Test
    void testPerformAction_OK_NotCommonNationalRecord_NewRecord_SameOwner() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("996", "a", "830010");
        String groupId = "830010";

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isPublishedDBCRecord(record)).thenReturn(false);
        when(state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(false);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, is(ServiceResult.newOkResult()));
    }

    @Test
    void testPerformAction_OK_NotCommonNationalRecord_ExistingRecord_DBCOwner_NoAuth() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("996", "a", "DBC");
        String groupId = "830010";

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);
        List<MarcField> l1 = new ArrayList<>();
        List<MarcField> l2 = new ArrayList<>();

        when(state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isPublishedDBCRecord(record)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().marcFieldsEqualsIgnoreAmpersand(l1, l2 )).thenReturn(true);
        when(state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_DBC_RECORDS)).thenReturn(false);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, is(createExpectedErrorReply("update.common.record.owner.dbc.error")));
    }

    @Test
    void testPerformAction_OK_NotCommonNationalRecord_ExistingRecord_DBCOwner_HasAuth() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("996", "a", "DBC");
        String groupId = "830010";
        List<MarcField> l1 = new ArrayList<>();
        List<MarcField> l2 = new ArrayList<>();

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isPublishedDBCRecord(record)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().marcFieldsEqualsIgnoreAmpersand(l1, l2 )).thenReturn(true);
        when(state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_DBC_RECORDS)).thenReturn(true);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, is(ServiceResult.newOkResult()));
    }

    @Test
    void testPerformAction_OK_NotCommonNationalRecord_ExistingRecord_RETOwner_NoAuth() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("996", "a", "RET");
        String groupId = "830010";
        List<MarcField> l1 = new ArrayList<>();
        List<MarcField> l2 = new ArrayList<>();

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isPublishedDBCRecord(record)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().marcFieldsEqualsIgnoreAmpersand(l1, l2 )).thenReturn(true);
        when(state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_RET_RECORD)).thenReturn(false);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, is(createExpectedErrorReply("update.common.record.error")));
    }

    @Test
    void testPerformAction_OK_NotCommonNationalRecord_ExistingRecord_RETOwner_WrongCatLevel() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("008", "v", "4");
        new MarcRecordWriter(record).addOrReplaceSubfield("996", "a", "RET");
        String groupId = "830010";
        List<MarcField> l1 = new ArrayList<>();
        List<MarcField> l2 = new ArrayList<>();

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isPublishedDBCRecord(record)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().marcFieldsEqualsIgnoreAmpersand(l1, l2 )).thenReturn(true);
        when(state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_RET_RECORD)).thenReturn(true);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, is(createExpectedErrorReply("update.common.record.katalogiseringsniveau.error")));
    }

    @Test
    void testPerformAction_OK_NotCommonNationalRecord_ExistingRecord_RETOwner_CorrectCatLevel() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("008", "v", "5");
        new MarcRecordWriter(record).addOrReplaceSubfield("996", "a", "RET");
        String groupId = "830010";
        List<MarcField> l1 = new ArrayList<>();
        List<MarcField> l2 = new ArrayList<>();

        MarcRecord currentRecord = new MarcRecord(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("008", "v", "4");

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isPublishedDBCRecord(record)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().marcFieldsEqualsIgnoreAmpersand(l1, l2 )).thenReturn(true);
        when(state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(currentRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_RET_RECORD)).thenReturn(true);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, is(ServiceResult.newOkResult()));
    }

    @Test
    void testPerformAction_OK_NotCommonNationalRecord_ExistingRecord_RETOwner_HasAuth() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("008", "v", "0");
        new MarcRecordWriter(record).addOrReplaceSubfield("996", "a", "RET");
        String groupId = "830010";
        List<MarcField> l1 = new ArrayList<>();
        List<MarcField> l2 = new ArrayList<>();

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isPublishedDBCRecord(record)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().marcFieldsEqualsIgnoreAmpersand(l1, l2 )).thenReturn(true);
        when(state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_RET_RECORD)).thenReturn(true);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, is(ServiceResult.newOkResult()));
    }

    @Test
    void testPerformAction_OK_NotCommonNationalRecord_ExistingRecord_NoCurrentOwner() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).removeField("996");
        String groupId = "830010";
        List<MarcField> l1 = new ArrayList<>();
        List<MarcField> l2 = new ArrayList<>();

        MarcRecord curRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("996", "a", "830010");

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isPublishedDBCRecord(record)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().marcFieldsEqualsIgnoreAmpersand(l1, l2 )).thenReturn(true);
        when(state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(curRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_DBC_RECORDS)).thenReturn(false);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, is(createExpectedErrorReply("update.common.record.error")));
    }

    @Test
    void testPerformAction_OK_NotCommonNationalRecord_ExistingRecord_AuthCommonLib_DifferentOwnerAndGroup() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("996", "a", "830010");
        String groupId = "830020";
        List<MarcField> l1 = new ArrayList<>();
        List<MarcField> l2 = new ArrayList<>();

        MarcRecord curRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("996", "a", "700400");

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isPublishedDBCRecord(record)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().marcFieldsEqualsIgnoreAmpersand(l1, l2 )).thenReturn(true);
        when(state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(curRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getVipCoreService().hasFeature("700400", VipCoreLibraryRulesConnector.Rule.AUTH_PUBLIC_LIB_COMMON_RECORD)).thenReturn(true);
        when(state.getVipCoreService().getLibraryGroup("700400")).thenReturn(LibraryGroup.FBS);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, is(createExpectedErrorReply("update.common.record.give.public.library.error")));
    }

    @Test
    void testPerformAction_OK_NotCommonNationalRecord_ExistingRecord_AuthCommonLib_700300_OtherOwner_OtherGroup() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("996", "a", "830010");
        String groupId = "830020";
        List<MarcField> l1 = new ArrayList<>();
        List<MarcField> l2 = new ArrayList<>();

        MarcRecord curRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("996", "a", "700300");

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isPublishedDBCRecord(record)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().marcFieldsEqualsIgnoreAmpersand(l1, l2 )).thenReturn(true);
        when(state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(curRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getVipCoreService().hasFeature("700300", VipCoreLibraryRulesConnector.Rule.AUTH_PUBLIC_LIB_COMMON_RECORD)).thenReturn(true);
        when(state.getVipCoreService().getLibraryGroup("700300")).thenReturn(LibraryGroup.SBCI);
        when(state.getVipCoreService().getLibraryGroup("830020")).thenReturn(LibraryGroup.FBS);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, is(createExpectedErrorReply("update.common.record.change.record.700300")));
    }

    @Test
    void testPerformAction_OK_NotCommonNationalRecord_ExistingRecord_AuthCommonLib_700300_SameOwner_OtherGroup() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("996", "a", "700300");
        String groupId = "830020";
        List<MarcField> l1 = new ArrayList<>();
        List<MarcField> l2 = new ArrayList<>();

        MarcRecord curRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("996", "a", "700300");

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isPublishedDBCRecord(record)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().marcFieldsEqualsIgnoreAmpersand(l1, l2 )).thenReturn(true);
        when(state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(curRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getVipCoreService().hasFeature("700300", VipCoreLibraryRulesConnector.Rule.AUTH_PUBLIC_LIB_COMMON_RECORD)).thenReturn(true);
        when(state.getVipCoreService().getLibraryGroup("700300")).thenReturn(LibraryGroup.SBCI);
        when(state.getVipCoreService().getLibraryGroup("830020")).thenReturn(LibraryGroup.FBS);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, is(createExpectedErrorReply("update.common.record.change.record.700300")));
    }

    @Test
    void testPerformAction_OK_NotCommonNationalRecord_ExistingRecord_AuthCommonLib_700300_OtherOwner_SameGroup() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("996", "a", "830010");
        String groupId = "700300";
        List<MarcField> l1 = new ArrayList<>();
        List<MarcField> l2 = new ArrayList<>();

        MarcRecord curRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("996", "a", "700300");

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isPublishedDBCRecord(record)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().marcFieldsEqualsIgnoreAmpersand(l1, l2 )).thenReturn(true);
        when(state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(curRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getVipCoreService().hasFeature("700300", VipCoreLibraryRulesConnector.Rule.AUTH_PUBLIC_LIB_COMMON_RECORD)).thenReturn(true);
        when(state.getVipCoreService().getLibraryGroup("700300")).thenReturn(LibraryGroup.SBCI);
        when(state.getVipCoreService().getLibraryGroup("830010")).thenReturn(LibraryGroup.FBS);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, is(createExpectedErrorReply("update.common.record.change.record.700300")));
    }

    @Test
    void testPerformAction_OK_NotCommonNationalRecord_ExistingRecord_AuthCommonLib_700300_Ok() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("996", "a", "700300");
        String groupId = "700300";
        List<MarcField> l1 = new ArrayList<>();
        List<MarcField> l2 = new ArrayList<>();

        MarcRecord curRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("996", "a", "700300");

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isPublishedDBCRecord(record)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().marcFieldsEqualsIgnoreAmpersand(l1, l2 )).thenReturn(true);
        when(state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(curRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getVipCoreService().hasFeature("700300", VipCoreLibraryRulesConnector.Rule.AUTH_PUBLIC_LIB_COMMON_RECORD)).thenReturn(true);
        when(state.getVipCoreService().getLibraryGroup("870970")).thenReturn(LibraryGroup.DBC);
        when(state.getVipCoreService().getLibraryGroup("700300")).thenReturn(LibraryGroup.SBCI);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, is(ServiceResult.newOkResult()));
    }

    @Test
    void testPerformAction_OK_NotCommonNationalRecord_ExistingRecord_AuthCommonLib_NoPermission() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("996", "a", "830010");
        String groupId = "830010";
        List<MarcField> l1 = new ArrayList<>();
        List<MarcField> l2 = new ArrayList<>();

        MarcRecord curRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("996", "a", "700400");

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isPublishedDBCRecord(record)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().marcFieldsEqualsIgnoreAmpersand(l1, l2 )).thenReturn(true);
        when(state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(curRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getVipCoreService().hasFeature("830010", VipCoreLibraryRulesConnector.Rule.AUTH_PUBLIC_LIB_COMMON_RECORD)).thenReturn(false);
        when(state.getVipCoreService().hasFeature("700400", VipCoreLibraryRulesConnector.Rule.AUTH_PUBLIC_LIB_COMMON_RECORD)).thenReturn(true);
        when(state.getVipCoreService().getLibraryGroup("830010")).thenReturn(LibraryGroup.FBS);
        when(state.getVipCoreService().getLibraryGroup("700400")).thenReturn(LibraryGroup.FBS);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, is(createExpectedErrorReply("update.common.record.take.public.library.error")));
    }

    @Test
    void testPerformAction_OK_NotCommonNationalRecord_ExistingRecord_AuthCommonLib_Ok() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("996", "a", "830010");
        String groupId = "830010";
        List<MarcField> l1 = new ArrayList<>();
        List<MarcField> l2 = new ArrayList<>();

        MarcRecord curRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("996", "a", "700400");

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isPublishedDBCRecord(record)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().marcFieldsEqualsIgnoreAmpersand(l1, l2 )).thenReturn(true);
        when(state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(curRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getVipCoreService().hasFeature("830010", VipCoreLibraryRulesConnector.Rule.AUTH_PUBLIC_LIB_COMMON_RECORD)).thenReturn(true);
        when(state.getVipCoreService().hasFeature("700400", VipCoreLibraryRulesConnector.Rule.AUTH_PUBLIC_LIB_COMMON_RECORD)).thenReturn(true);
        when(state.getVipCoreService().getLibraryGroup("830010")).thenReturn(LibraryGroup.FBS);
        when(state.getVipCoreService().getLibraryGroup("700400")).thenReturn(LibraryGroup.FBS);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, is(ServiceResult.newOkResult()));
    }

    @Test
    void testPerformAction_OK_NotCommonNationalRecord_ExistingRecord_DifferentOwnerAndGroupId() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("996", "a", "830010");
        String groupId = "830020";
        List<MarcField> l1 = new ArrayList<>();
        List<MarcField> l2 = new ArrayList<>();

        MarcRecord curRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("001", "b", "870970");


        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isPublishedDBCRecord(record)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().marcFieldsEqualsIgnoreAmpersand(l1, l2 )).thenReturn(true);
        when(state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(curRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_DBC_RECORDS)).thenReturn(false);
        when(state.getVipCoreService().getLibraryGroup("830010")).thenReturn(LibraryGroup.FBS);
        when(state.getVipCoreService().getLibraryGroup("700400")).thenReturn(LibraryGroup.FBS);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, is(createExpectedErrorReply("update.common.record.other.library.error")));
    }

    @Test
    void testAuthenticateMetaCompassField_AddOk() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecord curRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("665", "q", "Grønland");

        when(state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(curRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getVipCoreService().hasFeature("700400", VipCoreLibraryRulesConnector.Rule.AUTH_METACOMPASS)).thenReturn(true);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        List<MessageEntryDTO> actual = instance.authenticateMetaCompassField();
        assertThat(actual, is(new ArrayList<>()));
    }

    @Test
    void testAuthenticateMetaCompassField_AddError() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecord curRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("665", "q", "Grønland");

        when(state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(curRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getVipCoreService().hasFeature("700400", VipCoreLibraryRulesConnector.Rule.AUTH_METACOMPASS)).thenReturn(false);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        instance.setResourceBundle();
        List<MessageEntryDTO> actual = instance.authenticateMetaCompassField();

        MessageEntryDTO expectedMessageEntryDTO = new MessageEntryDTO();
        expectedMessageEntryDTO.setType(TypeEnumDTO.ERROR);
        expectedMessageEntryDTO.setMessage("Du har ikke ret til at ændre i felt 665");
        expectedMessageEntryDTO.setUrlForDocumentation("");
        List<MessageEntryDTO> expected = new ArrayList<>();
        expected.add(expectedMessageEntryDTO);

        assertThat(actual, is(expected));
    }

    @Test
    void testAuthenticateMetaCompassField_No665HasAuth() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecord curRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");

        when(state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(curRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getVipCoreService().hasFeature("700400", VipCoreLibraryRulesConnector.Rule.AUTH_METACOMPASS)).thenReturn(true);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        List<MessageEntryDTO> actual = instance.authenticateMetaCompassField();
        assertThat(actual, is(new ArrayList<>()));
    }

    @Test
    void testAuthenticateMetaCompassField_No665NoAuth() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecord curRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");

        when(state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(curRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getVipCoreService().hasFeature("700400", VipCoreLibraryRulesConnector.Rule.AUTH_METACOMPASS)).thenReturn(false);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        List<MessageEntryDTO> actual = instance.authenticateMetaCompassField();
        assertThat(actual, is(new ArrayList<>()));
    }

    @Test
    void testAuthenticateMetaCompassField_Same665HasAuth() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecord curRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("665", "q", "Grønland");
        List<MarcField> l1 = new ArrayList<>();

        new MarcRecordWriter(curRecord).addOrReplaceSubfield("665", "q", "Grønland");
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("665", "&", "lektor");
        List<MarcField> l2 = new ArrayList<>();

        when(state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(curRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getNoteAndSubjectExtensionsHandler().marcFieldsEqualsIgnoreAmpersand(l1, l2 )).thenReturn(true);
        when(state.getVipCoreService().hasFeature("700400", VipCoreLibraryRulesConnector.Rule.AUTH_METACOMPASS)).thenReturn(true);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        List<MessageEntryDTO> actual = instance.authenticateMetaCompassField();
        assertThat(actual, is(new ArrayList<>()));
    }

    // TODO ing
    @Test
    void testAuthenticateMetaCompassField_Same665NoAuth() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecord curRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("665", "q", "Grønland");
        List<MarcField> l1 = new ArrayList<>();

        new MarcRecordWriter(curRecord).addOrReplaceSubfield("665", "&", "lektor");
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("665", "q", "Grønland");
        List<MarcField> l2 = new ArrayList<>();

        when(state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(true);
        when(state.getNoteAndSubjectExtensionsHandler().marcFieldsEqualsIgnoreAmpersand(l1, l2 )).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(curRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getVipCoreService().hasFeature("700400", VipCoreLibraryRulesConnector.Rule.AUTH_METACOMPASS)).thenReturn(false);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        List<MessageEntryDTO> actual = instance.authenticateMetaCompassField();
        assertThat(actual, is(new ArrayList<>()));
    }

    @Test
    void testAuthenticateMetaCompassField_NewRecordNo665() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");

        when(state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(false);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        List<MessageEntryDTO> actual = instance.authenticateMetaCompassField();
        assertThat(actual, is(new ArrayList<>()));
    }

    @Test
    void testAuthenticateMetaCompassField_NewRecordNew665HasAuth() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("665", "q", "Grønland");

        when(state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(false);
        when(state.getVipCoreService().hasFeature("700400", VipCoreLibraryRulesConnector.Rule.AUTH_METACOMPASS)).thenReturn(true);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        List<MessageEntryDTO> actual = instance.authenticateMetaCompassField();
        assertThat(actual, is(new ArrayList<>()));
    }

    @Test
    void testAuthenticateMetaCompassField_NewRecordNew665NoAuth() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("665", "q", "Grønland");

        when(state.getRawRepo().recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(false);
        when(state.getVipCoreService().hasFeature("700400", VipCoreLibraryRulesConnector.Rule.AUTH_METACOMPASS)).thenReturn(false);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        instance.setResourceBundle();
        List<MessageEntryDTO> actual = instance.authenticateMetaCompassField();

        MessageEntryDTO expectedMessageEntryDTO = new MessageEntryDTO();
        expectedMessageEntryDTO.setType(TypeEnumDTO.ERROR);
        expectedMessageEntryDTO.setMessage("Du har ikke ret til at ændre i felt 665");
        expectedMessageEntryDTO.setUrlForDocumentation("");
        List<MessageEntryDTO> expected = new ArrayList<>();
        expected.add(expectedMessageEntryDTO);

        assertThat(actual, is(expected));
    }
}
