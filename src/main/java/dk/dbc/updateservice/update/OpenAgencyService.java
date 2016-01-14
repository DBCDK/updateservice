//-----------------------------------------------------------------------------
package dk.dbc.updateservice.update;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.utils.json.Json;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.openagency.client.OpenAgencyException;
import dk.dbc.openagency.client.OpenAgencyServiceFromURL;
import dk.dbc.updateservice.ws.JNDIResources;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.Singleton;
import javax.ejb.Stateless;
import java.io.IOException;
import java.util.Properties;

//-----------------------------------------------------------------------------
/**
 * EJB to access the OpenAgency web service.
 */
@Stateless
public class OpenAgencyService {
    @PostConstruct
    public void init() {
        OpenAgencyServiceFromURL.Builder builder = OpenAgencyServiceFromURL.builder();
        builder = builder.connectTimeout( CONNECT_TIMEOUT ).requestTimeout( REQUEST_TIMEOUT );

        libraryRules = builder.build( settings.getProperty( JNDIResources.OPENAGENCY_URL_KEY ) ).libraryRules();
    }

    public boolean hasFeature( String agencyId, LibraryRuleHandler.Rule feature ) throws OpenAgencyException {
        logger.entry( agencyId, feature );

        Boolean result = null;
        try {
            result = libraryRules.isAllowed( agencyId, feature );

            bizLogger.info( "Agency '{}' is allowed to use feature '{}': {}", agencyId, feature, result );
            return result;
        }
        catch( OpenAgencyException ex ) {
            bizLogger.error( "Failed to read feature from OpenAgency for ['{}':'{}']: {}", agencyId, feature, ex.getMessage() );
            try {
                if( ex.getRequest() != null ) {
                    bizLogger.error( "Request to OpenAgency:\n{}", Json.encodePretty( ex.getRequest() ) );
                }
                if( ex.getResponse() != null ) {
                    bizLogger.error( "Response from OpenAgency:\n{}", Json.encodePretty( ex.getResponse() ) );
                }
            }
            catch( IOException ioError ) {
                bizLogger.error( "Error with encoding request/response from OpenAgency: " + ioError.getMessage(), ioError );
            }

            throw ex;
        }
        finally {
            logger.exit( result );
        }
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final XLogger logger = XLoggerFactory.getXLogger( OpenAgencyService.class );
    private static final XLogger bizLogger = XLoggerFactory.getXLogger( BusinessLoggerFilter.LOGGER_NAME );
    private static final int CONNECT_TIMEOUT =  1 * 60 * 1000;
    private static final int REQUEST_TIMEOUT =  3 * 60 * 1000;

    /**
     * Resource to lookup the product name for authentication.
     */
    @Resource( lookup = JNDIResources.SETTINGS_NAME )
    private Properties settings;

    private LibraryRuleHandler libraryRules;
}
