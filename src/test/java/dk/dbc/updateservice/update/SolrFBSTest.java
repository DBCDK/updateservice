/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dk.dbc.updateservice.solr.SolrFBS;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Properties;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @ParameterizedTest
    @CsvSource({"marc.002a:06605141, 2 041 237 2",
            "marc.002a:06605142, 20412372",
            "marc.002a:06605143, ''",
    })
    void getOwnerOf002List(String query, String expected) throws Exception {
        Properties settings = new Properties();
        settings.put("SOLR_URL", solrUrl);

        SolrFBS instance = new SolrFBS(settings);
        assertThat(instance.getOwnerOf002(query), is(expected));
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
        assertThat(instance.hits("marc.002a:06605141"), is(1L));
        assertThat(instance.hits("marc.002a:76605141"), is(0L));

        assertThrows(SolrException.class, () -> instance.hits("marc.xxxsdas:*"));
    }

    @Test
    void testHits_UnknownHost() {
        Properties settings = new Properties();
        settings.put("SOLR_URL", "http://testHits_UnknownHost/solr/raw-repo-index");

        SolrFBS instance = new SolrFBS(settings);
        assertThrows(SolrException.class, () -> instance.hits("marc.002a:06605141"));
    }
}
