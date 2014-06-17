//-----------------------------------------------------------------------------
package dk.dbc.updateservice.ws;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrumjs.ejb.JavaScriptException;
import dk.dbc.oss.ns.catalogingupdate.CatalogingUpdatePortType;
import dk.dbc.oss.ns.catalogingupdate.GetValidateSchemasRequest;
import dk.dbc.oss.ns.catalogingupdate.GetValidateSchemasResult;
import dk.dbc.oss.ns.catalogingupdate.UpdateRecordRequest;
import dk.dbc.oss.ns.catalogingupdate.UpdateRecordResult;
import dk.dbc.oss.ns.catalogingupdate.UpdateStatusEnum;
import dk.dbc.updateservice.update.Updater;
import dk.dbc.updateservice.validate.Validator;
import java.sql.SQLException;
import java.util.List;
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
                writer.setUpdateStatus( UpdateStatusEnum.FAILED_INVALID_SCHEMA );
            }
            else if( !reader.isRecordSchemaValid() ) {
                writer.setUpdateStatus( UpdateStatusEnum.FAILED_INVALID_SCHEMA );                
            }
            else if( !reader.isRecordPackingValid() ) {
                writer.setUpdateStatus( UpdateStatusEnum.FAILED_INVALID_SCHEMA );                
            }
            else {
                MarcRecord record = reader.readRecord();
                List<ValidationError> valErrors = validator.validateRecord( reader.readValidateSchema(), record );
                
                if( !valErrors.isEmpty() ) {
                    writer.addValidateResults( valErrors );
                    writer.setUpdateStatus( UpdateStatusEnum.VALIDATION_ERROR );
                }
                else {
                    updater.updateRecord( record );
                    writer.setUpdateStatus( UpdateStatusEnum.OK );
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
