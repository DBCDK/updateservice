/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

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

public class NoteAndSubjectExtensionsHandler {
    private static final XLogger logger = XLoggerFactory.getXLogger(NoteAndSubjectExtensionsHandler.class);
    private VipCoreService vipCoreService;
    private RawRepo rawRepo;
    private ResourceBundle messages;

    static final String EXTENDABLE_NOTE_FIELDS = "504|530";
    static final String EXTENDABLE_SUBJECT_FIELDS = "600|610|630|631|666";
    private static final String EXTENDABLE_SUBJECT_FIELDS_NO_AMPERSAND = "652";
    private static final String NO_CLASSIFICATION = "uden klassemærke";

    public NoteAndSubjectExtensionsHandler(VipCoreService vipCoreService, RawRepo rawRepo, ResourceBundle messages) {
        this.vipCoreService = vipCoreService;
        this.rawRepo = rawRepo;
        this.messages = messages;
    }

    MarcRecord recordDataForRawRepo(MarcRecord record, String groupId) throws UpdateException, VipCoreException, UnsupportedEncodingException {
        logger.entry(record, groupId);

        try {
            final MarcRecordReader reader = new MarcRecordReader(record);
            final String recId = reader.getRecordId();
            if (!rawRepo.recordExists(recId, RawRepo.COMMON_AGENCY)) {
                logger.info("No existing record - returning same record");
                return record;
            }
            final MarcRecord curRecord = RecordContentTransformer.decodeRecord(rawRepo.fetchRecord(recId, RawRepo.COMMON_AGENCY).getContent());
            final MarcRecordReader curReader = new MarcRecordReader(curRecord);
            if (!isNationalCommonRecord(curRecord)) {
                logger.info("Record is not national common record - returning same record");
                return record;
            }
            logger.info("Checking for altered classifications for disputas type material");
            if (curReader.hasValue("008", "d", "m") &&
                    vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_ADD_DK5_TO_PHD_ALLOWED) &&
                    !canChangeClassificationForDisputas(reader)) {
                final String msg = messages.getString("update.dbc.record.652");
                logger.error("Unable to create sub actions due to an error: {}", msg);
                throw new UpdateException(msg);
            }
            final MarcRecord result = new MarcRecord();
            logger.info("Record exists and is common national record - setting extension fields");

            String extendableFieldsRx = createExtendableFieldsRx(groupId);

            if (extendableFieldsRx.isEmpty()) {
                logger.info("Agency {} doesn't have permission to edit notes or subject fields - returning same record", groupId);
                return record;
            }
            extendableFieldsRx += "|" + EXTENDABLE_SUBJECT_FIELDS_NO_AMPERSAND;
            logger.info("Extendable fields: {} ", extendableFieldsRx);

            // Validate that national common record doesn't have any note/subject fields without '&'
            // When a record is changed to a national common record all note and subject fields are removed
            // So the combination of national common record and DBC note and subject fields is an invalid state
            // The cause of that state is often that the record is marked national common record by a mistake.
            for (MarcField curField : curRecord.getFields()) {
                if (curField.getName().matches(extendableFieldsRx) && !"652".equals(curField.getName())) {
                    MarcFieldReader curFieldReader = new MarcFieldReader(curField);
                    if (curFieldReader.hasSubfield("&") && (curFieldReader.getValue("&").isEmpty() || "1".equals(curFieldReader.getValue("&")))) {
                        final String msg = messages.getString("update.dbc.record.dbc.notes");
                        logger.error("Unable to create sub actions due to an error: {}", msg);
                        throw new UpdateException(msg);
                    }
                }
            }

            // Start by handling all the not-note/subject fields in the existing record
            for (MarcField curField : curRecord.getFields()) {
                final MarcField fieldClone = new MarcField(curField);
                if (!fieldClone.getName().matches(extendableFieldsRx)) {
                    result.getFields().add(fieldClone);
                }
            }

            // Handle note/subject fields in the incoming record
            for (MarcField field : record.getFields()) {
                final MarcField fieldClone = new MarcField(field);
                if (fieldClone.getName().matches(extendableFieldsRx)) {
                    if (!"652".equals(fieldClone.getName())) {
                        // All other note/subject fields than 652 must have ampersand subfield to indicate the current owner of that field
                        // The & subfield must be placed as the first subfield. Instead of sorting the list of subfield we instead
                        // first remove any existing & subfield and then add it as the first element.
                        new MarcFieldWriter(fieldClone).removeSubfield("&");
                        fieldClone.getSubfields().add(0, new MarcSubField("&", groupId));
                    }

                    result.getFields().add(fieldClone);
                }
            }

            new MarcRecordWriter(result).sort();

            return result;
        } finally {
            logger.exit();
        }
    }

    boolean canChangeClassificationForDisputas(MarcRecordReader reader) throws UpdateException {
        logger.entry();

        try {
            String recordId = reader.getRecordId();
            if (rawRepo.recordExists(recordId, RawRepo.COMMON_AGENCY)) {
                MarcRecord currentRecord = RecordContentTransformer.decodeRecord(rawRepo.fetchRecord(recordId, RawRepo.COMMON_AGENCY).getContent());
                MarcRecordReader currentRecordReader = new MarcRecordReader(currentRecord);

                String new652 = reader.getValue("652", "m");
                String current652 = currentRecordReader.getValue("652", "m");

                if (current652 != null && new652 != null && !new652.toLowerCase().equals(current652.toLowerCase())) {
                    return current652.toLowerCase().equals(NO_CLASSIFICATION) &&
                            currentRecordReader.isDBCRecord() &&
                            currentRecordReader.hasValue("008", "d", "m");

                }
            }
        } catch (UnsupportedEncodingException ex) {
            logger.error(ex.getMessage(), ex);
            throw new UpdateException(ex.getMessage(), ex);
        } finally {
            logger.exit();
        }

        return true;
    }

    /**
     * This function checks whether the input field exist and is identical with a field in the record
     *
     * @param field  field to compare
     * @param record record to compare the field in
     * @return Boolean True if the record has a field that matches field
     */
    Boolean isFieldChangedInOtherRecord(MarcField field, MarcRecord record) {
        final MarcRecord cloneMarcRecord = new MarcRecord(record);
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
            extendableFields += EXTENDABLE_SUBJECT_FIELDS;
        }

        logger.info("Agency {} can change the following fields in a national common record: {}", agencyId, extendableFields);

        return extendableFields;
    }

    /**
     * Checks if this record is a national common record.
     *
     * @param record Record.
     * @return {Boolean} True / False.
     */
    public Boolean isNationalCommonRecord(MarcRecord record) {
        logger.entry(record);

        try {
            MarcRecordReader reader = new MarcRecordReader(record);

            if (!reader.hasValue("996", "a", "DBC")) {
                return false;
            }
            for (MarcField field : record.getFields()) {
                if (isFieldNationalCommonField(field)) {
                    return true;
                }
            }
            return false;
        } finally {
            logger.exit();
        }
    }

    /**
     * Checks if a field specifies that a record is a national common record.
     *
     * @param field Field.
     * @return True / False. Return false if a field is indicating it is a NCR field, and true if the field demonstrates it's not a NCR.
     */
    Boolean isFieldNationalCommonField(MarcField field) {
        logger.entry(field);

        try {
            return field.getName().equals("032") &&
                    hasOnlyNationalBibliographicCode(field) &&
                    hasNoLibraryCataloguingCode(field);
        } finally {
            logger.exit();
        }
    }

    private boolean hasOnlyNationalBibliographicCode(MarcField field032) {
        for (MarcSubField subfield : field032.getSubfields()) {
            if ("a".equals(subfield.getName()) && !subfield.getValue().matches("^(DBF|DBI|DBR|DBÅ|DKF|DLF|DMF|DMO|DOP|DPF|DPO|FBL|FPF|GBF|GBÅ|GMO|GPF|IDO|IDP|KIP).*$")) {
                return false;
            }
        }

        return true;
    }

    private boolean hasNoLibraryCataloguingCode(MarcField field032) {
        for (MarcSubField subfield : field032.getSubfields()) {
            if ("x".equals(subfield.getName()) && subfield.getValue().matches("^(BKM|BKR|BKX|CDM|CDR|CDV|CSR|CSV|UTI|SF|NET).*$")) {
                return false;
            }
        }

        return true;
    }

    /**
     * Validate whether the record is legal in regards to note and subject fields and the permissions of the group
     *
     * @param record  The incoming record
     * @param groupId GroupId of the requester
     * @return List of validation errors (ok returns empty list)
     * @throws UpdateException if communication with RawRepo or OpenAgency fails
     */
    public List<MessageEntryDTO> authenticateCommonRecordExtraFields(MarcRecord record, String groupId) throws UpdateException, VipCoreException {
        logger.entry(record, groupId);
        List<MessageEntryDTO> result = new ArrayList<>();
        try {
            MarcRecordReader reader = new MarcRecordReader(record);

            ResourceBundle resourceBundle = ResourceBundles.getBundle("messages");

            String recId = reader.getRecordId();
            if (!rawRepo.recordExists(recId, RawRepo.COMMON_AGENCY)) {
                return result;
            }
            MarcRecord curRecord;
            try {
                Map<String, MarcRecord> curRecordCollection = rawRepo.fetchRecordCollection(recId, RawRepo.COMMON_AGENCY);
                curRecord = ExpandCommonMarcRecord.expandMarcRecord(curRecordCollection, recId);
                logger.info("curRecord:\n{}", LogUtils.base64Encode(curRecord));
            } catch (RawRepoException e) {
                throw new UpdateException("Exception while loading current record", e);
            }
            MarcRecordWriter curWriter = new MarcRecordWriter(curRecord);
            MarcRecordReader curReader = new MarcRecordReader(curRecord);

            curWriter.addOrReplaceSubfield("001", "b", reader.getAgencyId());
            if (!isNationalCommonRecord(curRecord)) {
                return result;
            }

            if (!vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_NOTES)) {
                logger.info("AgencyId {} does not have feature AUTH_COMMON_NOTES in openagency - checking for changed note fields", groupId);
                // Check if all fields in the incoming record are in the existing record
                for (MarcField field : record.getFields()) {
                    if (field.getName().matches(EXTENDABLE_NOTE_FIELDS) && isFieldChangedInOtherRecord(field, curRecord)) {
                        String message = String.format(resourceBundle.getString("notes.subjects.edit.field.error"), groupId, field.getName(), recId);
                        result.add(createMessageDTO(message));
                    }
                }
                // Check if all fields in the existing record are in the incoming record
                for (MarcField field : curRecord.getFields()) {
                    if (field.getName().matches(EXTENDABLE_NOTE_FIELDS) && isFieldChangedInOtherRecord(field, record)) {
                        String fieldName = field.getName();
                        if (curReader.getFieldAll(fieldName).size() != reader.getFieldAll(fieldName).size()) {
                            String message = String.format(resourceBundle.getString("notes.subjects.delete.field.error"), groupId, fieldName, recId);
                            result.add(createMessageDTO(message));
                        }
                    }
                }
            }

            if (!vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_COMMON_SUBJECTS)) {
                logger.info("AgencyId {} does not have feature AUTH_COMMON_SUBJECTS in openagency - checking for changed note fields", groupId);
                // Check if all fields in the incoming record are in the existing record
                for (MarcField field : record.getFields()) {
                    if (field.getName().matches(EXTENDABLE_SUBJECT_FIELDS) && isFieldChangedInOtherRecord(field, curRecord)) {
                        String message = String.format(resourceBundle.getString("notes.subjects.edit.field.error"), groupId, field.getName(), recId);
                        result.add(createMessageDTO(message));
                    }
                }
                // Check if all fields in the existing record are in the incoming record
                for (MarcField field : curRecord.getFields()) {
                    if (field.getName().matches(EXTENDABLE_SUBJECT_FIELDS) && isFieldChangedInOtherRecord(field, record)) {
                        String fieldName = field.getName();
                        if (curReader.getFieldAll(fieldName).size() != reader.getFieldAll(fieldName).size()) {
                            String message = String.format(resourceBundle.getString("notes.subjects.delete.field.error"), groupId, fieldName, recId);
                            result.add(createMessageDTO(message));
                        }
                    }
                }
            }
            return result;
        } finally {
            logger.trace("Exit - NoteAndSubjectExtensionsHandler.authenticateExtensions(): {}", result);
        }
    }

    private MessageEntryDTO createMessageDTO(String message) {
        MessageEntryDTO result = new MessageEntryDTO();

        result.setMessage(message);
        result.setType(TypeEnumDTO.ERROR);

        return result;
    }

    public MarcRecord collapse(MarcRecord record, MarcRecord currentRecord, String groupId, boolean isNationalCommonRecord) throws VipCoreException {
        MarcRecord collapsedRecord = new MarcRecord(currentRecord);
        List<String> fieldsToCopy = new ArrayList<>();

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
        MarcRecordWriter curWriter = new MarcRecordWriter(collapsedRecord);
        curWriter.removeFields(fieldsToCopy);
        curWriter.copyFieldsFromRecord(fieldsToCopy, record);

        return collapsedRecord;
    }

}
