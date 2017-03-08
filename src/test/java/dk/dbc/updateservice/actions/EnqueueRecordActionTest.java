package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
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
        instance.setMimetype(MarcXChangeMimeType.MARCXCHANGE);
        String message = state.getMessages().getString("provider.id.not.set");
        assertThat(instance.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state)));
        verify(state.getRawRepo(), never()).changedRecord(anyString(), any(RecordId.class), anyString());
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
    public void testActionPerform_WithProviderId() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        Integer agencyId = AssertActionsUtil.getAgencyIdAsInteger(record);

        EnqueueRecordAction enqueueRecordAction = new EnqueueRecordAction(state, settings, record);
        enqueueRecordAction.setMimetype(MarcXChangeMimeType.MARCXCHANGE);
        assertThat(enqueueRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        ArgumentCaptor<String> argProvider = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RecordId> argId = ArgumentCaptor.forClass(RecordId.class);
        ArgumentCaptor<String> argMimetype = ArgumentCaptor.forClass(String.class);

        verify(state.getRawRepo()).changedRecord(argProvider.capture(), argId.capture(), argMimetype.capture());
        assertThat(argProvider.getValue(), equalTo(enqueueRecordAction.settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_FBS)));
        assertThat(argId.getValue(), equalTo(new RecordId(recordId, agencyId)));
        assertThat(argMimetype.getValue(), equalTo(enqueueRecordAction.getMimetype()));
    }
}
