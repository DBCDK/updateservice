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

public abstract class SolrBase {
    private final static XLogger LOGGER = XLoggerFactory.getXLogger(SolrBase.class);

    protected ResourceBundle messages;

    protected abstract URL setUrl(String query, String queryParam) throws UpdateException;

    protected JsonObject callSolr(URL url) throws SolrException, UpdateException {
        LOGGER.entry();

        int responseCode;
        try {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

            InputStream is;
            responseCode = conn.getResponseCode();

            if (responseCode == 200) {
                is = conn.getInputStream();
            } else {
                is = conn.getErrorStream();
            }

            JsonReader jReader = Json.createReader(is);
            JsonObject jObj = jReader.readObject();
            conn.disconnect();

            if (responseCode == 200) {
                LOGGER.info("Solr response {} ==> {}", url.toString(), jObj.toString());
            } else {
                String s = String.format("Solr response {%s} ==> {%s}", url.toString(), jObj.toString());
                LOGGER.warn(s);
                if (jObj.containsKey("error")) {
                    s = String.format("Solr returned error code %s: %s", jObj.getJsonObject("error").getInt("code"), jObj.getJsonObject("error").getString("msg"));
                    LOGGER.warn(s);
                }
                throw new SolrException(messages.getString("solr.error.responsecode"));
            }
            if (jObj.containsKey("response")) {
                return jObj.getJsonObject("response");
            } else {
                String s = String.format("Solr response {%s} ==> {%s}", url.toString(), jObj.toString());
                LOGGER.warn(s);
                if (jObj.containsKey("error")) {
                    s = String.format("Solr returned error code %s: %s", jObj.getJsonObject("error").getInt("code"), jObj.getJsonObject("error").getString("msg"));
                    LOGGER.warn(s);
                } else {
                    s = String.format("Very strange - could not locate neither response nor error section in Solr response %s", jObj.toString());
                }
                throw new UpdateException(s);
            }
        } catch (IOException ex) {
            String s = "Unable to connect to url " + url.toString() + ": " + ex.getMessage();
            LOGGER.warn(s);
            throw new SolrException(s, ex);
        } finally {
            LOGGER.exit();
        }
    }

    public long hits(String query) throws UpdateException, SolrException {
        LOGGER.entry(query);
        StopWatch watch = new Log4JStopWatch("service.solr.hits");
        URL solrUrl;

        try {
            solrUrl = setUrl(query, "");
            JsonObject response = callSolr(solrUrl);
            if (response.containsKey("numFound")) {
                return response.getInt("numFound");
            }
            String s = String.format("Unable to locate 'numFound' in Solr response %s", response.toString());
            LOGGER.warn(s);
            throw new UpdateException(s);
        } finally {
            watch.stop();
            LOGGER.exit();
        }
    }
    public String getSubjectIdNumber(String query) throws UpdateException, SolrException {
        LOGGER.entry(query);
        StopWatch watch = new Log4JStopWatch("service.solr.hits");
        URL solrUrl;

        try {
            solrUrl = setUrl(query, "&fl=marc.001a");
            JsonObject response = callSolr(solrUrl);
            if (response.containsKey("numFound")) {
                if (response.getInt("numFound") == 0) return "";
                else {
                    // Message from LJL - if more than one records, then treat the first
                    if (response.containsKey("docs")) {
                        JsonArray docsArray = response.getJsonArray("docs");
                        for (JsonObject jObj : docsArray.getValuesAs(JsonObject.class)) {
                            // Sometimes the value is an array and sometimes a string, so we need to check the type first
                            JsonValue jsonValue = jObj.get("marc.001a");
                            if (jsonValue.getValueType() == JsonValue.ValueType.ARRAY) {
                                JsonArray marc001aArray = (JsonArray) jsonValue;
                                return marc001aArray.getString(0);
                            } else if (jsonValue.getValueType() == JsonValue.ValueType.STRING) {
                                JsonString marc001aString = (JsonString) jsonValue;
                                return marc001aString.getString();
                            } else {
                                throw new SolrException("Expected type of marc.001a to be ARRAY or STRING but it was " + jsonValue.getValueType());
                            }
                        }
                    }
                }
            }
            String s = String.format("Unable to locate 'numFound' in Solr response %s", response.toString());
            LOGGER.warn(s);
            throw new UpdateException(s);
        } finally {
            watch.stop();
            LOGGER.exit();
        }
    }

    public boolean hasDocuments(String query) throws UpdateException, SolrException {
        LOGGER.entry(query);
        StopWatch watch = new Log4JStopWatch("service.solr.hasdocuments");

        Boolean result = null;
        try {
            return result = hits(query) != 0L;
        } finally {
            watch.stop();
            LOGGER.exit(result);
        }
    }
}
