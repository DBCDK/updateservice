package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.updateservice.client.BibliographicRecordFactory;
import dk.dbc.updateservice.dto.UpdateStatusEnumDto;
import dk.dbc.updateservice.service.api.BibliographicRecord;
import dk.dbc.updateservice.ws.UpdateRequestReader;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class ValidateOperationActionTest {
    private GlobalActionState state;
    private Properties settings;

    @Before
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
        settings = new UpdateTestUtils().getSettings();
    }

    @Test
    public void testPerformAction() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        BibliographicRecord bibliographicRecord = BibliographicRecordFactory.newMarcRecord(record);
        state.getUpdateServiceRequestDto().setBibliographicRecordDto(UpdateRequestReader.convertExternalBibliographicRecordToInternalBibliographicRecordDto(bibliographicRecord));
        String schemaName = "book";
        state.getUpdateServiceRequestDto().setSchemaName(schemaName);

        ValidateOperationAction validateOperationAction = new ValidateOperationAction(state, settings);
        validateOperationAction.setOkStatus(UpdateStatusEnumDto.OK);

        assertThat(validateOperationAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = validateOperationAction.children();
        assertThat(children.size(), is(3));

        ServiceAction child = children.get(0);
        assertTrue(child.getClass() == AuthenticateUserAction.class);
        AuthenticateUserAction authenticateUserAction = (AuthenticateUserAction) child;
        assertThat(authenticateUserAction.state.getAuthenticator(), is(state.getAuthenticator()));
        assertThat(authenticateUserAction.state.getUpdateServiceRequestDto().getAuthenticationDto(), is(state.getUpdateServiceRequestDto().getAuthenticationDto()));
        assertThat(authenticateUserAction.state.getWsContext(), is(state.getWsContext()));

        child = children.get(1);
        assertTrue(child.getClass() == ValidateSchemaAction.class);
        ValidateSchemaAction validateSchemaAction = (ValidateSchemaAction) child;
        assertThat(validateSchemaAction.state.getUpdateServiceRequestDto().getSchemaName(), equalTo(schemaName));
        assertThat(validateSchemaAction.state.getScripter(), is(state.getScripter()));
        assertThat(validateSchemaAction.settings, is(settings));

        child = children.get(2);
        assertTrue(child.getClass() == ValidateRecordAction.class);
        ValidateRecordAction validateRecordAction = (ValidateRecordAction) child;
        assertThat(validateRecordAction.state.getUpdateServiceRequestDto().getSchemaName(), equalTo(validateOperationAction.state.getSchemaName()));
        assertThat(validateRecordAction.state.readRecord(), is(validateOperationAction.state.readRecord()));
        assertThat(validateRecordAction.okStatus, is(validateOperationAction.okStatus));
        assertThat(validateRecordAction.state.getScripter(), is(state.getScripter()));
        assertThat(validateRecordAction.settings, is(settings));
    }
}
