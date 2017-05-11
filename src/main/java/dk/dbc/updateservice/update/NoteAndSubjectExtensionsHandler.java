/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.iscrum.records.*;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.openagency.client.OpenAgencyException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.UnsupportedEncodingException;

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
    Boolean isNationalCommonRecord(MarcRecord record) {
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

}
