//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.update.*;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Action to update a common school record.
 */
public class UpdateSchoolCommonRecord extends AbstractRawRepoAction {
    public UpdateSchoolCommonRecord( RawRepo rawRepo, MarcRecord record ) {
        super( "UpdateSchoolCommonRecord", rawRepo, record );

        this.holdingsItems = null;
        this.recordsHandler = null;
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

    public LibraryRecordsHandler getRecordsHandler() {
        return recordsHandler;
    }

    public void setRecordsHandler( LibraryRecordsHandler recordsHandler ) {
        this.recordsHandler = recordsHandler;
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
            if( reader.markedForDeletion() ) {
                moveSchoolEnrichmentsActions( RawRepo.RAWREPO_COMMON_LIBRARY );
                updateRecordAction();
            }
            else {
                updateRecordAction();
                moveSchoolEnrichmentsActions( RawRepo.SCHOOL_COMMON_AGENCY );
            }

            return ServiceResult.newOkResult();
        }
        catch( UnsupportedEncodingException ex ) {
            logger.error( ex.getMessage(), ex );
            throw new UpdateException( ex.getMessage(), ex );
        }
        finally {
            logger.exit();
        }
    }

    private void updateRecordAction() {
        logger.entry();

        try {
            UpdateEnrichmentRecordAction action = new UpdateEnrichmentRecordAction( rawRepo, record );
            action.setRecordsHandler( recordsHandler );
            action.setHoldingsItems( holdingsItems );
            action.setSolrService( solrService );
            action.setProviderId( providerId );

            children.add( action );
        }
        finally {
            logger.exit();
        }
    }

    private void moveSchoolEnrichmentsActions( Integer target ) throws UpdateException, UnsupportedEncodingException {
        logger.entry();

        try {
            Set<Integer> agencies = rawRepo.agenciesForRecord( record );
            if( agencies == null ) {
                return;
            }

            MarcRecordReader reader = new MarcRecordReader( record );
            String recordId = reader.recordId();

            for( Integer agencyId : agencies ) {
                if( !RawRepo.isSchoolEnrichment( agencyId ) ) {
                    continue;
                }

                Record rawRepoRecord = rawRepo.fetchRecord( recordId, agencyId );
                MarcRecord enrichmentRecord = new RawRepoDecoder().decodeRecord( rawRepoRecord.getContent() );

                LinkRecordAction linkRecordAction = new LinkRecordAction( rawRepo, enrichmentRecord );
                linkRecordAction.setLinkToRecordId( new RecordId( recordId, target ) );
                children.add( linkRecordAction );

                children.add( EnqueueRecordAction.newEnqueueAction( rawRepo, enrichmentRecord, providerId, MIMETYPE ) );
            }
        }
        finally {
            logger.exit();
        }
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final XLogger logger = XLoggerFactory.getXLogger( UpdateSchoolCommonRecord.class );
    private static final XLogger bizLogger = XLoggerFactory.getXLogger( BusinessLoggerFilter.LOGGER_NAME );

    static final String MIMETYPE = MarcXChangeMimeType.ENRICHMENT;

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

    /**
     * Class to give access to lookups for the rawrepo in solr.
     */
    private SolrService solrService;

    private String providerId;

    private ResourceBundle messages;
}
