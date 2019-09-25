/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.updateservice.client.BibliographicRecordExtraData;
import dk.dbc.updateservice.client.BibliographicRecordFactory;
import dk.dbc.updateservice.dto.OptionEnumDTO;
import dk.dbc.updateservice.dto.OptionsDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.service.api.BibliographicRecord;
import dk.dbc.updateservice.update.OpenAgencyService;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.ws.JNDIResources;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class UpdateRequestActionTest {
    private GlobalActionState state;
    private Properties settings;
    OpenAgencyService.LibraryGroup libraryGroup = OpenAgencyService.LibraryGroup.FBS;

    @Before
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
        settings = new UpdateTestUtils().getSettings();
        state.setMarcRecord(null);
        state.setLibraryGroup(libraryGroup);
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
        state.setMarcRecord(new MarcRecord());
        UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings);
        String message = state.getMessages().getString("request.record.is.missing");
        assertThat(updateRequestAction.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state)));
    }

    @Test
    public void test13LibraryInProduction() throws Exception {
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId("131010");
        UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings);
        String message = String.format(state.getMessages().getString("agency.is.not.allowed.for.this.instance"), state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId());
        assertThat(updateRequestAction.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state)));
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
        BibliographicRecord bibliographicRecord = BibliographicRecordFactory.newMarcRecord(AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE));
        state.getUpdateServiceRequestDTO().setBibliographicRecordDTO(UpdateRequestReader.convertExternalBibliographicRecordToInternalBibliographicRecordDto(bibliographicRecord));
        UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings);

        state.getUpdateServiceRequestDTO().getBibliographicRecordDTO().setRecordSchema(null);
        assertThat(updateRequestAction.performAction(), equalTo(ServiceResult.newStatusResult(UpdateStatusEnumDTO.FAILED)));

        state.getUpdateServiceRequestDTO().getBibliographicRecordDTO().setRecordSchema("wrong");
        assertThat(updateRequestAction.performAction(), equalTo(ServiceResult.newStatusResult(UpdateStatusEnumDTO.FAILED)));
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
        BibliographicRecord bibliographicRecord = BibliographicRecordFactory.newMarcRecord(AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE));
        state.getUpdateServiceRequestDTO().setBibliographicRecordDTO(UpdateRequestReader.convertExternalBibliographicRecordToInternalBibliographicRecordDto(bibliographicRecord));
        UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings);

        state.getUpdateServiceRequestDTO().getBibliographicRecordDTO().setRecordPacking(null);
        assertThat(updateRequestAction.performAction(), equalTo(ServiceResult.newStatusResult(UpdateStatusEnumDTO.FAILED)));

        state.getUpdateServiceRequestDTO().getBibliographicRecordDTO().setRecordPacking("wrong");
        assertThat(updateRequestAction.performAction(), equalTo(ServiceResult.newStatusResult(UpdateStatusEnumDTO.FAILED)));
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
        BibliographicRecord bibliographicRecord = BibliographicRecordFactory.newMarcRecord(AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE));
        state.getUpdateServiceRequestDTO().setBibliographicRecordDTO(UpdateRequestReader.convertExternalBibliographicRecordToInternalBibliographicRecordDto(bibliographicRecord));
        state.getUpdateServiceRequestDTO().setSchemaName("book");
        OptionsDTO optionsDTO = new OptionsDTO();
        optionsDTO.getOption().add(OptionEnumDTO.VALIDATE_ONLY);
        state.getUpdateServiceRequestDTO().setOptionsDTO(optionsDTO);

        UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings);
        assertThat(updateRequestAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateRequestAction.children();
        assertThat(children.size(), is(2));

        ServiceAction child = children.get(1);
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
        BibliographicRecord bibliographicRecord = BibliographicRecordFactory.newMarcRecord(AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE));
        state.getUpdateServiceRequestDTO().setBibliographicRecordDTO(UpdateRequestReader.convertExternalBibliographicRecordToInternalBibliographicRecordDto(bibliographicRecord));
        state.getUpdateServiceRequestDTO().setSchemaName("book");
        state.setLibraryGroup(OpenAgencyService.LibraryGroup.DBC);

        UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings);
        assertThat(updateRequestAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateRequestAction.children();
        assertThat(children.size(), is(3));

        ServiceAction child = children.get(0);
        assertTrue(child.getClass() == PreProcessingAction.class);

        child = children.get(1);
        assertTrue(child.getClass() == ValidateOperationAction.class);
        ValidateOperationAction validateOperationAction = (ValidateOperationAction) child;
        testValidateOperationActionOutput(validateOperationAction);

        child = children.get(2);
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
        settings.setProperty(JNDIResources.RAWREPO_PROVIDER_ID_FBS, "opencataloging");
        state.setLibraryGroup(OpenAgencyService.LibraryGroup.FBS);
        MarcRecord marcRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        BibliographicRecordExtraData bibliographicRecordExtraData = new BibliographicRecordExtraData();
        bibliographicRecordExtraData.setProviderName("new_provider_name");
        BibliographicRecord bibliographicRecord = BibliographicRecordFactory.newMarcRecord(marcRecord, bibliographicRecordExtraData);
        state.getUpdateServiceRequestDTO().setBibliographicRecordDTO(UpdateRequestReader.convertExternalBibliographicRecordToInternalBibliographicRecordDto(bibliographicRecord));
        state.getUpdateServiceRequestDTO().setSchemaName("book");

        UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings);
        assertThat(updateRequestAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateRequestAction.children();
        assertThat(children.size(), is(3));

        ServiceAction child = children.get(0);
        assertTrue(child.getClass() == PreProcessingAction.class);

        child = children.get(1);
        assertTrue(child.getClass() == ValidateOperationAction.class);
        ValidateOperationAction validateOperationAction = (ValidateOperationAction) child;
        testValidateOperationActionOutput(validateOperationAction);

        child = children.get(2);
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
        settings.setProperty(JNDIResources.RAWREPO_PROVIDER_ID_FBS, "opencataloging");
        MarcRecord marcRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        BibliographicRecordExtraData bibliographicRecordExtraData = new BibliographicRecordExtraData();
        bibliographicRecordExtraData.setProviderName("new_provider_name");
        BibliographicRecord bibliographicRecord = BibliographicRecordFactory.newMarcRecord(marcRecord, bibliographicRecordExtraData);
        state.getUpdateServiceRequestDTO().setBibliographicRecordDTO(UpdateRequestReader.convertExternalBibliographicRecordToInternalBibliographicRecordDto(bibliographicRecord));
        state.getUpdateServiceRequestDTO().setSchemaName("book");

        UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings);
        assertThat(updateRequestAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateRequestAction.children();
        assertThat(children.size(), is(3));

        ServiceAction child = children.get(0);
        assertTrue(child.getClass() == PreProcessingAction.class);

        child = children.get(1);
        assertTrue(child.getClass() == ValidateOperationAction.class);
        ValidateOperationAction validateOperationAction = (ValidateOperationAction) child;
        testValidateOperationActionOutput(validateOperationAction);

        child = children.get(2);
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
        settings.setProperty(JNDIResources.RAWREPO_PROVIDER_ID_DBC, "opencataloging");
        MarcRecord marcRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        BibliographicRecordExtraData bibliographicRecordExtraData = new BibliographicRecordExtraData();
        bibliographicRecordExtraData.setProviderName("new_provider_name");
        BibliographicRecord bibliographicRecord = BibliographicRecordFactory.newMarcRecord(marcRecord, bibliographicRecordExtraData);
        state.getUpdateServiceRequestDTO().setBibliographicRecordDTO(UpdateRequestReader.convertExternalBibliographicRecordToInternalBibliographicRecordDto(bibliographicRecord));
        state.getUpdateServiceRequestDTO().setSchemaName("book");
        state.setLibraryGroup(OpenAgencyService.LibraryGroup.DBC);
        when(state.getRawRepo().checkProvider(eq("new_provider_name"))).thenReturn(true);

        UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings);
        assertThat(updateRequestAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateRequestAction.children();
        assertThat(children.size(), is(3));

        ServiceAction child = children.get(0);
        assertTrue(child.getClass() == PreProcessingAction.class);

        child = children.get(1);
        assertTrue(child.getClass() == ValidateOperationAction.class);
        ValidateOperationAction validateOperationAction = (ValidateOperationAction) child;
        testValidateOperationActionOutput(validateOperationAction);

        Properties expectedSettings = (Properties) settings.clone();
        expectedSettings.setProperty(JNDIResources.RAWREPO_PROVIDER_ID_OVERRIDE, bibliographicRecordExtraData.getProviderName());

        child = children.get(2);
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
        BibliographicRecord bibliographicRecord = BibliographicRecordFactory.newMarcRecord(marcRecord, bibliographicRecordExtraData);
        state.getUpdateServiceRequestDTO().setBibliographicRecordDTO(UpdateRequestReader.convertExternalBibliographicRecordToInternalBibliographicRecordDto(bibliographicRecord));
        state.getUpdateServiceRequestDTO().setSchemaName("book");

        UpdateRequestAction instance = new UpdateRequestAction(state, settings);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));
    }

    private void testValidateOperationActionOutput(ValidateOperationAction validateOperationAction) {
        assertThat(validateOperationAction.state.getAuthenticator(), is(state.getAuthenticator()));
        assertThat(validateOperationAction.state.getUpdateServiceRequestDTO().getAuthenticationDTO(), is(state.getUpdateServiceRequestDTO().getAuthenticationDTO()));
        assertThat(validateOperationAction.state.getWsContext(), is(state.getWsContext()));
        assertThat(validateOperationAction.state.getUpdateServiceRequestDTO().getSchemaName(), equalTo(state.getUpdateServiceRequestDTO().getSchemaName()));
        assertThat(validateOperationAction.state.readRecord(), equalTo(state.readRecord()));
        assertThat(validateOperationAction.state.getScripter(), is(state.getScripter()));
        assertThat(validateOperationAction.settings, equalTo(settings));
    }

    private void testUpdateOperationAction(UpdateOperationAction updateOperationAction, Properties properties) {
        assertThat(updateOperationAction.getRawRepo(), is(state.getRawRepo()));
        assertThat(updateOperationAction.state.getAuthenticator(), is(state.getAuthenticator()));
        assertThat(updateOperationAction.state.getUpdateServiceRequestDTO().getAuthenticationDTO(), is(state.getUpdateServiceRequestDTO().getAuthenticationDTO()));
        assertThat(updateOperationAction.state.getHoldingsItems(), is(state.getHoldingsItems()));
        assertThat(updateOperationAction.state.getOpenAgencyService(), is(state.getOpenAgencyService()));
        assertThat(updateOperationAction.state.getLibraryRecordsHandler(), is(state.getLibraryRecordsHandler()));
        assertThat(updateOperationAction.state.getScripter(), is(state.getScripter()));
        assertThat(updateOperationAction.record, equalTo(state.readRecord()));
        assertThat(updateOperationAction.settings, equalTo(properties));
    }
}
