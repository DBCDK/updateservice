package dk.dbc.updateservice.actions;

import dk.dbc.updateservice.dto.MessageEntryDto;
import dk.dbc.updateservice.dto.TypeEnumDto;
import dk.dbc.updateservice.dto.UpdateStatusEnumDto;
import dk.dbc.updateservice.javascript.ScripterException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AuthenticateRecordActionTest {
    GlobalActionState state;

    @Before
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        state.getUpdateServiceRequestDto().getAuthenticationDto().setGroupId("700400");
    }

    /**
     * Test AuthenticateRecordAction.performAction() for authentication of a record no
     * errors or warnings.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A record.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Authenticate the record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return ServiceResult with status OK.
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_OK() throws Exception {
        when(state.getAuthenticator().authenticateRecord(state)).thenReturn(new ArrayList<>());
        AuthenticateRecordAction authenticateRecordAction = new AuthenticateRecordAction(state);
        ServiceResult serviceResult = authenticateRecordAction.performAction();
        assertThat(serviceResult, equalTo(ServiceResult.newOkResult()));
//        assertThat(authenticateRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));
        verify(state.getAuthenticator()).authenticateRecord(state);
    }

    /**
     * Test AuthenticateRecordAction.performAction() for authentication of a record with
     * authentication errors.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A record.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Authenticate the record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return ServiceResult with status VALIDATION_ERROR and a list of authentication errors.
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_Errors() throws Exception {
        List<MessageEntryDto> entries = UpdateTestUtils.createMessageEntryList(TypeEnumDto.ERROR, "error");
        when(state.getAuthenticator().authenticateRecord(state)).thenReturn(entries);
        AuthenticateRecordAction authenticateRecordAction = new AuthenticateRecordAction(state);
        ServiceResult expected = ServiceResult.newStatusResult(UpdateStatusEnumDto.FAILED);
        expected.setEntries(entries);
        assertThat(authenticateRecordAction.performAction(), equalTo(expected));
        verify(state.getAuthenticator()).authenticateRecord(state);
    }

    /**
     * Test AuthenticateRecordAction.performAction() for authentication of a record with
     * authentication warnings.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A record.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Authenticate the record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return ServiceResult with status OK and a list of authentication warnings.
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_Warnings() throws Exception {
        List<MessageEntryDto> entries = UpdateTestUtils.createMessageEntryList(TypeEnumDto.WARNING, "warning");
        when(state.getAuthenticator().authenticateRecord(state)).thenReturn(entries);
        AuthenticateRecordAction authenticateRecordAction = new AuthenticateRecordAction(state);
        ServiceResult expected = ServiceResult.newStatusResult(UpdateStatusEnumDto.OK);
        expected.setEntries(entries);
        assertThat(authenticateRecordAction.performAction(), equalTo(expected));
        verify(state.getAuthenticator()).authenticateRecord(state);
    }

    /**
     * Test AuthenticateRecordAction.performAction() for authentication of a record with
     * exception handling.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * Authenticate a record.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Authenticator throws an exception.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return ServiceResult with status FAILED_VALIDATION_INTERNAL_ERROR and a list
     * with the exception message.
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_Exception() throws Exception {
        ScripterException ex = new ScripterException("error");
        when(state.getAuthenticator().authenticateRecord(state)).thenThrow(ex);
        AuthenticateRecordAction instance = new AuthenticateRecordAction(state);
        String message = String.format(state.getMessages().getString("internal.authenticate.record.error"), ex.getMessage());
        ServiceResult expected = ServiceResult.newErrorResult(UpdateStatusEnumDto.FAILED, message, state);
        Assert.assertThat(instance.performAction(), equalTo(expected));
        verify(state.getAuthenticator()).authenticateRecord(state);
    }
}
