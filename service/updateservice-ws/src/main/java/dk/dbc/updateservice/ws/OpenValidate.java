//-----------------------------------------------------------------------------
package dk.dbc.updateservice.ws;

//-----------------------------------------------------------------------------
import com.google.gson.Gson;
import dk.dbc.iscrum.records.MarcFactory;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.utils.Exceptions;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.iscrumjs.ejb.JSEngine;
import dk.dbc.iscrumjs.ejb.JavaScriptException;
import dk.dbc.oss.ns.opencataloging.datacontracts.CDTOBibliographicalRecord;
import dk.dbc.oss.ns.opencataloging.datacontracts.CDTOValidateError;
import dk.dbc.oss.ns.opencataloging.datacontracts.CDTOValidateFailureInternalError;
import dk.dbc.oss.ns.opencataloging.datacontracts.CDTOValidateFailureInvalidContentOfBibliographicalRecord;
import dk.dbc.oss.ns.opencataloging.datacontracts.CDTOValidateFailureInvalidValidateSchema;
import dk.dbc.oss.ns.opencataloging.datacontracts.CDTOValidateInstance;
import dk.dbc.oss.ns.opencataloging.datacontracts.CDTOValidateRequest;
import dk.dbc.oss.ns.opencataloging.datacontracts.CDTOValidateResult;
import dk.dbc.oss.ns.opencataloging.datacontracts.CDTOValidateSuccess;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.jws.WebService;
import javax.xml.transform.dom.DOMSource;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.w3c.dom.Node;

//-----------------------------------------------------------------------------
/**
 * @ingroup ejb-ws
 * @brief Web service entry point for Validate SOAP service.
 *
 * This class implements the SOAP operations for our web service.
 *
 * @author stp
 */
@WebService( serviceName = "CValidationServices",
        portName = "BasicHttpBinding_IValidationServices",
        endpointInterface = "dk.dbc.oss.ns.opencatalogingvalidate.IValidationServices",
        targetNamespace = "http://oss.dbc.dk/ns/openCatalogingValidate",
        wsdlLocation = "META-INF/wsdl/openCatalogingValidate.wsdl"
)
@Stateless
public class OpenValidate {

    /**
     * @brief Defines SRU constant for RecordSchema tag to accept marcXChange
     * 2.0.
     */
    public static final String RECORD_SCHEMA_MARCXCHANGE_1_1 = "info:lc/xmlns/marcxchange-v1";

    /**
     * @brief Defines SRU constant for RecordPacking tag to accept xml.
     */
    public static final String RECORD_PACKING_XML = "xml";

    //-------------------------------------------------------------------------
    //              Java EE
    //-------------------------------------------------------------------------
    
    //!\name Construction
    //@{
    @PostConstruct
    public void init() {
        logger = XLoggerFactory.getXLogger( this.getClass() );
        logger.info( "Classpath: {}", System.getProperty( "classpath" ) );

        if ( jsProvider != null ) {
            try {
                jsProvider.initialize( IOUtils.loadProperties( OpenValidate.class.getClassLoader(),
                        ";", "dk/dbc/validateservice/ws/settings.properties",
                        "javascript/iscrum/settings.properties" ) );
            } catch ( IOException | IllegalArgumentException ex ) {
                logger.catching( XLogger.Level.WARN, ex );
            }
        }
    }
    //@}

    public CDTOValidateResult validate( CDTOValidateRequest parameters ) {
        logger.entry( parameters );
        Long startTime = ( new Date() ).getTime();
        CDTOValidateResult response = new CDTOValidateResult();
        try {
            // Validate validate schema.
            if ( !checkValidateSchema( parameters.getValidateSchema() ) ) {
                logger.warn( "Wrong validate schema: {}", parameters.getValidateSchema() );
                response.setValidateInstance( new CDTOValidateFailureInvalidValidateSchema() );
                logRuntime( startTime, "validate webservice call took: " );
                logger.exit( response );
                return response;
            }

            CDTOBibliographicalRecord srcRecord = parameters.getBibliographicalRecord();
            // Validate source record schema.
            if ( !srcRecord.getRecordSchema().equals( RECORD_SCHEMA_MARCXCHANGE_1_1 ) ) {
                logger.warn( "Wrong record schema: {}", srcRecord.getRecordSchema() );
                response.setValidateInstance( new CDTOValidateFailureInvalidValidateSchema() );
                logRuntime( startTime, "validate webservice call took: " );
                logger.exit( response );
                return response;
            }

            // Validate source record packing.
            if ( !srcRecord.getRecordPacking().equals( RECORD_PACKING_XML ) ) {
                logger.warn( "Wrong record packing: {}", srcRecord.getRecordPacking() );
                response.setValidateInstance( new CDTOValidateFailureInvalidValidateSchema() );
                logRuntime( startTime, "validate webservice call took: " );
                logger.exit( response );
                return response;
            }

            List<Object> list = parameters.getBibliographicalRecord().getRecordData().getContent();

            MarcFactory marcFactory = new MarcFactory();
            List<MarcRecord> records = new ArrayList<>();
            for ( Object o : list ) {
                if ( o instanceof Node ) {
                    records.addAll( marcFactory.createFromMarcXChange( new DOMSource( ( Node ) o ) ) );
                }
            }

            CDTOValidateInstance valResponse;
            if ( records.size() == 1 ) {
                valResponse = validateRecord( parameters.getValidateSchema(), records.get( 0 ) );
            } else {
                valResponse = new CDTOValidateFailureInvalidContentOfBibliographicalRecord();
            }

            response.setValidateInstance( valResponse );

            logRuntime( startTime, "validate webservice call took: " );
            logger.exit( response );
            return response;
        } catch ( Exception ex ) {
            logger.error( "Catch exception", ex );

            CDTOValidateFailureInternalError cDTOValidateFailureInternalError = new CDTOValidateFailureInternalError();
            cDTOValidateFailureInternalError.setErrorMessage( Exceptions.getCallStack( ex ) );
            response.setValidateInstance( cDTOValidateFailureInternalError );
            logRuntime( startTime, "validate webservice call took: " );
            logger.exit( response );
            return response;
        }
    }

    private boolean checkValidateSchema( String name ) throws JavaScriptException {
        logger.entry( name );
        Object jsResult;
        try {
            jsResult = jsProvider.callEntryPoint( "checkTemplate", name );
        } catch ( Exception ex ) {
            ex.printStackTrace();
            jsResult = false;
        }

        logger.trace( "Result from JS ({}): {}", jsResult.getClass().getName(), jsResult );

        if ( jsResult instanceof Boolean ) {
            logger.exit();
            return ( ( Boolean ) jsResult );
        }

        throw new JavaScriptException( String.format( "The JavaScript function %s must return a boolean value.", "checkTemplate" ) );
    }

    private CDTOValidateInstance validateRecord( String templateName, MarcRecord record ) {
        logger.entry( record );
        Gson gson = new Gson();
        CDTOValidateInstance result = null;
        Object jsResult = jsProvider.callEntryPoint( "validateRecord", templateName, gson.toJson( record ) );

        logger.trace( "Result from JS ({}): {}", jsResult.getClass().getName(), jsResult );

        ValidationError[] validationErrors = gson.fromJson( jsResult.toString(), ValidationError[].class );
        logger.trace( "Number of errors: {}", validationErrors.length );

        if ( validationErrors != null && validationErrors.length > 0 ) {
            CDTOValidateError entries = new CDTOValidateError();
            for ( ValidationError valError : validationErrors ) {
                entries.getCDTOValidateErrorEntry().add( valError.convertToServiceError() );
            }
            result = entries;
        } else {
            result = new CDTOValidateSuccess();
        }

        logger.exit( result );
        return result;
    }

    public static String loadTemplate( String resName ) throws IOException {
        XLoggerFactory.getXLogger( OpenValidate.class ).entry( resName );
        String ret = null;
        InputStream inputStream = IOUtils.getResourceAsStream( resName );
        if ( inputStream != null ) {
            ret = IOUtils.readAll( inputStream, "UTF-8" );
        }
        XLoggerFactory.getXLogger( OpenValidate.class ).exit( ret );
        return ret;
    }

    private void logRuntime( Long startTime, String logMessage ) {
        logger.entry( startTime, logMessage );
        Long endTime = ( new Date() ).getTime();
        logger.debug( logMessage + ( endTime - startTime ) + " ms" );
        logger.exit();
    }

    //------------------------------------------------------------------------
    //              Testing
    //------------------------------------------------------------------------
    //!\name Operations used for testing
    //@{
    /**
     * @brief Test properties. Properties that are only used by JUnit to set the
     * instance of members that is injected by the Java EE Container.
     * @param logger Logger to use for this run
     */
    public void setLogger( XLogger logger ) {
        this.logger = logger;
    }

    public void setJavaScriptProvider( JSEngine serviceProvider ) {
        this.jsProvider = serviceProvider;
    }
    //@}

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------
    //!\name EJB's
    //@{
    private XLogger logger;

    @EJB
    private JSEngine jsProvider;
    //@}

}
