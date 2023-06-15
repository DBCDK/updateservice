package dk.dbc.updateservice.solr;

import dk.dbc.updateservice.update.JNDIResources;
import dk.dbc.updateservice.update.SolrException;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.utils.ResourceBundles;
import jakarta.ejb.Stateless;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Properties;

import static dk.dbc.updateservice.rest.ApplicationConfig.LOG_DURATION_THRESHOLD_MS;

@Stateless
public class SolrFBS extends SolrBase {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(SolrFBS.class);

    private final Properties settings;

    // EJB initialization will fail if there is no default constructor
    public SolrFBS() {
        this(JNDIResources.getProperties());
    }

    public SolrFBS(Properties settings) {
        this.settings = settings;
        messages = ResourceBundles.getBundle("messages");
    }

    @Override
    protected URL setUrl(String query, String queryParam) throws UpdateException {
        final String SOLR_QUERY_URL = "%s/select?q=%s&wt=json";
        try {
            if (settings.containsKey(JNDIResources.SOLR_URL)) {
                final String url = settings.getProperty(JNDIResources.SOLR_URL);
                final URL solrUrl = new URL(String.format(SOLR_QUERY_URL, url, URLEncoder.encode(query, "UTF-8")) + queryParam);
                LOGGER.info("Solr call query: {} -> {}", query, solrUrl);
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
        final StopWatch watch = new Log4JStopWatch("service.solr.getownerof002").setTimeThreshold(LOG_DURATION_THRESHOLD_MS);

        String result = "";
        try {
            final URL solrUrl = setUrl(query, "&fl=marc.001a");
            final JsonObject response = callSolr(solrUrl);
            if (response.containsKey("docs")) {
                final JsonArray docsArray = response.getJsonArray("docs");
                for (JsonObject jObj : docsArray.getValuesAs(JsonObject.class)) {
                    // Sometimes the value is an array and sometimes a string, so we need to check the type first
                    final JsonValue jsonValue = jObj.get("marc.001a");
                    if (jsonValue.getValueType() == JsonValue.ValueType.ARRAY) {
                        final JsonArray marc001aArray = (JsonArray) jsonValue;
                        result = marc001aArray.getString(0);
                        break;
                    } else if (jsonValue.getValueType() == JsonValue.ValueType.STRING) {
                        final JsonString marc001aString = (JsonString) jsonValue;
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
        }
    }


}
