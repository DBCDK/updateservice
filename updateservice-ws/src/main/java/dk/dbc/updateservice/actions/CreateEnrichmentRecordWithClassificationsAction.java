//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcReader;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.update.LibraryRecordsHandler;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

//-----------------------------------------------------------------------------
/**
 * Action to create a new enrichment record from a common record.
 * <p>
 *     This action handles to case where we need to create a new enrichment
 *     of copy classification data into it from a common record. This case is
 *     triggered when the classification data is updated in a common record and
 *     there is no enrichment record for a agency that has holdings for the
 *     common record.
 * </p>
 * <p>
 *     The creation of the enrichment record is done by calling the JavaScript
 *     engine (thought LibraryRecordsHandler) to produce the actual enrichment
 *     record.
 * </p>
 */
public class CreateEnrichmentRecordWithClassificationsAction extends AbstractAction {
    public CreateEnrichmentRecordWithClassificationsAction( RawRepo rawRepo ) {
        this( rawRepo, null );
    }

    public CreateEnrichmentRecordWithClassificationsAction( RawRepo rawRepo, Integer agencyId ) {
        super( "CreateEnrichmentRecordAction" );

        this.rawRepo = rawRepo;
        this.agencyId = agencyId;
        this.recordsHandler = null;
    }

    public RawRepo getRawRepo() {
        return rawRepo;
    }

    public LibraryRecordsHandler getRecordsHandler() {
        return recordsHandler;
    }

    public void setRecordsHandler( LibraryRecordsHandler recordsHandler ) {
        this.recordsHandler = recordsHandler;
    }

    public Integer getAgencyId() {
        return agencyId;
    }

    public void setAgencyId( Integer agencyId ) {
        this.agencyId = agencyId;
    }

    public MarcRecord getCommonRecord() {
        return commonRecord;
    }

    public void setCommonRecord( MarcRecord commonRecord ) {
        this.commonRecord = commonRecord;
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
            MarcRecord enrichmentRecord = createRecord();
            bizLogger.info( "Creating sub actions to store new enrichment record." );
            bizLogger.info( "Enrichment record:\n{}", enrichmentRecord );

            String recordId = MarcReader.getRecordValue( enrichmentRecord, "001", "a" );

            StoreRecordAction storeRecordAction = new StoreRecordAction( rawRepo, enrichmentRecord );
            storeRecordAction.setMimetype( MIMETYPE );
            children.add( storeRecordAction );

            LinkRecordAction linkRecordAction = new LinkRecordAction( rawRepo, enrichmentRecord );
            linkRecordAction.setLinkToRecordId( new RecordId( recordId, RawRepo.RAWREPO_COMMON_LIBRARY ) );
            children.add( linkRecordAction );

            EnqueueRecordAction enqueueRecordAction = new EnqueueRecordAction( rawRepo, enrichmentRecord );
            enqueueRecordAction.setMimetype( MIMETYPE );
            children.add( enqueueRecordAction );

            return ServiceResult.newOkResult();
        }
        catch( ScripterException ex ) {
            logger.error( "Update error: " + ex.getMessage(), ex );
            throw new UpdateException( ex.getMessage(), ex );
        }
        finally {
            logger.exit();
        }
    }

    public MarcRecord createRecord() throws ScripterException {
        logger.entry();

        try {
            return recordsHandler.createLibraryExtendedRecord( commonRecord, agencyId );
        }
        finally {
            logger.exit();
        }
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final XLogger logger = XLoggerFactory.getXLogger( CreateEnrichmentRecordWithClassificationsAction.class );
    private static final XLogger bizLogger = XLoggerFactory.getXLogger( BusinessLoggerFilter.LOGGER_NAME );

    static final String MIMETYPE = MarcXChangeMimeType.ENRICHMENT;

    /**
     * RawRepo EJB to write records to the RawRepo.
     */
    protected RawRepo rawRepo;

    /**
     * Records handler to create an enrichment record thought JavaScript.
     */
    protected LibraryRecordsHandler recordsHandler;

    /**
     * Common record to use to create the enrichment record.
     */
    protected MarcRecord commonRecord;

    /**
     * Agency id of the library that will own the produced enrichment record.
     */
    private Integer agencyId;
}
