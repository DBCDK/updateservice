package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.UpdateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LinkRecordActionTest {
    private GlobalActionState state;

    @BeforeEach
    public void before() throws IOException, UpdateException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
    }

    /**
     * Test LinkRecord.performAction() to create a link to an existing record
     * in the rawrepo.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with a record.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Create a link to the record that is already in the rawrepo.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * The rawrepo is called to create the link to the existing record.
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_LinkedRecordExist() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.VOLUME_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.getRecordId();
        int agencyId = reader.getAgencyIdAsInt();
        String parentId = reader.getParentRecordId();

        when(state.getRawRepo().recordExists(parentId, agencyId)).thenReturn(true);

        LinkRecordAction linkRecordAction = new LinkRecordAction(state, record);
        linkRecordAction.setLinkToRecordId(new RecordId(parentId, agencyId));
        assertThat(linkRecordAction.performAction(), is(ServiceResult.newOkResult()));

        ArgumentCaptor<String> argRecordId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> argAgencyId = ArgumentCaptor.forClass(Integer.class);
        verify(state.getRawRepo()).recordExists(argRecordId.capture(), argAgencyId.capture());
        assertThat(argRecordId.getValue(), is(parentId));
        assertThat(argAgencyId.getValue(), is(agencyId));

        ArgumentCaptor<RecordId> argFrom = ArgumentCaptor.forClass(RecordId.class);
        ArgumentCaptor<RecordId> argTo = ArgumentCaptor.forClass(RecordId.class);
        verify(state.getRawRepo()).linkRecord(argFrom.capture(), argTo.capture());
        assertThat(argFrom.getValue(), is(new RecordId(recordId, agencyId)));
        assertThat(argTo.getValue(), is(linkRecordAction.getLinkToRecordId()));
    }

    /**
     * Test LinkRecord.performAction() to create a link to a non-existing record
     * in the rawrepo.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * An empty rawrepo.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Create a link to a record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * The rawrepo is called to create the link the existing record and
     * an error is returned.
     * </dd>
     * </dl>
     */
    @Test
    void testPerformAction_LinkedRecordNotExist() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.VOLUME_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.getRecordId();
        int agencyId = reader.getAgencyIdAsInt();
        String parentId = reader.getParentRecordId();

        when(state.getRawRepo().recordExists(parentId, agencyId)).thenReturn(false);

        LinkRecordAction instance = new LinkRecordAction(state, record);
        instance.setLinkToRecordId(new RecordId(parentId, agencyId));
        String message = String.format(state.getMessages().getString("reference.record.not.exist"), recordId, agencyId, parentId, agencyId);
        assertThat(instance.performAction(), is(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message)));

        ArgumentCaptor<String> argRecordId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> argAgencyId = ArgumentCaptor.forClass(Integer.class);
        verify(state.getRawRepo()).recordExists(argRecordId.capture(), argAgencyId.capture());
        assertThat(argRecordId.getValue(), is(parentId));
        assertThat(argAgencyId.getValue(), is(agencyId));
        verify(state.getRawRepo(), never()).linkRecord(any(RecordId.class), any(RecordId.class));
    }

}
