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

/**
 * Action to check that the validate scheme name from the request is a valid
 * name.
 * <p>
 *     The action is using a Scripter to call JavaScript code to validate the
 *     schema name.
 * </p>
 */
public class ValidateSchemaAction extends AbstractAction {

    private static final XLogger logger = XLoggerFactory.getXLogger( ValidateSchemaAction.class );
    private final XLogger bizLogger = XLoggerFactory.getXLogger( BusinessLoggerFilter.LOGGER_NAME );

    private String validateSchema;
    private Scripter scripter;
    private String groupId;
    private Properties settings;

    public ValidateSchemaAction( String validateSchema, Scripter scripter, Properties settings ) {
        super( "ValidateSchemaAction" );

        this.validateSchema = validateSchema;
        this.scripter = scripter;
        this.groupId = null;
        this.settings = settings;
    }

    public String getValidateSchema() {
        return validateSchema;
    }

    public Scripter getScripter() {
        return scripter;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId( String groupId ) {
        this.groupId = groupId;
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
        if( groupId == null ) {
            throw new IllegalArgumentException( "groupId must not be (null)" );
        }
        if( settings == null ) {
            throw new IllegalArgumentException( "settings must not be (null)" );
        }

        ServiceResult result = null;
        try {
            logger.debug("mvs hest checkTemplate");
            logger.debug("mvs hest checkTemplate groupId: " + groupId);
            logger.debug("mvs hest validate schema : " + validateSchema);
            logger.debug("mvs hest settings : " + settings);
            Object jsResult = scripter.callMethod( "checkTemplate", validateSchema, groupId, settings );

            logger.trace("Result from JS ({}): {}", jsResult.getClass().getName(), jsResult);

            logger.debug("mvs hest #1");
            if (jsResult instanceof Boolean) {
                Boolean validateSchemaFound = (Boolean)jsResult;
                if( validateSchemaFound ) {
                    bizLogger.info( "Validating schema '{}' successfully", validateSchema );
                    return result = ServiceResult.newOkResult();
                }

            logger.debug("mvs hest #2");
                bizLogger.error( "Validating schema '{}' failed", validateSchema );
                return result = ServiceResult.newStatusResult( UpdateStatusEnum.FAILED_INVALID_SCHEMA );
            }

            logger.debug("mvs hest #3");
            String message = String.format( "The JavaScript function %s must return a boolean value.", "checkTemplate");
            bizLogger.info( "Validating schema '{}'. Executing error: {}", validateSchema, message );
            logger.debug("mvs hest #4");
            return result = ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_INVALID_SCHEMA, message );

        }
        catch( ScripterException ex ) {
            logger.debug("mvs hest #5");
            bizLogger.info( "Validating schema '{}'. Executing error: {}", validateSchema, ex.getMessage() );
            return result = ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_INVALID_SCHEMA, ex.getMessage() );
        }
        finally {
            logger.exit( result );
        }
    }

    @Override
    public void setupMDCContext() {
    }
}
