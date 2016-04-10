//-----------------------------------------------------------------------------
package dk.dbc.updateservice.update;

//-----------------------------------------------------------------------------

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.ext.XLoggerFactory;
import org.slf4j.ext.XLogger;

import java.util.Properties;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

//-----------------------------------------------------------------------------
public class SolrServiceTest {
    @Before
    public void startSolrServer() {
        WireMockConfiguration wireMockConfiguration = wireMockConfig().
                port( 9090 ).
                withRootDirectory( "src/test/resources/wiremock/solr" );

        solrServer = new WireMockServer( wireMockConfiguration );
        solrServer.start();
    }

    @After
    public void stopSolrServer() {
        solrServer.stop();
    }


    @Test
    public void hasDocuments() throws Exception {
        Properties settings = new Properties();
        settings.put( "solr.url", "http://localhost:9090/solr/raw-repo-index" );

        SolrService instance = new SolrService( settings );
        assertThat( instance.hasDocuments( "marc.002a:06605141" ), is( true ) );
        assertThat( instance.hasDocuments( "marc.002a:76605141" ), is( false ) );
    }

    @Test( expected = UpdateException.class )
    public void testHits() throws Exception {
        StopWatch watch = new Log4JStopWatch();

        Properties settings = new Properties();
        settings.put( "solr.url", "http://localhost:9090/solr/raw-repo-index" );
        Thread.sleep( 5 );

        watch.stop( "properties.setup" );
        Thread.sleep( 5000 );

        SolrService instance = new SolrService( settings );
        assertThat( instance.hits( "marc.002a:06605141" ), equalTo( 1L ) );
        assertThat( instance.hits( "marc.002a:76605141" ), equalTo( 0L ) );

        instance.hits( "marc.xxxsdas:*" );
    }

    @Test( expected = UpdateException.class )
    public void testHits_UnknownHost() throws Exception {
        StopWatch watch = new Log4JStopWatch();

        Properties settings = new Properties();
        settings.put( "solr.url", "http://localhost:19090/solr/raw-repo-index" );
        Thread.sleep( 10 );

        watch.stop( "properties.setup" );
        Thread.sleep( 5000 );

        SolrService instance = new SolrService( settings );
        instance.hits( "marc.002a:06605141" );
    }

    private static final XLogger logger = XLoggerFactory.getXLogger( SolrServiceTest.class );
    WireMockServer solrServer;
}
