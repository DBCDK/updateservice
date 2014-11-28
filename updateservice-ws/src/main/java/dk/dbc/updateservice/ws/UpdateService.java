//-----------------------------------------------------------------------------
package dk.dbc.updateservice.ws;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcReader;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrumjs.ejb.JavaScriptException;
import dk.dbc.oss.ns.catalogingupdate.*;
import dk.dbc.oss.ns.catalogingupdate.Error;
import dk.dbc.updateservice.auth.Authenticator;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.update.Updater;
import dk.dbc.updateservice.validate.Validator;

import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.jws.WebService;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.MessageContext;

import org.slf4j.MDC;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import com.sun.xml.ws.developer.SchemaValidation;

//-----------------------------------------------------------------------------
/**
 * Implements the Update web service.
 * <p/>
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
 * <p/>
 * <img src="doc-files/ejbs.png" />
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

            logRequest();

            UpdateRequestReader reader = new UpdateRequestReader( updateRecordRequest );

            if( !authenticator.authenticateUser( wsContext, reader.readUserId(), reader.readGroupId(), reader.readPassword() ) ) {
                writer.setError( Error.AUTHENTICATION_ERROR );
                return writer.getResponse();
            }

            if( !validator.checkValidateSchema( reader.readSchemaName() ) ) {
                logger.warn( "Unknown validate schema: {}", reader.readSchemaName() );
                writer.setUpdateStatus( UpdateStatusEnum.FAILED_INVALID_SCHEMA );
            }
            else if( !reader.isRecordSchemaValid() ) {
                logger.warn( "Unknown record schema: {}", updateRecordRequest.getBibliographicRecord().getRecordSchema() );
                writer.setUpdateStatus( UpdateStatusEnum.FAILED_INVALID_SCHEMA );
            }
            else if( !reader.isRecordPackingValid() ) {
                logger.warn( "Unknown record packing: {}", updateRecordRequest.getBibliographicRecord().getRecordPacking() );
                writer.setUpdateStatus( UpdateStatusEnum.FAILED_INVALID_SCHEMA );
            }
            else {
                MarcRecord record = reader.readRecord();
                String recId = MarcReader.getRecordValue( record, "001", "a" );
                String libId = MarcReader.getRecordValue( record, "001", "b" );

                logger.info( "Validate record [{}|{}]", recId, libId );
                List<ValidationError> valErrors;
                try {
                    valErrors = validator.validateRecord( reader.readSchemaName(), record );
                }
                catch( EJBException ex ) {
                    logger.warn( "Exception doing validation: {}", ex );
                    writer = convertUpdateErrorToResponse( ex, UpdateStatusEnum.FAILED_VALIDATION_INTERNAL_ERROR );
                    return writer.getResponse();
                }

                writer.setUpdateStatus( UpdateStatusEnum.VALIDATE_ONLY );
                if( !valErrors.isEmpty() ) {
                    writer.addValidateResults( valErrors );
                    writer.setUpdateStatus( UpdateStatusEnum.VALIDATION_ERROR );
                }
                else {
                    Options options = updateRecordRequest.getOptions();
                    boolean doUpdate = true;

                    if( options != null && options.getOption() != null ) {
                        if( options.getOption().contains( UpdateOptionEnum.VALIDATE_ONLY ) ) {
                            doUpdate = false;
                        }
                    }

                    if( doUpdate ) {
                        try {
                            writer.setUpdateStatus( UpdateStatusEnum.OK );

                            logger.info( "Updating record [{}|{}]", recId, libId );
                            updater.updateRecord( record );
                        }
                        catch( UpdateException ex ) {
                            logger.warn( "Exception doing update: {}", ex);
                            writer = convertUpdateErrorToResponse( ex, UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR );
                            return writer.getResponse();
                        }
                    }
                }
            }

            return writer.getResponse();
        }
        catch( EJBException ex ) {
            logger.error( "Caught EJB Exception: {}", ex.getCause() );
            writer = convertUpdateErrorToResponse( ex, UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR );
            return writer.getResponse();
        }
        catch( Exception ex ) {
            logger.error( "Caught javascript exception: {}", ex );
            writer = convertUpdateErrorToResponse( ex, UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR );
            return writer.getResponse();
        }
        finally {
            MDC.remove( TRACKING_ID_LOG_CONTEXT);
            logger.exit( writer.getResponse() );
        }
    }

    /**
     * WS operation to return a list of validation schemes.
     * <p>
     * The actual lookup of validation schemes is done by the Validator EJB 
     * ({@link Validator#getValidateSchemas())
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
        catch( RuntimeException ex ) {
            logger.error( "Caught runtime exception: {}", ex.getCause() );
            throw ex;
        }
        finally {
            logger.exit();
            MDC.remove( TRACKING_ID_LOG_CONTEXT );
        }
    }

    private void logRequest() {
        MessageContext mc = wsContext.getMessageContext();
        HttpServletRequest req = (HttpServletRequest)mc.get( MessageContext.SERVLET_REQUEST );

        logger.info( "REQUEST:" );
        logger.info( "======================================" );
        logger.info( "Auth type: {}", req.getAuthType() );
        logger.info( "Context path: {}", req.getContextPath() );
        logger.info( "Content type: {}", req.getContentType() );
        logger.info( "Content length: {}", req.getContentLengthLong() );
        logger.info( "URI: {}", req.getRequestURI() );
        logger.info( "Client address: {}", req.getRemoteAddr() );
        logger.info( "Client host: {}", req.getRemoteHost() );
        logger.info( "Client port: {}", req.getRemotePort() );
        logger.info( "Headers" );
        logger.info( "--------------------------------------" );
        Enumeration<String> headerNames = req.getHeaderNames();
        while( headerNames.hasMoreElements() ) {
            String name = headerNames.nextElement();
            logger.info( "{}: {}", name, req.getHeader( name ) );
        }
        logger.info( "======================================" );

    }

    private UpdateResponseWriter convertUpdateErrorToResponse( Exception ex, UpdateStatusEnum status ) {
        Throwable throwable = ex;
        while( throwable != null && throwable.getClass().getPackage().getName().startsWith( "javax.ejb" ) ) {
            throwable = throwable.getCause();
        }

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
