/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

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

    static final String EXTENDABLE_NOTE_FIELDS = "504|530";
    static final String EXTENDABLE_CONTROLLED_SUBJECT_FIELDS = "600|610|630|666";
    static final String EXTENDABLE_SUBJECT_FIELDS = "631|664|665";
    private static final String EXTENDABLE_SUBJECT_FIELDS_NO_AMPERSAND = "652";
    private static final String NO_CLASSIFICATION = "uden klassemærke";

    public NoteAndSubjectExtensionsHandler(VipCoreService vipCoreService, RawRepo rawRepo, ResourceBundle messages) {
        this.vipCoreService = vipCoreService;
        this.rawRepo = rawRepo;
        this.messages = messages;
    }

    MarcRecord recordDataForRawRepo(MarcRecord marcRecord, String groupId) throws UpdateException, VipCoreException, UnsupportedEncodingException {
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);
        final String recId = reader.getRecordId();
        if (!rawRepo.recordExists(recId, RawRepo.COMMON_AGENCY)) {
            LOGGER.info("No existing record - returning same record");
            return marcRecord;
        }
        final MarcRecord curRecord = RecordContentTransformer.decodeRecord(rawRepo.fetchRecord(recId, RawRepo.COMMON_AGENCY).getContent());
        final MarcRecordReader curReader = new MarcRecordReader(curRecord);
        if (!"DBC".equals(curReader.getValue("996", "a"))) {
            LOGGER.info("Record is decentral - returning same record");
            return marcRecord;
        }

        // Other libraries are only allowed to enrich note and subject fields if the record is in production, i.e. has a weekcode in the post
        // However that has already been verified by AuthenticateRecordAction so at this point we assume everything is fine

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
            LOGGER.info("Agency {} doesn't have permission to edit notes or subject fields - returning same record", groupId);
            return marcRecord;
        }
        extendableFieldsRx += "|" + EXTENDABLE_SUBJECT_FIELDS_NO_AMPERSAND;
        LOGGER.info("Extendable fields: {} ", extendableFieldsRx);

        // Start by handling all the not-note/subject fields in the existing record
        for (MarcField curField : curRecord.getFields()) {
            final MarcField fieldClone = new MarcField(curField);
            if (!fieldClone.getName().matches(extendableFieldsRx)) {
                result.getFields().add(fieldClone);
            }
        }

        // Handle note fields
        final List<MarcField> newNoteFields = marcRecord.getFields().stream()
                .filter(field -> field.getName().matches(EXTENDABLE_NOTE_FIELDS)).collect(Collectors.toList());
        final List<MarcField> currentNoteFields = curRecord.getFields().stream()
                .filter(field -> field.getName().matches(EXTENDABLE_NOTE_FIELDS)).collect(Collectors.toList());

        if (newNoteFields.equals(currentNoteFields)) {
            result.getFields().addAll(currentNoteFields);
        } else {
            for (MarcField newNoteField : newNoteFields) {
                if (currentNoteFields.contains(newNoteField)) {
                    result.getFields().add(newNoteField);
                } else {
                    for (MarcField currentNoteField : currentNoteFields) {
                        if (newNoteField.getName().equals(currentNoteField.getName())) {
                            final MarcFieldReader currentNoteFieldReader = new MarcFieldReader(currentNoteField);
                            if (!currentNoteFieldReader.hasSubfield("&") || !currentNoteFieldReader.getValue("&").startsWith("7")) {
                                final String msg = String.format(messages.getString("update.dbc.record.dbc.notes"), newNoteField.getName());
                                LOGGER.error("Unable to create sub actions due to an error: {}", msg);
                                throw new UpdateException(msg);
                            }
                        }
                    }

                    result.getFields().add(copyWithNewAmpersand(newNoteField, groupId));
                }
            }
        }

        // Handle controlled subject fields.
        // Fields are allowed to be updated if either there isn't a *& subfield or the value of *& starts with 7 (folkebibliotek)
        // If there are changes all fields will have *& updated to be the current library
        final List<MarcField> newControlledSubjectFields = marcRecord.getFields().stream()
                .filter(field -> field.getName().matches(EXTENDABLE_CONTROLLED_SUBJECT_FIELDS)).collect(Collectors.toList());
        final List<MarcField> currentControlledSubjectFields = curRecord.getFields().stream()
                .filter(field -> field.getName().matches(EXTENDABLE_CONTROLLED_SUBJECT_FIELDS)).collect(Collectors.toList());

        if (marcFieldsEqualsIgnoreAmpersand(newControlledSubjectFields, currentControlledSubjectFields)) {
            // Controlled subject field are identical, so just copy the existing ones over
            result.getFields().addAll(currentControlledSubjectFields);
        } else {
            for (MarcField currentControlledSubjectField : currentControlledSubjectFields) {
                final MarcFieldReader currentControlledSubjectFieldReader = new MarcFieldReader(currentControlledSubjectField);
                if (currentControlledSubjectFieldReader.hasSubfield("&") && !currentControlledSubjectFieldReader.getValue("&").startsWith("7")) {
                    final String msg = messages.getString("update.dbc.record.dbc.subjects");
                    LOGGER.error("Unable to create sub actions due to an error: {}", msg);
                    throw new UpdateException(msg);
                }
            }
            // Now we know the fields can be overwritten - same owner is set on all controlled subject fields
            for (MarcField newControlledSubjectField : newControlledSubjectFields) {
                result.getFields().add(copyWithNewAmpersand(newControlledSubjectField, groupId));
            }
        }

        // Handle (non-controlled) subject fields

        final List<MarcField> newSubjectFields = marcRecord.getFields().stream()
                .filter(field -> field.getName().matches(EXTENDABLE_SUBJECT_FIELDS)).collect(Collectors.toList());
        final List<MarcField> currentSubjectFields = curRecord.getFields().stream()
                .filter(field -> field.getName().matches(EXTENDABLE_SUBJECT_FIELDS)).collect(Collectors.toList());

        // This is handling that records received from Cicero won't contain *&
        final List<MarcField> currentSubjectFieldsCloned = new ArrayList<>();
        currentSubjectFields.forEach(f -> currentSubjectFieldsCloned.add(new MarcField(f)));
        currentSubjectFieldsCloned.forEach(f -> new MarcFieldWriter(f).removeSubfield("&"));
        for (MarcField newSubjectField : newSubjectFields) {
            final MarcField newSubjectFieldClone = new MarcField(newSubjectField);
            new MarcFieldWriter(newSubjectFieldClone).removeSubfield("&");
            boolean addNewSubjectField = true;
            for (int i = 0; i < currentSubjectFieldsCloned.size(); i++) {
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

        // Handle field 652
        final List<MarcField> new652Fields = marcRecord.getFields().stream()
                .filter(field -> field.getName().matches(EXTENDABLE_SUBJECT_FIELDS_NO_AMPERSAND)).collect(Collectors.toList());
        final List<MarcField> current652Fields = curRecord.getFields().stream()
                .filter(field -> field.getName().matches(EXTENDABLE_SUBJECT_FIELDS_NO_AMPERSAND)).collect(Collectors.toList());

        final String value652m = curReader.getValue("652", "m");
        if (value652m != null && value652m.equalsIgnoreCase(NO_CLASSIFICATION)) {
            for (MarcField new652Field : new652Fields) {
                result.getFields().add(copyWithNewAmpersand(new652Field, groupId));
            }
        } else {
            if (!new652Fields.equals(current652Fields)) {
                final String msg = messages.getString("update.dbc.record.dbc.subjects");
                LOGGER.error("Unable to create sub actions due to an error: {}", msg);
                throw new UpdateException(msg);
            } else {
                result.getFields().addAll(current652Fields);
            }
        }

        new MarcRecordWriter(result).sort();

        return result;
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

        if (vipCoreService.hasFeature(agencyId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)) {
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
                for (RecordId child : children) {
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
                    CatalogExtractionCode.isPublished(marcRecord);
        }
    }

    /**
     * Validate whether the record is legal in regards to note and subject fields and the permissions of the group
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

        if (vipCoreService.getLibraryGroup(groupId).isFBS() && !CatalogExtractionCode.isPublished(marcRecord)) {
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
