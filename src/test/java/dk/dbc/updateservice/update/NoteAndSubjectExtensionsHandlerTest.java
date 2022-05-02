package dk.dbc.updateservice.update;

import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.common.records.MarcSubField;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

class NoteAndSubjectExtensionsHandlerTest {
    private static final ResourceBundle resourceBundle = ResourceBundles.getBundle("actions");

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

    protected static class TestSet {
        final MarcRecord inputRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_RECORD_CLASSIFICATION);
        final MarcRecord commonRec = new MarcRecord(inputRecord);
        final MarcRecordWriter inputWriter = new MarcRecordWriter(inputRecord);
        final MarcRecordReader inputReader = new MarcRecordReader(inputRecord);
        final MarcRecordWriter commonRecWriter = new MarcRecordWriter(commonRec);
        final MarcRecordReader reader = new MarcRecordReader(inputRecord);

        TestSet() throws IOException {
        }
    }

    // These tests are not perfect , and the actual testing of output is being done via ocb-tools, as we cannot test output here.
    @Test
    void checkForAlteredClassificationForDisputas_test_correct_record() throws Exception {
        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);
        TestSet testSet = new TestSet();

        testSet.inputWriter.addFieldSubfield("652", "m", "klassemærke1");
        testSet.commonRecWriter.addFieldSubfield("652", "m", "UdeN klAssemærke");

        when(rawRepo.recordExists(testSet.reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecord(testSet.reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(testSet.commonRec, MarcXChangeMimeType.MARCXCHANGE));

        assertThat(instance.canChangeClassificationForDisputas(testSet.inputReader), is(true));
    }

    @Test
    void checkForAlteredClassificationForDisputas_test_wrong_current_652() throws Exception {
        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);
        TestSet testSet = new TestSet();

        testSet.inputWriter.addFieldSubfield("652", "m", "klassemærke1");
        testSet.inputWriter.addFieldSubfield("652", "m", "klassemærke2");
        testSet.commonRecWriter.addFieldSubfield("652", "m", "med klassemærke");

        when(rawRepo.recordExists(testSet.reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecord(testSet.reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(testSet.commonRec, MarcXChangeMimeType.MARCXCHANGE));

        assertThat(instance.canChangeClassificationForDisputas(testSet.inputReader), is(false));
    }

    @Test
    void checkForAlteredClassificationForDisputas_test_wrong_materialType() throws Exception {
        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);
        TestSet testSet = new TestSet();

        testSet.inputWriter.addFieldSubfield("652", "m", "klassemærke1");
        testSet.commonRecWriter.addOrReplaceSubfield("008", "d", "l");
        testSet.commonRecWriter.addFieldSubfield("652", "m", "uden klassemærke");

        when(rawRepo.recordExists(testSet.reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecord(testSet.reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(testSet.commonRec, MarcXChangeMimeType.MARCXCHANGE));

        assertThat(instance.canChangeClassificationForDisputas(testSet.inputReader), is(false));
    }

    @Test
    void checkForAlteredClassificationForDisputas_test_no_652() throws Exception {
        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);
        TestSet testSet = new TestSet();

        testSet.inputWriter.addFieldSubfield("652", "m", "uden klassemærke");
        testSet.commonRecWriter.addOrReplaceSubfield("008", "d", "l");
        testSet.commonRecWriter.addFieldSubfield("652", "m", "uden klassemærke");

        when(rawRepo.recordExists(testSet.reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecord(testSet.reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(testSet.commonRec, MarcXChangeMimeType.MARCXCHANGE));

        assertThat(instance.canChangeClassificationForDisputas(testSet.inputReader), is(true));
    }

    @Test
    void checkForAlteredClassificationForDisputas_test_equal_652() throws Exception {
        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);
        TestSet testSet = new TestSet();

        testSet.inputWriter.addFieldSubfield("652", "m", "uden klassemærke");
        testSet.commonRecWriter.addFieldSubfield("652", "m", "uden klassemærke");

        when(rawRepo.recordExists(testSet.reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecord(testSet.reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(testSet.commonRec, MarcXChangeMimeType.MARCXCHANGE));

        assertThat(instance.canChangeClassificationForDisputas(testSet.inputReader), is(true));
    }

    @Test
    void testisNationalCommonRecord_OK() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.addOrReplaceSubfield("032", "a", "DBF12345");
        writer.addOrReplaceSubfield("032", "x", "SFU12345");

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
        MarcField field = new MarcField("001", "00");
        field.getSubfields().add(new MarcSubField("a", "111111111"));
        field.getSubfields().add(new MarcSubField("b", "870970"));
        field.getSubfields().add(new MarcSubField("c", "19971020"));
        field.getSubfields().add(new MarcSubField("d", "19940516"));
        field.getSubfields().add(new MarcSubField("f", "a"));

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);
        assertThat(instance.isFieldChangedInOtherRecord(field, record), is(true));
    }

    @Test
    void testisFieldChangedInOtherRecord_001_nomatch() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);

        // 001 00 *a 20611529 *b 870970 *c 19971020 *d 19940516 *f a
        MarcField field = new MarcField("001", "00");
        field.getSubfields().add(new MarcSubField("a", "20611529"));
        field.getSubfields().add(new MarcSubField("b", "870970"));
        field.getSubfields().add(new MarcSubField("c", "19971020"));
        field.getSubfields().add(new MarcSubField("d", "19940516"));
        field.getSubfields().add(new MarcSubField("f", "a"));

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);
        assertThat(instance.isFieldChangedInOtherRecord(field, record), is(false));
    }

    @Test
    void testisFieldChangedInOtherRecord_001_missing001cd_match() throws Exception {
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

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);
        assertThat(instance.isFieldChangedInOtherRecord(field, record), is(true));
    }

    @Test
    void testisFieldChangedInOtherRecord_Field900_withxwz_NoChange_1() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.EXPANDED_VOLUME);

        final MarcField field = new MarcField("900", "00");
        field.getSubfields().add(new MarcSubField("a", "Møllgaard"));
        field.getSubfields().add(new MarcSubField("h", "H. Peter"));
        field.getSubfields().add(new MarcSubField("x", "se"));
        field.getSubfields().add(new MarcSubField("w", "Møllgaard, Peter (f. 1964-02-23)"));
        field.getSubfields().add(new MarcSubField("z", "700/1"));

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);
        assertThat(instance.isFieldChangedInOtherRecord(field, record), is(false));
    }

    @Test
    void testisFieldChangedInOtherRecord_Field900_Withxwz_NoChange_2() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.EXPANDED_VOLUME);

        final MarcField field = new MarcField("900", "00");
        field.getSubfields().add(new MarcSubField("a", "Kastberg"));
        field.getSubfields().add(new MarcSubField("h", "Claus"));
        field.getSubfields().add(new MarcSubField("x", "se også under det senere navn"));
        field.getSubfields().add(new MarcSubField("w", "Kastberg Nielsen, Claus"));
        field.getSubfields().add(new MarcSubField("z", "700/2"));

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);
        assertThat(instance.isFieldChangedInOtherRecord(field, record), is(false));
    }

    @Test
    void testisFieldChangedInOtherRecord_Field900_Withoutxwz_NoChange() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.EXPANDED_VOLUME);

        final MarcField field = new MarcField("900", "00");
        field.getSubfields().add(new MarcSubField("a", "Møllgaard"));
        field.getSubfields().add(new MarcSubField("h", "H. Peter"));

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);
        assertThat(instance.isFieldChangedInOtherRecord(field, record), is(false));
    }

    @Test
    void testisFieldChangedInOtherRecord_Field900_Withoutxwz_Changed() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.EXPANDED_VOLUME);

        final MarcField field = new MarcField("900", "00");
        field.getSubfields().add(new MarcSubField("a", "Møllegaard")); // Added 'e'
        field.getSubfields().add(new MarcSubField("h", "H. Peter"));

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

        assertThat(instance.recordDataForRawRepo(record, groupId), is(record));
    }

    @Test
    void testrecordDataForRawRepo_isNotNationalCommonRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        String groupId = "870970";

        when(rawRepo.recordExists(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecord(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);

        assertThat(instance.recordDataForRawRepo(record, groupId), is(record));
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
        recordWriter.addOrReplaceSubfield("504", "&", "1");
        recordWriter.addOrReplaceSubfield("504", "a", "Julemandens Nisseslagteri");

        MarcRecord currentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter currentWriter = new MarcRecordWriter(currentRecord);
        currentWriter.removeField("100");
        currentWriter.addOrReplaceSubfield("100", "5", "12345678");
        currentWriter.addOrReplaceSubfield("100", "6", "876543");

        MarcRecord expected = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter expectedWriter = new MarcRecordWriter(expected);
        expectedWriter.removeField("100");
        expectedWriter.addOrReplaceSubfield("100", "5", "12345678");
        expectedWriter.addOrReplaceSubfield("100", "6", "876543");
        expectedWriter.removeField("504");
        expectedWriter.addOrReplaceSubfield("504", "&", "1");
        expectedWriter.addOrReplaceSubfield("504", "a", "Julemandens Nisseslagteri");

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
        writer.addOrReplaceSubfield("032", "a", "DBF12345");
        writer.addOrReplaceSubfield("032", "x", "ACC12345");

        MarcRecord existingRecord = new MarcRecord(record);

        when(rawRepo.recordExists(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecord(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingRecord, MarcXChangeMimeType.MARCXCHANGE));

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);
        assertThat(instance.recordDataForRawRepo(record, "820010"), is(record));
    }

    @Test
    void testNoteAndSubjectFields_DBCNote() throws Exception {
        String groupId = "830010";

        MarcRecord currentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        MarcRecordReader currentReader = new MarcRecordReader(currentRecord);
        MarcField currentNoteField = new MarcField("666", "00");
        currentNoteField.getSubfields().add(new MarcSubField("&", "1"));
        currentNoteField.getSubfields().add(new MarcSubField("a", "Julemandens Nisseslagteri"));
        currentRecord.getFields().add(currentNoteField);

        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        MarcField field = new MarcField("530", "00");
        field.setSubfields(Collections.singletonList(new MarcSubField("a", "Julemandens Nisseslagteri")));
        record.getFields().add(field);

        when(rawRepo.recordExists(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecord(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(currentRecord, MarcXChangeMimeType.MARCXCHANGE));

        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(true);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(true);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, ResourceBundles.getBundle("actions"));

        try {
            instance.recordDataForRawRepo(record, groupId);
            fail(); // To make sure we never hit this branch
        } catch (UpdateException ex) {
            assertThat(ex.getMessage(), is("Posten må ikke beriges, da den i forvejen er emnebehandlet"));
        }
    }

    @Test
    void testNoteAndSubjectFields_ExistingNote() throws Exception {
        String groupId = "730010";

        MarcRecord currentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        MarcRecordReader currentReader = new MarcRecordReader(currentRecord);
        MarcField currentNoteField = new MarcField("666", "00");
        currentNoteField.getSubfields().add(new MarcSubField("&", "723456"));
        currentNoteField.getSubfields().add(new MarcSubField("a", "Julemandens Nisseslagteri"));
        currentRecord.getFields().add(currentNoteField);

        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        MarcField field = new MarcField("530", "00");
        field.setSubfields(Collections.singletonList(new MarcSubField("a", "Julemandens Nisseslagteri")));
        record.getFields().add(field);

        MarcRecord expectedRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        MarcField expectedField = new MarcField("530", "00");
        expectedField.getSubfields().add(new MarcSubField("&", groupId));
        expectedField.getSubfields().add(new MarcSubField("a", "Julemandens Nisseslagteri"));
        expectedRecord.getFields().add(expectedField);
        new MarcRecordWriter(expectedRecord).sort();

        when(rawRepo.recordExists(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecord(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(currentRecord, MarcXChangeMimeType.MARCXCHANGE));

        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(true);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(true);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, ResourceBundles.getBundle("actions"));

        assertThat(instance.recordDataForRawRepo(record, groupId), is(expectedRecord));
    }

    @Test
    void testNoteAndSubjectFields_ExistingNote_Rejected() throws Exception {
        String groupId = "730010";

        MarcRecord currentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        MarcRecordReader currentReader = new MarcRecordReader(currentRecord);
        MarcField currentNoteField = new MarcField("666", "00");
        currentNoteField.getSubfields().add(new MarcSubField("&", "123456"));
        currentNoteField.getSubfields().add(new MarcSubField("a", "Julemandens Nisseslagteri"));
        currentRecord.getFields().add(currentNoteField);

        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        MarcField field = new MarcField("530", "00");
        field.setSubfields(Collections.singletonList(new MarcSubField("a", "Julemandens Nisseslagteri")));
        record.getFields().add(field);

        when(rawRepo.recordExists(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecord(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(currentRecord, MarcXChangeMimeType.MARCXCHANGE));

        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(true);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(true);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, ResourceBundles.getBundle("actions"));

        try {
            instance.recordDataForRawRepo(record, groupId);
            fail(); // To make sure we never hit this branch
        } catch (UpdateException ex) {
            assertThat(ex.getMessage(), is("Posten må ikke beriges, da den i forvejen er emnebehandlet"));
        }
    }

    @Test
    void testNoteAndSubjectFields_AddNote() throws Exception {
        String groupId = "730010";

        MarcRecord currentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        MarcRecordReader currentReader = new MarcRecordReader(currentRecord);
        MarcField currentNoteField = new MarcField("666", "00");
        currentNoteField.getSubfields().add(new MarcSubField("&", "723456"));
        currentNoteField.getSubfields().add(new MarcSubField("a", "Julemandens Nisseslagteri"));
        currentRecord.getFields().add(currentNoteField);

        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        MarcField field530 = new MarcField("530", "00");
        field530.setSubfields(Collections.singletonList(new MarcSubField("a", "Julemandens Nisseslagteri")));
        record.getFields().add(field530);
        record.getFields().add(currentNoteField);

        MarcRecord expectedRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        MarcField expectedField530 = new MarcField("530", "00");
        expectedField530.getSubfields().add(new MarcSubField("&", groupId));
        expectedField530.getSubfields().add(new MarcSubField("a", "Julemandens Nisseslagteri"));
        expectedRecord.getFields().add(expectedField530);
        MarcField expectedField666 = new MarcField("666", "00");
        expectedField666.getSubfields().add(new MarcSubField("&", "723456"));
        expectedField666.getSubfields().add(new MarcSubField("a", "Julemandens Nisseslagteri"));
        expectedRecord.getFields().add(expectedField666);
        new MarcRecordWriter(expectedRecord).sort();

        when(rawRepo.recordExists(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(rawRepo.fetchRecord(currentReader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(currentRecord, MarcXChangeMimeType.MARCXCHANGE));

        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)).thenReturn(true);
        when(vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)).thenReturn(true);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, ResourceBundles.getBundle("actions"));

        assertThat(instance.recordDataForRawRepo(record, groupId), is(expectedRecord));
    }

    @Test
    void testAuthenticateCommonRecordExtraFields_AllowedNote_AllowedSubject_AddSubjectField() throws Exception {
        final String groupId = "830010";

        final MarcRecord currentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordReader currentReader = new MarcRecordReader(currentRecord);

        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordWriter recordWriter = new MarcRecordWriter(record);
        recordWriter.addFieldSubfield("666", "a", "a subject");
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
        recordWriter.addFieldSubfield("530", "a", "a Note");
        recordWriter.addFieldSubfield("666", "a", "a subject");
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
    void testAuthenticateCommonRecordExtraFields_AllowedNote_NotAllowedSubject_AddNoteField() throws Exception {
        final String groupId = "830010";

        final MarcRecord currentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordReader currentReader = new MarcRecordReader(currentRecord);

        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordWriter recordWriter = new MarcRecordWriter(record);
        recordWriter.addFieldSubfield("530", "a", "a Note");
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
        recordWriter.addFieldSubfield("530", "a", "a Note");
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
        messageEntryDTO.setMessage("Brugeren '830010' har ikke ret til at rette/tilføje feltet '530' i posten '20611529'");
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
        recordWriter.addFieldSubfield("666", "a", "a subject");
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
        messageEntryDTO.setMessage("Brugeren '830010' har ikke ret til at rette/tilføje feltet '666' i posten '20611529'");
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
        recordWriter.addFieldSubfield("530", "a", "a Note");
        recordWriter.addFieldSubfield("666", "a", "a subject");
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
        messageEntryDTO530.setMessage("Brugeren '830010' har ikke ret til at rette/tilføje feltet '530' i posten '20611529'");
        expected.add(messageEntryDTO530);
        final MessageEntryDTO messageEntryDTO666 = new MessageEntryDTO();
        messageEntryDTO666.setType(TypeEnumDTO.ERROR);
        messageEntryDTO666.setMessage("Brugeren '830010' har ikke ret til at rette/tilføje feltet '666' i posten '20611529'");
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

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);

        assertThat(instance.recordDataForRawRepo(newRecord, groupId), is(expectedRecord));
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

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);

        assertThat(instance.recordDataForRawRepo(newRecord, groupId), is(expectedRecord));
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

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);

        assertThat(instance.recordDataForRawRepo(newRecord, groupId), is(expectedRecord));
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

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);

        assertThat(instance.recordDataForRawRepo(newRecord, groupId), is(expectedRecord));
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

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);

        try {
            instance.recordDataForRawRepo(newRecord1, groupId);
            fail();
        } catch (UpdateException ex) {
            assertThat(ex.getMessage(), is("Posten må ikke beriges med felt 504, da feltet indgår i DBCs registrering"));
        }

        final MarcRecord expectedRecord = AssertActionsUtil.loadRecord("records/record-6-expected.marc");
        final MarcRecord newRecord2 = AssertActionsUtil.loadRecord("records/record-6-new-2.marc");

        assertThat(instance.recordDataForRawRepo(newRecord2, groupId), is(expectedRecord));
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

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);

        assertThat(instance.recordDataForRawRepo(newRecord, groupId), is(expectedRecord));
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

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);

        assertThat(instance.recordDataForRawRepo(newRecord, groupId), is(expectedRecord));
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

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);

        assertThat(instance.recordDataForRawRepo(newRecord, groupId), is(expectedRecord));
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

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);

        assertThat(instance.recordDataForRawRepo(newRecord, groupId), is(expectedRecord));
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

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, resourceBundle);

        assertThat(instance.recordDataForRawRepo(newRecord, groupId), is(expectedRecord));
    }

    private MarcRecord sortRecord(MarcRecord record) {
        record.getFields().sort(Comparator.comparing(MarcField::getName));

        return record;
    }

}
