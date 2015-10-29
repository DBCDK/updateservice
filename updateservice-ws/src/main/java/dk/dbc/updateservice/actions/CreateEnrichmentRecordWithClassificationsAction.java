//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.records.MarcRecordWriter;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.update.LibraryRecordsHandler;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.ws.MDCUtil;
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
        super( "CreateEnrichmentRecordWithClassificationsAction" );

        this.rawRepo = rawRepo;
        this.agencyId = agencyId;
        this.recordsHandler = null;
        this.currentCommonRecord = null;
        this.updatingCommonRecord = null;
        this.commonRecordId = null;
        this.providerId = null;
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

    public MarcRecord getCurrentCommonRecord() {
        return currentCommonRecord;
    }

    public void setCurrentCommonRecord( MarcRecord currentCommonRecord ) {
        this.currentCommonRecord = currentCommonRecord;
    }

    public MarcRecord getUpdatingCommonRecord() {
        return updatingCommonRecord;
    }

    public void setUpdatingCommonRecord( MarcRecord updatingCommonRecord ) {
        this.updatingCommonRecord = updatingCommonRecord;
    }

    public String getCommonRecordId() {
        return commonRecordId;
    }

    public void setCommonRecordId( String commonRecordId ) {
        this.commonRecordId = commonRecordId;
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
            bizLogger.info( "Current common record:\n{}", currentCommonRecord );
            bizLogger.info( "Updating common record:\n{}", updatingCommonRecord );

            MarcRecord enrichmentRecord = createRecord();
            if( enrichmentRecord.getFields().isEmpty() ) {
                bizLogger.info( "No sub actions to create for an empty enrichment record." );

                return ServiceResult.newOkResult();
            }
            bizLogger.info( "Creating sub actions to store new enrichment record." );
            bizLogger.info( "Enrichment record:\n{}", enrichmentRecord );

            String recordId = new MarcRecordReader( enrichmentRecord ).recordId();

            StoreRecordAction storeRecordAction = new StoreRecordAction( rawRepo, enrichmentRecord );
            storeRecordAction.setMimetype( MIMETYPE );
            children.add( storeRecordAction );

            LinkRecordAction linkRecordAction = new LinkRecordAction( rawRepo, enrichmentRecord );
            linkRecordAction.setLinkToRecordId( new RecordId( recordId, RawRepo.RAWREPO_COMMON_LIBRARY ) );
            children.add( linkRecordAction );

            EnqueueRecordAction enqueueRecordAction = new EnqueueRecordAction( rawRepo, enrichmentRecord );
            enqueueRecordAction.setProviderId( providerId );
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

    @Override
    public void setupMDCContext() {
        MDCUtil.setupContextForEnrichmentRecord( updatingCommonRecord, agencyId.toString() );
    }

    public MarcRecord createRecord() throws ScripterException {
        logger.entry();

        MarcRecord result = null;
        try {
            result = recordsHandler.createLibraryExtendedRecord( currentCommonRecord, updatingCommonRecord, agencyId );
            if( commonRecordId != null ) {
                MarcRecordWriter writer = new MarcRecordWriter( result );
                writer.addOrReplaceSubfield( "001", "a", commonRecordId );
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
     * The current version of the common record that is begin updated.
     * <p>
     *     This member may be null.
     * </p>
     */
    protected MarcRecord currentCommonRecord;

    /**
     * Common record that is being updated.
     */
    protected MarcRecord updatingCommonRecord;

    /**
     * Record id to assign to the new enrichment record.
     * <p>
     *     Normally we use the record id from the common record that is used to create
     *     the enrichment record. But in a special case where we create an enrichment
     *     in the process of merging two common records with field 002 we need to assign the
     *     enrichment record to another common record.
     * </p>
     * <p>
     *     This property is used in that case. In other cases this member will be null.
     * </p>
     */
    protected String commonRecordId;

    /**
     * Agency id of the library that will own the produced enrichment record.
     */
    private Integer agencyId;

    private String providerId;
}
