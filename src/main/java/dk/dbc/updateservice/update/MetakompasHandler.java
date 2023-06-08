package dk.dbc.updateservice.update;

import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.marc.binding.DataField;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.marc.binding.SubField;
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
    private static final List<Character> atmosphereSubjectSubFields = List.of('n');
    private static final List<Character> nonAtmosphereSubjectSubFields = List.of('i', 'q', 'p', 'm', 'g', 'u', 'e', 'h', 'j', 'k', 'l', 's', 'r', 't', 'v');
    private static final String COMMON_RECORD_TEMPLATE =
            "001 00 *a*b190004*c*d*fa\n" +
                    "004 00 *rn*ae*xm\n" +
                    "008 00 *th*v0\n" +
                    "040 00 *bdan*fDBC\n" +
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

    private static void doUpdateMetakompasSubjectRecords(List<ServiceAction> children, GlobalActionState state, RawRepo rawRepo, String id, char subFieldName, String category, Properties properties, String subjectId)
            throws UpdateException {
        boolean makeCommon = true;
        boolean makeEnrich = true;
        String shortValue;
        List<DataField> fields;

        final MarcRecord mainRecord = UpdateRecordContentTransformer.decodeRecord(rawRepo.fetchRecord(subjectId, 190004).getContent());
        fields = mainRecord.getFields(DataField.class);
        for (DataField field : fields) {
            if (field.getTag().equals("670")) {
                makeCommon = false;
                break;
            }
        }
        if (makeCommon) {
            final MarcRecordWriter mWriter = new MarcRecordWriter(mainRecord);
            mWriter.setChangedTimestamp();
            mWriter.addFieldSubfield("670", 'a', id);
            children.add(new UpdateCommonRecordAction(state, properties, mainRecord));
        }

        final MarcRecord enrichmentRecord = UpdateRecordContentTransformer.decodeRecord(rawRepo.fetchRecord(subjectId, 191919).getContent());
        if ('n' == subFieldName) {
            shortValue = getMoodCategoryCode(category);
        } else {
            shortValue = String.valueOf(subFieldName);
        }
        fields = enrichmentRecord.getFields(DataField.class);
        for (DataField field : fields) {
            if (field.getTag().equals("d09")) {
                final List<SubField> subFields = field.getSubFields();
                subFields.add(new SubField('z', "EMK" + getWeekCode()));
            }
            if (field.getTag().equals("x09")) {
                final List<SubField> subFields = field.getSubFields();
                for (SubField subField : subFields) {
                    if ('p' == subField.getCode() && subField.getData().equals(shortValue))
                        makeEnrich = false;
                }

            }
        }
        if (makeEnrich) {
            final DataField x09Field = new DataField("x09", "00");
            final List<SubField> x09subFields = x09Field.getSubFields();
            x09subFields.add(new SubField('p', shortValue));
            final String metaCompassId = getNewIdNumber(properties, JNDIResources.OPENNUMBERROLL_NAME_FAUST);
            x09subFields.add(new SubField('q', metaCompassId));
            fields.add(x09Field);
            enrichmentRecord.getFields().clear();
            enrichmentRecord.getFields().addAll(fields);
            final MarcRecordWriter writer = new MarcRecordWriter(enrichmentRecord);
            writer.setChangedTimestamp();
            children.add(new UpdateEnrichmentRecordAction(state, properties, enrichmentRecord, 190004));
        }
    }

    private static void doCreateMetakompasSubjectRecords(List<ServiceAction> children, GlobalActionState state, String id, char subFieldName, String subfieldContent, String category, Properties properties)
            throws UpdateException {
        try {
            final MarcRecord commonSubjectRecord = UpdateRecordContentTransformer.readRecordFromString(COMMON_RECORD_TEMPLATE);
            final MarcRecordWriter writer = new MarcRecordWriter(commonSubjectRecord);
            final String newId = getNewIdNumber(properties, JNDIResources.OPENNUMBERROLL_NAME_FAUST_8);
            writer.addOrReplaceSubField("001", 'a', newId);
            writer.setChangedTimestamp();
            writer.setCreationTimestamp();
            writer.addOrReplaceSubField("165", 'a', subfieldContent);
            writer.addOrReplaceSubField("670", 'a', id);
            children.add(new CreateSingleRecordAction(state, properties, commonSubjectRecord));

            final MarcRecord enrichmentRecord = UpdateRecordContentTransformer.readRecordFromString(ENRICHMENT_RECORD_TEMPLATE);
            final MarcRecordWriter enrichmentWriter = new MarcRecordWriter(enrichmentRecord);
            enrichmentWriter.addOrReplaceSubField("001", 'a', newId);
            enrichmentWriter.setChangedTimestamp();
            enrichmentWriter.setCreationTimestamp();
            enrichmentWriter.addOrReplaceSubField("d09", 'z', "EMK" + getWeekCode());
            // This gets complicated - put different things in here depending on subfield
            if (atmosphereSubjectSubFields.contains(subFieldName)) {
                String shortValue = getMoodCategoryCode(category);
                enrichmentWriter.addOrReplaceSubField("x09", 'p', shortValue);
            } else {
                enrichmentWriter.addOrReplaceSubField("x09", 'p', String.valueOf(subFieldName));
            }
            final String metaCompassId = getNewIdNumber(properties, JNDIResources.OPENNUMBERROLL_NAME_FAUST);
            enrichmentWriter.addOrReplaceSubField("x09", 'q', metaCompassId);
            children.add(new UpdateEnrichmentRecordAction(state, properties, enrichmentRecord, 190004));
        } catch (UpdateException e) {
            throw new UpdateException("Creating subject record(s) failed", e);
        }

    }

    private static void identifyAction(List<ServiceAction> children, GlobalActionState state, String id, char subFieldName, String subfieldContent, String category, Properties properties, RawRepo rawRepo)
            throws UpdateException, SolrException {
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
            throws UpdateException, SolrException {
        final MarcRecordReader reader = new MarcRecordReader(minimalMetakompasRecord);
        final String id = reader.getRecordId();
        final List<DataField> listOf665 = reader.getFieldAll("665");
        for (DataField field : listOf665) {
            String category = "";
            if (field.getSubFields().stream().
                    anyMatch(subfield -> '&' == subfield.getCode() && "LEKTOR".equalsIgnoreCase(subfield.getData()))) {
                for (SubField subfield : field.getSubFields()) {
                    if ('&' == subfield.getCode() && !"LEKTOR".equalsIgnoreCase(subfield.getData())) {
                        category = subfield.getData();
                    }
                }
                for (SubField subfield : field.getSubFields()) {
                    final char subFieldName = subfield.getCode();
                    if ('&' != subFieldName) {
                        identifyAction(children, state, id, subFieldName, subfield.getData(), category, properties, rawRepo);
                    }
                }
            }
        }
    }
}
