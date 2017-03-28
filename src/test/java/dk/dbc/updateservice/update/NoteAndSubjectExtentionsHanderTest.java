package dk.dbc.updateservice.update;


import dk.dbc.iscrum.records.*;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.updateservice.actions.AssertActionsUtil;
import dk.dbc.updateservice.actions.GlobalActionState;
import dk.dbc.updateservice.actions.UpdateTestUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

public class NoteAndSubjectExtentionsHanderTest {
    private GlobalActionState state;

    @Before
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();

        MockitoAnnotations.initMocks(this);
    }

    @Mock
    private OpenAgencyService openAgencyService;

    @Mock
    private RawRepo rawRepo;

    @Test
    public void testIsFieldNationalCommonField_wrongField() throws Exception{
        MarcField field = new MarcField("001", "00");
        field.getSubfields().add(new MarcSubField("a", "12345678"));
        field.getSubfields().add(new MarcSubField("b", "870970"));

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo);
        assertThat(instance.isFieldNationalCommonField(field), equalTo(false));
    }

    @Test
    public void testIsFieldNationalCommonField_wrongSubfieldValue() throws Exception{
        MarcField field = new MarcField("032", "00");
        field.getSubfields().add(new MarcSubField("a", "ABC"));

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo);
        assertThat(instance.isFieldNationalCommonField(field), equalTo(true));
    }

    @Test
    public void testIsFieldNationalCommonField() throws Exception{
        MarcField field = new MarcField("032", "00");
        field.getSubfields().add(new MarcSubField("a", "BKM"));
        field.getSubfields().add(new MarcSubField("b", "870970"));

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo);
        assertThat(instance.isFieldNationalCommonField(field), equalTo(false));
    }

    @Test
    public void testisNationalCommonRecord_wrong032() throws Exception{
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo);
        assertThat(instance.isNationalCommonRecord(record), equalTo(true));
    }

    @Test
    public void testisNationalCommonRecord() throws Exception{
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.addOrReplaceSubfield("032", "a", "NET");

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo);
        assertThat(instance.isNationalCommonRecord(record), equalTo(false));
    }

    @Test
    public void testcreateExtentableFieldsRx_none() throws Exception {
        String agencyId = "870970";

        when(openAgencyService.hasFeature(eq(agencyId), eq(LibraryRuleHandler.Rule.AUTH_COMMON_NOTES))).thenReturn(false);
        when(openAgencyService.hasFeature(eq(agencyId), eq(LibraryRuleHandler.Rule.AUTH_COMMON_SUBJECTS))).thenReturn(false);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo);
        assertThat(instance.createExtendableFieldsRx(agencyId), equalTo(""));
    }

    @Test
    public void testcreateExtentableFieldsRx_notes() throws Exception {
        String agencyId = "870970";

        when(openAgencyService.hasFeature(eq(agencyId), eq(LibraryRuleHandler.Rule.AUTH_COMMON_NOTES))).thenReturn(true);
        when(openAgencyService.hasFeature(eq(agencyId), eq(LibraryRuleHandler.Rule.AUTH_COMMON_SUBJECTS))).thenReturn(false);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo);
        assertThat(instance.createExtendableFieldsRx(agencyId), equalTo(NoteAndSubjectExtensionsHandler.EXTENDABLE_NOTE_FIELDS));
    }

    @Test
    public void testcreateExtentableFieldsRx_subjects() throws Exception {
        String agencyId = "870970";

        when(openAgencyService.hasFeature(eq(agencyId), eq(LibraryRuleHandler.Rule.AUTH_COMMON_NOTES))).thenReturn(false);
        when(openAgencyService.hasFeature(eq(agencyId), eq(LibraryRuleHandler.Rule.AUTH_COMMON_SUBJECTS))).thenReturn(true);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo);
        assertThat(instance.createExtendableFieldsRx(agencyId), equalTo(NoteAndSubjectExtensionsHandler.EXTENDABLE_SUBJECT_FIELDS));
    }

    @Test
    public void testcreateExtentableFieldsRx_both() throws Exception {
        String agencyId = "870970";

        when(openAgencyService.hasFeature(eq(agencyId), eq(LibraryRuleHandler.Rule.AUTH_COMMON_NOTES))).thenReturn(true);
        when(openAgencyService.hasFeature(eq(agencyId), eq(LibraryRuleHandler.Rule.AUTH_COMMON_SUBJECTS))).thenReturn(true);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo);
        assertThat(instance.createExtendableFieldsRx(agencyId), equalTo(NoteAndSubjectExtensionsHandler.EXTENDABLE_NOTE_FIELDS + "|" + NoteAndSubjectExtensionsHandler.EXTENDABLE_SUBJECT_FIELDS));
    }

    @Test
    public void testisFieldChangedInOtherRecord_001_match() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcField field = new MarcField("001", "00");
        field.getSubfields().add(new MarcSubField("a", "111111111"));
        field.getSubfields().add(new MarcSubField("b", "870970"));
        field.getSubfields().add(new MarcSubField("c", "19971020"));
        field.getSubfields().add(new MarcSubField("d", "19940516"));
        field.getSubfields().add(new MarcSubField("f", "a"));

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo);
        assertThat(instance.isFieldChangedInOtherRecord(field, record), equalTo(true));
    }

    @Test
    public void testisFieldChangedInOtherRecord_001_nomatch() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);

        // 001 00 *a 20611529 *b 870970 *c 19971020 *d 19940516 *f a
        MarcField field = new MarcField("001", "00");
        field.getSubfields().add(new MarcSubField("a", "20611529"));
        field.getSubfields().add(new MarcSubField("b", "870970"));
        field.getSubfields().add(new MarcSubField("c", "19971020"));
        field.getSubfields().add(new MarcSubField("d", "19940516"));
        field.getSubfields().add(new MarcSubField("f", "a"));

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo);
        assertThat(instance.isFieldChangedInOtherRecord(field, record), equalTo(false));
    }

    @Test
    public void testisFieldChangedInOtherRecord_001_missing001cd_match() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.removeSubfield("001", "c");
        writer.removeSubfield("001", "d");

        // 001 00 *a 20611529 *b 870970 *c 19971020 *d 19940516 *f a
        MarcField field = new MarcField("001", "00");
        field.getSubfields().add(new MarcSubField("a", "20611529"));
        field.getSubfields().add(new MarcSubField("b", "870970"));
        field.getSubfields().add(new MarcSubField("c", "19971020"));
        field.getSubfields().add(new MarcSubField("d", "19940516"));
        field.getSubfields().add(new MarcSubField("f", "a"));

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo);
        assertThat(instance.isFieldChangedInOtherRecord(field, record), equalTo(true));
    }

    @Test
    public void testrecordDataForRawRepo_recordNotExists() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        String groupId = "870970";

        when(rawRepo.recordExists(eq(reader.recordId()), eq(RawRepo.RAWREPO_COMMON_LIBRARY))).thenReturn(false);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo);

        assertThat(instance.recordDataForRawRepo(record, groupId), equalTo(record));
    }

    @Test
    public void testrecordDataForRawRepo_isNotNationalCommonRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        String groupId = "870970";

        when(rawRepo.recordExists(eq(reader.recordId()), eq(RawRepo.RAWREPO_COMMON_LIBRARY))).thenReturn(true);
        when(rawRepo.fetchRecord(eq(reader.recordId()), eq(RawRepo.RAWREPO_COMMON_LIBRARY))).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo);

        assertThat(instance.recordDataForRawRepo(record, groupId), equalTo(record));
    }

    @Test
    public void testrecordDataForRawRepo() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        MarcRecordWriter writer = new MarcRecordWriter(record);

        writer.addOrReplaceSubfield("032", "a", "NET");
        String groupId = "870970";

        when(rawRepo.recordExists(eq(reader.recordId()), eq(RawRepo.RAWREPO_COMMON_LIBRARY))).thenReturn(true);
        when(rawRepo.fetchRecord(eq(reader.recordId()), eq(RawRepo.RAWREPO_COMMON_LIBRARY))).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(openAgencyService.hasFeature(eq(groupId), eq(LibraryRuleHandler.Rule.AUTH_COMMON_NOTES))).thenReturn(true);
        when(openAgencyService.hasFeature(eq(groupId), eq(LibraryRuleHandler.Rule.AUTH_COMMON_SUBJECTS))).thenReturn(true);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo);
        MarcRecord expected = new MarcRecord(record);
        //new MarcRecordReader(expected).getField("504").getSubfields().add(new MarcSubField("&", groupId));

        assertThat(instance.recordDataForRawRepo(record, groupId), equalTo(expected));
    }

}
