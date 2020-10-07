/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.opencat.connector.OpencatBusinessConnectorException;
import dk.dbc.updateservice.dto.AuthenticationDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
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
        OpencatBusinessConnectorException ex = new OpencatBusinessConnectorException("message");
        when(state.getOpencatBusiness().checkTemplate(anyString(), eq("400700"), anyString())).thenThrow(ex);
        assertThat(validateSchemaAction.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, ex.getMessage())));
    }

    @Test
    public void testSchemaFound() throws Exception {
        ValidateSchemaAction validateSchemaAction = new ValidateSchemaAction(state, settings);
        when(state.getOpencatBusiness().checkTemplate(eq("book"), eq("400700"), anyString())).thenReturn(true);
        assertThat(validateSchemaAction.performAction(), equalTo(ServiceResult.newOkResult()));
    }

    @Test
    public void testSchemaNotFound() throws Exception {
        ValidateSchemaAction validateSchemaAction = new ValidateSchemaAction(state, settings);
        when(state.getOpencatBusiness().checkTemplate(eq("book"), eq("400700"), anyString())).thenReturn(false);
        String message = String.format(state.getMessages().getString("update.schema.not.found"), state.getSchemaName());
        assertThat(validateSchemaAction.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message)));
    }
}
