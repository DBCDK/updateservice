//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

//-----------------------------------------------------------------------------
/**
 * Action to remove all links from a record to all other records.
 * <p>
 *     This action is used in these common cases:
 *     <ol>
 *         <li>If a record change status from a volume record to a single record.</li>
 *         <li>The record is deleted.</li>
 *     </ol>
 * </p>
 */
public class RemoveLinksAction extends AbstractRawRepoAction {
    public RemoveLinksAction( RawRepo rawRepo, MarcRecord record ) {
        super( "RemovesLinksAction", rawRepo, record );
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

        ServiceResult result = null;
        try {
            bizLogger.info( "Handling record:\n{}", record );

            MarcRecordReader reader = new MarcRecordReader( record );
            String recId = reader.recordId();
            Integer agencyId = reader.agencyIdAsInteger();

            rawRepo.removeLinks( new RecordId( recId, agencyId ) );
            bizLogger.info( "Removed all links for record {{}:{}} successfully", recId, agencyId );

            return result = ServiceResult.newOkResult();
        }
        finally {
            logger.exit( result );
        }
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final XLogger logger = XLoggerFactory.getXLogger( RemoveLinksAction.class );
    private static final XLogger bizLogger = XLoggerFactory.getXLogger( BusinessLoggerFilter.LOGGER_NAME );
}
