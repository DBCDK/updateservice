//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcReader;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.Record;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.*;
import dk.dbc.updateservice.ws.JNDIResources;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.*;

//-----------------------------------------------------------------------------
/**
 * Created by stp on 10/08/15.
 */
public class OverwriteSingleRecordAction extends AbstractRawRepoAction {
    public OverwriteSingleRecordAction( RawRepo rawRepo, MarcRecord record ) {
        super( "OverwriteSingleRecordAction", rawRepo, record );

        this.groupId = null;
        this.holdingsItems = null;
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
     * @return A list of ValidationError to be reported in the web service response.
     *
     * @throws UpdateException In case of an error.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        logger.entry();

        try {
            bizLogger.info( "Handling record:\n{}", record );

            if( !recordsHandler.hasClassificationData( record ) ) {
                return performStoreRecord();
            }

            MarcRecord currentRecord = loadCurrentRecord();
            if( !recordsHandler.hasClassificationsChanged( currentRecord, record ) ) {
                return performStoreRecord();
            }

            children.add( StoreRecordAction.newStoreAction( rawRepo, record, MIMETYPE ) );
            children.add( new RemoveLinksAction( rawRepo, record ) );
            children.addAll( createActionsForCreateOrUpdateEnrichments( currentRecord ) );
            children.add( EnqueueRecordAction.newEnqueueAction( rawRepo, record, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), MIMETYPE ) );

            return ServiceResult.newOkResult();
        }
        catch( ScripterException | UnsupportedEncodingException ex ) {
            return ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, ex.getMessage() );
        }
        finally {
            logger.exit();
        }
    }

    protected ServiceResult performStoreRecord() {
        logger.entry();

        try {
            children.add( StoreRecordAction.newStoreAction( rawRepo, record, MIMETYPE ) );
            children.add( new RemoveLinksAction( rawRepo, record ) );
            children.add( EnqueueRecordAction.newEnqueueAction( rawRepo, record, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), MIMETYPE ) );

            return ServiceResult.newOkResult();
        }
        finally {
            logger.exit();
        }

    }

    protected MarcRecord loadCurrentRecord() throws UpdateException, UnsupportedEncodingException {
        logger.entry();

        MarcRecord result = null;
        try {
            String recordId = MarcReader.getRecordValue( record, "001", "a" );
            Integer agencyId = Integer.valueOf( MarcReader.getRecordValue( record, "001", "b" ), 10 );

            Record record = rawRepo.fetchRecord( recordId, agencyId );
            return result = new RawRepoDecoder().decodeRecord( record.getContent() );
        }
        finally {
            logger.exit( result );
        }
    }

    private Set<Integer> loadLocalAgencies() throws UpdateException {
        logger.entry();

        Set<Integer> agencies = null;
        try {
            String recordId = MarcReader.getRecordValue( record, "001", "a" );

            agencies = rawRepo.agenciesForRecord( recordId );
            agencies.remove( rawRepo.RAWREPO_COMMON_LIBRARY );
            agencies.remove( rawRepo.COMMON_LIBRARY );

            return agencies;
        }
        finally {
            logger.exit( agencies );
        }
    }

    protected List<ServiceAction> createActionsForCreateOrUpdateEnrichments( MarcRecord currentRecord ) throws ScripterException, UpdateException, UnsupportedEncodingException {
        logger.entry( currentRecord );

        List<ServiceAction> result = new ArrayList<>();
        try {
            String recordId = MarcReader.getRecordValue( record, "001", "a" );
            Integer agencyId = Integer.valueOf( MarcReader.getRecordValue( record, "001", "b" ), 10 );

            if( recordsHandler.hasClassificationData( currentRecord ) && recordsHandler.hasClassificationData(record)) {
                if( recordsHandler.hasClassificationsChanged( currentRecord, record ) ) {
                    logger.info("Classifications was changed for common record [{}:{}]", recordId, agencyId);
                    Set<Integer> holdingsLibraries = holdingsItems.getAgenciesThatHasHoldingsFor( recordId );

                    RawRepoDecoder decoder = new RawRepoDecoder();
                    for (Integer id : holdingsLibraries) {
                        logger.info("Local library for record: {}", id);
                        if (rawRepo.recordExists(recordId, id)) {
                            Record extRecord = rawRepo.fetchRecord(recordId, id);
                            MarcRecord extRecordData = decoder.decodeRecord( extRecord.getContent() );
                            if (!recordsHandler.hasClassificationData(extRecordData)) {
                                logger.info( "Update classifications for extended library record: [{}:{}]", recordId, id );

                                UpdateClassificationsInEnrichmentRecordAction action = new UpdateClassificationsInEnrichmentRecordAction( rawRepo, extRecordData );
                                action.setCurrentCommonRecord( currentRecord );
                                action.setUpdatingCommonRecord( record );
                                action.setAgencyId( id );
                                action.setRecordsHandler( recordsHandler );
                                action.setProviderId( settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ) );

                                result.add( action );
                            }
                        }
                        else if( groupId.equals( id ) ) {
                                bizLogger.info( "Enrichment record is not created for record [{}:{}], because groupId equals agencyid", recordId, id );
                        }
                        else {
                            ServiceResult serviceResult = recordsHandler.shouldCreateEnrichmentRecords( settings, currentRecord, record );
                            if( serviceResult.getStatus() != UpdateStatusEnum.OK ) {
                                bizLogger.info( "Enrichment record is not created for reason: {}", serviceResult );
                            }
                            else {
                                logger.info( "Create new extended library record: [{}:{}].", recordId, id );

                                CreateEnrichmentRecordWithClassificationsAction action = new CreateEnrichmentRecordWithClassificationsAction( rawRepo, id );
                                action.setCurrentCommonRecord( currentRecord );
                                action.setUpdatingCommonRecord( record );
                                action.setRecordsHandler( recordsHandler );
                                action.setProviderId( settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ) );

                                result.add( action );
                            }
                        }
                    }
                }
            }

            return result;
        }
        finally {
            logger.exit( result );
        }
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final XLogger logger = XLoggerFactory.getXLogger( OverwriteSingleRecordAction.class );
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
