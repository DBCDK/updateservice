/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.rawrepo.RecordId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.verify;

class RemoveLinksActionTest {
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
    void testPerformAction() throws Exception {
        GlobalActionState state = new UpdateTestUtils().getGlobalActionStateMockObject();
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.getRecordId();
        int agencyId = reader.getAgencyIdAsInt();

        RemoveLinksAction instance = new RemoveLinksAction(state, record);
        assertThat(instance.performAction(), is(ServiceResult.newOkResult()));

        ArgumentCaptor<RecordId> arg = ArgumentCaptor.forClass(RecordId.class);
        verify(state.getRawRepo()).removeLinks(arg.capture());
        assertThat(arg.getValue(), is(new RecordId(recordId, agencyId)));
    }
}
