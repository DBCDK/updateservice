package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.marc.binding.DataField;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.updateservice.client.BibliographicRecordExtraData;
import dk.dbc.updateservice.dto.OptionEnumDTO;
import dk.dbc.updateservice.dto.OptionsDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.JNDIResources;
import dk.dbc.updateservice.update.LibraryGroup;
import dk.dbc.updateservice.update.UpdateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class UpdateRequestActionTest {
    private GlobalActionState state;
    private Properties settings;
    LibraryGroup libraryGroup = LibraryGroup.FBS;

    @BeforeEach
    public void before() throws IOException, UpdateException {
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
    void testEmptyRequest() throws Exception {
        // Technically, the next line is superfluous since the variable is borne as null, but if anyone changed that, it will now be caught
        state.getUpdateServiceRequestDTO().setBibliographicRecordDTO(null);
        UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings);
        String message = state.getMessages().getString("request.record.is.missing");
        assertThat(updateRequestAction.performAction(), is(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message)));
    }

    @Test
    void testNoField001() throws Exception {
        MarcRecord record = new MarcRecord();
        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.addOrReplaceSubField("004", 'r', "n");
        state.getUpdateServiceRequestDTO().setBibliographicRecordDTO(AssertActionsUtil.constructBibliographicRecordDTO(record, null));
        UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings);
        String message = state.getMessages().getString("sanity.check.failed.no.001");
        assertThat(updateRequestAction.sanityCheckRecord(), is(message));
    }

    @Test
    void testEmptyField001() throws Exception {
        MarcRecord record = new MarcRecord();
        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.addOrReplaceSubField("001", 'a', "");
        writer.addOrReplaceSubField("001", 'b', "870970");
        writer.addOrReplaceSubField("004", 'r', "n");
        state.getUpdateServiceRequestDTO().setBibliographicRecordDTO(AssertActionsUtil.constructBibliographicRecordDTO(record, null));
        UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings);
        String message = state.getMessages().getString("sanity.check.failed.empty.001");
        assertThat(updateRequestAction.sanityCheckRecord(), is(message));
    }

    @Test
    void testSpacesInId() throws Exception {
        MarcRecord record = new MarcRecord();
        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.addOrReplaceSubField("001", 'a', "1 234 567 8");
        writer.addOrReplaceSubField("001", 'b', "870970");
        state.getUpdateServiceRequestDTO().setBibliographicRecordDTO(AssertActionsUtil.constructBibliographicRecordDTO(record, null));
        UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings);
        String message = state.getMessages().getString("sanity.check.failed.spaces.001");
        assertThat(updateRequestAction.sanityCheckRecord(), is(message));
    }

    // this test is a bit awkward since the reader.getAgencyAsInt throws an exception
    @Test
    void testBadLibraryNo() throws Exception {
        MarcRecord record = new MarcRecord();
        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.addFieldSubfield("001", 'a', "12345678");
        writer.addOrReplaceSubField("001", 'b', "0");
        state.getUpdateServiceRequestDTO().setBibliographicRecordDTO(AssertActionsUtil.constructBibliographicRecordDTO(record, null));
        UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings);
        String message = state.getMessages().getString("sanity.check.failed.libraryno.001");
        assertThat(updateRequestAction.sanityCheckRecord(), is(message));
    }

    @ParameterizedTest
    @CsvSource({",", " ,", " , ", "jule,mand", "4,2"})
    void test_not_null(String ind1, String ind2) throws Exception {
        settings = new Properties();
        final MarcRecord marcRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        marcRecord.getFields(DataField.class).get(5).setInd1(ind1 != null ? ind1.charAt(0) : null);
        marcRecord.getFields(DataField.class).get(5).setInd2(ind2 != null ? ind2.charAt(0) : null);

        state.getUpdateServiceRequestDTO().setBibliographicRecordDTO(AssertActionsUtil.constructBibliographicRecordDTO(marcRecord, null));
        final UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings);
        assertThat(updateRequestAction.sanityCheckRecord(), is("Indikatoren i felt 032 mangler eller har en ugyldig værdi"));
    }

    @Test
    void testMissingIndicatorsNull() throws Exception {
        settings = new Properties();
        MarcRecord marcRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        marcRecord.getFields(DataField.class).get(5).setInd1(null);
        marcRecord.getFields(DataField.class).get(5).setInd2(null);

        state.getUpdateServiceRequestDTO().setBibliographicRecordDTO(AssertActionsUtil.constructBibliographicRecordDTO(marcRecord, null));
        UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings);
        assertThat(updateRequestAction.sanityCheckRecord(), is("Indikatoren i felt 032 mangler eller har en ugyldig værdi"));
    }

    @Test
    void test13LibraryInProduction() throws Exception {
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId("131010");
        UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings);
        String message = String.format(state.getMessages().getString("agency.is.not.allowed.for.this.instance"), state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId());
        assertThat(updateRequestAction.performAction(), is(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message)));
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
    void testWrongRecordSchema() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        state.getUpdateServiceRequestDTO().setBibliographicRecordDTO(AssertActionsUtil.constructBibliographicRecordDTO(record, null));
        UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings);

        state.getUpdateServiceRequestDTO().getBibliographicRecordDTO().setRecordSchema(null);
        assertThat(updateRequestAction.performAction(), is(ServiceResult.newStatusResult(UpdateStatusEnumDTO.FAILED)));

        state.getUpdateServiceRequestDTO().getBibliographicRecordDTO().setRecordSchema("wrong");
        assertThat(updateRequestAction.performAction(), is(ServiceResult.newStatusResult(UpdateStatusEnumDTO.FAILED)));
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
    void testWrongRecordPacking() throws Exception {
        final MarcRecord marcRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        state.getUpdateServiceRequestDTO().setBibliographicRecordDTO(AssertActionsUtil.constructBibliographicRecordDTO(marcRecord, null));
        UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings);

        state.getUpdateServiceRequestDTO().getBibliographicRecordDTO().setRecordPacking(null);
        assertThat(updateRequestAction.performAction(), is(ServiceResult.newStatusResult(UpdateStatusEnumDTO.FAILED)));

        state.getUpdateServiceRequestDTO().getBibliographicRecordDTO().setRecordPacking("wrong");
        assertThat(updateRequestAction.performAction(), is(ServiceResult.newStatusResult(UpdateStatusEnumDTO.FAILED)));
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
    void testValidRecordForValidate() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        state.getUpdateServiceRequestDTO().setBibliographicRecordDTO(AssertActionsUtil.constructBibliographicRecordDTO(record, null));
        state.getUpdateServiceRequestDTO().setSchemaName("book");
        OptionsDTO optionsDTO = new OptionsDTO();
        optionsDTO.getOption().add(OptionEnumDTO.VALIDATE_ONLY);
        state.getUpdateServiceRequestDTO().setOptionsDTO(optionsDTO);

        UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings);
        assertThat(updateRequestAction.performAction(), is(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateRequestAction.children();
        assertThat(children.size(), is(2));

        ServiceAction child = children.get(1);
        assertThat(child.getClass(), is(ValidateOperationAction.class));

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
    void testValidRecordForUpdate() throws Exception {
        final MarcRecord marcRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        state.getUpdateServiceRequestDTO().setBibliographicRecordDTO(AssertActionsUtil.constructBibliographicRecordDTO(marcRecord, null));
        state.getUpdateServiceRequestDTO().setSchemaName("book");
        state.setLibraryGroup(LibraryGroup.DBC);

        UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings);
        assertThat(updateRequestAction.performAction(), is(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateRequestAction.children();
        assertThat(children.size(), is(4));

        ServiceAction child = children.get(0);
        assertThat(child.getClass(), is(PreProcessingAction.class));

        child = children.get(1);
        assertThat(child.getClass(), is(ValidateOperationAction.class));
        ValidateOperationAction validateOperationAction = (ValidateOperationAction) child;
        testValidateOperationActionOutput(validateOperationAction);

        child = children.get(2);
        assertThat(child.getClass(), is(AuthenticateRecordAction.class));
        child = children.get(3);
        assertThat(child.getClass(), is(UpdateOperationAction.class));
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
    void testValidRecordForUpdate_NoJNDISettings_ExtraRecordData() throws Exception {
        settings.setProperty(JNDIResources.RAWREPO_PROVIDER_ID_FBS, "opencataloging");
        state.setLibraryGroup(LibraryGroup.FBS);
        MarcRecord marcRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        BibliographicRecordExtraData bibliographicRecordExtraData = new BibliographicRecordExtraData();
        bibliographicRecordExtraData.setProviderName("new_provider_name");
        state.getUpdateServiceRequestDTO().setBibliographicRecordDTO(AssertActionsUtil.constructBibliographicRecordDTO(marcRecord, bibliographicRecordExtraData));
        state.getUpdateServiceRequestDTO().setSchemaName("book");

        UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings);
        assertThat(updateRequestAction.performAction(), is(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateRequestAction.children();
        assertThat(children.size(), is(4));

        ServiceAction child = children.get(0);
        assertThat(child.getClass(), is(PreProcessingAction.class));

        child = children.get(1);
        assertThat(child.getClass(), is(ValidateOperationAction.class));
        ValidateOperationAction validateOperationAction = (ValidateOperationAction) child;
        testValidateOperationActionOutput(validateOperationAction);

        child = children.get(2);
        assertThat(child.getClass(), is(AuthenticateRecordAction.class));
        child = children.get(3);
        assertThat(child.getClass(), is(UpdateOperationAction.class));
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
    void testValidRecordForUpdate_JNDISettingsIsFalse_ExtraRecordData() throws Exception {
        settings.setProperty(JNDIResources.RAWREPO_PROVIDER_ID_FBS, "opencataloging");
        MarcRecord marcRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        BibliographicRecordExtraData bibliographicRecordExtraData = new BibliographicRecordExtraData();
        bibliographicRecordExtraData.setProviderName("new_provider_name");
        state.getUpdateServiceRequestDTO().setBibliographicRecordDTO(AssertActionsUtil.constructBibliographicRecordDTO(marcRecord, bibliographicRecordExtraData));
        state.getUpdateServiceRequestDTO().setSchemaName("book");

        UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings);
        assertThat(updateRequestAction.performAction(), is(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateRequestAction.children();
        assertThat(children.size(), is(4));

        ServiceAction child = children.get(0);
        assertThat(child.getClass(), is(PreProcessingAction.class));

        child = children.get(1);
        assertThat(child.getClass(), is(ValidateOperationAction.class));
        ValidateOperationAction validateOperationAction = (ValidateOperationAction) child;
        testValidateOperationActionOutput(validateOperationAction);

        child = children.get(2);
        assertThat(child.getClass(), is(AuthenticateRecordAction.class));
        child = children.get(3);
        assertThat(child.getClass(), is(UpdateOperationAction.class));
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
    void testValidRecordForUpdate_JNDISettingsIsTrue_ExtraRecordData() throws Exception {
        settings.setProperty(JNDIResources.RAWREPO_PROVIDER_ID_DBC, "opencataloging");
        MarcRecord marcRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        BibliographicRecordExtraData bibliographicRecordExtraData = new BibliographicRecordExtraData();
        bibliographicRecordExtraData.setProviderName("new_provider_name");
        state.getUpdateServiceRequestDTO().setBibliographicRecordDTO(AssertActionsUtil.constructBibliographicRecordDTO(marcRecord, bibliographicRecordExtraData));
        state.getUpdateServiceRequestDTO().setSchemaName("book");
        state.setLibraryGroup(LibraryGroup.DBC);
        when(state.getRawRepo().checkProvider("new_provider_name")).thenReturn(true);

        UpdateRequestAction updateRequestAction = new UpdateRequestAction(state, settings);
        assertThat(updateRequestAction.performAction(), is(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateRequestAction.children();
        assertThat(children.size(), is(4));

        ServiceAction child = children.get(0);
        assertThat(child.getClass(), is(PreProcessingAction.class));

        child = children.get(1);
        assertThat(child.getClass(), is(ValidateOperationAction.class));
        ValidateOperationAction validateOperationAction = (ValidateOperationAction) child;
        testValidateOperationActionOutput(validateOperationAction);

        child = children.get(2);
        assertThat(child.getClass(), is(AuthenticateRecordAction.class));
        child = children.get(3);
        assertThat(child.getClass(), is(UpdateOperationAction.class));
        UpdateOperationAction updateOperationAction = (UpdateOperationAction) child;
        testUpdateOperationAction(updateOperationAction, settings);
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
    @Test
    void testValidRecordForUpdate_JNDISettingsIsTrue_ExtraRecordData_ProviderNameIsNull() throws Exception {
        settings = new Properties();
        MarcRecord marcRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        BibliographicRecordExtraData bibliographicRecordExtraData = new BibliographicRecordExtraData();
        bibliographicRecordExtraData.setProviderName("new_provider_name");
        state.getUpdateServiceRequestDTO().setBibliographicRecordDTO(AssertActionsUtil.constructBibliographicRecordDTO(marcRecord, bibliographicRecordExtraData));
        state.getUpdateServiceRequestDTO().setSchemaName("book");

        UpdateRequestAction instance = new UpdateRequestAction(state, settings);
        assertThrows(UpdateException.class, instance::performAction);
    }

    private void testValidateOperationActionOutput(ValidateOperationAction validateOperationAction) {
        assertThat(validateOperationAction.state.getAuthenticator(), is(state.getAuthenticator()));
        assertThat(validateOperationAction.state.getUpdateServiceRequestDTO().getAuthenticationDTO(), is(state.getUpdateServiceRequestDTO().getAuthenticationDTO()));
        assertThat(validateOperationAction.state.getUpdateServiceRequestDTO().getSchemaName(), is(state.getUpdateServiceRequestDTO().getSchemaName()));
        assertThat(validateOperationAction.state.readRecord(), is(state.readRecord()));
        assertThat(validateOperationAction.properties, is(settings));
    }

    private void testUpdateOperationAction(UpdateOperationAction updateOperationAction, Properties properties) {
        assertThat(updateOperationAction.getRawRepo(), is(state.getRawRepo()));
        assertThat(updateOperationAction.state.getAuthenticator(), is(state.getAuthenticator()));
        assertThat(updateOperationAction.state.getUpdateServiceRequestDTO().getAuthenticationDTO(), is(state.getUpdateServiceRequestDTO().getAuthenticationDTO()));
        assertThat(updateOperationAction.state.getHoldingsItems(), is(state.getHoldingsItems()));
        assertThat(updateOperationAction.state.getVipCoreService(), is(state.getVipCoreService()));
        assertThat(updateOperationAction.state.getLibraryRecordsHandler(), is(state.getLibraryRecordsHandler()));
        assertThat(updateOperationAction.marcRecord, is(state.readRecord()));
        assertThat(updateOperationAction.settings, is(properties));
    }
}
