package dk.dbc.updateservice.update;

import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.marc.binding.DataField;
import dk.dbc.marc.binding.Field;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.marc.binding.SubField;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.actions.AssertActionsUtil;
import dk.dbc.updateservice.dto.MessageEntryDTO;
import dk.dbc.updateservice.dto.TypeEnumDTO;
import dk.dbc.updateservice.utils.ResourceBundles;
import dk.dbc.vipcore.libraryrules.VipCoreLibraryRulesConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

class NoteAndSubjectExtensionsHandlerTest {
    private static final ResourceBundle resourceBundle = ResourceBundles.getBundle("actions");
    private static final ResourceBundle resourceBundleMessages = ResourceBundles.getBundle("messages");

    @Mock
    VipCoreService vipCoreService;

    @Mock
    RawRepo rawRepo;

    private AutoCloseable closeable;

    @BeforeEach
    void openMocks() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void releaseMocks() throws Exception {
        closeable.close();
    }

    @Test
    void testisNationalCommonRecord_OK() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.addOrReplaceSubField("032", 'a', "DBF12345");
        writer.addOrReplaceSubField("032", 'x', "SFU12345");

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);
        assertThat(instance.isPublishedDBCRecord(record), is(false));
    }

    @Test
    void testcreateExtentableFieldsRx_none() throws Exception {
        String agencyId = "870970";

        when(vipCoreService.hasFeature(agencyId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(false);
        when(vipCoreService.hasFeature(agencyId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(false);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);
        assertThat(instance.createExtendableFieldsRx(agencyId), is(""));
    }

    @Test
    void testcreateExtentableFieldsRx_notes() throws Exception {
        String agencyId = "870970";

        when(vipCoreService.hasFeature(agencyId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(true);
        when(vipCoreService.hasFeature(agencyId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(false);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);
        assertThat(instance.createExtendableFieldsRx(agencyId), is(NoteAndSubjectExtensionsHandler.EXTENDABLE_NOTE_FIELDS));
    }

    @Test
    void testcreateExtentableFieldsRx_subjects() throws Exception {
        String agencyId = "870970";

        when(vipCoreService.hasFeature(agencyId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(false);
        when(vipCoreService.hasFeature(agencyId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(true);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);
        assertThat(instance.createExtendableFieldsRx(agencyId), is(NoteAndSubjectExtensionsHandler.EXTENDABLE_CONTROLLED_SUBJECT_FIELDS + "|" + NoteAndSubjectExtensionsHandler.EXTENDABLE_SUBJECT_FIELDS));
    }

    @Test
    void testcreateExtentableFieldsRx_both() throws Exception {
        String agencyId = "870970";

        when(vipCoreService.hasFeature(agencyId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(true);
        when(vipCoreService.hasFeature(agencyId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(true);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);
        assertThat(instance.createExtendableFieldsRx(agencyId), is(NoteAndSubjectExtensionsHandler.EXTENDABLE_NOTE_FIELDS + "|" + NoteAndSubjectExtensionsHandler.EXTENDABLE_CONTROLLED_SUBJECT_FIELDS + "|" + NoteAndSubjectExtensionsHandler.EXTENDABLE_SUBJECT_FIELDS));
    }

    @Test
    void testisFieldChangedInOtherRecord_001_match() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        DataField field = new DataField("001", "00");
        field.getSubFields().add(new SubField('a', "111111111"));
        field.getSubFields().add(new SubField('b', "870970"));
        field.getSubFields().add(new SubField('c', "19971020"));
        field.getSubFields().add(new SubField('d', "19940516"));
        field.getSubFields().add(new SubField('f', "a"));

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);
        assertThat(instance.isFieldChangedInOtherRecord(field, record), is(true));
    }

    @Test
    void testisFieldChangedInOtherRecord_001_nomatch() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);

        // 001 00 *a 20611529 *b 870970 *c 19971020 *d 19940516 *f a
        DataField field = new DataField("001", "00");
        field.getSubFields().add(new SubField('a', "20611529"));
        field.getSubFields().add(new SubField('b', "870970"));
        field.getSubFields().add(new SubField('c', "19971020"));
        field.getSubFields().add(new SubField('d', "19940516"));
        field.getSubFields().add(new SubField('f', "a"));

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);
        assertThat(instance.isFieldChangedInOtherRecord(field, record), is(false));
    }

    @Test
    void testisFieldChangedInOtherRecord_001_missing001cd_match() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.removeSubfield("001", 'c');
        writer.removeSubfield("001", 'd');

        // 001 00 *a 20611529 *b 870970 *c 19971020 *d 19940516 *f a
        DataField field = new DataField("001", "00");
        field.getSubFields().add(new SubField('a', "20611529"));
        field.getSubFields().add(new SubField('b', "870970"));
        field.getSubFields().add(new SubField('c', "19971020"));
        field.getSubFields().add(new SubField('d', "19940516"));
        field.getSubFields().add(new SubField('f', "a"));

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);
        assertThat(instance.isFieldChangedInOtherRecord(field, record), is(true));
    }

    @Test
    void testisFieldChangedInOtherRecord_Field900_withxwz_NoChange_1() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.EXPANDED_VOLUME);

        final DataField field = new DataField("900", "00");
        field.getSubFields().add(new SubField('a', "Møllgaard"));
        field.getSubFields().add(new SubField('h', "H. Peter"));
        field.getSubFields().add(new SubField('x', "se"));
        field.getSubFields().add(new SubField('w', "Møllgaard, Peter (f. 1964-02-23)"));
        field.getSubFields().add(new SubField('z', "700/1"));

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);
        assertThat(instance.isFieldChangedInOtherRecord(field, record), is(false));
    }

    @Test
    void testisFieldChangedInOtherRecord_Field900_Withxwz_NoChange_2() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.EXPANDED_VOLUME);

        final DataField field = new DataField("900", "00");
        field.getSubFields().add(new SubField('a', "Kastberg"));
        field.getSubFields().add(new SubField('h', "Claus"));
        field.getSubFields().add(new SubField('x', "se også under det senere navn"));
        field.getSubFields().add(new SubField('w', "Kastberg Nielsen, Claus"));
        field.getSubFields().add(new SubField('z', "700/2"));

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);
        assertThat(instance.isFieldChangedInOtherRecord(field, record), is(false));
    }

    @Test
    void testisFieldChangedInOtherRecord_Field900_Withoutxwz_NoChange() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.EXPANDED_VOLUME);

        final DataField field = new DataField("900", "00");
        field.getSubFields().add(new SubField('a', "Møllgaard"));
        field.getSubFields().add(new SubField('h', "H. Peter"));

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);
        assertThat(instance.isFieldChangedInOtherRecord(field, record), is(false));
    }

    @Test
    void testisFieldChangedInOtherRecord_Field900_Withoutxwz_Changed() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.EXPANDED_VOLUME);

        final DataField field = new DataField("900", "00");
        field.getSubFields().add(new SubField('a', "Møllegaard")); // Added 'e'
        field.getSubFields().add(new SubField('h', "H. Peter"));

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);
        assertThat(instance.isFieldChangedInOtherRecord(field, record), is(true));
    }

    @Test
    void testrecordDataForRawRepo_recordNotExists() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        String groupId = "870970";

        when(rawRepo.recordExists(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(false);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);

        assertThat(instance.extensionRecordDataForRawRepo(record, groupId), is(record));
    }

    @Test
    void testrecordDataForRawRepo_isNotNationalCommonRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        String groupId = "870970";

        when(rawRepo.recordExists(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecord(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);

        assertThat(instance.extensionRecordDataForRawRepo(record, groupId), is(record));
    }

    @Test
    void testCollapseSameRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecord currentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecord expected = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);

        String groupId = "830010";

        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(true);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(true);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);

        assertThat(sortRecord(instance.collapse(record, currentRecord, groupId, false)), is(sortRecord(expected)));
    }

    @Test
    void testCollapseWithAUT() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter recordWriter = new MarcRecordWriter(record);
        recordWriter.removeField("504");
        recordWriter.addOrReplaceSubField("504", '&', "1");
        recordWriter.addOrReplaceSubField("504", 'a', "Julemandens Nisseslagteri");

        MarcRecord currentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter currentWriter = new MarcRecordWriter(currentRecord);
        currentWriter.removeField("100");
        currentWriter.addOrReplaceSubField("100", '5', "12345678");
        currentWriter.addOrReplaceSubField("100", '6', "876543");

        MarcRecord expected = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter expectedWriter = new MarcRecordWriter(expected);
        expectedWriter.removeField("100");
        expectedWriter.addOrReplaceSubField("100", '5', "12345678");
        expectedWriter.addOrReplaceSubField("100", '6', "876543");
        expectedWriter.removeField("504");
        expectedWriter.addOrReplaceSubField("504", '&', "1");
        expectedWriter.addOrReplaceSubField("504", 'a', "Julemandens Nisseslagteri");

        String groupId = "830010";

        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(true);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(true);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);

        assertThat(sortRecord(instance.collapse(record, currentRecord, groupId, false)), is(sortRecord(expected)));
    }

    @Test
    void testNoteAndSubject_NoNotesOrSubjectPermissions() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);

        MarcRecordReader reader = new MarcRecordReader(record);

        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.addOrReplaceSubField("032", 'a', "DBF12345");
        writer.addOrReplaceSubField("032", 'x', "ACC12345");

        MarcRecord existingRecord = new MarcRecord(record);

        when(rawRepo.recordExists(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecord(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingRecord, MarcXChangeMimeType.MARCXCHANGE));

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);
        assertThat(instance.extensionRecordDataForRawRepo(record, "820010"), is(record));
    }

    @Test
    void testNoteAndSubjectFields_DBCNote() throws Exception {
        String groupId = "830010";

        MarcRecord currentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        MarcRecordReader currentReader = new MarcRecordReader(currentRecord);
        DataField currentNoteField = new DataField("666", "00");
        currentNoteField.getSubFields().add(new SubField('&', "1"));
        currentNoteField.getSubFields().add(new SubField('a', "Julemandens Nisseslagteri"));
        currentRecord.getFields().add(currentNoteField);

        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        DataField field = new DataField("530", "00");
        field.getSubFields().clear();
        field.getSubFields().add(new SubField('a', "Julemandens Nisseslagteri"));
        record.getFields().add(field);

        when(rawRepo.recordExists(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecord(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(currentRecord, MarcXChangeMimeType.MARCXCHANGE));

        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(true);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(true);

        Map<String, MarcRecord> result = new HashMap<>();
        result.put(currentReader.getRecordId(), currentRecord);
        when(rawRepo.fetchRecordCollection(currentReader.getRecordId(), currentReader.getAgencyIdAsInt())).thenReturn(result);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, ResourceBundles.getBundle("actions"));

        try {
            instance.extensionRecordDataForRawRepo(record, groupId);
            fail(); // To make sure we never hit this branch
        } catch (UpdateException ex) {
            assertThat(ex.getMessage(), is(resourceBundle.getString("update.dbc.record.dbc.subjects")));
        }
    }

    @Test
    void testNoteAndSubjectFields_ExistingNote() throws Exception {
        String groupId = "730010";

        MarcRecord currentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        MarcRecordReader currentReader = new MarcRecordReader(currentRecord);
        DataField currentNoteField = new DataField("666", "00");
        currentNoteField.getSubFields().add(new SubField('&', "723456"));
        currentNoteField.getSubFields().add(new SubField('a', "Julemandens Nisseslagteri"));
        currentRecord.getFields().add(currentNoteField);

        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        DataField field = new DataField("530", "00");
        field.getSubFields().clear();
        field.getSubFields().add(new SubField('a', "Julemandens Nisseslagteri"));
        record.getFields().add(field);

        MarcRecord expectedRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        DataField expectedField = new DataField("530", "00");
        expectedField.getSubFields().add(new SubField('&', groupId));
        expectedField.getSubFields().add(new SubField('a', "Julemandens Nisseslagteri"));
        expectedRecord.getFields().add(expectedField);
        new MarcRecordWriter(expectedRecord).sort();

        when(rawRepo.recordExists(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecord(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(currentRecord, MarcXChangeMimeType.MARCXCHANGE));

        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(true);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(true);

        Map<String, MarcRecord> result = new HashMap<>();
        result.put(currentReader.getRecordId(), currentRecord);
        when(rawRepo.fetchRecordCollection(currentReader.getRecordId(), currentReader.getAgencyIdAsInt())).thenReturn(result);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, ResourceBundles.getBundle("actions"));

        assertThat(instance.extensionRecordDataForRawRepo(record, groupId), is(expectedRecord));
    }

    /**
     * current record are added a field 666 with *& containing the value 1 making the field a dbc field
     * Then we make an incoming record with no 666 field
     * The test fails due to the missing subject field
     *
     * @throws Exception happens when the extensionRecordDataForRawRepo for some reason doesn't fail
     */
    @Test
    void testNoteAndSubjectFields_ExistingSubject_Rejected() throws Exception {
        String groupId = "730010";

        MarcRecord currentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        MarcRecordReader currentReader = new MarcRecordReader(currentRecord);
        DataField currentNoteField = new DataField("666", "00");
        currentNoteField.getSubFields().add(new SubField('&', "1"));
        currentNoteField.getSubFields().add(new SubField('a', "Julemandens Nisseslagteri"));
        currentRecord.getFields().add(currentNoteField);

        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);

        when(rawRepo.recordExists(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecord(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(currentRecord, MarcXChangeMimeType.MARCXCHANGE));

        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(true);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(true);

        Map<String, MarcRecord> result = new HashMap<>();
        result.put(currentReader.getRecordId(), currentRecord);
        when(rawRepo.fetchRecordCollection(currentReader.getRecordId(), currentReader.getAgencyIdAsInt())).thenReturn(result);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, ResourceBundles.getBundle("actions"));

        try {
            instance.extensionRecordDataForRawRepo(record, groupId);
            fail(); // To make sure we never hit this branch
        } catch (UpdateException ex) {
            assertThat(ex.getMessage(), is(resourceBundle.getString("update.dbc.record.dbc.subjects")));
        }
    }

    @Test
    void testNoteAndSubjectFields_AddNote() throws Exception {
        String groupId = "730010";

        MarcRecord currentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        MarcRecordReader currentReader = new MarcRecordReader(currentRecord);
        DataField currentNoteField = new DataField("666", "00");
        currentNoteField.getSubFields().add(new SubField('&', "723456"));
        currentNoteField.getSubFields().add(new SubField('a', "Julemandens Nisseslagteri"));
        currentRecord.getFields().add(currentNoteField);

        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        DataField field530 = new DataField("530", "00");
        field530.getSubFields().clear();
        field530.getSubFields().add(new SubField('a', "Julemandens Nisseslagteri"));
        record.getFields().add(field530);
        record.getFields().add(currentNoteField);

        MarcRecord expectedRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        DataField expectedField530 = new DataField("530", "00");
        expectedField530.getSubFields().add(new SubField('&', groupId));
        expectedField530.getSubFields().add(new SubField('a', "Julemandens Nisseslagteri"));
        expectedRecord.getFields().add(expectedField530);
        DataField expectedField666 = new DataField("666", "00");
        expectedField666.getSubFields().add(new SubField('&', "723456"));
        expectedField666.getSubFields().add(new SubField('a', "Julemandens Nisseslagteri"));
        expectedRecord.getFields().add(expectedField666);
        new MarcRecordWriter(expectedRecord).sort();

        when(rawRepo.recordExists(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecord(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(currentRecord, MarcXChangeMimeType.MARCXCHANGE));

        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(true);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(true);

        Map<String, MarcRecord> result = new HashMap<>();
        result.put(currentReader.getRecordId(), currentRecord);
        when(rawRepo.fetchRecordCollection(currentReader.getRecordId(), currentReader.getAgencyIdAsInt())).thenReturn(result);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, ResourceBundles.getBundle("actions"));

        assertThat(instance.extensionRecordDataForRawRepo(record, groupId), is(expectedRecord));
    }

    @Test
    void testAuthenticateCommonRecordExtraFields_AllowedNote_AllowedSubject_AddSubjectField() throws Exception {
        final String groupId = "830010";

        final MarcRecord currentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordReader currentReader = new MarcRecordReader(currentRecord);

        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordWriter recordWriter = new MarcRecordWriter(record);
        recordWriter.addFieldSubfield("666", 'a', "a subject");
        final Map<String, MarcRecord> curRecordCollection = new HashMap<>();
        curRecordCollection.put(currentReader.getRecordId(), currentRecord);

        when(rawRepo.recordExists(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecordCollection(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(curRecordCollection);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(true);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(true);
        when(vipCoreService.getLibraryGroup(groupId)).thenReturn(LibraryGroup.FBS);

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, ResourceBundles.getBundle("actions"));
        final List<MessageEntryDTO> expected = new ArrayList<>();
        final List<MessageEntryDTO> actual = instance.authenticateCommonRecordExtraFields(record, groupId);

        assertThat(actual, is(expected));
    }

    @Test
    void testAuthenticateCommonRecordExtraFields_AllowedNote_AllowedSubject_AddNoteField_AddSubjectField() throws Exception {
        final String groupId = "830010";

        final MarcRecord currentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordReader currentReader = new MarcRecordReader(currentRecord);

        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordWriter recordWriter = new MarcRecordWriter(record);
        recordWriter.addFieldSubfield("530", 'a', "a Note");
        recordWriter.addFieldSubfield("666", 'a', "a subject");
        final Map<String, MarcRecord> curRecordCollection = new HashMap<>();
        curRecordCollection.put(currentReader.getRecordId(), currentRecord);

        when(rawRepo.recordExists(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecordCollection(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(curRecordCollection);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(true);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(true);
        when(vipCoreService.getLibraryGroup(groupId)).thenReturn(LibraryGroup.FBS);

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, ResourceBundles.getBundle("actions"));
        final List<MessageEntryDTO> expected = new ArrayList<>();
        final List<MessageEntryDTO> actual = instance.authenticateCommonRecordExtraFields(record, groupId);

        assertThat(actual, is(expected));
    }

    @Test
    void testLibraryCreateNewCatalogCodeNotCbAndNotAuthRoot() throws Exception {
        final String groupId = "830010";
        when(vipCoreService.isAuthRootOrCB(groupId)).thenReturn(false);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(true);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(true);
        when(vipCoreService.getLibraryGroup(groupId)).thenReturn(LibraryGroup.FBS);
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.OVE_RECORD);
        final MarcRecordReader currentReader = new MarcRecordReader(record);
        final Map<String, MarcRecord> curRecordCollection = new HashMap<>();
        curRecordCollection.put(currentReader.getRecordId(), record);
        when(rawRepo.recordExists(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(false);
        when(rawRepo.fetchRecordCollection(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(curRecordCollection);

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, ResourceBundles.getBundle("actions"));
        // final MarcRecord actual = instance.extensionRecordDataForRawRepo(record, groupId);

        try {
            instance.extensionRecordDataForRawRepo(record, groupId);
            fail();
        } catch (UpdateException ex) {
            assertThat(ex.getMessage(), is(resourceBundle.getString("update.library.record.catalog.codes.not.cb")));
        }
    }

    @Test
    void testLibraryCreateNewCatalogCode() throws Exception {
        final String groupId = "830010";
        when(vipCoreService.isAuthRootOrCB(groupId)).thenReturn(true);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(true);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(true);
        when(vipCoreService.getLibraryGroup(groupId)).thenReturn(LibraryGroup.FBS);
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.OVE_RECORD);
        final MarcRecord result = new MarcRecord(record);
        final MarcRecordReader currentReader = new MarcRecordReader(record);
        final MarcRecordWriter resultWriter = new MarcRecordWriter(result);
        final Map<String, MarcRecord> curRecordCollection = new HashMap<>();
        curRecordCollection.put(currentReader.getRecordId(), record);
        when(rawRepo.recordExists(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(false);
        when(rawRepo.fetchRecordCollection(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(curRecordCollection);
        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, ResourceBundles.getBundle("actions"));
        final MarcRecord actual = instance.extensionRecordDataForRawRepo(record, groupId);
        resultWriter.removeField("032");
        DataField addField = new DataField("032", "00");
        addField.getSubFields().add(new SubField('&', groupId));
        addField.getSubFields().add(new SubField('x', "OVE199746"));
        result.getFields().add(addField);
        assertThat(actual, is(result));
    }

    @Test
    void testCreateCatalogField() throws Exception {
        final String groupId = "830010";
        when(vipCoreService.isAuthRootOrCB(groupId)).thenReturn(true);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(true);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(true);
        when(vipCoreService.getLibraryGroup(groupId)).thenReturn(LibraryGroup.FBS);

        final MarcRecord currentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordReader currentReader = new MarcRecordReader(currentRecord);
        final Map<String, MarcRecord> curRecordCollection = new HashMap<>();
        curRecordCollection.put(currentReader.getRecordId(), currentRecord);
        when(rawRepo.recordExists(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecordCollection(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(curRecordCollection);
    }

    // Ok, hvad er det egentlig vi vil teste ?
    // hvad fås hvis der ikke er et 032 i den nye post
    // hvad fås hvis der ikke er et 032 i den gamle post
    // har den nye post andet end OVE kode i sig ?
    // og hvis den ikke har ?
    // hvad fås hvis gammel og ny er ens bortset fra en ny

    @Test
    void testCompareCatalogSubfields() {

        // Verifies that the content is the same despite order and existence of *& and OVE code
        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, ResourceBundles.getBundle("actions"));
        DataField m1 = new DataField();
        m1.setTag("032");
        DataField m2 = new DataField();
        m2.setTag("032");
        SubField s1 = new SubField('a', "tekst1");
        SubField s2 = new SubField('b', "tekst2");
        SubField s3 = new SubField('c', "tekst3");
        SubField s4 = new SubField('&', "875100");
        SubField s5 = new SubField('x', "OVE202218");
        m1.getSubFields().add(s1);
        m1.getSubFields().add(s2);
        m1.getSubFields().add(s3);
        assertThat(instance.compareCatalogSubFields(m1, m2, true), is(false));
        m2.getSubFields().add(s1);
        m2.getSubFields().add(s3);
        m2.getSubFields().add(s2);
        assertThat(instance.compareCatalogSubFields(m1, m2, true), is(true));
        assertThat(instance.compareCatalogSubFields(m1, m2, false), is(true));
        m2.getSubFields().add(s4);
        m2.getSubFields().add(s5);
        assertThat(instance.compareCatalogSubFields(m1, m2, true), is(false));
        assertThat(instance.compareCatalogSubFields(m1, m2, false), is(true));

    }

    @Test
    void testAuthenticateCommonRecordExtraFields_AllowedNote_NotAllowedSubject_AddNoteField() throws Exception {
        final String groupId = "830010";

        final MarcRecord currentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordReader currentReader = new MarcRecordReader(currentRecord);

        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordWriter recordWriter = new MarcRecordWriter(record);
        recordWriter.addFieldSubfield("530", 'a', "a Note");
        final Map<String, MarcRecord> curRecordCollection = new HashMap<>();
        curRecordCollection.put(currentReader.getRecordId(), currentRecord);

        when(rawRepo.recordExists(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecordCollection(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(curRecordCollection);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(true);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(true);
        when(vipCoreService.getLibraryGroup(groupId)).thenReturn(LibraryGroup.FBS);

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, ResourceBundles.getBundle("actions"));
        final List<MessageEntryDTO> expected = new ArrayList<>();
        final List<MessageEntryDTO> actual = instance.authenticateCommonRecordExtraFields(record, groupId);

        assertThat(actual, is(expected));
    }

    @Test
    void testAuthenticateCommonRecordExtraFields_NotAllowedNote_AllowedSubject_AddNoteField() throws Exception {
        final String groupId = "830010";

        final MarcRecord currentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordReader currentReader = new MarcRecordReader(currentRecord);

        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordWriter recordWriter = new MarcRecordWriter(record);
        recordWriter.addFieldSubfield("530", 'a', "a Note");
        final Map<String, MarcRecord> curRecordCollection = new HashMap<>();
        curRecordCollection.put(currentReader.getRecordId(), currentRecord);

        when(rawRepo.recordExists(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecordCollection(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(curRecordCollection);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(false);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(true);
        when(vipCoreService.getLibraryGroup(groupId)).thenReturn(LibraryGroup.FBS);

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, ResourceBundles.getBundle("actions"));
        final List<MessageEntryDTO> expected = new ArrayList<>();
        final MessageEntryDTO messageEntryDTO = new MessageEntryDTO();
        messageEntryDTO.setType(TypeEnumDTO.ERROR);
        messageEntryDTO.setMessage(String.format(resourceBundleMessages.getString("notes.subjects.edit.field.error"), groupId, "530", currentReader.getRecordId()));
        expected.add(messageEntryDTO);

        final List<MessageEntryDTO> actual = instance.authenticateCommonRecordExtraFields(record, groupId);

        assertThat(actual, is(expected));
    }

    @Test
    void testAuthenticateCommonRecordExtraFields_AllowedNote_NotAllowedSubject_AddSubjectField() throws Exception {
        final String groupId = "830010";

        final MarcRecord currentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordReader currentReader = new MarcRecordReader(currentRecord);

        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordWriter recordWriter = new MarcRecordWriter(record);
        recordWriter.addFieldSubfield("666", 'a', "a subject");
        final Map<String, MarcRecord> curRecordCollection = new HashMap<>();
        curRecordCollection.put(currentReader.getRecordId(), currentRecord);

        when(rawRepo.recordExists(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecordCollection(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(curRecordCollection);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(true);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(false);
        when(vipCoreService.getLibraryGroup(groupId)).thenReturn(LibraryGroup.FBS);

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, ResourceBundles.getBundle("actions"));
        final List<MessageEntryDTO> expected = new ArrayList<>();
        final MessageEntryDTO messageEntryDTO = new MessageEntryDTO();
        messageEntryDTO.setType(TypeEnumDTO.ERROR);
        messageEntryDTO.setMessage(String.format(resourceBundleMessages.getString("notes.subjects.edit.field.error"), groupId, "666", currentReader.getRecordId()));
        expected.add(messageEntryDTO);

        final List<MessageEntryDTO> actual = instance.authenticateCommonRecordExtraFields(record, groupId);

        assertThat(actual, is(expected));
    }

    @Test
    void testAuthenticateCommonRecordExtraFields_NotAllowedNote_NotAllowedSubject_AddNoteField_AddSubjectField() throws Exception {
        final String groupId = "830010";

        final MarcRecord currentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordReader currentReader = new MarcRecordReader(currentRecord);

        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordWriter recordWriter = new MarcRecordWriter(record);
        recordWriter.addFieldSubfield("530", 'a', "a Note");
        recordWriter.addFieldSubfield("666", 'a', "a subject");
        final Map<String, MarcRecord> curRecordCollection = new HashMap<>();
        curRecordCollection.put(currentReader.getRecordId(), currentRecord);

        when(rawRepo.recordExists(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecordCollection(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(curRecordCollection);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(false);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(false);
        when(vipCoreService.getLibraryGroup(groupId)).thenReturn(LibraryGroup.FBS);

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, ResourceBundles.getBundle("actions"));

        final List<MessageEntryDTO> expected = new ArrayList<>();
        final MessageEntryDTO messageEntryDTO530 = new MessageEntryDTO();
        messageEntryDTO530.setType(TypeEnumDTO.ERROR);
        messageEntryDTO530.setMessage(String.format(resourceBundleMessages.getString("notes.subjects.edit.field.error"), groupId, "530", currentReader.getRecordId()));
        expected.add(messageEntryDTO530);
        final MessageEntryDTO messageEntryDTO666 = new MessageEntryDTO();
        messageEntryDTO666.setType(TypeEnumDTO.ERROR);
        messageEntryDTO666.setMessage(String.format(resourceBundleMessages.getString("notes.subjects.edit.field.error"), groupId, "666", currentReader.getRecordId()));
        expected.add(messageEntryDTO666);

        final List<MessageEntryDTO> actual = instance.authenticateCommonRecordExtraFields(record, groupId);

        assertThat(actual, is(expected));
    }

    @Test
    void testNewNoteAndSubjectFieldRules2022_1() throws Exception {
        final MarcRecord existingRecord = AssertActionsUtil.loadRecord("records/record-1-existing.marc");
        final MarcRecord expectedRecord = AssertActionsUtil.loadRecord("records/record-1-expected.marc");
        final MarcRecord newRecord = AssertActionsUtil.loadRecord("records/record-1-new.marc");
        final String bibliographicRecordId = "47579562";
        final String groupId = "785100";

        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(true);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(true);
        when(rawRepo.recordExists(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecord(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingRecord, MarcXChangeMimeType.MARCXCHANGE));

        Map<String, MarcRecord> result = new HashMap<>();
        result.put(bibliographicRecordId, existingRecord);
        when(rawRepo.fetchRecordCollection(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(result);

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);

        assertThat(instance.extensionRecordDataForRawRepo(newRecord, groupId), is(expectedRecord));
    }

    @Test
    void testNewNoteAndSubjectFieldRules2022_3() throws Exception {
        final MarcRecord existingRecord = AssertActionsUtil.loadRecord("records/record-3-existing.marc");
        final MarcRecord expectedRecord = AssertActionsUtil.loadRecord("records/record-3-expected.marc");
        final MarcRecord newRecord = AssertActionsUtil.loadRecord("records/record-3-new.marc");
        final String bibliographicRecordId = "29044481";
        final String groupId = "785100";

        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(true);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(true);
        when(rawRepo.recordExists(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecord(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingRecord, MarcXChangeMimeType.MARCXCHANGE));

        Map<String, MarcRecord> result = new HashMap<>();
        result.put(bibliographicRecordId, existingRecord);
        when(rawRepo.fetchRecordCollection(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(result);

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);

        assertThat(instance.extensionRecordDataForRawRepo(newRecord, groupId), is(expectedRecord));
    }

    @Test
    void testNewNoteAndSubjectFieldRules2022_4() throws Exception {
        final MarcRecord existingRecord = AssertActionsUtil.loadRecord("records/record-4-existing.marc");
        final MarcRecord expectedRecord = AssertActionsUtil.loadRecord("records/record-4-expected.marc");
        final MarcRecord newRecord = AssertActionsUtil.loadRecord("records/record-4-new.marc");
        final MarcRecord volumeRecord = AssertActionsUtil.loadRecord("records/record-4-volume.marc");
        final String bibliographicRecordId = "47481899";
        final String bibliographicRecordIdVolume = "47425042";
        final String groupId = "785100";
        final RecordId volumeRecordId = new RecordId(bibliographicRecordIdVolume, RawRepo.COMMON_AGENCY);

        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(true);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(true);
        when(rawRepo.recordExists(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecord(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(rawRepo.children(new RecordId(bibliographicRecordId, RawRepo.COMMON_AGENCY))).thenReturn(new HashSet<>(List.of(volumeRecordId)));
        when(rawRepo.fetchRecord(bibliographicRecordIdVolume, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(volumeRecord, MarcXChangeMimeType.MARCXCHANGE));

        Map<String, MarcRecord> result = new HashMap<>();
        result.put(bibliographicRecordId, existingRecord);
        when(rawRepo.fetchRecordCollection(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(result);

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);

        assertThat(instance.extensionRecordDataForRawRepo(newRecord, groupId), is(expectedRecord));
    }

    @Test
    void testNewNoteAndSubjectFieldRules2022_5() throws Exception {
        final MarcRecord existingRecord = AssertActionsUtil.loadRecord("records/record-5-existing.marc");
        final MarcRecord expectedRecord = AssertActionsUtil.loadRecord("records/record-5-expected.marc");
        final MarcRecord newRecord = AssertActionsUtil.loadRecord("records/record-5-new.marc");
        final String bibliographicRecordId = "47425042";
        final String groupId = "785100";

        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(true);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(true);
        when(rawRepo.recordExists(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecord(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingRecord, MarcXChangeMimeType.MARCXCHANGE));

        Map<String, MarcRecord> result = new HashMap<>();
        result.put(bibliographicRecordId, existingRecord);
        when(rawRepo.fetchRecordCollection(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(result);

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);

        assertThat(instance.extensionRecordDataForRawRepo(newRecord, groupId), is(expectedRecord));
    }

    @Test
    void testNewNoteAndSubjectFieldRules2022_6() throws Exception {
        final MarcRecord existingRecord = AssertActionsUtil.loadRecord("records/record-6-existing.marc");
        final MarcRecord newRecord1 = AssertActionsUtil.loadRecord("records/record-6-new-1.marc");
        final String bibliographicRecordId = "48349935";
        final String groupId = "785100";

        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(true);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(true);
        when(rawRepo.recordExists(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecord(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingRecord, MarcXChangeMimeType.MARCXCHANGE));

        Map<String, MarcRecord> result = new HashMap<>();
        result.put(bibliographicRecordId, existingRecord);
        when(rawRepo.fetchRecordCollection(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(result);

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);

        try {
            instance.extensionRecordDataForRawRepo(newRecord1, groupId);
            fail();
        } catch (UpdateException ex) {
            assertThat(ex.getMessage(), is(String.format(resourceBundle.getString("update.dbc.record.dbc.notes"), "504")));
        }

        final MarcRecord expectedRecord = AssertActionsUtil.loadRecord("records/record-6-expected.marc");
        final MarcRecord newRecord2 = AssertActionsUtil.loadRecord("records/record-6-new-2.marc");

        assertThat(instance.extensionRecordDataForRawRepo(newRecord2, groupId), is(expectedRecord));
    }

    @Test
    void testNewNoteAndSubjectFieldRules2022_7() throws Exception {
        final MarcRecord existingRecord = AssertActionsUtil.loadRecord("records/record-7-existing.marc");
        final MarcRecord expectedRecord = AssertActionsUtil.loadRecord("records/record-7-expected.marc");
        final MarcRecord newRecord = AssertActionsUtil.loadRecord("records/record-7-new.marc");
        final String bibliographicRecordId = "05415292";
        final String groupId = "785100";

        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(true);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(true);
        when(rawRepo.recordExists(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecord(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingRecord, MarcXChangeMimeType.MARCXCHANGE));

        Map<String, MarcRecord> result = new HashMap<>();
        result.put(bibliographicRecordId, existingRecord);
        when(rawRepo.fetchRecordCollection(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(result);

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);

        assertThat(instance.extensionRecordDataForRawRepo(newRecord, groupId), is(expectedRecord));
    }

    @Test
    void testNewNoteAndSubjectFieldRules2022_8() throws Exception {
        final MarcRecord existingRecord = AssertActionsUtil.loadRecord("records/record-8-existing.marc");
        final MarcRecord expectedRecord = AssertActionsUtil.loadRecord("records/record-8-expected.marc");
        final MarcRecord newRecord = AssertActionsUtil.loadRecord("records/record-8-new.marc");
        final String bibliographicRecordId = "05415292";
        final String groupId = "710100";

        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(true);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(true);
        when(rawRepo.recordExists(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecord(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingRecord, MarcXChangeMimeType.MARCXCHANGE));

        Map<String, MarcRecord> result = new HashMap<>();
        result.put(bibliographicRecordId, existingRecord);
        when(rawRepo.fetchRecordCollection(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(result);

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);

        assertThat(instance.extensionRecordDataForRawRepo(newRecord, groupId), is(expectedRecord));
    }

    @Test
    void testNewNoteAndSubjectFieldRules2022_10() throws Exception {
        final MarcRecord existingRecord = AssertActionsUtil.loadRecord("records/record-10-existing.marc");
        final MarcRecord expectedRecord = AssertActionsUtil.loadRecord("records/record-10-expected.marc");
        final MarcRecord newRecord = AssertActionsUtil.loadRecord("records/record-10-new.marc");
        final String bibliographicRecordId = "29044481";
        final String groupId = "785100";

        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(true);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(true);
        when(rawRepo.recordExists(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecord(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingRecord, MarcXChangeMimeType.MARCXCHANGE));

        Map<String, MarcRecord> result = new HashMap<>();
        result.put(bibliographicRecordId, existingRecord);
        when(rawRepo.fetchRecordCollection(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(result);

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);

        assertThat(instance.extensionRecordDataForRawRepo(newRecord, groupId), is(expectedRecord));
    }

    @Test
    void testNewNoteAndSubjectFieldRules2022_11() throws Exception {
        final MarcRecord existingRecord = AssertActionsUtil.loadRecord("records/record-11-existing.marc");
        final MarcRecord expectedRecord = AssertActionsUtil.loadRecord("records/record-11-expected.marc");
        final MarcRecord newRecord = AssertActionsUtil.loadRecord("records/record-11-new.marc");
        final MarcRecord volumeRecord = AssertActionsUtil.loadRecord("records/record-11-volume.marc");
        final String volumeBibliographicRecordId = "47425042";
        final String bibliographicRecordId = "47481899";
        final String groupId = "785100";
        final RecordId volumeRecordId = new RecordId(volumeBibliographicRecordId, RawRepo.COMMON_AGENCY);

        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(true);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(true);
        when(rawRepo.recordExists(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecord(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(rawRepo.children(new RecordId(bibliographicRecordId, RawRepo.COMMON_AGENCY))).thenReturn(new HashSet<>(List.of(volumeRecordId)));
        when(rawRepo.fetchRecord(volumeBibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(volumeRecord, MarcXChangeMimeType.MARCXCHANGE));

        Map<String, MarcRecord> result = new HashMap<>();
        result.put(bibliographicRecordId, existingRecord);
        when(rawRepo.fetchRecordCollection(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(result);

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);

        assertThat(instance.extensionRecordDataForRawRepo(newRecord, groupId), is(expectedRecord));
    }

    @Test
    void testNewNoteAndSubjectFieldRules2022_12() throws Exception {
        final MarcRecord existingRecord = AssertActionsUtil.loadRecord("records/record-12-existing.marc");
        final MarcRecord expectedRecord = AssertActionsUtil.loadRecord("records/record-12-expected.marc");
        final MarcRecord newRecord = AssertActionsUtil.loadRecord("records/record-12-new.marc");
        final String bibliographicRecordId = "47425042";
        final String groupId = "785100";

        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(true);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(true);
        when(rawRepo.recordExists(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecord(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingRecord, MarcXChangeMimeType.MARCXCHANGE));

        Map<String, MarcRecord> result = new HashMap<>();
        result.put(bibliographicRecordId, existingRecord);
        when(rawRepo.fetchRecordCollection(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(result);

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);

        assertThat(instance.extensionRecordDataForRawRepo(newRecord, groupId), is(expectedRecord));
    }

    @Test
    void testNewNoteAndSubjectFieldRules2022_51256875() throws Exception {
        final MarcRecord existingRecord = AssertActionsUtil.loadRecord("records/record-51256875-existing.marc");
        final MarcRecord expectedRecord = AssertActionsUtil.loadRecord("records/record-51256875-expected.marc");
        final MarcRecord newRecord = AssertActionsUtil.loadRecord("records/record-51256875-new.marc");
        final String bibliographicRecordId = "51256875";
        final String groupId = "773000";

        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(true);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(true);
        when(rawRepo.recordExists(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecord(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingRecord, MarcXChangeMimeType.MARCXCHANGE));

        Map<String, MarcRecord> result = new HashMap<>();
        result.put(bibliographicRecordId, existingRecord);
        when(rawRepo.fetchRecordCollection(bibliographicRecordId, RawRepo.COMMON_AGENCY)).thenReturn(result);

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);

        assertThat(instance.extensionRecordDataForRawRepo(newRecord, groupId), is(expectedRecord));
    }

    private MarcRecord sortRecord(MarcRecord record) {
        record.getFields().sort(Comparator.comparing(Field::getTag));

        return record;
    }

}
