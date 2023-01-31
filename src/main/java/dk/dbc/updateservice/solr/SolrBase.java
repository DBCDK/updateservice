/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.solr;

import dk.dbc.updateservice.update.SolrException;
import dk.dbc.updateservice.update.UpdateException;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ResourceBundle;

import static dk.dbc.updateservice.rest.ApplicationConfig.LOG_DURATION_THRESHOLD_MS;

public abstract class SolrBase {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(SolrBase.class);
    private static final String ERROR_CODE = "error";
    private static final String NUM_FOUND = "numFound";

    protected ResourceBundle messages;

    protected abstract URL setUrl(String query, String queryParam) throws UpdateException;

    protected JsonObject callSolr(URL url) throws SolrException, UpdateException {
        int responseCode;
        try {
            final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

            InputStream is;
            responseCode = conn.getResponseCode();

            if (responseCode == 200) {
                is = conn.getInputStream();
            } else {
                is = conn.getErrorStream();
            }

            try (JsonReader jReader = Json.createReader(is)) {
                final JsonObject jObj = jReader.readObject();

                conn.disconnect();

                if (responseCode == 200) {
                    LOGGER.info("Solr response {} ==> {}", url.toString(), jObj.toString());
                } else {
                    String s = String.format("Solr response {%s} ==> {%s}", url, jObj.toString());
                    LOGGER.warn(s);
                    if (jObj.containsKey(ERROR_CODE)) {
                        s = String.format("Solr returned error code %s: %s", jObj.getJsonObject(ERROR_CODE).getInt("code"), jObj.getJsonObject(ERROR_CODE).getString("msg"));
                        LOGGER.warn(s);
                    }
                    throw new SolrException(messages.getString("solr.error.responsecode"));
                }
                if (jObj.containsKey("response")) {
                    return jObj.getJsonObject("response");
                } else {
                    String s = String.format("Solr response {%s} ==> {%s}", url, jObj);
                    LOGGER.warn(s);
                    if (jObj.containsKey(ERROR_CODE)) {
                        s = String.format("Solr returned error code %s: %s", jObj.getJsonObject(ERROR_CODE).getInt("code"), jObj.getJsonObject(ERROR_CODE).getString("msg"));
                        LOGGER.warn(s);
                    } else {
                        s = String.format("Very strange - could not locate neither response nor error section in Solr response %s", jObj);
                    }
                    throw new UpdateException(s);
                }
            }
        } catch (IOException ex) {
            String s = "Unable to connect to url " + url;
            throw new SolrException(s, ex);
        }
    }

    public long hits(String query) throws UpdateException {
        final StopWatch watch = new Log4JStopWatch("service.solr.hits").setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        try {
            final URL solrUrl = setUrl(query, "");
            final JsonObject response = callSolr(solrUrl);
            if (response.containsKey(NUM_FOUND)) {
                return response.getInt(NUM_FOUND);
            }
            final String s = String.format("Unable to locate 'numFound' in Solr response %s", response);
            throw new UpdateException(s);
        } finally {
            watch.stop();
        }
    }

    public String getSubjectIdNumber(String query) throws UpdateException {
        final StopWatch watch = new Log4JStopWatch("service.solr.hits").setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        try {
            final URL solrUrl = setUrl(query, "&fl=marc.001a");
            final JsonObject response = callSolr(solrUrl);
            if (response.containsKey(NUM_FOUND)) {
                if (response.getInt(NUM_FOUND) == 0) return "";
                else {
                    // Message from LJL - if more than one records, then treat the first
                    if (response.containsKey("docs")) {
                        final JsonArray docsArray = response.getJsonArray("docs");
                        for (JsonObject jObj : docsArray.getValuesAs(JsonObject.class)) {
                            // Sometimes the value is an array and sometimes a string, so we need to check the type first
                            final JsonValue jsonValue = jObj.get("marc.001a");
                            if (jsonValue.getValueType() == JsonValue.ValueType.ARRAY) {
                                final JsonArray marc001aArray = (JsonArray) jsonValue;
                                return marc001aArray.getString(0);
                            } else if (jsonValue.getValueType() == JsonValue.ValueType.STRING) {
                                final JsonString marc001aString = (JsonString) jsonValue;
                                return marc001aString.getString();
                            } else {
                                throw new SolrException("Expected type of marc.001a to be ARRAY or STRING but it was " + jsonValue.getValueType());
                            }
                        }
                    }
                }
            }
            String s = String.format("Unable to locate 'numFound' in Solr response %s", response);
            LOGGER.warn(s);
            throw new UpdateException(s);
        } finally {
            watch.stop();
        }
    }

    public boolean hasDocuments(String query) throws UpdateException {
        final StopWatch watch = new Log4JStopWatch("service.solr.hasdocuments").setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        try {
            return hits(query) != 0L;
        } finally {
            watch.stop();
        }
    }
}
