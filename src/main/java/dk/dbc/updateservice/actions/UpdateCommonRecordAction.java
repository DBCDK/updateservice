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
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.Properties;
import java.util.ResourceBundle;

//-----------------------------------------------------------------------------
/**
 * This action is used to update a common record.
 * <p>
 *     This action does not actual update the enrichment record, but creates child
 *     actions to do the actual update. The record is checked for integrity so
 *     the data model is not violated.
 * </p>
 */
public class UpdateCommonRecordAction extends AbstractRawRepoAction {
    public UpdateCommonRecordAction( RawRepo rawRepo, MarcRecord record ) {
        super( "UpdateCommonRecord", rawRepo, record );

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
            UpdateSingleRecord action;
            bizLogger.info( "Handling record:\n{}", record );

            MarcRecordReader reader = new MarcRecordReader( record );
            if( !reader.markedForDeletion() ) {
                if( solrService.hasDocuments( SolrServiceIndexer.createSubfieldQueryDBCOnly( "002a", reader.recordId() ) ) ) {
                    String message = messages.getString( "update.record.with.002.links" );

                    bizLogger.error( "Unable to create sub actions due to an error: {}", message );
                    return ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message );
                }
            }

            String parentId = reader.parentId();
            if( parentId != null && !parentId.isEmpty() ) {
                action = new UpdateVolumeRecord( rawRepo, record );
            }
            else {
                action = new UpdateSingleRecord( rawRepo, record );
            }

            action.setGroupId( groupId );
            action.setHoldingsItems( holdingsItems );
            action.setOpenAgencyService( openAgencyService );
            action.setSolrService( solrService );
            action.setRecordsHandler( recordsHandler );
            action.setSettings( settings );

            children.add( action );
            return ServiceResult.newOkResult();
        }
        finally {
            logger.exit();
        }
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final XLogger logger = XLoggerFactory.getXLogger( UpdateCommonRecordAction.class );
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
    private Properties settings;

    private ResourceBundle messages;
}
