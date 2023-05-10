package dk.dbc.updateservice.update;

import dk.dbc.common.records.CatalogExtractionCode;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.marc.binding.DataField;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.marc.binding.SubField;
import org.apache.commons.lang.StringUtils;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;

import static dk.dbc.marc.binding.MarcRecord.hasTag;

public class DefaultEnrichmentRecordHandler {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(DefaultEnrichmentRecordHandler.class);

    private static final List<String> CAT_CODES = Arrays.asList("DBF", "DLF", "DBI", "DMF", "DMO", "DPF", "BKM", "GBF", "GMO", "GPF", "FPF", "DBR", "UTI");
    private static final List<String> CENTRAL_OWNERS = Arrays.asList("DBC", "800010");

    private DefaultEnrichmentRecordHandler() {

    }

    /**
     * Checks if we should create enrichment records for a common record.
     * An enrichment record should be created if:
     * - The current record doesn't have classification (opstilling)
     * - The new record does not have a temporary production date
     * - The current record is not under production
     * - The owner changes from decentral to DBC or KB, even if the record is under production
     * <p>
     * In case no enrichment should be created a log is added with an reason
     *
     * @param updatingCommonRecord The common record being updated
     * @param currentCommonRecord  The current common record
     * @return boolean - true if enrichment record should be created, otherwise false
     */
    public static boolean shouldCreateEnrichmentRecordsResult(ResourceBundle resourceBundle, MarcRecord updatingCommonRecord, MarcRecord currentCommonRecord) {
        final MarcRecordReader updatingCommonRecordReader = new MarcRecordReader(updatingCommonRecord);
        final MarcRecordReader currentCommonRecordReader = new MarcRecordReader(currentCommonRecord);

        if (matchesNoClassification(currentCommonRecordReader.getValue("652", 'm'))) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info(String.format(resourceBundle.getString("do.not.create.enrichments.reason"), "652m", currentCommonRecordReader.getValue("652", 'm')));
            }
            return false;
        }

        if (matchesCatCodeAndTemporaryDate(updatingCommonRecordReader.getValue("032", 'x'))) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info(String.format(resourceBundle.getString("do.not.create.enrichments.reason"), "032x", updatingCommonRecordReader.getValue("032", 'x')));
            }
            return false;
        }

        if (matchesCatCodeAndTemporaryDate(updatingCommonRecordReader.getValue("032", 'a'))) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info(String.format(resourceBundle.getString("do.not.create.enrichments.reason"), "032a", updatingCommonRecordReader.getValue("032", 'a')));
            }
            return false;
        }


        // If the existing record is decentral, and it changes owner to either DBC or KB then we must trigger enrichment logic
        final String currentOwner = currentCommonRecordReader.getValue("996", 'a');
        final String updatingOwner = updatingCommonRecordReader.getValue("996", 'a');
        if (updatingOwner != null && currentOwner != null &&
                CENTRAL_OWNERS.contains(updatingOwner) && !updatingOwner.equals(currentOwner)) {
            LOGGER.info("Detected owner change from decentral to DBC or KB");

            return true;
        }

        // Check if record has been published before today - ie. is it still in production.
        if (CatalogExtractionCode.isUnderProduction(updatingCommonRecord)) {
            // It wasn't, that is, it's still in production, so it should fail unless
            // if 008*u==r then we have to check if content of 032a|x is about to change (some CAT_CODES_TEMPORARY_DATE only).
            if (updatingCommonRecordReader.hasValue("008", 'u', "r")) {
                if (matchKatCodes(currentCommonRecord, updatingCommonRecord)) {
                    // 032 not changed
                    if (LOGGER.isInfoEnabled()) {
                        LOGGER.info(resourceBundle.getString("do.not.create.enrichments.inproduction.reason"));
                    }
                    return false;
                }
            } else {
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info(resourceBundle.getString("do.not.create.enrichments.inproduction.reason"));
                }
                return false;
            }
        }

        return true;
    }

    public static boolean hasMinusEnrichment(MarcRecord marcRecord) {
        final MarcRecordReader recordReader = new MarcRecordReader(marcRecord);
        final String value = recordReader.getValue("z98", 'b');

        return "minus påhængspost".equalsIgnoreCase(value);
    }

    /**
     * The purpose of this function is to generate a list of string of every 032 subfield (a and x)
     * The list can be used to compare the 032 field from two records
     *
     * @param marcRecord to extract the 032 field from
     * @return list of "subfield.name:subfield.value" text
     */
    static List<String> collectProductionCodes(MarcRecord marcRecord) {
        final List<String> result = new ArrayList<>();
        final DataField field = marcRecord.getField(DataField.class, hasTag("032")).orElse(null);

        if (field != null) {
            for (SubField subfield : field.getSubFields()) {
                if (subfield.getData().length() > 2 && "OVE".matches(subfield.getData().substring(0, 3))) {
                    // OVE codes is not a part of the codes that define production state for a record owned by DBC
                    continue;
                }
                if (matchesCatCodeAndDate(subfield.getData())) {
                    result.add(subfield.getCode() + ":" + subfield.getData());
                }
            }
        }

        return result;
    }

    /**
     * This function compares the 032 fields on the two input records
     *
     * @param actualRec The first record
     * @param newRec    The second record
     * @return true if the 032 fields are identical (unsorted), otherwise false
     */
    static boolean matchKatCodes(MarcRecord actualRec, MarcRecord newRec) {
        final List<String> oldValues = collectProductionCodes(actualRec);
        final List<String> newValues = collectProductionCodes(newRec);

        Collections.sort(oldValues);
        Collections.sort(newValues);

        LOGGER.debug("oldValues: {}", oldValues);
        LOGGER.debug("newValues: {}", newValues);

        if (oldValues.size() != newValues.size()) {
            LOGGER.debug("oldValues.length !== newValues.length");
            return false;
        } else {
            LOGGER.debug("oldValues.length === newValues.length");
            for (int i = 0; i < oldValues.size(); i++) {
                if (!oldValues.get(i).equals(newValues.get(i))) {
                    LOGGER.debug("oldValues[i] !== newValues[i]: {} {} {}", oldValues.get(i), "!==", newValues.get(i));
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean matchesCatCodeAndDate(String input) {
        if (input.length() == 9) {
            final String firstThreeLetters = input.substring(0, 3);
            final String lastSixLetters = input.substring(3);

            return CAT_CODES.contains(firstThreeLetters.toUpperCase()) && StringUtils.isNumeric(lastSixLetters);
        }

        return false;
    }

    private static boolean matchesCatCodeAndTemporaryDate(String input) {
        if (input != null && input.length() == 9) {
            final String firstThreeLetters = input.substring(0, 3);
            final String lastSixLetters = input.substring(3);

            return CAT_CODES.contains(firstThreeLetters.toUpperCase()) && "999999".equalsIgnoreCase(lastSixLetters);
        }

        return false;
    }

    private static boolean matchesNoClassification(String input) {
        if (input != null) {
            final String newTitle = "ny titel";
            final String withoutClass = "uden klassemærke";

            return newTitle.equalsIgnoreCase(input) || withoutClass.equalsIgnoreCase(input);
        }

        return false;
    }

}
