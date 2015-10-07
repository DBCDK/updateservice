//-----------------------------------------------------------------------------
package dk.dbc.updateservice.update;

//-----------------------------------------------------------------------------
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
import java.util.Properties;

//-----------------------------------------------------------------------------
/**
 * Singleton EJB to access the OpenAgency web service.
 */
@Singleton
@ConcurrencyManagement( ConcurrencyManagementType.BEAN )
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
            result = libraryRules.isAllowed( Integer.valueOf( agencyId, 10 ), feature );

            bizLogger.info( "Agency '{}' is allowed to use feature '{}': {}", agencyId, feature, result );
            return result;
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
