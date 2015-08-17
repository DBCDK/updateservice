//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------

import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.updateservice.auth.Authenticator;
import dk.dbc.updateservice.auth.AuthenticatorException;
import dk.dbc.updateservice.service.api.Authentication;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.xml.ws.WebServiceContext;
import java.util.ResourceBundle;

//-----------------------------------------------------------------------------
/**
 * Action to authenticate the user from the request.
 * <p>
 *     The authentication is done against the forsright webservice by the parsed
 *     Authenticator EJB.
 * </p>
 */
public class AuthenticateUserAction extends AbstractAction {
    public AuthenticateUserAction( Authenticator authenticator, Authentication authentication, WebServiceContext wsContext ) {
        super( ACTION_NAME );

        this.authenticator = authenticator;
        this.authentication = authentication;
        this.wsContext = wsContext;

        this.messages = ResourceBundles.getBundle( this, "actions" );
    }

    public Authenticator getAuthenticator() {
        return authenticator;
    }

    public Authentication getAuthentication() {
        return authentication;
    }

    public WebServiceContext getWsContext() {
        return wsContext;
    }

    /**
     * Performs this actions and may create any child actions.
     *
     * @return A list of ValidationError to be reported in the web service response.
     *
     * @throws UpdateException In case of an error.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        logger.entry();

        ServiceResult result = new ServiceResult();

        try {
            if( authenticator == null ) {
                throw new IllegalArgumentException( "authenticator er obligatorisk" );
            }

            if( authentication == null ) {
                bizLogger.error( "Authentication arguments is missing in the request." );
                return result = ServiceResult.newAuthErrorResult();
            }

            if( authentication.getUserIdAut() == null ) {
                bizLogger.error( "User name is missing in authentication arguments in the request" );

                return result = ServiceResult.newAuthErrorResult();
            }

            if( authentication.getGroupIdAut() == null ) {
                bizLogger.error( "Group name is missing in authentication arguments in the request" );

                return result = ServiceResult.newAuthErrorResult();
            }

            if( authentication.getPasswordAut() == null ) {
                bizLogger.error( "Password is missing in authentication arguments in the request" );

                return result = ServiceResult.newAuthErrorResult();
            }

            if( authenticator.authenticateUser( wsContext, authentication.getUserIdAut(), authentication.getGroupIdAut(), authentication.getPasswordAut() ) ) {
                bizLogger.info( "User {}/{} is authenticated successfully", authentication.getGroupIdAut(), authentication.getUserIdAut() );
                return result = ServiceResult.newOkResult();
            }

            bizLogger.error( "User {}/{} could not be authenticated", authentication.getGroupIdAut(), authentication.getUserIdAut() );
            return result = ServiceResult.newAuthErrorResult();
        }
        catch( AuthenticatorException ex ) {
            String message = String.format( messages.getString( "authentication.error" ), ex.getMessage() );
            logger.error( message, ex );
            bizLogger.error( "Critical error in authenticating user {}/{}: {}", authentication.getGroupIdAut(), authentication.getUserIdAut(), ex.getMessage() );

            return result = ServiceResult.newAuthErrorResult();
        }
        finally {
            logger.exit( result );
        }
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final XLogger logger = XLoggerFactory.getXLogger( AuthenticateUserAction.class );
    private final XLogger bizLogger = XLoggerFactory.getXLogger( BusinessLoggerFilter.LOGGER_NAME );
    private static final String ACTION_NAME = "AuthenticateUser";

    private Authenticator authenticator;
    private Authentication authentication;
    private WebServiceContext wsContext;

    private ResourceBundle messages;
}
