//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.Properties;

//-----------------------------------------------------------------------------
/**
 * Action to check that the validate scheme name from the request is a valid
 * name.
 * <p>
 *     The action is using a Scripter to call JavaScript code to validate the
 *     schema name.
 * </p>
 */
public class ValidateSchemaAction extends AbstractAction {
    public ValidateSchemaAction( String validateSchema, Scripter scripter, Properties settings ) {
        super( "ValidateSchemaAction" );

        this.validateSchema = validateSchema;
        this.scripter = scripter;
        this.settings = settings;
    }

    public String getValidateSchema() {
        return validateSchema;
    }

    public Scripter getScripter() {
        return scripter;
    }

    public Properties getSettings() {
        return settings;
    }

    /**
     * Performs this actions and may create any child actions.
     *
     * @return A list of ValidationError to be reported in the web service response.
     *
     * @throws UpdateException In case of an error.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        logger.entry();

        if( validateSchema == null ) {
            throw new IllegalArgumentException( "validateSchema must not be (null)" );
        }
        if( scripter == null ) {
            throw new IllegalArgumentException( "scripter must not be (null)" );
        }
        if( settings == null ) {
            throw new IllegalArgumentException( "settings must not be (null)" );
        }

        ServiceResult result = null;
        try {
            Object jsResult = scripter.callMethod( ENTRY_POINT_FILENAME, "checkTemplate", validateSchema, settings );

            logger.trace("Result from JS ({}): {}", jsResult.getClass().getName(), jsResult);

            if (jsResult instanceof Boolean) {
                Boolean validateSchemaFound = (Boolean)jsResult;
                if( validateSchemaFound ) {
                    bizLogger.info( "Validating schema '{}' successfully", validateSchema );
                    return result = ServiceResult.newOkResult();
                }

                bizLogger.error( "Validating schema '{}' failed", validateSchema );
                return result = ServiceResult.newStatusResult( UpdateStatusEnum.FAILED_INVALID_SCHEMA );
            }

            String message = String.format( "The JavaScript function %s must return a boolean value.", "checkTemplate");
            bizLogger.info( "Validating schema '{}'. Executing error: {}", validateSchema, message );
            return result = ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_INVALID_SCHEMA, message );

        }
        catch( ScripterException ex ) {
            bizLogger.info( "Validating schema '{}'. Executing error: {}", validateSchema, ex.getMessage() );
            return result = ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_INVALID_SCHEMA, ex.getMessage() );
        }
        finally {
            logger.exit( result );
        }
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final XLogger logger = XLoggerFactory.getXLogger( ValidateSchemaAction.class );
    private final XLogger bizLogger = XLoggerFactory.getXLogger( BusinessLoggerFilter.LOGGER_NAME );

    private static final String ENTRY_POINT_FILENAME = "validator.js";

    private String validateSchema;
    private Scripter scripter;
    private Properties settings;
}
