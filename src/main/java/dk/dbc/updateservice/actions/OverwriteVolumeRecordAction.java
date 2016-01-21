//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.ws.JNDIResources;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ResourceBundle;

//-----------------------------------------------------------------------------
/**
 * Action to overwrite an existing volume record.
 */
public class OverwriteVolumeRecordAction extends OverwriteSingleRecordAction {
    public OverwriteVolumeRecordAction( RawRepo rawRepo, MarcRecord record ) {
        super( rawRepo, record );
        this.name = "OverwriteVolumeRecordAction";

        this.messages = ResourceBundles.getBundle( this, "actions" );
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

        ServiceResult result = ServiceResult.newOkResult();;
        try {
            bizLogger.info( "Handling record:\n{}", record );

            MarcRecordReader reader = new MarcRecordReader( record );
            String recordId = reader.recordId();
            String parentId = reader.parentId();
            Integer agencyId = reader.agencyIdAsInteger();

            if( recordId.equals( parentId ) ) {
                Integer errorAgencyId = agencyId;
                if( errorAgencyId.equals( RawRepo.RAWREPO_COMMON_LIBRARY ) ) {
                    errorAgencyId = RawRepo.COMMON_LIBRARY;
                }
                String message = String.format( messages.getString( "parent.point.to.itself" ), recordId, errorAgencyId );

                bizLogger.error( "Unable to create sub actions doing to an error: {}", message );
                return ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message );
            }

            if( !rawRepo.recordExists( parentId, agencyId ) ) {
                String message = messages.getString( "reference.record.not.exist" );

                bizLogger.error( "Unable to create sub actions doing to an error: {}", message );
                return ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message );
            }

            MarcRecord currentRecord = loadCurrentRecord();

            children.add( StoreRecordAction.newStoreAction( rawRepo, record, MIMETYPE ) );
            children.add( new RemoveLinksAction( rawRepo, record ) );
            children.add( LinkRecordAction.newLinkParentAction( rawRepo, record ) );
            children.addAll( createActionsForCreateOrUpdateEnrichments( currentRecord ) );

            result = performActionsFor002Links();
            children.add( EnqueueRecordAction.newEnqueueAction( rawRepo, record, getSettings().getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), MIMETYPE ) );

            return result;
        }
        catch( ScripterException | UnsupportedEncodingException ex ) {
            return result = ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, ex.getMessage() );
        }
        finally {
            logger.exit( result );
        }
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final XLogger logger = XLoggerFactory.getXLogger( OverwriteVolumeRecordAction.class );
    private static final XLogger bizLogger = XLoggerFactory.getXLogger( BusinessLoggerFilter.LOGGER_NAME );

    private ResourceBundle messages;
}
