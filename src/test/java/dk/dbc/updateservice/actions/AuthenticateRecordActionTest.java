/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordWriter;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.updateservice.dto.*;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AuthenticateRecordActionTest {
    GlobalActionState state;
    private Properties settings;

    @Before
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId("700400");
        settings = new UpdateTestUtils().getSettings();
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

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, settings, record);
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

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, settings, record);
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

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, settings, record);
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

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, settings, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, equalTo(expected));
    }

    @Test
    public void testPerformAction_OK_NotCommonNationalRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");
        String groupId = "830010";

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId(groupId);
        UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);
        state.setUpdateServiceRequestDTO(updateServiceRequestDTO);

        ObjectMapper mapper = new ObjectMapper();
        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_ROOT)).thenReturn(false);
        when(state.getNoteAndSubjectExtensionsHandler().isNationalCommonRecord(record)).thenReturn(false);
        when(state.getScripter().callMethod("authenticateRecord", mapper.writeValueAsString(record), state.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId(), groupId, settings)).thenReturn("[]");

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, settings, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, equalTo(ServiceResult.newOkResult()));
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

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, settings, record);
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

        AuthenticateRecordAction instance = new AuthenticateRecordAction(state, settings, record);
        ServiceResult actual = instance.performAction();
        assertThat(actual, equalTo(expected));
    }

}
