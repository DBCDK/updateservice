//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcReader;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcWriter;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.*;
import dk.dbc.updateservice.ws.JNDIResources;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.ResourceBundle;

//-----------------------------------------------------------------------------
/**
 * Action to delete a common record.
 * <p>
 *     It supports single and volume records.
 * </p>
 */
public class DeleteCommonRecordAction extends AbstractRawRepoAction {
    public DeleteCommonRecordAction( RawRepo rawRepo, MarcRecord record ) {
        super( "DeleteCommonRecordAction", rawRepo, record );

        this.recordsHandler = null;
        this.holdingsItems = null;
        this.providerId = null;
        this.messages = ResourceBundles.getBundle( this, "actions" );
    }

    public LibraryRecordsHandler getRecordsHandler() {
        return recordsHandler;
    }

    public void setRecordsHandler( LibraryRecordsHandler recordsHandler ) {
        this.recordsHandler = recordsHandler;
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
     * @return A ServiceResult to be reported in the web service response.
     *
     * @throws UpdateException In case of an error.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        logger.entry();

        try {
            if( !rawRepo.children( record ).isEmpty() ) {
                String message = messages.getString( "delete.record.children.error" );
                String recordId = MarcReader.getRecordValue( record, "001", "a" );

                String errorMessage = String.format( message, recordId );

                bizLogger.error( "Unable to create sub actions doing to an error: {}", errorMessage );
                return ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, errorMessage );
            }

            for( RecordId enrichmentId : rawRepo.enrichments( record ) ) {
                Record rawRepoEnrichmentRecord = rawRepo.fetchRecord( enrichmentId.getBibliographicRecordId(), enrichmentId.getAgencyId() );
                MarcRecord enrichmentRecord = new RawRepoDecoder().decodeRecord( rawRepoEnrichmentRecord.getContent() );

                MarcWriter.addOrReplaceSubfield( enrichmentRecord, "004", "r", "d" );

                UpdateEnrichmentRecordAction action = new UpdateEnrichmentRecordAction( rawRepo, enrichmentRecord );
                action.setRecordsHandler( recordsHandler );
                action.setHoldingsItems( holdingsItems );
                action.setProviderId( providerId );

                children.add( action );
            }

            bizLogger.error( "Creating sub actions successfully" );

            children.add( new RemoveLinksAction( rawRepo, record ) );
            children.add( DeleteRecordAction.newDeleteRecordAction( rawRepo, record, MIMETYPE ) );
            children.add( EnqueueRecordAction.newEnqueueAction( rawRepo, record, providerId, MIMETYPE ) );

            return ServiceResult.newOkResult();
        }
        catch( UnsupportedEncodingException ex ) {
            logger.error( ex.getMessage(), ex );
            return ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, ex.getMessage() );
        }
        finally {
            logger.exit();
        }
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final XLogger logger = XLoggerFactory.getXLogger( OverwriteSingleRecordAction.class );
    private static final XLogger bizLogger = XLoggerFactory.getXLogger( BusinessLoggerFilter.LOGGER_NAME );

    static final String MIMETYPE = MarcXChangeMimeType.MARCXCHANGE;

    private LibraryRecordsHandler recordsHandler;

    /**
     * Class to give access to the holdings database.
     */
    private HoldingsItems holdingsItems;
    private String providerId;

    private ResourceBundle messages;
}