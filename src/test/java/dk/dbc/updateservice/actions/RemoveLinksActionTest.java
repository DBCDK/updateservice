/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.rawrepo.RecordId;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Mockito.verify;

public class RemoveLinksActionTest {
    /**
     * Test RemovesLinksAction.performAction() to remove all links from a record.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Remove the links for a record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * The rawrepo is called to remove all links for the record.
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction() throws Exception {
        GlobalActionState state = new UpdateTestUtils().getGlobalActionStateMockObject();
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.getRecordId();
        Integer agencyId = reader.getAgencyIdAsInteger();

        RemoveLinksAction instance = new RemoveLinksAction(state, record);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        ArgumentCaptor<RecordId> arg = ArgumentCaptor.forClass(RecordId.class);
        verify(state.getRawRepo()).removeLinks(arg.capture());
        assertThat(arg.getValue(), equalTo(new RecordId(recordId, agencyId)));
    }
}
