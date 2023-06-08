package dk.dbc.updateservice.actions;


import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.utils.ResourceBundles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.ResourceBundle;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LinkMatVurdRecordsActionTest {
    private GlobalActionState state;

    @BeforeEach
    public void before() throws Exception {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
        when(state.getRawRepo().recordExists("11111111", 870970)).thenReturn(true);
        when(state.getRawRepo().recordExists("22222222", 870970)).thenReturn(true);
        when(state.getRawRepo().recordExists("33333333", 870970)).thenReturn(true);
        when(state.getRawRepo().recordExists("44444444", 870970)).thenReturn(true);
        when(state.getRawRepo().recordExists("99999999", 870970)).thenReturn(false);

    }

    @Test
    void testNewLinkMatVurdRecordAction_r01_single() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.MATVURD_1);
        final LinkMatVurdRecordsAction instance = new LinkMatVurdRecordsAction(state, record);

        assertThat(instance.performAction(), is(ServiceResult.newOkResult()));

        ArgumentCaptor<RecordId> fromProvider = ArgumentCaptor.forClass(RecordId.class);
        ArgumentCaptor<RecordId> toProvider = ArgumentCaptor.forClass(RecordId.class);

        verify(state.getRawRepo(), times(1)).linkRecordAppend(fromProvider.capture(), toProvider.capture());

        assertThat(fromProvider.getValue().getAgencyId(), is(870976));
        assertThat(fromProvider.getValue().getBibliographicRecordId(), is("12345678"));

        assertThat(toProvider.getValue().getAgencyId(), is(870970));
        assertThat(toProvider.getValue().getBibliographicRecordId(), is("11111111"));
    }

    @Test
    void testNewLinkMatVurdRecordAction_r01_multiple() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.MATVURD_2);
        final LinkMatVurdRecordsAction instance = new LinkMatVurdRecordsAction(state, record);

        assertThat(instance.performAction(), is(ServiceResult.newOkResult()));

        ArgumentCaptor<RecordId> fromProvider = ArgumentCaptor.forClass(RecordId.class);
        ArgumentCaptor<RecordId> toProvider = ArgumentCaptor.forClass(RecordId.class);

        verify(state.getRawRepo(), times(2)).linkRecordAppend(fromProvider.capture(), toProvider.capture());

        assertThat(fromProvider.getValue().getAgencyId(), is(870976));
        assertThat(fromProvider.getValue().getBibliographicRecordId(), is("12345678"));

        assertThat(toProvider.getAllValues(), is(Arrays.asList(
                new RecordId("11111111", 870970),
                new RecordId("22222222", 870970))));
    }

    @Test
    void testNewLinkMatVurdRecordAction_r02_single() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.MATVURD_3);
        final LinkMatVurdRecordsAction instance = new LinkMatVurdRecordsAction(state, record);

        assertThat(instance.performAction(), is(ServiceResult.newOkResult()));

        ArgumentCaptor<RecordId> fromProvider = ArgumentCaptor.forClass(RecordId.class);
        ArgumentCaptor<RecordId> toProvider = ArgumentCaptor.forClass(RecordId.class);

        verify(state.getRawRepo(), times(1)).linkRecordAppend(fromProvider.capture(), toProvider.capture());

        assertThat(fromProvider.getValue().getAgencyId(), is(870976));
        assertThat(fromProvider.getValue().getBibliographicRecordId(), is("12345678"));

        assertThat(toProvider.getValue().getAgencyId(), is(870970));
        assertThat(toProvider.getValue().getBibliographicRecordId(), is("11111111"));
    }

    @Test
    void testNewLinkMatVurdRecordAction_r02_multiple() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.MATVURD_4);
        final LinkMatVurdRecordsAction instance = new LinkMatVurdRecordsAction(state, record);

        assertThat(instance.performAction(), is(ServiceResult.newOkResult()));

        ArgumentCaptor<RecordId> fromProvider = ArgumentCaptor.forClass(RecordId.class);
        ArgumentCaptor<RecordId> toProvider = ArgumentCaptor.forClass(RecordId.class);

        verify(state.getRawRepo(), times(2)).linkRecordAppend(fromProvider.capture(), toProvider.capture());

        assertThat(fromProvider.getValue().getAgencyId(), is(870976));
        assertThat(fromProvider.getValue().getBibliographicRecordId(), is("12345678"));

        assertThat(toProvider.getAllValues(), is(Arrays.asList(
                new RecordId("11111111", 870970),
                new RecordId("22222222", 870970))));
    }

    @Test
    void testNewLinkMatVurdRecordAction_r01_r02_multiple() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.MATVURD_5);
        final LinkMatVurdRecordsAction instance = new LinkMatVurdRecordsAction(state, record);

        assertThat(instance.performAction(), is(ServiceResult.newOkResult()));

        ArgumentCaptor<RecordId> fromProvider = ArgumentCaptor.forClass(RecordId.class);
        ArgumentCaptor<RecordId> toProvider = ArgumentCaptor.forClass(RecordId.class);

        verify(state.getRawRepo(), times(4)).linkRecordAppend(fromProvider.capture(), toProvider.capture());

        assertThat(fromProvider.getValue().getAgencyId(), is(870976));
        assertThat(fromProvider.getValue().getBibliographicRecordId(), is("12345678"));

        assertThat(toProvider.getAllValues(), is(Arrays.asList(
                new RecordId("11111111", 870970),
                new RecordId("22222222", 870970),
                new RecordId("33333333", 870970),
                new RecordId("44444444", 870970))));
    }

    @Test
    void testNewLinkMatVurdRecordAction_NotFound() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.MATVURD_6);

        ResourceBundle resourceBundle = ResourceBundles.getBundle("actions");
        String message = String.format(resourceBundle.getString("ref.record.doesnt.exist"), "99999999", "870970");

        final LinkMatVurdRecordsAction instance = new LinkMatVurdRecordsAction(state, record);
        assertThat(instance.performAction(), is(UpdateTestUtils.createFailedServiceResult(message)));
    }

    @Test
    void recordWithoutMatVurdFields() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);

        LinkMatVurdRecordsAction instance = new LinkMatVurdRecordsAction(state, record);
        assertThat(instance.performAction(), is(ServiceResult.newOkResult()));

        verify(state.getRawRepo(), never()).linkRecordAppend(any(RecordId.class), any(RecordId.class));
    }

}
