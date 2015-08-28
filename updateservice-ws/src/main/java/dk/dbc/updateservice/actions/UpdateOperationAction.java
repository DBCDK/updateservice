//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------

import dk.dbc.iscrum.records.MarcReader;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.auth.Authenticator;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.service.api.Authentication;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.HoldingsItems;
import dk.dbc.updateservice.update.LibraryRecordsHandler;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.ws.JNDIResources;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.*;

//-----------------------------------------------------------------------------

/**
 * Action to perform an Update Operation for a record.
 * <p/>
 * This action needs the following to be able to authenticate a record:
 * <ol>
 *     <li>The record to authenticate, <code>record</code></li>
 *     <li>
 *         The name of the template that contains the validation rules to check against the record,
 *         <code>schemaName</code>
 *     </li>
 *     <li>
 *         An Authenticator that do the actual authentication, <code>authenticator</code>.
 *     </li>
 *     <li>
 *         Login information to be parsed to <code>authenticator</code>.
 *     </li>
 * </ol>
 */
public class UpdateOperationAction extends AbstractRawRepoAction {
    /**
     * Constructs an instance with a template name and a record.
     * <p/>
     * The JavaScript logic needs an JavaScript environment and a set of
     * settings to work properly. These can be set though the properties <code>scripter</code>
     * and <code>settings</code>. This constructor initialize these to null.
     *
     * @param record     The record to validate.
     */
    public UpdateOperationAction( RawRepo rawRepo, MarcRecord record ) {
        super( "UpdateOperationAction", rawRepo, record );

        this.authenticator = null;
        this.authentication = null;
        this.holdingsItems = null;
        this.recordsHandler = null;
        this.settings = null;

        this.messages = ResourceBundles.getBundle( this, "actions" );
    }

    public Authenticator getAuthenticator() {
        return authenticator;
    }

    public void setAuthenticator( Authenticator authenticator ) {
        this.authenticator = authenticator;
    }

    public Authentication getAuthentication() {
        return authentication;
    }

    public void setAuthentication( Authentication authentication ) {
        this.authentication = authentication;
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
     * Updates the record in rawrepo.
     * <p>
     * The operation is performed by adding these child actions:
     * <ol>
     *     <li>
     *         AuthenticateRecordAction: To authenticate the record against the user calling
     *         the web service.
     *     </li>
     * </ol>
     *
     * @return ServiceResult with status UpdateStatusEnum.OK
     *
     * @throws UpdateException Never thrown.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        logger.entry();

        ServiceResult result = null;
        try {
            ServiceResult checkResult = checkRecordForUpdatability();
            if( checkResult.getStatus() != UpdateStatusEnum.OK ) {
                bizLogger.error( "Unable to update record: {}", checkResult );
                return checkResult;
            }

            AuthenticateRecordAction authenticateRecordAction = new AuthenticateRecordAction( this.record );
            authenticateRecordAction.setAuthenticator( this.authenticator );
            authenticateRecordAction.setAuthentication( this.authentication );
            this.children.add( authenticateRecordAction );

            bizLogger.info( "Split record into records to store in rawrepo." );
            List<MarcRecord> records = recordsHandler.recordDataForRawRepo( record, authentication.getUserIdAut(), authentication.getGroupIdAut() );
            Collections.sort( records, new ProcessOrder() );

            for( MarcRecord rec : records ) {
                bizLogger.info( "" );
                bizLogger.info( "Create sub actions for record:\n{}", rec );

                Integer agencyId = Integer.parseInt( MarcReader.getRecordValue( rec, "001", "b" ), 10 );

                if( agencyId.equals( RawRepo.RAWREPO_COMMON_LIBRARY ) ) {
                    UpdateCommonRecordAction action = new UpdateCommonRecordAction( rawRepo, rec );
                    action.setGroupId( Integer.valueOf( this.authentication.getGroupIdAut(), 10 ) );
                    action.setRecordsHandler( recordsHandler );
                    action.setHoldingsItems( holdingsItems );
                    action.setSettings( settings );

                    children.add( action );
                }
                else {
                    if( commonRecordExists( records, rec ) ) {
                        UpdateEnrichmentRecordAction action = new UpdateEnrichmentRecordAction( rawRepo, rec );
                        action.setRecordsHandler( recordsHandler );
                        action.setHoldingsItems( holdingsItems );
                        action.setProviderId( settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ) );

                        children.add( action );
                    }
                    else {
                        UpdateLocalRecordAction action = new UpdateLocalRecordAction( rawRepo, rec );
                        action.setHoldingsItems( holdingsItems );
                        action.setProviderId( settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ) );

                        children.add( action );
                    }
                }
            }

            return result = ServiceResult.newOkResult();
        }
        catch( ScripterException ex ) {
            return result = ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, ex.getMessage() );
        }
        finally {
            logger.exit( result );
        }
    }

    private boolean commonRecordExists( List<MarcRecord> records, MarcRecord rec ) throws UpdateException {
        logger.entry();

        try {
            String recordId = MarcReader.getRecordValue( rec, "001", "a" );

            if( rawRepo.recordExists( recordId, RawRepo.RAWREPO_COMMON_LIBRARY ) ) {
                return true;
            }

            for( MarcRecord record : records ) {
                String checkRecordId = MarcReader.getRecordValue( record, "001", "a" );
                Integer checkAgencyId = Integer.parseInt( MarcReader.getRecordValue( record, "001", "b" ), 10 );

                if( checkRecordId.equals( recordId ) && checkAgencyId.equals( RawRepo.RAWREPO_COMMON_LIBRARY) ) {
                    return true;
                }
            }

            return false;
        }
        finally {
            logger.exit();
        }
    }

    private ServiceResult checkRecordForUpdatability() throws UpdateException {
        logger.entry();

        try {
            if( !MarcReader.markedForDeletion( record ) ) {
                return ServiceResult.newOkResult();
            }

            String recordId = MarcReader.getRecordValue( record, "001", "a" );
            int agencyId = Integer.parseInt( MarcReader.getRecordValue( record, "001", "b" ), 10 );
            int rawRepoAgencyId = agencyId;

            if( agencyId == RawRepo.COMMON_LIBRARY ) {
                rawRepoAgencyId = RawRepo.RAWREPO_COMMON_LIBRARY;
            }

            if( !rawRepo.children( new RecordId( recordId, rawRepoAgencyId ) ).isEmpty() ) {
                String message = String.format( messages.getString( "delete.record.children.error" ), recordId );
                return ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message );
            }

            return ServiceResult.newOkResult();
        }
        finally {
            logger.exit();
        }
    }


    /**
     * Class to sort the records returned from JavaScript in the order they should be
     * processed.
     * <p/>
     * The records are sorted in this order:
     * <ol>
     *     <li>Common records are processed before local and enrichment records.</li>
     *     <li>
     *         If one of the records has the deletion mark in 004r then the process order
     *         is reversed.
     *     </li>
     * </ol>
     */
    private class ProcessOrder implements Comparator<MarcRecord> {

        /**
         * Compares its two arguments for order.  Returns a negative integer, zero, or a positive integer as the first
         * argument is less than, equal to, or greater than the second.<p>
         * <p>
         * In the foregoing description, the notation <tt>sgn(</tt><i>expression</i><tt>)</tt> designates the mathematical
         * <i>signum</i> function, which is defined to return one of <tt>-1</tt>, <tt>0</tt>, or <tt>1</tt> according to
         * whether the value of <i>expression</i> is negative, zero or positive.<p>
         * <p>
         * The implementor must ensure that <tt>sgn(compare(x, y)) == -sgn(compare(y, x))</tt> for all <tt>x</tt> and
         * <tt>y</tt>.  (This implies that <tt>compare(x, y)</tt> must throw an exception if and only if <tt>compare(y,
         * x)</tt> throws an exception.)<p>
         * <p>
         * The implementor must also ensure that the relation is transitive: <tt>((compare(x, y)&gt;0) &amp;&amp;
         * (compare(y, z)&gt;0))</tt> implies <tt>compare(x, z)&gt;0</tt>.<p>
         * <p>
         * Finally, the implementor must ensure that <tt>compare(x, y)==0</tt> implies that <tt>sgn(compare(x,
         * z))==sgn(compare(y, z))</tt> for all <tt>z</tt>.<p>
         * <p>
         * It is generally the case, but <i>not</i> strictly required that <tt>(compare(x, y)==0) == (x.equals(y))</tt>.
         * Generally speaking, any comparator that violates this condition should clearly indicate this fact.  The
         * recommended language is "Note: this comparator imposes orderings that are inconsistent with equals."
         *
         * @param o1 the first object to be compared.
         * @param o2 the second object to be compared.
         *
         * @return a negative integer, zero, or a positive integer as the first argument is less than, equal to, or greater
         * than the second.
         *
         * @throws NullPointerException if an argument is null and this comparator does not permit null arguments
         * @throws ClassCastException   if the arguments' types prevent them from being compared by this comparator.
         */
        @Override
        public int compare( MarcRecord o1, MarcRecord o2 ) {
            Integer agency1 = Integer.valueOf( MarcReader.getRecordValue( o1, "001", "b" ), 10 );
            Integer agency2 = Integer.valueOf( MarcReader.getRecordValue( o2, "001", "b" ), 10 );

            int result;
            if( agency1.equals( agency2 ) ) {
                result = 0;
            }
            else if( agency1.equals( RawRepo.RAWREPO_COMMON_LIBRARY ) ) {
                result = -1;
            }
            else {
                result = 1;
            }

            if( MarcReader.markedForDeletion( o1 ) || MarcReader.markedForDeletion( o2 ) ) {
                return result * -1;
            }

            return result;
        }
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final XLogger logger = XLoggerFactory.getXLogger( UpdateOperationAction.class );
    private static final XLogger bizLogger = XLoggerFactory.getXLogger( BusinessLoggerFilter.LOGGER_NAME );

    /**
     * Authroticator EJB to authenticate the parsed record.
     */
    private Authenticator authenticator;

    /**
     * Authentication members from the request.
     * <p/>
     * Group, username and password.
     */
    private Authentication authentication;

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