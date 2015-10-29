//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.records.MarcRecordWriter;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.*;
import dk.dbc.updateservice.ws.JNDIResources;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.Properties;

//-----------------------------------------------------------------------------
/**
 * Action to move an existing enrichment to another common record.
 * <p>
 *     The only change that is maked to the enrichment record is in 001a.
 *     The rest of the record is unchanged.
 * </p>
 * <p>
 *     The action verifies that the new common record exists.
 * </p>
 * <p>
 *     The old enrichment record is deleted from the rawrepo.
 * </p>
 */
public class MoveEnrichmentRecordAction extends AbstractRawRepoAction {
    public MoveEnrichmentRecordAction( RawRepo rawRepo, MarcRecord record ) {
        super( "MoveEnrichmentRecordAction", rawRepo, record );

        this.commonRecord = null;
        this.recordsHandler = null;
        this.holdingsItems = null;
        this.settings = null;
    }

    public MarcRecord getCommonRecord() {
        return commonRecord;
    }

    public void setCommonRecord( MarcRecord commonRecord ) {
        this.commonRecord = commonRecord;
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

        ServiceResult result = null;
        try {
            bizLogger.info( "Handling record:\n{}", record );

            children.add( createDeleteEnrichmentAction() );
            children.add( createMoveEnrichmentToCommonRecordAction() );

            return result = ServiceResult.newOkResult();
        }
        finally {
            logger.exit( result );
        }
    }

    /**
     * Constructs an action to delete the enrichment record in this action.
     *
     * @return An instance of UpdateEnrichmentRecordAction
     */
    private ServiceAction createDeleteEnrichmentAction() {
        logger.entry();

        ServiceAction result = null;
        try {
            MarcRecord deleteRecord = new MarcRecord( record );

            MarcRecordReader reader = new MarcRecordReader( deleteRecord );
            String recordId = reader.recordId();
            String agencyId = reader.agencyId();

            bizLogger.info( "Create action to delete old enrichment record {{}:{}}", recordId, agencyId );

            MarcRecordWriter writer = new MarcRecordWriter( deleteRecord );
            writer.markForDeletion();

            return createUpdateRecordAction( deleteRecord );
        }
        finally {
            logger.exit( result );
        }
    }

    /**
     * Constructs an action to delete the enrichment record in this action.
     *
     * @return An instance of UpdateEnrichmentRecordAction
     */
    private ServiceAction createMoveEnrichmentToCommonRecordAction() throws UpdateException {
        logger.entry();

        ServiceAction result = null;
        try {
            String commonRecordId = new MarcRecordReader( commonRecord ).recordId();

            MarcRecord newEnrichmentRecord = new MarcRecord( record );
            MarcRecordWriter writer = new MarcRecordWriter( newEnrichmentRecord );
            writer.addOrReplaceSubfield( "001", "a", commonRecordId );

            MarcRecordReader reader = new MarcRecordReader( record );
            String recordId = reader.recordId();
            String agencyId = reader.agencyId();
            bizLogger.info( "Create action to let new enrichment record {{}:{}} point to common record {}", recordId, agencyId, commonRecordId );

            if( recordsHandler.hasClassificationData( newEnrichmentRecord ) ) {
                return createUpdateRecordAction( newEnrichmentRecord );
            }

            MarcRecord currentCommonRecord = new RawRepoDecoder().decodeRecord( rawRepo.fetchRecord( recordId, RawRepo.RAWREPO_COMMON_LIBRARY ).getContent() );

            ServiceResult currentCommonRecordShouldCreateEnrichments = recordsHandler.shouldCreateEnrichmentRecords( settings, currentCommonRecord, currentCommonRecord );
            ServiceResult newCommonRecordShouldCreateEnrichments = recordsHandler.shouldCreateEnrichmentRecords( settings, commonRecord, commonRecord );

            boolean isCurrentCommonRecordPublished = currentCommonRecordShouldCreateEnrichments.getStatus() == UpdateStatusEnum.OK;
            boolean isNewCommonRecordPublished = newCommonRecordShouldCreateEnrichments.getStatus() == UpdateStatusEnum.OK;

            if( isCurrentCommonRecordPublished || isNewCommonRecordPublished ) {
                return createUpdateRecordAndClassificationsAction( newEnrichmentRecord, currentCommonRecord );
            }

            return createUpdateRecordAction( newEnrichmentRecord );
        }
        catch( ScripterException | UnsupportedEncodingException ex ) {
            throw new UpdateException( ex.getMessage(), ex );
        }
        finally {
            logger.exit( result );
        }
    }

    /**
     * Constructs an action to update a record where classification data is
     * not updated.
     *
     * @param updateRecord The record to update.
     *
     * @return An instance of UpdateEnrichmentRecordAction
     */
    private ServiceAction createUpdateRecordAction( MarcRecord updateRecord ) {
        logger.entry();

        ServiceAction result = null;
        try {
            UpdateEnrichmentRecordAction action = new UpdateEnrichmentRecordAction( rawRepo, updateRecord );
            action.setRecordsHandler( recordsHandler );
            action.setHoldingsItems( holdingsItems );
            action.setProviderId( settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ) );

            return action;
        }
        finally {
            logger.exit( result );
        }
    }

    /**
     * Constructs an action to update a record where classification data is
     * updated.
     *
     * @param updateRecord The record to update.
     * @param commonRecord The common record to copy classifications data from.
     *
     * @return An instance of UpdateEnrichmentRecordAction
     */
    private ServiceAction createUpdateRecordAndClassificationsAction( MarcRecord updateRecord, MarcRecord commonRecord ) {
        logger.entry();

        ServiceAction result = null;
        try {
            MarcRecordReader reader = new MarcRecordReader( updateRecord );

            UpdateClassificationsInEnrichmentRecordAction action = new UpdateClassificationsInEnrichmentRecordAction( rawRepo );
            action.setCurrentCommonRecord( commonRecord );
            action.setUpdatingCommonRecord( commonRecord );
            action.setEnrichmentRecord( updateRecord );
            action.setAgencyId( reader.agencyIdAsInteger() );
            action.setRecordsHandler( recordsHandler );
            action.setProviderId( settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ) );

            return action;
        }
        finally {
            logger.exit( result );
        }
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final XLogger logger = XLoggerFactory.getXLogger( MoveEnrichmentRecordAction.class );
    private static final XLogger bizLogger = XLoggerFactory.getXLogger( BusinessLoggerFilter.LOGGER_NAME );

    private MarcRecord commonRecord;

    private LibraryRecordsHandler recordsHandler;

    private HoldingsItems holdingsItems;
    private Properties settings;
}
