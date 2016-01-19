//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.iscrum.utils.json.Json;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.ws.MDCUtil;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.IOException;
import java.util.ResourceBundle;

//-----------------------------------------------------------------------------
/**
 * Action to check a record for double records.
 */
public class DoubleRecordCheckingAction extends AbstractAction {
    public DoubleRecordCheckingAction( MarcRecord record ) {
        super( "DoubleRecordCheckingAction" );

        this.record = record;
        this.scripter = null;
        this.messages = ResourceBundles.getBundle( this, "actions" );
    }

    public MarcRecord getRecord() {
        return record;
    }

    public void setRecord( MarcRecord record ) {
        this.record = record;
    }

    public Scripter getScripter() {
        return scripter;
    }

    public void setScripter( Scripter scripter ) {
        this.scripter = scripter;
    }

    /**
     * Performs this actions and may create any child actions.
     *
     * @return A ServiceResult to be reported in the web service response.
     *
     * @throws UpdateException In case of an error.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        logger.entry();

        ServiceResult result = null;
        try {
            bizLogger.info( "Handling record:\n{}", record );

            scripter.callMethod( ENTRY_POINT, Json.encode( record ) );

            return result = ServiceResult.newOkResult();
        }
        catch( IOException | ScripterException ex ) {
            String message = String.format( messages.getString( "internal.double.record.check.error" ), ex.getMessage() );
            logger.error( message, ex );

            return result = ServiceResult.newOkResult();
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

    private static final XLogger logger = XLoggerFactory.getXLogger( DoubleRecordCheckingAction.class );
    private final XLogger bizLogger = XLoggerFactory.getXLogger( BusinessLoggerFilter.LOGGER_NAME );

    private final String ENTRY_POINT = "checkDoubleRecord";

    /**
     * The record to check for double records.
     */
    private MarcRecord record;

    /**
     * JavaScript engine to execute the validation rules on the record.
     */
    private Scripter scripter;

    /**
     * Resource messages to use in validation or system errors.
     */
    private ResourceBundle messages;
}
