package dk.dbc.updateservice.actions;


import dk.dbc.iscrum.records.MarcField;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcSubField;
import dk.dbc.rawrepo.RecordId;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class LinkAuthorityRecordsActionTest {
    private GlobalActionState state;
    private Properties settings = new UpdateTestUtils().getSettings();

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

}
