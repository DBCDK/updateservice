package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordFactory;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.iscrum.utils.json.Json;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.service.api.ValidateEntry;
import dk.dbc.updateservice.service.api.ValidateWarningOrErrorEnum;
import dk.dbc.updateservice.ws.ValidationError;
import org.junit.Test;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.ResourceBundle;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ValidateRecordActionTest {
    private static final String BOOK_RECORD_RESOURCE = "/dk/dbc/updateservice/actions/book.marc";

    private ResourceBundle messages;

    public ValidateRecordActionTest() {
        this.messages = ResourceBundles.getBundle(this, "actions");
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
        InputStream is = getClass().getResourceAsStream(BOOK_RECORD_RESOURCE);
        MarcRecord record = MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));

        Scripter scripter = mock(Scripter.class);
        Properties settings = new Properties();

        ValidateRecordAction instance = new ValidateRecordAction("bog", record, UpdateStatusEnum.OK);
        instance.setScripter(scripter);
        instance.setSettings(settings);

        when(scripter.callMethod("validateRecord", "bog", Json.encode(record), settings)).thenReturn("[]");

        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));
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
        InputStream is = getClass().getResourceAsStream(BOOK_RECORD_RESOURCE);
        MarcRecord record = MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));

        Scripter scripter = mock(Scripter.class);
        Properties settings = new Properties();

        ValidateRecordAction instance = new ValidateRecordAction("bog", record, UpdateStatusEnum.OK);
        instance.setScripter(scripter);
        instance.setSettings(settings);

        ValidationError warn = ValidationError.newError(ValidateWarningOrErrorEnum.WARNING, "warning");
        List<ValidationError> jsReturnList = Arrays.asList(warn);

        when(scripter.callMethod("validateRecord", "bog", Json.encode(record), settings)).thenReturn(Json.encode(jsReturnList));

        ServiceResult expected = ServiceResult.newOkResult();
        expected.addEntry(warn);
        assertThat(instance.performAction(), equalTo(expected));
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
        InputStream is = getClass().getResourceAsStream(BOOK_RECORD_RESOURCE);
        MarcRecord record = MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));

        Scripter scripter = mock(Scripter.class);
        Properties settings = new Properties();

        ValidateRecordAction instance = new ValidateRecordAction("bog", record, UpdateStatusEnum.OK);
        instance.setScripter(scripter);
        instance.setSettings(settings);

        ValidationError err = ValidationError.newError(ValidateWarningOrErrorEnum.ERROR, "error");
        List<ValidationError> jsReturnList = Arrays.asList(err);

        when(scripter.callMethod("validateRecord", "bog", Json.encode(record), settings)).thenReturn(Json.encode(jsReturnList));

        ServiceResult expected = ServiceResult.newStatusResult(UpdateStatusEnum.VALIDATION_ERROR);
        expected.addEntry(err);
        assertThat(instance.performAction(), equalTo(expected));
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
        InputStream is = getClass().getResourceAsStream(BOOK_RECORD_RESOURCE);
        MarcRecord record = MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));

        Scripter scripter = mock(Scripter.class);
        Properties settings = new Properties();

        ValidateRecordAction instance = new ValidateRecordAction("bog", record, UpdateStatusEnum.OK);
        instance.setScripter(scripter);
        instance.setSettings(settings);

        ScripterException ex = new ScripterException("error");
        when(scripter.callMethod("validateRecord", "bog", Json.encode(record), settings)).thenThrow(ex);

        String message = String.format(messages.getString("internal.validate.record.error"), ex.getMessage());
        ServiceResult expected = ServiceResult.newErrorResult(UpdateStatusEnum.FAILED_VALIDATION_INTERNAL_ERROR, message);
        assertThat(instance.performAction(), equalTo(expected));
    }

    /**
     * Test ValidateRecordAction.performAction() for validation of a record with an
     * invalid datatype returned by the JavaScript environment.
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
    public void testPerformAction_JavaScriptWrongReturnType() throws Exception {
        InputStream is = getClass().getResourceAsStream(BOOK_RECORD_RESOURCE);
        MarcRecord record = MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));

        Scripter scripter = mock(Scripter.class);
        Properties settings = new Properties();

        ValidateRecordAction instance = new ValidateRecordAction("bog", record, UpdateStatusEnum.OK);
        instance.setScripter(scripter);
        instance.setSettings(settings);

        when(scripter.callMethod("validateRecord", "bog", Json.encode(record), settings)).thenReturn(27);

        ServiceResult actual = instance.performAction();
        assertThat(actual.getServiceError(), nullValue());
        assertThat(actual.getStatus(), equalTo(UpdateStatusEnum.FAILED_VALIDATION_INTERNAL_ERROR));
        assertThat(actual.getEntries(), notNullValue());
        assertThat(actual.getEntries().size(), is(1));

        ValidateEntry validateEntry = actual.getEntries().get(0);
        assertThat(validateEntry, notNullValue());
        assertThat(validateEntry.getWarningOrError(), is(ValidateWarningOrErrorEnum.ERROR));
        assertThat(validateEntry.getUrlForDocumentation(), nullValue());
        assertThat(validateEntry.getOrdinalPositionOfField(), nullValue());
        assertThat(validateEntry.getOrdinalPositionOfSubField(), nullValue());

        String message = String.format(messages.getString("internal.validate.record.error"), "");
        assertThat(validateEntry.getMessage(), startsWith(message));
    }
}
