/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.updateservice.dto.AuthenticationDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.javascript.ScripterException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class ValidateSchemaActionTest {
    private GlobalActionState state;
    private Properties settings;
    private String templateGroup = "fbs";

    @Before
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
        state.getUpdateServiceRequestDTO().setSchemaName("book");
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId("400700");
        state.setTemplateGroup(templateGroup);
        settings = new UpdateTestUtils().getSettings();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testScripterIsNull() throws Exception {
        state.getUpdateServiceRequestDTO().setSchemaName("name");
        state.getUpdateServiceRequestDTO().setAuthenticationDTO(new AuthenticationDTO());
        state.setScripter(null);
        ValidateSchemaAction validateSchemaAction = new ValidateSchemaAction(state, settings);
        validateSchemaAction.performAction();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSettingsIsNull() throws Exception {
        settings = null;
        state.getUpdateServiceRequestDTO().setAuthenticationDTO(new AuthenticationDTO());
        ValidateSchemaAction validateSchemaAction = new ValidateSchemaAction(state, settings);
        validateSchemaAction.performAction();
    }

    @Test
    public void testValidateSchemaIsNull() throws Exception {
        state.getUpdateServiceRequestDTO().setSchemaName(null);
        state.getUpdateServiceRequestDTO().setAuthenticationDTO(new AuthenticationDTO());
        ValidateSchemaAction validateSchemaAction = new ValidateSchemaAction(state, settings);
        assertThat(validateSchemaAction.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, "validateSchema must not be empty")));
    }

    @Test
    public void testScripterException() throws Exception {
        ValidateSchemaAction validateSchemaAction = new ValidateSchemaAction(state, settings);
        ScripterException ex = new ScripterException("message");
        when(state.getScripter().callMethod(anyString(), anyString(), eq("400700"), anyString(), eq(settings))).thenThrow(ex);
        assertThat(validateSchemaAction.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, ex.getMessage())));
    }

    @Test
    public void testScripterWrongDatatype() throws Exception {
        ValidateSchemaAction validateSchemaAction = new ValidateSchemaAction(state, settings);
        when(state.getScripter().callMethod(anyString(), anyString(), eq("400700"), anyString(), eq(settings))).thenReturn(27);
        String message = "The JavaScript function checkTemplate must return a boolean value.";
        assertThat(validateSchemaAction.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message)));
    }

    @Test
    public void testSchemaFound() throws Exception {
        ValidateSchemaAction validateSchemaAction = new ValidateSchemaAction(state, settings);
        when(state.getScripter().callMethod(eq("checkTemplate"), eq("book"), eq("400700"), anyString(), eq(settings))).thenReturn(true);
        assertThat(validateSchemaAction.performAction(), equalTo(ServiceResult.newOkResult()));
    }

    @Test
    public void testSchemaNotFound() throws Exception {
        ValidateSchemaAction validateSchemaAction = new ValidateSchemaAction(state, settings);
        when(state.getScripter().callMethod(eq("checkTemplate"), eq("book"), eq("400700"), anyString(), eq(settings))).thenReturn(false);
        String message = String.format(state.getMessages().getString("update.schema.not.found"), state.getSchemaName());
        assertThat(validateSchemaAction.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message)));
    }
}
