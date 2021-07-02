/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.opencat.connector.OpencatBusinessConnectorException;
import dk.dbc.updateservice.dto.AuthenticationDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class ValidateSchemaActionTest {
    private GlobalActionState state;
    private Properties settings;

    @BeforeEach
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
        state.getUpdateServiceRequestDTO().setSchemaName("book");
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId("400700");
        final String templateGroup = "fbs";
        state.setTemplateGroup(templateGroup);
        settings = new UpdateTestUtils().getSettings();
        MDC.put("trackingId", "ValidateSchemaActionTest");
    }

    @AfterAll
    static void afterAll() {
        MDC.clear();
    }

    @Test
    void testSettingsIsNull() {
        settings = null;
        state.getUpdateServiceRequestDTO().setAuthenticationDTO(new AuthenticationDTO());
        ValidateSchemaAction validateSchemaAction = new ValidateSchemaAction(state, settings);
        assertThrows(IllegalArgumentException.class, validateSchemaAction::performAction);
    }

    @Test
    void testValidateSchemaIsNull() throws Exception {
        state.getUpdateServiceRequestDTO().setSchemaName(null);
        state.getUpdateServiceRequestDTO().setAuthenticationDTO(new AuthenticationDTO());
        ValidateSchemaAction validateSchemaAction = new ValidateSchemaAction(state, settings);
        assertThat(validateSchemaAction.performAction(), is(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, "validateSchema must not be empty")));
    }

    @Test
    void testScripterException() throws Exception {
        ValidateSchemaAction validateSchemaAction = new ValidateSchemaAction(state, settings);
        OpencatBusinessConnectorException ex = new OpencatBusinessConnectorException("message");
        when(state.getOpencatBusiness().checkTemplate(anyString(), eq("400700"), anyString(), eq("ValidateSchemaActionTest"))).thenThrow(ex);
        assertThat(validateSchemaAction.performAction(), is(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, ex.getMessage())));
    }

    @Test
    void testSchemaFound() throws Exception {
        ValidateSchemaAction validateSchemaAction = new ValidateSchemaAction(state, settings);
        when(state.getOpencatBusiness().checkTemplate(eq("book"), eq("400700"), anyString(), eq("ValidateSchemaActionTest"))).thenReturn(true);
        assertThat(validateSchemaAction.performAction(), is(ServiceResult.newOkResult()));
    }

    @Test
    void testSchemaNotFound() throws Exception {
        ValidateSchemaAction validateSchemaAction = new ValidateSchemaAction(state, settings);
        when(state.getOpencatBusiness().checkTemplate(eq("book"), eq("400700"), anyString())).thenReturn(false);
        String message = String.format(state.getMessages().getString("update.schema.not.found"), state.getSchemaName());
        assertThat(validateSchemaAction.performAction(), is(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message)));
    }
}
