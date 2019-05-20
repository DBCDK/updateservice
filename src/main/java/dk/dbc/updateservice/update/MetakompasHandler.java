/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.common.records.*;
import dk.dbc.common.records.utils.RecordContentTransformer;
import dk.dbc.updateservice.actions.*;
import dk.dbc.updateservice.ws.JNDIResources;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

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
import java.util.*;
import java.util.stream.Collectors;

public class MetakompasHandler {
    private static final XLogger logger = XLoggerFactory.getXLogger(MetakompasHandler.class);

    private static final List<String> metakompasSubFieldsToCopy = Arrays.asList("e", "g", "p", "m");
    private static final List<String> atmosphereSubjectSubFields = Collections.singletonList("n");
    private static final List<String> nonAtmosphereSubjectSubFields = Arrays.asList("i","q","p","m","g","u","e","h","j","k","l","s","r","t");
    private static final String commonRecordTemplate =
            "001 00 *a*b190004*c*d*fa*tFAUST\n" +
                    "004 00 *rn*ae*xm\n" +
                    "008 00 *th*v0\n" +
                    "040 00 *bdan*fDBC\n" +
                    "165 00 \n" +
                    "565 00 *n*xBT\n" +
                    "670 00 *a\n" +
                    "996 00 *aDBC\n";
    private static final String enrichmentRecordTemplate =
            "001 00 *a*b191919*c*d*fa*tFAUST\n" +
                    "004 00 *rn*ae*xm\n" +
                    "d08 00 *aMetakompas\n" +
                    "d09 00 *z\n" +
                    "x08 00 *ps\n" +
                    "x09 00 *p";

    private static String callUrl(String url) throws UpdateException {
        try {

            logger.info("Numberroll url : {}", url);
            URL numberUrl = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) numberUrl.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            InputStream is;
            int response = conn.getResponseCode();
            if (response == 200) {
                logger.info("Ok Went Well");
                is = conn.getInputStream();
            } else {
                logger.info("DIDNT Went Well {}", response);
                is = conn.getErrorStream();
            }
            JsonReader jReader = Json.createReader(is);
            JsonObject jObj = jReader.readObject();
            conn.disconnect();

            if (response == 200) {
                logger.info("Numberroll response {} ==> {}", url, jObj.toString());
            } else {
                String s = String.format("Numberroll response {%s} ==> {%s}", url, jObj.toString());
                logger.warn(s);
                if (jObj.containsKey("error")) {
                    s = String.format("Numberroll returned error code : %s", jObj.getJsonObject("error").toString());
                    logger.warn(s);
                }
                throw new UpdateException(s);
            }
            if (jObj.containsKey("numberRollResponse")) {
                return jObj.getJsonObject("numberRollResponse").getJsonObject("rollNumber").getString("$");
            } else {
                throw new UpdateException("Numberroll request did not contain a rollnumber");
            }
        } catch (IOException e) {
            logger.info("IOException {}", e.getMessage());
            throw new UpdateException(e.getMessage());
        }
    }

    private static String getNewIdNumber(Properties properties) throws UpdateException {
        if (properties.containsKey(JNDIResources.PROP_OPENNUMBERROLL_URL)) {
            String url = properties.getProperty(JNDIResources.PROP_OPENNUMBERROLL_URL);
            logger.info("Numberroll url {}", properties.getProperty(JNDIResources.PROP_OPENNUMBERROLL_URL));
            if (properties.containsKey(JNDIResources.PROP_OPENNUMBERROLL_NAME_FAUST)) {
                logger.info("Numberroll name {}", properties.getProperty(JNDIResources.PROP_OPENNUMBERROLL_NAME_FAUST));
                String res = callUrl(url + "?action=numberRoll&numberRollName=" + properties.getProperty(JNDIResources.PROP_OPENNUMBERROLL_NAME_FAUST) + "&outputType=json");
                logger.info("Got new id number {} ", res);
                return res;
            } else throw new UpdateException("No configuration numberroll");
        } else throw new UpdateException("No configuration for opennumberroll service");
    }

    private static void doCreateMetakompasSubjectRecords(List<ServiceAction> children, GlobalActionState state, String id, String index, String subFieldName, String subfieldContent, String category, Properties properties)
            throws UpdateException, SolrException {
        try {
            String solrQuery = SolrServiceIndexer.createGetSubjectHits(index, subfieldContent);
            if (!state.getSolrService().hasDocuments(solrQuery)) {
                MarcRecord commonSubjectRecord = MarcRecordFactory.readRecord(commonRecordTemplate);
                MarcRecordWriter writer = new MarcRecordWriter(commonSubjectRecord);
                String newId = getNewIdNumber(properties);
                writer.addOrReplaceSubfield("001", "a", newId);
                writer.setChangedTimestamp();
                writer.setCreationTimestamp();
                if ("n".equals(subFieldName)) writer.addOrReplaceSubfield("165", "n", subfieldContent);
                    else writer.addOrReplaceSubfield("165", "a", subfieldContent);
                if ("".equals(category)) {
                    writer.removeField("565");
                } else writer.addOrReplaceSubfield("565", "n", category);
                writer.addOrReplaceSubfield("670", "a", id);
                children.add(new CreateSingleRecordAction(state, properties, commonSubjectRecord));

                MarcRecord enrichmentRecord = MarcRecordFactory.readRecord(enrichmentRecordTemplate);
                writer = new MarcRecordWriter(enrichmentRecord);
                writer.addOrReplaceSubfield("001", "a", newId);
                writer.setChangedTimestamp();
                writer.setCreationTimestamp();
                LocalDate ld = LocalDate.now();
                DateTimeFormatter ywFormat = DateTimeFormatter.ofPattern("yyyyww");
                String weekCode = ld.format(ywFormat);
                writer.addOrReplaceSubfield("d09", "z", "EMK" + weekCode);
                writer.addOrReplaceSubfield("x09", "p", subFieldName);
                children.add(new UpdateEnrichmentRecordAction(state, properties, enrichmentRecord, 190004));
            }
        } catch (UpdateException | SolrException e) {
            logger.info("Creating subject record(s) failed {}", e.getMessage());
            throw e;
        }

    }

    /**
     * This function creates subject records for subjects mentioned in field 665 and not found in the subject database.
     * @param minimalMetakompasRecord The record that has to be checked
     *
     */
    public static void createMetakompasSubjectRecords(List<ServiceAction> children, GlobalActionState state, MarcRecord minimalMetakompasRecord, Properties properties)
            throws UpdateException, SolrException {

        MarcRecordReader reader = new MarcRecordReader(minimalMetakompasRecord);
        String id = reader.getRecordId();
        List<MarcField> listOf665 = reader.getFieldAll("665");
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
                    String subFieldName = subfield.getName();
                    if (!"&".equals(subFieldName)) {
                        if (atmosphereSubjectSubFields.contains(subFieldName)) {
                            doCreateMetakompasSubjectRecords(children, state, id, "phrase.vln", subFieldName, subfield.getValue(), category, properties);
                        } else {
                            if (nonAtmosphereSubjectSubFields.contains(subfield.getName())) {
                                doCreateMetakompasSubjectRecords(children, state, id, "phrase.vla", subFieldName, subfield.getValue(), category, properties);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * This function handles the situation where metakompas sends a minimal record to updateservice
     * <p>
     * The metakompas templates only allow fields 001, 004 and 665. The template is used only by the metakompas application.
     * <p>
     * When metakompas template is used we need to load the existing record and then use that with replaced 665 field from the input
     * <p>
     * Additionally certain 665 subfields are copied to 666 subfields.
     *
     * @return The record to be used for the rest if the execution
     * @throws UnsupportedEncodingException Thrown if the record has wrong encoding
     * @throws UpdateException Thrown when the record doesn't exist - don't expect it to happen because the
     *                         enrichMetakompasRecord function will catch this too.
     */
    public static MarcRecord enrichMetakompasRecord(RawRepo rawRepo, MarcRecord minimalMetakompasRecord) throws UnsupportedEncodingException, UpdateException {
        logger.info("Got metakompas template so updated the request record.");
        logger.info("Input metakompas record: \n{}", minimalMetakompasRecord);

        MarcRecordReader reader = new MarcRecordReader(minimalMetakompasRecord);

        if (!rawRepo.recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())) {
            throw new UpdateException("In order to update field 665 the record must exist");
        }

        MarcRecord fullMetakompassRecord = RecordContentTransformer.decodeRecord(rawRepo.fetchMergedDBCRecord(reader.getRecordId(), RawRepo.DBC_ENRICHMENT).getContent());
        MarcRecordWriter fullMetakompassRecordWriter = new MarcRecordWriter(fullMetakompassRecord);
        fullMetakompassRecordWriter.removeField("665");
        fullMetakompassRecord.getFields().addAll(reader.getFieldAll("665"));

        boolean hasAdded666Subfield = false;
        /*
         * If the record is not yet published and the record is send from metakompas then copy relevant 665 subfields to 666.
         *
         * Note that in order to be able manually edit the copied 666 subfields the copy only happens when using the
         * metakompas schema.
         */
        if (!CatalogExtractionCode.isPublished(fullMetakompassRecord)) {
            hasAdded666Subfield = copyMetakompasFields(fullMetakompassRecord);
        }

        // If no 666 subfields are updated (either because there was no change or because the record is published) then
        // we must add *z98 Minus korrekturprint to suppress unnecessary proof printing
        if (!hasAdded666Subfield) {
            addMinusProofPrinting(fullMetakompassRecord);
        }

        fullMetakompassRecordWriter.sort();

        logger.info("Output metakompas record: \n{}", fullMetakompassRecord);

        return fullMetakompassRecord;
    }

    /**
     * If the record is still under production then all 665 *q, *e, *i and *g subfields must be copied to 666
     */
    static boolean copyMetakompasFields(MarcRecord record) {
        boolean hasAdded666Subfield = false;
        final List<MarcSubField> subfieldsToCopy = new ArrayList<>();
        final List<MarcField> fields665 = record.getFields().stream().
                filter(field -> "665".equals(field.getName())).
                collect(Collectors.toList());

        for (MarcField field : fields665) {
            if (field.getSubfields().stream().
                    anyMatch(subfield -> "&".equals(subfield.getName()) && "LEKTOR".equalsIgnoreCase(subfield.getValue()))) {
                for (MarcSubField subfield : field.getSubfields()) {
                    // 665 *q -> 666 *q
                    if ("q".equals(subfield.getName())) {
                        subfieldsToCopy.add(new MarcSubField("q", subfield.getValue()));
                    }

                    // 665 *i -> 666 *i is year interval, otherwise *i -> *s
                    if ("i".equals(subfield.getName())) {
                        if (isYearInterval(subfield.getValue())) {
                            subfieldsToCopy.add(new MarcSubField("i", subfield.getValue()));
                        } else {
                            subfieldsToCopy.add(new MarcSubField("s", subfield.getValue()));
                        }
                    }

                    // 665 *e/*g/*p/*m -> 666 *s
                    if (metakompasSubFieldsToCopy.contains(subfield.getName())) {
                        subfieldsToCopy.add(new MarcSubField("s", subfield.getValue()));
                    }
                }
            }
        }

        if (subfieldsToCopy.size() > 0) {
            logger.info("Found {} number of 665 subfield to copy");
            // Fields added by automation should always have an empty *0
            final MarcSubField subfield0 = new MarcSubField("0", "");
            final List<MarcField> fields666 = record.getFields().stream().
                    filter(field -> "666".equals(field.getName())).
                    collect(Collectors.toList());

            for (MarcSubField subfieldToCopy : subfieldsToCopy) {
                boolean hasSubfield = false;
                for (MarcField field666 : fields666) {
                    if (field666.getSubfields().contains(subfieldToCopy)) {
                        // If the field has the subfield to copy but doesn't have *0 subfield then *0 must be added
                        if (!field666.getSubfields().contains(subfield0)) {
                            field666.getSubfields().add(0, subfield0);
                        }

                        hasSubfield = true;
                        break;
                    }
                }

                if (!hasSubfield) {
                    record.getFields().add(new MarcField("666", "00", Arrays.asList(subfield0, subfieldToCopy)));
                    hasAdded666Subfield = true;
                }
            }
        }

        return hasAdded666Subfield;
    }

    static void addMinusProofPrinting(MarcRecord record) {
        new MarcRecordWriter(record).addOrReplaceSubfield("z98", "a", "Minus korrekturprint");
    }

    /**
     * Check if a string matches the year interval pattern
     *
     * @param value The string to check
     * @return True if the pattern matches otherwise False
     */
    static boolean isYearInterval(String value) {
        return value.matches("\\d+-\\d+");
    }

}
