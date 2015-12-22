//-----------------------------------------------------------------------------
package dk.dbc.updateservice.update;

//-----------------------------------------------------------------------------

import dk.dbc.iscrum.utils.json.Json;
import dk.dbc.updateservice.ws.JNDIResources;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Properties;

/**
 * EJB to make lookups in a SOLR index of the rawrepo database.
 */
@Stateless
public class SolrService {
    public SolrService() {
        this( new Properties() );
    }

    public SolrService( Properties settings ) {
        this.settings = settings;
    }

    public boolean hasDocuments( String q ) throws UpdateException {
        logger.entry( q );

        Boolean result = null;
        try {
            return result = hits( q ) != 0L;
        }
        finally {
            logger.exit( result );
        }
    }

    public long hits( String q ) throws UpdateException {
        logger.entry();

        URL solrUrl = null;
        try {
            if( settings.containsKey( "solr.url" ) ) {
                String url = settings.getProperty( "solr.url" );

                solrUrl = new URL( String.format( SOLR_QUERY_URL, url, URLEncoder.encode( q ) ) );
                Map<String, Object> response = callSolr( solrUrl );

                if( response == null ) {
                    logger.warn( "Solr return a null response for query: {} -> {}", q, solrUrl );
                    return 0;
                }

                if( response.containsKey( "response" ) ) {
                    Map<String, Object> solrResponseMap = (Map<String, Object>)response.get( "response" );
                    if( solrResponseMap.containsKey( "numFound" ) ) {
                        String solrResponseNumFound = solrResponseMap.get( "numFound" ).toString();
                        return Long.valueOf( solrResponseNumFound, 10 );
                    }
                }
                else if( response.containsKey( "error" ) ) {
                    Map<String, Object> errorMap = (Map<String, Object>)response.get( "error" );

                    String code = null;
                    String message = null;

                    if( errorMap.containsKey( "code" ) ) {
                        code = errorMap.get( "code" ).toString();
                    }
                    if( errorMap.containsKey( "msg" ) ) {
                        message = errorMap.get( "msg" ).toString();
                    }

                    String s = String.format( "Solr at %s returned response code %s: %s", solrUrl, code, message );
                    logger.warn( s );

                    throw new UpdateException( s );
                }

                throw new UpdateException( String.format( "Unable to locate 'numFound' in Solr response %s", response ) );
            }

            throw new UpdateException( "The key 'solr.url' does not exist in settings" );
        }
        catch( IOException ex ) {
            if( solrUrl == null ) {
                throw new UpdateException( ex.getMessage(), ex );
            }

            throw new UpdateException( "Unable to connect to url " + solrUrl.toString() + ": " + ex.getMessage(), ex );
        }
        finally {
            logger.exit();
        }
    }

    public Map<String, Object> callSolr( URL url ) throws IOException {
        logger.entry();

        int responseCode = -1;
        Map<String, Object> result = null;
        try {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

            InputStream is;
            responseCode = conn.getResponseCode();
            if( responseCode == 200 ) {
                is = conn.getInputStream();
            }
            else {
                is = conn.getErrorStream();
            }
            BufferedReader br = new BufferedReader( new InputStreamReader( is ) );

            String response = "";
            String line;
            while ((line = br.readLine()) != null) {
                response += line;
            }

            conn.disconnect();
            logger.debug( "Solr response {} ==> {}", url.toString(), response );

            if( responseCode == 404 ) {
                throw new IOException( String.format( "Unable to access url [%s]: %s", conn.getResponseCode(), response ) );
            }

            result = Json.decode( response, Map.class );
            return result;
        }
        catch( IOException ex ) {
            logger.error( ex.getMessage(), ex );
            throw ex;
        }
        finally {
            logger.exit( result );
        }
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    /**
     * Logger instance to write entries to the log files.
     */
    private static XLogger logger = XLoggerFactory.getXLogger( SolrService.class );

    private static String SOLR_QUERY_URL = "%s/select?q=%s&wt=json";

    /**
     * Resource to lookup the product name for authentication.
     */
    @Resource( lookup = JNDIResources.SETTINGS_NAME )
    private Properties settings;
}