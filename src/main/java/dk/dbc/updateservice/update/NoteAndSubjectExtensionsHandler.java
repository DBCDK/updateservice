/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.iscrum.records.*;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.openagency.client.OpenAgencyException;
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

    static final String EXTENDABLE_NOTE_FIELDS = "504|530|534";
    static final String EXTENDABLE_SUBJECT_FIELDS = "600|610|630|631|666";

    public NoteAndSubjectExtensionsHandler(OpenAgencyService openAgencyService, RawRepo rawRepo) {
        this.openAgencyService = openAgencyService;
        this.rawRepo = rawRepo;
    }

    MarcRecord recordDataForRawRepo(MarcRecord record, String groupId) throws UpdateException, OpenAgencyException, UnsupportedEncodingException {
        logger.entry(record, groupId);

        try {
            MarcRecordReader reader = new MarcRecordReader(record);
            String recId = reader.recordId();

            if (!rawRepo.recordExists(recId, RawRepo.COMMON_AGENCY)) {
                logger.info("No existing record - returning same record");
                return record;
            }

            MarcRecord curRecord = new RawRepoDecoder().decodeRecord(rawRepo.fetchRecord(recId, RawRepo.COMMON_AGENCY).getContent());

            if (!isNationalCommonRecord(curRecord)) {
                logger.info("Record is not national common record - returning same record");
                return record;
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
     * @param record
     * @param groupId
     * @return
     * @throws UpdateException
     */
    public List<MessageEntryDTO> authenticateCommonRecordExtraFields(MarcRecord record, String groupId) throws UpdateException {
        logger.entry(record, groupId);
        List<MessageEntryDTO> result = new ArrayList<>();
        try {
            MarcRecordReader reader = new MarcRecordReader(record);

            ResourceBundle resourceBundle = ResourceBundles.getBundle("messages");

            String recId = reader.recordId();
            if (!rawRepo.recordExists(recId, RawRepo.COMMON_AGENCY)) {
                return result;
            }
            MarcRecord curRecord;
            try {
                Map<String, MarcRecord> curRecordCollection = rawRepo.fetchRecordCollection(recId, RawRepo.COMMON_AGENCY);
                curRecord = ExpandCommonRecord.expand(curRecordCollection);
                logger.info("curRecord:\n{}", curRecord);
            } catch (UnsupportedEncodingException e) {
                throw new UpdateException("Exception while loading current record", e);
            }
            MarcRecordWriter curWriter = new MarcRecordWriter(curRecord);
            MarcRecordReader curReader = new MarcRecordReader(curRecord);

            curWriter.addOrReplaceSubfield("001", "b", reader.agencyId());
            if (!isNationalCommonRecord(curRecord)) {
                return result;
            }

            String extendableFieldsRx = "";
            try {
                extendableFieldsRx = createExtendableFieldsRx(groupId);
            } catch (OpenAgencyException e) {
                throw new UpdateException("Caught OpenAgencyException", e);
            }

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
            logger.trace("Exit - NoteAndSubjectExtentionsHandler.authenticateExtensions(): ", result);
        }
    }

    private MessageEntryDTO createMessageDTO(String message) {
        MessageEntryDTO result = new MessageEntryDTO();

        result.setMessage(message);
        result.setType(TypeEnumDTO.ERROR);

        return result;
    }

    public MarcRecord collapse(MarcRecord record, MarcRecord currentRecord, String groupId) throws OpenAgencyException {
        MarcRecord collapsedRecord = new MarcRecord(currentRecord);
        List<String> extendableFieldsRx = Arrays.asList(createExtendableFieldsRx(groupId).split("\\|"));
        // We need to copy 996 from the incoming record as well, as that field could have been modified in an earlier action
        // But because the Arrays.asList returns an immutable list we need to copy the content to another list.
        List<String> fieldsToCopy = new ArrayList<>();
        fieldsToCopy.addAll(extendableFieldsRx);
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
