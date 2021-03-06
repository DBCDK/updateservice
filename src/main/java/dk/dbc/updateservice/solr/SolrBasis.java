/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.solr;

import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.utils.ResourceBundles;
import dk.dbc.updateservice.update.JNDIResources;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.ejb.Stateless;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Properties;

@Stateless
public class SolrBasis extends SolrBase {
    private static XLogger logger = XLoggerFactory.getXLogger(SolrBasis.class);

    private Properties settings;

    public SolrBasis() {
        this(JNDIResources.getProperties());
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
            if (settings.containsKey(JNDIResources.SOLR_BASIS_URL)) {
                String url = settings.getProperty(JNDIResources.SOLR_BASIS_URL);
                logger.info("Using basis solr url: {}", url);
                URL solrUrl = new URL(String.format(SOLR_QUERY_URL, url, URLEncoder.encode(query, "UTF-8")) + queryParam);
                logger.info("Solr call query: {} -> {}", query, solrUrl);
                return solrUrl;
            } else {
                throw new UpdateException("The key '" + JNDIResources.SOLR_BASIS_URL + "' does not exist in settings");
            }
        } catch (IOException ex) {
            // IOException covers several exceptions thrown by URL and URLEncoder
            throw new UpdateException("Bad encoding or malformed URL : " + ex.getMessage(), ex);
        }
    }
}
