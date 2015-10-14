//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcReader;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcWriter;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.update.*;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.UnsupportedEncodingException;

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
        this.providerId = null;
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

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId( String providerId ) {
        this.providerId = providerId;
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

            String recordId = MarcReader.getRecordValue( record, "001", "a" );
            String agencyId = MarcReader.getRecordValue( record, "001", "b" );

            String commonRecordId = MarcReader.getRecordValue( commonRecord, "001", "a" );
            MarcRecord newEnrichmentRecord = new MarcRecord( record );
            MarcWriter.addOrReplaceSubfield( newEnrichmentRecord, "001", "a", commonRecordId );

            UpdateEnrichmentRecordAction action;

            bizLogger.info( "Create action to delete old enrichment record {{}:{}}", recordId, agencyId );
            MarcWriter.addOrReplaceSubfield( record, "004", "r", "d" );
            action = new UpdateEnrichmentRecordAction( rawRepo, record );
            action.setRecordsHandler( recordsHandler );
            action.setHoldingsItems( holdingsItems );
            action.setProviderId( providerId );
            children.add( action );

            bizLogger.info( "Create action to let new enrichment record {{}:{}} point to common record {}", recordId, agencyId, commonRecordId );

            if( recordsHandler.hasClassificationData( newEnrichmentRecord ) ) {
                action = new UpdateEnrichmentRecordAction( rawRepo, newEnrichmentRecord );
                action.setRecordsHandler( recordsHandler );
                action.setHoldingsItems( holdingsItems );
                action.setProviderId( providerId );
                children.add( action );
            }
            else {
                MarcRecord currentCommonRecord = new RawRepoDecoder().decodeRecord( rawRepo.fetchRecord( recordId, RawRepo.RAWREPO_COMMON_LIBRARY ).getContent() );

                UpdateClassificationsInEnrichmentRecordAction updateClassificationsInEnrichmentRecordAction = new UpdateClassificationsInEnrichmentRecordAction( rawRepo );
                updateClassificationsInEnrichmentRecordAction.setCurrentCommonRecord( currentCommonRecord );
                updateClassificationsInEnrichmentRecordAction.setUpdatingCommonRecord( currentCommonRecord );
                updateClassificationsInEnrichmentRecordAction.setEnrichmentRecord( newEnrichmentRecord );
                updateClassificationsInEnrichmentRecordAction.setAgencyId( Integer.valueOf( MarcReader.getRecordValue( newEnrichmentRecord, "001", "b" ), 10 ) );
                updateClassificationsInEnrichmentRecordAction.setRecordsHandler( recordsHandler );
                updateClassificationsInEnrichmentRecordAction.setProviderId( providerId );
                children.add( updateClassificationsInEnrichmentRecordAction );
            }

            return result = ServiceResult.newOkResult();
        }
        catch( ScripterException | UnsupportedEncodingException ex ) {
            throw new UpdateException( ex.getMessage(), ex );
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
    private String providerId;
}
