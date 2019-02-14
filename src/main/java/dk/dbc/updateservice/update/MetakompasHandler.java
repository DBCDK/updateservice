/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.common.records.CatalogExtractionCode;
import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.common.records.MarcSubField;
import dk.dbc.common.records.utils.RecordContentTransformer;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MetakompasHandler {
    private static final XLogger logger = XLoggerFactory.getXLogger(MetakompasHandler.class);

    private static final List<String> metakompasSubFieldsToCopy = Arrays.asList("e", "g", "p", "m");

    /**
     * This function handles the situation where metakompas sends a minimal record to updateservice
     * <p>
     * The metakompas templates only allow fields 001, 004 and 665. The template is used only by the metakompas application.
     * <p>
     * When metakompas template is used we need to load the existing record and then use that with replaced 665 field from the input
     * <p>
     * Additionally certain 665 subfields are copied to 666 subfields.
     *
     * @return The record to be used for the rest if the execution
     * @throws UpdateException
     * @throws UnsupportedEncodingException
     */
    public static MarcRecord enrichMetakompasRecord(RawRepo rawRepo, MarcRecord minimalMetakompasRecord) throws UnsupportedEncodingException, UpdateException {
        logger.info("Got metakompas template so updated the request record.");
        logger.info("Input metakompas record: \n{}", minimalMetakompasRecord);

        MarcRecordReader reader = new MarcRecordReader(minimalMetakompasRecord);

        if (!rawRepo.recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())) {
            throw new UpdateException("In order to update field 665 the record must exist");
        }

        MarcRecord fullMetakompassRecord = RecordContentTransformer.decodeRecord(rawRepo.fetchMergedDBCRecord(reader.getRecordId(), RawRepo.DBC_ENRICHMENT).getContent());
        MarcRecordWriter fullMetakompassRecordWriter = new MarcRecordWriter(fullMetakompassRecord);
        fullMetakompassRecordWriter.removeField("665");
        fullMetakompassRecord.getFields().addAll(reader.getFieldAll("665"));

        int copiedSubfieldsCount = 0;
        /*
         * If the record is not yet published and the record is send from metakompas then copy relevant 665 subfields to 666.
         *
         * Note that in order to be able manually edit the copied 666 subfields the copy only happens when using the
         * metakompas schema.
         */
        if (!CatalogExtractionCode.isPublished(fullMetakompassRecord)) {
            copiedSubfieldsCount = copyMetakompasFields(fullMetakompassRecord);
        }

        // If no 666 subfields are updated (either because there was no change or because the record is published) then
        // we must add *z98 Minus korrekturprint to suppress unnecessary proof printing
        if (copiedSubfieldsCount == 0) {
            addMinusProofPrinting(fullMetakompassRecord);
        }

        fullMetakompassRecordWriter.sort();

        logger.info("Output metakompas record: \n{}", fullMetakompassRecord);

        return fullMetakompassRecord;
    }

    /**
     * If the record is still under production then all 665 *q, *e, *i and *g subfields must be copied to 666
     */
    static int copyMetakompasFields(MarcRecord record) {
        int count = 0;
        final List<MarcSubField> subfieldsToCopy = new ArrayList<>();
        final List<MarcField> fields665 = record.getFields().stream().
                filter(field -> "665".equals(field.getName())).
                collect(Collectors.toList());

        for (MarcField field : fields665) {
            if (field.getSubfields().stream().
                    anyMatch(subfield -> "&".equals(subfield.getName()) && "LEKTOR".equalsIgnoreCase(subfield.getValue()))) {
                for (MarcSubField subfield : field.getSubfields()) {
                    // 665 *q -> 666 *q
                    if ("q".equals(subfield.getName())) {
                        subfieldsToCopy.add(new MarcSubField("q", subfield.getValue()));
                    }

                    // 665 *i -> 666 *i is year interval, otherwise *i -> *s
                    if ("i".equals(subfield.getName())) {
                        if (isYearInterval(subfield.getValue())) {
                            subfieldsToCopy.add(new MarcSubField("i", subfield.getValue()));
                        } else {
                            subfieldsToCopy.add(new MarcSubField("s", subfield.getValue()));
                        }
                    }

                    // 665 *e/*g/*p/*m -> 666 *s
                    if (metakompasSubFieldsToCopy.contains(subfield.getName())) {
                        subfieldsToCopy.add(new MarcSubField("s", subfield.getValue()));
                    }
                }
            }
        }

        if (subfieldsToCopy.size() > 0) {
            count = subfieldsToCopy.size();
            logger.info("Found {} number of 665 subfield to copy");
            // Fields added by automation should always have an empty *0
            final MarcSubField subfield0 = new MarcSubField("0", "");
            final List<MarcField> fields666 = record.getFields().stream().
                    filter(field -> "666".equals(field.getName())).
                    collect(Collectors.toList());

            for (MarcSubField subfieldToCopy : subfieldsToCopy) {
                boolean hasSubfield = false;
                for (MarcField field666 : fields666) {
                    if (field666.getSubfields().contains(subfieldToCopy)) {
                        // If the field has the subfield to copy but doesn't have *0 subfield then *0 must be added
                        if (!field666.getSubfields().contains(subfield0)) {
                            field666.getSubfields().add(0, subfield0);
                        }

                        hasSubfield = true;
                        break;
                    }
                }

                if (!hasSubfield) {
                    record.getFields().add(new MarcField("666", "00", Arrays.asList(subfield0, subfieldToCopy)));
                }
            }
        }

        return count;
    }

    static void addMinusProofPrinting(MarcRecord record) {
        new MarcRecordWriter(record).addOrReplaceSubfield("z98", "a", "Minus korrekturprint");
    }

    /**
     * Check if a string matches the year interval pattern
     *
     * @param value The string to check
     * @return True if the pattern matches otherwise False
     */
    static boolean isYearInterval(String value) {
        return value.matches("\\d+-\\d+");
    }

}
