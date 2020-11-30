/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.JNDIResources;
import dk.dbc.updateservice.update.LibraryGroup;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EnqueueRecordActionTest {
    private GlobalActionState state;
    private Properties settings;
    LibraryGroup libraryGroup = LibraryGroup.FBS;

    @Before
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
        state.setLibraryGroup(libraryGroup);
        settings = new UpdateTestUtils().getSettings();
    }

    private MarcRecord prepareRecord(String bibliographicRecordId, int agencyId) {
        final MarcRecord record = new MarcRecord();
        final MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.addOrReplaceSubfield("001", "a", bibliographicRecordId);
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
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        final EnqueueRecordAction instance = new EnqueueRecordAction(state, new Properties(), record);
        final String message = state.getMessages().getString("provider.id.not.set");
        assertThat(instance.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message)));
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
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        final String bibliographicRecordId = AssertActionsUtil.getRecordId(record);
        final int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        final EnqueueRecordAction enqueueRecordAction = new EnqueueRecordAction(state, settings, record);
        assertThat(enqueueRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        final ArgumentCaptor<String> argProvider = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<RecordId> argId = ArgumentCaptor.forClass(RecordId.class);
        final ArgumentCaptor<Integer> priority = ArgumentCaptor.forClass(int.class);

        verify(state.getRawRepo()).changedRecord(argProvider.capture(), argId.capture(), priority.capture());
        assertThat(argProvider.getValue(), equalTo(enqueueRecordAction.settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_FBS)));
        assertThat(argId.getValue(), equalTo(new RecordId(bibliographicRecordId, agencyId)));
        assertThat(priority.getValue(), equalTo(500));
    }

    @Test
    public void testActionPerform_ProviderOverride() throws Exception {
        final String bibliographicRecordId = "12345678";
        final int agencyId = 654321;

        final MarcRecord record = prepareRecord(bibliographicRecordId, agencyId);

        final Properties clonedSettings = (Properties) settings.clone();
        clonedSettings.setProperty(JNDIResources.RAWREPO_PROVIDER_ID_OVERRIDE, "dataio-update-well3.5");
        clonedSettings.setProperty(JNDIResources.RAWREPO_PRIORITY_OVERRIDE, "1000");

        final EnqueueRecordAction enqueueRecordAction = new EnqueueRecordAction(state, clonedSettings, record);
        assertThat(enqueueRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        final ArgumentCaptor<String> argProvider = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<RecordId> argId = ArgumentCaptor.forClass(RecordId.class);
        final ArgumentCaptor<Integer> priority = ArgumentCaptor.forClass(int.class);

        verify(state.getRawRepo()).changedRecord(argProvider.capture(), argId.capture(), priority.capture());
        assertThat(argProvider.getValue(), equalTo(enqueueRecordAction.settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_OVERRIDE)));
        assertThat(argId.getValue(), equalTo(new RecordId(bibliographicRecordId, agencyId)));
        assertThat(priority.getValue(), equalTo(1000));
    }

    @Test
    public void testActionPerform_ProviderArticle() throws Exception {
        final String bibliographicRecordId = "12345678";
        final int agencyId = 870971;

        final MarcRecord record = prepareRecord(bibliographicRecordId, agencyId);

        final Properties clonedSettings = (Properties) settings.clone();
        clonedSettings.setProperty(JNDIResources.RAWREPO_PROVIDER_ID_OVERRIDE, "dataio-bulk");
        clonedSettings.setProperty(JNDIResources.RAWREPO_PRIORITY_OVERRIDE, "1000");

        final EnqueueRecordAction enqueueRecordAction = new EnqueueRecordAction(state, clonedSettings, record);
        assertThat(enqueueRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        final ArgumentCaptor<String> argProvider = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<RecordId> argId = ArgumentCaptor.forClass(RecordId.class);
        final ArgumentCaptor<Integer> priority = ArgumentCaptor.forClass(int.class);

        verify(state.getRawRepo()).changedRecord(argProvider.capture(), argId.capture(), priority.capture());
        assertThat(argProvider.getValue(), equalTo("dataio-bulk"));
        assertThat(argId.getValue(), equalTo(new RecordId(bibliographicRecordId, agencyId)));
        assertThat(priority.getValue(), equalTo(1000));

        final ArgumentCaptor<RecordId> enqueueRecordIdCaptor = ArgumentCaptor.forClass(RecordId.class);
        final ArgumentCaptor<String> enqueueProviderIdCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<Boolean> enqueueChangedCaptor = ArgumentCaptor.forClass(boolean.class);
        final ArgumentCaptor<Boolean> enqueueLeafCaptor = ArgumentCaptor.forClass(boolean.class);
        final ArgumentCaptor<Integer> enqueuePriorityCaptor = ArgumentCaptor.forClass(int.class);

        verify(state.getRawRepo(), never()).enqueue(
                enqueueRecordIdCaptor.capture(),
                enqueueProviderIdCaptor.capture(),
                enqueueChangedCaptor.capture(),
                enqueueLeafCaptor.capture(),
                enqueuePriorityCaptor.capture());
    }

    @Test
    public void testActionPerform_ProviderArticle_WithChildren() throws Exception {
        final String bibliographicRecordId = "12345678";
        final int agencyId = 870971;

        final MarcRecord record = prepareRecord(bibliographicRecordId, agencyId);
        final RecordId recordId = new RecordId(bibliographicRecordId, agencyId);

        final Properties clonedSettings = (Properties) settings.clone();
        clonedSettings.setProperty(JNDIResources.RAWREPO_PROVIDER_ID_OVERRIDE, "dataio-bulk");
        clonedSettings.setProperty(JNDIResources.RAWREPO_PRIORITY_OVERRIDE, "1000");

        when(state.getRawRepo().children(record)).thenReturn(
                new HashSet<>(Collections.singletonList(new RecordId("child", 870971))));

        final EnqueueRecordAction enqueueRecordAction = new EnqueueRecordAction(state, clonedSettings, record);
        assertThat(enqueueRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        final ArgumentCaptor<String> argProvider = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<RecordId> argId = ArgumentCaptor.forClass(RecordId.class);
        final ArgumentCaptor<Integer> priority = ArgumentCaptor.forClass(int.class);

        verify(state.getRawRepo()).changedRecord(argProvider.capture(), argId.capture(), priority.capture());
        assertThat(argProvider.getValue(), equalTo("dataio-bulk"));
        assertThat(argId.getValue(), equalTo(recordId));
        assertThat(priority.getValue(), equalTo(1000));

        final ArgumentCaptor<RecordId> enqueueRecordIdCaptor = ArgumentCaptor.forClass(RecordId.class);
        final ArgumentCaptor<String> enqueueProviderIdCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<Boolean> enqueueChangedCaptor = ArgumentCaptor.forClass(boolean.class);
        final ArgumentCaptor<Boolean> enqueueLeafCaptor = ArgumentCaptor.forClass(boolean.class);
        final ArgumentCaptor<Integer> enqueuePriorityCaptor = ArgumentCaptor.forClass(int.class);

        verify(state.getRawRepo()).enqueue(
                enqueueRecordIdCaptor.capture(),
                enqueueProviderIdCaptor.capture(),
                enqueueChangedCaptor.capture(),
                enqueueLeafCaptor.capture(),
                enqueuePriorityCaptor.capture());

        assertThat(enqueueRecordIdCaptor.getValue(), equalTo(new RecordId(bibliographicRecordId, 191919)));
        assertThat(enqueueProviderIdCaptor.getValue(), equalTo("dataio-bulk"));
        assertThat(enqueueChangedCaptor.getValue(), equalTo(true));
        assertThat(enqueueLeafCaptor.getValue(), equalTo(true));
        assertThat(enqueuePriorityCaptor.getValue(), equalTo(1000));
    }

    @Test
    public void testActionPerform_ProviderDBC() throws Exception {
        final String bibliographicRecordId = "12345678";
        final int agencyId = 870970;

        final MarcRecord record = prepareRecord(bibliographicRecordId, agencyId);

        state.setLibraryGroup(LibraryGroup.DBC);

        final Properties newSettings = (Properties) settings.clone();
        newSettings.setProperty(JNDIResources.RAWREPO_PRIORITY_OVERRIDE, "1000");

        final EnqueueRecordAction enqueueRecordAction = new EnqueueRecordAction(state, newSettings, record);
        assertThat(enqueueRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        final ArgumentCaptor<String> argProvider = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<RecordId> argId = ArgumentCaptor.forClass(RecordId.class);
        final ArgumentCaptor<Integer> priority = ArgumentCaptor.forClass(int.class);

        verify(state.getRawRepo()).changedRecord(argProvider.capture(), argId.capture(), priority.capture());
        assertThat(argProvider.getValue(), equalTo(enqueueRecordAction.settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_DBC)));
        assertThat(argId.getValue(), equalTo(new RecordId(bibliographicRecordId, agencyId)));
        assertThat(priority.getValue(), equalTo(1000));
    }

    @Test
    public void testActionPerform_ProviderPH() throws Exception {
        final String bibliographicRecordId = "12345678";
        final int agencyId = 654321;

        final MarcRecord record = prepareRecord(bibliographicRecordId, agencyId);

        state.setLibraryGroup(LibraryGroup.PH);

        final EnqueueRecordAction enqueueRecordAction = new EnqueueRecordAction(state, settings, record);
        assertThat(enqueueRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        final ArgumentCaptor<String> argProvider = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<RecordId> argId = ArgumentCaptor.forClass(RecordId.class);
        final ArgumentCaptor<Integer> priority = ArgumentCaptor.forClass(int.class);

        verify(state.getRawRepo()).changedRecord(argProvider.capture(), argId.capture(), priority.capture());
        assertThat(argProvider.getValue(), equalTo(enqueueRecordAction.settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_PH)));
        assertThat(argId.getValue(), equalTo(new RecordId(bibliographicRecordId, agencyId)));
        assertThat(priority.getValue(), equalTo(500));
    }
}
