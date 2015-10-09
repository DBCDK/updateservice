//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcReader;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.openagency.client.OpenAgencyException;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
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
        this.openAgencyService = null;
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

        ServiceResult result = ServiceResult.newOkResult();;
        try {
            bizLogger.info( "Handling record:\n{}", record );

            MarcRecord currentRecord = loadCurrentRecord();

            children.add( StoreRecordAction.newStoreAction( rawRepo, record, MIMETYPE ) );
            children.add( new RemoveLinksAction( rawRepo, record ) );
            children.addAll( createActionsForCreateOrUpdateEnrichments( currentRecord ) );

            result = performActionsFor002Links( currentRecord );
            children.add( EnqueueRecordAction.newEnqueueAction( rawRepo, record, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ), MIMETYPE ) );

            return result;
        }
        catch( ScripterException | UnsupportedEncodingException ex ) {
            return ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, ex.getMessage() );
        }
        finally {
            logger.exit( result );
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

                        if( !openAgencyService.hasFeature( id.toString(), LibraryRuleHandler.Rule.USE_ENRICHMENTS ) ) {
                            continue;
                        }

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
        catch( OpenAgencyException ex ) {
            throw new UpdateException( ex.getMessage(), ex );
        }
        finally {
            logger.exit( result );
        }
    }

    protected ServiceResult performActionsFor002Links( MarcRecord currentRecord ) throws ScripterException, UpdateException, UnsupportedEncodingException {
        logger.entry( currentRecord );

        ServiceResult result = ServiceResult.newOkResult();
        try {
            boolean classificationsChanged = recordsHandler.hasClassificationsChanged( currentRecord, record );
            MarcRecordReader currentRecordReader = new MarcRecordReader( currentRecord );

            for( String recordId : new MarcRecordReader( record ).getValues( "002", "a" ) ) {
                if( currentRecordReader.hasValue( "002", "a", recordId ) ) {
                    continue;
                }

                if( !rawRepo.recordExists( recordId, RawRepo.RAWREPO_COMMON_LIBRARY ) ) {
                    String message = String.format( messages.getString( "record.does.not.exist" ), recordId );
                    return result = ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message );
                }

                if( classificationsChanged ) {
                    Set<Integer> holdingAgencies = holdingsItems.getAgenciesThatHasHoldingsFor( recordId );
                    Set<RecordId> enrichmentIds = rawRepo.enrichments( new RecordId( recordId, RawRepo.RAWREPO_COMMON_LIBRARY ) );

                    for( Integer holdingAgencyId : holdingAgencies ) {
                        if( !openAgencyService.hasFeature( holdingAgencyId.toString(), LibraryRuleHandler.Rule.USE_ENRICHMENTS ) ) {
                            continue;
                        }

                        if( !enrichmentIds.contains( new RecordId( recordId, holdingAgencyId ) ) ) {
                            CreateEnrichmentRecordWithClassificationsAction action = new CreateEnrichmentRecordWithClassificationsAction( rawRepo, holdingAgencyId );
                            action.setUpdatingCommonRecord( record );
                            action.setCurrentCommonRecord( currentRecord );
                            action.setRecordsHandler( recordsHandler );
                            action.setProviderId( settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ) );

                            children.add( action );
                        }
                    }
                }

                Set<RecordId> enrichmentIds = rawRepo.enrichments( new RecordId( recordId, RawRepo.RAWREPO_COMMON_LIBRARY ) );
                for( RecordId enrichmentId : enrichmentIds ) {
                    if( enrichmentId.getAgencyId() == RawRepo.COMMON_LIBRARY ) {
                        continue;
                    }
                    if( !openAgencyService.hasFeature( String.valueOf( enrichmentId.getAgencyId() ), LibraryRuleHandler.Rule.USE_ENRICHMENTS ) ) {
                        continue;
                    }

                    Record enrichmentRecord = rawRepo.fetchRecord( enrichmentId.getBibliographicRecordId(), enrichmentId.getAgencyId() );
                    MarcRecord enrichmentRecordData = new RawRepoDecoder().decodeRecord( enrichmentRecord.getContent() );

                    MoveEnrichmentRecordAction action = new MoveEnrichmentRecordAction( rawRepo, enrichmentRecordData );
                    action.setCurrentCommonRecord( currentRecord );
                    action.setUpdatingCommonRecord( record );
                    action.setRecordsHandler( recordsHandler );
                    action.setHoldingsItems( holdingsItems );
                    action.setProviderId( settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ) );

                    children.add( action );
                }
            }

            return result;
        }
        catch( OpenAgencyException ex ) {
            throw new UpdateException( ex.getMessage(), ex );
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

    private ResourceBundle messages;
}
