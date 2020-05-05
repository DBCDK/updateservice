/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcSubField;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.RawRepo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        assertThat(instance.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message)));

        ArgumentCaptor<String> argRecordId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> argAgencyId = ArgumentCaptor.forClass(Integer.class);
        verify(state.getRawRepo()).recordExists(argRecordId.capture(), argAgencyId.capture());
        assertThat(argRecordId.getValue(), equalTo(parentId));
        assertThat(argAgencyId.getValue(), equalTo(agencyId));
        verify(state.getRawRepo(), never()).linkRecord(any(RecordId.class), any(RecordId.class));
    }

    @Test
    public void testNewLinkMatVurdRecordAction_1() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.MATVURD_1);

        final List<LinkRecordAction> linkRecordActionList = LinkRecordAction.newLinkMatVurdRecordAction(state, record);

        assertThat(linkRecordActionList.size(), equalTo(1));

        final ListIterator<LinkRecordAction> iterator = linkRecordActionList.listIterator();
        AssertActionsUtil.assertLinkRecordAction(iterator.next(), state.getRawRepo(), record, constructMarcRecordWithId("52919568", RawRepo.COMMON_AGENCY));
    }

    @Test
    public void testNewLinkMatVurdRecordAction_2() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.MATVURD_2);

        final List<LinkRecordAction> linkRecordActionList = LinkRecordAction.newLinkMatVurdRecordAction(state, record);

        assertThat(linkRecordActionList.size(), equalTo(2));

        final ListIterator<LinkRecordAction> iterator = linkRecordActionList.listIterator();
        AssertActionsUtil.assertLinkRecordAction(iterator.next(), state.getRawRepo(), record, constructMarcRecordWithId("54486960", RawRepo.COMMON_AGENCY));
        AssertActionsUtil.assertLinkRecordAction(iterator.next(), state.getRawRepo(), record, constructMarcRecordWithId("54486987", RawRepo.COMMON_AGENCY));
    }

    @Test
    public void testNewLinkMatVurdRecordAction_3() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.MATVURD_3);

        final List<LinkRecordAction> linkRecordActionList = LinkRecordAction.newLinkMatVurdRecordAction(state, record);

        assertThat(linkRecordActionList.size(), equalTo(1));

        final ListIterator<LinkRecordAction> iterator = linkRecordActionList.listIterator();
        AssertActionsUtil.assertLinkRecordAction(iterator.next(), state.getRawRepo(), record, constructMarcRecordWithId("47791588", RawRepo.COMMON_AGENCY));
    }

    @Test
    public void testNewLinkMatVurdRecordAction_4() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.MATVURD_4);

        final List<LinkRecordAction> linkRecordActionList = LinkRecordAction.newLinkMatVurdRecordAction(state, record);

        assertThat(linkRecordActionList.size(), equalTo(1));

        final ListIterator<LinkRecordAction> iterator = linkRecordActionList.listIterator();
        AssertActionsUtil.assertLinkRecordAction(iterator.next(), state.getRawRepo(), record, constructMarcRecordWithId("47909481", RawRepo.COMMON_AGENCY));
    }

    @Test
    public void testNewLinkMatVurdRecordAction_5() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.MATVURD_5);

        final List<LinkRecordAction> linkRecordActionList = LinkRecordAction.newLinkMatVurdRecordAction(state, record);

        assertThat(linkRecordActionList.size(), equalTo(1));

        final ListIterator<LinkRecordAction> iterator = linkRecordActionList.listIterator();
        AssertActionsUtil.assertLinkRecordAction(iterator.next(), state.getRawRepo(), record, constructMarcRecordWithId("21126209", RawRepo.COMMON_AGENCY));
    }

    @Test
    public void testNewLinkMatVurdRecordAction_6() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.MATVURD_6);

        final List<LinkRecordAction> linkRecordActionList = LinkRecordAction.newLinkMatVurdRecordAction(state, record);

        assertThat(linkRecordActionList.size(), equalTo(2));

        final ListIterator<LinkRecordAction> iterator = linkRecordActionList.listIterator();
        AssertActionsUtil.assertLinkRecordAction(iterator.next(), state.getRawRepo(), record, constructMarcRecordWithId("55100594", RawRepo.COMMON_AGENCY));
        AssertActionsUtil.assertLinkRecordAction(iterator.next(), state.getRawRepo(), record, constructMarcRecordWithId("54945124", RawRepo.COMMON_AGENCY));
    }

    @Test
    public void testNewLinkMatVurdRecordAction_NoRelations() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.VOLUME_RECORD_RESOURCE);

        final List<LinkRecordAction> linkRecordActionList = LinkRecordAction.newLinkMatVurdRecordAction(state, record);

        assertThat(linkRecordActionList.size(), equalTo(0));
    }

    private MarcRecord constructMarcRecordWithId(String bibliographicRecordId, int agencyId) {
        final MarcRecord marcRecord = new MarcRecord();
        final MarcSubField marcSubFieldA = new MarcSubField("a", bibliographicRecordId);
        final MarcSubField marcSubFieldB = new MarcSubField("b", Integer.toString(agencyId));
        final MarcField marcField = new MarcField("001", "00");
        marcField.getSubfields().addAll(Arrays.asList(marcSubFieldA, marcSubFieldB));

        marcRecord.getFields().add(marcField);

        return marcRecord;
    }
}
