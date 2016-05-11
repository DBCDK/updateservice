//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.SolrService;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.ResourceBundle;

//-----------------------------------------------------------------------------
/**
 * This action is used to create a new common record.
 */
public class CreateSingleRecordAction extends AbstractRawRepoAction {
    public CreateSingleRecordAction( RawRepo rawRepo, MarcRecord record ) {
        super( "CreateSingleRecordAction", rawRepo, record );

        this.solrService = null;

        this.messages = ResourceBundles.getBundle( this, "actions" );
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
     * @return A list of ValidationError to be reported in the web service response.
     *
     * @throws UpdateException In case of an error.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        logger.entry();

        try {
            bizLogger.info( "Handling record:\n{}", record );

            if( !rawRepo.agenciesForRecord( record ).isEmpty() ) {
                String message = messages.getString( "create.record.with.locals" );

                bizLogger.error( "Unable to create sub actions doing to an error: {}", message );
                return ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message );
            }

            MarcRecordReader reader = new MarcRecordReader( record );
            if( solrService.hasDocuments( SolrServiceIndexer.createSubfieldQueryDBCOnly( "002a", reader.recordId() ) ) ) {
                String message = messages.getString( "update.record.with.002.links" );

                bizLogger.error( "Unable to create sub actions doing to an error: {}", message );
                return ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message );
            }

            bizLogger.error( "Creating sub actions successfully" );

            children.add( StoreRecordAction.newStoreAction( rawRepo, record, MIMETYPE ) );
            children.add( EnqueueRecordAction.newEnqueueAction( rawRepo, record, providerId, MIMETYPE ) );

            return ServiceResult.newOkResult();
        }
        finally {
            logger.exit();
        }
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final XLogger logger = XLoggerFactory.getXLogger( CreateSingleRecordAction.class );
    private static final XLogger bizLogger = XLoggerFactory.getXLogger( BusinessLoggerFilter.LOGGER_NAME );

    static final String MIMETYPE = MarcXChangeMimeType.MARCXCHANGE;

    /**
     * Class to give access to lookups for the rawrepo in solr.
     */
    private SolrService solrService;

    private String providerId;
    private ResourceBundle messages;
}
