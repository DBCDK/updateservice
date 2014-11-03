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

import java.util.List;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.jws.WebService;

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
        try {
            MDC.put( TRACKING_ID_LOG_CONTEXT, updateRecordRequest.getTrackingId() );

            logger.entry( updateRecordRequest );

            UpdateRequestReader reader = new UpdateRequestReader( updateRecordRequest );
            UpdateResponseWriter writer = new UpdateResponseWriter();

            if( !authenticator.authenticateUser( reader.readUserId(), reader.readGroupId(), reader.readPassword() ) ) {
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
                List<ValidationError> valErrors = validator.validateRecord( reader.readSchemaName(), record );

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
                            writer.setUpdateStatus( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR );
                            logger.error( "Update error: {}", ex  );
                        }
                    }
                }
            }

            logger.exit( writer.getResponse() );
            return writer.getResponse();
        }
        catch( EJBException ex ) {
            logger.error( "Catched EJB exception: {}", ex.getCause() );
            throw ex;
        }
        catch( JavaScriptException ex ) {
            logger.error( "Catched javascript exception: {}", ex );
            throw new EJBException( ex );
        }
        catch( RuntimeException ex ) {
            logger.error( "Catched runtime exception: {}", ex );
            throw new EJBException( ex );
        }
        finally {
            MDC.remove( TRACKING_ID_LOG_CONTEXT );
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
            response.setSchemasStatus( SchemasStatusEnum.OK );
            response.getSchema().addAll( names );
                        
            return response;
        }
        catch( RuntimeException ex ) {
            logger.error( "Catched runtime exception: {}", ex.getCause() );
            throw ex;
        }
        finally {
            logger.exit();
            MDC.remove( TRACKING_ID_LOG_CONTEXT );
        }
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
