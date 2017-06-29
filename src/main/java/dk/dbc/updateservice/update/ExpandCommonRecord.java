package dk.dbc.updateservice.update;


import dk.dbc.iscrum.records.*;

import java.io.UnsupportedEncodingException;
import java.util.*;

public class ExpandCommonRecord {

    private static final List<String> AUTHORITY_FIELD_LIST = Arrays.asList("100", "600", "700");

    /**
     * The function takes a set of  records and return a common marc record expanded with authority fields (if any)
     *
     * @param records map containing a common record and x amount of authority records
     * @return a single common record expanded with authority data
     * @throws UnsupportedEncodingException if the records can't be decoded
     * @throws UpdateException              if the collection doesn't contain the necessary records
     */
    public static MarcRecord expand(Map<String, MarcRecord> records) throws UnsupportedEncodingException, UpdateException {
        MarcRecord expandedRecord = new MarcRecord();
        MarcRecord commonRecord = null;
        Map<String, MarcRecord> authorityRecords = new HashMap<>();

        // Key is the recordId and value is the record. AgencyId have to be found in the record
        Iterator it = records.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            MarcRecord marcRecord = (MarcRecord) pair.getValue();
            MarcRecordReader reader = new MarcRecordReader(marcRecord);
            String recordId = pair.getKey().toString();
            String agencyId = reader.agencyId();
            if ("870970".equals(agencyId)) {
                commonRecord = new MarcRecord(marcRecord);
            } else if ("870979".equals(agencyId)) {
                authorityRecords.put(recordId, new MarcRecord(marcRecord));
            }
            it.remove();
        }

        if (commonRecord == null) {
            throw new UpdateException("The record collection doesn't contain a common record");
        }

        /*
         * Okay, here are (some) of the rules for expanding with auth records:
         * Fields that can contain AUT are: 100, 600, 700
         * AUT reference are located in *5 and *6
         *
         * A field points to AUT data if:
         * Field name is either 100, 600 or 700
         * And contains subfields *5 and *6
         *
         * Rules for expanding are:
         * Remove *5 and *6
         * Add all subfields from AUT record field 100
         * If AUT record contains field 400 or 500 then add that field as well to the expanded record but as field 900
         */
        boolean foundAuthField;

        MarcRecordReader reader = new MarcRecordReader(commonRecord);
        handleNonRepeatableField(reader.getField("100"), expandedRecord, authorityRecords);
        handleRepeatableField(reader.getFieldAll("600"), expandedRecord, authorityRecords);
        handleRepeatableField(reader.getFieldAll("700"), expandedRecord, authorityRecords);

        for (MarcField field : commonRecord.getFields()) {
            if (!AUTHORITY_FIELD_LIST.contains(field.getName())) {
                expandedRecord.getFields().add(new MarcField(field));
            }
        }

        return expandedRecord;
    }

    private static void handleRepeatableField(List<MarcField> fields, MarcRecord expandedRecord, Map<String, MarcRecord> authorityRecords) throws UpdateException {
        Integer authIndicator = 0;
        for (MarcField field : fields) {
            MarcFieldReader fieldReader = new MarcFieldReader(field);
            if (fieldReader.hasSubfield("å")) {
                Integer indicator = Integer.parseInt(fieldReader.getValue("å"));
                if (indicator > authIndicator) {
                    authIndicator = indicator;
                }
            }
        }
        authIndicator++;

        for (MarcField field : fields) {
            MarcFieldReader fieldReader = new MarcFieldReader(field);

            if (fieldReader.hasSubfield("5") && fieldReader.hasSubfield("6")) {
                String authRecordId = fieldReader.getValue("6");

                // Find the index of where the AUT reference subfields are in the field
                // We need to add the AUT content at that location
                Integer authSubfieldIndex = 0;
                for (int i = 0; i < field.getSubfields().size(); i++) {
                    if (field.getSubfields().get(i).getName().equals("5")) {
                        authSubfieldIndex = i;
                        break;
                    }
                }

                MarcRecord authRecord = authorityRecords.get(authRecordId);

                if (authRecord == null) {
                    throw new UpdateException("Could not find " + authRecordId + " record in the authority list");
                }

                MarcRecordReader authRecordReader = new MarcRecordReader(authRecord);
                MarcField authField = new MarcField(authRecordReader.getField("100"));

                MarcField expandedField = new MarcField(field);
                MarcFieldWriter expandedFieldWriter = new MarcFieldWriter(expandedField);
                expandedFieldWriter.removeSubfield("5");
                expandedFieldWriter.removeSubfield("6");
                expandedField.setIndicator("00");
                for (MarcSubField authSubfield : authField.getSubfields()) {
                    expandedField.getSubfields().add(authSubfieldIndex++, new MarcSubField(authSubfield));
                }


                if (authRecordReader.hasField("400") || authRecordReader.hasField("500")) {
                    String pointer = field.getName();
                    if (authIndicator > 0) {
                        expandedField.getSubfields().add(0, new MarcSubField("å", authIndicator.toString()));
                        pointer += "/" + authIndicator.toString();
                    }

                    for (MarcField auth400 : authRecordReader.getFieldAll("400")) {
                        MarcField newAuth400 = new MarcField("900", "00");
                        newAuth400.setSubfields(auth400.getSubfields());
                        newAuth400.getSubfields().add(new MarcSubField("z", pointer));
                        expandedRecord.getFields().add(newAuth400);
                    }

                    for (MarcField auth500 : authRecordReader.getFieldAll("500")) {
                        MarcField newAuth500 = new MarcField("900", "00");
                        newAuth500.setSubfields(auth500.getSubfields());
                        newAuth500.getSubfields().add(new MarcSubField("z", pointer));
                        expandedRecord.getFields().add(newAuth500);
                    }

                    authIndicator++;
                }

                expandedRecord.getFields().add(new MarcField(expandedField));
            } else {
                expandedRecord.getFields().add(new MarcField(field));
            }
        }

    }

    private static void handleNonRepeatableField(MarcField field, MarcRecord expandedRecord, Map<String, MarcRecord> authorityRecords) throws UpdateException {
        MarcFieldReader fieldReader = new MarcFieldReader(field);
        if (field != null) {
            if (fieldReader.hasSubfield("5") && fieldReader.hasSubfield("6")) {
                String authRecordId = fieldReader.getValue("6");

                // Find the index of where the AUT reference subfields are in the field
                // We need to add the AUT content at that location
                Integer authSubfieldIndex = 0;
                for (int i = 0; i < field.getSubfields().size(); i++) {
                    if (field.getSubfields().get(i).getName().equals("5")) {
                        authSubfieldIndex = i;
                        break;
                    }
                }

                MarcRecord authRecord = authorityRecords.get(authRecordId);

                if (authRecord == null) {
                    throw new UpdateException("Could not find " + authRecordId + " record in the authority list");
                }

                MarcRecordReader authRecordReader = new MarcRecordReader(authRecord);
                MarcField authField = new MarcField(authRecordReader.getField("100"));

                MarcField expandedField = new MarcField(field);
                MarcFieldWriter expandedFieldWriter = new MarcFieldWriter(expandedField);
                expandedFieldWriter.removeSubfield("5");
                expandedFieldWriter.removeSubfield("6");
                expandedField.setIndicator("00");
                for (MarcSubField authSubfield : authField.getSubfields()) {
                    expandedField.getSubfields().add(authSubfieldIndex++, new MarcSubField(authSubfield));
                }

                expandedRecord.getFields().add(new MarcField(expandedField));

                for (MarcField auth400 : authRecordReader.getFieldAll("400")) {
                    MarcField newAuth400 = new MarcField("900", "00");
                    newAuth400.setSubfields(auth400.getSubfields());
                    newAuth400.getSubfields().add(new MarcSubField("z", field.getName()));
                    expandedRecord.getFields().add(newAuth400);
                }

                for (MarcField auth500 : authRecordReader.getFieldAll("500")) {
                    MarcField newAuth500 = new MarcField("900", "00");
                    newAuth500.setSubfields(auth500.getSubfields());
                    newAuth500.getSubfields().add(new MarcSubField("z", field.getName()));
                    expandedRecord.getFields().add(newAuth500);
                }
            } else {
                expandedRecord.getFields().add(new MarcField(field));
            }
        }
    }
}