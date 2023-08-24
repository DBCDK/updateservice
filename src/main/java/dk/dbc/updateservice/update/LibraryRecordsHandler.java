package dk.dbc.updateservice.update;

import dk.dbc.common.records.CatalogExtractionCode;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.common.records.UpdateOwnership;
import dk.dbc.commons.jsonb.JSONBException;
import dk.dbc.marc.binding.DataField;
import dk.dbc.marc.binding.Leader;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.marc.binding.SubField;
import dk.dbc.marc.reader.MarcReaderException;
import dk.dbc.opencat.connector.OpencatBusinessConnector;
import dk.dbc.opencat.connector.OpencatBusinessConnectorException;
import dk.dbc.updateservice.utils.DeferredLogger;
import dk.dbc.vipcore.exception.VipCoreException;
import dk.dbc.vipcore.libraryrules.VipCoreLibraryRulesConnector;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.MDC;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import java.io.UnsupportedEncodingException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

import static dk.dbc.marc.reader.DanMarc2LineFormatReader.DEFAULT_LEADER;
import static dk.dbc.updateservice.rest.ApplicationConfig.LOG_DURATION_THRESHOLD_MS;
import static dk.dbc.updateservice.utils.MDCUtil.MDC_TRACKING_ID_LOG_CONTEXT;

import static dk.dbc.marc.binding.DataField.hasSubFieldCode;
/**
 * Class to manipulate library records for a local library. Local records and
 * local extended records.
 * <p>
 */
@Stateless
public class LibraryRecordsHandler {
    private static final DeferredLogger LOGGER = new DeferredLogger(LibraryRecordsHandler.class);
    private static final List<String> CLASSIFICATION_FIELDS = Arrays.asList("008", "009", "038", "039", "100", "110", "239", "245", "652");
    private static final List<String> REFERENCE_FIELDS = Arrays.asList("900", "910", "945");
    private static final List<String> RECORD_CONTROL_FIELDS = Arrays.asList("001", "004", "996");
    private static final List<String> CONTROL_AND_CLASSIFICATION_FIELDS = new ArrayList<>();
    private static final List<Character> IGNORABLE_CONTROL_SUBFIELDS = Arrays.asList('&', '0', '1', '4');
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

    public LibraryRecordsHandler() {
        /*
         * Ignore the fact that intellij claims this is never used.
         * If it doesn't exist then the following error will come when deploying :
         * javax.enterprise.system.core ======> Exception while loading the app : EJB Container initialization error
         * java.lang.NoSuchMethodError: dk.dbc.updateservice.update.LibraryRecordsHandler: method <init>()V not found
         */
    }

    /**
     * Tests if a record is published
     *
     * @param marcRecord The record.
     * @return <code>true</code> if published
     * <code>false</code> otherwise.
     */
    public boolean isRecordInProduction(MarcRecord marcRecord) {
        return CatalogExtractionCode.isUnderProduction(marcRecord);
    }

    /**
     * Tests if a record contains any classification data.
     *
     * @param marcRecord The record.
     * @return <code>true</code> if classifications where found,
     * <code>false</code> otherwise.
     */
    public boolean hasClassificationData(MarcRecord marcRecord) {
        final List<DataField> fields = marcRecord.getFields(DataField.class);
        for (DataField field : fields) {
            if (CLASSIFICATION_FIELDS.contains(field.getTag())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Collect pairs of subfields a and g from field 009
     *
     * @param reader reader for the record
     * @return List of paired subfields
     */
    private List<String> getLowerCaseAGPairs(MarcRecordReader reader) {
        final List<String> result = new ArrayList<>();
        DataField field = reader.getField("009");
        if (field == null) {
            LOGGER.use(log -> log.info("field 009 is NULL"));
            return result;
        }
        final List<SubField> subfieldList = field.getSubFields();
        boolean gotLowerCaseA = false;
        String aString = "";
        String gString;
        // run through until a - search for g - if a first, then push a,<empty>
        // if g then push a,g - start from scratch.
        // after : if a and no g then push "a",<empty>
        // can there be "a" after "a" ? what about g after g ?
        // There are no validation for them being paired so yes
        // test cases :
        // *a s *b b -> as
        // *a p *b a *a g -> ap ag
        // *a s *b b *g xc -> asgxc
        // *a s *b b *g xc *a p -> asgxc ap
        // *a s *b b *g xc *g tk -> asgxc gtk
        // *a s *b a *g xc *a s *b m *g th *a s *g xk *b a *h xx -> asgxc asgth asgxk
        for (SubField aSubfieldList : subfieldList) {
            if ('a' == aSubfieldList.getCode()) {
                if (gotLowerCaseA) {
                    result.add("a" + aString);
                }
                gotLowerCaseA = true;
                aString = aSubfieldList.getData();
            } else {
                if ('g' == aSubfieldList.getCode()) {
                    gString = aSubfieldList.getData();
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
    private String getCompareString(List<SubField> subfieldList, String subfields, boolean normalize, int cut) {
        if (subfieldList == null) {
            return "";
        }
        final StringBuilder collector = new StringBuilder();
        String subCollector;
        for (SubField aSubfieldList : subfieldList) {
            if (subfields.indexOf(aSubfieldList.getCode()) > -1) {
                if (normalize) {
                    subCollector = Normalizer.normalize(aSubfieldList.getData(), Normalizer.Form.NFD).replaceAll(DIACRITICAL_MARKS, "");
                } else {
                    subCollector = aSubfieldList.getData();
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
    private boolean compareSubfieldContent(List<SubField> oldList, List<SubField> newList, String subfields, boolean normalize, int cut) {
        return LOGGER.call(log -> {
            if (oldList == null && newList == null) {
                log.info("compareSubfieldContent - both NULL");
                return true;
            }
            final String oldMatch = getCompareString(oldList, subfields, normalize, cut);
            final String newMatch = getCompareString(newList, subfields, normalize, cut);
            log.info("Old str <{}>, new str <{}>", oldMatch, newMatch);
            return oldMatch.equals(newMatch);
        });
    }

    /**
     * Returns the content of a field/subfield if exists and an empty string if not
     *
     * @param reader   record reader
     * @param field    the wanted field
     * @param subfield the wanted subfield
     * @return subfield content or an empty string
     */
    private String getSubFieldsContent(MarcRecordReader reader, String field, char subfield) {
        final String value = reader.getValue(field, subfield);
        if (value == null) return "";
        else return value;
    }

    /**
     * Tests if the classifications has changed between 2 records.
     * <p>
     * This method is mainly used to check for changes between 2 versions of
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
        // Things have changed - in 652 it is now wanted to have a 10-character limit, but still not global.
        final int compareLength = 0;

        LOGGER.use(log -> {
            log.debug("Old record\n{}", oldRecord);
            log.debug("New record\n{}", newRecord);

        });

        // We call each of the classification check functions in order to get every change message
        final boolean resultCheck008 = check008(oldReader, newReader, classificationsChangedMessage);
        final boolean resultCheck009 = check009(oldReader, newReader, classificationsChangedMessage);
        final boolean resultCheck038 = check038(oldReader, newReader, classificationsChangedMessage);
        final boolean resultCheck039 = check039(oldReader, newReader, classificationsChangedMessage);
        final boolean resultCheck100 = check100(oldReader, newReader, classificationsChangedMessage);
        final boolean resultCheck110 = check110(oldReader, newReader, classificationsChangedMessage);
        final boolean resultCheck239And245 = check239And245(oldReader, newReader, compareLength, classificationsChangedMessage);
        final boolean resultCheck245 = check245(oldReader, newReader, compareLength, classificationsChangedMessage);
        final boolean resultCheck652 = check652(oldReader, newReader, 10, classificationsChangedMessage);

        return resultCheck008 ||
                resultCheck009 ||
                resultCheck038 ||
                resultCheck039 ||
                resultCheck100 ||
                resultCheck110 ||
                resultCheck239And245 ||
                resultCheck245 ||
                resultCheck652;
    }

    private boolean check008(MarcRecordReader oldReader, MarcRecordReader newReader, List<String> classificationsChangedMessage) {
        final String oldValue = getSubFieldsContent(oldReader, "008", 't');
        final String newValue = getSubFieldsContent(newReader, "008", 't');
        return LOGGER.call(log -> {
            // if 008*t has changed from m or s to p and reverse return true.
            if ((oldValue.equals("m") || oldValue.equals("s")) && newValue.equals("p")) {
                classificationsChangedMessage.add("classificationchanged.reason.008t.ms.to.p");
                log.info("Classification has changed - reason 008t m|s -> p");
                return true;
            }

            if ((newValue.equals("m") || newValue.equals("s")) && oldValue.equals("p")) {
                classificationsChangedMessage.add("classificationchanged.reason.008t.p.to.ms");
                log.info("Classification has changed - reason 008t p -> m|s");
                return true;
            }
            return false;
        });
    }

    private boolean check009(MarcRecordReader oldReader, MarcRecordReader newReader, List<String> classificationsChangedMessage) {
        // has content of 009ag changed
        // se evt her for baggrund : http://praxis.dbc.dk/formatpraksis/px-for1796.html/#pxkoko
        final List<String> oldLowerCaseAGPairs = getLowerCaseAGPairs(oldReader);
        final List<String> newLowerCaseAGPairs = getLowerCaseAGPairs(newReader);
        if (!oldLowerCaseAGPairs.containsAll(newLowerCaseAGPairs) || !newLowerCaseAGPairs.containsAll(oldLowerCaseAGPairs)) {
            classificationsChangedMessage.add("classificationchanged.reason.009ag.difference");
            LOGGER.use(log -> log.info("Classification has changed - reason 009ag difference"));
            return true;
        } else {
            return false;
        }
    }

    private boolean check038(MarcRecordReader oldReader, MarcRecordReader newReader, List<String> classificationsChangedMessage) {
        final String oldValue = getSubFieldsContent(oldReader, "038", 'a');
        final String newValue = getSubFieldsContent(newReader, "038", 'a');

        // if content of 038a has changed return true
        if (!oldValue.equals(newValue)) {
            classificationsChangedMessage.add("classificationchanged.reason.038a.difference");
            LOGGER.use(log -> log.info("Classification has changed - reason 038a difference"));
            return true;
        } else {
            return false;
        }
    }

    private boolean check039(MarcRecordReader oldReader, MarcRecordReader newReader, List<String> classificationsChangedMessage) {
        final DataField oldField = oldReader.getField("039");
        final DataField newField = newReader.getField("039");
        List<SubField> oldSubfieldList;
        List<SubField> newSubfieldList;

        // if content of 039 has changed return true
        boolean result = false;
        if (oldField != null && newField != null) {
            oldSubfieldList = oldField.getSubFields();
            newSubfieldList = newField.getSubFields();
            if (!new HashSet<>(oldSubfieldList).containsAll(newSubfieldList) || !new HashSet<>(newSubfieldList).containsAll(oldSubfieldList)) {
                result = true;
            }
        }

        if (result || oldField == null && newField != null || oldField != null && newField == null) {
            classificationsChangedMessage.add("classificationchanged.reason.039.difference");
            LOGGER.use(log -> log.info("Classification has changed - reason 039 difference"));
            return true;
        } else {
            return false;
        }
    }

    private boolean check100(MarcRecordReader oldReader, MarcRecordReader newReader, List<String> classificationsChangedMessage) {
        final DataField oldField = oldReader.getField("100");
        final DataField newField = newReader.getField("100");

        // if content of 100*[ahkef] stripped has changed return true.
        final List<SubField> oldSubfieldList = oldField == null ? null : oldField.getSubFields();
        final List<SubField> newSubfieldList = newField == null ? null : newField.getSubFields();

        if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "ahkef", true, 0)) {
            classificationsChangedMessage.add("classificationchanged.reason.100.difference");
            LOGGER.use(log -> log.info("Classification has changed - reason 100ahkef difference"));
            return true;
        } else {
            return false;
        }
    }

    private boolean check110(MarcRecordReader oldReader, MarcRecordReader newReader, List<String> classificationsChangedMessage) {
        final DataField oldField = oldReader.getField("110");
        final DataField newField = newReader.getField("110");

        // if content of 110*[saceikj] stripped has changed return true
        final List<SubField> oldSubfieldList = oldField == null ? null : oldField.getSubFields();
        final List<SubField> newSubfieldList = newField == null ? null : newField.getSubFields();

        if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "saceikj", true, 0)) {
            classificationsChangedMessage.add("classificationchanged.reason.110.difference");
            LOGGER.use(log -> log.info("Classification has changed - reason 110saceikj difference"));
            return true;
        } else {
            return false;
        }
    }


    private boolean check239And245(MarcRecordReader oldReader, MarcRecordReader newReader, int cut, List<String> classificationsChangedMessage) {
        return LOGGER.call(log -> {
            List<SubField> oldSubfieldList;
            List<SubField> newSubfieldList;
            DataField oldField;
            DataField newField;
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
                    final DataField field245 = oldReader.getField("245");
                    if (field245 != null) {
                        f245a = getCompareString(field245.getSubFields(), "a", true, cut);
                    }
                    final String f239t = getCompareString(newReader.getField("239").getSubFields(), "t", true, cut);
                    checkField239 = !f245a.equals(f239t);
                    if (checkField239 && !f239t.equals("")) {
                        classificationsChangedMessage.add("classificationchanged.reason.239t.difference");
                        log.info("Classification has changed - reason 239t difference");
                        return true;
                    }
                    checkField245 = checkField239;
                }
            } else {
                if (newField == null) {
                    final DataField field245 = newReader.getField("245");
                    if (field245 != null) {
                        f245a = getCompareString(field245.getSubFields(), "a", true, cut);
                    }
                    final String f239t = getCompareString(oldReader.getField("239").getSubFields(), "t", true, cut);
                    checkField239 = !f245a.equals(f239t);
                    if (checkField239 && !f239t.equals("")) {
                        classificationsChangedMessage.add("classificationchanged.reason.239t.difference");
                        log.info("Classification has changed - reason 239t difference ");
                        return true;
                    }
                    checkField245 = checkField239;
                } else {
                    checkField239 = true;
                    newValue = newReader.getValue("239", 't');
                    if (newValue != null) {
                        checkField245 = false;
                    }
                }
            }

            if (checkField239) {
                oldSubfieldList = oldField == null ? null : oldField.getSubFields();
                newSubfieldList = newField == null ? null : newField.getSubFields();
                if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "ahkeft\u00F8", true, cut)) {
                    classificationsChangedMessage.add("classificationchanged.reason.239.difference");
                    log.info("Classification has changed - reason 239ahkeft\u00F8 difference");
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
            oldSubfieldList = oldField == null ? null : oldField.getSubFields();
            newSubfieldList = newField == null ? null : newField.getSubFields();
            if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "a", true, cut)) {
                newValue = newReader.getValue("004", 'a');
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
                    log.info("Classification has changed - reason 245a difference");
                    return true;
                }
            }

            return false;
        });
    }

    private boolean check245(MarcRecordReader oldReader, MarcRecordReader newReader, int cut, List<String> classificationsChangedMessage) {
        final DataField oldField = oldReader.getField("245");
        final DataField newField = newReader.getField("245");
        final List<SubField> oldSubfieldList = oldField == null ? null : oldField.getSubFields();
        final List<SubField> newSubfieldList = newField == null ? null : newField.getSubFields();

        return LOGGER.call(log -> {
            if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "g", true, cut)) {
                classificationsChangedMessage.add("classificationchanged.reason.245g.difference");
                log.info("Classification has changed - reason 245g difference");
                return true;
            }

            if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "m", false, 0)) {
                classificationsChangedMessage.add("classificationchanged.reason.245m.difference");
                log.info("Classification has changed - reason 245m difference");
                return true;
            }

            if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "n", true, 0)) {
                classificationsChangedMessage.add("classificationchanged.reason.245n.difference");
                log.info("Classification has changed - reason 245n difference");
                return true;
            }

            if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "o", true, cut)) {
                classificationsChangedMessage.add("classificationchanged.reason.245o.difference");
                log.info("Classification has changed - reason 245o difference");
                return true;
            }

            if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "y", true, cut)) {
                classificationsChangedMessage.add("classificationchanged.reason.245y.difference");
                log.info("Classification has changed - reason 245y difference");
                return true;
            }

            if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "\u00E6", true, cut)) {
                classificationsChangedMessage.add("classificationchanged.reason.245æ.difference");
                log.info("Classification has changed - reason 245æ difference");
                return true;
            }

            if (!compareSubfieldContent(oldSubfieldList, newSubfieldList, "\u00F8", true, cut)) {
                classificationsChangedMessage.add("classificationchanged.reason.245ø.difference");
                log.info("Classification has changed - reason 245ø difference");
                return true;
            }

            return false;
        });
    }

    /**
     * Return a field that have an m or o subfield. One of those subfields should be in the record since it is
     * mandatory to have one, but only one.
     * @param newReader new record reader
     * @return the field
     */
    private DataField get652654MorO(MarcRecordReader newReader, String fieldName) {
        List<DataField> fields = newReader.getFieldAll(fieldName);
        for (DataField field : fields) {
            if (field.hasSubField(hasSubFieldCode('m')) || field.hasSubField(hasSubFieldCode('o'))) {
                return sortField652(field, fieldName);
            }
        }
        return new DataField();
    }

    /**
     * There is no guarantee that the subfields in 652 come in a specific order, so we order them
     * for further treatment
     * @param fieldToSort field to sort
     * @return the sorted field
     */
    DataField sortField652(DataField fieldToSort, String fieldName) {
        DataField result = new DataField(fieldName, "00");
        List<Character> subfields = new ArrayList<>(Arrays.asList('m', 'o', 'a', 'b', 'e', 'f', 'h'));
        for (Character subfield : subfields) {
            Optional<SubField> s = fieldToSort.getSubField(hasSubFieldCode(subfield));
            if (s.isPresent()) {
                result.addSubField(s.get());
            }
        }
        return result;
    }

    /**
     * Rules has changed so now there must only be one 652m or 652o which have all 652 classification
     * information.
     * @param oldReader Reader for the existing record
     * @param newReader Reader for the incoming record
     * @param cut       Kind of obsolete, but kept for now
     * @param classificationsChangedMessage List with reasons - kind of funny, there will only be one
     *                                      even if there are multiple changes.
     * @return Return true if there is relevant changes, otherwise false
     */
    private boolean check652(MarcRecordReader oldReader, MarcRecordReader newReader, int cut, List<String> classificationsChangedMessage) {
        return LOGGER.call(log -> {

            // First we collect the fields with *o or *m from new and old. Rules says that there may only be one in a record.
            // Rules also say that 652*m/o is obligatory so there will be a new.
            DataField current = get652654MorO(oldReader, "652");
            DataField new652 = get652654MorO(newReader, "652");
            if (current.getSubFields().size() > 0) {
                if (compareSubfieldContent(current.getSubFields(), new652.getSubFields(), "moabefh",
                        true, cut)) {
                    return false;
                }
            } else {
                // Sadly, they weren't equal, so we look for 654 in the old record - some panic may ensue
                current = get652654MorO(oldReader, "654");
                if (current.getSubFields().size() > 0 &&
                        compareSubfieldContent(current.getSubFields(), new652.getSubFields(), "moabefh",
                                true, cut)) {
                    return false;
                }
            }

            // Now we will say something nice to the librarian
            if (!compareSubfieldContent(current.getSubFields(hasSubFieldCode('m')),
                    new652.getSubFields(hasSubFieldCode('m')), "m", true, cut)) {
                classificationsChangedMessage.add("classificationchanged.reason.652m.difference");
                log.info("Classification has changed - reason 652m difference");
                return true;
            }
            if (!compareSubfieldContent(current.getSubFields(hasSubFieldCode('o')),
                    new652.getSubFields(hasSubFieldCode('o')), "o", true, cut)) {
                classificationsChangedMessage.add("classificationchanged.reason.652o.difference");
                log.info("Classification has changed - reason 652o difference");
                return true;
            }
            if (!compareSubfieldContent(current.getSubFields(hasSubFieldCode('a')),
                    new652.getSubFields(hasSubFieldCode('a')), "a", true, cut)) {
                classificationsChangedMessage.add("classificationchanged.reason.652a.difference");
                log.info("Classification has changed - reason 652a difference");
                return true;
            }
            if (!compareSubfieldContent(current.getSubFields(hasSubFieldCode('b')),
                    new652.getSubFields(hasSubFieldCode('b')), "b", true, cut)) {
                classificationsChangedMessage.add("classificationchanged.reason.652b.difference");
                log.info("Classification has changed - reason 652b difference");
                return true;
            }
            if (!compareSubfieldContent(current.getSubFields(hasSubFieldCode('e')),
                    new652.getSubFields(hasSubFieldCode('e')), "e", true, cut)) {
                classificationsChangedMessage.add("classificationchanged.reason.652mo.e.difference");
                log.info("Classification has changed - reason 652e difference");
                return true;
            }
            if (!compareSubfieldContent(current.getSubFields(hasSubFieldCode('f')),
                    new652.getSubFields(hasSubFieldCode('f')), "f", true, cut)) {
                classificationsChangedMessage.add("classificationchanged.reason.652mo.f.difference");
                log.info("Classification has changed - reason 652f difference");
                return true;
            }
            if (!compareSubfieldContent(current.getSubFields(hasSubFieldCode('h')),
                    new652.getSubFields(hasSubFieldCode('h')), "h", true, cut)) {
                classificationsChangedMessage.add("classificationchanged.reason.652mo.h.difference");
                log.info("Classification has changed - reason 652h difference");
                return true;
            }

            return false;
        });
    }

    private MarcRecord updateClassificationsInRecord(MarcRecord currentCommonMarc, MarcRecord libraryRecord) {
        final MarcRecord result = new MarcRecord(libraryRecord);
        if (!hasClassificationData(libraryRecord)) {
            final MarcRecordWriter writer = new MarcRecordWriter(result);
            writer.copyFieldsFromRecord(CLASSIFICATION_FIELDS, currentCommonMarc);
        }
        return result;
    }

    private MarcRecord correctRecordIfEmpty(MarcRecord marcRecord) {
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);
        final String agency = reader.getAgencyId();
        if (Integer.toString(RawRepo.DBC_ENRICHMENT).equals(agency) || Integer.toString(RawRepo.COMMON_AGENCY).equals(agency)) {
            return marcRecord;
        }

        // Special case for PH libraries: record with only 001 and 004 is allowed if 004 contains *n
        if (reader.hasSubfield("004", 'n')) {
            return marcRecord;
        }

        // If record contains other fields than 001, 004 and 996, return the record, otherwise an empty record
        final List<DataField> fieldList = marcRecord.getFields(DataField.class);
        for (DataField wFieldList : fieldList) {
            if (!RECORD_CONTROL_FIELDS.contains(wFieldList.getTag())) {
                return marcRecord;
            }
        }

        return new MarcRecord().setLeader(new Leader().setData(DEFAULT_LEADER));
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
        MarcRecord result = new MarcRecord().setLeader(new Leader().setData(DEFAULT_LEADER));
        final MarcRecordWriter writer = new MarcRecordWriter(result);
        final MarcRecordReader reader = new MarcRecordReader(updatingCommonRecord);

        writer.addOrReplaceSubField("001", 'a', reader.getRecordId());
        writer.addOrReplaceSubField("001", 'b', agencyId);
        writer.setChangedTimestamp();
        writer.setCreationTimestamp();
        writer.addOrReplaceSubField("001", 'f', "a");
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

    private boolean isEnrichmentReferenceFieldPresentInAlreadyProcessedFields(DataField field, MarcRecord enrichment) {
        String subfieldZ = field.getSubField(hasSubFieldCode('z')).orElse(null).getData();
        if (subfieldZ != null) {
            if (subfieldZ.length() > 4) {
                subfieldZ = subfieldZ.substring(0, 2);
            }
            MarcRecordReader reader = new MarcRecordReader(enrichment);
            return reader.hasField(subfieldZ);
        }
        return false;
    }

    private boolean isFieldPresentInList(DataField enrichmentField, List<DataField> commonRecordFieldList) {
        final String cleanedEnrichmentField = enrichmentField.toString().trim();
        for (DataField field : commonRecordFieldList) {
            if (cleanedEnrichmentField.equals(field.toString().trim())) {
                return true;
            }
        }
        return false;
    }

    private DataField createRecordFieldWithoutIgnorableSubfields(DataField enrichmentField) {
        final DataField newField = new DataField();
        for (SubField subfield : enrichmentField.getSubFields()) {
            if (!IGNORABLE_CONTROL_SUBFIELDS.contains(subfield.getCode())) {
                newField.getSubFields().add(new SubField(subfield));
            }
        }
        return newField;
    }

    private List<DataField> createRecordFieldListWithoutIgnorableSubfields(List<DataField> commonFieldList) {
        final List<DataField> collector = new ArrayList<>();
        for (DataField field : commonFieldList) {
            collector.add(createRecordFieldWithoutIgnorableSubfields(field));
        }
        return collector;
    }

    private boolean isEnrichmentFieldPresentInCommonFieldList(DataField enrichmentField, List<DataField> commonFieldList) {
        final DataField cleanedField = createRecordFieldWithoutIgnorableSubfields(enrichmentField);
        final List<DataField> listCleanedFields = createRecordFieldListWithoutIgnorableSubfields(commonFieldList);
        return isFieldPresentInList(cleanedField, listCleanedFields);
    }

    // This function checks if a specific enrichment field should be kept, by examine the following:
    // (1) if the field nbr. is in the list of always keep fields (001, 004, 996 + classification fields)
    // (2) if field is not found in the common record from RawRepo
    // (3) if the field is a reference field that points to either a field from (1) or (2)
    private boolean shouldEnrichmentRecordFieldBeKept(DataField enrichmentField, MarcRecord common, MarcRecord enrichment) {
        if (CONTROL_AND_CLASSIFICATION_FIELDS.contains(enrichmentField.getTag())) {
            return true;
        }
        final MarcRecordReader reader = new MarcRecordReader(common);
        if (reader.hasField(enrichmentField.getTag())) {
            if (REFERENCE_FIELDS.contains(enrichmentField.getTag())) {
                return isEnrichmentReferenceFieldPresentInAlreadyProcessedFields(enrichmentField, enrichment);
            } else {
                // get a list of fields in common with same name as enrichmentField
                final List<DataField> fields = reader.getFieldAll(enrichmentField.getTag());
                return !isEnrichmentFieldPresentInCommonFieldList(enrichmentField, fields);
            }
        }
        return true;
    }

    private MarcRecord cleanupEnrichmentRecord(MarcRecord enrichment, MarcRecord common) {
        final MarcRecord newRecord = new MarcRecord().setLeader(new Leader().setData(DEFAULT_LEADER));
        final List<DataField> fields = enrichment.getFields(DataField.class);
        for (DataField field : fields) {
            if (shouldEnrichmentRecordFieldBeKept(field, common, enrichment)) {
                newRecord.getFields().add(field);
            }
        }
        return newRecord;
    }

    public MarcRecord correctLibraryExtendedRecord(MarcRecord commonRecord, MarcRecord enrichmentRecord) {
        return LOGGER.call(log -> {
            MarcRecord result = null;
            if (hasClassificationData(commonRecord)) {
                log.info("Enrichment has classificationData");
                if (!hasClassificationsChanged(commonRecord, enrichmentRecord, new ArrayList<>())) {
                    log.info("!hasClassificationsChanged");
                    final MarcRecordWriter writer = new MarcRecordWriter(enrichmentRecord);
                    writer.removeFields(CLASSIFICATION_FIELDS);
                } else {
                    log.info("hasClassificationsChanged");
                    result = enrichmentRecord;
                }
            }
            if (result == null) {
                result = new MarcRecord(enrichmentRecord);
            }

            result = cleanupEnrichmentRecord(result, commonRecord);

            result = correctRecordIfEmpty(result);

            return result;
        });
    }

    /**
     * This function will split (if necessary) the input record into common record and DBC enrichment record
     *
     * @param marcRecord   The record to be updated
     * @param libraryGroup Whether it is a FBS or DataIO template
     * @return a list of records to put in rawrepo
     * @throws VipCoreException             in case of an error
     * @throws UnsupportedEncodingException in case of an error
     * @throws UpdateException              in case of an error
     */
    public List<MarcRecord> recordDataForRawRepo(MarcRecord marcRecord, String groupId, LibraryGroup libraryGroup, ResourceBundle messages, boolean isAdmin) throws VipCoreException, UpdateException {
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);
        if (!isAdmin && reader.getAgencyIdAsInt() == RawRepo.COMMON_AGENCY &&
                rawRepo.recordExists(reader.getRecordId(), RawRepo.COMMON_AGENCY)) {
            final MarcRecord existingRecord = UpdateRecordContentTransformer.decodeRecord(rawRepo.fetchRecord(reader.getRecordId(), RawRepo.COMMON_AGENCY).getContent());
            UpdateOwnership.mergeRecord(marcRecord, existingRecord);
        }

        if (libraryGroup.isFBS()) {
            return recordDataForRawRepoFBS(marcRecord, groupId, messages);
        } else { // Assuming DataIO mode
            return recordDataForRawRepoDataIO(marcRecord, groupId);
        }
    }

    private List<MarcRecord> recordDataForRawRepoFBS(MarcRecord marcRecord, String groupId, ResourceBundle messages) throws VipCoreException, UpdateException {
        final List<MarcRecord> result = splitRecordFBS(marcRecord, groupId, messages);

        for (MarcRecord r : result) {
            final MarcRecordWriter writer = new MarcRecordWriter(r);
            writer.setChangedTimestamp();
        }

        return result;
    }

    private List<MarcRecord> recordDataForRawRepoDataIO(MarcRecord marcRecord, String groupId) throws VipCoreException {
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);
        return LOGGER.callChecked(log -> {
            if (RawRepo.DBC_AGENCY_LIST.contains(reader.getAgencyId()) && (
                    vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS) ||
                            vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_ROOT) ||
                            vipCoreService.hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.AUTH_METACOMPASS))) {

                log.info("Record is 870970 and has either USE_ENRICHMENT, AUTH_ROOT or AUTH_METACOMPASS so calling splitRecordDataIO");
                return splitRecordDataIO(marcRecord, reader.getAgencyId());
            } else {
                log.info("Record is not 870970 or has neither USE_ENRICHMENT, AUTH_ROOT nor AUTH_METACOMPASS so returning same record");
                return Collections.singletonList(marcRecord);
            }
        });
    }

    /**
     * If the FBS record is an existing common (870970) record then split it into updated common record and
     * DBC enrichment record
     *
     * @param marcRecord The record to be updated
     * @param groupId    The groupId from the ws request
     * @return List containing common and DBC record
     * @throws VipCoreException in case of an error
     * @throws UpdateException  in case of an error
     */
    private List<MarcRecord> splitRecordFBS(MarcRecord marcRecord, String groupId, ResourceBundle messages) throws VipCoreException, UpdateException {
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);

        return LOGGER.<List<MarcRecord>, VipCoreException, UpdateException>callChecked2(log -> {
            if (reader.getAgencyIdAsInt() != RawRepo.COMMON_AGENCY) {
                log.info("Agency id of record is not 870970 - returning same record");
                return Collections.singletonList(marcRecord);
            }
            final NoteAndSubjectExtensionsHandler noteAndSubjectExtensionsHandler = new NoteAndSubjectExtensionsHandler(this.vipCoreService, rawRepo, messages);

            final MarcRecord correctedRecord = noteAndSubjectExtensionsHandler.extensionRecordDataForRawRepo(marcRecord, groupId);
            final MarcRecordReader correctedRecordReader = new MarcRecordReader(correctedRecord);
            MarcRecord dbcEnrichmentRecord;

            final String recId = correctedRecordReader.getRecordId();
            final String owner = correctedRecordReader.getValue("996", 'a');

            if (owner == null) {
                log.debug("No owner in record.");

                return Collections.singletonList(correctedRecord);
            } else {
                log.info("Owner of record is {}", owner);
            }

            if (!rawRepo.recordExists(recId, RawRepo.DBC_ENRICHMENT)) {
                log.debug("DBC enrichment record [{}:{}] does not exist.", recId, RawRepo.DBC_ENRICHMENT);
                dbcEnrichmentRecord = new MarcRecord().setLeader(new Leader().setData(DEFAULT_LEADER));
                final DataField corrected001Field = new DataField(correctedRecordReader.getField("001"));
                dbcEnrichmentRecord.getFields().add(corrected001Field);

                new MarcRecordWriter(dbcEnrichmentRecord).addOrReplaceSubField("001", 'b', Integer.toString(RawRepo.DBC_ENRICHMENT));
            } else {
                log.debug("DBC enrichment record [{}:{}] found.", recId, RawRepo.DBC_ENRICHMENT);
                dbcEnrichmentRecord = UpdateRecordContentTransformer.decodeRecord(rawRepo.fetchRecord(recId, RawRepo.DBC_ENRICHMENT).getContent());
            }

            final String recordStatus = correctedRecordReader.getValue("004", 'r');
            if (recordStatus != null) {
                log.debug("Replace 004 *r in DBC enrichment record with: {}", recordStatus);
                new MarcRecordWriter(dbcEnrichmentRecord).addOrReplaceSubField("004", 'r', recordStatus);
            }

            final String recordType = correctedRecordReader.getValue("004", 'a');
            if (recordType != null) {
                log.debug("Replace 004 *a in DBC enrichment record with: {}", recordType);
                new MarcRecordWriter(dbcEnrichmentRecord).addOrReplaceSubField("004", 'a', recordType);
            }


            log.info("correctedRecord\n{}", correctedRecord);
            log.info("dbcEnrichmentRecord\n{}", dbcEnrichmentRecord);

            return Arrays.asList(correctedRecord, dbcEnrichmentRecord);
        });
    }


    /**
     * Split the input record into two record:
     * One with all normal Marc fields (001 - 999) and a new record with DBC fields
     *
     * @param marcRecord The record to be updated
     * @return List containing common and DBC record
     */
    List<MarcRecord> splitRecordDataIO(MarcRecord marcRecord, String agencyId) {
        final MarcRecord dbcRecord = new MarcRecord().setLeader(new Leader().setData(DEFAULT_LEADER));
        final MarcRecord commonRecord = new MarcRecord().setLeader(new Leader().setData(DEFAULT_LEADER));

        for (DataField field : marcRecord.getFields(DataField.class)) {
            if (field.getTag().equals("001")) {
                final DataField commonField = new DataField(field);
                for (int c = 0; c < commonField.getSubFields().size(); c++) {
                    if ('b' == commonField.getSubFields().get(c).getCode()) {
                        commonField.getSubFields().get(c).setData(agencyId);
                    }
                }
                commonRecord.getFields().add(commonField);

                final DataField dbcField = new DataField(field);
                for (int d = 0; d < dbcField.getSubFields().size(); d++) {
                    if ('b' == dbcField.getSubFields().get(d).getCode()) {
                        dbcField.getSubFields().get(d).setData(Integer.toString(RawRepo.DBC_ENRICHMENT));
                    }
                }
                dbcRecord.getFields().add(dbcField);
            } else if (field.getTag().equals("004")) {
                dbcRecord.getFields().add(field);
                commonRecord.getFields().add(field);
            } else if (field.getTag().matches("[a-z].*")) {
                dbcRecord.getFields().add(field);
            } else {
                commonRecord.getFields().add(field);
            }
        }
        LOGGER.use(log -> {
            log.info("commonRecord\n{}", commonRecord);
            log.info("dbcRecord\n{}", dbcRecord);
        });
        return Arrays.asList(commonRecord, dbcRecord);
    }


    /**
     * Modifies the new record if record is being re-categorized
     *
     * @param currentCommonRecord  record in rr
     * @param updatingCommonRecord incoming record
     * @param extendedRecord       extended record in rr
     * @return record with notes about eventual recategorization
     * @throws UpdateException Trouble calling js.
     */
    private MarcRecord recategorization(MarcRecord currentCommonRecord, MarcRecord updatingCommonRecord, MarcRecord extendedRecord) throws UpdateException {
        final StopWatch watch = new Log4JStopWatch("opencatBusiness.doRecategorizationThings").setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        try {
            final String trackingId = MDC.get(MDC_TRACKING_ID_LOG_CONTEXT);

            return opencatBusinessConnector.doRecategorizationThings(currentCommonRecord, updatingCommonRecord, extendedRecord, trackingId);
        } catch (OpencatBusinessConnectorException | JSONBException | MarcReaderException ex) {
            throw new UpdateException("Error when executing OpencatBusinessConnector function: doRecategorizationThings", ex);
        } finally {
            watch.stop();
        }
    }

    /**
     * Creates a 512 notefield from one record.
     * utilizes the create512 notefield functionality , which expects two records, one current, one updating
     * In this case we are not updating , but just wants a 512 field from existing data.
     *
     * @param marcRecord The record.
     * @return DataField containing 512 data
     * @throws UpdateException in case of an error
     */

    public DataField fetchNoteField(MarcRecord marcRecord) throws UpdateException {
        final StopWatch watch = new Log4JStopWatch("opencatBusiness.recategorizationNoteFieldFactory").setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        try {
            final String trackingId = MDC.get(MDC_TRACKING_ID_LOG_CONTEXT);

            return opencatBusinessConnector.recategorizationNoteFieldFactory(marcRecord, trackingId);
        } catch (OpencatBusinessConnectorException | JSONBException ex) {
            throw new UpdateException("Error when executing OpencatBusinessConnector function: changeUpdateRecordForUpdate", ex);
        } finally {
            watch.stop();
        }
    }
}
