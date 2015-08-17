//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------

import dk.dbc.iscrum.records.MarcReader;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.updateservice.update.HoldingsItems;
import dk.dbc.updateservice.update.LibraryRecordsHandler;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

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
        this.recordsHandler = null;
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

    public LibraryRecordsHandler getRecordsHandler() {
        return recordsHandler;
    }

    public void setRecordsHandler( LibraryRecordsHandler recordsHandler ) {
        this.recordsHandler = recordsHandler;
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

            String parentId = MarcReader.readParentId( record );
            if( parentId != null && !parentId.isEmpty() ) {
                action = new UpdateVolumeRecord( rawRepo, record );
            }
            else {
                action = new UpdateSingleRecord( rawRepo, record );
            }

            action.setGroupId( groupId );
            action.setHoldingsItems( holdingsItems );
            action.setRecordsHandler( recordsHandler );

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
     * Class to give access to the JavaScript engine to handle records.
     * <p>
     *      The LibraryRecordsHandler is used to check records for changes in
     *      classifications.
     * </p>
     */
    private LibraryRecordsHandler recordsHandler;
}
