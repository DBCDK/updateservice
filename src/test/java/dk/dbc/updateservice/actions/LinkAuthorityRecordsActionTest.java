/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;


import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcSubField;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.utils.ResourceBundles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.Arrays;
import java.util.ResourceBundle;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LinkAuthorityRecordsActionTest {
    private GlobalActionState state;

    @BeforeEach
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
    }

    @Test
    void recordWithoutAuthFields() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);

        LinkAuthorityRecordsAction instance = new LinkAuthorityRecordsAction(state, record);
        assertThat(instance.performAction(), is(ServiceResult.newOkResult()));

        verify(state.getRawRepo(), never()).linkRecordAppend(any(RecordId.class), any(RecordId.class));
    }

    @Test
    void recordWithAuthFields() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        for (String field : RawRepo.AUTHORITY_FIELDS) {
            record.getFields().add(new MarcField(field, "00", Arrays.asList(new MarcSubField("5", "870979"), new MarcSubField("6", field + "11111111"))));
            when(state.getRawRepo().recordExists(field + "11111111", 870979)).thenReturn(true);
        }

        LinkAuthorityRecordsAction instance = new LinkAuthorityRecordsAction(state, record);
        assertThat(instance.performAction(), is(ServiceResult.newOkResult()));

        ArgumentCaptor<RecordId> fromProvider = ArgumentCaptor.forClass(RecordId.class);
        ArgumentCaptor<RecordId> toProvider = ArgumentCaptor.forClass(RecordId.class);

        verify(state.getRawRepo(), times(RawRepo.AUTHORITY_FIELDS.size())).linkRecordAppend(fromProvider.capture(), toProvider.capture());

        assertThat(fromProvider.getValue().getAgencyId(), is(870970));
        assertThat(fromProvider.getValue().getBibliographicRecordId(), is("20611529"));

        assertThat(toProvider.getValue().getAgencyId(), is(870979));
        assertThat(toProvider.getValue().getBibliographicRecordId(), is(RawRepo.AUTHORITY_FIELDS.get(RawRepo.AUTHORITY_FIELDS.size() - 1) + "11111111"));
    }

    @Test
    void recordWithAuthFields_NotFound() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        record.getFields().add(new MarcField("600", "00", Arrays.asList(new MarcSubField("5", "870979"), new MarcSubField("6", "22222222"))));

        when(state.getRawRepo().recordExists("22222222", 870979)).thenReturn(false);

        ResourceBundle resourceBundle = ResourceBundles.getBundle("actions");
        String message = String.format(resourceBundle.getString("ref.record.doesnt.exist"), "22222222", "870979");

        LinkAuthorityRecordsAction instance = new LinkAuthorityRecordsAction(state, record);
        assertThat(instance.performAction(), is(UpdateTestUtils.createFailedServiceResult(message)));
    }
}
