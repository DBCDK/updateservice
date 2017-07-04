/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;


import dk.dbc.iscrum.records.*;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.updateservice.actions.AssertActionsUtil;
import dk.dbc.updateservice.actions.GlobalActionState;
import dk.dbc.updateservice.actions.UpdateTestUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.ResourceBundle;

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
    public void testIsFieldNationalCommonField_wrongField() throws Exception {
        MarcField field = new MarcField("001", "00");
        field.getSubfields().add(new MarcSubField("a", "12345678"));
        field.getSubfields().add(new MarcSubField("b", "870970"));

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo, null);
        assertThat(instance.isFieldNationalCommonField(field), equalTo(false));
    }

    @Test
    public void testIsFieldNationalCommonField_wrongSubfieldValue() throws Exception {
        MarcField field = new MarcField("032", "00");
        field.getSubfields().add(new MarcSubField("a", "ABC"));

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo, null);
        assertThat(instance.isFieldNationalCommonField(field), equalTo(true));
    }

    @Test
    public void testIsFieldNationalCommonField() throws Exception {
        MarcField field = new MarcField("032", "00");
        field.getSubfields().add(new MarcSubField("a", "BKM"));
        field.getSubfields().add(new MarcSubField("b", "870970"));

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo, null);
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
        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo, null);
        TestSet testSet = new TestSet();

        testSet.inputWriter.addFieldSubfield("652", "m", "klassemærke1");
        testSet.commonRecWriter.addFieldSubfield("652", "m", "UdeN klAssemærke");

        when(rawRepo.recordExists(eq(testSet.reader.recordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(rawRepo.fetchRecord(eq(testSet.reader.recordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(testSet.commonRec, MarcXChangeMimeType.MARCXCHANGE));

        try {
            instance.checkForAlteredClassificationForDisputas(testSet.inputReader, testSet.messages);
        } catch (UpdateException ue) {
            Assert.assertNull(ue);
        }
    }

    @Test
    public void checkForAlteredClassificationForDisputas_test_wrong_current_652() throws Exception {

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo, null);
        TestSet testSet = new TestSet();

        testSet.inputWriter.addFieldSubfield("652", "m", "klassemærke1");
        testSet.inputWriter.addFieldSubfield("652", "m", "klassemærke2");
        testSet.commonRecWriter.addFieldSubfield("652", "m", "med klassemærke");

        when(rawRepo.recordExists(eq(testSet.reader.recordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(rawRepo.fetchRecord(eq(testSet.reader.recordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(testSet.commonRec, MarcXChangeMimeType.MARCXCHANGE));

        try {
            instance.checkForAlteredClassificationForDisputas(testSet.inputReader, testSet.messages);

        } catch (UpdateException ue) {
            Assert.assertEquals(ue.getMessage(), "Postens klassemærke kan ikke ændres");
        }
    }


    @Test
    public void checkForAlteredClassificationForDisputas_test_wrong_materialType() throws Exception {

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo, null);
        TestSet testSet = new TestSet();

        testSet.inputWriter.addFieldSubfield("652", "m", "klassemærke1");
        testSet.commonRecWriter.addOrReplaceSubfield("008", "d", "l");
        testSet.commonRecWriter.addFieldSubfield("652", "m", "uden klassemærke");

        when(rawRepo.recordExists(eq(testSet.reader.recordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(rawRepo.fetchRecord(eq(testSet.reader.recordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(testSet.commonRec, MarcXChangeMimeType.MARCXCHANGE));

        try {
            instance.checkForAlteredClassificationForDisputas(testSet.inputReader, testSet.messages);

        } catch (UpdateException ue) {
            Assert.assertEquals(ue.getMessage(), "Postens klassemærke kan ikke ændres");
        }
    }

    @Test
    public void checkForAlteredClassificationForDisputas_test_no_652() throws Exception {

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo, null);
        TestSet testSet = new TestSet();

        testSet.inputWriter.addFieldSubfield("652", "m", "uden klassemærke");
        testSet.commonRecWriter.addOrReplaceSubfield("008", "d", "l");
        testSet.commonRecWriter.addFieldSubfield("652", "m", "uden klassemærke");

        when(rawRepo.recordExists(eq(testSet.reader.recordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(rawRepo.fetchRecord(eq(testSet.reader.recordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(testSet.commonRec, MarcXChangeMimeType.MARCXCHANGE));

        try {
            instance.checkForAlteredClassificationForDisputas(testSet.inputReader, testSet.messages);
        } catch (UpdateException ue) {
            Assert.assertNull(ue);
        }
    }

    @Test
    public void checkForAlteredClassificationForDisputas_test_equal_652() throws Exception {

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo, null);
        TestSet testSet = new TestSet();

        testSet.inputWriter.addFieldSubfield("652", "m", "uden klassemærke");
        testSet.commonRecWriter.addFieldSubfield("652", "m", "uden klassemærke");

        when(rawRepo.recordExists(eq(testSet.reader.recordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(rawRepo.fetchRecord(eq(testSet.reader.recordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(testSet.commonRec, MarcXChangeMimeType.MARCXCHANGE));

        try {
            instance.checkForAlteredClassificationForDisputas(testSet.inputReader, testSet.messages);
        } catch (UpdateException ue) {
            Assert.assertNull(ue);
        }
    }

    @Test
    public void testisNationalCommonRecord_wrong032() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo, null);
        assertThat(instance.isNationalCommonRecord(record), equalTo(true));
    }

    @Test
    public void testisNationalCommonRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.addOrReplaceSubfield("032", "a", "NET");

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo, null);
        assertThat(instance.isNationalCommonRecord(record), equalTo(false));
    }

    @Test
    public void testcreateExtentableFieldsRx_none() throws Exception {
        String agencyId = "870970";

        when(openAgencyService.hasFeature(eq(agencyId), eq(LibraryRuleHandler.Rule.AUTH_COMMON_NOTES))).thenReturn(false);
        when(openAgencyService.hasFeature(eq(agencyId), eq(LibraryRuleHandler.Rule.AUTH_COMMON_SUBJECTS))).thenReturn(false);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo, null);
        assertThat(instance.createExtendableFieldsRx(agencyId), equalTo(""));
    }

    @Test
    public void testcreateExtentableFieldsRx_notes() throws Exception {
        String agencyId = "870970";

        when(openAgencyService.hasFeature(eq(agencyId), eq(LibraryRuleHandler.Rule.AUTH_COMMON_NOTES))).thenReturn(true);
        when(openAgencyService.hasFeature(eq(agencyId), eq(LibraryRuleHandler.Rule.AUTH_COMMON_SUBJECTS))).thenReturn(false);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo, null);
        assertThat(instance.createExtendableFieldsRx(agencyId), equalTo(NoteAndSubjectExtensionsHandler.EXTENDABLE_NOTE_FIELDS));
    }

    @Test
    public void testcreateExtentableFieldsRx_subjects() throws Exception {
        String agencyId = "870970";

        when(openAgencyService.hasFeature(eq(agencyId), eq(LibraryRuleHandler.Rule.AUTH_COMMON_NOTES))).thenReturn(false);
        when(openAgencyService.hasFeature(eq(agencyId), eq(LibraryRuleHandler.Rule.AUTH_COMMON_SUBJECTS))).thenReturn(true);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo, null);
        assertThat(instance.createExtendableFieldsRx(agencyId), equalTo(NoteAndSubjectExtensionsHandler.EXTENDABLE_SUBJECT_FIELDS));
    }

    @Test
    public void testcreateExtentableFieldsRx_both() throws Exception {
        String agencyId = "870970";

        when(openAgencyService.hasFeature(eq(agencyId), eq(LibraryRuleHandler.Rule.AUTH_COMMON_NOTES))).thenReturn(true);
        when(openAgencyService.hasFeature(eq(agencyId), eq(LibraryRuleHandler.Rule.AUTH_COMMON_SUBJECTS))).thenReturn(true);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo, null);
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

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo, null);
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

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo, null);
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

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo, null);
        assertThat(instance.isFieldChangedInOtherRecord(field, record), equalTo(true));
    }

    @Test
    public void testrecordDataForRawRepo_recordNotExists() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        String groupId = "870970";

        when(rawRepo.recordExists(eq(reader.recordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(false);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo, null);

        assertThat(instance.recordDataForRawRepo(record, groupId), equalTo(record));
    }

    @Test
    public void testrecordDataForRawRepo_isNotNationalCommonRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        String groupId = "870970";

        when(rawRepo.recordExists(eq(reader.recordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(rawRepo.fetchRecord(eq(reader.recordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo, null);

        assertThat(instance.recordDataForRawRepo(record, groupId), equalTo(record));
    }

    @Test
    public void testrecordDataForRawRepo() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        MarcRecordWriter writer = new MarcRecordWriter(record);

        writer.addOrReplaceSubfield("032", "a", "NET");
        String groupId = "870970";

        when(rawRepo.recordExists(eq(reader.recordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(rawRepo.fetchRecord(eq(reader.recordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(openAgencyService.hasFeature(eq(groupId), eq(LibraryRuleHandler.Rule.AUTH_COMMON_NOTES))).thenReturn(true);
        when(openAgencyService.hasFeature(eq(groupId), eq(LibraryRuleHandler.Rule.AUTH_COMMON_SUBJECTS))).thenReturn(true);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo, null);
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

        when(openAgencyService.hasFeature(eq(groupId), eq(LibraryRuleHandler.Rule.AUTH_COMMON_NOTES))).thenReturn(true);
        when(openAgencyService.hasFeature(eq(groupId), eq(LibraryRuleHandler.Rule.AUTH_COMMON_SUBJECTS))).thenReturn(true);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo, null);

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

        when(openAgencyService.hasFeature(eq(groupId), eq(LibraryRuleHandler.Rule.AUTH_COMMON_NOTES))).thenReturn(true);
        when(openAgencyService.hasFeature(eq(groupId), eq(LibraryRuleHandler.Rule.AUTH_COMMON_SUBJECTS))).thenReturn(true);

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo, null);

        assertThat(sortRecord(instance.collapse(record, currentRecord, groupId, false)), equalTo(sortRecord(expected)));
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
