//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.iscrum.utils.json.Json;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.ws.MDCUtil;
import dk.dbc.updateservice.ws.ValidationError;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.ResourceBundle;

//-----------------------------------------------------------------------------
/**
 * Action to validate a record.
 * <p/>
 * This action needs the following to be able to validate a record:
 * <ol>
 *     <li>The record to validate, <code>record</code></li>
 *     <li>
 *         The name of the template that contains the validation rules to check against the record,
 *         <code>schemaName</code>
 *     </li>
 *     <li>
 *         A JavaScript environment, <code>scripter</code>.
 *     </li>
 *     <li>
 *         The JavaScript logic need some settings as a set of Properties to work properly.
 *         These settings can be set thought <code>settings</code>. This class does not use these
 *         settings by itself.
 *     </li>
 * </ol>
 */
public class ValidateRecordAction extends AbstractAction {
    /**
     * Constructs an instance with a template name and a record.
     * <p/>
     * The JavaScript logic needs an JavaScript environment and a set of
     * settings to work properly. These can be set though the properties <code>scripter</code>
     * and <code>settings</code>. This constructor initialize these to null.
     *
     * @param schemaName The name of the template use validate the record with.
     * @param record     The record to validate.
     */
    public ValidateRecordAction( String schemaName, MarcRecord record, UpdateStatusEnum okStatus ) {
        super( "ValidateRecordAction" );

        this.schemaName = schemaName;
        this.record = record;
        this.okStatus = okStatus;
        this.scripter = null;
        this.settings = null;
        this.messages = ResourceBundles.getBundle( this, "actions" );
    }

    public String getSchemaName() {
        return schemaName;
    }

    public MarcRecord getRecord() {
        return record;
    }

    public UpdateStatusEnum getOkStatus() {
        return okStatus;
    }

    public Scripter getScripter() {
        return scripter;
    }

    public void setScripter( Scripter scripter ) {
        this.scripter = scripter;
    }

    public Properties getSettings() {
        return settings;
    }

    public void setSettings( Properties settings ) {
        this.settings = settings;
    }

    /**
     * Validates the record against the JavaScript logic.
     * <p/>
     * If the JavaScript logic returns any validation errors they are converted to
     * validation entries in the ServiceResult with the status
     * <code>UpdateStatusEnum.VALIDATION_ERROR</code>. If no errors are returned
     * we use the status from <code>okStatus</code>.
     * <p/>
     * Exceptions from the JavaScript logic is converted to a ServiceResult with the
     * status <code>UpdateStatusEnum.FAILED_VALIDATION_INTERNAL_ERROR</code>. The actual
     * exception message returned as a validation entry in the ServiceResult.
     *
     * @return The constructed ServiceResult.
     *
     * @throws UpdateException Never thrown.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        logger.entry();

        ServiceResult result = null;
        try {
            bizLogger.info( "Handling record:\n{}", record );

            Object jsResult = scripter.callMethod( "validateRecord", schemaName, Json.encode( record ), settings );
            logger.trace("Result from JS ({}): {}", jsResult.getClass().getName(), jsResult);

            List<ValidationError> errors = Json.decodeArray( jsResult.toString(), ValidationError.class );
            result = new ServiceResult();
            result.addEntries( errors );

            MarcRecordReader reader = new MarcRecordReader( record );
            String recordId = reader.recordId();
            String agencyId = reader.agencyId();

            if( result.hasErrors() ) {
                bizLogger.error( "Record {{}:{}} contains validation errors.", recordId, agencyId );
                result.setStatus( UpdateStatusEnum.VALIDATION_ERROR );
            }
            else {
                bizLogger.info( "Record {{}:{}} has validated successfully.", recordId, agencyId );
                result.setStatus( okStatus );
            }

            return result;
        }
        catch( IOException | ScripterException ex ) {
            String message = String.format( messages.getString( "internal.validate.record.error" ), ex.getMessage() );
            logger.error( message, ex );
            return result = ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_VALIDATION_INTERNAL_ERROR, message );
        }
        finally {
            logger.exit( result );
        }
    }

    @Override
    public void setupMDCContext() {
        MDCUtil.setupContextForRecord( record );
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final XLogger logger = XLoggerFactory.getXLogger( ValidateRecordAction.class );
    private final XLogger bizLogger = XLoggerFactory.getXLogger( BusinessLoggerFilter.LOGGER_NAME );

    /**
     * Schema name to use to validate the record.
     */
    private String schemaName;

    /**
     * The record to validate.
     */
    private MarcRecord record;

    /**
     * Status to use if the validation succeed without no errors.
     * <p/>
     * We use two different statuses for success:
     * <ul>
     *     <li>UpdateStatusEnum.VALIDATE_ONLY: If we only validates the record.</li>
     *     <li>UpdateStatusEnum.OK: If we updates the record.</li>
     * </ul>
     */
    private UpdateStatusEnum okStatus;

    /**
     * JavaScript engine to execute the validation rules on the record.
     */
    private Scripter scripter;

    /**
     * Settings that is required by the JavaScript implementation.
     */
    private Properties settings;

    /**
     * Resource messages to use in validation or system errors.
     */
    private ResourceBundle messages;
}
