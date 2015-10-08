//-----------------------------------------------------------------------------
package dk.dbc.updateservice.ws;

//-----------------------------------------------------------------------------

import com.sun.xml.ws.developer.SchemaValidation;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.updateservice.actions.ServiceEngine;
import dk.dbc.updateservice.actions.ServiceResult;
import dk.dbc.updateservice.actions.UpdateRequestAction;
import dk.dbc.updateservice.auth.Authenticator;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.service.api.*;
import dk.dbc.updateservice.update.HoldingsItems;
import dk.dbc.updateservice.update.LibraryRecordsHandler;
import dk.dbc.updateservice.update.OpenAgencyService;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.validate.Validator;
import org.slf4j.MDC;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.jws.WebService;
import javax.servlet.http.HttpServletRequest;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.MessageContext;
import java.util.*;

//-----------------------------------------------------------------------------
/**
 * Implements the Update web service.
 *
 * The web services uses 3 EJB's to implement its functionality:
 * <dl>
 *  <dt>Validator</dt>
 *  <dd>
 *      Validates a record by using an JavaScript engine. This EJB also has
 *      the responsibility to return a list of valid validation schemes.
 *  </dd>
 *  <dt>Updater</dt>
 *  <dd>
 *      Updates a record in the rawrepo.
 *  </dd>
 *  <dt>JSEngine</dt>
 *  <dd>EJB to execute JavaScript.</dd>
 * </dl>
 * Graphically it looks like this:
 *
 * <img src="doc-files/ejbs.png" alt="ejbs.png">
 *
 * @author stp
 */
@WebService( serviceName = "CatalogingUpdateServices", portName = "CatalogingUpdatePort", endpointInterface = "dk.dbc.updateservice.service.api.CatalogingUpdatePortType", targetNamespace = "http://oss.dbc.dk/ns/catalogingUpdate", wsdlLocation = "META-INF/wsdl/update/catalogingUpdate.wsdl" )
@SchemaValidation
@Stateless
public class UpdateService implements CatalogingUpdatePortType {
    //-------------------------------------------------------------------------
    //              Java EE
    //-------------------------------------------------------------------------

    /**
     * Initialization of the EJB after it has been created by the JavaEE
     * container.
     * <p>
     * It simply initialize the XLogger instance for logging.
     */
    @PostConstruct
    public void init() {
        logger.entry();
        try {
            if( recordsHandler == null ) {
                this.recordsHandler = new LibraryRecordsHandler( scripter, "updater.js" );
            }
        }
        catch( MissingResourceException ex ) {
            logger.error( "Unable to load resource", ex );
        }
        finally {
            logger.exit();
        }
    }

    //-------------------------------------------------------------------------
    //              Business logic
    //-------------------------------------------------------------------------

    /**
     * Update or validate a bibliographic record to the rawrepo.
     * <p>
     * This operation has 2 uses:
     * <ol>
     *  <li>Validation of the record only.</li>
     *  <li>Validation and update of the record</li>
     * </ol>
     * The actual operation is specified in the request by {@link Options}
     *
     * @param updateRecordRequest The request.
     *
     * @return Returns an instance of UpdateRecordResult with the status of the
     *         status and result of the update.
     *
     * @throws EJBException in the case of an error.
     */
    @Override
    public UpdateRecordResult updateRecord( UpdateRecordRequest updateRecordRequest ) {
        UUID prefixId = UUID.randomUUID();

        MDC.put( REQUEST_ID_LOG_CONTEXT, updateRecordRequest.getTrackingId() );
        MDC.put( PREFIX_ID_LOG_CONTEXT, prefixId.toString() );
        MDC.put( TRACKING_ID_LOG_CONTEXT, prefixId.toString() );

        logger.entry( updateRecordRequest );

        UpdateResponseWriter writer = new UpdateResponseWriter();
        UpdateRequestAction action = null;
        ServiceEngine engine = null;
        try {
            logger.info( "MDC: {}", MDC.getCopyOfContextMap() );
            logger.info( "Request tracking id: {}", updateRecordRequest.getTrackingId() );

            action = new UpdateRequestAction( rawRepo, updateRecordRequest, wsContext );
            action.setHoldingsItems( holdingsItems );
            action.setOpenAgencyService( openAgencyService );
            action.setRecordsHandler( recordsHandler );
            action.setAuthenticator( authenticator );
            action.setScripter( scripter );
            action.setSettings( settings );

            engine = new ServiceEngine();
            engine.setLoggerKeys( MDC.getCopyOfContextMap() );
            ServiceResult serviceResult = engine.executeAction( action );

            if( serviceResult.getServiceError() != null ) {
                writer.setError( serviceResult.getServiceError() );
            }
            else {
                writer.setUpdateStatus( serviceResult.getStatus() );
            }
            writer.addValidateEntries( serviceResult.getEntries() );

            bizLogger.info( "Returning response." );
            return writer.getResponse();
        }
        catch( EJBException ex ) {
            bizLogger.error( "Caught EJB Exception: {}", findServiceException( ex ).getMessage() );
            logger.error( "Caught EJB Exception: {}", ex );
            writer = convertUpdateErrorToResponse( ex, UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR );
            return writer.getResponse();
        }
        catch( Exception ex ) {
            bizLogger.error( "Caught javascript exception: {}", findServiceException( ex ).getMessage() );
            logger.error( "Caught javascript exception: {}", ex );
            writer = convertUpdateErrorToResponse( ex, UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR );
            return writer.getResponse();
        }
        finally {
            if( engine != null && action != null ) {
                bizLogger.info( "" );
                bizLogger.info( "Executed action:" );
                engine.printActions( action );
            }

            bizLogger.exit( writer.getResponse() );
            logger.exit( writer.getResponse() );
            MDC.clear();
        }
    }

    /**
     * WS operation to return a list of validation schemes.
     *
     * The actual lookup of validation schemes is done by the Validator EJB
     * ({@link Validator#getValidateSchemas ()})
     *
     * @param getValidateSchemasRequest The request.
     *
     * @return Returns an instance of GetValidateSchemasResult with the list of
     *         validation schemes.
     *
     * @throws EJBException In case of an error.
     */
    @Override
    public GetSchemasResult getSchemas( GetSchemasRequest getValidateSchemasRequest ) {
        try {
            MDC.put( TRACKING_ID_LOG_CONTEXT, getValidateSchemasRequest.getTrackingId() );

            logger.entry( getValidateSchemasRequest );
            List<Schema> names = validator.getValidateSchemas();

            GetSchemasResult response = new GetSchemasResult();
            response.setSchemasStatus(SchemasStatusEnum.OK);
            response.getSchema().addAll( names );

            return response;
        }
        catch( ScripterException ex ) {
            logger.error( "Caught JavaScript exception: {}", ex.getCause() );

            GetSchemasResult response = new GetSchemasResult();
            response.setSchemasStatus(SchemasStatusEnum.FAILED_INTERNAL_ERROR);

            return response;
        }
        catch( RuntimeException ex ) {
            logger.error( "Caught runtime exception: {}", ex.getCause() );
            throw ex;
        }
        finally {
            logger.exit();
            MDC.remove( TRACKING_ID_LOG_CONTEXT );
        }
    }

    private void logRequest( UpdateRequestReader reader ) {
        MessageContext mc = wsContext.getMessageContext();
        HttpServletRequest req = (HttpServletRequest)mc.get( MessageContext.SERVLET_REQUEST );

        bizLogger.info( "REQUEST:" );
        bizLogger.info( "======================================" );
        bizLogger.info( "Auth type: {}", req.getAuthType() );
        bizLogger.info( "Context path: {}", req.getContextPath() );
        bizLogger.info( "Content type: {}", req.getContentType() );
        bizLogger.info( "Content length: {}", req.getContentLengthLong() );
        bizLogger.info( "URI: {}", req.getRequestURI() );
        bizLogger.info( "Client address: {}", req.getRemoteAddr() );
        bizLogger.info( "Client host: {}", req.getRemoteHost() );
        bizLogger.info( "Client port: {}", req.getRemotePort() );
        bizLogger.info( "Headers" );
        bizLogger.info( "--------------------------------------" );
        bizLogger.info( "" );
        Enumeration<String> headerNames = req.getHeaderNames();
        while( headerNames.hasMoreElements() ) {
            String name = headerNames.nextElement();
            bizLogger.info( "{}: {}", name, req.getHeader( name ) );
        }
        bizLogger.info( "--------------------------------------" );
        bizLogger.info( "" );
        bizLogger.info( "Template name: {}", reader.readSchemaName() );
        bizLogger.info( "ValidationOnly option: {}", reader.hasValidationOnlyOption() ? "True" : "False" );
        bizLogger.info( "Request record: \n{}", reader.readRecord().toString() );
        bizLogger.info( "======================================" );
    }

    private Throwable findServiceException( Exception ex ) {
        Throwable throwable = ex;
        while( throwable != null && throwable.getClass().getPackage().getName().startsWith( "javax.ejb" ) ) {
            throwable = throwable.getCause();
        }

        return throwable;
    }

    private UpdateResponseWriter convertUpdateErrorToResponse( Exception ex, UpdateStatusEnum status ) {
        Throwable throwable = findServiceException( ex );

        UpdateResponseWriter writer = new UpdateResponseWriter();
        writer.setUpdateStatus( status );

        ValidationError valError = new ValidationError();
        HashMap<String, Object> map = new HashMap<>();
        map.put( "message", throwable.getMessage() );
        valError.setParams( map );

        writer.addValidateResults( Arrays.asList( valError ) );

        return writer;
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    /**
     * Logger instance to write entries to the log files.
     */
    private static final XLogger logger = XLoggerFactory.getXLogger( UpdateService.class );
    private static final XLogger bizLogger = XLoggerFactory.getXLogger( BusinessLoggerFilter.LOGGER_NAME );

    /**
     * MDC constant for tackingId in the log files.
     */
    public static final String REQUEST_ID_LOG_CONTEXT = "requestId";

    /**
     * MDC constant for tackingId in the log files.
     */
    public static final String PREFIX_ID_LOG_CONTEXT = "prefixId";

    /**
     * MDC constant for tackingId in the log files.
     */
    public static final String TRACKING_ID_LOG_CONTEXT = "trackingId";

    @Resource
    WebServiceContext wsContext;

    @Resource( lookup = JNDIResources.SETTINGS_NAME )
    private Properties settings;

    /**
     * EJB for authentication.
     */
    @EJB
    Authenticator authenticator;

    @EJB
    private Scripter scripter;

    @EJB
    private RawRepo rawRepo;

    @EJB
    private HoldingsItems holdingsItems;

    @EJB
    private OpenAgencyService openAgencyService;

    /**
     * EJB for record validation.
     */
    @EJB
    private Validator validator;

    private LibraryRecordsHandler recordsHandler;
}
