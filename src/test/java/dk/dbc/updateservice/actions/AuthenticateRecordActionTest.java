/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.records.MarcRecordWriter;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.updateservice.dto.*;
import dk.dbc.updateservice.update.RawRepo;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

public class AuthenticateRecordActionTest {
    GlobalActionState state;

    @Before
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
    public void testPerformAction_OK_AUTH_ROOT() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        String groupId = "123456";

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_ROOT)).thenReturn(true);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, equalTo(ServiceResult.newOkResult()));
    }

    @Test
    public void testPerformAction_OK_SameAgency() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        String groupId = "700400";

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_ROOT)).thenReturn(false);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, equalTo(ServiceResult.newOkResult()));
    }

    @Test
    public void testPerformAction_OK_School() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "300000");
        String groupId = "312345";

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_ROOT)).thenReturn(false);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, equalTo(ServiceResult.newOkResult()));
    }

    @Test
    public void testPerformAction_Fail_DifferentGroupsIds() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        String groupId = "700300";

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_ROOT)).thenReturn(false);

        ServiceResult expected = new ServiceResult();
        expected.setStatus(UpdateStatusEnumDTO.OK);
        expected.setEntries(new ArrayList<>());
        MessageEntryDTO messageEntryDTO = new MessageEntryDTO();
        messageEntryDTO.setMessage("Du har ikke ret til at rette posten '20611529' da den er ejet af et andet bibliotek");
        expected.getEntries().add(messageEntryDTO);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, equalTo(expected));
    }

    @Test
    public void testPerformAction_OK_IsCommonNationalRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        String groupId = "830010";

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        List<MessageEntryDTO> validationErrors = new ArrayList<>();
        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isNationalCommonRecord(record)).thenReturn(true);
        when(state.getNoteAndSubjectExtensionsHandler().authenticateCommonRecordExtraFields(record, groupId)).thenReturn(validationErrors);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, equalTo(ServiceResult.newOkResult()));
    }

    @Test
    public void testPerformAction_Fail_IsCommonNationalRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        String groupId = "830010";

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        List<MessageEntryDTO> validationErrors = new ArrayList<>();
        MessageEntryDTO messageEntryDTO = new MessageEntryDTO();
        messageEntryDTO.setMessage("fejl");
        validationErrors.add(messageEntryDTO);
        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isNationalCommonRecord(record)).thenReturn(true);
        when(state.getNoteAndSubjectExtensionsHandler().authenticateCommonRecordExtraFields(record, groupId)).thenReturn(validationErrors);

        ServiceResult expected = new ServiceResult();
        expected.setStatus(UpdateStatusEnumDTO.OK);
        expected.setEntries(new ArrayList<>());
        MessageEntryDTO expectedMessageEntryDTO = new MessageEntryDTO();
        expectedMessageEntryDTO.setMessage("fejl");
        expected.getEntries().add(expectedMessageEntryDTO);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, equalTo(expected));
    }

    @Test
    public void testPerformAction_OK_NotCommonNationalRecord_NewRecord_NoOwner() throws Exception {
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

        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isNationalCommonRecord(record)).thenReturn(false);
        when(state.getRawRepo().recordExists(reader.recordId(), reader.agencyIdAsInteger())).thenReturn(false);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, equalTo(createExpectedErrorReply("create.common.record.error")));
    }

    @Test
    public void testPerformAction_OK_NotCommonNationalRecord_NewRecord_DifferentOwner() throws Exception {
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

        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isNationalCommonRecord(record)).thenReturn(false);
        when(state.getRawRepo().recordExists(reader.recordId(), reader.agencyIdAsInteger())).thenReturn(false);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, equalTo(createExpectedErrorReply("create.common.record.other.library.error")));
    }

    @Test
    public void testPerformAction_OK_NotCommonNationalRecord_NewRecord_SameOwner() throws Exception {
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

        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isNationalCommonRecord(record)).thenReturn(false);
        when(state.getRawRepo().recordExists(reader.recordId(), reader.agencyIdAsInteger())).thenReturn(false);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, equalTo(ServiceResult.newOkResult()));
    }

    @Test
    public void testPerformAction_OK_NotCommonNationalRecord_ExistingRecord_DBCOwner_NoAuth() throws Exception {
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

        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isNationalCommonRecord(record)).thenReturn(false);
        when(state.getRawRepo().recordExists(reader.recordId(), reader.agencyIdAsInteger())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.recordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_DBC_RECORDS)).thenReturn(false);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, equalTo(createExpectedErrorReply("update.common.record.owner.dbc.error")));
    }

    @Test
    public void testPerformAction_OK_NotCommonNationalRecord_ExistingRecord_DBCOwner_HasAuth() throws Exception {
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

        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isNationalCommonRecord(record)).thenReturn(false);
        when(state.getRawRepo().recordExists(reader.recordId(), reader.agencyIdAsInteger())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.recordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_DBC_RECORDS)).thenReturn(true);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, equalTo(ServiceResult.newOkResult()));
    }

    @Test
    public void testPerformAction_OK_NotCommonNationalRecord_ExistingRecord_RETOwner_NoAuth() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("996", "a", "RET");
        String groupId = "830010";

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isNationalCommonRecord(record)).thenReturn(false);
        when(state.getRawRepo().recordExists(reader.recordId(), reader.agencyIdAsInteger())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.recordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_RET_RECORD)).thenReturn(false);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, equalTo(createExpectedErrorReply("update.common.record.error")));
    }

    @Test
    public void testPerformAction_OK_NotCommonNationalRecord_ExistingRecord_RETOwner_WrongCatLevel() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("008", "v", "42");
        new MarcRecordWriter(record).addOrReplaceSubfield("996", "a", "RET");
        String groupId = "830010";

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isNationalCommonRecord(record)).thenReturn(false);
        when(state.getRawRepo().recordExists(reader.recordId(), reader.agencyIdAsInteger())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.recordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_RET_RECORD)).thenReturn(true);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, equalTo(createExpectedErrorReply("update.common.record.katalogiseringsniveau.error")));
    }

    @Test
    public void testPerformAction_OK_NotCommonNationalRecord_ExistingRecord_RETOwner_HasAuth() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("008", "v", "0");
        new MarcRecordWriter(record).addOrReplaceSubfield("996", "a", "RET");
        String groupId = "830010";

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isNationalCommonRecord(record)).thenReturn(false);
        when(state.getRawRepo().recordExists(reader.recordId(), reader.agencyIdAsInteger())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.recordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_RET_RECORD)).thenReturn(true);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, equalTo(ServiceResult.newOkResult()));
    }

    @Test
    public void testPerformAction_OK_NotCommonNationalRecord_ExistingRecord_NoCurrentOwner() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).removeField("996");
        String groupId = "830010";

        MarcRecord curRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("996", "a", "830010");

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isNationalCommonRecord(record)).thenReturn(false);
        when(state.getRawRepo().recordExists(reader.recordId(), reader.agencyIdAsInteger())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.recordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(curRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_DBC_RECORDS)).thenReturn(false);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, equalTo(createExpectedErrorReply("update.common.record.error")));
    }

    @Test
    public void testPerformAction_OK_NotCommonNationalRecord_ExistingRecord_AuthCommonLib_DifferentOwnerAndGroup() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("996", "a", "830010");
        String groupId = "830020";

        MarcRecord curRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("996", "a", "700400");

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isNationalCommonRecord(record)).thenReturn(false);
        when(state.getRawRepo().recordExists(reader.recordId(), reader.agencyIdAsInteger())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.recordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(curRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getOpenAgencyService().hasFeature("700400", LibraryRuleHandler.Rule.AUTH_PUBLIC_LIB_COMMON_RECORD)).thenReturn(true);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, equalTo(createExpectedErrorReply("update.common.record.give.public.library.error")));
    }

    @Test
    public void testPerformAction_OK_NotCommonNationalRecord_ExistingRecord_AuthCommonLib_700300_OtherOwner_OtherGroup() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("996", "a", "830010");
        String groupId = "830020";

        MarcRecord curRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("996", "a", "700300");

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isNationalCommonRecord(record)).thenReturn(false);
        when(state.getRawRepo().recordExists(reader.recordId(), reader.agencyIdAsInteger())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.recordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(curRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getOpenAgencyService().hasFeature("700300", LibraryRuleHandler.Rule.AUTH_PUBLIC_LIB_COMMON_RECORD)).thenReturn(true);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, equalTo(createExpectedErrorReply("update.common.record.change.record.700300")));
    }

    @Test
    public void testPerformAction_OK_NotCommonNationalRecord_ExistingRecord_AuthCommonLib_700300_SameOwner_OtherGroup() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("996", "a", "700300");
        String groupId = "830020";

        MarcRecord curRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("996", "a", "700300");

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isNationalCommonRecord(record)).thenReturn(false);
        when(state.getRawRepo().recordExists(reader.recordId(), reader.agencyIdAsInteger())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.recordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(curRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getOpenAgencyService().hasFeature("700300", LibraryRuleHandler.Rule.AUTH_PUBLIC_LIB_COMMON_RECORD)).thenReturn(true);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, equalTo(createExpectedErrorReply("update.common.record.change.record.700300")));
    }

    @Test
    public void testPerformAction_OK_NotCommonNationalRecord_ExistingRecord_AuthCommonLib_700300_OtherOwner_SameGroup() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("996", "a", "830010");
        String groupId = "700300";

        MarcRecord curRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("996", "a", "700300");

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isNationalCommonRecord(record)).thenReturn(false);
        when(state.getRawRepo().recordExists(reader.recordId(), reader.agencyIdAsInteger())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.recordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(curRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getOpenAgencyService().hasFeature("700300", LibraryRuleHandler.Rule.AUTH_PUBLIC_LIB_COMMON_RECORD)).thenReturn(true);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, equalTo(createExpectedErrorReply("update.common.record.change.record.700300")));
    }

    @Test
    public void testPerformAction_OK_NotCommonNationalRecord_ExistingRecord_AuthCommonLib_700300_Ok() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("996", "a", "700300");
        String groupId = "700300";

        MarcRecord curRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("996", "a", "700300");

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isNationalCommonRecord(record)).thenReturn(false);
        when(state.getRawRepo().recordExists(reader.recordId(), reader.agencyIdAsInteger())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.recordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(curRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getOpenAgencyService().hasFeature("700300", LibraryRuleHandler.Rule.AUTH_PUBLIC_LIB_COMMON_RECORD)).thenReturn(true);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, equalTo(ServiceResult.newOkResult()));
    }

    @Test
    public void testPerformAction_OK_NotCommonNationalRecord_ExistingRecord_AuthCommonLib_NoPermission() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("996", "a", "830010");
        String groupId = "830010";

        MarcRecord curRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("996", "a", "700400");

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isNationalCommonRecord(record)).thenReturn(false);
        when(state.getRawRepo().recordExists(reader.recordId(), reader.agencyIdAsInteger())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.recordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(curRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getOpenAgencyService().hasFeature("830010", LibraryRuleHandler.Rule.AUTH_PUBLIC_LIB_COMMON_RECORD)).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature("700400", LibraryRuleHandler.Rule.AUTH_PUBLIC_LIB_COMMON_RECORD)).thenReturn(true);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, equalTo(createExpectedErrorReply("update.common.record.take.public.library.error")));
    }

    @Test
    public void testPerformAction_OK_NotCommonNationalRecord_ExistingRecord_AuthCommonLib_Ok() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("996", "a", "830010");
        String groupId = "830010";

        MarcRecord curRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("996", "a", "700400");

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isNationalCommonRecord(record)).thenReturn(false);
        when(state.getRawRepo().recordExists(reader.recordId(), reader.agencyIdAsInteger())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.recordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(curRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getOpenAgencyService().hasFeature("830010", LibraryRuleHandler.Rule.AUTH_PUBLIC_LIB_COMMON_RECORD)).thenReturn(true);
        when(state.getOpenAgencyService().hasFeature("700400", LibraryRuleHandler.Rule.AUTH_PUBLIC_LIB_COMMON_RECORD)).thenReturn(true);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, equalTo(ServiceResult.newOkResult()));
    }

    @Test
    public void testPerformAction_OK_NotCommonNationalRecord_ExistingRecord_DifferentOwnerAndGroupId() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        new MarcRecordWriter(record).addOrReplaceSubfield("996", "a", "830010");
        String groupId = "830020";

        MarcRecord curRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(curRecord).addOrReplaceSubfield("001", "b", "870970");


        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isNationalCommonRecord(record)).thenReturn(false);
        when(state.getRawRepo().recordExists(reader.recordId(), reader.agencyIdAsInteger())).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.recordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(curRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_DBC_RECORDS)).thenReturn(false);

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, equalTo(createExpectedErrorReply("update.common.record.other.library.error")));
    }

}
