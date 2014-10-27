//-----------------------------------------------------------------------------
package dk.dbc.updateservice.validate;

//-----------------------------------------------------------------------------
import com.google.gson.Gson;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.iscrumjs.ejb.JSEngine;
import dk.dbc.iscrumjs.ejb.JavaScriptException;
import dk.dbc.oss.ns.catalogingupdate.Schema;
import dk.dbc.updateservice.ws.JNDIResources;
import dk.dbc.updateservice.ws.ValidationError;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;

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
                        ";", "dk/dbc/updateservice/ws/settings.properties",
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
    
    public List<Schema> getValidateSchemas() {
        logger.entry();
        List<Schema> result = new ArrayList<>();

        Gson gson = new Gson();
        Object jsResult = jsProvider.callEntryPoint( "getValidateSchemas" );
        logger.trace( "Result from JS ({}): {}", jsResult.getClass().getName(), jsResult );
        
        Schema[] names = gson.fromJson( jsResult.toString(), Schema[].class );
        result.addAll( Arrays.asList( names ) );
        logger.trace( "Number of templates: {}", result.size() );
        
        logger.exit( result );
        return result;
    }

    public List<ValidationError> validateRecord( String validateSchema, MarcRecord record ) {
        logger.entry( validateSchema, record );
        List<ValidationError> result = new ArrayList<>();

        if( settings != null ) {
	        for( Entry<Object, Object> prop : settings.entrySet() ) {
	        	logger.debug( "Property: {} -> {}", prop.getKey(), prop.getValue() );
	        }
        }
        
        Gson gson = new Gson();
        Object jsResult = jsProvider.callEntryPoint( "validateRecord", validateSchema, gson.toJson( record ), settings );
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
    
    @EJB
    private JSEngine jsProvider;
    
    @Resource( lookup = JNDIResources.SETTINGS_NAME )
    private Properties settings; 
}
