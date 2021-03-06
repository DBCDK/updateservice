/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.common.records.CatalogExtractionCode;
import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcFieldReader;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.common.records.MarcSubField;
import dk.dbc.common.records.UpdateOwnership;
import dk.dbc.common.records.utils.LogUtils;
import dk.dbc.common.records.utils.RecordContentTransformer;
import dk.dbc.jsonb.JSONBException;
import dk.dbc.opencat.connector.OpencatBusinessConnector;
import dk.dbc.opencat.connector.OpencatBusinessConnectorException;
import dk.dbc.vipcore.exception.VipCoreException;
import dk.dbc.vipcore.libraryrules.VipCoreLibraryRulesConnector;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.MDC;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;

import static dk.dbc.updateservice.utils.MDCUtil.MDC_TRACKING_ID_LOG_CONTEXT;

/**
 * Class to manipulate library records for a local library. Local records and
 * local extended records.
 * <p>
 */
@Stateless
public class LibraryRecordsHandler {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(LibraryRecordsHandler.class);
    private static final List<String> CLASSIFICATION_FIELDS = Arrays.asList("008", "009", "038", "039", "100", "110", "239", "245", "652");
    private static final List<String> REFERENCE_FIELDS = Arrays.asList("900", "910", "945");
    private static final List<String> RECORD_CONTROL_FIELDS = Arrays.asList("001", "004", "996");
    private static final List<String> CONTROL_AND_CLASSIFICATION_FIELDS = new ArrayList<>();
    private static final List<String> IGNORABLE_CONTROL_SUBFIELDS = Arrays.asList("&", "0", "1", "4");
    private static final String DIACRITICAL_MARKS = "[\\p{InCombiningDiacriticalMarks}]";
    private static final String ALPHA_NUMERIC_DANISH_CHARS = "[^a-z0-9\u00E6\u00F8\u00E5]";

    @EJB
    private VipCoreService vipCoreService;

    @Inject
    private OpencatBusinessConnector opencatBusinessConnector;

    @EJB
    private RawRepo rawRepo;

    @PostConstruct
    public void setList() {
        CONTROL_AND_CLASSIFICATION_FIELDS.addAll(RECORD_CONTROL_FIELDS);
        CONTROL_AND_CLASSIFICATION_FIELDS.addAll(CLASSIFICATION_FIELDS);
    }

    /**
     * Ignore the fact that intellij claims this is never used.
     * If it doesn't exist then the following error will come when deploying :
     * javax.enterprise.system.core ======> Exception while loading the app : EJB Container initialization error
     * java.lang.NoSuchMethodError: dk.dbc.updateservice.update.LibraryRecordsHandler: method <init>()V not found
     */
    public LibraryRecordsHandler() {
    }

    /**
     * Tests if a record is published
     *
     * @param record The record.
     * @return <code>true</code> if published
     * <code>false</code> otherwise.
     */
    public boolean isRecordInProduction(MarcRecord record) {
        LOGGER.entry(record);

        try {
            return CatalogExtractionCode.isUnderProduction(record);
        } finally {
            LOGGER.exit();
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
        LOGGER.entry(record);
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
            LOGGER.exit(result);
        }
    }

    /**
     * Collect pairs of subfields a and g from field 009
     *
     * @param reader reader for the record
     * @return List of paired subfields
     */
    private List<String> getLowerCaseAGPairs(MarcRecordReader reader) {
        final List<String> result = new ArrayList<>();
        MarcField field;
        field = reader.getField("009");
        if (field == null) {
            LOGGER.info("field NULL");
            return result;
        }
        final List<MarcSubField> SubfieldList = field.getSubfields();
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
        final StringBuilder collector = new StringBuilder();
        String subCollector;
        for (MarcSubField aSubfieldList : subfieldList) {
            if (subfields.contains(aSubfieldList.getName())) {
                if (normalize) {
                    subCollector = Normalizer.normalize(aSubfieldList.getValue(), Normalizer.Form.NFD).replaceAll(DIACRITICAL_MARKS, "");
                } else {
                    subCollector = aSubfieldList.getValue();
                }
                subCollector = subCollector.toLowerCase().replaceAll(ALPHA_NUMERIC_DANISH_CHARS, "");
                if (cut > 0 && subCollector.length() > cut) {
                    subCollector = subCollector.substring(0, cut);
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
            LOGGER.info("compareSubfieldContent - both NULL");
            return true;
        }
        final String oldMatch = getCompareString(oldList, subfields, normalize, cut);
        final String newMatch = getCompareString(newList, subfields, normalize, cut);
        LOGGER.info("Old str <{}>, new str <{}>", oldMatch, newMatch);
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
        final String oldValue = oldList.getValue(field, subfield);
        final String newValue = newList.getValue(field, subfield);

        return oldValue != null &&
                newValue != null &&
                cutAndClean(oldValue, normalize, cut).equals(cutAndClean(newValue, normalize, cut)) || oldValue == null && newValue == null;

    }

    private boolean compareMultiSubfieldContentMultiField(MarcRecordReader oldReader, MarcRecordReader newReader, String field, String subfield, boolean normalize, int cut) {
        final List<String> oldValues = oldReader.getValues(field, subfield);
        final List<String> newValues = newReader.getValues(field, subfield);

        if (oldValues.size() != newValues.size()) {
            return false;
        }

        Collections.sort(oldValues);
        Collections.sort(newValues);

        for (int i = 0; i < oldValues.size(); i++) {
            if (!cutAndClean(oldValues.get(i), normalize, cut).equals(cutAndClean(newValues.get(i), normalize, cut))) {
                return false;
            }
        }

        return true;
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
        final String value = reader.getValue(field, subfield);
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
        return hasClassificationsChanged(oldRecord, newRecord, new ArrayList<>());
    }

    /**
     * Tests if the classifications has changed between 2 records.
     * <p>
     * This method is mainly used to checks for changes between 2 versions of
     * the same record.
     *
     * @param oldRecord                     The old record.
     * @param newRecord                     The new record.
     * @param classificationsChangedMessage List of reasons for the eventual classification change
     * @return <code>true</code> if there is changes in the classifications,
     * <code>false</code> otherwise.
     */
    public boolean hasClassificationsChanged(MarcRecord oldRecord, MarcRecord newRecord, List<String> classificationsChangedMessage) {
        final MarcRecordReader oldReader = new MarcRecordReader(oldRecord);
        final MarcRecordReader newReader = new MarcRecordReader(newRecord);


        // It is wanted to compare the full data content when deciding if classification has changed.
        // This would normally mean removal of this variable and fix things where it has effect.
        // Though, it has been mentioned that it could change in the future, so we don't do the nice stuff.
        int compareLength = 0;

        // If the library is a FBS library then we need to compare the full subfield values and not just the first 10 chars
        if (RawRepo.COMMON_AGENCY != newReader.getAgencyIdAsInt()) {
            compareLength = 0; // 0 = compare whole string
        }

        LOGGER.debug("Old record\n{}", oldRecord);
        LOGGER.debug("New record\n{}", newRecord);

        return check008(oldReader, newReader, classificationsChangedMessage) ||
                check009(oldReader, newReader, classificationsChangedMessage) ||
                check038(oldReader, newReader, classificationsChangedMessage) ||
                check039(oldReader, newReader, classificationsChangedMessage) ||
                check100(oldReader, newReader, classificationsChangedMessage) ||
                check110(oldReader, newReader, classificationsChangedMessage) ||
                check239And245(oldReader, newReader, compareLength, classificationsChangedMessage) ||
                check245(oldReader, newReader, compareLength, classificationsChangedMessage) ||
                check652(oldReader, newReader, compareLength, classificationsChangedMessage);
    }

    private boolean check008(MarcRecordReader oldReader, MarcRecordReader newReader, List<String> classificationsChangedMessage) {
        final String oldValue = getSubfieldContent(oldReader, "008", "t");
        final String newValue = getSubfieldContent(newReader, "008", "t");

        // if 008*t has changed from m or s to p and reverse return true.
        if ((oldValue.equals("m") || oldValue.equals("s")) && newValue.equals("p")) {
            classificationsChangedMessage.add("classificationchanged.reason.008t.ms.to.p");
            LOGGER.info("Classification has changed - reason 008t m|s -> p");
            return true;
        }

        if ((newValue.equals("m") || newValue.equals("s")) && oldValue.equals("p")) {
            classificationsChangedMessage.add("classificationchanged.reason.008t.p.to.ms");
            LOGGER.info("Classification has changed - reason 008t p -> m|s");
            return true;
        }

        return false;
    }

    private boolean check009(MarcRecordReader oldReader, MarcRecordReader newReader, List<String> classificationsChangedMessage) {
        // has content of 009ag changed
        // se evt her for baggrund : http://praxis.dbc.dk/formatpraksis/px-for1796.html/#pxkoko
        final List<String> oldLowerCaseAGPairs = getLowerCaseAGPairs(oldReader);
        final List<String> newLowerCaseAGPairs = getLowerCaseAGPairs(newReader);
        if (!oldLowerCaseAGPairs.containsAll(newLowerCaseAGPairs) || !newLowerCaseAGPairs.containsAll(oldLowerCaseAGPairs)) {
            classificationsChangedMessage.add("classificationchanged.reason.009ag.difference");
            LOGGER.info("Classification has changed - reason 009ag difference");
            return true;
        } else {
            return false;
        }
    }

    private boolean check038(MarcRecordReader oldReader, MarcRecordReader newReader, List<String> classificationsChangedMessage) {
        final String oldValue = getSubfieldContent(oldReader, "038", "a");
        final String newValue = getSubfieldContent(newReader, "038", "a");

        // if content of 038a has changed return true
        if (!oldValue.equals(newValue)) {
            classificationsChangedMessage.add("classificationchanged.reason.038a.difference");
            LOGGER.info("Classification has changed - reason 038a difference");
            return true;
        } else {
            return false;
        }
    }

    private boolean check039(MarcRecordReader oldReader, MarcRecordReader newReader, List<String> classificationsChangedMessage) {
        final MarcField oldField = oldReader.getField("039");
        final MarcField newField = newReader.getField("039");
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

        if (result || oldField == null && newField != null || oldField != null && newField == null) {
            classificationsChangedMessage.add("classificationchanged.reason.039.difference");
            LOGGER.info("Classification has changed - reason 039 difference");
            return true;
        } else {
            return false;
        }
    }

    private boolean check100(MarcRecordReader oldReader, MarcRecordReader newReader, List<String> classificationsChangedMessage) {
        final MarcField oldField = oldReader.getField("100");
        final MarcField newField = newReader.getField("100");

        // if content of 100*[ahkef] stripped has changed return true.
        final List<MarcSubField> oldSubfieldList = oldField == null ? null : oldField.getSubfields();
        final List<MarcSubField> newSubfieldList = newField == null ? null : newField.getSubfields();

        if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "ahkef", true, 0)) {
            classificationsChangedMessage.add("classificationchanged.reason.100ahkef.difference");
            LOGGER.info("Classification has changed - reason 100ahkef difference");
            return true;
        } else {
            return false;
        }
    }

    private boolean check110(MarcRecordReader oldReader, MarcRecordReader newReader, List<String> classificationsChangedMessage) {
        final MarcField oldField = oldReader.getField("110");
        final MarcField newField = newReader.getField("110");

        // if content of 110*[saceikj] stripped has changed return true
        final List<MarcSubField> oldSubfieldList = oldField == null ? null : oldField.getSubfields();
        final List<MarcSubField> newSubfieldList = newField == null ? null : newField.getSubfields();

        if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "saceikj", true, 0)) {
            classificationsChangedMessage.add("classificationchanged.reason.110saceikj.difference");
            LOGGER.info("Classification has changed - reason 110saceikj difference");
            return true;
        } else {
            return false;
        }
    }


    private boolean check239And245(MarcRecordReader oldReader, MarcRecordReader newReader, int cut, List<String> classificationsChangedMessage) {
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
                final MarcField Field245 = oldReader.getField("245");
                if (Field245 != null) {
                    f245a = getCompareString(Field245.getSubfields(), "a", true, cut);
                }
                final String f239t = getCompareString(newReader.getField("239").getSubfields(), "t", true, cut);
                checkField239 = !f245a.equals(f239t);
                if (checkField239 && !f239t.equals("")) {
                    classificationsChangedMessage.add("classificationchanged.reason.239t.difference");
                    LOGGER.info("Classification has changed - reason 239t difference");
                    return true;
                }
                checkField245 = checkField239;
            }
        } else {
            if (newField == null) {
                final MarcField Field245 = newReader.getField("245");
                if (Field245 != null) {
                    f245a = getCompareString(Field245.getSubfields(), "a", true, cut);
                }
                final String f239t = getCompareString(oldReader.getField("239").getSubfields(), "t", true, cut);
                checkField239 = !f245a.equals(f239t);
                if (checkField239 && !f239t.equals("")) {
                    classificationsChangedMessage.add("classificationchanged.reason.239t.difference");
                    LOGGER.info("Classification has changed - reason 239t difference ");
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
            if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "ahkeft\u00F8", true, cut)) {
                classificationsChangedMessage.add("classificationchanged.reason.110saceikjå.difference");
                LOGGER.info("Classification has changed - reason 239ahkeft\u00F8 difference");
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
        if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "a", true, cut)) {
            newValue = newReader.getValue("004", "a");
            newValue = newValue == null ? "" : newValue;
            if (newValue.equals("s")) {
                if (compareSubfieldContent(oldSubfieldList, newSubfieldList, "n", true, 0)) {
                    checkField245 = false;
                }
            } else {
                if (newValue.equals("b") && compareSubfieldContent(oldSubfieldList, newSubfieldList, "g", true, 0)) {
                    checkField245 = false;
                }
            }
            if (checkField245) {
                classificationsChangedMessage.add("classificationchanged.reason.245a.difference");
                LOGGER.info("Classification has changed - reason 245a difference");
                return true;
            }
        }

        return false;
    }

    private boolean check245(MarcRecordReader oldReader, MarcRecordReader newReader, int cut, List<String> classificationsChangedMessage) {
        final MarcField oldField = oldReader.getField("245");
        final MarcField newField = newReader.getField("245");
        final List<MarcSubField> oldSubfieldList = oldField == null ? null : oldField.getSubfields();
        final List<MarcSubField> newSubfieldList = newField == null ? null : newField.getSubfields();

        if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "g", true, cut)) {
            classificationsChangedMessage.add("classificationchanged.reason.245g.difference");
            LOGGER.info("Classification has changed - reason 245g difference");
            return true;
        }

        if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "m", false, 0)) {
            classificationsChangedMessage.add("classificationchanged.reason.245m.difference");
            LOGGER.info("Classification has changed - reason 245m difference");
            return true;
        }

        if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "n", true, 0)) {
            classificationsChangedMessage.add("classificationchanged.reason.245n.difference");
            LOGGER.info("Classification has changed - reason 245n difference");
            return true;
        }

        if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "o", true, cut)) {
            classificationsChangedMessage.add("classificationchanged.reason.245o.difference");
            LOGGER.info("Classification has changed - reason 245o difference");
            return true;
        }

        if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "y", true, cut)) {
            classificationsChangedMessage.add("classificationchanged.reason.245y.difference");
            LOGGER.info("Classification has changed - reason 245y difference");
            return true;
        }

        if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "\u00E6", true, cut)) {
            classificationsChangedMessage.add("classificationchanged.reason.245æ.difference");
            LOGGER.info("Classification has changed - reason 245æ difference");
            return true;
        }

        if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "\u00F8", true, cut)) {
            classificationsChangedMessage.add("classificationchanged.reason.245ø.difference");
            LOGGER.info("Classification has changed - reason 245ø difference");
            return true;
        }

        return false;
    }

    private boolean check652(MarcRecordReader oldReader, MarcRecordReader newReader, int cut, List<String> classificationsChangedMessage) {
        // 652 section
        if (!compareMultiSubfieldContentMultiField(oldReader, newReader, "652", "a", true, cut)) {
            classificationsChangedMessage.add("classificationchanged.reason.652a.difference");
            LOGGER.info("Classification has changed - reason 652a difference");
            return true;
        }

        if (!compareMultiSubfieldContentMultiField(oldReader, newReader, "652", "b", true, cut)) {
            classificationsChangedMessage.add("classificationchanged.reason.652b.difference");
            LOGGER.info("Classification has changed - reason 652b difference");
            return true;
        }

        //  if 652m or 652o then
        //      if 652e stripped changed return true
        //      if 652f stripped changed return true
        //      if 652h stripped changed return true
        final String f652m = oldReader.getValue("652", "m");
        final String f652o = oldReader.getValue("652", "o");
        final boolean subfieldMHasBeenCopied = hasSubfieldBeenCopied(oldReader, newReader, "654", "652", "m");
        final boolean subfieldOHasBeenCopied = hasSubfieldBeenCopied(oldReader, newReader, "654", "652", "o");
        if ((f652m != null || f652o != null) &&
                !(subfieldMHasBeenCopied || subfieldOHasBeenCopied)) {
            if (!compareSubfieldContentMultiField(oldReader, newReader, "652", "e", true, 0)) {
                classificationsChangedMessage.add("classificationchanged.reason.652mo.e.difference");
                LOGGER.info("Classification has changed - reason 652m|o : subfield e difference");
                return true;
            }
            if (!compareSubfieldContentMultiField(oldReader, newReader, "652", "f", true, 0)) {
                classificationsChangedMessage.add("classificationchanged.reason.652mo.f.difference");
                LOGGER.info("Classification has changed - reason 652m|o : subfield f difference");
                return true;
            }
            if (!compareSubfieldContentMultiField(oldReader, newReader, "652", "h", true, 0)) {
                classificationsChangedMessage.add("classificationchanged.reason.652mo.h.difference");
                LOGGER.info("Classification has changed - reason 652m|o : subfield h difference");
                return true;
            }
        }

        //  if 652m stripped changed return true
        if (!subfieldMHasBeenCopied && !compareSubfieldContentMultiField(oldReader, newReader, "652", "m", true, 0)) {
            classificationsChangedMessage.add("classificationchanged.reason.652m.difference");
            LOGGER.info("Classification has changed - reason 652m difference");
            return true;
        }

        //  if 652o stripped changed return true
        if (!subfieldOHasBeenCopied && !compareSubfieldContentMultiField(oldReader, newReader, "652", "o", true, 0)) {
            classificationsChangedMessage.add("classificationchanged.reason.652o.difference");
            LOGGER.info("Classification has changed - reason 652o difference");
            return true;
        }

        return false;
    }

    private boolean hasSubfieldBeenCopied(MarcRecordReader oldReader, MarcRecordReader newReader, String oldField, String newField, String subfield) {
        final String oldValue = oldReader.getValue(oldField, subfield);
        final String newValue = newReader.getValue(newField, subfield);

        return oldValue != null && oldValue.equals(newValue);
    }


    private MarcRecord updateClassificationsInRecord(MarcRecord currentCommonMarc, MarcRecord libraryRecord) {
        final MarcRecord result = new MarcRecord(libraryRecord);
        if (!hasClassificationData(libraryRecord)) {
            final MarcRecordWriter writer = new MarcRecordWriter(result);
            writer.copyFieldsFromRecord(CLASSIFICATION_FIELDS, currentCommonMarc);
        }
        return result;
    }

    private MarcRecord correctRecordIfEmpty(MarcRecord record) {
        final MarcRecordReader reader = new MarcRecordReader(record);
        final String agency = reader.getAgencyId();
        if (Integer.toString(RawRepo.DBC_ENRICHMENT).equals(agency) || Integer.toString(RawRepo.COMMON_AGENCY).equals(agency)) {
            return record;
        }

        // Special case for PH libraries: record with only 001 and 004 is allowed if 004 contains *n
        if (reader.hasSubfield("004", "n")) {
            return record;
        }

        // If record contains other fields than 001, 004 and 996, return the record, otherwise an empty record
        final List<MarcField> fieldList = record.getFields();
        for (MarcField wFieldList : fieldList) {
            if (!RECORD_CONTROL_FIELDS.contains(wFieldList.getName())) {
                return record;
            }
        }

        return new MarcRecord();
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
     * @throws UpdateException in case of an error
     */
    public MarcRecord createLibraryExtendedRecord(MarcRecord currentCommonRecord, MarcRecord updatingCommonRecord, String agencyId) throws UpdateException {
        MarcRecord result = new MarcRecord();
        final MarcRecordWriter writer = new MarcRecordWriter(result);
        final MarcRecordReader reader = new MarcRecordReader(updatingCommonRecord);

        writer.addOrReplaceSubfield("001", "a", reader.getRecordId());
        writer.addOrReplaceSubfield("001", "b", agencyId);
        writer.setChangedTimestamp();
        writer.setCreationTimestamp();
        writer.addOrReplaceSubfield("001", "f", "a");
        result = updateLibraryExtendedRecord(currentCommonRecord, updatingCommonRecord, result);
        return result;
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
     * @throws UpdateException in case of an error
     */
    public MarcRecord updateLibraryExtendedRecord(MarcRecord currentCommonRecord, MarcRecord updatingCommonRecord, MarcRecord enrichmentRecord) throws UpdateException {
        MarcRecord result = updateClassificationsInRecord(currentCommonRecord, enrichmentRecord);
        result = recategorization(currentCommonRecord, updatingCommonRecord, result);
        final MarcRecordWriter writer = new MarcRecordWriter(result);
        writer.removeField("004");
        writer.copyFieldsFromRecord(Collections.singletonList("004"), updatingCommonRecord);
        result = correctRecordIfEmpty(result);
        return result;
    }

    private boolean isEnrichmentReferenceFieldPresentInAlreadyProcessedFields(MarcField field, MarcRecord enrichment) {
        final MarcFieldReader fieldReader = new MarcFieldReader(field);
        String subfieldZ = fieldReader.getValue("z");
        if (subfieldZ != null) {
            if (subfieldZ.length() > 4) {
                subfieldZ = subfieldZ.substring(0, 2);
            }
            MarcRecordReader reader = new MarcRecordReader(enrichment);
            return reader.hasField(subfieldZ);
        }
        return false;
    }

    private boolean isFieldPresentInList(MarcField enrichmentField, List<MarcField> commonRecordFieldList) {
        final String cleanedEnrichmentField = enrichmentField.toString().trim();
        for (MarcField field : commonRecordFieldList) {
            if (cleanedEnrichmentField.equals(field.toString().trim())) {
                return true;
            }
        }
        return false;
    }

    private MarcField createRecordFieldWithoutIgnorableSubfields(MarcField enrichmentField) {
        final MarcField newField = new MarcField();
        for (MarcSubField subfield : enrichmentField.getSubfields()) {
            if (!IGNORABLE_CONTROL_SUBFIELDS.contains(subfield.getName())) {
                newField.getSubfields().add(new MarcSubField(subfield.getName(), subfield.getValue()));
            }
        }
        return newField;
    }

    private List<MarcField> createRecordFieldListWithoutIgnorableSubfields(List<MarcField> commonFieldList) {
        final List<MarcField> collector = new ArrayList<>();
        for (MarcField field : commonFieldList) {
            collector.add(createRecordFieldWithoutIgnorableSubfields(field));
        }
        return collector;
    }

    private boolean isEnrichmentFieldPresentInCommonFieldList(MarcField enrichmentField, List<MarcField> commonFieldList) {
        final MarcField cleanedField = createRecordFieldWithoutIgnorableSubfields(enrichmentField);
        final List<MarcField> listCleanedFields = createRecordFieldListWithoutIgnorableSubfields(commonFieldList);
        return isFieldPresentInList(cleanedField, listCleanedFields);
    }

    // This function checks if a specific enrichment field should be kept, by examine the following:
    // (1) if the field nbr. is in the list of always keep fields (001, 004, 996 + classification fields)
    // (2) if field is not found in the common record from RawRepo
    // (3) if the field is a reference field that points to either a field from (1) or (2)
    private boolean shouldEnrichmentRecordFieldBeKept(MarcField enrichmentField, MarcRecord common, MarcRecord enrichment) {
        if (CONTROL_AND_CLASSIFICATION_FIELDS.contains(enrichmentField.getName())) {
            return true;
        }
        final MarcRecordReader reader = new MarcRecordReader(common);
        if (reader.hasField(enrichmentField.getName())) {
            if (REFERENCE_FIELDS.contains(enrichmentField.getName())) {
                return isEnrichmentReferenceFieldPresentInAlreadyProcessedFields(enrichmentField, enrichment);
            } else {
                // get a list of fields in common with same name as enrichmentField
                final List<MarcField> fields = reader.getFieldAll(enrichmentField.getName());
                return !isEnrichmentFieldPresentInCommonFieldList(enrichmentField, fields);
            }
        }
        return true;
    }

    private MarcRecord cleanupEnrichmentRecord(MarcRecord enrichment, MarcRecord common) {
        final MarcRecord newRecord = new MarcRecord();
        final List<MarcField> fields = enrichment.getFields();
        for (MarcField field : fields) {
            if (shouldEnrichmentRecordFieldBeKept(field, common, enrichment)) {
                newRecord.getFields().add(field);
            }
        }
        return newRecord;
    }

    public MarcRecord correctLibraryExtendedRecord(MarcRecord commonRecord, MarcRecord enrichmentRecord) {
        LOGGER.entry(commonRecord, enrichmentRecord);
        MarcRecord result = null;
        if (hasClassificationData(commonRecord)) {
            LOGGER.info("Enrichment has classificationData");
            if (!hasClassificationsChanged(commonRecord, enrichmentRecord, new ArrayList<>())) {
                LOGGER.info("!hasClassificationsChanged");
                final MarcRecordWriter writer = new MarcRecordWriter(enrichmentRecord);
                writer.removeFields(CLASSIFICATION_FIELDS);
            } else {
                LOGGER.info("hasClassificationsChanged");
                result = enrichmentRecord;
            }
        }
        if (result == null) {
            result = new MarcRecord(enrichmentRecord);
        }

        LOGGER.info("Result from correctLibraryExtendedRecord BEFORE CLEAN UP {}", LogUtils.base64Encode(result));

        result = cleanupEnrichmentRecord(result, commonRecord);

        LOGGER.info("Result from correctLibraryExtendedRecord AFTER CLEAN UP {}", LogUtils.base64Encode(result));

        result = correctRecordIfEmpty(result);

        LOGGER.info("Final result of correctLibraryExtendedRecord {}", LogUtils.base64Encode(result));

        return result;
    }

    /**
     * This function will split (if necessary) the input record into common record and DBC enrichment record
     *
     * @param record       The record to be updated
     * @param libraryGroup Whether it is a FBS or DataIO template
     * @return a list of records to put in rawrepo
     * @throws VipCoreException             in case of an error
     * @throws UnsupportedEncodingException in case of an error
     * @throws UpdateException              in case of an error
     */
    public List<MarcRecord> recordDataForRawRepo(MarcRecord record, String groupId, LibraryGroup libraryGroup, ResourceBundle messages, boolean isAdmin) throws VipCoreException, UnsupportedEncodingException, UpdateException {
        LOGGER.entry(record, groupId, libraryGroup, messages);

        List<MarcRecord> result = new ArrayList<>();
        try {
            final MarcRecordReader reader = new MarcRecordReader(record);
            if (!isAdmin && reader.getAgencyIdAsInt() == RawRepo.COMMON_AGENCY &&
                    rawRepo.recordExists(reader.getRecordId(), RawRepo.COMMON_AGENCY)) {
                final MarcRecord existingRecord = RecordContentTransformer.decodeRecord(rawRepo.fetchRecord(reader.getRecordId(), RawRepo.COMMON_AGENCY).getContent());
                UpdateOwnership.mergeRecord(record, existingRecord);
            }

            // TODO Remove hardcoded 700300
            if ("700300".equals(groupId) || libraryGroup.isFBS()) {
                result = recordDataForRawRepoFBS(record, groupId, messages);
            } else { // Assuming DataIO mode
                result = recordDataForRawRepoDataIO(record, groupId);
            }

            return result;
        } finally {
            LOGGER.exit(result);
        }
    }

    private List<MarcRecord> recordDataForRawRepoFBS(MarcRecord record, String groupId, ResourceBundle messages) throws VipCoreException, UpdateException, UnsupportedEncodingException {
        LOGGER.entry(record, groupId, messages);
        List<MarcRecord> result = new ArrayList<>();
        try {
            result = splitRecordFBS(record, groupId, messages);

            for (MarcRecord r : result) {
                final MarcRecordWriter writer = new MarcRecordWriter(r);
                writer.setChangedTimestamp();
            }

            return result;
        } finally {
            LOGGER.exit(result);
        }
    }

    private List<MarcRecord> recordDataForRawRepoDataIO(MarcRecord record, String groupId) throws VipCoreException {
        LOGGER.entry(record, groupId);

        List<MarcRecord> result = new ArrayList<>();
        final MarcRecordReader reader = new MarcRecordReader(record);
        try {
            if (RawRepo.DBC_AGENCY_LIST.contains(reader.getAgencyId()) && (
                    vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS) ||
                            vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_ROOT) ||
                            vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_METACOMPASS))) {

                LOGGER.info("Record is 870970 and has either USE_ENRICHMENT, AUTH_ROOT or AUTH_METACOMPASS so calling splitRecordDataIO");
                result = splitRecordDataIO(record, reader.getAgencyId());
            } else {
                LOGGER.info("Record is not 870970 or has neither USE_ENRICHMENT, AUTH_ROOT nor AUTH_METACOMPASS so returning same record");
                result = Collections.singletonList(record);
            }

            return result;
        } finally {
            LOGGER.exit(result);
        }
    }

    /**
     * If the FBS record is an existing common (870970) record then split it into updated common record and
     * DBC enrichment record
     *
     * @param record  The record to be updated
     * @param groupId The groupId from the ws request
     * @return List containing common and DBC record
     * @throws VipCoreException             in case of an error
     * @throws UpdateException              in case of an error
     * @throws UnsupportedEncodingException in case of an error
     */
    private List<MarcRecord> splitRecordFBS(MarcRecord record, String groupId, ResourceBundle messages) throws VipCoreException, UpdateException, UnsupportedEncodingException {
        LOGGER.entry(record, groupId);

        try {
            final MarcRecordReader reader = new MarcRecordReader(record);

            if (reader.getAgencyIdAsInt() != RawRepo.COMMON_AGENCY) {
                LOGGER.info("Agency id of record is not 870970 - returning same record");
                return Collections.singletonList(record);
            }
            final NoteAndSubjectExtensionsHandler noteAndSubjectExtensionsHandler = new NoteAndSubjectExtensionsHandler(this.vipCoreService, rawRepo, messages);

            final MarcRecord correctedRecord = noteAndSubjectExtensionsHandler.recordDataForRawRepo(record, groupId);
            final MarcRecordReader correctedRecordReader = new MarcRecordReader(correctedRecord);
            MarcRecord dbcEnrichmentRecord;

            final String recId = correctedRecordReader.getRecordId();
            final String owner = correctedRecordReader.getValue("996", "a");

            if (owner == null) {
                LOGGER.debug("No owner in record.");

                return Collections.singletonList(correctedRecord);
            } else {
                LOGGER.info("Owner of record is {}", owner);
            }

            if (!rawRepo.recordExists(recId, RawRepo.DBC_ENRICHMENT)) {
                LOGGER.debug("DBC enrichment record [{}:{}] does not exist.", recId, RawRepo.DBC_ENRICHMENT);
                dbcEnrichmentRecord = new MarcRecord();
                final MarcField corrected001Field = new MarcField(correctedRecordReader.getField("001"));
                dbcEnrichmentRecord.getFields().add(corrected001Field);

                new MarcRecordWriter(dbcEnrichmentRecord).addOrReplaceSubfield("001", "b", Integer.toString(RawRepo.DBC_ENRICHMENT));
            } else {
                LOGGER.debug("DBC enrichment record [{}:{}] found.", recId, RawRepo.DBC_ENRICHMENT);
                dbcEnrichmentRecord = RecordContentTransformer.decodeRecord(rawRepo.fetchRecord(recId, RawRepo.DBC_ENRICHMENT).getContent());
            }

            final String recordStatus = correctedRecordReader.getValue("004", "r");
            if (recordStatus != null) {
                LOGGER.debug("Replace 004 *r in DBC enrichment record with: {}", recordStatus);
                new MarcRecordWriter(dbcEnrichmentRecord).addOrReplaceSubfield("004", "r", recordStatus);
            }

            final String recordType = correctedRecordReader.getValue("004", "a");
            if (recordType != null) {
                LOGGER.debug("Replace 004 *a in DBC enrichment record with: {}", recordType);
                new MarcRecordWriter(dbcEnrichmentRecord).addOrReplaceSubfield("004", "a", recordType);
            }


            LOGGER.info("correctedRecord\n{}", correctedRecord);
            LOGGER.info("dbcEnrichmentRecord\n{}", dbcEnrichmentRecord);

            return Arrays.asList(correctedRecord, dbcEnrichmentRecord);
        } finally {
            LOGGER.exit();
        }
    }


    /**
     * Split the input record into two record:
     * One with all normal Marc fields (001 - 999) and a new record with DBC fields
     *
     * @param record The record to be updated
     * @return List containing common and DBC record
     */
    List<MarcRecord> splitRecordDataIO(MarcRecord record, String agencyId) {
        LOGGER.entry(record);

        try {
            final MarcRecord dbcRecord = new MarcRecord();
            final MarcRecord commonRecord = new MarcRecord();

            for (MarcField field : record.getFields()) {
                if (field.getName().equals("001")) {
                    final MarcField commonField = new MarcField(field);
                    for (int c = 0; c < commonField.getSubfields().size(); c++) {
                        if (commonField.getSubfields().get(c).getName().equals("b")) {
                            commonField.getSubfields().get(c).setValue(agencyId);
                        }
                    }
                    commonRecord.getFields().add(commonField);

                    final MarcField dbcField = new MarcField(field);
                    for (int d = 0; d < dbcField.getSubfields().size(); d++) {
                        if (dbcField.getSubfields().get(d).getName().equals("b")) {
                            dbcField.getSubfields().get(d).setValue(Integer.toString(RawRepo.DBC_ENRICHMENT));
                        }
                    }
                    dbcRecord.getFields().add(dbcField);
                } else if (field.getName().equals("004")) {
                    dbcRecord.getFields().add(field);
                    commonRecord.getFields().add(field);
                } else if (field.getName().matches("[a-z].*")) {
                    dbcRecord.getFields().add(field);
                } else {
                    commonRecord.getFields().add(field);
                }
            }

            LOGGER.info("commonRecord\n{}", commonRecord);
            LOGGER.info("dbcRecord\n{}", dbcRecord);

            return Arrays.asList(commonRecord, dbcRecord);
        } finally {
            LOGGER.exit();
        }
    }


    /**
     * Modifies the new record if record is being recategorized
     *
     * @param currentCommonRecord  record in rr
     * @param updatingCommonRecord incoming record
     * @param extendedRecord       extended record in rr
     * @return record with notes about eventual recategorization
     * @throws UpdateException Trouble calling js.
     */
    private MarcRecord recategorization(MarcRecord currentCommonRecord, MarcRecord updatingCommonRecord, MarcRecord extendedRecord) throws UpdateException {
        LOGGER.entry(currentCommonRecord, updatingCommonRecord, extendedRecord);
        final StopWatch watch = new Log4JStopWatch("opencatBusiness.doRecategorizationThings");
        try {
            final String trackingId = MDC.get(MDC_TRACKING_ID_LOG_CONTEXT);

            return opencatBusinessConnector.doRecategorizationThings(currentCommonRecord, updatingCommonRecord, extendedRecord, trackingId);
        } catch (IOException | OpencatBusinessConnectorException | JSONBException | JAXBException ex) {
            throw new UpdateException("Error when executing OpencatBusinessConnector function: doRecategorizationThings", ex);
        } finally {
            watch.stop();
            LOGGER.exit();
        }
    }

    /**
     * Creates a 512 notefield from one record.
     * utilizes the create512 notefield functionality , which expects two records, one current, one updating
     * In this case we are not updating , but just wants a 512 field from existing data.
     *
     * @param record The record.
     * @return MarcField containing 512 data
     * @throws UpdateException in case of an error
     */

    public MarcField fetchNoteField(MarcRecord record) throws UpdateException {
        LOGGER.entry(record);
        final StopWatch watch = new Log4JStopWatch("opencatBusiness.recategorizationNoteFieldFactory");
        MarcField mf = null;
        try {
            final String trackingId = MDC.get(MDC_TRACKING_ID_LOG_CONTEXT);

            mf = opencatBusinessConnector.recategorizationNoteFieldFactory(record, trackingId);

            return mf;
        } catch (IOException | OpencatBusinessConnectorException | JSONBException | JAXBException ex) {
            throw new UpdateException("Error when executing OpencatBusinessConnector function: changeUpdateRecordForUpdate", ex);
        } finally {
            watch.stop();
            LOGGER.exit(mf);
        }
    }
}
