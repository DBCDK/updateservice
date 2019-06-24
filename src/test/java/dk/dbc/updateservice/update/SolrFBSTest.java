/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dk.dbc.updateservice.solr.SolrFBS;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

public class SolrFBSTest {
    private WireMockServer solrServer;

    @Before
    public void startSolrServer() {
        WireMockConfiguration wireMockConfiguration = wireMockConfig().
                port(9090).
                withRootDirectory("src/test/resources/wiremock/solr");

        solrServer = new WireMockServer(wireMockConfiguration);
        solrServer.start();
    }

    @After
    public void stopSolrServer() {
        solrServer.stop();
    }

    @Test
    public void getOwnerOf002List() throws Exception {
        Properties settings = new Properties();
        settings.put("solr.url", "http://localhost:9090/solr/raw-repo-index");

        SolrFBS instance = new SolrFBS(settings);
        assertThat(instance.getOwnerOf002("marc.002a:06605141"), is("2 041 237 2"));
    }

    @Test
    public void getOwnerOf002String() throws Exception {
        Properties settings = new Properties();
        settings.put("solr.url", "http://localhost:9090/solr/raw-repo-index");

        SolrFBS instance = new SolrFBS(settings);
        assertThat(instance.getOwnerOf002("marc.002a:06605142"), is("20412372"));
    }

    @Test
    public void getOwnerOf002NoHits() throws Exception {
        Properties settings = new Properties();
        settings.put("solr.url", "http://localhost:9090/solr/raw-repo-index");

        SolrFBS instance = new SolrFBS(settings);
        assertThat(instance.getOwnerOf002("marc.002a:06605143"), is(""));
    }

    @Test
    public void hasDocuments() throws Exception {
        Properties settings = new Properties();
        settings.put("solr.url", "http://localhost:9090/solr/raw-repo-index");

        SolrFBS instance = new SolrFBS(settings);
        assertThat(instance.hasDocuments("marc.002a:76605141"), is(false));
        assertThat(instance.hasDocuments("marc.002a:06605141"), is(true));
    }

    @Test(expected = SolrException.class)
    public void testHits() throws Exception {

        Properties settings = new Properties();
        settings.put("solr.url", "http://localhost:9090/solr/raw-repo-index");

        SolrFBS instance = new SolrFBS(settings);
        assertThat(instance.hits("marc.002a:06605141"), equalTo(1L));
        assertThat(instance.hits("marc.002a:76605141"), equalTo(0L));

        instance.hits("marc.xxxsdas:*");
    }

    @Test(expected = SolrException.class)
    public void testHits_UnknownHost() throws Exception {
        Properties settings = new Properties();
        settings.put("solr.url", "http://localhost:19090/solr/raw-repo-index");

        SolrFBS instance = new SolrFBS(settings);
        instance.hits("marc.002a:06605141");
    }
}
