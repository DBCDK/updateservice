//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.ResourceBundle;

/**
 * Action to enqueue a record in rawrepo.
 * <p>
 *     When a record is updated or created in the rawrepo it needs to be
 *     enqueued so other components in the flow of services can be notified
 *     about the changed record.
 * </p>
 * <p>
 *     This action does exactly that for a single record.
 * </p>
 * <p>
 *     <strong>Note:</strong> For this to work properly the record need to has
 *     its links to other record setup correctly. If they are
 *     missing, not all records that points to this record will be enqueued
 *     correctly.
 * </p>
 */
public class EnqueueRecordAction extends AbstractRawRepoAction {
    public EnqueueRecordAction( RawRepo rawRepo, MarcRecord record ) {
        super( "EnqueueRecordAction", rawRepo, record );
        providerId = null;

        this.messages = ResourceBundles.getBundle( this, "actions" );
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId( String providerId ) {
        this.providerId = providerId;
    }

    public String getMimetype() {
        return mimetype;
    }

    public void setMimetype( String mimetype ) {
        this.mimetype = mimetype;
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

        ServiceResult result = null;
        try {
            bizLogger.info( "Handling record:\n{}", record );

            if( providerId == null ) {
                return result = ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, messages.getString( "provider.id.not.set" ) );
            }

            MarcRecordReader reader = new MarcRecordReader( record );
            String recId = reader.recordId();
            Integer agencyId = reader.agencyIdAsInteger();

            rawRepo.changedRecord( providerId, new RecordId( recId, agencyId ), this.mimetype );
            bizLogger.info( "The record {{}:{}} successfully enqueued", recId, agencyId );

            return result = ServiceResult.newOkResult();
        }
        finally {
            logger.exit( result );
        }
    }

    /**
     * Factory method to create a EnqueueRecordAction.
     */
    public static EnqueueRecordAction newEnqueueAction( RawRepo rawRepo, MarcRecord record, String providerId, String mimetype ) {
        logger.entry( rawRepo, record, mimetype );

        EnqueueRecordAction enqueueRecordAction = null;
        try {
            enqueueRecordAction = new EnqueueRecordAction( rawRepo, record );
            enqueueRecordAction.setProviderId( providerId );
            enqueueRecordAction.setMimetype( mimetype );

            return enqueueRecordAction;
        }
        finally {
            logger.exit();
        }
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final XLogger logger = XLoggerFactory.getXLogger( EnqueueRecordAction.class );
    private static final XLogger bizLogger = XLoggerFactory.getXLogger( BusinessLoggerFilter.LOGGER_NAME );

    /**
     * Rawrepo requires a provider name for the service that changes its
     * content. This constant defines the provider name for the update web
     * service.
     */
    private String providerId;

    /**
     * Mimetype for the record.
     * <p>
     *     It is required by the rawrepo when records are enqueued. It is
     *     not used by this class.
     * </p>
     */
    private String mimetype;

    private ResourceBundle messages;
}