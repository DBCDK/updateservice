//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------

import dk.dbc.iscrum.records.MarcReader;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.HoldingsItems;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.ResourceBundle;

//-----------------------------------------------------------------------------
/**
 * Action to creates a new volume record.
 * <p>
 *     The main difference from CreateSingleRecordAction is that we need to link
 *     the volume record with its parent.
 * </p>
 */
public class CreateVolumeRecordAction extends AbstractRawRepoAction {
    public CreateVolumeRecordAction( RawRepo rawRepo, MarcRecord record ) {
        super( "CreateVolumeRecordAction", rawRepo, record );

        this.holdingsItems = null;
        this.providerId = null;
        this.messages = ResourceBundles.getBundle( this, "actions" );
    }

    public HoldingsItems getHoldingsItems() {
        return holdingsItems;
    }

    public void setHoldingsItems( HoldingsItems holdingsItems ) {
        this.holdingsItems = holdingsItems;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId( String providerId ) {
        this.providerId = providerId;
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

        try {
            String recordId = MarcReader.getRecordValue( record, "001", "a" );
            String parentId = MarcReader.readParentId( record );
            Integer agencyId = Integer.parseInt( MarcReader.getRecordValue( record, "001", "b" ), 10 );

            if( recordId.equals( parentId ) ) {
                String message = String.format( messages.getString( "parent.point.to.itself" ), recordId, agencyId );

                bizLogger.error( "Unable to create sub actions doing to an error: {}", message );
                return ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message );
            }

            if( !rawRepo.recordExists( parentId, agencyId ) ) {
                String message = messages.getString( "reference.record.not.exist" );

                bizLogger.error( "Unable to create sub actions doing to an error: {}", message );
                return ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message );
            }

            if( !rawRepo.agenciesForRecord( record ).isEmpty() ) {
                String message = messages.getString( "create.record.with.locals" );

                bizLogger.error( "Unable to create sub actions doing to an error: {}", message );
                return ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message );
            }

            bizLogger.error( "Creating sub actions successfully" );

            children.add( StoreRecordAction.newStoreAction( rawRepo, record, MIMETYPE ) );
            children.add( new RemoveLinksAction( rawRepo, record ) );
            children.add( LinkRecordAction.newLinkParentAction( rawRepo, record ) );
            children.add( EnqueueRecordAction.newEnqueueAction( rawRepo, record, providerId, MIMETYPE ) );

            return ServiceResult.newOkResult();
        }
        finally {
            logger.exit();
        }
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final XLogger logger = XLoggerFactory.getXLogger( CreateVolumeRecordAction.class );
    private static final XLogger bizLogger = XLoggerFactory.getXLogger( BusinessLoggerFilter.LOGGER_NAME );

    static final String MIMETYPE = MarcXChangeMimeType.MARCXCHANGE;

    /**
     * Class to give access to the holdings database.
     */
    private HoldingsItems holdingsItems;
    private String providerId;

    private ResourceBundle messages;
}