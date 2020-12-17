/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dk.dbc.updateservice.solr.SolrFBS;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;

class SolrFBSTest {
    private static WireMockServer solrServer;
    private static String solrUrl;

    @BeforeAll
    public static void startSolrServer() {
        WireMockConfiguration wireMockConfiguration = wireMockConfig().
                dynamicPort().
                withRootDirectory("src/test/resources/wiremock/solr");

        solrServer = new WireMockServer(wireMockConfiguration);
        solrServer.start();
        solrUrl = String.format("http://localhost:%s/solr/raw-repo-index", solrServer.port());
    }

    @AfterAll
    public static void stopSolrServer() {
        solrServer.stop();
    }

    @Test
    void getOwnerOf002List() throws Exception {
        Properties settings = new Properties();
        settings.put("SOLR_URL", solrUrl);

        SolrFBS instance = new SolrFBS(settings);
        assertThat(instance.getOwnerOf002("marc.002a:06605141"), is("2 041 237 2"));
    }

    @Test
    void getOwnerOf002String() throws Exception {
        Properties settings = new Properties();
        settings.put("SOLR_URL", solrUrl);

        SolrFBS instance = new SolrFBS(settings);
        assertThat(instance.getOwnerOf002("marc.002a:06605142"), is("20412372"));
    }

    @Test
    void getOwnerOf002NoHits() throws Exception {
        Properties settings = new Properties();
        settings.put("SOLR_URL", solrUrl);

        SolrFBS instance = new SolrFBS(settings);
        assertThat(instance.getOwnerOf002("marc.002a:06605143"), is(""));
    }

    @Test
    void hasDocuments() throws Exception {
        Properties settings = new Properties();
        settings.put("SOLR_URL", solrUrl);

        SolrFBS instance = new SolrFBS(settings);
        assertThat(instance.hasDocuments("marc.002a:76605141"), is(false));
        assertThat(instance.hasDocuments("marc.002a:06605141"), is(true));
    }

    @Test
    void testHits() throws Exception {

        Properties settings = new Properties();
        settings.put("SOLR_URL", solrUrl);

        SolrFBS instance = new SolrFBS(settings);
        assertThat(instance.hits("marc.002a:06605141"), equalTo(1L));
        assertThat(instance.hits("marc.002a:76605141"), equalTo(0L));

        Assertions.assertThrows(SolrException.class, () -> instance.hits("marc.xxxsdas:*"));
    }

    @Test
    void testHits_UnknownHost() {
        Properties settings = new Properties();
        settings.put("SOLR_URL", "http://testHits_UnknownHost/solr/raw-repo-index");

        SolrFBS instance = new SolrFBS(settings);
        Assertions.assertThrows(SolrException.class, () -> instance.hits("marc.002a:06605141"));
    }
}
