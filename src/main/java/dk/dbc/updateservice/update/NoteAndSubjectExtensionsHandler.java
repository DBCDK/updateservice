
package dk.dbc.updateservice.update;

import dk.dbc.common.records.CatalogExtractionCode;
import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcFieldReader;
import dk.dbc.common.records.MarcFieldWriter;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.common.records.MarcSubField;
import dk.dbc.common.records.utils.LogUtils;
import dk.dbc.common.records.utils.RecordContentTransformer;
import dk.dbc.marcrecord.ExpandCommonMarcRecord;
import dk.dbc.rawrepo.RawRepoException;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.MessageEntryDTO;
import dk.dbc.updateservice.dto.TypeEnumDTO;
import dk.dbc.updateservice.utils.ResourceBundles;
import dk.dbc.vipcore.exception.VipCoreException;
import dk.dbc.vipcore.libraryrules.VipCoreLibraryRulesConnector;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

public class NoteAndSubjectExtensionsHandler {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(NoteAndSubjectExtensionsHandler.class);
    private final VipCoreService vipCoreService;
    private final RawRepo rawRepo;
    private final ResourceBundle messages;

    static final String CATALOGUE_CODE_FIELD = "032";
    static final String EXTENDABLE_NOTE_FIELDS = "504|530";
    static final String EXTENDABLE_CONTROLLED_SUBJECT_FIELDS = "600|610|630|666";
    static final String EXTENDABLE_SUBJECT_FIELDS = "631|664|665";
    private static final String CLASSIFICATION_FIELDS = "652";
    private static final String NO_CLASSIFICATION = "uden klassemærke";

    public NoteAndSubjectExtensionsHandler(VipCoreService vipCoreService, RawRepo rawRepo, ResourceBundle messages) {
        this.vipCoreService = vipCoreService;
        this.rawRepo = rawRepo;
        this.messages = messages;
    }

    void addSubjectFields(MarcRecord result, MarcRecord marcRecord, MarcRecord curRecord, String groupId ) throws UpdateException {
        final List<MarcField> newControlledSubjectFields = marcRecord.getFields().stream()
                .filter(field -> field.getName().matches(EXTENDABLE_CONTROLLED_SUBJECT_FIELDS)).collect(Collectors.toList());
        final List<MarcField> currentControlledSubjectFields = curRecord.getFields().stream()
                .filter(field -> field.getName().matches(EXTENDABLE_CONTROLLED_SUBJECT_FIELDS)).collect(Collectors.toList());

        if (marcFieldsEqualsIgnoreAmpersand(newControlledSubjectFields, currentControlledSubjectFields)) {
            // Controlled subject field are identical, so just copy the existing ones over
            result.getFields().addAll(currentControlledSubjectFields);
        } else {
            // If there are changes then we need to see if one or more subject fields doesn't originate from an FBS library
            for (MarcField currentControlledSubjectField : currentControlledSubjectFields) {
                final MarcFieldReader currentControlledSubjectFieldReader = new MarcFieldReader(currentControlledSubjectField);
                if (currentControlledSubjectFieldReader.hasSubfield("&") && !currentControlledSubjectFieldReader.getValue("&").startsWith("7")) {
                    final String msg = messages.getString("update.dbc.record.dbc.subjects");
                    LOGGER.error("Unable to create sub actions due to an error: {}", msg);
                    throw new UpdateException(msg);
                }
            }
            // Now we know the fields can be overwritten - same owner is set on all controlled subject fields even if the value existed before
            for (MarcField newControlledSubjectField : newControlledSubjectFields) {
                result.getFields().add(copyWithNewAmpersand(newControlledSubjectField, groupId));
            }
        }

        // Handle (non-controlled) subject fields. These fields are handled individually
        final List<MarcField> newSubjectFields = marcRecord.getFields().stream()
                .filter(field -> field.getName().matches(EXTENDABLE_SUBJECT_FIELDS)).collect(Collectors.toList());
        final List<MarcField> currentSubjectFields = curRecord.getFields().stream()
                .filter(field -> field.getName().matches(EXTENDABLE_SUBJECT_FIELDS)).collect(Collectors.toList());

        if (marcFieldsEqualsIgnoreAmpersand(newSubjectFields, currentSubjectFields)) {
            // Subject field are identical, so just copy the existing ones over
            result.getFields().addAll(currentSubjectFields);
        } else {
            final List<MarcField> currentSubjectFieldsCloned = new ArrayList<>();
            currentSubjectFields.forEach(f -> currentSubjectFieldsCloned.add(new MarcField(f)));
            currentSubjectFieldsCloned.forEach(f -> new MarcFieldWriter(f).removeSubfield("&"));
            // If the existing field is unchanged then we need to keep the value of *& which might not be included in the incoming record
            for (MarcField newSubjectField : newSubjectFields) {
                final MarcField newSubjectFieldClone = new MarcField(newSubjectField);
                new MarcFieldWriter(newSubjectFieldClone).removeSubfield("&");
                boolean addNewSubjectField = true;
                for (int i = 0; i < currentSubjectFieldsCloned.size(); i++) {
                    // The position of the fields are the same. If we match a field in the list without ampersand then
                    // copy the existing field from the list with ampersand
                    if (currentSubjectFieldsCloned.get(i).equals(newSubjectFieldClone)) {
                        result.getFields().add(currentSubjectFields.get(i));
                        addNewSubjectField = false;
                        break;
                    }
                }
                if (addNewSubjectField) {
                    newSubjectFieldClone.getSubfields().add(0, new MarcSubField("&", groupId));
                    result.getFields().add(newSubjectFieldClone);
                }
            }
        }
    }

    void addNoteFields(MarcRecord result, MarcRecord marcRecord, MarcRecord curRecord, String groupId ) throws UpdateException {

        final List<MarcField> newNoteFields = marcRecord.getFields().stream()
                .filter(field -> field.getName().matches(EXTENDABLE_NOTE_FIELDS)).collect(Collectors.toList());
        final List<MarcField> currentNoteFields = curRecord.getFields().stream()
                .filter(field -> field.getName().matches(EXTENDABLE_NOTE_FIELDS)).collect(Collectors.toList());
        final List<MarcField> currentNoteFieldsNoAmpersand = new ArrayList<>();
        currentNoteFields.forEach(f -> currentNoteFieldsNoAmpersand.add(new MarcField(f)));
        currentNoteFieldsNoAmpersand.forEach(f -> new MarcFieldWriter(f).removeSubfield("&"));

        if (marcFieldsEqualsIgnoreAmpersand(newNoteFields, currentNoteFields)) {
            result.getFields().addAll(currentNoteFields);
        } else {
            for (MarcField newNoteField : newNoteFields) {
                MarcField newNoteFieldNoAmpersand = new MarcField(newNoteField);
                new MarcFieldWriter(newNoteFieldNoAmpersand).removeSubfield("&");
                if (currentNoteFieldsNoAmpersand.contains(newNoteFieldNoAmpersand)) {
                    result.getFields().add(newNoteField);
                } else {
                    for (MarcField currentNoteField : currentNoteFields) {
                        if (newNoteField.getName().equals(currentNoteField.getName())) {
                            final MarcFieldReader currentNoteFieldReader = new MarcFieldReader(currentNoteField);
                            // No *& means the field is owned by DBC
                            if (!currentNoteFieldReader.hasSubfield("&") || !currentNoteFieldReader.getValue("&").startsWith("7")) {
                                final String msg = String.format(messages.getString("update.dbc.record.dbc.notes"), newNoteField.getName());
                                // Business exception which means we don't want the error in the errorlog, so only log as info
                                LOGGER.info("Unable to create sub actions due to an error: {}", msg);
                                throw new UpdateException(msg);
                            }
                        }
                    }
                    result.getFields().add(copyWithNewAmpersand(newNoteField, groupId));
                }
            }
        }
    }

    void addCatalogField(MarcRecord result, MarcRecord marcRecord, MarcRecord curRecord, String groupId ) throws UpdateException {
        // Technically "there can be only one" of this field - maybe we can murder the List ? Nah, we keep the list of one
        final List<MarcField> newCatalogCodeFields = marcRecord.getFields().stream()
                .filter(field -> field.getName().matches(CATALOGUE_CODE_FIELD)).collect(Collectors.toList());
        final List<MarcField> currentCatalogCodeFields = curRecord.getFields().stream()
                .filter(field -> field.getName().matches(CATALOGUE_CODE_FIELD)).collect(Collectors.toList());
        List<MarcField> newFields = createCatalogField(newCatalogCodeFields, currentCatalogCodeFields, groupId);
        new MarcRecordWriter(result).removeField("032");
        result.getFields().addAll(newFields);
    }

    /**
     *
     * @param marcRecord        The incoming record
     * @param groupId           The library number for the updating library
     * @return                  The (maybe) corrected record
     * @throws UpdateException  A failure is found in the incoming record
     * @throws VipCoreException A problem was met when trying to get information from the vip system
     * @throws UnsupportedEncodingException Very rare error - there are something serious wrong since this should be found earlier.
     * This function handles adding of library data to a dbc record. The subject, notes and classification things are pretty simple since
     * fields are added with an *& subfield with the owner in it.
     * Adding, modifying or deleting an OVE code is a bit more complicated since there can be only on field 032. Libraries can only modify a
     * subfield 032x that contains an OVE code (subfield & will be modified automatically in such cases) - all other subfields are forbidden to modify.
     * The library must also have the vip right REGIONAL_OBLIGATIONS set to make such an update.
     * DBC on the other hand may correct all subfields including one that contain an OVE code.
     * In case of deletion, the & subfield will be removed.
     * Some comments on subfields in 032 :
     * If a local record (001b=groupId) then all *x subfields and catalog codes in those are allowed - legal codes will be handled in the ordinary validation
     * If a common record owned by a library (996a=groupId) the same rules are as above.
     * If a common record is owned by DBC (996a=DBC) then only 032xOVE<weekcode> must be touched.
     * Use of 032a is strictly forbidden except for DBC.
     */
    MarcRecord recordDataForRawRepo(MarcRecord marcRecord, String groupId) throws UpdateException, VipCoreException, UnsupportedEncodingException {
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);
        final String recId = reader.getRecordId();
        if (!rawRepo.recordExists(recId, RawRepo.COMMON_AGENCY)) {
            LOGGER.info("No existing record - returning same record");
            if (!"DBC".equals(reader.getValue("996", "a")) && reader.hasField("032")) {
                if (!vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.REGIONAL_OBLIGATIONS)) {
                    final String msg = messages.getString("update.library.record.catalog.codes.not.cb");
                    LOGGER.error("Unable to create sub actions due to an error: {}", msg);
                    throw new UpdateException(msg);
                }
                validateCatalogCodes(marcRecord);
                addCatalogField(marcRecord, marcRecord, new MarcRecord(), groupId);
            }
            return marcRecord;
        }
        final MarcRecord curRecord = RecordContentTransformer.decodeRecord(rawRepo.fetchRecord(recId, RawRepo.COMMON_AGENCY).getContent());
        final MarcRecordReader curReader = new MarcRecordReader(curRecord);
        if (!"DBC".equals(curReader.getValue("996", "a"))) {
            LOGGER.info("Record is decentral - returning same record");
            validateCatalogCodes(marcRecord);
            return marcRecord;
        }

        // Other libraries are only allowed to enrich note and subject fields if the record is in production, i.e. has a weekcode in the record
        // However that will be verified by AuthenticateRecordAction so at this point we assume everything is fine

        LOGGER.info("Checking for altered classifications for disputas type material");
        if (curReader.hasValue("008", "d", "m") &&
                vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_ADD_DK5_TO_PHD_ALLOWED) &&
                !canChangeClassificationForDisputas(reader)) {
            final String msg = messages.getString("update.dbc.record.652");
            LOGGER.error("Unable to create sub actions due to an error: {}", msg);
            throw new UpdateException(msg);
        }
        final MarcRecord result = new MarcRecord();
        LOGGER.info("Record exists and is common national record - setting extension fields");

        String extendableFieldsRx = createExtendableFieldsRx(groupId);

        if (extendableFieldsRx.isEmpty()) {
            LOGGER.info("Agency {} doesn't have permission to edit notes, subject or OVE fields - returning same record", groupId);
            return marcRecord;
        }
        extendableFieldsRx += "|" + CLASSIFICATION_FIELDS;
        LOGGER.info("Extendable fields: {} ", extendableFieldsRx);

        // Start by handling all the not-note/subject/OVE fields in the existing record
        for (MarcField curField : curRecord.getFields()) {
            if (!curField.getName().matches(extendableFieldsRx)) {
                final MarcField fieldClone = new MarcField(curField);
                result.getFields().add(fieldClone);
            }
        }

        // Handling field 032
        if (vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.REGIONAL_OBLIGATIONS)) {
            addCatalogField(result, marcRecord, curRecord, groupId);
        }

        // Handle note fields. These fields are handled individually
        // Each field can only be changed if either the field is missing in the existing record or if the field is owned
        // by another FBS library
        if (vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)) {
            addNoteFields(result, marcRecord, curRecord, groupId);
        }

        // Handle controlled subject fields. These fields are handled as a collection.
        // Fields are allowed to be updated if either there isn't a *& subfield or the value of *& starts with 7 (folkebibliotek)
        // If there are changes all fields will have *& updated to be the current library
        if (vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)) {
            addSubjectFields(result, marcRecord, curRecord, groupId);
        }

        // Handle classification field. Classification is only allowed to be updated if the existing record contains "uden klassemærke".
        // At this point in the code we know that either the field is unchanged or the library is allowed to update the field,
        // so we only have to check if the classification is changed or not.
        final List<MarcField> new652Fields = marcRecord.getFields().stream()
                .filter(field -> field.getName().matches(CLASSIFICATION_FIELDS)).collect(Collectors.toList());
        final List<MarcField> current652Fields = curRecord.getFields().stream()
                .filter(field -> field.getName().matches(CLASSIFICATION_FIELDS)).collect(Collectors.toList());
        if (marcFieldsEqualsIgnoreAmpersand(new652Fields, current652Fields)) {
            result.getFields().addAll(current652Fields);
        } else {
            for (MarcField new652Field : new652Fields) {
                result.getFields().add(copyWithNewAmpersand(new652Field, groupId));
            }
        }

        new MarcRecordWriter(result).sort();

        return result;
    }

    private void validateCatalogCodes(MarcRecord record) throws UpdateException {
        final List<MarcField> newCatalogCodeFields = record.getFields().stream()
                .filter(field -> field.getName().matches(CATALOGUE_CODE_FIELD)).collect(Collectors.toList());
        for (MarcField field : newCatalogCodeFields) {
            for (MarcSubField subField : field.getSubfields()) {
                if (subField.getName().equals("a")) {
                    final String msg = messages.getString("update.library.record.catalog.codes.x.only");
                    LOGGER.error("Unable to create sub actions due to an error: {}", msg);
                    throw new UpdateException(msg);
                }
            }
        }

    }

    private String getOveCode(MarcField newCatalogField) {
        for (MarcSubField subField : newCatalogField.getSubfields()) {
            if ("x".equals(subField.getName()) && subField.getValue().startsWith("OVE")) return subField.getValue();
        }
        return "";
    }

    List<MarcSubField> updateOveAndAmp(MarcField field, String groupId, String oveCode) {
        List<MarcSubField> newList = new ArrayList<>();
        List<MarcSubField> subs = field.getSubfields();
        for (MarcSubField s1 : subs) {
            if ("&".equals(s1.getName())) continue;
            if ("x".equals(s1.getName()) && s1.getValue().startsWith("OVE")) continue;
            newList.add(s1);
        }
        if (!oveCode.isEmpty()) {
            newList.add(new MarcSubField("&", groupId));
            newList.add(new MarcSubField("x", oveCode));
        }
        return newList;

    }

    List<MarcSubField> clean(List<MarcSubField> subs, boolean all) {
        List<MarcSubField> newList = new ArrayList<>();
        if (all) {
            newList.addAll(subs);
        } else {
            for (MarcSubField s1 : subs) {
                if ("&".equals(s1.getName())) continue;
                if ("x".equals(s1.getName()) && s1.getValue().startsWith("OVE")) continue;
                newList.add(s1);
            }
        }
        return newList;
    }
    /**
     * @param l1  the field 032 in the incoming record
     * @param l2  the field 032 in the existing record
     * @param all if true, all subfields will be matched otherwise *& and *x with OVE
     * @return    true if l1 and l2 contains the same no matter order
     */
    boolean compareCatalogSubFields(MarcField l1, MarcField l2, boolean all) {
        Collection<MarcSubField> s1 = clean(l1.getSubfields(), all);
        Collection<MarcSubField> s2 = clean(l2.getSubfields(), all);
        for (MarcSubField s : s1) {
            if (s2.contains(s)) {
                s2.remove(s);
            } else {
                return false;
            }
        }
        return s2.isEmpty();
    }

    /**
     *
     * @param newCatalogCodeFields      field 032 in incoming record - may be empty
     * @param currentCatalogCodeFields  field 032 in existing record - may also be empty
     * @return                          the new field 032
     * @throws UpdateException          some subfields not containing an OVE code were removed or added which is strictly forbidden
     * This function handle the case where a library attempts to add an OVE code to a DBC owned record. It also expects that there is only one field 032
     * Cases are :
     * 1: If the two fields are totally equal, then return one of them.
     * 2: If the new field is empty, then old, if it exists, may not contain subfields "a" and "x" except for "OVE", that will be an error - if it doesn't, the field
     * will be deleted
     * 3: If the old field is empty, then new field, if it exists, may not contain subfields "a" and "x" without "OVE", that will be an error. If it contains *xOVE then
     * add *& and *xOVE
     * 4: If there are both an old and a new 032 field then it should be checked that there only are difference due to *& and "OVE", that is,
     * if the fields only differ on *& and *xOVE then those in the current shall be removed, the OVE code added and a *& with groupId should be added
     */
    private List<MarcField> createCatalogField(List<MarcField> newCatalogCodeFields, List<MarcField> currentCatalogCodeFields, String groupId) throws UpdateException {
        List <MarcField> resultCatalogCodeFields = new ArrayList<>();

        MarcField currentWork;
        currentWork = currentCatalogCodeFields.isEmpty() ? new MarcField() : currentCatalogCodeFields.get(0);
        MarcField newWork;
        newWork = newCatalogCodeFields.isEmpty() ? new MarcField() : newCatalogCodeFields.get(0);

        // Point 1
        if (compareCatalogSubFields(newWork, currentWork, true)) {
            return currentCatalogCodeFields;
        }

        // Point 2
        if (newCatalogCodeFields.isEmpty()) {
            // No need to check if current is empty - that case is handled in point 1
            if (clean(currentWork.getSubfields(), true).isEmpty()) {
                // remove any *& and OVE
                return newCatalogCodeFields;
            } else {
                final String msg = messages.getString("update.dbc.record.dbc.catalog.codes");
                LOGGER.error("Unable to create sub actions due to an error: {}", msg);
                throw new UpdateException(msg);
            }
        }

        // Point 3
        // * 3: If the old field is empty, then new field, if it exists, may not contain subfields "a" and "x" without "OVE", that will be an error. If it contains *xOVE then add necessary
        if (currentCatalogCodeFields.isEmpty()) {
            if (clean(newWork.getSubfields(), false).isEmpty()) {
                String oveCode = getOveCode(newWork);
                newWork.setSubfields(updateOveAndAmp(newWork, groupId, oveCode));
                resultCatalogCodeFields.add(newWork);
                return resultCatalogCodeFields;
            } else {
                final String msg = messages.getString("update.dbc.record.dbc.catalog.codes");
                LOGGER.error("Unable to create sub actions due to an error: {}", msg);
                throw new UpdateException(msg);
            }
        }

        // Point 4
        // * 4: If there are both an old and a new 032 field then it should be checked that there only are difference due to *& and "OVE", that is,
        // * if the fields only differ on *& and *xOVE then those in the current shall be removed, the OVE code added and a *& with groupId should be added
        if (compareCatalogSubFields(newWork, currentWork, false)) {
            String oveCode = getOveCode(newWork);
            newWork.setSubfields(updateOveAndAmp(newWork, groupId, oveCode));
            resultCatalogCodeFields.add(newWork);
            return resultCatalogCodeFields;
        } else {
            final String msg = messages.getString("update.dbc.record.dbc.catalog.codes");
            LOGGER.error("Unable to create sub actions due to an error: {}", msg);
            throw new UpdateException(msg);
        }

    }

    private MarcField copyWithNewAmpersand(MarcField marcField, String groupId) {
        final MarcField fieldClone = new MarcField(marcField);
        new MarcFieldWriter(fieldClone).removeSubfield("&");
        fieldClone.getSubfields().add(0, new MarcSubField("&", groupId));
        return fieldClone;
    }

    private boolean marcFieldsEqualsIgnoreAmpersand(List<MarcField> l1, List<MarcField> l2) {
        final List<MarcField> l1Clone = new ArrayList<>();
        final List<MarcField> l2Clone = new ArrayList<>();

        l1.forEach(f -> l1Clone.add(new MarcField(f)));
        l1Clone.forEach(f -> new MarcFieldWriter(f).removeSubfield("&"));

        l2.forEach(f -> l2Clone.add(new MarcField(f)));
        l2Clone.forEach(f -> new MarcFieldWriter(f).removeSubfield("&"));

        return l1.equals(l2);
    }

    boolean canChangeClassificationForDisputas(MarcRecordReader reader) throws UpdateException {
        try {
            final String recordId = reader.getRecordId();
            if (rawRepo.recordExists(recordId, RawRepo.COMMON_AGENCY)) {
                final MarcRecord currentRecord = RecordContentTransformer.decodeRecord(rawRepo.fetchRecord(recordId, RawRepo.COMMON_AGENCY).getContent());
                final MarcRecordReader currentRecordReader = new MarcRecordReader(currentRecord);

                final String new652 = reader.getValue("652", "m");
                final String current652 = currentRecordReader.getValue("652", "m");

                if (current652 != null && new652 != null && !new652.equalsIgnoreCase(current652)) {
                    return current652.equalsIgnoreCase(NO_CLASSIFICATION) &&
                            currentRecordReader.isDBCRecord() &&
                            currentRecordReader.hasValue("008", "d", "m");

                }
            }
        } catch (UnsupportedEncodingException ex) {
            LOGGER.error(ex.getMessage(), ex);
            throw new UpdateException(ex.getMessage(), ex);
        }

        return true;
    }

    /**
     * This function checks whether the input field exist and is identical with a field in the record
     *
     * @param field      field to compare
     * @param marcRecord record to compare the field in
     * @return boolean True if the record has a field that matches field
     */
    boolean isFieldChangedInOtherRecord(MarcField field, MarcRecord marcRecord) {
        final MarcRecord cloneMarcRecord = new MarcRecord(marcRecord);
        final MarcRecordReader cloneMarcRecordReader = new MarcRecordReader(cloneMarcRecord);
        final MarcRecordWriter cloneMarcRecordWriter = new MarcRecordWriter(cloneMarcRecord);
        final MarcFieldReader fieldReader = new MarcFieldReader(field);

        if (field.getName().equals("001")) {
            if (fieldReader.hasSubfield("c")) {
                cloneMarcRecordWriter.addOrReplaceSubfield("001", "c", fieldReader.getValue("c"));
            }

            if (fieldReader.hasSubfield("d")) {
                cloneMarcRecordWriter.addOrReplaceSubfield("001", "d", fieldReader.getValue("d"));
            }
        }

        // Handle field which has subfields from expanded authority records which is not allowed in the template
        if (Arrays.asList("900", "910", "945", "952").contains(field.getName())) {
            for (MarcField cloneField : cloneMarcRecordReader.getFieldAll(field.getName())) {
                final MarcFieldWriter cloneFieldWriter = new MarcFieldWriter(cloneField);

                for (String subfieldName : Arrays.asList("w", "x", "z")) {
                    if (fieldReader.hasSubfield(subfieldName)) {
                        cloneFieldWriter.addOrReplaceSubfield(subfieldName, fieldReader.getValue(subfieldName));
                    } else {
                        cloneFieldWriter.removeSubfield(subfieldName);
                    }
                }
            }
        }

        for (MarcField cf : cloneMarcRecordReader.getFieldAll(field.getName())) {
            if (cf.equals(field)) {
                return false;
            }
        }

        return true;
    }

    /**
     * This function returns a list of allowed extendable fields as a regex string
     *
     * @param agencyId AgencyId of the library to check for
     * @return String containing fields which can be used in regex
     * @throws VipCoreException In case VipCore throws exception
     */
    String createExtendableFieldsRx(String agencyId) throws VipCoreException {
        String extendableFields = "";

        if (vipCoreService.hasFeature(agencyId, VipCoreLibraryRulesConnector.Rule.REGIONAL_OBLIGATIONS)) {
            extendableFields += CATALOGUE_CODE_FIELD;
        }

        if (vipCoreService.hasFeature(agencyId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)) {
            if (!extendableFields.isEmpty()) {
                extendableFields += "|";
            }
            extendableFields += EXTENDABLE_NOTE_FIELDS;
        }

        if (vipCoreService.hasFeature(agencyId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)) {
            if (!extendableFields.isEmpty()) {
                extendableFields += "|";
            }
            extendableFields += EXTENDABLE_CONTROLLED_SUBJECT_FIELDS;
            extendableFields += "|";
            extendableFields += EXTENDABLE_SUBJECT_FIELDS;
        }

        LOGGER.info("Agency {} can change the following fields in a national common record: {}", agencyId, extendableFields);

        return extendableFields;
    }

    /**
     * Checks if this record is a national common record.
     *
     * @param marcRecord Record.
     * @return {boolean} True / False.
     */
    public boolean isPublishedDBCRecord(MarcRecord marcRecord) throws UpdateException {
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);

        final String recordType = reader.getValue("004", "a");

        if (Arrays.asList("h", "s").contains(recordType)) {
            try {
                final RecordId recordId = new RecordId(reader.getRecordId(), reader.getAgencyIdAsInt());

                final Set<RecordId> children = rawRepo.children(recordId);
                // Children includes both enrichment and other record types, e.g. article. Underlying records of a 870970
                // record will always have agency id 870970 therefor we filter records with that agency id
                final Set<RecordId> commonChildren = children.stream().filter(r -> r.getAgencyId() == RawRepo.COMMON_AGENCY).collect(Collectors.toSet());

                for (RecordId child : commonChildren) {
                    final Record childRecord = rawRepo.fetchRecord(child.getBibliographicRecordId(), child.getAgencyId());
                    final MarcRecord childMarcRecord = RecordContentTransformer.decodeRecord(childRecord.getContent());

                    if (isPublishedDBCRecord(childMarcRecord)) {
                        return true;
                    }
                }
                return false;
            } catch (UnsupportedEncodingException e) {
                throw new UpdateException("Exception when decoding child records for {}", e);
            }
        } else {
            return reader.hasValue("996", "a", "DBC") &&
                    CatalogExtractionCode.isPublishedIgnoreCatalogCodes(marcRecord);
        }
    }

    /**
     * Validate whether the record is legal in regard to note and subject fields and the permissions of the group
     *
     * @param marcRecord The incoming record
     * @param groupId    GroupId of the requester
     * @return List of validation errors (ok returns empty list)
     * @throws UpdateException if communication with RawRepo or OpenAgency fails
     */
    public List<MessageEntryDTO> authenticateCommonRecordExtraFields(MarcRecord marcRecord, String groupId) throws UpdateException, VipCoreException {
        final List<MessageEntryDTO> result = new ArrayList<>();
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);

        final ResourceBundle resourceBundle = ResourceBundles.getBundle("messages");

        final String recId = reader.getRecordId();
        if (!rawRepo.recordExists(recId, RawRepo.COMMON_AGENCY)) {
            return result;
        }
        MarcRecord curRecord;
        try {
            final Map<String, MarcRecord> curRecordCollection = rawRepo.fetchRecordCollection(recId, RawRepo.COMMON_AGENCY);
            curRecord = ExpandCommonMarcRecord.expandMarcRecord(curRecordCollection, recId);
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("curRecord:\n{}", LogUtils.base64Encode(curRecord));
            }
        } catch (RawRepoException e) {
            throw new UpdateException("Exception while loading current record", e);
        }
        final MarcRecordWriter curWriter = new MarcRecordWriter(curRecord);
        final MarcRecordReader curReader = new MarcRecordReader(curRecord);

        curWriter.addOrReplaceSubfield("001", "b", reader.getAgencyId());
        if (!isPublishedDBCRecord(curRecord)) {
            return result;
        }

        if (!vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)) {
            LOGGER.info("AgencyId {} does not have feature AUTH_COMMON_NOTES in vipcore - checking for changed note fields", groupId);
            // Check if all fields in the incoming record are in the existing record
            for (MarcField field : marcRecord.getFields()) {
                if (field.getName().matches(EXTENDABLE_NOTE_FIELDS) && isFieldChangedInOtherRecord(field, curRecord)) {
                    final String message = String.format(resourceBundle.getString("notes.subjects.edit.field.error"), groupId, field.getName(), recId);
                    result.add(createMessageDTO(message));
                }
            }
            // Check if all fields in the existing record are in the incoming record
            for (MarcField field : curRecord.getFields()) {
                if (field.getName().matches(EXTENDABLE_NOTE_FIELDS) && isFieldChangedInOtherRecord(field, marcRecord)) {
                    final String fieldName = field.getName();
                    if (curReader.getFieldAll(fieldName).size() != reader.getFieldAll(fieldName).size()) {
                        final String message = String.format(resourceBundle.getString("notes.subjects.delete.field.error"), groupId, fieldName, recId);
                        result.add(createMessageDTO(message));
                    }
                }
            }
        }

        if (!vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)) {
            LOGGER.info("AgencyId {} does not have feature AUTH_COMMON_SUBJECTS in vipcore - checking for changed note fields", groupId);
            // Check if all fields in the incoming record are in the existing record
            for (MarcField field : marcRecord.getFields()) {
                if (field.getName().matches(EXTENDABLE_CONTROLLED_SUBJECT_FIELDS) && isFieldChangedInOtherRecord(field, curRecord)) {
                    final String message = String.format(resourceBundle.getString("notes.subjects.edit.field.error"), groupId, field.getName(), recId);
                    result.add(createMessageDTO(message));
                }
            }
            // Check if all fields in the existing record are in the incoming record
            for (MarcField field : curRecord.getFields()) {
                if (field.getName().matches(EXTENDABLE_CONTROLLED_SUBJECT_FIELDS) && isFieldChangedInOtherRecord(field, marcRecord)) {
                    final String fieldName = field.getName();
                    if (curReader.getFieldAll(fieldName).size() != reader.getFieldAll(fieldName).size()) {
                        final String message = String.format(resourceBundle.getString("notes.subjects.delete.field.error"), groupId, fieldName, recId);
                        result.add(createMessageDTO(message));
                    }
                }
            }
        }

        if (vipCoreService.getLibraryGroup(groupId).isFBS() && !CatalogExtractionCode.isPublishedIgnoreCatalogCodes(marcRecord)) {
            final String message = String.format(resourceBundle.getString("notes.subjects.not.in.production"), groupId, recId);
            result.add(createMessageDTO(message));
        }

        return result;
    }

    private MessageEntryDTO createMessageDTO(String message) {
        MessageEntryDTO result = new MessageEntryDTO();

        result.setMessage(message);
        result.setType(TypeEnumDTO.ERROR);

        return result;
    }

    public MarcRecord collapse(MarcRecord marcRecord, MarcRecord currentRecord, String groupId, boolean isNationalCommonRecord) throws VipCoreException {
        final MarcRecord collapsedRecord = new MarcRecord(currentRecord);
        final List<String> fieldsToCopy = new ArrayList<>();

        if (isNationalCommonRecord) {
            fieldsToCopy.addAll(Arrays.asList(createExtendableFieldsRx(groupId).split("\\|")));
        } else {
            fieldsToCopy.addAll(Arrays.asList(EXTENDABLE_NOTE_FIELDS.split("\\|")));
            fieldsToCopy.addAll(Arrays.asList(EXTENDABLE_SUBJECT_FIELDS.split("\\|")));
        }

        // We need to copy 996 from the incoming record as well, as that field could have been modified in an earlier action
        // But because the Arrays.asList returns an immutable list we need to copy the content to another list.
        fieldsToCopy.add("245");
        fieldsToCopy.add("521");
        fieldsToCopy.add("652");
        fieldsToCopy.add("996");
        final MarcRecordWriter curWriter = new MarcRecordWriter(collapsedRecord);
        curWriter.removeFields(fieldsToCopy);
        curWriter.copyFieldsFromRecord(fieldsToCopy, marcRecord);

        return collapsedRecord;
    }

}
