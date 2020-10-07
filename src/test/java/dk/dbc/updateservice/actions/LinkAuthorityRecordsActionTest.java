/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;


import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcSubField;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.utils.ResourceBundles;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.Arrays;
import java.util.ResourceBundle;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LinkAuthorityRecordsActionTest {
    private GlobalActionState state;

    @Before
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
    }

    @Test
    public void recordWithoutAuthFields() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);

        LinkAuthorityRecordsAction instance = new LinkAuthorityRecordsAction(state, record);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        verify(state.getRawRepo(), never()).linkRecordAppend(any(RecordId.class), any(RecordId.class));
    }

    @Test
    public void recordWithAuthFields() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        record.getFields().add(new MarcField("100", "00", Arrays.asList(new MarcSubField("5", "870979"), new MarcSubField("6", "11111111"))));
        record.getFields().add(new MarcField("600", "00", Arrays.asList(new MarcSubField("5", "870979"), new MarcSubField("6", "22222222"))));
        record.getFields().add(new MarcField("700", "00", Arrays.asList(new MarcSubField("5", "870979"), new MarcSubField("6", "33333333"))));

        when(state.getRawRepo().recordExists("11111111", 870979)).thenReturn(true);
        when(state.getRawRepo().recordExists("22222222", 870979)).thenReturn(true);
        when(state.getRawRepo().recordExists("33333333", 870979)).thenReturn(true);

        LinkAuthorityRecordsAction instance = new LinkAuthorityRecordsAction(state, record);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        ArgumentCaptor<RecordId> fromProvider = ArgumentCaptor.forClass(RecordId.class);
        ArgumentCaptor<RecordId> toProvider = ArgumentCaptor.forClass(RecordId.class);

        verify(state.getRawRepo(), times(3)).linkRecordAppend(fromProvider.capture(), toProvider.capture());

        assertThat(fromProvider.getValue().getAgencyId(), equalTo(870970));
        assertThat(fromProvider.getValue().getBibliographicRecordId(), equalTo("20611529"));

        assertThat(toProvider.getValue().getAgencyId(), equalTo(870979));
        assertThat(toProvider.getValue().getBibliographicRecordId(), equalTo("33333333"));
    }

    @Test
    public void recordWithAuthFields_NotFound() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        record.getFields().add(new MarcField("600", "00", Arrays.asList(new MarcSubField("5", "870979"), new MarcSubField("6", "22222222"))));

        when(state.getRawRepo().recordExists("22222222", 870979)).thenReturn(false);

        ResourceBundle resourceBundle = ResourceBundles.getBundle("actions");
        String message = String.format(resourceBundle.getString("ref.record.doesnt.exist"), "22222222", "870979");

        LinkAuthorityRecordsAction instance = new LinkAuthorityRecordsAction(state, record);
        assertThat(instance.performAction(), equalTo(UpdateTestUtils.createFailedServiceResult(message)));
    }
}
