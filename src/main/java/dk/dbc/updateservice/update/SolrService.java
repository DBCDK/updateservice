/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.updateservice.ws.JNDIResources;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Properties;
import java.util.ResourceBundle;

/**
 * EJB to make lookups in a SOLR index of the rawrepo database.
 */
@Stateless
public class SolrService {
    private static XLogger logger = XLoggerFactory.getXLogger(SolrService.class);

    @Resource(lookup = JNDIResources.JNDI_NAME_UPDATESERVICE)
    private Properties settings;

    private ResourceBundle messages;

    public SolrService() {
        this(new Properties());
    }

    public SolrService(Properties settings) {
        this.settings = settings;
        messages = ResourceBundles.getBundle("messages");
    }

    private URL setUrl(String query) throws UpdateException {
        String SOLR_QUERY_URL = "%s/select?q=%s&wt=json";
        logger.entry();
        try {
            if (settings.containsKey("solr.url")) {
                String url = settings.getProperty("solr.url");
                URL solrUrl = new URL(String.format(SOLR_QUERY_URL, url, URLEncoder.encode(query, "UTF-8")));
                logger.info("Solr call query: {} -> {}", query, solrUrl);
                return solrUrl;
            } else {
                throw new UpdateException("The key 'solr.url' does not exist in settings");
            }

        } catch (IOException ex) {
            // IOException covers several exceptions thrown by URL and URLEncoder
            throw new UpdateException("Bad encoding or malformed URL : " + ex.getMessage(), ex);
        }
    }

    private JsonObject callSolr(URL url) throws SolrException, UpdateException {
        logger.entry();

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
                logger.info("Solr response {} ==> {}", url.toString(), jObj.toString());
            } else {
                String s = String.format("Solr response {%s} ==> {%s}", url.toString(), jObj.toString());
                logger.warn(s);
                if (jObj.containsKey("error")) {
                    s = String.format("Solr returned error code %s: %s", jObj.getJsonObject("error").getInt("code"), jObj.getJsonObject("error").getString("msg"));
                    logger.warn(s);
                }
                throw new SolrException(messages.getString("solr.error.responsecode"));
            }
            if (jObj.containsKey("response")) {
                return jObj.getJsonObject("response");
            } else {
                String s = String.format("Solr response {%s} ==> {%s}", url.toString(), jObj.toString());
                logger.warn(s);
                if (jObj.containsKey("error")) {
                    s = String.format("Solr returned error code %s: %s", jObj.getJsonObject("error").getInt("code"), jObj.getJsonObject("error").getString("msg"));
                    logger.warn(s);
                } else {
                    s = String.format("Very strange - could not locate neither response nor error section in Solr response %s", jObj.toString());
                }
                throw new UpdateException(s);
            }
        } catch (IOException ex) {
            String s = "Unable to connect to url " + url.toString() + ": " + ex.getMessage();
            logger.warn(s);
            throw new SolrException(s, ex);
        } finally {
            logger.exit();
        }
    }

    long hits(String query) throws UpdateException, SolrException  {
        logger.entry(query);
        StopWatch watch = new Log4JStopWatch("service.solr.hits");
        URL solrUrl;

        try {
            solrUrl = setUrl(query);
            JsonObject response = callSolr(solrUrl);
            if (response.containsKey("numFound")) {
                return response.getInt("numFound");
            }
            String s = String.format("Unable to locate 'numFound' in Solr response %s", response.toString());
            logger.warn(s);
            throw new UpdateException(s);

        } finally {
            watch.stop();
            logger.exit();
        }
    }

    public String getOwnerOf002(String query) throws UpdateException, SolrException {
        logger.entry(query);
        StopWatch watch = new Log4JStopWatch("service.solr.getownerof002");
        URL solrUrl;

        String result = "";
        try {
            solrUrl = setUrl(query);
            JsonObject response = callSolr(solrUrl);
            if (response.containsKey("docs")) {
                JsonArray docsArray = response.getJsonArray("docs");
                for (JsonObject jObj : docsArray.getValuesAs(JsonObject.class)) {
                    JsonArray marc001aArray = jObj.getJsonArray("marc.001a");
                    if (marc001aArray != null) {
                        result = marc001aArray.getString(0);
                        break;
                    }

                }
            }
            return result;

        } finally {
            watch.stop();
            logger.exit(result);
        }
    }

    public boolean hasDocuments(String query) throws UpdateException, SolrException {
        logger.entry(query);
        StopWatch watch = new Log4JStopWatch("service.solr.hasdocuments");

        Boolean result = null;
        try {
            return result = hits(query) != 0L;
        } finally {
            watch.stop();
            logger.exit(result);
        }
    }

}
