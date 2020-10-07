/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;


import dk.dbc.common.records.MarcRecord;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.utils.ResourceBundles;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.ResourceBundle;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LinkMatVurdRecordsActionTest {
    private GlobalActionState state;

    @Before
    public void before() throws Exception {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
        when(state.getRawRepo().recordExists("11111111", 870970)).thenReturn(true);
        when(state.getRawRepo().recordExists("22222222", 870970)).thenReturn(true);
        when(state.getRawRepo().recordExists("33333333", 870970)).thenReturn(true);
        when(state.getRawRepo().recordExists("44444444", 870970)).thenReturn(true);
        when(state.getRawRepo().recordExists("99999999", 870970)).thenReturn(false);

    }

    @Test
    public void testNewLinkMatVurdRecordAction_r01_single() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.MATVURD_1);
        final LinkMatVurdRecordsAction instance = new LinkMatVurdRecordsAction(state, record);

        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        ArgumentCaptor<RecordId> fromProvider = ArgumentCaptor.forClass(RecordId.class);
        ArgumentCaptor<RecordId> toProvider = ArgumentCaptor.forClass(RecordId.class);

        verify(state.getRawRepo(), times(1)).linkRecordAppend(fromProvider.capture(), toProvider.capture());

        assertThat(fromProvider.getValue().getAgencyId(), equalTo(870976));
        assertThat(fromProvider.getValue().getBibliographicRecordId(), equalTo("12345678"));

        assertThat(toProvider.getValue().getAgencyId(), equalTo(870970));
        assertThat(toProvider.getValue().getBibliographicRecordId(), equalTo("11111111"));
    }

    @Test
    public void testNewLinkMatVurdRecordAction_r01_multiple() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.MATVURD_2);
        final LinkMatVurdRecordsAction instance = new LinkMatVurdRecordsAction(state, record);

        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        ArgumentCaptor<RecordId> fromProvider = ArgumentCaptor.forClass(RecordId.class);
        ArgumentCaptor<RecordId> toProvider = ArgumentCaptor.forClass(RecordId.class);

        verify(state.getRawRepo(), times(2)).linkRecordAppend(fromProvider.capture(), toProvider.capture());

        assertThat(fromProvider.getValue().getAgencyId(), equalTo(870976));
        assertThat(fromProvider.getValue().getBibliographicRecordId(), equalTo("12345678"));

        assertThat(toProvider.getAllValues(), equalTo(Arrays.asList(
                new RecordId("11111111", 870970),
                new RecordId("22222222", 870970))));
    }

    @Test
    public void testNewLinkMatVurdRecordAction_r02_single() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.MATVURD_3);
        final LinkMatVurdRecordsAction instance = new LinkMatVurdRecordsAction(state, record);

        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        ArgumentCaptor<RecordId> fromProvider = ArgumentCaptor.forClass(RecordId.class);
        ArgumentCaptor<RecordId> toProvider = ArgumentCaptor.forClass(RecordId.class);

        verify(state.getRawRepo(), times(1)).linkRecordAppend(fromProvider.capture(), toProvider.capture());

        assertThat(fromProvider.getValue().getAgencyId(), equalTo(870976));
        assertThat(fromProvider.getValue().getBibliographicRecordId(), equalTo("12345678"));

        assertThat(toProvider.getValue().getAgencyId(), equalTo(870970));
        assertThat(toProvider.getValue().getBibliographicRecordId(), equalTo("11111111"));
    }

    @Test
    public void testNewLinkMatVurdRecordAction_r02_multiple() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.MATVURD_4);
        final LinkMatVurdRecordsAction instance = new LinkMatVurdRecordsAction(state, record);

        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        ArgumentCaptor<RecordId> fromProvider = ArgumentCaptor.forClass(RecordId.class);
        ArgumentCaptor<RecordId> toProvider = ArgumentCaptor.forClass(RecordId.class);

        verify(state.getRawRepo(), times(2)).linkRecordAppend(fromProvider.capture(), toProvider.capture());

        assertThat(fromProvider.getValue().getAgencyId(), equalTo(870976));
        assertThat(fromProvider.getValue().getBibliographicRecordId(), equalTo("12345678"));

        assertThat(toProvider.getAllValues(), equalTo(Arrays.asList(
                new RecordId("11111111", 870970),
                new RecordId("22222222", 870970))));
    }

    @Test
    public void testNewLinkMatVurdRecordAction_r01_r02_multiple() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.MATVURD_5);
        final LinkMatVurdRecordsAction instance = new LinkMatVurdRecordsAction(state, record);

        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        ArgumentCaptor<RecordId> fromProvider = ArgumentCaptor.forClass(RecordId.class);
        ArgumentCaptor<RecordId> toProvider = ArgumentCaptor.forClass(RecordId.class);

        verify(state.getRawRepo(), times(4)).linkRecordAppend(fromProvider.capture(), toProvider.capture());

        assertThat(fromProvider.getValue().getAgencyId(), equalTo(870976));
        assertThat(fromProvider.getValue().getBibliographicRecordId(), equalTo("12345678"));

        assertThat(toProvider.getAllValues(), equalTo(Arrays.asList(
                new RecordId("11111111", 870970),
                new RecordId("22222222", 870970),
                new RecordId("33333333", 870970),
                new RecordId("44444444", 870970))));
    }

    @Test
    public void testNewLinkMatVurdRecordAction_NotFound() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.MATVURD_6);

        ResourceBundle resourceBundle = ResourceBundles.getBundle("actions");
        String message = String.format(resourceBundle.getString("ref.record.doesnt.exist"), "99999999", "870970");

        final LinkMatVurdRecordsAction instance = new LinkMatVurdRecordsAction(state, record);
        assertThat(instance.performAction(), equalTo(UpdateTestUtils.createFailedServiceResult(message)));
    }

    @Test
    public void recordWithoutMatVurdFields() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);

        LinkMatVurdRecordsAction instance = new LinkMatVurdRecordsAction(state, record);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        verify(state.getRawRepo(), never()).linkRecordAppend(any(RecordId.class), any(RecordId.class));
    }

}
