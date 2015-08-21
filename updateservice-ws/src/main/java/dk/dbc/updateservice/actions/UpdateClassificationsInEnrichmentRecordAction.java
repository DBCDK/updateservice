//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.update.RawRepo;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

//-----------------------------------------------------------------------------
/**
 * Updates the classifications in a enrichment record from the classifications
 * from a common record.
 */
public class UpdateClassificationsInEnrichmentRecordAction extends CreateEnrichmentRecordWithClassificationsAction {
    public UpdateClassificationsInEnrichmentRecordAction( RawRepo rawRepo ) {
        this( rawRepo, null );
    }

    public UpdateClassificationsInEnrichmentRecordAction( RawRepo rawRepo, MarcRecord enrichmentRecord ) {
        super( rawRepo );

        this.enrichmentRecord = enrichmentRecord;
    }

    public MarcRecord getEnrichmentRecord() {
        return enrichmentRecord;
    }

    public void setEnrichmentRecord( MarcRecord enrichmentRecord ) {
        this.enrichmentRecord = enrichmentRecord;
    }

    /**
     * updates the classifications in the enrichment record.
     *
     * @return The enrichment record after its classifications has been updated.
     *
     * @throws ScripterException In case of en JavaScript error.
     */
    @Override
    public MarcRecord createRecord() throws ScripterException {
        logger.entry();

        try {
            if( updatingCommonRecord == null ) {
                throw new IllegalStateException( "updatingCommonRecord is not assigned a value" );
            }

            if( enrichmentRecord == null ) {
                throw new IllegalStateException( "enrichmentRecord is not assigned a value" );
            }

            if( recordsHandler == null ) {
                throw new IllegalStateException( "recordsHandler is not assigned a value" );
            }

            return this.recordsHandler.updateLibraryExtendedRecord( this.currentCommonRecord, this.updatingCommonRecord, this.enrichmentRecord );
        }
        finally {
            logger.exit();
        }
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final XLogger logger = XLoggerFactory.getXLogger( UpdateClassificationsInEnrichmentRecordAction.class );

    /**
     * Enrichment record that needs to update its classifications.
     */
    private MarcRecord enrichmentRecord;
}
