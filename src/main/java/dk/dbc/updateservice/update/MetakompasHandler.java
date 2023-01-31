
package dk.dbc.updateservice.update;

import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordFactory;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.common.records.MarcSubField;
import dk.dbc.common.records.utils.RecordContentTransformer;
import dk.dbc.updateservice.actions.CreateSingleRecordAction;
import dk.dbc.updateservice.actions.GlobalActionState;
import dk.dbc.updateservice.actions.ServiceAction;
import dk.dbc.updateservice.actions.UpdateCommonRecordAction;
import dk.dbc.updateservice.actions.UpdateEnrichmentRecordAction;
import dk.dbc.updateservice.utils.DeferredLogger;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static java.util.Map.entry;

public class MetakompasHandler {
    private static final DeferredLogger LOGGER = new DeferredLogger(MetakompasHandler.class);

    private static final String INDEX_NAME = "phrase.vla";
    private static final Map<String, String> MOOD_CATEGORY_CODES = Map.ofEntries(
            entry("positiv", "na"),
            entry("humoristisk", "nb"),
            entry("romantisk", "nc"),
            entry("erotisk", "nd"),
            entry("dramatisk", "ne"),
            entry("trist", "nf"),
            entry("uhyggelig", "ng"),
            entry("fantasifuld", "nh"),
            entry("tankevækkende", "ni"));
    private static final List<String> atmosphereSubjectSubFields = Collections.singletonList("n");
    private static final List<String> nonAtmosphereSubjectSubFields = Arrays.asList("i", "q", "p", "m", "g", "u", "e", "h", "j", "k", "l", "s", "r", "t", "v");
    private static final String COMMON_RECORD_TEMPLATE =
            "001 00 *a*b190004*c*d*fa\n" +
                    "004 00 *rn*ae*xm\n" +
                    "008 00 *th*v0\n" +
                    "040 00 *bdan*fDBC\n" +
                    "165 00 \n" +
                    "670 00 *a\n" +
                    "996 00 *aDBC\n";
    private static final String ENRICHMENT_RECORD_TEMPLATE =
            "001 00 *a*b191919*c*d*fa\n" +
                    "004 00 *rn*ae*xm\n" +
                    "d08 00 *aMetakompas\n" +
                    "d09 00 *z\n" +
                    "x08 00 *ps\n" +
                    "x09 00 *p*q";

    private MetakompasHandler() {

    }

    private static String callUrl(String url) throws UpdateException {
        return LOGGER.callChecked(log -> {
            try {
                log.info("Numberroll url : {}", url);
                final URL numberUrl = new URL(url);
                final HttpURLConnection conn = (HttpURLConnection) numberUrl.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                InputStream is;
                final int response = conn.getResponseCode();
                if (response == 200) {
                    log.info("Ok Went Well");
                    is = conn.getInputStream();
                } else {
                    log.info("DIDNT Went Well {}", response);
                    is = conn.getErrorStream();
                }
                final JsonReader jReader = Json.createReader(is);
                final JsonObject jObj = jReader.readObject();
                conn.disconnect();

                if (response == 200) {
                    log.info("Numberroll response {} ==> {}", url, jObj.toString());
                } else {
                    String s = String.format("Numberroll response {%s} ==> {%s}", url, jObj.toString());
                    log.warn(s);
                    if (jObj.containsKey("error")) {
                        s = String.format("Numberroll returned error code : %s", jObj.getJsonObject("error").toString());
                        log.warn(s);
                    }
                    throw new UpdateException(s);
                }
                if (jObj.containsKey("numberRollResponse")) {
                    return jObj.getJsonObject("numberRollResponse").getJsonObject("rollNumber").getString("$");
                } else {
                    throw new UpdateException("Numberroll request did not contain a rollnumber");
                }
            } catch (IOException e) {
                throw new UpdateException(e.getMessage());
            }
        });
    }

    private static String getNewIdNumber(Properties properties, String rollName) throws UpdateException {
        return LOGGER.callChecked(log -> {
            if (properties.containsKey(JNDIResources.OPENNUMBERROLL_URL)) {
                final String url = properties.getProperty(JNDIResources.OPENNUMBERROLL_URL);
                log.info("Numberroll url {}", properties.getProperty(JNDIResources.OPENNUMBERROLL_URL));
                if (properties.containsKey(rollName)) {
                    log.info("Numberroll name {}", properties.getProperty(rollName));
                    final String res = callUrl(url + "?action=numberRoll&numberRollName=" + properties.getProperty(rollName) + "&outputType=json");
                    log.info("Got new id number {} ", res);
                    return res;
                } else {
                    throw new UpdateException("No configuration numberroll");
                }
            } else {
                throw new UpdateException("No configuration for opennumberroll service");
            }
        });
    }

    private static String getMoodCategoryCode(String subfieldContent) {
        return MOOD_CATEGORY_CODES.getOrDefault(subfieldContent, "");
    }

    private static String getWeekCode() {
        final LocalDate ld = LocalDate.now();
        final DateTimeFormatter ywFormat = DateTimeFormatter.ofPattern("yyyyww");
        return ld.format(ywFormat);
    }

    private static void doUpdateMetakompasSubjectRecords(List<ServiceAction> children, GlobalActionState state, RawRepo rawRepo, String id, String subFieldName, String category, Properties properties, String subjectId)
            throws UpdateException, UnsupportedEncodingException {
        boolean makeCommon = true;
        boolean makeEnrich = true;
        String shortValue;
        List<MarcField> fields;

        final MarcRecord mainRecord = RecordContentTransformer.decodeRecord(rawRepo.fetchRecord(subjectId, 190004).getContent());
        fields = mainRecord.getFields();
        for (MarcField field : fields) {
            if (field.getName().equals("670")) {
                makeCommon = false;
                break;
            }
        }
        if (makeCommon) {
            final MarcRecordWriter mWriter = new MarcRecordWriter(mainRecord);
            mWriter.setChangedTimestamp();
            mWriter.addFieldSubfield("670", "a", id);
            children.add(new UpdateCommonRecordAction(state, properties, mainRecord));
        }

        final MarcRecord enrichmentRecord = RecordContentTransformer.decodeRecord(rawRepo.fetchRecord(subjectId, 191919).getContent());
        if (subFieldName.equals("n")) {
            shortValue = getMoodCategoryCode(category);
        } else {
            shortValue = subFieldName;
        }
        fields = enrichmentRecord.getFields();
        for (MarcField field : fields) {
            if (field.getName().equals("d09")) {
                final   List<MarcSubField> subFields = field.getSubfields();
                subFields.add(new MarcSubField("z", "EMK" + getWeekCode()));
            }
            if (field.getName().equals("x09")) {
                final List<MarcSubField> subFields = field.getSubfields();
                for (MarcSubField subField : subFields) {
                    if (subField.getName().equals("p") && subField.getValue().equals(shortValue))
                        makeEnrich = false;
                }

            }
        }
        if (makeEnrich) {
            final MarcField x09Field = new MarcField();
            x09Field.setName("x09");
            x09Field.setIndicator("00");
            final List<MarcSubField> x09subFields = x09Field.getSubfields();
            x09subFields.add(new MarcSubField("p", shortValue));
            final String metaCompassId = getNewIdNumber(properties, JNDIResources.OPENNUMBERROLL_NAME_FAUST);
            x09subFields.add(new MarcSubField("q", metaCompassId));
            fields.add(x09Field);
            enrichmentRecord.setFields(fields);
            final MarcRecordWriter writer = new MarcRecordWriter(enrichmentRecord);
            writer.setChangedTimestamp();
            children.add(new UpdateEnrichmentRecordAction(state, properties, enrichmentRecord, 190004));
        }
    }

    private static void doCreateMetakompasSubjectRecords(List<ServiceAction> children, GlobalActionState state, String id, String subFieldName, String subfieldContent, String category, Properties properties)
            throws UpdateException {
        try {
            final MarcRecord commonSubjectRecord = MarcRecordFactory.readRecord(COMMON_RECORD_TEMPLATE);
            final MarcRecordWriter writer = new MarcRecordWriter(commonSubjectRecord);
            final String newId = getNewIdNumber(properties, JNDIResources.OPENNUMBERROLL_NAME_FAUST_8);
            writer.addOrReplaceSubfield("001", "a", newId);
            writer.setChangedTimestamp();
            writer.setCreationTimestamp();
            writer.addOrReplaceSubfield("165", "a", subfieldContent);
            writer.addOrReplaceSubfield("670", "a", id);
            children.add(new CreateSingleRecordAction(state, properties, commonSubjectRecord));

            final MarcRecord enrichmentRecord = MarcRecordFactory.readRecord(ENRICHMENT_RECORD_TEMPLATE);
            final MarcRecordWriter enrichmentWriter = new MarcRecordWriter(enrichmentRecord);
            enrichmentWriter.addOrReplaceSubfield("001", "a", newId);
            enrichmentWriter.setChangedTimestamp();
            enrichmentWriter.setCreationTimestamp();
            enrichmentWriter.addOrReplaceSubfield("d09", "z", "EMK" + getWeekCode());
            // This gets complicated - put different things in here depending on subfield
            if (atmosphereSubjectSubFields.contains(subFieldName)) {
                String shortValue = getMoodCategoryCode(category);
                enrichmentWriter.addOrReplaceSubfield("x09", "p", shortValue);
            } else {
                enrichmentWriter.addOrReplaceSubfield("x09", "p", subFieldName);
            }
            final String metaCompassId = getNewIdNumber(properties, JNDIResources.OPENNUMBERROLL_NAME_FAUST);
            enrichmentWriter.addOrReplaceSubfield("x09", "q", metaCompassId);
            children.add(new UpdateEnrichmentRecordAction(state, properties, enrichmentRecord, 190004));
        } catch (UpdateException e) {
            throw new UpdateException("Creating subject record(s) failed", e);
        }

    }

    private static void identifyAction(List<ServiceAction> children, GlobalActionState state, String id, String subFieldName, String subfieldContent, String category, Properties properties, RawRepo rawRepo)
            throws UpdateException, SolrException, UnsupportedEncodingException {
        if (!(atmosphereSubjectSubFields.contains(subFieldName) || nonAtmosphereSubjectSubFields.contains(subFieldName)))
            return;
        final String solrQuery = SolrServiceIndexer.createGetSubjectId(INDEX_NAME, subfieldContent);
        final String subjectId = state.getSolrBasis().getSubjectIdNumber(solrQuery);
        if (subjectId.equals("")) {
            // create new subject record
            doCreateMetakompasSubjectRecords(children, state, id, subFieldName, subfieldContent, category, properties);
        } else {
            // update new subject record - note: x09p differ if n or anything else
            doUpdateMetakompasSubjectRecords(children, state, rawRepo, id, subFieldName, category, properties, subjectId);
        }
    }

    /**
     * This function creates subject records for subjects mentioned in field 665 and not found in the subject database.
     *
     * @param minimalMetakompasRecord The record that has to be checked
     */
    public static void createMetakompasSubjectRecords(List<ServiceAction> children, GlobalActionState state, RawRepo rawRepo, MarcRecord minimalMetakompasRecord, Properties properties)
            throws UpdateException, SolrException, UnsupportedEncodingException {
        final MarcRecordReader reader = new MarcRecordReader(minimalMetakompasRecord);
        final String id = reader.getRecordId();
        final List<MarcField> listOf665 = reader.getFieldAll("665");
        for (MarcField field : listOf665) {
            String category = "";
            if (field.getSubfields().stream().
                    anyMatch(subfield -> "&".equals(subfield.getName()) && "LEKTOR".equalsIgnoreCase(subfield.getValue()))) {
                for (MarcSubField subfield : field.getSubfields()) {
                    if ("&".equals(subfield.getName()) && !"LEKTOR".equalsIgnoreCase(subfield.getValue())) {
                        category = subfield.getValue();
                    }
                }
                for (MarcSubField subfield : field.getSubfields()) {
                    final String subFieldName = subfield.getName();
                    if (!"&".equals(subFieldName)) {
                        identifyAction(children, state, id, subFieldName, subfield.getValue(), category, properties, rawRepo);
                    }
                }
            }
        }
    }
}
