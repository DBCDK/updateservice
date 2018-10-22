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
import dk.dbc.common.records.utils.RecordContentTransformer;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This action is responsible for performing preprocessing of incoming records
 */
public class PreProcessingAction extends AbstractRawRepoAction {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(UpdateRequestAction.class);
    private static final String pattern = "^(For|for) ([0-9]+)-([0-9]+) (år)";
    private static final Pattern p = Pattern.compile(pattern);

    public PreProcessingAction(GlobalActionState globalActionState) {
        super(PreProcessingAction.class.getSimpleName(), globalActionState);
    }

    @Override
    public ServiceResult performAction() throws UpdateException {
        LOGGER.entry();
        try {
            final MarcRecord record = state.getMarcRecord();
            final MarcRecordReader reader = new MarcRecordReader(record);

            // Pre-processing should only be performed on 870970 records owned by DBC
            if (reader.getAgencyIdAsInt() == RawRepo.COMMON_AGENCY && reader.hasValue("996", "a", "DBC")) {
                processAgeInterval(record, reader);
                processCodeForEBooks(record, reader);
                processFirstOrNewEdition(record, reader);
            }

            return ServiceResult.newOkResult();
        } catch (UnsupportedEncodingException ex) {
            throw new UpdateException("Caught unexpected exception: " + ex.toString());
        } finally {
            LOGGER.exit();
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
    private void processAgeInterval(MarcRecord record, MarcRecordReader reader) {
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
    private void processCodeForEBooks(MarcRecord record, MarcRecordReader reader) {
        // This pre-processing action can only add 008 *w1 - so if the subfield already exists then there is no point in continuing
        if (reader.hasValue("008", "w", "1")) {
            return;
        }

        // This pre-processing is not applicable to volume or section records
        final String bibliographicRecordType = reader.getValue("004", "a");
        if ("b".equals(bibliographicRecordType) || "s".equals(bibliographicRecordType)) {
            return;
        }

        // 009 *aa = text
        // 009 *gxe = online
        // 008 *tp = periodica
        // 008 *uo = not complete periodica
        if ("a".equals(reader.getValue("009", "a")) && "xe".equals(reader.getValue("009", "g")) &&
                !"p".equals(reader.getValue("008", "t")) && !"o".equals(reader.getValue("008", "u"))) {
            final MarcRecordWriter writer = new MarcRecordWriter(record);
            writer.addOrReplaceSubfield("008", "w", "1");
        }
    }

    /**
     * When a record is created the specific type of edition is set in 008*u. However when the record is updated 008*u
     * can be set to the value 'r' which means updated. When the existing record is either a first edition or new edition that
     * indicator must remain visible on the record. This is done by adding 008*&.
     * <p>
     * Rule:
     * Must be a 870970 record
     * Record doesn't already have 008*&
     * Edition is updated (not new or first edition)
     * <p>
     * Note: The first edition indicator should be only be applied if the release status (008 *u) is no longer first edition.
     *
     * @param record The record to be processed
     */
    private void processFirstOrNewEdition(MarcRecord record, MarcRecordReader reader) throws UpdateException, UnsupportedEncodingException {
        // 008*u = Release status
        // r = updated but unchanged edition
        // u = new edition
        // f = first edition
        String subfield008u = reader.getValue("008", "u");
        // *& is repeatable and have different meaning so we have to match the specific values
        final boolean has008AmpersandF = reader.hasValue("008", "&", "f");
        final boolean has008AmpersandU = reader.hasValue("008", "&", "u");
        if ("r".equals(subfield008u) && // Update edition
                !(has008AmpersandF || has008AmpersandU) && // Doesn't already have indicator
                rawRepo.recordExistsMaybeDeleted(reader.getRecordId(), reader.getAgencyIdAsInt())) { // Record exists
            // Note that creating a new record with 008 *u = r must be handled manually
            final MarcRecord existingRecord = RecordContentTransformer.decodeRecord(rawRepo.fetchRecord(reader.getRecordId(), reader.getAgencyIdAsInt()).getContent());
            final MarcRecordReader existingReader = new MarcRecordReader(existingRecord);
            final String existingSubfield008u = existingReader.getValue("008", "u");
            final String existingSubfield250a = existingReader.getValue("250", "a"); // Edition description

            if ("f".equals(existingSubfield008u)) {
                update008AmpersandEdition(record, "f");
            } else if ("u".equals(existingSubfield008u)) {
                update008AmpersandEdition(record, "u");
            } else if ("r".equals(existingSubfield008u)) {
                if (existingSubfield250a == null) {
                    update008AmpersandEdition(record, "f");
                } else if (existingSubfield250a.contains("1.")) { // as in "1. edition"
                    // "i.e." means corrected edition description.
                    // It is therefor assumed that "1. edition" combined with "corrected edition" means the record is an edition update and not a first edition
                    // See http://praxis.dbc.dk/formatpraksis/px-for1862.html/#-250a-udgavebetegnelse for more details
                    if (existingSubfield250a.contains("i.e.")) {
                        update008AmpersandEdition(record, "u");
                    } else {
                        update008AmpersandEdition(record, "f");
                    }
                } else {
                    // Field 520 contains several subfield which can hold a lot of text
                    // So in order to look for a string "somewhere" in 520 we have to loop through all the subfields
                    final MarcField field520 = existingReader.getField("520");
                    if (field520 != null) {
                        for (MarcSubField subField : field520.getSubfields()) {
                            if (subField.getValue().contains("idligere")) {
                                update008AmpersandEdition(record, "u");
                                break;
                            }
                        }
                    }
                }
            }
            // If someone updates the a first edition record then 008 *u must be manually changed to the value u
            // And in that case the 008 *& should be changed to indicate new edition
        } else if ("u".equals(subfield008u) && reader.hasValue("008", "&", "f")) {
            update008AmpersandEdition(record, "u");
        }
    }

    /**
     * Field 008 can have up to three different *& subfields which all have different meaning.
     * So in order to change between "first edition" and "new edition" we have to either update the existing *& with the
     * opposite value are add a new *&.
     * Simply using addOrReplace will lead to bad things
     *
     * @param record The record which should be updated
     * @param value  The new value for *&
     */
    private void update008AmpersandEdition(MarcRecord record, String value) {
        final MarcRecordReader reader = new MarcRecordReader(record);
        // There is no reason to continue if the field already has the correct value
        if (!reader.hasValue("008", "&", value)) {
            final MarcField field = reader.getField("008");
            // Here we know that there isn't a 008 *& with the input value
            // However there might be a *& with the opposite value (u <> f)
            final String oppositeValue = "u".equals(value) ? "f" : "u";
            if (reader.hasValue("008", "&", oppositeValue)) {
                for (MarcSubField subField : field.getSubfields()) {
                    if ("&".equals(subField.getName()) && oppositeValue.equals(subField.getValue())) {
                        subField.setValue(value);
                        break;
                    }
                }
            } else {
                // If there isn't a *& for either value or opposite value then just add a new *& subfield
                field.getSubfields().add(new MarcSubField("&", value));
            }
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
