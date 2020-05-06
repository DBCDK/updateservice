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

import java.io.IOException;
import java.util.ResourceBundle;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LinkMatVurdRecordsActionTest {
    private GlobalActionState state;

    @Before
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
    }

    @Test
    public void testNewLinkMatVurdRecordAction_1() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.MATVURD_1);

        when(state.getRawRepo().recordExists("52919568", 870970)).thenReturn(true);

        final LinkMatVurdRecordsAction instance = new LinkMatVurdRecordsAction(state, record);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        ArgumentCaptor<RecordId> fromProvider = ArgumentCaptor.forClass(RecordId.class);
        ArgumentCaptor<RecordId> toProvider = ArgumentCaptor.forClass(RecordId.class);

        verify(state.getRawRepo(), times(1)).linkRecordAppend(fromProvider.capture(), toProvider.capture());

        assertThat(fromProvider.getValue().getAgencyId(), equalTo(870976));
        assertThat(fromProvider.getValue().getBibliographicRecordId(), equalTo("12345678"));

        assertThat(toProvider.getValue().getAgencyId(), equalTo(870970));
        assertThat(toProvider.getValue().getBibliographicRecordId(), equalTo("52919568"));
    }

    @Test
    public void testNewLinkMatVurdRecordAction_2() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.MATVURD_2);

        when(state.getRawRepo().recordExists("54486960", 870970)).thenReturn(true);
        when(state.getRawRepo().recordExists("54486987", 870970)).thenReturn(true);

        final LinkMatVurdRecordsAction instance = new LinkMatVurdRecordsAction(state, record);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        ArgumentCaptor<RecordId> fromProvider = ArgumentCaptor.forClass(RecordId.class);
        ArgumentCaptor<RecordId> toProvider = ArgumentCaptor.forClass(RecordId.class);

        verify(state.getRawRepo(), times(2)).linkRecordAppend(fromProvider.capture(), toProvider.capture());

        assertThat(fromProvider.getValue().getAgencyId(), equalTo(870976));
        assertThat(fromProvider.getValue().getBibliographicRecordId(), equalTo("12345678"));

        assertThat(toProvider.getValue().getAgencyId(), equalTo(870970));
        assertThat(toProvider.getValue().getBibliographicRecordId(), equalTo("54486960"));
    }

    @Test
    public void testNewLinkMatVurdRecordAction_3() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.MATVURD_3);

        when(state.getRawRepo().recordExists("47791588", 870970)).thenReturn(true);

        final LinkMatVurdRecordsAction instance = new LinkMatVurdRecordsAction(state, record);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        ArgumentCaptor<RecordId> fromProvider = ArgumentCaptor.forClass(RecordId.class);
        ArgumentCaptor<RecordId> toProvider = ArgumentCaptor.forClass(RecordId.class);

        verify(state.getRawRepo(), times(1)).linkRecordAppend(fromProvider.capture(), toProvider.capture());

        assertThat(fromProvider.getValue().getAgencyId(), equalTo(870976));
        assertThat(fromProvider.getValue().getBibliographicRecordId(), equalTo("12345678"));

        assertThat(toProvider.getValue().getAgencyId(), equalTo(870970));
        assertThat(toProvider.getValue().getBibliographicRecordId(), equalTo("47791588"));
    }

    @Test
    public void testNewLinkMatVurdRecordAction_4() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.MATVURD_4);

        when(state.getRawRepo().recordExists("47909481", 870970)).thenReturn(true);

        final LinkMatVurdRecordsAction instance = new LinkMatVurdRecordsAction(state, record);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        ArgumentCaptor<RecordId> fromProvider = ArgumentCaptor.forClass(RecordId.class);
        ArgumentCaptor<RecordId> toProvider = ArgumentCaptor.forClass(RecordId.class);

        verify(state.getRawRepo(), times(1)).linkRecordAppend(fromProvider.capture(), toProvider.capture());

        assertThat(fromProvider.getValue().getAgencyId(), equalTo(870976));
        assertThat(fromProvider.getValue().getBibliographicRecordId(), equalTo("12345678"));

        assertThat(toProvider.getValue().getAgencyId(), equalTo(870970));
        assertThat(toProvider.getValue().getBibliographicRecordId(), equalTo("47909481"));
    }

    @Test
    public void testNewLinkMatVurdRecordAction_5() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.MATVURD_5);

        when(state.getRawRepo().recordExists("21126209", 870970)).thenReturn(true);

        final LinkMatVurdRecordsAction instance = new LinkMatVurdRecordsAction(state, record);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        ArgumentCaptor<RecordId> fromProvider = ArgumentCaptor.forClass(RecordId.class);
        ArgumentCaptor<RecordId> toProvider = ArgumentCaptor.forClass(RecordId.class);

        verify(state.getRawRepo(), times(1)).linkRecordAppend(fromProvider.capture(), toProvider.capture());

        assertThat(fromProvider.getValue().getAgencyId(), equalTo(870976));
        assertThat(fromProvider.getValue().getBibliographicRecordId(), equalTo("12345678"));

        assertThat(toProvider.getValue().getAgencyId(), equalTo(870970));
        assertThat(toProvider.getValue().getBibliographicRecordId(), equalTo("21126209"));
    }

    @Test
    public void testNewLinkMatVurdRecordAction_6() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.MATVURD_6);

        when(state.getRawRepo().recordExists("55100594", 870970)).thenReturn(true);
        when(state.getRawRepo().recordExists("54945124", 870970)).thenReturn(true);

        final LinkMatVurdRecordsAction instance = new LinkMatVurdRecordsAction(state, record);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        ArgumentCaptor<RecordId> fromProvider = ArgumentCaptor.forClass(RecordId.class);
        ArgumentCaptor<RecordId> toProvider = ArgumentCaptor.forClass(RecordId.class);

        verify(state.getRawRepo(), times(2)).linkRecordAppend(fromProvider.capture(), toProvider.capture());

        assertThat(fromProvider.getValue().getAgencyId(), equalTo(870976));
        assertThat(fromProvider.getValue().getBibliographicRecordId(), equalTo("12345678"));

        assertThat(toProvider.getValue().getAgencyId(), equalTo(870970));
        assertThat(toProvider.getValue().getBibliographicRecordId(), equalTo("54945124"));
    }

    @Test
    public void recordWithoutMatVurdFields() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);

        LinkMatVurdRecordsAction instance = new LinkMatVurdRecordsAction(state, record);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        verify(state.getRawRepo(), never()).linkRecordAppend(any(RecordId.class), any(RecordId.class));
    }


    @Test
    public void recordWithMatVurdFields_NotFound() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.MATVURD_1);

        when(state.getRawRepo().recordExists("52919568", 870970)).thenReturn(false);

        ResourceBundle resourceBundle = ResourceBundles.getBundle("actions");
        String message = String.format(resourceBundle.getString("ref.record.doesnt.exist"), "52919568", "870970");

        final LinkMatVurdRecordsAction instance = new LinkMatVurdRecordsAction(state, record);
        assertThat(instance.performAction(), equalTo(UpdateTestUtils.createFailedServiceResult(message)));
    }
}
