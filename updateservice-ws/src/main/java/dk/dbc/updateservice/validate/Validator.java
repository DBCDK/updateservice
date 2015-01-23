//-----------------------------------------------------------------------------
package dk.dbc.updateservice.validate;

//-----------------------------------------------------------------------------

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.oss.ns.catalogingupdate.Schema;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.ws.JNDIResources;
import dk.dbc.updateservice.ws.ValidationError;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.type.TypeFactory;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;

//-----------------------------------------------------------------------------
/**
 *
 * @author stp
 */
@Stateless
@LocalBean
public class Validator {
    public Validator() {
        this.scripter = null;
        this.settings = null;
    }

    public Validator( Scripter scripter, Properties settings ) {
        this.scripter = scripter;
        this.settings = settings;
    }

    //-------------------------------------------------------------------------
    //              Business logic
    //-------------------------------------------------------------------------

    public boolean checkValidateSchema( String validateSchema ) throws ScripterException {
        logger.entry( validateSchema );
        Object jsResult = null;

        try {
            jsResult = scripter.callMethod( ENTRY_POINT_FILENAME, "checkTemplate", validateSchema, settings );

            logger.trace("Result from JS ({}): {}", jsResult.getClass().getName(), jsResult);

            if (jsResult instanceof Boolean) {
                return ((Boolean) jsResult);
            }

            throw new ScripterException("The JavaScript function %s must return a boolean value.", "checkTemplate");
        }
        finally {
            logger.exit( jsResult );
        }
    }
    
    public List<Schema> getValidateSchemas() throws ScripterException {
        logger.entry();
        List<Schema> result = new ArrayList<>();

        try {
            ObjectMapper mapper = new ObjectMapper();
            Object jsResult = scripter.callMethod( ENTRY_POINT_FILENAME, "getValidateSchemas", settings );
            logger.trace("Result from JS ({}): {}", jsResult.getClass().getName(), jsResult);

            Schema[] names = mapper.readValue(jsResult.toString(), Schema[].class);
            result.addAll(Arrays.asList(names));
            logger.trace("Number of templates: {}", result.size());

            return result;
        }
        catch( IOException ex ) {
            throw new ScripterException( "Error when executing JavaScript function: createLibraryExtendedRecord", ex );
        }
        finally {
            logger.exit( result );
        }
    }

    public List<ValidationError> validateRecord( String validateSchema, MarcRecord record ) throws ScripterException {
        logger.entry( validateSchema, record );
        List<ValidationError> result = new ArrayList<>();

        try {
            if (settings != null) {
                for (Entry<Object, Object> prop : settings.entrySet()) {
                    logger.debug("Property: {} -> {}", prop.getKey(), prop.getValue());
                }
            }

            ObjectMapper mapper = new ObjectMapper();
            Object jsResult = scripter.callMethod( ENTRY_POINT_FILENAME, "validateRecord", validateSchema, mapper.writeValueAsString( record ), settings );
            logger.trace("Result from JS ({}): {}", jsResult.getClass().getName(), jsResult);

            List<ValidationError> validationErrors = mapper.readValue( jsResult.toString(), TypeFactory.defaultInstance().constructCollectionType( List.class, ValidationError.class ) );
            result.addAll( validationErrors );
            logger.trace("Number of errors: {}", result.size());

            return result;
        }
        catch( IOException ex ) {
            throw new ScripterException( ex.getMessage(), ex );
        }
        finally {
            logger.exit( result );
        }
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final XLogger logger = XLoggerFactory.getXLogger( Validator.class );

    private static final String ENTRY_POINT_FILENAME = "validator.js";
    
    @EJB
    private Scripter scripter;
    
    @Resource( lookup = JNDIResources.SETTINGS_NAME )
    private Properties settings; 
}
