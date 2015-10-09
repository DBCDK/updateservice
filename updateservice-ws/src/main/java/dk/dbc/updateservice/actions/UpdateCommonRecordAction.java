//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------

import dk.dbc.iscrum.records.MarcReader;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.updateservice.update.*;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.Properties;

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
        this.recordsHandler = null;
        this.settings = null;
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
            UpdateSingleRecord action = null;
            bizLogger.info( "Handling record:\n{}", record );

            String parentId = MarcReader.readParentId( record );
            if( parentId != null && !parentId.isEmpty() ) {
                action = new UpdateVolumeRecord( rawRepo, record );
            }
            else {
                action = new UpdateSingleRecord( rawRepo, record );
            }

            action.setGroupId( groupId );
            action.setHoldingsItems( holdingsItems );
            action.setOpenAgencyService( openAgencyService );
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
     * Class to give access to the JavaScript engine to handle records.
     * <p>
     *      The LibraryRecordsHandler is used to check records for changes in
     *      classifications.
     * </p>
     */
    private LibraryRecordsHandler recordsHandler;
    private Properties settings;
}
