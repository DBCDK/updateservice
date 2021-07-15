/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.common.records.*;
import org.apache.commons.lang.StringUtils;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.*;

public class DefaultEnrichmentRecordHandler {
    private static final XLogger logger = XLoggerFactory.getXLogger(DefaultEnrichmentRecordHandler.class);

    private static final List<String> CAT_CODES = Arrays.asList("DBF", "DLF", "DBI", "DMF", "DMO", "DPF", "BKM", "GBF", "GMO", "GPF", "FPF", "DBR", "UTI");

    /**
     * Checks if we should create enrichment records for a common record.
     * An enrichment record should be created if:
     * - The current record doesn't have classification (opstilling)
     * - The new record does not have a temporary production date
     * - The current record is not under production
     *
     * In case no enrichment should be created a log is added with an reason
     *
     * @param updatingCommonRecord The common record being updated
     * @param currentCommonRecord  The current common record
     *
     * @return boolean - true if enrichment record should be created, otherwise false
     *
     */
    public static boolean shouldCreateEnrichmentRecordsResult(ResourceBundle resourceBundle, MarcRecord updatingCommonRecord, MarcRecord currentCommonRecord) {
        logger.entry(updatingCommonRecord, currentCommonRecord);
        boolean result = true;
        try {
            MarcRecordReader updatingCommonRecordReader = new MarcRecordReader(updatingCommonRecord);
            MarcRecordReader currentCommonRecordReader = new MarcRecordReader(currentCommonRecord);

            if (matchesNoClassification(currentCommonRecordReader.getValue("652", "m"))) {
                logger.info(String.format(resourceBundle.getString("do.not.create.enrichments.reason"), "652m", currentCommonRecordReader.getValue("652", "m")));
                return result = false;
            }

            if (matchesCatCodeAndTemporaryDate(updatingCommonRecordReader.getValue("032", "x"))) {
                logger.info(String.format(resourceBundle.getString("do.not.create.enrichments.reason"), "032x", updatingCommonRecordReader.getValue("032", "x")));
                return result = false;
            }

            if (matchesCatCodeAndTemporaryDate(updatingCommonRecordReader.getValue("032", "a"))) {
                logger.info(String.format(resourceBundle.getString("do.not.create.enrichments.reason"), "032a", updatingCommonRecordReader.getValue("032", "a")));
                return result = false;
            }

            // Check if record has been published before today - ie. is it still in production.
            if (CatalogExtractionCode.isUnderProduction(updatingCommonRecord)) {
                // It wasn't, that is, it's still in production, so it should fail unless
                // if 008*u==r then we have to check if content of 032a|x is about to change (some CAT_CODES_TEMPORARY_DATE only).
                if (updatingCommonRecordReader.hasValue("008", "u", "r")) {
                    if (matchKatCodes(currentCommonRecord, updatingCommonRecord)) {
                        // 032 not changed
                        logger.info(String.format(resourceBundle.getString("do.not.create.enrichments.inproduction.reason")));
                        return result = false;
                    }
                } else {
                    logger.info(String.format(resourceBundle.getString("do.not.create.enrichments.inproduction.reason")));
                    return result = false;
                }
            }

            return result;
        } finally {
            logger.exit(result);
        }
    }

    /**
     * The purpose of this function is to generate a list of string of every 032 subfield (a and x)
     * The list can be used to compare the 032 field from two records
     *
     * @param record to extract the 032 field from
     * @return list of "subfield.name:subfield.value" text
     */
    static List<String> collectProductionCodes(MarcRecord record) {
        logger.entry(record);
        List<String> result = new ArrayList<>();
        MarcRecordReader reader = new MarcRecordReader(record);

        try {
            MarcField field = reader.getField("032");
            if (field != null) {
                for (MarcSubField subfield : field.getSubfields()) {
                    if (matchesCatCodeAndDate(subfield.getValue())) {
                        result.add(subfield.getName() + ":" + subfield.getValue());
                    }
                }
            }

            return result;
        } finally {
            logger.exit(result);
        }
    }

    /**
     * This function compares the 032 fields on the two input records
     *
     * @param actualRec The first record
     * @param newRec The second record
     * @return true if the 032 fields are identical (unsorted), otherwise false
     */
    static boolean matchKatCodes(MarcRecord actualRec, MarcRecord newRec) {
        logger.entry(actualRec, newRec);

        boolean result = false;

        try {
            List<String> oldValues = collectProductionCodes(actualRec);
            List<String> newValues = collectProductionCodes(newRec);

            Collections.sort(oldValues);
            Collections.sort(newValues);

            logger.debug("oldValues: {}", oldValues);
            logger.debug("newValues: {}", newValues);

            if (oldValues.size() != newValues.size()) {
                logger.debug("oldValues.length !== newValues.length");
                return result = false;
            } else {
                logger.debug("oldValues.length === newValues.length");
                for (int i = 0; i < oldValues.size(); i++) {
                    if (!oldValues.get(i).equals(newValues.get(i))) {
                        logger.debug("oldValues[i] !== newValues[i]: {} {} {}", oldValues.get(i), "!==", newValues.get(i));
                        return result = false;
                    }
                }
            }

            return result = true;
        } finally {
            logger.exit(result);
        }
    }

    private static boolean matchesCatCodeAndDate(String input) {
        if (input.length() == 9) {
            String firstThreeLetters = input.substring(0,3);
            String lastSixLetters = input.substring(3);

            return CAT_CODES.contains(firstThreeLetters.toUpperCase()) && StringUtils.isNumeric(lastSixLetters);
        }

        return false;
    }

    private static boolean matchesCatCodeAndTemporaryDate(String input) {
        if (input != null && input.length() == 9) {
            String firstThreeLetters = input.substring(0,3);
            String lastSixLetters = input.substring(3);

            return CAT_CODES.contains(firstThreeLetters.toUpperCase()) && "999999".equalsIgnoreCase(lastSixLetters);
        }

        return false;
    }

    private static boolean matchesNoClassification(String input) {
        String newTitle = "ny titel";
        String withoutClass = "uden klassemærke";

        return newTitle.equalsIgnoreCase(input) || withoutClass.equalsIgnoreCase(input);
    }

}
