//-----------------------------------------------------------------------------
package dk.dbc.updateservice.ws;

//-----------------------------------------------------------------------------

import com.sun.xml.ws.developer.SchemaValidation;
import dk.dbc.iscrum.records.MarcReader;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.oss.ns.catalogingupdate.*;
import dk.dbc.oss.ns.catalogingupdate.Error;
import dk.dbc.updateservice.auth.Authenticator;
import dk.dbc.updateservice.auth.AuthenticatorException;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.update.Updater;
import dk.dbc.updateservice.validate.Validator;
import org.slf4j.MDC;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.jws.WebService;
import javax.servlet.http.HttpServletRequest;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.MessageContext;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;

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
@WebService( serviceName = "CatalogingUpdateServices", portName = "CatalogingUpdatePort", endpointInterface = "dk.dbc.oss.ns.catalogingupdate.CatalogingUpdatePortType", targetNamespace = "http://oss.dbc.dk/ns/catalogingUpdate", wsdlLocation = "META-INF/wsdl/update/catalogingUpdate.wsdl" )
@SchemaValidation
@Stateless
public class UpdateService implements CatalogingUpdatePortType {

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
        logger.entry( updateRecordRequest );
        UpdateResponseWriter writer = new UpdateResponseWriter();

        try {
            MDC.put( TRACKING_ID_LOG_CONTEXT, updateRecordRequest.getTrackingId() );

            UpdateRequestReader reader = new UpdateRequestReader( updateRecordRequest );
            logRequest( reader );

            if( !authenticator.authenticateUser( wsContext, reader.readUserId(), reader.readGroupId(), reader.readPassword() ) ) {
                writer.setError( Error.AUTHENTICATION_ERROR );
                bizLogger.info( "Could not authenticator user: {}/{}", reader.readUserId(), reader.readGroupId() );
                return writer.getResponse();
            }

            bizLogger.info( "Authenticated user: {}/{}", reader.readUserId(), reader.readGroupId() );
            try {
                if (!validator.checkValidateSchema(reader.readSchemaName())) {
                    bizLogger.warn("Unknown validate schema: {}", reader.readSchemaName());
                    writer.setUpdateStatus(UpdateStatusEnum.FAILED_INVALID_SCHEMA);

                    return writer.getResponse();
                }
            }
            catch( ScripterException ex ) {
                bizLogger.warn( "Exception doing checking validate schema: {}", findServiceException( ex ).getMessage() );
                logger.warn( "Exception doing checking validate schema: {}", ex );
                writer = convertUpdateErrorToResponse( ex, UpdateStatusEnum.FAILED_INVALID_SCHEMA );
                return writer.getResponse();
            }

            if( !reader.isRecordSchemaValid() ) {
                bizLogger.warn( "Unknown record schema: {}", updateRecordRequest.getBibliographicRecord().getRecordSchema() );
                writer.setUpdateStatus( UpdateStatusEnum.FAILED_INVALID_SCHEMA );
                return writer.getResponse();
            }

            if( !reader.isRecordPackingValid() ) {
                bizLogger.warn( "Unknown record packing: {}", updateRecordRequest.getBibliographicRecord().getRecordPacking() );
                writer.setUpdateStatus( UpdateStatusEnum.FAILED_INVALID_SCHEMA );
                return writer.getResponse();
            }

            MarcRecord record = reader.readRecord();
            List<ValidationError> authErrors;
            try {
                authErrors = authenticator.authenticateRecord( record, reader.readUserId(), reader.readGroupId() );
            }
            catch( EJBException ex ) {
                bizLogger.warn( "Exception doing authentication: {}", findServiceException( ex ).getMessage() );
                logger.warn( "Exception doing authentication: ", ex );
                writer = convertUpdateErrorToResponse( ex, UpdateStatusEnum.FAILED_VALIDATION_INTERNAL_ERROR );
                return writer.getResponse();
            }

            if( !authErrors.isEmpty() ) {
                writer.addValidateResults( authErrors );
                writer.setUpdateStatus( UpdateStatusEnum.VALIDATION_ERROR );

                bizLogger.error( "Errors with authenticating the record:" );
                for( ValidationError err : authErrors ) {
                    err.writeLog( bizLogger );
                }

                return writer.getResponse();
            }

            String recId = MarcReader.getRecordValue( record, "001", "a" );
            String libId = MarcReader.getRecordValue( record, "001", "b" );

            bizLogger.info( "Validate record [{}|{}]", recId, libId );
            List<ValidationError> valErrors;
            try {
                valErrors = validator.validateRecord( reader.readSchemaName(), record );
                if( valErrors.isEmpty() ) {
                    bizLogger.info( "Record contains no validation errors." );
                }
                else {
                    for( ValidationError err : valErrors ) {
                        err.writeLog( bizLogger );
                    }
                }
            }
            catch( EJBException ex ) {
                bizLogger.warn( "Exception doing validation: {}", ex );
                writer = convertUpdateErrorToResponse( ex, UpdateStatusEnum.FAILED_VALIDATION_INTERNAL_ERROR );
                return writer.getResponse();
            }

            writer.setUpdateStatus( UpdateStatusEnum.VALIDATE_ONLY );
            writer.addValidateResults( valErrors );
            boolean hasValErrors = false;
            for( ValidationError err: valErrors ) {
                if( err.getType() == ValidateWarningOrErrorEnum.ERROR ) {
                    hasValErrors = true;
                    break;
                }
            }
            if( hasValErrors ) {
                writer.setUpdateStatus( UpdateStatusEnum.VALIDATION_ERROR );
            }
            else {
                if( !reader.hasValidationOnlyOption() ) {
                    try {
                        writer.setUpdateStatus( UpdateStatusEnum.OK );

                        bizLogger.info( "Updating record [{}|{}]", recId, libId );
                        updater.updateRecord( record, reader.readUserId(), reader.readGroupId() );
                        bizLogger.info( "Record [{}|{}] is updated succesfully", recId, libId );
                    }
                    catch( UpdateException ex ) {
                        bizLogger.warn( "Exception doing update: {}", findServiceException( ex ).getMessage() );
                        logger.warn( "Exception doing update:", ex );
                        writer = convertUpdateErrorToResponse( ex, UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR );
                        return writer.getResponse();
                    }
                }
            }

            bizLogger.info( "Returning response." );
            return writer.getResponse();
        }
        catch( AuthenticatorException ex ) {
            bizLogger.error( "Caught Authenticator Exception: {}", findServiceException( ex ).getMessage() );
            logger.error( "Caught Authenticator Exception: {}", ex );
            writer = new UpdateResponseWriter();
            writer.setError( Error.AUTHENTICATION_ERROR );
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
            MDC.remove( TRACKING_ID_LOG_CONTEXT);
            bizLogger.exit( writer.getResponse() );
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
    private final XLogger logger = XLoggerFactory.getXLogger( this.getClass() );
    private final XLogger bizLogger = XLoggerFactory.getXLogger( BusinessLoggerFilter.LOGGER_NAME );

    /**
     * MDC constant for tackingId in the log files.
     */
    private static final String TRACKING_ID_LOG_CONTEXT = "trackingId";

    @Resource
    WebServiceContext wsContext;

    /**
     * EJB for authentication.
     */
    @EJB
    Authenticator authenticator;

    /**
     * EJB for record validation.
     */
    @EJB
    Validator validator;

    /**
     * EJB to update records in rawrepo.
     */
    @EJB
    Updater updater;
}
