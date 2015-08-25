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
import dk.dbc.updateservice.ws.JNDIResources;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.Properties;

//-----------------------------------------------------------------------------
/**
 * Action to create, overwrite or delete a single record.
 */
public class UpdateSingleRecord extends AbstractRawRepoAction {
    public UpdateSingleRecord( RawRepo rawRepo, MarcRecord record ) {
        super( "UpdateSingleRecord", rawRepo, record );

        this.groupId = null;
        this.holdingsItems = null;
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
            String recordId = MarcReader.getRecordValue( record, "001", "a" );
            Integer agencyId = Integer.valueOf( MarcReader.getRecordValue( record, "001", "b" ), 10 );

            if( !rawRepo.recordExists( recordId, agencyId ) ) {
                children.add( createCreateRecordAction() );
                return ServiceResult.newOkResult();
            }

            if( MarcReader.markedForDeletion( record ) ) {
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
    protected Properties settings;
}
