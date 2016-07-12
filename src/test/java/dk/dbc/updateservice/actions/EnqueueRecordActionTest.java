package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.RawRepo;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ResourceBundle;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

public class EnqueueRecordActionTest {
    private ResourceBundle messages;

    public EnqueueRecordActionTest() {
        this.messages = ResourceBundles.getBundle(this, "actions");
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

        RawRepo rawRepo = mock(RawRepo.class);
        EnqueueRecordAction instance = new EnqueueRecordAction(rawRepo, record);
        instance.setMimetype(MarcXChangeMimeType.MARCXCHANGE);

        String message = messages.getString("provider.id.not.set");
        assertThat(instance.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message)));

        verify(rawRepo, never()).changedRecord(anyString(), any(RecordId.class), anyString());
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
        Integer agencyId = AssertActionsUtil.getAgencyId(record);

        RawRepo rawRepo = mock(RawRepo.class);
        EnqueueRecordAction instance = new EnqueueRecordAction(rawRepo, record);
        instance.setProviderId("xxx");
        instance.setMimetype(MarcXChangeMimeType.MARCXCHANGE);

        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        ArgumentCaptor<String> argProvider = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RecordId> argId = ArgumentCaptor.forClass(RecordId.class);
        ArgumentCaptor<String> argMimetype = ArgumentCaptor.forClass(String.class);

        verify(rawRepo).changedRecord(argProvider.capture(), argId.capture(), argMimetype.capture());
        assertThat(argProvider.getValue(), equalTo(instance.getProviderId()));
        assertThat(argId.getValue(), equalTo(new RecordId(recordId, agencyId)));
        assertThat(argMimetype.getValue(), equalTo(instance.getMimetype()));
    }
}
