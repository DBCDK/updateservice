/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.updateservice.update.LibraryGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ValidateOperationActionTest {
    private GlobalActionState state;
    private Properties settings;

    @BeforeEach
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
        settings = new UpdateTestUtils().getSettings();
    }

    @Test
    void testPerformAction_fbs_noDoubleRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        state.getUpdateServiceRequestDTO().setBibliographicRecordDTO(AssertActionsUtil.constructBibliographicRecordDTO(record, null));
        String schemaName = "book";
        state.getUpdateServiceRequestDTO().setSchemaName(schemaName);
        LibraryGroup libraryGroup = LibraryGroup.FBS;
        state.setLibraryGroup(libraryGroup);

        ValidateOperationAction validateOperationAction = new ValidateOperationAction(state, settings);

        assertThat(validateOperationAction.performAction(), is(ServiceResult.newOkResult()));

        List<ServiceAction> children = validateOperationAction.children();
        assertThat(children.size(), is(3));

        ServiceAction child = children.get(0);
        assertThat(child.getClass(), is(AuthenticateUserAction.class));
        AuthenticateUserAction authenticateUserAction = (AuthenticateUserAction) child;
        assertThat(authenticateUserAction.state.getAuthenticator(), is(state.getAuthenticator()));
        assertThat(authenticateUserAction.state.getUpdateServiceRequestDTO().getAuthenticationDTO(), is(state.getUpdateServiceRequestDTO().getAuthenticationDTO()));

        child = children.get(1);
        assertThat(child.getClass(), is(ValidateSchemaAction.class));
        ValidateSchemaAction validateSchemaAction = (ValidateSchemaAction) child;
        assertThat(validateSchemaAction.state.getUpdateServiceRequestDTO().getSchemaName(), is(schemaName));
        assertThat(validateSchemaAction.settings, is(settings));

        child = children.get(2);
        assertThat(child.getClass(), is(ValidateRecordAction.class));
        ValidateRecordAction validateRecordAction = (ValidateRecordAction) child;
        assertThat(validateRecordAction.state.getUpdateServiceRequestDTO().getSchemaName(), is(validateOperationAction.state.getSchemaName()));
        assertThat(validateRecordAction.state.readRecord(), is(validateOperationAction.state.readRecord()));
        assertThat(validateRecordAction.settings, is(settings));
    }

    @Test
    void testPerformAction_fbs_DoubleRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "b", "870970");

        state.getUpdateServiceRequestDTO().setBibliographicRecordDTO(AssertActionsUtil.constructBibliographicRecordDTO(record, null));
        String schemaName = "book";
        state.getUpdateServiceRequestDTO().setSchemaName(schemaName);
        LibraryGroup libraryGroup = LibraryGroup.FBS;
        state.setLibraryGroup(libraryGroup);

        ValidateOperationAction validateOperationAction = new ValidateOperationAction(state, settings);

        assertThat(validateOperationAction.performAction(), is(ServiceResult.newOkResult()));

        List<ServiceAction> children = validateOperationAction.children();
        assertThat(children.size(), is(3));

        ServiceAction child = children.get(0);
        assertThat(child.getClass(), is(AuthenticateUserAction.class));
        AuthenticateUserAction authenticateUserAction = (AuthenticateUserAction) child;
        assertThat(authenticateUserAction.state.getAuthenticator(), is(state.getAuthenticator()));
        assertThat(authenticateUserAction.state.getUpdateServiceRequestDTO().getAuthenticationDTO(), is(state.getUpdateServiceRequestDTO().getAuthenticationDTO()));

        child = children.get(1);
        assertThat(child.getClass(), is(ValidateSchemaAction.class));
        ValidateSchemaAction validateSchemaAction = (ValidateSchemaAction) child;
        assertThat(validateSchemaAction.state.getUpdateServiceRequestDTO().getSchemaName(), is(schemaName));
        assertThat(validateSchemaAction.settings, is(settings));

        child = children.get(2);
        assertThat(child.getClass(), is(DoubleRecordFrontendAndValidateAction.class));
        DoubleRecordFrontendAndValidateAction doubleRecordFrontendAndValidateAction = (DoubleRecordFrontendAndValidateAction) child;
        assertThat(doubleRecordFrontendAndValidateAction.state.getUpdateServiceRequestDTO().getSchemaName(), is(validateOperationAction.state.getSchemaName()));
        assertThat(doubleRecordFrontendAndValidateAction.state.readRecord(), is(validateOperationAction.state.readRecord()));
        assertThat(doubleRecordFrontendAndValidateAction.settings, is(settings));
    }
}
