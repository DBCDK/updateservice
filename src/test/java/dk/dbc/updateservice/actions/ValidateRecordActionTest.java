package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcFieldWriter;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.opencat.connector.OpencatBusinessConnectorException;
import dk.dbc.updateservice.dto.MessageEntryDTO;
import dk.dbc.updateservice.dto.TypeEnumDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.vipcore.libraryrules.VipCoreLibraryRulesConnector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBException;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

class ValidateRecordActionTest {
    private GlobalActionState state;
    private Properties settings;
    private static final String GROUP_ID = "700000";
    private static final String SCHEMA_NAME = "bog";
    private MarcRecord record;

    @BeforeEach
    public void before() throws IOException, JAXBException, SAXException, ParserConfigurationException {
        record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);

        state = new UpdateTestUtils().getGlobalActionStateMockObject();
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId(GROUP_ID);
        state.getUpdateServiceRequestDTO().setBibliographicRecordDTO(AssertActionsUtil.constructBibliographicRecordDTO(record, null));
        state.getUpdateServiceRequestDTO().setSchemaName(SCHEMA_NAME);
        settings = new UpdateTestUtils().getSettings();
        MDC.put("trackingId", "ValidateRecordActionTest");
    }

    @AfterAll
    static void afterAll() {
        MDC.clear();
    }

    /**
     * Test ValidateRecordAction.performAction() for validation a record succesfully
     * without any errors.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A record and a JavaScript environment with settings.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Call JavaScript to validate the record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return ServiceResult with status ok.
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_Ok() throws Exception {
        final ValidateRecordAction validateRecordAction = new ValidateRecordAction(state, settings);
        when(state.getOpencatBusiness().validateRecord(SCHEMA_NAME, record)).thenReturn(new ArrayList<>());
        assertThat(validateRecordAction.performAction(), is(ServiceResult.newOkResult()));
    }

    /**
     * Test ValidateRecordAction.performAction() for validation a record with validation
     * warnings.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A record and a JavaScript environment with settings.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Call JavaScript to validate the record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return ServiceResult with status ok.
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_ValidationWarnings() throws Exception {
        final ValidateRecordAction validateRecordAction = new ValidateRecordAction(state, settings);

        final List<MessageEntryDTO> jsReturnList = UpdateTestUtils.createMessageEntryList(TypeEnumDTO.WARNING, "warning");
        when(state.getOpencatBusiness().validateRecord(SCHEMA_NAME, record, "ValidateRecordActionTest")).thenReturn(jsReturnList);
        when(state.getVipCoreService().hasFeature(GROUP_ID, VipCoreLibraryRulesConnector.Rule.AUTH_ROOT)).thenReturn(false);

        final ServiceResult expected = ServiceResult.newOkResult();
        expected.setEntries(jsReturnList);
        assertThat(validateRecordAction.performAction(), is(expected));
    }

    /**
     * Test ValidateRecordAction.performAction() for validation a record with validation
     * errors.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A record and a JavaScript environment with settings.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Call JavaScript to validate the record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return ServiceResult with status VALIDATION_ERROR.
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_ValidationErrors() throws Exception {
        final ValidateRecordAction validateRecordAction = new ValidateRecordAction(state, settings);

        final List<MessageEntryDTO> jsReturnList = UpdateTestUtils.createMessageEntryList(TypeEnumDTO.ERROR, "error");
        when(state.getOpencatBusiness().validateRecord(SCHEMA_NAME, record, "ValidateRecordActionTest")).thenReturn(jsReturnList);
        when(state.getVipCoreService().hasFeature(GROUP_ID, VipCoreLibraryRulesConnector.Rule.AUTH_ROOT)).thenReturn(false);

        final ServiceResult expected = ServiceResult.newStatusResult(UpdateStatusEnumDTO.FAILED);
        expected.setEntries(jsReturnList);
        assertThat(validateRecordAction.performAction(), is(expected));
    }

    /**
     * Test ValidateRecordAction.performAction() for validation a record with an
     * exception from the JavaScript environment.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A record and a JavaScript environment with settings.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Call JavaScript to validate the record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return ServiceResult with status FAILED_VALIDATION_INTERNAL_ERROR.
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_JavaScriptException() throws Exception {
        final ValidateRecordAction validateRecordAction = new ValidateRecordAction(state, settings);

        final OpencatBusinessConnectorException ex = new OpencatBusinessConnectorException("error");
        when(state.getOpencatBusiness().validateRecord(SCHEMA_NAME, record, "ValidateRecordActionTest")).thenThrow(ex);
        when(state.getVipCoreService().hasFeature(GROUP_ID, VipCoreLibraryRulesConnector.Rule.AUTH_ROOT)).thenReturn(false);

        final String message = String.format(state.getMessages().getString("internal.validate.record.error"), ex.getMessage());
        ServiceResult expected = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
        assertThat(validateRecordAction.performAction(), is(expected));
    }

    @Test
    void testDeleteCommonRecordNotAuthRoot() throws Exception {
        final ValidateRecordAction validateRecordAction = new ValidateRecordAction(state, settings);
        record = AssertActionsUtil.loadRecord(AssertActionsUtil.VOLUME_RECORD_RESOURCE);
        new MarcRecordWriter(record).addOrReplaceSubfield("004", "r", "d");
        state.setMarcRecord(record);

        final String message = state.getMessages().getString("delete.record.common.record.missing.rights");
        ServiceResult expected = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);


        when(state.getVipCoreService().hasFeature(GROUP_ID, VipCoreLibraryRulesConnector.Rule.AUTH_ROOT)).thenReturn(false);
        assertThat(validateRecordAction.performAction(), is(expected));

    }

}
