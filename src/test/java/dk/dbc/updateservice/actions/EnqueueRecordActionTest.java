/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.OpenAgencyService;
import dk.dbc.updateservice.ws.JNDIResources;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.Properties;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class EnqueueRecordActionTest {
    private GlobalActionState state;
    private Properties settings;
    OpenAgencyService.LibraryGroup libraryGroup = OpenAgencyService.LibraryGroup.FBS;

    @Before
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
        state.setLibraryGroup(libraryGroup);
        settings = new UpdateTestUtils().getSettings();
    }

    private MarcRecord prepareRecord(String faust, int agencyId) {
        MarcRecord record = new MarcRecord();
        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.addOrReplaceSubfield("001", "a", faust);
        writer.addOrReplaceSubfield("001", "b", Integer.toString(agencyId));

        return record;
    }

    /**
     * Test EnqueueRecordAction.performAction() to enqueue a record in the rawrepo.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with records.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Enqueue a record by its id. Provider id is not initailized.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return status: FAILED_VALIDATION_INTERNAL_ERROR.
     * </dd>
     * </dl>
     */
    @Test
    public void testActionPerform_WithNoProviderId() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        EnqueueRecordAction instance = new EnqueueRecordAction(state, new Properties(), record);
        String message = state.getMessages().getString("provider.id.not.set");
        assertThat(instance.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state)));
        verify(state.getRawRepo(), never()).changedRecord(anyString(), any(RecordId.class));
    }

    /**
     * Test EnqueueRecordAction.performAction() to enqueue a record in the rawrepo.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with records.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Enqueue a record by its id and a valid provider id.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * The rawrepo is called to enqueued the record and all records that
     * link to it.
     * </dd>
     * </dl>
     */
    @Test
    public void testActionPerform_ProviderIdFBS() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        EnqueueRecordAction enqueueRecordAction = new EnqueueRecordAction(state, settings, record);
        assertThat(enqueueRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        ArgumentCaptor<String> argProvider = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RecordId> argId = ArgumentCaptor.forClass(RecordId.class);
        ArgumentCaptor<Integer> priority = ArgumentCaptor.forClass(int.class);

        verify(state.getRawRepo()).changedRecord(argProvider.capture(), argId.capture(), priority.capture());
        assertThat(argProvider.getValue(), equalTo(enqueueRecordAction.settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_FBS)));
        assertThat(argId.getValue(), equalTo(new RecordId(recordId, agencyId)));
        assertThat(priority.getValue(), equalTo(500));
    }

    @Test
    public void testActionPerform_ProviderOverride() throws Exception {
        String faust = "12345678";
        int agencyId = 654321;

        MarcRecord record = prepareRecord(faust, agencyId);

        Properties clonedSettings = (Properties) settings.clone();
        clonedSettings.setProperty(JNDIResources.RAWREPO_PROVIDER_ID_OVERRIDE, "dataio-update-well3.5");
        clonedSettings.setProperty(JNDIResources.RAWREPO_PRIORITY_OVERRIDE, "1000");

        EnqueueRecordAction enqueueRecordAction = new EnqueueRecordAction(state, clonedSettings, record);
        assertThat(enqueueRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        ArgumentCaptor<String> argProvider = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RecordId> argId = ArgumentCaptor.forClass(RecordId.class);
        ArgumentCaptor<Integer> priority = ArgumentCaptor.forClass(int.class);

        verify(state.getRawRepo()).changedRecord(argProvider.capture(), argId.capture(), priority.capture());
        assertThat(argProvider.getValue(), equalTo(enqueueRecordAction.settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_OVERRIDE)));
        assertThat(argId.getValue(), equalTo(new RecordId(faust, agencyId)));
        assertThat(priority.getValue(), equalTo(1000));
    }

    @Test
    public void testActionPerform_ProviderArticle() throws Exception {
        String faust = "12345678";
        int agencyId = 870971;

        MarcRecord record = prepareRecord(faust, agencyId);

        Properties clonedSettings = (Properties) settings.clone();
        clonedSettings.setProperty(JNDIResources.RAWREPO_PROVIDER_ID_OVERRIDE, "dataio-bulk");
        clonedSettings.setProperty(JNDIResources.RAWREPO_PRIORITY_OVERRIDE, "1000");

        EnqueueRecordAction enqueueRecordAction = new EnqueueRecordAction(state, clonedSettings, record);
        assertThat(enqueueRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        ArgumentCaptor<String> argProvider = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RecordId> argId = ArgumentCaptor.forClass(RecordId.class);
        ArgumentCaptor<Integer> priority = ArgumentCaptor.forClass(int.class);

        verify(state.getRawRepo()).changedRecord(argProvider.capture(), argId.capture(), priority.capture());
        assertThat(argProvider.getValue(), equalTo("dataio-bulk"));
        assertThat(argId.getValue(), equalTo(new RecordId(faust, agencyId)));
        assertThat(priority.getValue(), equalTo(1000));
    }

    @Test
    public void testActionPerform_ProviderDBC() throws Exception {
        String faust = "12345678";
        int agencyId = 870970;

        MarcRecord record = prepareRecord(faust, agencyId);

        state.setLibraryGroup(OpenAgencyService.LibraryGroup.DBC);

        Properties newSettings = (Properties) settings.clone();
        newSettings.setProperty(JNDIResources.RAWREPO_PRIORITY_OVERRIDE, "1000");

        EnqueueRecordAction enqueueRecordAction = new EnqueueRecordAction(state, newSettings, record);
        assertThat(enqueueRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        ArgumentCaptor<String> argProvider = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RecordId> argId = ArgumentCaptor.forClass(RecordId.class);
        ArgumentCaptor<Integer> priority = ArgumentCaptor.forClass(int.class);

        verify(state.getRawRepo()).changedRecord(argProvider.capture(), argId.capture(), priority.capture());
        assertThat(argProvider.getValue(), equalTo(enqueueRecordAction.settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_DBC)));
        assertThat(argId.getValue(), equalTo(new RecordId(faust, agencyId)));
        assertThat(priority.getValue(), equalTo(1000));
    }

    @Test
    public void testActionPerform_ProviderPH() throws Exception {
        String faust = "12345678";
        int agencyId = 654321;

        MarcRecord record = prepareRecord(faust, agencyId);

        state.setLibraryGroup(OpenAgencyService.LibraryGroup.PH);

        EnqueueRecordAction enqueueRecordAction = new EnqueueRecordAction(state, settings, record);
        assertThat(enqueueRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        ArgumentCaptor<String> argProvider = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RecordId> argId = ArgumentCaptor.forClass(RecordId.class);
        ArgumentCaptor<Integer> priority = ArgumentCaptor.forClass(int.class);

        verify(state.getRawRepo()).changedRecord(argProvider.capture(), argId.capture(), priority.capture());
        assertThat(argProvider.getValue(), equalTo(enqueueRecordAction.settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_PH)));
        assertThat(argId.getValue(), equalTo(new RecordId(faust, agencyId)));
        assertThat(priority.getValue(), equalTo(500));
    }
}
