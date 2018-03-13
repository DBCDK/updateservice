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

    public PreProcessingAction(GlobalActionState globalActionState) {
        super(UpdateRequestAction.class.getSimpleName(), globalActionState);
    }

    @Override
    public ServiceResult performAction() throws UpdateException {
        logger.entry();
        try {
            MarcRecord record = state.getMarcRecord();

            processAgeInterval(record);

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
     * remove the 666 *u field
     * add new '666 00 *0 *u For z år' subfield for each year between x and y (including both)
     * <p>
     * If there is no matching subfield then nothing is done to the record
     *
     * @param record The record to be processed
     * @throws UpdateException If anything goes wrong
     */
    private void processAgeInterval(MarcRecord record) throws UpdateException {
        MarcRecordReader reader = new MarcRecordReader(record);

        // It would be easier to use reader.getValues but we need a reference to the field object
        for (MarcField field : reader.getFieldAll("666")) {
            MarcFieldReader fieldReader = new MarcFieldReader(field);

            if (fieldReader.hasSubfield("u")) {
                String fieldValue = fieldReader.getValue("u");

                String pattern = "^(For|for) ([0-9]+)-([0-9]+) (år)";
                Pattern p = Pattern.compile(pattern);
                Matcher m = p.matcher(fieldValue);

                if (m.find()) {
                    String forString = m.group(1);
                    int year = Integer.parseInt(m.group(2));
                    int endYear = Integer.parseInt(m.group(3));
                    String yearString = m.group(4);

                    while (year <= endYear) {
                        // The message could have been 'For %s år' instead of '%s %s %s' however the capitalization of
                        // 'for' in the age subfield must be the same as in the original 666 *u subfield
                        // so we reuse text from the input instead
                        String value = String.format("%s %s %s", forString, year, yearString);
                        if (!reader.hasValue("666", "u", value)) {
                            record.getFields().add(getNewMarcField666(value));
                        }
                        year++;
                    }

                    // According to the preprocessing rules the original interval field must be removed
                    record.getFields().remove(field);

                    // The new fields are added to the bottom of the field list, so we have to do a simple sort on field name
                    new MarcRecordWriter(record).sort();
                }
            }
        }
    }

    private MarcField getNewMarcField666(String value) {
        MarcSubField subfield0 = new MarcSubField("0", "");
        MarcSubField subfieldU = new MarcSubField("u", value);

        List<MarcSubField> subfields = new ArrayList<>();
        subfields.add(subfield0);
        subfields.add(subfieldU);

        return new MarcField("666", "00", subfields);
    }

}
