package dk.dbc.updateservice.solr;

import dk.dbc.updateservice.update.SolrException;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.utils.ResourceBundles;
import dk.dbc.updateservice.update.JNDIResources;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.ejb.Stateless;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Properties;

@Stateless
public class SolrFBS extends SolrBase {
    private static XLogger logger = XLoggerFactory.getXLogger(SolrFBS.class);

    private Properties settings;

    public SolrFBS() {
        this(JNDIResources.getProperties());
    }

    public SolrFBS(Properties settings) {
        this.settings = settings;
        messages = ResourceBundles.getBundle("messages");
    }

    @Override
    protected URL setUrl(String query, String queryParam) throws UpdateException {
        String SOLR_QUERY_URL = "%s/select?q=%s&wt=json";
        logger.entry();
        try {
            if (settings.containsKey(JNDIResources.SOLR_URL)) {
                String url = settings.getProperty(JNDIResources.SOLR_URL);
                URL solrUrl = new URL(String.format(SOLR_QUERY_URL, url, URLEncoder.encode(query, "UTF-8")) + queryParam);
                logger.info("Solr call query: {} -> {}", query, solrUrl);
                return solrUrl;
            } else {
                throw new UpdateException("The key '" + JNDIResources.SOLR_URL + "' does not exist in settings");
            }
        } catch (IOException ex) {
            // IOException covers several exceptions thrown by URL and URLEncoder
            throw new UpdateException("Bad encoding or malformed URL : " + ex.getMessage(), ex);
        }
    }

    public String getOwnerOf002(String query) throws UpdateException, SolrException {
        logger.entry(query);
        StopWatch watch = new Log4JStopWatch("service.solr.getownerof002");
        URL solrUrl;

        String result = "";
        try {
            solrUrl = setUrl(query, "&fl=marc.001a");
            JsonObject response = callSolr(solrUrl);
            if (response.containsKey("docs")) {
                JsonArray docsArray = response.getJsonArray("docs");
                for (JsonObject jObj : docsArray.getValuesAs(JsonObject.class)) {
                    // Sometimes the value is an array and sometimes a string, so we need to check the type first
                    JsonValue jsonValue = jObj.get("marc.001a");
                    if (jsonValue.getValueType() == JsonValue.ValueType.ARRAY) {
                        JsonArray marc001aArray = (JsonArray) jsonValue;
                        result = marc001aArray.getString(0);
                        break;
                    } else if (jsonValue.getValueType() == JsonValue.ValueType.STRING) {
                        JsonString marc001aString = (JsonString) jsonValue;
                        result = marc001aString.getString();
                        break;
                    } else {
                        throw new SolrException("Expected type of marc.001a to be ARRAY or STRING but it was " + jsonValue.getValueType());
                    }
                }
            }
            return result;
        } finally {
            watch.stop();
            logger.exit(result);
        }
    }


}
