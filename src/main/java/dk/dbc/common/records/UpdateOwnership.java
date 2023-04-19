package dk.dbc.common.records;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class UpdateOwnership {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(UpdateOwnership.class);

    /**
     * Merges ownership in field 996 from record and currentRecord.
     * The result is written to field 996 in record.
     * <p>
     * There are three scenarios to take into account:
     * - The owner (996 *a) on both records are the same ->
     * copy 996 from current to new record
     * - Owners are different, and existing record has no *o field ->
     * set 996 *a = new owner
     * add 996 *o = current owner
     * add 996 *m = current owner
     * - Owners are different and existing record has an *o field ->
     * set 996 *a = new owner
     * add 996 *m = current owner unless already added
     * set 996 *o = original owner
     * <p>
     * Some info about 996:
     * *a is current owner
     * *o is the original owner (should never be changed)
     * *m (repeatable) is the previous owners - m is unordered
     *
     * @param newRecord     The record to merge and write the result to.
     * @param currentRecord currentRecord The current record to merge with.
     * @return MarcRecord The record with the merged result in record.
     */
    public static MarcRecord mergeRecord(MarcRecord newRecord, MarcRecord currentRecord) {
        LOGGER.entry(newRecord, currentRecord);

        try {
            if (newRecord == null || currentRecord == null) {
                return newRecord;
            }

            final MarcRecordReader currentRecordReader = new MarcRecordReader(currentRecord);

            final MarcRecordReader newRecordReader = new MarcRecordReader(newRecord);
            final MarcRecordWriter newRecordWriter = new MarcRecordWriter(newRecord);

            final String currentOwner = currentRecordReader.getValue("996", "a");
            final String newOwner = newRecordReader.getValue("996", "a");

            if (currentOwner == null) {
                return newRecord;
            }

            LOGGER.debug("currentOwner: {}", currentOwner);
            LOGGER.debug("newOwner: {}", newOwner);

            newRecordWriter.removeField("996");

            // A 996 field will most likely exist on the new record but we check anyway.
            // Field 996 is assumed to always exist on the current record as that field is mandatory.
            // If the new and current owners are the same, simply copy the 996 field from current record.
            if (newOwner == null || newOwner.equals(currentOwner)) {
                newRecord.getFields().add(new MarcField(currentRecordReader.getField("996")));
            } else {
                final MarcField ownerField = new MarcField("996", "00");

                // Handle 996 *a
                ownerField.getSubfields().add(new MarcSubField("a", newOwner));

                if (currentOwner.startsWith("7") && !"RET".equals(newOwner)) {
                    // Handle 996 *o
                    if (currentRecordReader.hasSubfield("996", "o")) {
                        final String originalOwner = currentRecordReader.getValue("996", "o");

                        ownerField.getSubfields().add(new MarcSubField("o", originalOwner));

                        // Handle 996 *m
                        final List<String> previousOwners = createListOfPreviousOwners(currentRecordReader);

                        for (String previousOwner : previousOwners) {
                            ownerField.getSubfields().add(new MarcSubField("m", previousOwner));
                        }
                    } else {
                        ownerField.getSubfields().add(new MarcSubField("o", currentOwner));
                    }
                }
                newRecord.getFields().add(ownerField);
            }

            newRecordWriter.sort();

            return newRecord;
        } finally {
            LOGGER.exit(newRecord);
        }
    }

    private static List<String> createListOfPreviousOwners(MarcRecordReader reader) {
        final List<String> owners;

        if (reader.hasSubfield("996", "m")) {
            owners = reader.getValues("996", "m");
        } else {
            owners = new ArrayList<>();
        }

        final String currentOwner = reader.getValue("996", "a");

        // Current owner should be added to the list of previous owners unless already listed
        if (!owners.contains(currentOwner)) {
            owners.add(currentOwner);
        }

        return owners;
    }

}
