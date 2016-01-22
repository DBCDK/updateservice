//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.records.MarcRecordWriter;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.*;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.ResourceBundle;
import java.util.Set;

//-----------------------------------------------------------------------------
/**
 * Action to update a local record.
 * <p>
 *     This action does not actual update the local record, but creates child
 *     actions to do the actual update. The record is checked for integrity so
 *     the data model is not violated.
 * </p>
 */
public class UpdateLocalRecordAction extends AbstractRawRepoAction {
    public UpdateLocalRecordAction( RawRepo rawRepo, MarcRecord record ) {
        super( "UpdateLocalRecord", rawRepo, record );

        this.holdingsItems = null;
        this.solrService = null;
        this.providerId = null;
        this.messages = ResourceBundles.getBundle( this, "actions" );
    }

    public HoldingsItems getHoldingsItems() {
        return holdingsItems;
    }

    public void setHoldingsItems( HoldingsItems holdingsItems ) {
        this.holdingsItems = holdingsItems;
    }

    public SolrService getSolrService() {
        return solrService;
    }

    public void setSolrService( SolrService solrService ) {
        this.solrService = solrService;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId( String providerId ) {
        this.providerId = providerId;
    }

    /**
     * Creates child actions to update a local record.
     *
     * @return Status OK or FAILED_UPDATE_INTERNAL_ERROR if the record can
     *         not be updated.
     *
     * @throws UpdateException In case of critical errors.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        logger.entry();

        try {
            bizLogger.info( "Handling record:\n{}", record );

            MarcRecordReader reader = new MarcRecordReader( this.record );
            String parentId = reader.parentId();
            if( reader.markedForDeletion() ) {
                if( parentId == null ) {
                    return performSingleDeleteAction();
                }

                return performVolumeDeleteAction();
            }

            if( parentId == null ) {
                return performUpdateSingleRecordAction();
            }

            return performUpdateVolumeRecordAction( parentId );
        }
        finally {
            logger.exit();
        }
    }

    /**
     * Creates child actions to update a single record that is not
     * marked for deletion.
     * <p>
     *     Singles records are updated as follows:
     *     <ol>
     *         <li>Store the record.</li>
     *         <li>Remove existing links to other records.</li>
     *         <li>Enqueue the record.</li>
     *     </ol>
     * </p>
     *
     * @return OK.
     *
     * @throws UpdateException In case of critical errors.
     */
    private ServiceResult performUpdateSingleRecordAction() throws UpdateException {
        logger.entry();

        try {
            MarcRecordReader reader = new MarcRecordReader( record );

            if( !rawRepo.recordExists( reader.recordId(), reader.agencyIdAsInteger() ) ) {
                if( solrService.hasDocuments( SolrServiceIndexer.createSubfieldQuery( "002a", reader.recordId() ) ) ) {
                    String message = messages.getString( "update.record.with.002.links" );

                    bizLogger.error( "Unable to create sub actions doing to an error: {}", message );
                    return ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message );
                }
            }

            StoreRecordAction storeRecordAction = new StoreRecordAction( rawRepo, record );
            storeRecordAction.setMimetype( MIMETYPE );
            children.add( storeRecordAction );

            children.add( new RemoveLinksAction( rawRepo, record ) );

            EnqueueRecordAction enqueueRecordAction = new EnqueueRecordAction( rawRepo, record );
            enqueueRecordAction.setProviderId( providerId );
            enqueueRecordAction.setMimetype( MIMETYPE );
            children.add( enqueueRecordAction );

            return ServiceResult.newOkResult();
        }
        finally {
            logger.exit();
        }
    }

    /**
     * Creates child actions to update a volume record that is not
     * marked for deletion.
     * <p>
     *     Volume records are updated as follows:
     *     <ol>
     *         <li>Store the record.</li>
     *         <li>Link it to the parent record.</li>
     *         <li>Enqueue the record.</li>
     *     </ol>
     * </p>
     *
     * @return OK.
     *
     * @throws UpdateException In case of critical errors.
     */
    private ServiceResult performUpdateVolumeRecordAction( String parentId ) throws UpdateException {
        logger.entry();

        try {
            MarcRecordReader reader = new MarcRecordReader( this.record );
            String recordId = reader.recordId();
            Integer agencyId = reader.agencyIdAsInteger();

            if( recordId.equals( parentId ) ) {
                String message = String.format( messages.getString( "parent.point.to.itself" ), recordId, agencyId );
                return ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message );
            }

            if( !rawRepo.recordExists( parentId, agencyId ) ) {
                String message = String.format( messages.getString( "reference.record.not.exist" ), recordId, agencyId, parentId, agencyId );
                return ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message );
            }

            if( !rawRepo.recordExists( recordId, agencyId ) ) {
                if( solrService.hasDocuments( SolrServiceIndexer.createSubfieldQuery( "002a", reader.recordId() ) ) ) {
                    String message = messages.getString( "update.record.with.002.links" );

                    bizLogger.error( "Unable to create sub actions doing to an error: {}", message );
                    return ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message );
                }
            }

            StoreRecordAction storeRecordAction = new StoreRecordAction( rawRepo, record );
            storeRecordAction.setMimetype( MIMETYPE );
            children.add( storeRecordAction );

            LinkRecordAction linkRecordAction = new LinkRecordAction( rawRepo, record );
            linkRecordAction.setLinkToRecordId( new RecordId( parentId, agencyId ) );
            children.add( linkRecordAction );

            EnqueueRecordAction enqueueRecordAction = new EnqueueRecordAction( rawRepo, record );
            enqueueRecordAction.setProviderId( providerId );
            enqueueRecordAction.setMimetype( MIMETYPE );
            children.add( enqueueRecordAction );

            return ServiceResult.newOkResult();
        }
        finally {
            logger.exit();
        }
    }

    /**
     * Creates child actions to update a single record that is
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
    private ServiceResult performSingleDeleteAction() throws UpdateException {
        logger.entry();

        try {
            if( !rawRepo.children( record ).isEmpty() ) {
                MarcRecordReader reader = new MarcRecordReader( this.record );
                String recordId = reader.recordId();
                String message = String.format( messages.getString( "delete.record.children.error" ), recordId );

                return ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message );
            }

            if( !holdingsItems.getAgenciesThatHasHoldingsFor( this.record ).isEmpty() ) {
                String message = messages.getString( "delete.local.with.holdings.error" );
                return ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message );
            }

            children.add( new RemoveLinksAction( rawRepo, record ) );

            DeleteRecordAction deleteRecordAction = new DeleteRecordAction( rawRepo, record );
            deleteRecordAction.setMimetype( MIMETYPE );
            children.add( deleteRecordAction );

            EnqueueRecordAction enqueueRecordAction = new EnqueueRecordAction( rawRepo, record );
            enqueueRecordAction.setProviderId( providerId );
            enqueueRecordAction.setMimetype( MIMETYPE );
            children.add( enqueueRecordAction );

            return ServiceResult.newOkResult();
        }
        finally {
            logger.exit();
        }
    }

    private ServiceResult performVolumeDeleteAction() throws UpdateException {
        logger.entry();

        ServiceResult result = null;
        try {
            result = performSingleDeleteAction();
            if( result.getStatus() != UpdateStatusEnum.OK ) {
                return result;
            }

            MarcRecordReader reader = new MarcRecordReader( this.record );
            String parentId = reader.parentId();
            Integer agencyId = reader.agencyIdAsInteger();

            Set<RecordId> children = rawRepo.children( new RecordId( parentId, agencyId ) );
            if( children.size() != 1 ) {
                return result = ServiceResult.newOkResult();
            }

            MarcRecord mainRecord = new RawRepoDecoder().decodeRecord( rawRepo.fetchRecord( parentId, agencyId ).getContent() );
            MarcRecordWriter writer = new MarcRecordWriter( mainRecord );
            writer.markForDeletion();

            UpdateLocalRecordAction action = new UpdateLocalRecordAction( rawRepo, mainRecord );
            action.setHoldingsItems( holdingsItems );
            action.setSolrService( solrService );
            action.setProviderId( providerId );
            this.children.add( action );

            return ServiceResult.newOkResult();
        }
        catch( UnsupportedEncodingException ex ) {
            throw new UpdateException( ex.getMessage(), ex );
        }
        finally {
            logger.exit( result );
        }
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final XLogger logger = XLoggerFactory.getXLogger( UpdateLocalRecordAction.class );
    private static final XLogger bizLogger = XLoggerFactory.getXLogger( BusinessLoggerFilter.LOGGER_NAME );

    static final String MIMETYPE = MarcXChangeMimeType.MARCXCHANGE;

    private HoldingsItems holdingsItems;

    /**
     * Class to give access to lookups for the rawrepo in solr.
     */
    private SolrService solrService;

    private String providerId;

    private ResourceBundle messages;
}
