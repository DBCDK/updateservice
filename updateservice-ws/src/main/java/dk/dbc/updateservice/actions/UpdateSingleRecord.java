//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.*;
import dk.dbc.updateservice.ws.JNDIResources;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.Properties;
import java.util.ResourceBundle;

//-----------------------------------------------------------------------------
/**
 * Action to create, overwrite or delete a single record.
 */
public class UpdateSingleRecord extends AbstractRawRepoAction {
    public UpdateSingleRecord( RawRepo rawRepo, MarcRecord record ) {
        super( "UpdateSingleRecord", rawRepo, record );

        this.groupId = null;
        this.holdingsItems = null;
        this.openAgencyService = null;
        this.solrService = null;
        this.recordsHandler = null;
        this.settings = null;

        this.messages = ResourceBundles.getBundle( this, "actions" );
    }

    public Integer getGroupId() {
        return groupId;
    }

    public void setGroupId( Integer groupId ) {
        this.groupId = groupId;
    }

    public HoldingsItems getHoldingsItems() {
        return holdingsItems;
    }

    public void setHoldingsItems( HoldingsItems holdingsItems ) {
        this.holdingsItems = holdingsItems;
    }

    public OpenAgencyService getOpenAgencyService() {
        return openAgencyService;
    }

    public void setOpenAgencyService( OpenAgencyService openAgencyService ) {
        this.openAgencyService = openAgencyService;
    }

    public SolrService getSolrService() {
        return solrService;
    }

    public void setSolrService( SolrService solrService ) {
        this.solrService = solrService;
    }

    public LibraryRecordsHandler getRecordsHandler() {
        return recordsHandler;
    }

    public void setRecordsHandler( LibraryRecordsHandler recordsHandler ) {
        this.recordsHandler = recordsHandler;
    }

    public Properties getSettings() {
        return settings;
    }

    public void setSettings( Properties settings ) {
        this.settings = settings;
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
            bizLogger.info( "Handling record:\n{}", record );

            MarcRecordReader reader = new MarcRecordReader( record );
            String recordId = reader.recordId();
            Integer agencyId = reader.agencyIdAsInteger();

            if( !rawRepo.recordExists( recordId, agencyId ) ) {
                children.add( createCreateRecordAction() );
                return ServiceResult.newOkResult();
            }

            if( reader.markedForDeletion() ) {
                String solrQuery = SolrServiceIndexer.createSubfieldQuery( "002a", recordId );
                boolean hasHoldings = !holdingsItems.getAgenciesThatHasHoldingsFor( record ).isEmpty();
                boolean has002Links = solrService.hasDocuments( solrQuery );

                if( hasHoldings && !has002Links ) {
                    String message = messages.getString( "delete.common.with.holdings.error" );
                    return ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message );
                }

                children.add( createDeleteRecordAction() );
                return ServiceResult.newOkResult();
            }

            children.add( createOverwriteRecordAction() );
            return ServiceResult.newOkResult();
        }
        finally {
            logger.exit();
        }
    }

    /**
     * Factory method to construct the ServiceAction to create a new record.
     */
    protected ServiceAction createCreateRecordAction() {
        logger.entry();

        try {
            CreateSingleRecordAction action = new CreateSingleRecordAction( rawRepo, record );
            action.setProviderId( settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ) );

            return action;
        }
        finally {
            logger.exit();
        }
    }

    /**
     * Factory method to construct the ServiceAction to overwrite an existing record.
     */
    protected ServiceAction createOverwriteRecordAction() {
        logger.entry();

        try {
            OverwriteSingleRecordAction action = new OverwriteSingleRecordAction( rawRepo, record );
            action.setGroupId( groupId );
            action.setHoldingsItems( holdingsItems );
            action.setOpenAgencyService( openAgencyService );
            action.setRecordsHandler( recordsHandler );
            action.setSettings( settings );

            return action;
        }
        finally {
            logger.exit();
        }
    }

    /**
     * Factory method to construct the ServiceAction to delete a record.
     */
    protected ServiceAction createDeleteRecordAction() {
        logger.entry();

        try {
            DeleteCommonRecordAction action = new DeleteCommonRecordAction( rawRepo, record );
            action.setRecordsHandler( recordsHandler );
            action.setHoldingsItems( holdingsItems );
            action.setProviderId( settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ) );

            return action;
        }
        finally {
            logger.exit();
        }
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final XLogger logger = XLoggerFactory.getXLogger( UpdateSingleRecord.class );
    private static final XLogger bizLogger = XLoggerFactory.getXLogger( BusinessLoggerFilter.LOGGER_NAME );

    static final String MIMETYPE = MarcXChangeMimeType.MARCXCHANGE;

    /**
     * Group id of the user.
     */
    private Integer groupId;

    /**
     * Class to give access to the holdings database.
     */
    private HoldingsItems holdingsItems;

    /**
     * Class to give access to the OpenAgency web service
     */
    private OpenAgencyService openAgencyService;

    /**
     * Class to give access to lookups for the rawrepo in solr.
     */
    private SolrService solrService;

    /**
     * Class to give access to the JavaScript engine to handle records.
     * <p>
     *      The LibraryRecordsHandler is used to check records for changes in
     *      classifications.
     * </p>
     */
    private LibraryRecordsHandler recordsHandler;
    protected Properties settings;

    private ResourceBundle messages;
}
