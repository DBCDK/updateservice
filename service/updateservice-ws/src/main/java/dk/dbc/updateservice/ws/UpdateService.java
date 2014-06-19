//-----------------------------------------------------------------------------
package dk.dbc.updateservice.ws;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcReader;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrumjs.ejb.JavaScriptException;
import dk.dbc.oss.ns.catalogingupdate.CatalogingUpdatePortType;
import dk.dbc.oss.ns.catalogingupdate.GetValidateSchemasRequest;
import dk.dbc.oss.ns.catalogingupdate.GetValidateSchemasResult;
import dk.dbc.oss.ns.catalogingupdate.UpdateOptionEnum;
import dk.dbc.oss.ns.catalogingupdate.UpdateRecordRequest;
import dk.dbc.oss.ns.catalogingupdate.UpdateRecordResult;
import dk.dbc.oss.ns.catalogingupdate.UpdateStatusEnum;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.update.Updater;
import dk.dbc.updateservice.validate.Validator;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.jws.WebService;
import javax.xml.bind.JAXBException;
import org.slf4j.MDC;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

//-----------------------------------------------------------------------------
/**
 *
 * @author stp
 */
@WebService( serviceName = "CatalogingUpdateServices", portName = "CatalogingUpdatePort", endpointInterface = "dk.dbc.oss.ns.catalogingupdate.CatalogingUpdatePortType", targetNamespace = "http://oss.dbc.dk/ns/catalogingUpdate", wsdlLocation = "META-INF/wsdl/catalogingUpdate.wsdl" )
@Stateless
public class UpdateService implements CatalogingUpdatePortType {

    @Override
    public UpdateRecordResult updateRecord( UpdateRecordRequest updateRecordRequest ) {
        try {
            MDC.put( TRACKING_ID_LOG_CONTEXT, updateRecordRequest.getTrackingId() );
            
            logger.entry( updateRecordRequest );
            
            UpdateRequestReader reader = new UpdateRequestReader( updateRecordRequest );
            UpdateResponseWriter writer = new UpdateResponseWriter();
            
            if( !validator.checkValidateSchema( reader.readValidateSchema() ) ) {
                logger.warn( "Unknown validate schema: {}", reader.readValidateSchema() );
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
                List<ValidationError> valErrors = validator.validateRecord( reader.readValidateSchema(), record );
                
                writer.setUpdateStatus( UpdateStatusEnum.VALIDATE_ONLY );
                if( !valErrors.isEmpty() ) {
                    writer.addValidateResults( valErrors );
                    writer.setUpdateStatus( UpdateStatusEnum.VALIDATION_ERROR );
                }
                else if( !updateRecordRequest.getOptions().getOption().contains( UpdateOptionEnum.VALIDATE_ONLY ) ) {
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
                        
            logger.exit( writer.getResponse() );
            return writer.getResponse();
        }
        catch( EJBException ex ) {
            logger.error( "Got EJBException: {}", ex.getCause() );
            throw ex;
        }
        catch( ClassNotFoundException | IllegalArgumentException | JAXBException | JavaScriptException | SQLException ex ) {
            logger.error( "Got exception: {}", ex );
            throw new EJBException( ex );
        }
        finally {
            MDC.remove( TRACKING_ID_LOG_CONTEXT );
        }
    }

    @Override
    public GetValidateSchemasResult getValidateSchemas( GetValidateSchemasRequest getValidateSchemasRequest ) {
        try {
            MDC.put( TRACKING_ID_LOG_CONTEXT, getValidateSchemasRequest.getTrackingId() );
            
            logger.entry( getValidateSchemasRequest );
            logger.exit();
        } 
        catch( EJBException ex ) {
            logger.error( "Got EJBException: {}", ex.getCause() );
            throw ex;
        }
        finally {
            MDC.remove( TRACKING_ID_LOG_CONTEXT );
        }
        
        throw new UnsupportedOperationException( "Not implemented yet." );
    }
    
    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private final XLogger logger = XLoggerFactory.getXLogger( this.getClass() );
    private static final String TRACKING_ID_LOG_CONTEXT = "trackingId";
    
    @EJB
    Validator validator;
    
    @EJB
    Updater updater;
}
