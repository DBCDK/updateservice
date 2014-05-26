//-----------------------------------------------------------------------------
package dk.dbc.updateservice.validate;

//-----------------------------------------------------------------------------
import com.google.gson.Gson;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.iscrumjs.ejb.JSEngine;
import dk.dbc.iscrumjs.ejb.JavaScriptException;
import dk.dbc.updateservice.ws.ValidationError;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.LocalBean;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

//-----------------------------------------------------------------------------
/**
 *
 * @author stp
 */
@Stateless
@LocalBean
public class Validator {
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
                jsProvider.initialize( IOUtils.loadProperties( Validator.class.getClassLoader(),
                        ";", "dk/dbc/validateservice/ws/settings.properties",
                        "javascript/iscrum/settings.properties" ) );
            } catch ( IOException | IllegalArgumentException ex ) {
                logger.catching( XLogger.Level.WARN, ex );
            }
        }
    }
    //@}

    //-------------------------------------------------------------------------
    //              Business logic
    //-------------------------------------------------------------------------

    public boolean checkValidateSchema( String validateSchema ) throws JavaScriptException {
        logger.entry( validateSchema );
        Object jsResult;
        try {
            jsResult = jsProvider.callEntryPoint( "checkTemplate", validateSchema );
        } catch ( IllegalStateException ex ) {
            logger.error( "Error when executing JavaScript to check the validate schema.", ex);
            jsResult = false;
        }

        logger.trace( "Result from JS ({}): {}", jsResult.getClass().getName(), jsResult );

        if ( jsResult instanceof Boolean ) {
            logger.exit();
            return ( ( Boolean ) jsResult );
        }

        throw new JavaScriptException( String.format( "The JavaScript function %s must return a boolean value.", "checkTemplate" ) );
    }
    
    public List<ValidationError> validateRecord( String validateSchema, MarcRecord record ) {
        logger.entry( validateSchema, record );
        List<ValidationError> result = new ArrayList<>();

        Gson gson = new Gson();
        Object jsResult = jsProvider.callEntryPoint( "validateRecord", validateSchema, gson.toJson( record ) );
        logger.trace( "Result from JS ({}): {}", jsResult.getClass().getName(), jsResult );
        
        ValidationError[] validationErrors = gson.fromJson( jsResult.toString(), ValidationError[].class );
        result.addAll( Arrays.asList( validationErrors ) );
        logger.trace( "Number of errors: {}", result.size() );
        
        logger.exit( result );
        return result;
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

    private XLogger logger;
    
    //!\name EJB's
    //@{
    @EJB
    private JSEngine jsProvider;
    //@}
}
