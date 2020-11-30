/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;


import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.common.records.MarcSubField;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.updateservice.actions.AssertActionsUtil;
import dk.dbc.updateservice.dto.MessageEntryDTO;
import dk.dbc.updateservice.dto.TypeEnumDTO;
import dk.dbc.updateservice.utils.ResourceBundles;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class NoteAndSubjectExtentionsHanderTest {

    @Before
    public void before() throws IOException {
        MockitoAnnotations.initMocks(this);
    }

    @Mock
    private VipCoreService vipCoreService;

    @Mock
    private RawRepo rawRepo;

    @Test
    public void testIsFieldNationalCommonField_wrongField() throws Exception {
        MarcField field = new MarcField("001", "00");
        field.getSubfields().add(new MarcSubField("a", "12345678"));
        field.getSubfields().add(new MarcSubField("b", "870970"));

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, null);
        assertThat(instance.isFieldNationalCommonField(field), equalTo(false));
    }

    @Test
    public void testIsFieldNationalCommonField_wrongSubfieldValue() throws Exception {
        MarcField field = new MarcField("032", "00");
        field.getSubfields().add(new MarcSubField("x", "ABC"));

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, null);
        assertThat(instance.isFieldNationalCommonField(field), equalTo(true));
    }

    @Test
    public void testIsFieldNationalCommonField() throws Exception {
        MarcField field = new MarcField("032", "00");
        field.getSubfields().add(new MarcSubField("a", "BKM"));
        field.getSubfields().add(new MarcSubField("b", "870970"));

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, null);
        assertThat(instance.isFieldNationalCommonField(field), equalTo(false));
    }

    protected class TestSet {
        private MarcRecord inputRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_RECORD_CLASSIFICATION);
        private MarcRecord commonRec = new MarcRecord(inputRecord);
        private MarcRecordWriter inputWriter = new MarcRecordWriter(inputRecord);
        private MarcRecordReader inputReader = new MarcRecordReader(inputRecord);
        private MarcRecordWriter commonRecWriter = new MarcRecordWriter(commonRec);
        private MarcRecordReader reader = new MarcRecordReader(inputRecord);
        private ResourceBundle messages = ResourceBundles.getBundle("actions");

        TestSet() throws IOException {
        }
    }

    // These tests are not perfect , and the actual testing of output is being done via ocb-tools, as we cannot test output here.
    @Test
    public void checkForAlteredClassificationForDisputas_test_correct_record() throws Exception {
        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, null);
        TestSet testSet = new TestSet();

        testSet.inputWriter.addFieldSubfield("652", "m", "klassemærke1");
        testSet.commonRecWriter.addFieldSubfield("652", "m", "UdeN klAssemærke");

        when(rawRepo.recordExists(eq(testSet.reader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(rawRepo.fetchRecord(eq(testSet.reader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(testSet.commonRec, MarcXChangeMimeType.MARCXCHANGE));

        assertThat(instance.canChangeClassificationForDisputas(testSet.inputReader), equalTo(true));
    }

    @Test
    public void checkForAlteredClassificationForDisputas_test_wrong_current_652() throws Exception {

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, null);
        TestSet testSet = new TestSet();

        testSet.inputWriter.addFieldSubfield("652", "m", "klassemærke1");
        testSet.inputWriter.addFieldSubfield("652", "m", "klassemærke2");
        testSet.commonRecWriter.addFieldSubfield("652", "m", "med klassemærke");

        when(rawRepo.recordExists(eq(testSet.reader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(rawRepo.fetchRecord(eq(testSet.reader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(testSet.commonRec, MarcXChangeMimeType.MARCXCHANGE));

        assertThat(instance.canChangeClassificationForDisputas(testSet.inputReader), equalTo(false));
    }

    @Test
    public void checkForAlteredClassificationForDisputas_test_wrong_materialType() throws Exception {

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, null);
        TestSet testSet = new TestSet();

        testSet.inputWriter.addFieldSubfield("652", "m", "klassemærke1");
        testSet.commonRecWriter.addOrReplaceSubfield("008", "d", "l");
        testSet.commonRecWriter.addFieldSubfield("652", "m", "uden klassemærke");

        when(rawRepo.recordExists(eq(testSet.reader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(rawRepo.fetchRecord(eq(testSet.reader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(testSet.commonRec, MarcXChangeMimeType.MARCXCHANGE));

        assertThat(instance.canChangeClassificationForDisputas(testSet.inputReader), equalTo(false));
    }

    @Test
    public void checkForAlteredClassificationForDisputas_test_no_652() throws Exception {

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, null);
        TestSet testSet = new TestSet();

        testSet.inputWriter.addFieldSubfield("652", "m", "uden klassemærke");
        testSet.commonRecWriter.addOrReplaceSubfield("008", "d", "l");
        testSet.commonRecWriter.addFieldSubfield("652", "m", "uden klassemærke");

        when(rawRepo.recordExists(eq(testSet.reader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(rawRepo.fetchRecord(eq(testSet.reader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(testSet.commonRec, MarcXChangeMimeType.MARCXCHANGE));

        assertThat(instance.canChangeClassificationForDisputas(testSet.inputReader), equalTo(true));
    }

    @Test
    public void checkForAlteredClassificationForDisputas_test_equal_652() throws Exception {

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, null);
        TestSet testSet = new TestSet();

        testSet.inputWriter.addFieldSubfield("652", "m", "uden klassemærke");
        testSet.commonRecWriter.addFieldSubfield("652", "m", "uden klassemærke");

        when(rawRepo.recordExists(eq(testSet.reader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(rawRepo.fetchRecord(eq(testSet.reader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(testSet.commonRec, MarcXChangeMimeType.MARCXCHANGE));

        assertThat(instance.canChangeClassificationForDisputas(testSet.inputReader), equalTo(true));
    }

    @Test
    public void testisNationalCommonRecord_wrong032() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, null);
        assertThat(instance.isNationalCommonRecord(record), equalTo(false));
    }

    @Test
    public void testisNationalCommonRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.addOrReplaceSubfield("032", "a", "NET");

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, null);
        assertThat(instance.isNationalCommonRecord(record), equalTo(false));
    }

    @Test
    public void testisNationalCommonRecord_OK() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.addOrReplaceSubfield("032", "a", "DBF12345");
        writer.addOrReplaceSubfield("032", "x", "SFU12345");

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, null);
        assertThat(instance.isNationalCommonRecord(record), equalTo(false));
    }

    @Test
    public void testisNationalCommonRecord_wrong032x() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.addOrReplaceSubfield("032", "a", "DBF12345");
        writer.addOrReplaceSubfield("032", "x", "ACC12345");

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, null);
        assertThat(instance.isNationalCommonRecord(record), equalTo(true));
    }

    @Test
    public void testcreateExtentableFieldsRx_none() throws Exception {
        String agencyId = "870970";

        when(vipCoreService.hasFeature(eq(agencyId), eq(VipCoreService.Rule.AUTH_COMMON_NOTES))).thenReturn(false);
        when(vipCoreService.hasFeature(eq(agencyId), eq(VipCoreService.Rule.AUTH_COMMON_SUBJECTS))).thenReturn(false);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, null);
        assertThat(instance.createExtendableFieldsRx(agencyId), equalTo(""));
    }

    @Test
    public void testcreateExtentableFieldsRx_notes() throws Exception {
        String agencyId = "870970";

        when(vipCoreService.hasFeature(eq(agencyId), eq(VipCoreService.Rule.AUTH_COMMON_NOTES))).thenReturn(true);
        when(vipCoreService.hasFeature(eq(agencyId), eq(VipCoreService.Rule.AUTH_COMMON_SUBJECTS))).thenReturn(false);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, null);
        assertThat(instance.createExtendableFieldsRx(agencyId), equalTo(NoteAndSubjectExtensionsHandler.EXTENDABLE_NOTE_FIELDS));
    }

    @Test
    public void testcreateExtentableFieldsRx_subjects() throws Exception {
        String agencyId = "870970";

        when(vipCoreService.hasFeature(eq(agencyId), eq(VipCoreService.Rule.AUTH_COMMON_NOTES))).thenReturn(false);
        when(vipCoreService.hasFeature(eq(agencyId), eq(VipCoreService.Rule.AUTH_COMMON_SUBJECTS))).thenReturn(true);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, null);
        assertThat(instance.createExtendableFieldsRx(agencyId), equalTo(NoteAndSubjectExtensionsHandler.EXTENDABLE_SUBJECT_FIELDS));
    }

    @Test
    public void testcreateExtentableFieldsRx_both() throws Exception {
        String agencyId = "870970";

        when(vipCoreService.hasFeature(eq(agencyId), eq(VipCoreService.Rule.AUTH_COMMON_NOTES))).thenReturn(true);
        when(vipCoreService.hasFeature(eq(agencyId), eq(VipCoreService.Rule.AUTH_COMMON_SUBJECTS))).thenReturn(true);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, null);
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

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, null);
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

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, null);
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

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, null);
        assertThat(instance.isFieldChangedInOtherRecord(field, record), equalTo(true));
    }

    @Test
    public void testisFieldChangedInOtherRecord_Field900_withxwz_NoChange_1() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.EXPANDED_VOLUME);

        final MarcField field = new MarcField("900", "00");
        field.getSubfields().add(new MarcSubField("a", "Møllgaard"));
        field.getSubfields().add(new MarcSubField("h", "H. Peter"));
        field.getSubfields().add(new MarcSubField("x", "se"));
        field.getSubfields().add(new MarcSubField("w", "Møllgaard, Peter (f. 1964-02-23)"));
        field.getSubfields().add(new MarcSubField("z", "700/1"));

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, null);
        assertThat(instance.isFieldChangedInOtherRecord(field, record), equalTo(false));
    }

    @Test
    public void testisFieldChangedInOtherRecord_Field900_Withxwz_NoChange_2() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.EXPANDED_VOLUME);

        final MarcField field = new MarcField("900", "00");
        field.getSubfields().add(new MarcSubField("a", "Kastberg"));
        field.getSubfields().add(new MarcSubField("h", "Claus"));
        field.getSubfields().add(new MarcSubField("x", "se også under det senere navn"));
        field.getSubfields().add(new MarcSubField("w", "Kastberg Nielsen, Claus"));
        field.getSubfields().add(new MarcSubField("z", "700/2"));

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, null);
        assertThat(instance.isFieldChangedInOtherRecord(field, record), equalTo(false));
    }

    @Test
    public void testisFieldChangedInOtherRecord_Field900_Withoutxwz_NoChange() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.EXPANDED_VOLUME);

        final MarcField field = new MarcField("900", "00");
        field.getSubfields().add(new MarcSubField("a", "Møllgaard"));
        field.getSubfields().add(new MarcSubField("h", "H. Peter"));

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, null);
        assertThat(instance.isFieldChangedInOtherRecord(field, record), equalTo(false));
    }

    @Test
    public void testisFieldChangedInOtherRecord_Field900_Withoutxwz_Changed() throws Exception {
        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.EXPANDED_VOLUME);

        final MarcField field = new MarcField("900", "00");
        field.getSubfields().add(new MarcSubField("a", "Møllegaard")); // Added 'e'
        field.getSubfields().add(new MarcSubField("h", "H. Peter"));

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, null);
        assertThat(instance.isFieldChangedInOtherRecord(field, record), equalTo(true));
    }

    @Test
    public void testrecordDataForRawRepo_recordNotExists() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        String groupId = "870970";

        when(rawRepo.recordExists(eq(reader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(false);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, null);

        assertThat(instance.recordDataForRawRepo(record, groupId), equalTo(record));
    }

    @Test
    public void testrecordDataForRawRepo_isNotNationalCommonRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        String groupId = "870970";

        when(rawRepo.recordExists(eq(reader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(rawRepo.fetchRecord(eq(reader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, null);

        assertThat(instance.recordDataForRawRepo(record, groupId), equalTo(record));
    }

    @Test
    public void testrecordDataForRawRepo() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        MarcRecordWriter writer = new MarcRecordWriter(record);

        writer.addOrReplaceSubfield("032", "a", "NET");
        String groupId = "870970";

        when(rawRepo.recordExists(eq(reader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(rawRepo.fetchRecord(eq(reader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(vipCoreService.hasFeature(eq(groupId), eq(VipCoreService.Rule.AUTH_COMMON_NOTES))).thenReturn(true);
        when(vipCoreService.hasFeature(eq(groupId), eq(VipCoreService.Rule.AUTH_COMMON_SUBJECTS))).thenReturn(true);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, null);
        MarcRecord expected = new MarcRecord(record);
        //new MarcRecordReader(expected).getField("504").getSubfields().add(new MarcSubField("&", groupId));

        assertThat(instance.recordDataForRawRepo(record, groupId), equalTo(expected));
    }

    @Test
    public void testCollapseSameRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecord currentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecord expected = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);

        String groupId = "830010";

        when(vipCoreService.hasFeature(eq(groupId), eq(VipCoreService.Rule.AUTH_COMMON_NOTES))).thenReturn(true);
        when(vipCoreService.hasFeature(eq(groupId), eq(VipCoreService.Rule.AUTH_COMMON_SUBJECTS))).thenReturn(true);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, null);

        assertThat(sortRecord(instance.collapse(record, currentRecord, groupId, false)), equalTo(sortRecord(expected)));
    }

    @Test
    public void testCollapseWithAUT() throws Exception {
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

        when(vipCoreService.hasFeature(eq(groupId), eq(VipCoreService.Rule.AUTH_COMMON_NOTES))).thenReturn(true);
        when(vipCoreService.hasFeature(eq(groupId), eq(VipCoreService.Rule.AUTH_COMMON_SUBJECTS))).thenReturn(true);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, null);

        assertThat(sortRecord(instance.collapse(record, currentRecord, groupId, false)), equalTo(sortRecord(expected)));
    }

    @Test
    public void testNoteAndSubject_NoNotesOrSubjectPermissions() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);

        MarcRecordReader reader = new MarcRecordReader(record);

        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.addOrReplaceSubfield("032", "a", "DBF12345");
        writer.addOrReplaceSubfield("032", "x", "ACC12345");

        MarcRecord existingRecord = new MarcRecord(record);

        when(rawRepo.recordExists(eq(reader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(rawRepo.fetchRecord(eq(reader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(existingRecord, MarcXChangeMimeType.MARCXCHANGE));

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, null);
        assertThat(instance.recordDataForRawRepo(record, "820010"), equalTo(record));
    }

    @Test
    public void testNoteAndSubjectFields_DBCNote() throws Exception {
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

        when(rawRepo.recordExists(eq(currentReader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(rawRepo.fetchRecord(eq(currentReader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(currentRecord, MarcXChangeMimeType.MARCXCHANGE));

        when(vipCoreService.hasFeature(eq(groupId), eq(VipCoreService.Rule.AUTH_COMMON_NOTES))).thenReturn(true);
        when(vipCoreService.hasFeature(eq(groupId), eq(VipCoreService.Rule.AUTH_COMMON_SUBJECTS))).thenReturn(true);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, ResourceBundles.getBundle("actions"));

        try {
            instance.recordDataForRawRepo(record, groupId);
            fail(); // To make sure we never hit this branch
        } catch (UpdateException ex) {
            assertThat(ex.getMessage(), equalTo("Posten må ikke beriges"));
        }
    }

    @Test
    public void testNoteAndSubjectFields_ExistingNote() throws Exception {
        String groupId = "830010";

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

        MarcRecord expectedRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        MarcField expectedField = new MarcField("530", "00");
        expectedField.getSubfields().add(new MarcSubField("&", groupId));
        expectedField.getSubfields().add(new MarcSubField("a", "Julemandens Nisseslagteri"));
        expectedRecord.getFields().add(expectedField);
        new MarcRecordWriter(expectedRecord).sort();

        when(rawRepo.recordExists(eq(currentReader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(rawRepo.fetchRecord(eq(currentReader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(currentRecord, MarcXChangeMimeType.MARCXCHANGE));

        when(vipCoreService.hasFeature(eq(groupId), eq(VipCoreService.Rule.AUTH_COMMON_NOTES))).thenReturn(true);
        when(vipCoreService.hasFeature(eq(groupId), eq(VipCoreService.Rule.AUTH_COMMON_SUBJECTS))).thenReturn(true);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, ResourceBundles.getBundle("actions"));

        assertThat(instance.recordDataForRawRepo(record, groupId), equalTo(expectedRecord));
    }

    @Test
    public void testNoteAndSubjectFields_AddNote() throws Exception {
        String groupId = "830010";

        MarcRecord currentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        MarcRecordReader currentReader = new MarcRecordReader(currentRecord);
        MarcField currentNoteField = new MarcField("666", "00");
        currentNoteField.getSubfields().add(new MarcSubField("&", "123456"));
        currentNoteField.getSubfields().add(new MarcSubField("a", "Julemandens Nisseslagteri"));
        currentRecord.getFields().add(currentNoteField);

        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        MarcField field530 = new MarcField("530", "00");
        field530.setSubfields(Collections.singletonList(new MarcSubField("a", "Julemandens Nisseslagteri")));
        record.getFields().add(field530);
        MarcField field666 = new MarcField("666", "00");
        field666.getSubfields().add(new MarcSubField("&", "123456"));
        field666.getSubfields().add(new MarcSubField("a", "Julemandens Nisseslagteri"));
        currentRecord.getFields().add(field666);
        record.getFields().add(field666);

        MarcRecord expectedRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        MarcField expectedField530 = new MarcField("530", "00");
        expectedField530.getSubfields().add(new MarcSubField("&", groupId));
        expectedField530.getSubfields().add(new MarcSubField("a", "Julemandens Nisseslagteri"));
        expectedRecord.getFields().add(expectedField530);
        MarcField expectedField666 = new MarcField("666", "00");
        expectedField666.getSubfields().add(new MarcSubField("&", groupId));
        expectedField666.getSubfields().add(new MarcSubField("a", "Julemandens Nisseslagteri"));
        expectedRecord.getFields().add(expectedField666);
        new MarcRecordWriter(expectedRecord).sort();

        when(rawRepo.recordExists(eq(currentReader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(rawRepo.fetchRecord(eq(currentReader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(currentRecord, MarcXChangeMimeType.MARCXCHANGE));

        when(vipCoreService.hasFeature(eq(groupId), eq(VipCoreService.Rule.AUTH_COMMON_NOTES))).thenReturn(true);
        when(vipCoreService.hasFeature(eq(groupId), eq(VipCoreService.Rule.AUTH_COMMON_SUBJECTS))).thenReturn(true);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, ResourceBundles.getBundle("actions"));

        assertThat(instance.recordDataForRawRepo(record, groupId), equalTo(expectedRecord));
    }

    @Test
    public void testAuthenticateCommonRecordExtraFields_AllowedNote_AllowedSubject_AddNoteField() throws Exception {
        final String groupId = "830010";

        final MarcRecord currentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordReader currentReader = new MarcRecordReader(currentRecord);

        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordWriter recordWriter = new MarcRecordWriter(record);
        recordWriter.addFieldSubfield("530", "a", "a Note");
        final Map<String, MarcRecord> curRecordCollection = new HashMap<>();
        curRecordCollection.put(currentReader.getRecordId(), currentRecord);

        when(rawRepo.recordExists(eq(currentReader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(rawRepo.fetchRecordCollection(eq(currentReader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(curRecordCollection);
        when(vipCoreService.hasFeature(eq(groupId), eq(VipCoreService.Rule.AUTH_COMMON_NOTES))).thenReturn(true);
        when(vipCoreService.hasFeature(eq(groupId), eq(VipCoreService.Rule.AUTH_COMMON_SUBJECTS))).thenReturn(true);

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, ResourceBundles.getBundle("actions"));
        final List<MessageEntryDTO> expected = new ArrayList<>();
        final List<MessageEntryDTO> actual = instance.authenticateCommonRecordExtraFields(record, groupId);

        assertThat(actual, equalTo(expected));
    }

    @Test
    public void testAuthenticateCommonRecordExtraFields_AllowedNote_AllowedSubject_AddSubjectField() throws Exception {
        final String groupId = "830010";

        final MarcRecord currentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordReader currentReader = new MarcRecordReader(currentRecord);

        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordWriter recordWriter = new MarcRecordWriter(record);
        recordWriter.addFieldSubfield("666", "a", "a subject");
        final Map<String, MarcRecord> curRecordCollection = new HashMap<>();
        curRecordCollection.put(currentReader.getRecordId(), currentRecord);

        when(rawRepo.recordExists(eq(currentReader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(rawRepo.fetchRecordCollection(eq(currentReader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(curRecordCollection);
        when(vipCoreService.hasFeature(eq(groupId), eq(VipCoreService.Rule.AUTH_COMMON_NOTES))).thenReturn(true);
        when(vipCoreService.hasFeature(eq(groupId), eq(VipCoreService.Rule.AUTH_COMMON_SUBJECTS))).thenReturn(true);

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, ResourceBundles.getBundle("actions"));
        final List<MessageEntryDTO> expected = new ArrayList<>();
        final List<MessageEntryDTO> actual = instance.authenticateCommonRecordExtraFields(record, groupId);

        assertThat(actual, equalTo(expected));
    }

    @Test
    public void testAuthenticateCommonRecordExtraFields_AllowedNote_AllowedSubject_AddNoteField_AddSubjectField() throws Exception {
        final String groupId = "830010";

        final MarcRecord currentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordReader currentReader = new MarcRecordReader(currentRecord);

        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordWriter recordWriter = new MarcRecordWriter(record);
        recordWriter.addFieldSubfield("530", "a", "a Note");
        recordWriter.addFieldSubfield("666", "a", "a subject");
        final Map<String, MarcRecord> curRecordCollection = new HashMap<>();
        curRecordCollection.put(currentReader.getRecordId(), currentRecord);

        when(rawRepo.recordExists(eq(currentReader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(rawRepo.fetchRecordCollection(eq(currentReader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(curRecordCollection);
        when(vipCoreService.hasFeature(eq(groupId), eq(VipCoreService.Rule.AUTH_COMMON_NOTES))).thenReturn(true);
        when(vipCoreService.hasFeature(eq(groupId), eq(VipCoreService.Rule.AUTH_COMMON_SUBJECTS))).thenReturn(true);

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, ResourceBundles.getBundle("actions"));
        final List<MessageEntryDTO> expected = new ArrayList<>();
        final List<MessageEntryDTO> actual = instance.authenticateCommonRecordExtraFields(record, groupId);

        assertThat(actual, equalTo(expected));
    }

    @Test
    public void testAuthenticateCommonRecordExtraFields_AllowedNote_NotAllowedSubject_AddNoteField() throws Exception {
        final String groupId = "830010";

        final MarcRecord currentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordReader currentReader = new MarcRecordReader(currentRecord);

        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordWriter recordWriter = new MarcRecordWriter(record);
        recordWriter.addFieldSubfield("530", "a", "a Note");
        final Map<String, MarcRecord> curRecordCollection = new HashMap<>();
        curRecordCollection.put(currentReader.getRecordId(), currentRecord);

        when(rawRepo.recordExists(eq(currentReader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(rawRepo.fetchRecordCollection(eq(currentReader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(curRecordCollection);
        when(vipCoreService.hasFeature(eq(groupId), eq(VipCoreService.Rule.AUTH_COMMON_NOTES))).thenReturn(true);
        when(vipCoreService.hasFeature(eq(groupId), eq(VipCoreService.Rule.AUTH_COMMON_SUBJECTS))).thenReturn(true);

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, ResourceBundles.getBundle("actions"));
        final List<MessageEntryDTO> expected = new ArrayList<>();
        final List<MessageEntryDTO> actual = instance.authenticateCommonRecordExtraFields(record, groupId);

        assertThat(actual, equalTo(expected));
    }

    @Test
    public void testAuthenticateCommonRecordExtraFields_NotAllowedNote_AllowedSubject_AddSubjectField() throws Exception {
        final String groupId = "830010";

        final MarcRecord currentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordReader currentReader = new MarcRecordReader(currentRecord);

        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordWriter recordWriter = new MarcRecordWriter(record);
        recordWriter.addFieldSubfield("666", "a", "a subject");
        final Map<String, MarcRecord> curRecordCollection = new HashMap<>();
        curRecordCollection.put(currentReader.getRecordId(), currentRecord);

        when(rawRepo.recordExists(eq(currentReader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(rawRepo.fetchRecordCollection(eq(currentReader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(curRecordCollection);
        when(vipCoreService.hasFeature(eq(groupId), eq(VipCoreService.Rule.AUTH_COMMON_NOTES))).thenReturn(true);
        when(vipCoreService.hasFeature(eq(groupId), eq(VipCoreService.Rule.AUTH_COMMON_SUBJECTS))).thenReturn(true);

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, ResourceBundles.getBundle("actions"));
        final List<MessageEntryDTO> expected = new ArrayList<>();
        final List<MessageEntryDTO> actual = instance.authenticateCommonRecordExtraFields(record, groupId);

        assertThat(actual, equalTo(expected));
    }

    @Test
    public void testAuthenticateCommonRecordExtraFields_NotAllowedNote_AllowedSubject_AddNoteField() throws Exception {
        final String groupId = "830010";

        final MarcRecord currentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordReader currentReader = new MarcRecordReader(currentRecord);

        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordWriter recordWriter = new MarcRecordWriter(record);
        recordWriter.addFieldSubfield("530", "a", "a Note");
        final Map<String, MarcRecord> curRecordCollection = new HashMap<>();
        curRecordCollection.put(currentReader.getRecordId(), currentRecord);

        when(rawRepo.recordExists(eq(currentReader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(rawRepo.fetchRecordCollection(eq(currentReader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(curRecordCollection);
        when(vipCoreService.hasFeature(eq(groupId), eq(VipCoreService.Rule.AUTH_COMMON_NOTES))).thenReturn(false);
        when(vipCoreService.hasFeature(eq(groupId), eq(VipCoreService.Rule.AUTH_COMMON_SUBJECTS))).thenReturn(true);

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, ResourceBundles.getBundle("actions"));
        final List<MessageEntryDTO> expected = new ArrayList<>();
        final MessageEntryDTO messageEntryDTO = new MessageEntryDTO();
        messageEntryDTO.setType(TypeEnumDTO.ERROR);
        messageEntryDTO.setMessage("Brugeren '830010' har ikke ret til at rette/tilføje feltet '530' i posten '20611529'");
        expected.add(messageEntryDTO);

        final List<MessageEntryDTO> actual = instance.authenticateCommonRecordExtraFields(record, groupId);

        assertThat(actual, equalTo(expected));
    }

    @Test
    public void testAuthenticateCommonRecordExtraFields_AllowedNote_NotAllowedSubject_AddSubjectField() throws Exception {
        final String groupId = "830010";

        final MarcRecord currentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordReader currentReader = new MarcRecordReader(currentRecord);

        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordWriter recordWriter = new MarcRecordWriter(record);
        recordWriter.addFieldSubfield("666", "a", "a subject");
        final Map<String, MarcRecord> curRecordCollection = new HashMap<>();
        curRecordCollection.put(currentReader.getRecordId(), currentRecord);

        when(rawRepo.recordExists(eq(currentReader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(rawRepo.fetchRecordCollection(eq(currentReader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(curRecordCollection);
        when(vipCoreService.hasFeature(eq(groupId), eq(VipCoreService.Rule.AUTH_COMMON_NOTES))).thenReturn(true);
        when(vipCoreService.hasFeature(eq(groupId), eq(VipCoreService.Rule.AUTH_COMMON_SUBJECTS))).thenReturn(false);

        final NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(vipCoreService, rawRepo, ResourceBundles.getBundle("actions"));
        final List<MessageEntryDTO> expected = new ArrayList<>();
        final MessageEntryDTO messageEntryDTO = new MessageEntryDTO();
        messageEntryDTO.setType(TypeEnumDTO.ERROR);
        messageEntryDTO.setMessage("Brugeren '830010' har ikke ret til at rette/tilføje feltet '666' i posten '20611529'");
        expected.add(messageEntryDTO);

        final List<MessageEntryDTO> actual = instance.authenticateCommonRecordExtraFields(record, groupId);

        assertThat(actual, equalTo(expected));
    }

    @Test
    public void testAuthenticateCommonRecordExtraFields_NotAllowedNote_NotAllowedSubject_AddNoteField_AddSubjectField() throws Exception {
        final String groupId = "830010";

        final MarcRecord currentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordReader currentReader = new MarcRecordReader(currentRecord);

        final MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.NATIONAL_COMMON_RECORD);
        final MarcRecordWriter recordWriter = new MarcRecordWriter(record);
        recordWriter.addFieldSubfield("530", "a", "a Note");
        recordWriter.addFieldSubfield("666", "a", "a subject");
        final Map<String, MarcRecord> curRecordCollection = new HashMap<>();
        curRecordCollection.put(currentReader.getRecordId(), currentRecord);

        when(rawRepo.recordExists(eq(currentReader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(rawRepo.fetchRecordCollection(eq(currentReader.getRecordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(curRecordCollection);
        when(vipCoreService.hasFeature(eq(groupId), eq(VipCoreService.Rule.AUTH_COMMON_NOTES))).thenReturn(false);
        when(vipCoreService.hasFeature(eq(groupId), eq(VipCoreService.Rule.AUTH_COMMON_SUBJECTS))).thenReturn(false);

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

        assertThat(actual, equalTo(expected));
    }

    private MarcRecord sortRecord(MarcRecord record) {
        Collections.sort(record.getFields(), new Comparator<MarcField>() {
            public int compare(MarcField o1, MarcField o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });

        return record;
    }

}
