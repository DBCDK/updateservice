/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class LinkRecordActionTest {
    private GlobalActionState state;

    @Before
    public void before() throws IOException {
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
    public void testPerformAction_LinkedRecordExist() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.VOLUME_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.getRecordId();
        int agencyId = reader.getAgencyIdAsInt();
        String parentId = reader.getParentRecordId();

        when(state.getRawRepo().recordExists(eq(parentId), eq(agencyId))).thenReturn(true);

        LinkRecordAction linkRecordAction = new LinkRecordAction(state, record);
        linkRecordAction.setLinkToRecordId(new RecordId(parentId, agencyId));
        assertThat(linkRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        ArgumentCaptor<String> argRecordId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> argAgencyId = ArgumentCaptor.forClass(Integer.class);
        verify(state.getRawRepo()).recordExists(argRecordId.capture(), argAgencyId.capture());
        assertThat(argRecordId.getValue(), equalTo(parentId));
        assertThat(argAgencyId.getValue(), equalTo(agencyId));

        ArgumentCaptor<RecordId> argFrom = ArgumentCaptor.forClass(RecordId.class);
        ArgumentCaptor<RecordId> argTo = ArgumentCaptor.forClass(RecordId.class);
        verify(state.getRawRepo()).linkRecord(argFrom.capture(), argTo.capture());
        assertThat(argFrom.getValue(), equalTo(new RecordId(recordId, agencyId)));
        assertThat(argTo.getValue(), equalTo(linkRecordAction.getLinkToRecordId()));
    }

    /**
     * Test LinkRecord.performAction() to create a link to an non existing record
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
    public void testPerformAction_LinkedRecordNotExist() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.VOLUME_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.getRecordId();
        int agencyId = reader.getAgencyIdAsInt();
        String parentId = reader.getParentRecordId();

        when(state.getRawRepo().recordExists(eq(parentId), eq(agencyId))).thenReturn(false);

        LinkRecordAction instance = new LinkRecordAction(state, record);
        instance.setLinkToRecordId(new RecordId(parentId, agencyId));
        String message = String.format(state.getMessages().getString("reference.record.not.exist"), recordId, agencyId, parentId, agencyId);
        assertThat(instance.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state)));

        ArgumentCaptor<String> argRecordId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> argAgencyId = ArgumentCaptor.forClass(Integer.class);
        verify(state.getRawRepo()).recordExists(argRecordId.capture(), argAgencyId.capture());
        assertThat(argRecordId.getValue(), equalTo(parentId));
        assertThat(argAgencyId.getValue(), equalTo(agencyId));
        verify(state.getRawRepo(), never()).linkRecord(any(RecordId.class), any(RecordId.class));
    }
}
