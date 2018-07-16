/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcFieldReader;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.common.records.MarcSubField;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This action is responsible for performing preprocessing of incoming records
 */
public class PreProcessingAction extends AbstractRawRepoAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(UpdateRequestAction.class);
    private static final String pattern = "^(For|for) ([0-9]+)-([0-9]+) (år)";
    private static final Pattern p = Pattern.compile(pattern);

    public PreProcessingAction(GlobalActionState globalActionState) {
        super(UpdateRequestAction.class.getSimpleName(), globalActionState);
    }

    @Override
    public ServiceResult performAction() throws UpdateException {
        logger.entry();
        try {
            final MarcRecord record = state.getMarcRecord();

            processAgeInterval(record);
            processCodeForEBooks(record);

            return ServiceResult.newOkResult();
        } finally {
            logger.exit();
        }
    }

    /**
     * This function expands/writes out the age interval in 666 *u
     * <p>
     * Rule:
     * If there is a 666 *u subfield that matches 'For x-y år' then
     * 1) Remove all 666 *u subfields
     * 2)add new '666 00 *0 *u For z år' subfield for each year between x and y (including both)
     * <p>
     * If there is no matching subfield then nothing is done to the record
     *
     * @param record The record to be processed
     */
    private void processAgeInterval(MarcRecord record) {
        final MarcRecordReader reader = new MarcRecordReader(record);

        final List<Matcher> matchers = reader.getSubfieldValueMatchers("666", "u", p);

        if (matchers.size() > 0) {
            // First remove all existing 666 *u subfields
            remove666UFields(record);
        }

        for (Matcher m : matchers) {
            final String forString = m.group(1);
            int year = Integer.parseInt(m.group(2));
            final int endYear = Integer.parseInt(m.group(3));
            final String yearString = m.group(4);

            while (year <= endYear) {
                // The message could have been 'For %s år' instead of '%s %s %s' however the capitalization of
                // 'for' in the age subfield must be the same as in the original 666 *u subfield
                // so we reuse text from the input instead
                record.getFields().add(getNewMarcField666(String.format("%s %s %s", forString, year, yearString)));
                year++;
            }

            // The new fields are added to the bottom of the field list, so we have to do a simple sort on field name
            new MarcRecordWriter(record).sort();
        }
    }

    /**
     * This function adds a code (008 *w1) to mark the record is an e-book, if it is an e-book
     * <p>
     * Rule:
     * Must be a 870790 record
     * The record is not a volume or section record
     * It is a e-book
     * The record is not already marked as an e-book
     * <p>
     * If the conditions are not met or 008 *w1 already exists then nothing is done to the record
     *
     * @param record The record to be processed
     */
    private void processCodeForEBooks(MarcRecord record) {
        final MarcRecordReader reader = new MarcRecordReader(record);

        // This preprocessing is only applicable for common records, so if it is any other kind of agency then just abort now
        if (!"870970".equals(reader.getAgencyId())) {
            return;
        }

        // This preprocessing action can only add 008 *w1 - so if the subfield already exists then there is no point in continuing
        if (reader.hasValue("008", "w", "1")) {
            return;
        }

        // This preprocessing is not applicable to volume or section records
        String bibliographicRecordType = reader.getValue("004", "a");
        if ("b".equals(bibliographicRecordType) || "s".equals(bibliographicRecordType)) {
            return;
        }

        // 009 *aa = text
        // 009 *gxe = online
        // 008 *tp = periodica
        // 008 *uo = not complete periodica
        if ("a".equals(reader.getValue("009", "a")) && "xe".equals(reader.getValue("009", "g")) &&
                !"p".equals(reader.getValue("008", "t")) && !"o".equals(reader.getValue("008", "u"))) {
            MarcRecordWriter writer = new MarcRecordWriter(record);
            writer.addOrReplaceSubfield("008", "w", "1");
        }
    }

    private void remove666UFields(MarcRecord record) {
        final List<MarcField> fieldsToRemove = new ArrayList<>();

        for (MarcField field : record.getFields()) {
            if ("666".equals(field.getName())) {
                MarcFieldReader fieldReader = new MarcFieldReader(field);
                if (fieldReader.hasSubfield("u") && !fieldReader.hasSubfield("0")) {
                    fieldsToRemove.add(field);
                }
            }
        }

        record.getFields().removeAll(fieldsToRemove);
    }

    private MarcField getNewMarcField666(String value) {
        final MarcSubField subfield0 = new MarcSubField("0", "");
        final MarcSubField subfieldU = new MarcSubField("u", value);

        final List<MarcSubField> subfields = new ArrayList<>();
        subfields.add(subfield0);
        subfields.add(subfieldU);

        return new MarcField("666", "00", subfields);
    }

}
