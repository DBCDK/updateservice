/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;

import java.util.Date;
import java.util.Set;

/**
 * @author stp
 */
public class RawRepoRecordMock implements Record {
    private RecordId id = null;
    private byte[] content = null;
    private String mimeType = null;
    private Date created = null;
    private Date modified = null;
    private String trackingId = null;
    private boolean original = false;
    private boolean deleted = false;
    private boolean enriched = false;
    private Set<RecordId> references = null;

    public RawRepoRecordMock(String id, int library) {
        this.id = new RecordId(id, library);
    }
    /*
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
            instance.checkForAlteredClassificationForDisputas(testSet.inputRecord, testSet.inputReader, testSet.messages);
        } catch (UpdateException ue) {
            assertNull(ue);
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
            instance.checkForAlteredClassificationForDisputas(testSet.inputRecord, testSet.inputReader, testSet.messages);

        } catch (UpdateException ue) {
            assertEquals(ue.getMessage(), "Postens klassemærke kan ikke ændres");
        }
    }


    @Test
    public void checkForAlteredClassificationForDisputas_test_wrong_materialType() throws Exception {

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo, null);
        TestSet testSet = new TestSet();

        testSet.inputWriter.addFieldSubfield("652", "m", "klassemærke1");
        testSet.commonRecWriter.addOrReplaceSubfield("008" ,"d" , "l");
        testSet.commonRecWriter.addFieldSubfield("652", "m", "uden klassemærke");

        when(rawRepo.recordExists(eq(testSet.reader.recordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(rawRepo.fetchRecord(eq(testSet.reader.recordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(testSet.commonRec, MarcXChangeMimeType.MARCXCHANGE));

        try {
            instance.checkForAlteredClassificationForDisputas(testSet.inputRecord, testSet.inputReader, testSet.messages);

        } catch (UpdateException ue) {
            assertEquals(ue.getMessage(), "Postens klassemærke kan ikke ændres");
        }
    }

    @Test
    public void checkForAlteredClassificationForDisputas_test_no_652() throws Exception {

        NoteAndSubjectExtensionsHandler instance = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo, null);
        TestSet testSet = new TestSet();

        testSet.inputWriter.addFieldSubfield("652", "m", "uden klassemærke");
        testSet.commonRecWriter.addOrReplaceSubfield("008" ,"d" , "l");
        testSet.commonRecWriter.addFieldSubfield("652", "m", "uden klassemærke");

        when(rawRepo.recordExists(eq(testSet.reader.recordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(rawRepo.fetchRecord(eq(testSet.reader.recordId()), eq(RawRepo.COMMON_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(testSet.commonRec, MarcXChangeMimeType.MARCXCHANGE));

        try {
            instance.checkForAlteredClassificationForDisputas(testSet.inputRecord, testSet.inputReader, testSet.messages);
        } catch (UpdateException ue) {
            assertNull(ue);
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
            instance.checkForAlteredClassificationForDisputas(testSet.inputRecord, testSet.inputReader, testSet.messages);
        } catch (UpdateException ue) {
            assertNull(ue);
        }
    }



     */

    @Override
    public RecordId getId() {
        return id;
    }

    public void setId(RecordId id) {
        this.id = id;
    }

    @Override
    public byte[] getContent() {
        return content;
    }

    @Override
    public void setContent(byte[] content) {
        this.content = content;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    @Override
    public Date getCreated() {
        return created;
    }

    @Override
    public void setCreated(Date created) {
        this.created = created;
    }

    @Override
    public Date getModified() {
        return modified;
    }

    @Override
    public void setModified(Date modified) {
        this.modified = modified;
    }

    @Override
    public String getTrackingId() {
        return trackingId;
    }

    public void setTrackingId(String trackingId) {
        this.trackingId = trackingId;
    }

    @Override
    public boolean isOriginal() {
        return original;
    }

    public void setOriginal(boolean original) {
        this.original = original;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public boolean isEnriched() {
        return enriched;
    }

    public void setEnriched(boolean enriched) {
        this.enriched = enriched;
    }

    public String getEnrichmentTrail() {
        return "";
    }

    public Set<RecordId> getReferences() {
        return references;
    }

    public void setReferences(Set<RecordId> references) {
        this.references = references;
    }
}
