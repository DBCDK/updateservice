/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.opencat.connector.OpencatBusinessConnectorException;
import dk.dbc.updateservice.client.BibliographicRecordFactory;
import dk.dbc.updateservice.dto.MessageEntryDTO;
import dk.dbc.updateservice.dto.TypeEnumDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.service.api.BibliographicRecord;
import dk.dbc.updateservice.ws.UpdateRequestReader;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBException;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

public class ValidateRecordActionTest {
    private GlobalActionState state;
    private Properties settings;
    private static final String GROUP_ID = "700000";
    private static final String SCHEMA_NAME = "bog";
    private MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);

    public ValidateRecordActionTest() throws IOException {
    }

    @Before
    public void before() throws IOException, JAXBException, SAXException, ParserConfigurationException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId(GROUP_ID);
        final BibliographicRecord bibliographicRecord = BibliographicRecordFactory.newMarcRecord(record);
        state.getUpdateServiceRequestDTO().setBibliographicRecordDTO(UpdateRequestReader.convertExternalBibliographicRecordToInternalBibliographicRecordDto(bibliographicRecord));
        state.getUpdateServiceRequestDTO().setSchemaName(SCHEMA_NAME);
        settings = new UpdateTestUtils().getSettings();
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
    public void testPerformAction_Ok() throws Exception {
        final ValidateRecordAction validateRecordAction = new ValidateRecordAction(state, settings);

        when(state.getOpencatBusiness().validateRecord(SCHEMA_NAME, record)).thenReturn(new ArrayList<>());

        assertThat(validateRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));
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
    public void testPerformAction_ValidationWarnings() throws Exception {
        final ValidateRecordAction validateRecordAction = new ValidateRecordAction(state, settings);
        final List<MessageEntryDTO> jsReturnList = UpdateTestUtils.createMessageEntryList(TypeEnumDTO.WARNING, "warning");

        when(state.getOpencatBusiness().validateRecord(SCHEMA_NAME, record)).thenReturn(jsReturnList);

        final ServiceResult expected = ServiceResult.newOkResult();
        expected.setEntries(jsReturnList);
        assertThat(validateRecordAction.performAction(), equalTo(expected));
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
    public void testPerformAction_ValidationErrors() throws Exception {
        final ValidateRecordAction validateRecordAction = new ValidateRecordAction(state, settings);
        final List<MessageEntryDTO> jsReturnList = UpdateTestUtils.createMessageEntryList(TypeEnumDTO.ERROR, "error");

        when(state.getOpencatBusiness().validateRecord(SCHEMA_NAME, record)).thenReturn(jsReturnList);

        final ServiceResult expected = ServiceResult.newStatusResult(UpdateStatusEnumDTO.FAILED);
        expected.setEntries(jsReturnList);
        assertThat(validateRecordAction.performAction(), equalTo(expected));
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
    public void testPerformAction_JavaScriptException() throws Exception {
        final ValidateRecordAction validateRecordAction = new ValidateRecordAction(state, settings);
        final OpencatBusinessConnectorException ex = new OpencatBusinessConnectorException("error");

        when(state.getOpencatBusiness().validateRecord(SCHEMA_NAME, record)).thenThrow(ex);

        final String message = String.format(state.getMessages().getString("internal.validate.record.error"), ex.getMessage());
        final ServiceResult expected = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
        assertThat(validateRecordAction.performAction(), equalTo(expected));
    }

}
