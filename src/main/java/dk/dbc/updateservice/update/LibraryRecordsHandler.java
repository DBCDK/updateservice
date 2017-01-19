package dk.dbc.updateservice.update;

import dk.dbc.iscrum.records.CatalogExtractionCode;
import dk.dbc.iscrum.records.MarcField;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.records.MarcRecordWriter;
import dk.dbc.iscrum.records.MarcSubField;
import dk.dbc.iscrum.records.UpdateOwnership;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.openagency.client.OpenAgencyException;
import dk.dbc.updateservice.actions.UpdateMode;
import dk.dbc.updateservice.dto.AuthenticationDto;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.javascript.ScripterException;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Class to manipulate library records for a local library. Local records and
 * local extended records.
 * <p>
 */
@Stateless
public class LibraryRecordsHandler {
    private static final XLogger logger = XLoggerFactory.getXLogger(LibraryRecordsHandler.class);
    private static final List<String> CLASSIFICATION_FIELDS = Arrays.asList("008", "009", "038", "039", "100", "110", "239", "245", "652");
    private static final String DIACRITICAL_MARKS = "[\\p{InCombiningDiacriticalMarks}]";
    private static final String ALPHA_NUMERIC_DANISH_CHARS = "[^a-z0-9\u00E6\u00F8\u00E5]";

    @EJB
    private Scripter scripter;

    @EJB
    private OpenAgencyService openAgencyService;

    @EJB
    private RawRepo rawRepo;

    public LibraryRecordsHandler() {
    }

    protected LibraryRecordsHandler(Scripter scripter) {
        this.scripter = scripter;
    }

    /**
     * Tests if a record is published
     *
     * @param record The record.
     * @return <code>true</code> if published
     * <code>false</code> otherwise.
     * @throws ScripterException in case of an error
     */
    public boolean isRecordInProduction(MarcRecord record) throws ScripterException {
        logger.entry(record);

        try {
            return CatalogExtractionCode.isUnderProduction(record);
        } finally {
            logger.exit();
        }
    }

    /**
     * Tests if a record contains any classification data.
     *
     * @param record The record.
     * @return <code>true</code> if classifications where found,
     * <code>false</code> otherwise.
     */
    public boolean hasClassificationData(MarcRecord record) {
        logger.entry(record);
        boolean result = false;
        try {
            List<MarcField> fields = record.getFields();
            for (MarcField field : fields) {
                if (CLASSIFICATION_FIELDS.contains(field.getName())) {
                    return result = true;
                }
            }
            return result = false;
        } finally {
            logger.exit(result);
        }
    }

    /**
     * Collect pairs of subfields a and g from field 009
     *
     * @param reader reader for the record
     * @return List of paired subfields
     */
    private List<String> getLowerCaseAGPairs(MarcRecordReader reader) {
        List<String> result = new ArrayList<>();
        MarcField field;
        field = reader.getField("009");
        if (field == null) {
            logger.info("field NULL");
            return result;
        }
        List<MarcSubField> SubfieldList = field.getSubfields();
        boolean gotLowerCaseA = false;
        String aString = "";
        String gString;
        // run through until a - search for g - if a first, then push a,<empty>
        // if g then push a,g - start from scratch.
        // after : if a and no g then push a,<empty>
        // can there be a after a ? what about g after g ?
        // There are no validation for them being paired so yes
        // test cases :
        // *a s *b b -> as
        // *a p *b a *a g -> ap ag
        // *a s *b b *g xc -> asgxc
        // *a s *b b *g xc *a p -> asgxc ap
        // *a s *b b *g xc *g tk -> asgxc gtk
        // *a s *b a *g xc *a s *b m *g th *a s *g xk *b a *h xx -> asgxc asgth asgxk
        for (MarcSubField aSubfieldList : SubfieldList) {
            if ("a".equals(aSubfieldList.getName())) {
                if (gotLowerCaseA) {
                    result.add("a" + aString);
                }
                gotLowerCaseA = true;
                aString = aSubfieldList.getValue();
            } else {
                if ("g".equals(aSubfieldList.getName())) {
                    gString = aSubfieldList.getValue();
                    if (gotLowerCaseA) {
                        result.add("a" + aString + "g" + gString);
                        gotLowerCaseA = false;
                    } else {
                        result.add("g" + gString);
                    }

                }
            }
        }
        if (gotLowerCaseA) {
            result.add("a" + aString);
        }
        return result;
    }

    /**
     * Returns a string containing subfield content maybe stripped, maybe normalized
     *
     * @param subfieldList List of subfields in the field
     * @param subfields    subfields to pick data from
     * @param normalize    should it be normalized ? NB : all other than alphanumeric are always stripped
     * @param cut          Should we look at less than the full content of a subfield ?
     * @return The collected data
     */
    private String getCompareString(List<MarcSubField> subfieldList, String subfields, boolean normalize, int cut) {
        if (subfieldList == null) {
            return "";
        }
        StringBuilder collector = new StringBuilder("");
        String subCollector;
        for (MarcSubField aSubfieldList : subfieldList) {
            if (subfields.contains(aSubfieldList.getName())) {
                if (normalize) {
                    subCollector = Normalizer.normalize(aSubfieldList.getValue(), Normalizer.Form.NFD).replaceAll(DIACRITICAL_MARKS, "");
                } else {
                    subCollector = aSubfieldList.getValue();
                }
                subCollector = subCollector.toLowerCase().replaceAll(ALPHA_NUMERIC_DANISH_CHARS, "");
                if (cut > 0) {
                    if (subCollector.length() > cut) {
                        subCollector = subCollector.substring(0, cut);
                    }
                }
                collector.append(subCollector);
            }
        }
        return collector.toString();
    }

    /**
     * Compares two lists of specified subfields possibly with normalization and limit
     * Extraction of data need to be done even if one of the lists are null because the content of the subfields may be empty
     * which is comparable with null.
     *
     * @param oldList   subfieldlist from existing record
     * @param newList   subfieldlist from new record
     * @param subfields subfields that should be compared
     * @param normalize toLower and removing accents
     * @param cut       reduced range of subfield content
     * @return returns true if equal otherwise false
     */
    private boolean compareSubfieldContent(List<MarcSubField> oldList, List<MarcSubField> newList, String subfields, boolean normalize, int cut) {
        if (oldList == null && newList == null) {
            logger.info("compareSubfieldContent - both NULL");
            return true;
        }
        String oldMatch = getCompareString(oldList, subfields, normalize, cut);
        String newMatch = getCompareString(newList, subfields, normalize, cut);
        logger.info("Old str <{}>, new str <{}>", oldMatch, newMatch);
        return oldMatch.equals(newMatch);
    }

    /**
     * Normalize and cut a string
     *
     * @param input     the string to treat
     * @param normalize should we normalize ?
     * @param cut       should we cut ?
     * @return Nicely trimmed string
     */
    private String cutAndClean(String input, boolean normalize, int cut) {
        String result = "";
        if (normalize) {
            result = Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll(DIACRITICAL_MARKS, "");
        }
        result = result.toLowerCase().replaceAll(ALPHA_NUMERIC_DANISH_CHARS, "");
        if (cut > 0 && result.length() > cut) {
            result = result.substring(0, cut);
        }
        return result;
    }

    /**
     * Compares content of multi occurrence fields
     *
     * @param oldList   reader for old record
     * @param newList   reader for new record
     * @param field     field to check
     * @param subfield  subfields to check
     * @param normalize should we normalize ?
     * @param cut       should we cut ?
     * @return The result of the comparison
     */
    private boolean compareSubfieldContentMultiField(MarcRecordReader oldList, MarcRecordReader newList, String field, String subfield, boolean normalize, int cut) {
        String oldValue = oldList.getValue(field, subfield);
        String newValue = newList.getValue(field, subfield);
        if (oldValue != null && newValue != null) {
            if (cutAndClean(oldValue, normalize, cut).equals(cutAndClean(newValue, normalize, cut))) {
                return true;
            }
        }
        if (oldValue == null && newValue == null) {
            return true;
        }
        return false;
    }

    /**
     * Returns the content of a field/subfield if exists and an empty string if not
     *
     * @param reader   record reader
     * @param field    the wanted field
     * @param subfield the wanted subfield
     * @return subfield content or an empty string
     */
    private String getSubfieldContent(MarcRecordReader reader, String field, String subfield) {
        String value = reader.getValue(field, subfield);
        if (value == null) return "";
        else return value;
    }

    /**
     * Tests if the classifications has changed between 2 records.
     * <p>
     * This method is mainly used to checks for changes between 2 versions of
     * the same record.
     *
     * @param oldRecord The old record.
     * @param newRecord The new record.
     * @return <code>true</code> if there is changes in the classifications,
     * <code>false</code> otherwise.
     */
    public boolean hasClassificationsChanged(MarcRecord oldRecord, MarcRecord newRecord) {
        MarcRecordReader oldReader = new MarcRecordReader(oldRecord);
        MarcRecordReader newReader = new MarcRecordReader(newRecord);

        logger.debug("Old record {}", oldRecord);
        logger.debug("New record {}", newRecord);

        return check008(oldReader, newReader) ||
                check009(oldReader, newReader) ||
                check038(oldReader, newReader) ||
                check039(oldReader, newReader) ||
                check100(oldReader, newReader) ||
                check110(oldReader, newReader) ||
                check239And245(oldReader, newReader) ||
                check245(oldReader, newReader) ||
                check652(oldReader, newReader);

    }

    private boolean check008(MarcRecordReader oldReader, MarcRecordReader newReader) {
        String oldValue = getSubfieldContent(oldReader, "008", "t");
        String newValue = getSubfieldContent(newReader, "008", "t");

        // if 008*t has changed from m or s to p and reverse return true.
        if ((oldValue.equals("m") || oldValue.equals("s")) && newValue.equals("p")) {
            logger.info("Classification has changed - reason 008t m|s -> p");
            return true;
        }

        if ((newValue.equals("m") || newValue.equals("s")) && oldValue.equals("p")) {
            logger.info("Classification has changed - reason 008t p -> m|s");
            return true;
        }

        return false;
    }

    private boolean check009(MarcRecordReader oldReader, MarcRecordReader newReader) {
        // has content of 009ag changed
        // se evt her for baggrund : http://praxis.dbc.dk/formatpraksis/px-for1796.html/#pxkoko
        List<String> oldLowerCaseAGPairs = getLowerCaseAGPairs(oldReader);
        List<String> newLowerCaseAGPairs = getLowerCaseAGPairs(newReader);
        if (!oldLowerCaseAGPairs.containsAll(newLowerCaseAGPairs) || !newLowerCaseAGPairs.containsAll(oldLowerCaseAGPairs)) {
            logger.info("Classification has changed - reason 009ag difference");
            return true;
        } else {
            return false;
        }
    }

    private boolean check038(MarcRecordReader oldReader, MarcRecordReader newReader) {
        String oldValue = getSubfieldContent(oldReader, "038", "a");
        String newValue = getSubfieldContent(newReader, "038", "a");

        // if content of 038a has changed return true
        if (!oldValue.equals(newValue)) {
            logger.info("Classification has changed - reason 038a difference");
            return true;
        } else {
            return false;
        }
    }

    private boolean check039(MarcRecordReader oldReader, MarcRecordReader newReader) {
        MarcField oldField = oldReader.getField("039");
        MarcField newField = newReader.getField("039");
        List<MarcSubField> oldSubfieldList;
        List<MarcSubField> newSubfieldList;

        // if content of 039 has changed return true
        boolean result = false;
        if (oldField != null && newField != null) {
            oldSubfieldList = oldField.getSubfields();
            newSubfieldList = newField.getSubfields();
            if (!oldSubfieldList.containsAll(newSubfieldList) || !newSubfieldList.containsAll(oldSubfieldList)) {
                result = true;
            }
        }

        if (result || ((oldField == null && newField != null) || (oldField != null && newField == null))) {
            logger.info("Classification has changed - reason 039 difference");
            return true;
        } else {
            return false;
        }
    }

    private boolean check100(MarcRecordReader oldReader, MarcRecordReader newReader) {
        MarcField oldField = oldReader.getField("100");
        MarcField newField = newReader.getField("100");

        // if content of 100*[ahkef] stripped has changed return true.
        List<MarcSubField> oldSubfieldList = oldField == null ? null : oldField.getSubfields();
        List<MarcSubField> newSubfieldList = newField == null ? null : newField.getSubfields();

        if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "ahkef", true, 0)) {
            logger.info("Classification has changed - reason 100ahkef difference");
            return true;
        } else {
            return false;
        }
    }

    private boolean check110(MarcRecordReader oldReader, MarcRecordReader newReader) {
        MarcField oldField = oldReader.getField("110");
        MarcField newField = newReader.getField("110");

        // if content of 110*[saceikj] stripped has changed return true
        List<MarcSubField> oldSubfieldList = oldField == null ? null : oldField.getSubfields();
        List<MarcSubField> newSubfieldList = newField == null ? null : newField.getSubfields();

        if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "saceikj", true, 0)) {
            logger.info("Classification has changed - reason 110saceikj difference");
            return true;
        } else {
            return false;
        }
    }


    private boolean check239And245(MarcRecordReader oldReader, MarcRecordReader newReader) {
        List<MarcSubField> oldSubfieldList;
        List<MarcSubField> newSubfieldList;
        MarcField oldField;
        MarcField newField;
        String newValue;

        // 239 og evt 245 check :
        // hvis der ikke er 239 i old/new så skal der checkes 245a (se senere)
        // hvis der er 239 i begge, så skal 239 checkes. Hvis der ikke er 239t i ny post så skal 245a checkes
        // hvis der kun er 239 i en af posterne, så skal 239 checkes hvis old245a != new239t og omvendt. 245a check følger dette.
        //      hvis der er et 239t og det er forskelligt fra 245a så skal der returneres (ingen grund til at checke øvrige delfelter).
        //
        // hvis 245a har ændret sig :
        // hvis 004a = s og der er et 245n i mindst en af posterne og 245n ikke har ændet sig så skal 239 betinget 245 check ikke gælde
        // hvis 004a = b og der er et 245g i mindst en af posterne og 245g ikke har ændet sig så skal 239 betinget 245 check ikke gælde
        // if checkField : if 239*[ahkeftø] stripped 10 has changed return true.
        oldField = oldReader.getField("239");
        newField = newReader.getField("239");
        boolean checkField239 = false;
        boolean checkField245 = true;
        String f245a = "";
        if (oldField == null) {
            if (newField != null) {
                MarcField Field245 = oldReader.getField("245");
                if (Field245 != null) {
                    f245a = getCompareString(Field245.getSubfields(), "a", true, 10);
                }
                String f239t = getCompareString(newReader.getField("239").getSubfields(), "t", true, 10);
                checkField239 = !f245a.equals(f239t);
                if (checkField239 && !f239t.equals("")) {
                    logger.info("Classification has changed - reason 239t difference");
                    return true;
                }
                checkField245 = checkField239;
            }
        } else {
            if (newField == null) {
                MarcField Field245 = newReader.getField("245");
                if (Field245 != null) {
                    f245a = getCompareString(Field245.getSubfields(), "a", true, 10);
                }
                String f239t = getCompareString(oldReader.getField("239").getSubfields(), "t", true, 10);
                checkField239 = !f245a.equals(f239t);
                if (checkField239 && !f239t.equals("")) {
                    logger.info("Classification has changed - reason 239t difference ");
                    return true;
                }
                checkField245 = checkField239;
            } else {
                checkField239 = true;
                newValue = newReader.getValue("239", "t");
                if (newValue != null) {
                    checkField245 = false;
                }
            }
        }

        if (checkField239) {
            oldSubfieldList = oldField == null ? null : oldField.getSubfields();
            newSubfieldList = newField == null ? null : newField.getSubfields();
            if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "ahkeft\u00F8", true, 10)) {
                logger.info("Classification has changed - reason 239ahkeft\u00F8 difference");
                return true;
            }
        }

        // if 245a stripped 10 has changed then
        //      if new 004a == s then
        //          get 245n stripped from old and new
        //          if equal check245a false
        //      else
        //      if new 004a == b then
        //          get 245g stripped 10 from old and new
        //          if equal check245a false
        //  if check245a
        //      return true.
        oldField = oldReader.getField("245");
        newField = newReader.getField("245");
        oldSubfieldList = oldField == null ? null : oldField.getSubfields();
        newSubfieldList = newField == null ? null : newField.getSubfields();
        if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "a", true, 10)) {
            newValue = newReader.getValue("004", "a");
            newValue = newValue == null ? "" : newValue;
            if (newValue.equals("s")) {
                if (compareSubfieldContent(oldSubfieldList, newSubfieldList, "n", true, 0)) {
                    checkField245 = false;
                }
            } else {
                if (newValue.equals("b")) {
                    if (compareSubfieldContent(oldSubfieldList, newSubfieldList, "g", true, 0)) {
                        checkField245 = false;
                    }
                }
            }
            if (checkField245) {
                logger.info("Classification has changed - reason 245a difference");
                return true;
            }
        }

        return false;
    }

    private boolean check245(MarcRecordReader oldReader, MarcRecordReader newReader) {
        MarcField oldField = oldReader.getField("245");
        MarcField newField = newReader.getField("245");
        List<MarcSubField> oldSubfieldList = oldField == null ? null : oldField.getSubfields();
        List<MarcSubField> newSubfieldList = newField == null ? null : newField.getSubfields();

        //  if 245g stripped 10 changed return true
        if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "g", true, 10)) {
            logger.info("Classification has changed - reason 245g difference");
            return true;
        }
        //  if 245m changed return true
        if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "m", false, 0)) {
            logger.info("Classification has changed - reason 245m difference");
            return true;
        }
        //  if 245n stripped changed return true
        if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "n", true, 0)) {
            logger.info("Classification has changed - reason 245n difference");
            return true;
        }
        //  if 245o stripped 10 changed return true
        if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "o", true, 10)) {
            logger.info("Classification has changed - reason 245o difference");
            return true;
        }
        //  if 245y stripped 10 changed return true
        if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "y", true, 10)) {
            logger.info("Classification has changed - reason 245y difference");
            return true;
        }
        //  if 245æ stripped 10 changed return true
        if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "\u00E6", true, 10)) {
            logger.info("Classification has changed - reason 245æ difference");
            return true;
        }
        //  if 245ø stripped 10 changed return true
        if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "\u00F8", true, 10)) {
            logger.info("Classification has changed - reason 245ø difference");
            return true;
        }

        return false;
    }

    private boolean check652(MarcRecordReader oldReader, MarcRecordReader newReader) {
        // 652 section
        //  if 652a stripped 10 changed return true
        if (!compareSubfieldContentMultiField(oldReader, newReader, "652", "a", true, 10)) {
            logger.info("Classification has changed - reason 652a difference");
            return true;
        }

        //  if 652b stripped 10 changed return true
        if (!compareSubfieldContentMultiField(oldReader, newReader, "652", "b", true, 10)) {
            logger.info("Classification has changed - reason 652b difference");
            return true;
        }

        //  if 652m or 652o then
        //      if 652e stripped changed return true
        //      if 652f stripped changed return true
        //      if 652h stripped changed return true
        String f652m = oldReader.getValue("652", "m");
        String f652o = oldReader.getValue("652", "o");
        if (f652m != null || f652o != null) {
            if (!compareSubfieldContentMultiField(oldReader, newReader, "652", "e", true, 0)) {
                logger.info("Classification has changed - reason 652m|o : subfield e difference");
                return true;
            }
            if (!compareSubfieldContentMultiField(oldReader, newReader, "652", "f", true, 0)) {
                logger.info("Classification has changed - reason 652m|o : subfield f difference");
                return true;
            }
            if (!compareSubfieldContentMultiField(oldReader, newReader, "652", "h", true, 0)) {
                logger.info("Classification has changed - reason 652m|o : subfield h difference");
                return true;
            }

        }

        //  if 652m stripped changed return true
        if (!compareSubfieldContentMultiField(oldReader, newReader, "652", "m", true, 0)) {
            logger.info("Classification has changed - reason 652m difference");
            return true;
        }

        //  if 652o stripped changed return true
        if (!compareSubfieldContentMultiField(oldReader, newReader, "652", "o", true, 0)) {
            logger.info("Classification has changed - reason 652o difference");
            return true;
        }

        return false;
    }

    /**
     * Creates an extended library record based on the bibliographic
     * classification elements of the record from DBC
     *
     * @param currentCommonRecord The record from DBC.
     * @param agencyId            The library id for the library, that the extended
     *                            record will be created for.
     * @return Returns the library record after it has been updated.
     * <code>libraryRecord</code> may have changed.
     * @throws ScripterException in case of an error
     */
    public MarcRecord createLibraryExtendedRecord(MarcRecord currentCommonRecord, MarcRecord updatingCommonRecord, String agencyId) throws ScripterException {
        logger.entry(currentCommonRecord, updatingCommonRecord, agencyId);
        Object jsResult = null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            String jsonCurrentCommonRecord = mapper.writeValueAsString(currentCommonRecord);
            String jsonUpdatingCommonRecord = mapper.writeValueAsString(updatingCommonRecord);
            jsResult = scripter.callMethod("createLibraryExtendedRecord", jsonCurrentCommonRecord, jsonUpdatingCommonRecord, Integer.valueOf(agencyId));
            logger.debug("Result from createLibraryExtendedRecord JS ({}): {}", jsResult.getClass().getName(), jsResult);
            if (jsResult instanceof String) {
                return mapper.readValue(jsResult.toString(), MarcRecord.class);
            }
            throw new ScripterException(String.format("The JavaScript function %s must return a String value.", "createLibraryExtendedRecord"));
        } catch (IOException ex) {
            throw new ScripterException("Error when executing JavaScript function: createLibraryExtendedRecord", ex);
        } finally {
            logger.exit(jsResult);
        }
    }

    /**
     * This method updates an extended library record based on the bibliographic
     * classification elements of the record from DBC
     *
     * @param currentCommonRecord  The record from DBC.
     * @param updatingCommonRecord The record that is being updated.
     * @param enrichmentRecord     The library extended record.
     * @return Returns the library record after it has been updated.
     * <code>enrichmentRecord</code> may have changed.
     * @throws ScripterException in case of an error
     */
    public MarcRecord updateLibraryExtendedRecord(MarcRecord currentCommonRecord, MarcRecord updatingCommonRecord, MarcRecord enrichmentRecord) throws ScripterException {
        logger.entry(currentCommonRecord, updatingCommonRecord, enrichmentRecord);
        Object jsResult = null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            String jsonCurrentCommonRecord = mapper.writeValueAsString(currentCommonRecord);
            String jsonUpdatingCommonRecord = mapper.writeValueAsString(updatingCommonRecord);
            String jsonEnrichmentRecord = mapper.writeValueAsString(enrichmentRecord);

            jsResult = scripter.callMethod("updateLibraryExtendedRecord", jsonCurrentCommonRecord, jsonUpdatingCommonRecord, jsonEnrichmentRecord);

            logger.debug("Result from updateLibraryExtendedRecord JS ({}): {}", jsResult.getClass().getName(), jsResult);

            if (jsResult instanceof String) {
                return mapper.readValue(jsResult.toString(), MarcRecord.class);
            }

            throw new ScripterException(String.format("The JavaScript function %s must return a String value.", "updateLibraryExtendedRecord"));
        } catch (IOException ex) {
            throw new ScripterException("Error when executing JavaScript function: updateLibraryExtendedRecord", ex);
        } finally {
            logger.exit(jsResult);
        }
    }

    public MarcRecord correctLibraryExtendedRecord(MarcRecord commonRecord, MarcRecord enrichmentRecord) throws ScripterException {
        logger.entry(commonRecord, enrichmentRecord);
        Object jsResult = null;

        try {
            ObjectMapper mapper = new ObjectMapper();
            String jsonCommonRecord = mapper.writeValueAsString(commonRecord);
            String jsonEnrichmentRecord = mapper.writeValueAsString(enrichmentRecord);

            jsResult = scripter.callMethod("correctLibraryExtendedRecord", jsonCommonRecord, jsonEnrichmentRecord);

            logger.debug("Result from correctLibraryExtendedRecord JS ({}): {}", jsResult.getClass().getName(), jsResult);

            if (jsResult instanceof String) {
                return mapper.readValue(jsResult.toString(), MarcRecord.class);
            }

            throw new ScripterException(String.format("The JavaScript function %s must return a String value.", "correctLibraryExtendedRecord"));
        } catch (IOException ex) {
            throw new ScripterException("Error when executing JavaScript function: correctLibraryExtendedRecord", ex);
        } finally {
            logger.exit(jsResult);
        }
    }

    /**
     * This function will split (if necessary) the input record into common record and DBC enrichment record
     *
     * @param record            The record to be updated
     * @param authenticationDto Auth DTO from the ws request
     * @param updateMode        Whether it is a FBS or DataIO template
     * @return a list of records to put in rawrepo
     * @throws OpenAgencyException          in case of an error
     * @throws UnsupportedEncodingException in case of an error
     * @throws UpdateException              in case of an error
     */
    public List<MarcRecord> recordDataForRawRepo(MarcRecord record, AuthenticationDto authenticationDto, UpdateMode updateMode) throws OpenAgencyException, UnsupportedEncodingException, UpdateException {
        logger.entry(record, authenticationDto, updateMode);

        List<MarcRecord> result = new ArrayList<>();

        try {
            if (updateMode.isFBSMode()) {
                result = recordDataForRawRepoFBS(record, authenticationDto.getGroupId());
            } else { // Assuming DataIO mode
                result = recordDataForRawRepoDataIO(record, authenticationDto.getGroupId());
            }

            return result;
        } finally {
            logger.exit(result);
        }
    }

    private List<MarcRecord> recordDataForRawRepoFBS(MarcRecord record, String groupId) throws OpenAgencyException, UpdateException, UnsupportedEncodingException {
        logger.entry(record, groupId);

        List<MarcRecord> result = new ArrayList<>();

        try {
            result = splitRecordFBS(record, groupId);

            for (MarcRecord r : result) {
                MarcRecordWriter writer = new MarcRecordWriter(r);
                writer.setChangedTimestamp();
            }

            return result;
        } finally {
            logger.exit(result);
        }
    }

    private List<MarcRecord> recordDataForRawRepoDataIO(MarcRecord record, String groupId) throws OpenAgencyException {
        logger.entry(record, groupId);

        List<MarcRecord> result = new ArrayList<>();

        try {
            if (openAgencyService.hasFeature(groupId, LibraryRuleHandler.Rule.USE_ENRICHMENTS) ||
                    openAgencyService.hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_ROOT)) {

                logger.info("Record has either USE_ENRICHMENT or AUTH_ROOT so calling splitCompleteBasisRecord");
                result = splitRecordDataIO(record);
            } else {
                logger.info("Record has neither USE_ENRICHMENT nor AUTH_ROOT so returning record");
                result = Arrays.asList(record);
            }

            return result;

        } finally {
            logger.exit(result);
        }
    }

    /**
     * If the FBS record is an existing common (870970) record then split it into updated common record and
     * DBC enrichment record
     *
     * @param record  The record to be updated
     * @param groupId The groupId from the ws request
     * @return List containing common and DBC record
     * @throws OpenAgencyException          in case of an error
     * @throws UpdateException              in case of an error
     * @throws UnsupportedEncodingException in case of an error
     */
    private List<MarcRecord> splitRecordFBS(MarcRecord record, String groupId) throws OpenAgencyException, UpdateException, UnsupportedEncodingException {
        logger.entry(record, groupId);

        try {
            MarcRecordReader reader = new MarcRecordReader(record);
            if (!reader.agencyIdAsInteger().equals(RawRepo.RAWREPO_COMMON_LIBRARY)) {
                logger.info("Agency id of record is not 870970 - returning same record");
                return Arrays.asList(record);
            }

            NoteAndSubjectExtensionsHandler noteAndSubjectExtensionsHandler = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo);

            MarcRecord correctedRecord = noteAndSubjectExtensionsHandler.recordDataForRawRepo(record, groupId);

            MarcRecordReader correctedRecordReader = new MarcRecordReader(correctedRecord);
            MarcRecord dbcEnrichmentRecord;

            String recId = correctedRecordReader.recordId();
            Integer agencyId = RawRepo.RAWREPO_COMMON_LIBRARY;

            MarcRecord curRecord;

            if (rawRepo.recordExists(recId, RawRepo.RAWREPO_COMMON_LIBRARY)) {
                curRecord = new RawRepoDecoder().decodeRecord(rawRepo.fetchRecord(recId, agencyId).getContent());
                correctedRecord = UpdateOwnership.mergeRecord(correctedRecord, curRecord);
                logger.info("correctedRecord after mergeRecord\n{}", correctedRecord);
            } else {
                logger.info("Common record [{}:{}] does not exist", recId, RawRepo.RAWREPO_COMMON_LIBRARY);
            }

            String owner = correctedRecordReader.getValue("996", "a");

            if (owner == null) {
                logger.debug("No owner in record.");

                return Arrays.asList(correctedRecord);
            } else {
                logger.info("Owner of record is {}", owner);
            }

            if (!rawRepo.recordExists(recId, RawRepo.COMMON_LIBRARY)) {
                logger.debug("DBC enrichment record [{}:{}] does not exist.", recId, RawRepo.COMMON_LIBRARY);
                dbcEnrichmentRecord = new MarcRecord();
                MarcField corrected001Field = new MarcField(correctedRecordReader.getField("001"));
                dbcEnrichmentRecord.getFields().add(corrected001Field);

                new MarcRecordWriter(dbcEnrichmentRecord).addOrReplaceSubfield("001", "b", RawRepo.COMMON_LIBRARY.toString());
            } else {
                logger.debug("DBC enrichment record [{}:{}] found.", recId, RawRepo.COMMON_LIBRARY);
                dbcEnrichmentRecord = new RawRepoDecoder().decodeRecord(rawRepo.fetchRecord(recId, RawRepo.COMMON_LIBRARY).getContent());
            }

            String recordStatus = correctedRecordReader.getValue("004", "r");
            if (recordStatus != null) {
                logger.debug("Replace 004 *r in DBC enrichment record with: {}", recordStatus);
                new MarcRecordWriter(dbcEnrichmentRecord).addOrReplaceSubfield("004", "r", recordStatus);
            }

            String recordType = correctedRecordReader.getValue("004", "a");
            if (recordType != null) {
                logger.debug("Replace 004 *a in DBC enrichment record with: {}", recordType);
                new MarcRecordWriter(dbcEnrichmentRecord).addOrReplaceSubfield("004", "a", recordType);
            }

            logger.info("correctedRecord\n{}", correctedRecord);
            logger.info("dbcEnrichmentRecord\n{}", dbcEnrichmentRecord);

            return Arrays.asList(correctedRecord, dbcEnrichmentRecord);
        } finally {
            logger.exit();
        }
    }

    /**
     * Split the input record into two record:
     * One with all normal Marc fields (001 - 999) and a new record with DBC fields
     *
     * @param record The record to be updated
     * @return List containing common and DBC record
     */
    List<MarcRecord> splitRecordDataIO(MarcRecord record) {
        logger.entry(record);

        try {
            MarcRecord dbcRecord = new MarcRecord();
            MarcRecord commonRecord = new MarcRecord();

            for (int i = 0; i < record.getFields().size(); i++) {
                MarcField field = record.getFields().get(i);
                if (field.getName().equals("001")) {
                    MarcField dbcField = new MarcField(field);
                    for (int d = 0; d < dbcField.getSubfields().size(); d++) {
                        if (dbcField.getSubfields().get(d).getName().equals("b")) {
                            dbcField.getSubfields().get(d).setValue(RawRepo.COMMON_LIBRARY.toString());
                        }
                    }
                    dbcRecord.getFields().add(dbcField);

                    MarcField commonField = new MarcField(field);
                    for (int c = 0; c < commonField.getSubfields().size(); c++) {
                        if (commonField.getSubfields().get(c).getName().equals("b")) {
                            commonField.getSubfields().get(c).setValue(RawRepo.RAWREPO_COMMON_LIBRARY.toString());
                        }
                    }
                    commonRecord.getFields().add(commonField);

                } else if (field.getName().equals("004")) {
                    dbcRecord.getFields().add(field);
                    commonRecord.getFields().add(field);
                } else if (field.getName().matches("[a-z].*")) {
                    dbcRecord.getFields().add(field);
                } else {
                    commonRecord.getFields().add(field);
                }
            }


            logger.info("commonRecord\n{}", commonRecord);
            logger.info("dbcRecord\n{}", dbcRecord);

            return Arrays.asList(commonRecord, dbcRecord);
        } finally {
            logger.exit();
        }
    }


    /**
     * Creates a 512 notefield from one record.
     * utilizes the create512 notefield functionality , which expects two records, one current, one updating
     * In this case we are not updating , but just wants a 512 field from existing data.
     *
     * @param record The record.
     * @return MarcField containing 512 data
     * @throws ScripterException in case of an error
     */

    public MarcField fetchNoteField(MarcRecord record) throws ScripterException {
        logger.entry(record);

        MarcField mf = null;
        Object jsResult;
        ObjectMapper mapper = new ObjectMapper();
        try {
            try {
                String json = mapper.writeValueAsString(record);
                jsResult = scripter.callMethod("recategorizationNoteFieldFactory", json);
            } catch (IOException ex) {
                throw new ScripterException("Error when executing JavaScript function: fetchNoteField", ex);
            }
            logger.debug("Result from recategorizationNoteFieldFactory JS ({}): {}", jsResult.getClass().getName(), jsResult);

            if (!(jsResult instanceof String)) {
                throw new ScripterException("The JavaScript function %s must return a String value.", "recordDataForRawRepo");
            }
            return mf = mapper.readValue((String) jsResult, MarcField.class);

        } catch (IOException ex) {
            throw new ScripterException("Error when executing JavaScript function: changeUpdateRecordForUpdate", ex);
        } finally {
            logger.exit(mf);
        }
    }
}
