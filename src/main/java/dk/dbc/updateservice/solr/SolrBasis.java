package dk.dbc.updateservice.solr;

import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.utils.ResourceBundles;
import dk.dbc.updateservice.ws.JNDIResources;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Properties;

@Stateless
public class SolrBasis extends SolrBase {
    private static XLogger logger = XLoggerFactory.getXLogger(SolrBasis.class);

    @Resource(lookup = JNDIResources.JNDI_NAME_UPDATESERVICE)
    private Properties settings;

    public SolrBasis() {
        this(new Properties());
    }

    public SolrBasis(Properties settings) {
        this.settings = settings;
        messages = ResourceBundles.getBundle("messages");
    }

    @Override
    protected URL setUrl(String query, String queryParam) throws UpdateException {
        String SOLR_QUERY_URL = "%s/select?q=%s&wt=json";
        logger.entry();
        try {
            if (settings.containsKey(JNDIResources.SOLR_BASIS_URL_KEY)) {
                String url = settings.getProperty(JNDIResources.SOLR_BASIS_URL_KEY);
                logger.info("Using basis solr url: {}", url);
                URL solrUrl = new URL(String.format(SOLR_QUERY_URL, url, URLEncoder.encode(query, "UTF-8")) + queryParam);
                logger.info("Solr call query: {} -> {}", query, solrUrl);
                return solrUrl;
            } else {
                throw new UpdateException("The key '" + JNDIResources.SOLR_BASIS_URL_KEY + "' does not exist in settings");
            }
        } catch (IOException ex) {
            // IOException covers several exceptions thrown by URL and URLEncoder
            throw new UpdateException("Bad encoding or malformed URL : " + ex.getMessage(), ex);
        }
    }
}
