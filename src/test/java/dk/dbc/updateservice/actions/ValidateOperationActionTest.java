package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordWriter;
import dk.dbc.updateservice.client.BibliographicRecordFactory;
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
    public void testPerformAction_fbs_noDoubleRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        BibliographicRecord bibliographicRecord = BibliographicRecordFactory.newMarcRecord(record);
        state.getUpdateServiceRequestDTO().setBibliographicRecordDTO(UpdateRequestReader.convertExternalBibliographicRecordToInternalBibliographicRecordDto(bibliographicRecord));
        String schemaName = "book";
        state.getUpdateServiceRequestDTO().setSchemaName(schemaName);

        ValidateOperationAction validateOperationAction = new ValidateOperationAction(state, settings);

        assertThat(validateOperationAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = validateOperationAction.children();
        assertThat(children.size(), is(3));

        ServiceAction child = children.get(0);
        assertTrue(child.getClass() == AuthenticateUserAction.class);
        AuthenticateUserAction authenticateUserAction = (AuthenticateUserAction) child;
        assertThat(authenticateUserAction.state.getAuthenticator(), is(state.getAuthenticator()));
        assertThat(authenticateUserAction.state.getUpdateServiceRequestDTO().getAuthenticationDTO(), is(state.getUpdateServiceRequestDTO().getAuthenticationDTO()));
        assertThat(authenticateUserAction.state.getWsContext(), is(state.getWsContext()));

        child = children.get(1);
        assertTrue(child.getClass() == ValidateSchemaAction.class);
        ValidateSchemaAction validateSchemaAction = (ValidateSchemaAction) child;
        assertThat(validateSchemaAction.state.getUpdateServiceRequestDTO().getSchemaName(), equalTo(schemaName));
        assertThat(validateSchemaAction.state.getScripter(), is(state.getScripter()));
        assertThat(validateSchemaAction.settings, is(settings));

        child = children.get(2);
        assertTrue(child.getClass() == ValidateRecordAction.class);
        ValidateRecordAction validateRecordAction = (ValidateRecordAction) child;
        assertThat(validateRecordAction.state.getUpdateServiceRequestDTO().getSchemaName(), equalTo(validateOperationAction.state.getSchemaName()));
        assertThat(validateRecordAction.state.readRecord(), is(validateOperationAction.state.readRecord()));
        assertThat(validateRecordAction.state.getScripter(), is(state.getScripter()));
        assertThat(validateRecordAction.settings, is(settings));
    }

    @Test
    public void testPerformAction_fbs_DoubleRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");

        BibliographicRecord bibliographicRecord = BibliographicRecordFactory.newMarcRecord(record);
        state.getUpdateServiceRequestDTO().setBibliographicRecordDTO(UpdateRequestReader.convertExternalBibliographicRecordToInternalBibliographicRecordDto(bibliographicRecord));
        String schemaName = "book";
        state.getUpdateServiceRequestDTO().setSchemaName(schemaName);
        UpdateMode updateModeDataFBS = new UpdateMode(UpdateMode.Mode.FBS);
        state.setUpdateMode(updateModeDataFBS);

        ValidateOperationAction validateOperationAction = new ValidateOperationAction(state, settings);

        assertThat(validateOperationAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = validateOperationAction.children();
        assertThat(children.size(), is(3));

        ServiceAction child = children.get(0);
        assertTrue(child.getClass() == AuthenticateUserAction.class);
        AuthenticateUserAction authenticateUserAction = (AuthenticateUserAction) child;
        assertThat(authenticateUserAction.state.getAuthenticator(), is(state.getAuthenticator()));
        assertThat(authenticateUserAction.state.getUpdateServiceRequestDTO().getAuthenticationDTO(), is(state.getUpdateServiceRequestDTO().getAuthenticationDTO()));
        assertThat(authenticateUserAction.state.getWsContext(), is(state.getWsContext()));

        child = children.get(1);
        assertTrue(child.getClass() == ValidateSchemaAction.class);
        ValidateSchemaAction validateSchemaAction = (ValidateSchemaAction) child;
        assertThat(validateSchemaAction.state.getUpdateServiceRequestDTO().getSchemaName(), equalTo(schemaName));
        assertThat(validateSchemaAction.state.getScripter(), is(state.getScripter()));
        assertThat(validateSchemaAction.settings, is(settings));

        child = children.get(2);
        assertTrue(child.getClass() == DoubleRecordFrontendAndValidateAction.class);
        DoubleRecordFrontendAndValidateAction doubleRecordFrontendAndValidateAction = (DoubleRecordFrontendAndValidateAction) child;
        assertThat(doubleRecordFrontendAndValidateAction.state.getUpdateServiceRequestDTO().getSchemaName(), equalTo(validateOperationAction.state.getSchemaName()));
        assertThat(doubleRecordFrontendAndValidateAction.state.readRecord(), is(validateOperationAction.state.readRecord()));
        assertThat(doubleRecordFrontendAndValidateAction.state.getScripter(), is(state.getScripter()));
        assertThat(doubleRecordFrontendAndValidateAction.settings, is(settings));
    }
}
