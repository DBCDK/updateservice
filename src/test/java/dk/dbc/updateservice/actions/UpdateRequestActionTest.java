package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.updateservice.client.BibliographicRecordExtraData;
import dk.dbc.updateservice.client.BibliographicRecordFactory;
import dk.dbc.updateservice.service.api.Options;
import dk.dbc.updateservice.service.api.UpdateOptionEnum;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.ws.JNDIResources;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class UpdateRequestActionTest {
    private GlobalActionState state;
    private Properties settings;

    @Before
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
        settings = new UpdateTestUtils().getSettings();
    }

    /**
     * Test UpdateRequestAction.performAction() with an empty request and an empty
     * WebServiceContext.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * An empty request and WebServiceContext.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Perform the request.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return ServiceResult with an error.
     * </dd>
     * </dl>
     */
    @Test
    public void testEmptyRequest() throws Exception {
        MarcRecord record = new MarcRecord();
        UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings, record);
        String message = state.getMessages().getString("request.record.is.missing");
        assertThat(updateRequestAction.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnum.FAILED, message, state)));
    }

    @Test
    public void test13LibraryInProduction() throws Exception {
        state.getUpdateRecordRequest().getAuthentication().setGroupIdAut("131010");
        UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings, null);
        String message = String.format(state.getMessages().getString("agency.is.not.allowed.for.this.instance"), state.getUpdateRecordRequest().getAuthentication().getGroupIdAut());
        assertThat(updateRequestAction.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnum.FAILED, message, state)));
    }

    /**
     * Test UpdateRequestAction.performAction() with a request containing a valid record
     * but with wrong record schema.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A request with wrong record schema.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Perform the request.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return ServiceResult with an error.
     * </dd>
     * </dl>
     */
    @Test
    public void testWrongRecordSchema() throws Exception {
        state.getUpdateRecordRequest().setBibliographicRecord(BibliographicRecordFactory.newMarcRecord(AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE)));
        UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings, state.readRecord());

        state.getUpdateRecordRequest().getBibliographicRecord().setRecordSchema(null);
        assertThat(updateRequestAction.performAction(), equalTo(ServiceResult.newStatusResult(UpdateStatusEnum.FAILED)));

        state.getUpdateRecordRequest().getBibliographicRecord().setRecordSchema("wrong");
        assertThat(updateRequestAction.performAction(), equalTo(ServiceResult.newStatusResult(UpdateStatusEnum.FAILED)));
    }

    /**
     * Test UpdateRequestAction.performAction() with a request containing a valid record
     * but with wrong record schema.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A request with wrong record schema.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Perform the request.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return ServiceResult with an error.
     * </dd>
     * </dl>
     */
    @Test
    public void testWrongRecordPacking() throws Exception {
        state.getUpdateRecordRequest().setBibliographicRecord(BibliographicRecordFactory.newMarcRecord(AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE)));
        UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings, state.readRecord());

        state.getUpdateRecordRequest().getBibliographicRecord().setRecordPacking(null);
        assertThat(updateRequestAction.performAction(), equalTo(ServiceResult.newStatusResult(UpdateStatusEnum.FAILED)));

        state.getUpdateRecordRequest().getBibliographicRecord().setRecordPacking("wrong");
        assertThat(updateRequestAction.performAction(), equalTo(ServiceResult.newStatusResult(UpdateStatusEnum.FAILED)));
    }

    /**
     * Test UpdateRequestAction.performAction() with a valid request for validating a record.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A complete valid request with for validating a record.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Perform the request.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return a ok service result.
     * </dd>
     * </dl>
     */
    @Test
    public void testValidRecordForValidate() throws Exception {
        state.getUpdateRecordRequest().setBibliographicRecord(BibliographicRecordFactory.newMarcRecord(AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE)));
        state.getUpdateRecordRequest().setSchemaName("book");
        Options options = new Options();
        options.getOption().add(UpdateOptionEnum.VALIDATE_ONLY);
        state.getUpdateRecordRequest().setOptions(options);

        UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings, state.readRecord());
        assertThat(updateRequestAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateRequestAction.children();
        assertThat(children.size(), is(1));

        ServiceAction child = children.get(0);
        assertTrue(child.getClass() == ValidateOperationAction.class);

        ValidateOperationAction validateOperationAction = (ValidateOperationAction) child;
        testValidateOperationActionOutput(validateOperationAction);
    }

    /**
     * Test UpdateRequestAction.performAction() with a valid request for updating a record.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A complete valid request with for validating a record.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Perform the request.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return a ok service result.
     * </dd>
     * </dl>
     */
    @Test
    public void testValidRecordForUpdate() throws Exception {
        state.getUpdateRecordRequest().setBibliographicRecord(BibliographicRecordFactory.newMarcRecord(AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE)));
        state.getUpdateRecordRequest().setSchemaName("book");

        UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings, state.readRecord());
        assertThat(updateRequestAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateRequestAction.children();
        assertThat(children.size(), is(2));

        ServiceAction child = children.get(0);
        assertTrue(child.getClass() == ValidateOperationAction.class);
        ValidateOperationAction validateOperationAction = (ValidateOperationAction) child;
        testValidateOperationActionOutput(validateOperationAction);

        child = children.get(1);
        assertTrue(child.getClass() == UpdateOperationAction.class);
        UpdateOperationAction updateOperationAction = (UpdateOperationAction) child;
        testUpdateOperationAction(updateOperationAction, settings);
    }

    /**
     * Test UpdateRequestAction.performAction() with a valid request for updating a record, but with extra record data
     * with provider name.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A complete valid request with for validating a record.
     * <p>
     * No extra record settings in JNDI.
     * </p>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Perform the request.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Throw exception.
     * </dd>
     * </dl>
     */
    @Test
    public void testValidRecordForUpdate_NoJNDISettings_ExtraRecordData() throws Exception {
        settings.setProperty(JNDIResources.RAWREPO_PROVIDER_ID, "opencataloging");
        MarcRecord marcRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        BibliographicRecordExtraData bibliographicRecordExtraData = new BibliographicRecordExtraData();
        bibliographicRecordExtraData.setProviderName("new_provider_name");
        state.getUpdateRecordRequest().setBibliographicRecord(BibliographicRecordFactory.newMarcRecord(marcRecord, bibliographicRecordExtraData));
        state.getUpdateRecordRequest().setSchemaName("book");

        UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings, state.readRecord());
        assertThat(updateRequestAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateRequestAction.children();
        assertThat(children.size(), is(2));

        ServiceAction child = children.get(0);
        assertTrue(child.getClass() == ValidateOperationAction.class);
        ValidateOperationAction validateOperationAction = (ValidateOperationAction) child;
        testValidateOperationActionOutput(validateOperationAction);

        child = children.get(1);
        assertTrue(child.getClass() == UpdateOperationAction.class);
        UpdateOperationAction updateOperationAction = (UpdateOperationAction) child;
        testUpdateOperationAction(updateOperationAction, settings);
    }

    /**
     * Test UpdateRequestAction.performAction() with a valid request for updating a record, but with extra record data
     * with provider name.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A complete valid request with for validating a record.
     * <p>
     * JNDI settings with "false" in allow extra record data.
     * </p>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Perform the request.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Throw exception.
     * </dd>
     * </dl>
     */
    @Test
    public void testValidRecordForUpdate_JNDISettingsIsFalse_ExtraRecordData() throws Exception {
        settings.setProperty(JNDIResources.RAWREPO_PROVIDER_ID, "opencataloging");
        settings.setProperty(JNDIResources.ALLOW_EXTRA_RECORD_DATA_KEY, "False");
        MarcRecord marcRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        BibliographicRecordExtraData bibliographicRecordExtraData = new BibliographicRecordExtraData();
        bibliographicRecordExtraData.setProviderName("new_provider_name");
        state.getUpdateRecordRequest().setBibliographicRecord(BibliographicRecordFactory.newMarcRecord(marcRecord, bibliographicRecordExtraData));
        state.getUpdateRecordRequest().setSchemaName("book");

        UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings, state.readRecord());
        assertThat(updateRequestAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateRequestAction.children();
        assertThat(children.size(), is(2));

        ServiceAction child = children.get(0);
        assertTrue(child.getClass() == ValidateOperationAction.class);
        ValidateOperationAction validateOperationAction = (ValidateOperationAction) child;
        testValidateOperationActionOutput(validateOperationAction);

        child = children.get(1);
        assertTrue(child.getClass() == UpdateOperationAction.class);
        UpdateOperationAction updateOperationAction = (UpdateOperationAction) child;
        testUpdateOperationAction(updateOperationAction, settings);
    }

    /**
     * Test UpdateRequestAction.performAction() with a valid request for updating a record, but with extra record data
     * with provider name.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A complete valid request with for validating a record.
     * <p>
     * JNDI settings with "true" in allow extra record data.
     * </p>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Perform the request.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Throw exception.
     * </dd>
     * </dl>
     */
    @Test
    public void testValidRecordForUpdate_JNDISettingsIsTrue_ExtraRecordData() throws Exception {
        settings.setProperty(JNDIResources.RAWREPO_PROVIDER_ID, "opencataloging");
        settings.setProperty(JNDIResources.ALLOW_EXTRA_RECORD_DATA_KEY, "True");
        MarcRecord marcRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        BibliographicRecordExtraData bibliographicRecordExtraData = new BibliographicRecordExtraData();
        bibliographicRecordExtraData.setProviderName("new_provider_name");
        state.getUpdateRecordRequest().setBibliographicRecord(BibliographicRecordFactory.newMarcRecord(marcRecord, bibliographicRecordExtraData));
        state.getUpdateRecordRequest().setSchemaName("book");

        UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings, state.readRecord());
        assertThat(updateRequestAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateRequestAction.children();
        assertThat(children.size(), is(2));

        ServiceAction child = children.get(0);
        assertTrue(child.getClass() == ValidateOperationAction.class);
        ValidateOperationAction validateOperationAction = (ValidateOperationAction) child;
        testValidateOperationActionOutput(validateOperationAction);

        Properties expectedSettings = (Properties) settings.clone();
        expectedSettings.setProperty(JNDIResources.RAWREPO_PROVIDER_ID, bibliographicRecordExtraData.getProviderName());

        child = children.get(1);
        assertTrue(child.getClass() == UpdateOperationAction.class);
        UpdateOperationAction updateOperationAction = (UpdateOperationAction) child;
        testUpdateOperationAction(updateOperationAction, expectedSettings);
    }

    /**
     * Test UpdateRequestAction.performAction() with a valid request for updating a record, but with extra record data
     * with provider name with null value.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A complete valid request with for validating a record.
     * <p>
     * JNDI settings with "true" in allow extra record data.
     * </p>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Perform the request.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Throw exception.
     * </dd>
     * </dl>
     */
    @Test(expected = UpdateException.class)
    public void testValidRecordForUpdate_JNDISettingsIsTrue_ExtraRecordData_ProviderNameIsNull() throws Exception {
        settings = new Properties();
        MarcRecord marcRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        BibliographicRecordExtraData bibliographicRecordExtraData = new BibliographicRecordExtraData();
        bibliographicRecordExtraData.setProviderName("new_provider_name");
        state.getUpdateRecordRequest().setBibliographicRecord(BibliographicRecordFactory.newMarcRecord(marcRecord, bibliographicRecordExtraData));
        state.getUpdateRecordRequest().setSchemaName("book");

        UpdateRequestAction instance = new UpdateRequestAction(state, settings, state.readRecord());
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));
    }

    private void testValidateOperationActionOutput(ValidateOperationAction validateOperationAction) {
        assertThat(validateOperationAction.state.getAuthenticator(), is(state.getAuthenticator()));
        assertThat(validateOperationAction.state.getUpdateRecordRequest().getAuthentication(), is(state.getUpdateRecordRequest().getAuthentication()));
        assertThat(validateOperationAction.state.getWsContext(), is(state.getWsContext()));
        assertThat(validateOperationAction.state.getUpdateRecordRequest().getSchemaName(), equalTo(state.getUpdateRecordRequest().getSchemaName()));
        assertThat(validateOperationAction.okStatus, is(UpdateStatusEnum.OK));
        assertThat(validateOperationAction.state.readRecord(), equalTo(state.readRecord()));
        assertThat(validateOperationAction.state.getScripter(), is(state.getScripter()));
        assertThat(validateOperationAction.settings, equalTo(settings));
    }

    private void testUpdateOperationAction(UpdateOperationAction updateOperationAction, Properties properties) {
        assertThat(updateOperationAction.getRawRepo(), is(state.getRawRepo()));
        assertThat(updateOperationAction.state.getAuthenticator(), is(state.getAuthenticator()));
        assertThat(updateOperationAction.state.getUpdateRecordRequest().getAuthentication(), is(state.getUpdateRecordRequest().getAuthentication()));
        assertThat(updateOperationAction.state.getHoldingsItems(), is(state.getHoldingsItems()));
        assertThat(updateOperationAction.state.getOpenAgencyService(), is(state.getOpenAgencyService()));
        assertThat(updateOperationAction.state.getLibraryRecordsHandler(), is(state.getLibraryRecordsHandler()));
        assertThat(updateOperationAction.state.getScripter(), is(state.getScripter()));
        assertThat(updateOperationAction.record, equalTo(state.readRecord()));
        assertThat(updateOperationAction.settings, equalTo(properties));
    }
}
