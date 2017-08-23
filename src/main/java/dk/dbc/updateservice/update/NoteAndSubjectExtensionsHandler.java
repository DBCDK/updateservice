/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.common.records.*;
import dk.dbc.common.records.utils.RecordContentTransformer;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.marcrecord.ExpandCommonMarcRecord;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.openagency.client.OpenAgencyException;
import dk.dbc.rawrepo.RawRepoException;
import dk.dbc.updateservice.dto.MessageEntryDTO;
import dk.dbc.updateservice.dto.TypeEnumDTO;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.*;

public class NoteAndSubjectExtensionsHandler {
    private static final XLogger logger = XLoggerFactory.getXLogger(NoteAndSubjectExtensionsHandler.class);
    private OpenAgencyService openAgencyService;
    private RawRepo rawRepo;
    private ResourceBundle messages;

    static final String EXTENDABLE_NOTE_FIELDS = "504|530|534";
    static final String EXTENDABLE_SUBJECT_FIELDS = "600|610|630|631|666";
    private static final String EXTENDABLE_SUBJECT_FIELDS_NO_AMPERSAND = "652";
    private static final String NO_CLASSIFICATION = "uden klassemærke";

    public NoteAndSubjectExtensionsHandler(OpenAgencyService openAgencyService, RawRepo rawRepo, ResourceBundle messages) {
        this.openAgencyService = openAgencyService;
        this.rawRepo = rawRepo;
        this.messages = messages;
    }

    MarcRecord recordDataForRawRepo(MarcRecord record, String groupId) throws UpdateException, OpenAgencyException, UnsupportedEncodingException {
        logger.entry(record, groupId);

        try {
            MarcRecordReader reader = new MarcRecordReader(record);
            String recId = reader.getRecordId();

            if (!rawRepo.recordExists(recId, RawRepo.COMMON_AGENCY)) {
                logger.info("No existing record - returning same record");
                return record;
            }

            MarcRecord curRecord = RecordContentTransformer.decodeRecord(rawRepo.fetchRecord(recId, RawRepo.COMMON_AGENCY).getContent());

            if (!isNationalCommonRecord(curRecord)) {
                logger.info("Record is not national common record - returning same record");
                return record;
            }

            logger.info("Checking for altered classifications for disputas type material");
            if (reader.hasValue("008", "d", "m")
                    && openAgencyService.hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_COMMON_SUBJECTS)) {
                checkForAlteredClassificationForDisputas(reader, messages);
            }

            logger.info("Record exists and is common national record - setting extension fields");
            String extendableFieldsRx = createExtendableFieldsRx(groupId);
            logger.info("Extendablefields: {} ", extendableFieldsRx);
            if (!extendableFieldsRx.isEmpty()) {
                for (MarcField field : record.getFields()) {
                    MarcFieldReader fieldReader = new MarcFieldReader(field);
                    if (field.getName().matches(extendableFieldsRx)) {
                        logger.info("Found extendable field! {}", field.getName());
                        if (fieldReader.hasSubfield("&")) {
                            field.getSubfields().add(new MarcSubField("&", groupId));
                        } else if (isFieldChangedInOtherRecord(field, curRecord)) {
                            field.getSubfields().add(0, new MarcSubField("&", groupId));
                        }
                    }
                }
            }

            return record;
        } finally {
            logger.exit(record);
        }
    }

    void checkForAlteredClassificationForDisputas(MarcRecordReader reader, ResourceBundle messages) throws UpdateException {
        logger.entry();

        try {
            String recordId = reader.getRecordId();
            if (rawRepo.recordExists(recordId, RawRepo.COMMON_AGENCY)) {
                MarcRecord currentRecord = RecordContentTransformer.decodeRecord(rawRepo.fetchRecord(recordId, RawRepo.COMMON_AGENCY).getContent());
                MarcRecordReader currentRecordReader = new MarcRecordReader(currentRecord);

                String new652 = reader.getValue("652", "m");
                String current652 = currentRecordReader.getValue("652", "m");

                if (current652 != null && new652 != null && !new652.toLowerCase().equals(current652.toLowerCase())) {
                    if (!(current652.toLowerCase().equals(NO_CLASSIFICATION) ||
                            currentRecordReader.isDBCRecord() ||
                            currentRecordReader.hasValue("008", "d", "m"))) {
                        String msg = messages.getString("update.dbc.record.652");
                        logger.error("Unable to create sub actions due to an error: {}", msg);
                        throw new UpdateException(msg);
                    }
                    logger.info("Common record met disputas criteria and is being updated");
                }
            }
        } catch (UnsupportedEncodingException ex) {
            logger.error(ex.getMessage(), ex);
            throw new UpdateException(ex.getMessage(), ex);
        } finally {
            logger.exit();
        }
    }

    /**
     * This function checks whether the input field exist and is identical with a field in the record
     *
     * @param field  field to compare
     * @param record record to compare the field in
     * @return Boolean True if the record has a field that matches field
     */
    Boolean isFieldChangedInOtherRecord(MarcField field, MarcRecord record) {
        MarcRecord clone = new MarcRecord(record);
        MarcRecordReader reader = new MarcRecordReader(clone);
        MarcRecordWriter writer = new MarcRecordWriter(clone);
        MarcFieldReader fieldReader = new MarcFieldReader(field);

        if (field.getName().equals("001")) {
            if (fieldReader.hasSubfield("c")) {
                writer.addOrReplaceSubfield("001", "c", fieldReader.getValue("c"));
            }

            if (fieldReader.hasSubfield("d")) {
                writer.addOrReplaceSubfield("001", "d", fieldReader.getValue("d"));
            }
        }

        for (MarcField cf : reader.getFieldAll(field.getName())) {
            if (cf.getName().equals(field.getName())) {
                if (cf.equals(field)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * This function returns a list of allowed extendable fields as a regex string
     *
     * @param agencyId AgencyId of the library to check for
     * @return String containing fields which can be used in regex
     * @throws OpenAgencyException Incase OpenAgency throws exception
     */
    String createExtendableFieldsRx(String agencyId) throws OpenAgencyException {
        String extendableFields = "";

        if (openAgencyService.hasFeature(agencyId, LibraryRuleHandler.Rule.AUTH_COMMON_NOTES)) {
            extendableFields += EXTENDABLE_NOTE_FIELDS;
        }

        if (openAgencyService.hasFeature(agencyId, LibraryRuleHandler.Rule.AUTH_COMMON_SUBJECTS)) {
            if (!extendableFields.isEmpty()) {
                extendableFields += "|";
            }
            extendableFields += EXTENDABLE_SUBJECT_FIELDS;
        }

        logger.info("Bibliotek {} har ret til at rette i følgende felter i en national post: {}", agencyId, extendableFields);

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
            MarcFieldReader reader = new MarcFieldReader(field);

            return field.getName().equals("032") && reader.hasSubfield("a") && !reader.getValue("a").matches("^(BKM|NET|SF).*$");
        } finally {
            logger.exit();
        }
    }

    /**
     * Validate whether the record is legal in regards to note and subject fields and the permissions of the group
     *
     * @param record  The incoming record
     * @param groupId GroupId of the requester
     * @return List of validation errors (ok returns empty list)
     * @throws UpdateException if communication with RawRepo or OpenAgency fails
     */
    public List<MessageEntryDTO> authenticateCommonRecordExtraFields(MarcRecord record, String groupId) throws UpdateException {
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
                curRecord = ExpandCommonMarcRecord.expandMarcRecord(curRecordCollection);
                logger.info("curRecord:\n{}", curRecord);
            } catch (UnsupportedEncodingException | RawRepoException e) {
                throw new UpdateException("Exception while loading current record", e);
            }
            MarcRecordWriter curWriter = new MarcRecordWriter(curRecord);
            MarcRecordReader curReader = new MarcRecordReader(curRecord);

            curWriter.addOrReplaceSubfield("001", "b", reader.getAgencyId());
            if (!isNationalCommonRecord(curRecord)) {
                return result;
            }

            String extendableFieldsRx;
            try {
                extendableFieldsRx = createExtendableFieldsRx(groupId);
            } catch (OpenAgencyException e) {
                throw new UpdateException("Caught OpenAgencyException", e);
            }
            if (!extendableFieldsRx.isEmpty()) {
                extendableFieldsRx += "|";
            }
            extendableFieldsRx += EXTENDABLE_SUBJECT_FIELDS_NO_AMPERSAND;
            for (MarcField field : record.getFields()) {
                if (!(!extendableFieldsRx.isEmpty() && field.getName().matches(extendableFieldsRx))) {
                    if (isFieldChangedInOtherRecord(field, curRecord)) {
                        String message = String.format(resourceBundle.getString("notes.subjects.edit.field.error"), groupId, field.getName(), recId);
                        result.add(createMessageDTO(message));
                    }
                }
            }
            for (MarcField field : curRecord.getFields()) {
                if (!(!extendableFieldsRx.isEmpty() && field.getName().matches(extendableFieldsRx))) {
                    if (isFieldChangedInOtherRecord(field, record)) {
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
            logger.trace("Exit - NoteAndSubjectExtensionsHandler.authenticateExtensions(): ", result);
        }
    }

    private MessageEntryDTO createMessageDTO(String message) {
        MessageEntryDTO result = new MessageEntryDTO();

        result.setMessage(message);
        result.setType(TypeEnumDTO.ERROR);

        return result;
    }

    public MarcRecord collapse(MarcRecord record, MarcRecord currentRecord, String groupId, boolean isNationalCommonRecord) throws OpenAgencyException {
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
