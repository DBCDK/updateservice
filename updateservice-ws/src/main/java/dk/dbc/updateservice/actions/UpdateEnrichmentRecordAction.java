//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcReader;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.*;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.ResourceBundle;

//-----------------------------------------------------------------------------
/**
 * Action to update an enrichment record.
 * <p>
 *     This action does not actual update the enrichment record, but creates child
 *     actions to do the actual update. The record is checked for integrity so
 *     the data model is not violated.
 * </p>
 */
public class UpdateEnrichmentRecordAction extends AbstractRawRepoAction {
    public UpdateEnrichmentRecordAction( RawRepo rawRepo, MarcRecord record ) {
        super( "UpdateEnrichmentRecordAction", rawRepo, record );

        this.decoder = new RawRepoDecoder();
        this.recordsHandler = null;
        this.holdingsItems = null;
        this.providerId = null;

        this.messages = ResourceBundles.getBundle( this, "actions" );
    }

    public void setDecoder( RawRepoDecoder decoder ) {
        this.decoder = decoder;
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
     * Constructs child actions to update or delete the enrichment record.
     * <p>
     *     An enrichment record is updated as follows:
     *     <ol>
     *         <li>
     *             If it is marked for deletion when creates actions for
     *             deletion and return.
     *         </li>
     *         <li>
     *             Correct classification data in the enrichment record from
     *             the common record. If the record is empty then create child
     *             actions for deletion and return.
     *         </li>
     *         <li>
     *             Create child actions to save the enrichment record in rawrepo.
     *         </li>
     *     </ol>
     * </p>
     * <b>Note:</b> This implementation assumes that the common record exists in
     * rawrepo so the record being updated or deleted is an enrichment record.
     *
     * @return Service result with OK.
     *
     * @throws UpdateException In case of an error.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        logger.entry();

        try {
            if( MarcReader.markedForDeletion( record ) ) {
                return performDeletionAction();
            }

            String recordId = MarcReader.getRecordValue( record, "001", "a" );

            String parentId = MarcReader.getRecordValue( record, "014", "a" );
            if( parentId != null && !parentId.isEmpty() ) {
                String agencyId = MarcReader.getRecordValue( record, "001", "b" );
                String message = String.format( messages.getString( "enrichment.has.parent" ), recordId, agencyId );

                bizLogger.warn( "Unable to update enrichment record doing to an error: {}", message );
                return ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message );
            }

            if( !rawRepo.recordExists( recordId, RawRepo.RAWREPO_COMMON_LIBRARY ) ) {
                String message = String.format( messages.getString( "record.does.not.exist" ), recordId );

                bizLogger.warn( "Unable to update enrichment record doing to an error: {}", message );
                return ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message );
            }

            Record commonRecord = rawRepo.fetchRecord( recordId, RawRepo.RAWREPO_COMMON_LIBRARY );
            MarcRecord enrichmentRecord = recordsHandler.correctLibraryExtendedRecord( decoder.decodeRecord( commonRecord.getContent() ), record );

            bizLogger.info( "Correct content of enrichment record." );
            bizLogger.info( "Old content:\n{}", record );
            bizLogger.info( "New content:\n{}", enrichmentRecord );

            if( enrichmentRecord.getFields().isEmpty() ) {
                return performDeletionAction();
            }

            return performSaveRecord( enrichmentRecord );
        }
        catch( UnsupportedEncodingException | ScripterException ex ) {
            logger.error( "Update error: " + ex.getMessage(), ex );
            throw new UpdateException( ex.getMessage(), ex );
        }
        finally {
            logger.exit();
        }
    }

    /**
     * Creates child actions to update an enrichment record that is not
     * marked for deletion.
     * <p>
     *     Enrichment records are updated as follows:
     *     <ol>
     *         <li>Store the record.</li>
     *         <li>Link it to the common record.</li>
     *         <li>Enqueue the record.</li>
     *     </ol>
     * </p>
     *
     * @return OK.
     *
     * @throws UpdateException In case of critical errors.
     */
    private ServiceResult performSaveRecord( MarcRecord enrichmentRecord ) throws UpdateException {
        logger.entry();

        try {
            String recordId = MarcReader.getRecordValue( record, "001", "a" );

            StoreRecordAction storeRecordAction = new StoreRecordAction( rawRepo, enrichmentRecord );
            storeRecordAction.setMimetype( MIMETYPE );
            children.add( storeRecordAction );

            LinkRecordAction linkRecordAction = new LinkRecordAction( rawRepo, enrichmentRecord );
            linkRecordAction.setLinkToRecordId( new RecordId( recordId, RawRepo.RAWREPO_COMMON_LIBRARY ) );
            children.add( linkRecordAction );
            children.add( EnqueueRecordAction.newEnqueueAction( rawRepo, enrichmentRecord, providerId, MIMETYPE ) );

            return ServiceResult.newOkResult();
        }
        finally {
            logger.exit();
        }
    }

    /**
     * Creates child actions to update a record that is
     * marked for deletion.
     * <p>
     *     Records are deleted as follows:
     *     <ol>
     *         <li>Remove existing links to other records.</li>
     *         <li>Store the record and mark it as deleted.</li>
     *         <li>Enqueue the record.</li>
     *     </ol>
     * </p>
     *
     * @return OK.
     *
     * @throws UpdateException In case of critical errors.
     */
    private ServiceResult performDeletionAction() throws UpdateException {
        logger.entry();

        try {
            if( !holdingsItems.getAgenciesThatHasHoldingsFor( this.record ).isEmpty() ) {
                String message = messages.getString( "delete.enrichment.with.holdings.error" );

                bizLogger.warn( "Unable to delete enrichment record doing to an error: {}", message );
                return ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message );
            }

            bizLogger.info( "Creating sub actions to delete enrichment record successfully" );
            children.add( new RemoveLinksAction( rawRepo, record ) );

            DeleteRecordAction deleteRecordAction = new DeleteRecordAction( rawRepo, record );
            deleteRecordAction.setMimetype( MIMETYPE );
            children.add( deleteRecordAction );
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

    private static final XLogger logger = XLoggerFactory.getXLogger( UpdateEnrichmentRecordAction.class );
    private static final XLogger bizLogger = XLoggerFactory.getXLogger( BusinessLoggerFilter.LOGGER_NAME );

    static final String MIMETYPE = MarcXChangeMimeType.ENRICHMENT;

    private RawRepoDecoder decoder;
    private LibraryRecordsHandler recordsHandler;

    private HoldingsItems holdingsItems;
    private String providerId;

    private ResourceBundle messages;
}
