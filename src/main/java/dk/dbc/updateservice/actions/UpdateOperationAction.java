//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.openagency.client.OpenAgencyException;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.auth.Authenticator;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.service.api.Authentication;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.*;
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
        this.openAgencyService = null;
        this.solrService = null;
        this.recordsHandler = null;
        this.scripter = null;
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

    public OpenAgencyService getOpenAgencyService() {
        return openAgencyService;
    }

    public void setOpenAgencyService( OpenAgencyService openAgencyService ) {
        this.openAgencyService = openAgencyService;
    }

    public SolrService getSolrService() {
        return solrService;
    }

    public void setSolrService( SolrService solrService ) {
        this.solrService = solrService;
    }

    public LibraryRecordsHandler getRecordsHandler() {
        return recordsHandler;
    }

    public void setRecordsHandler( LibraryRecordsHandler recordsHandler ) {
        this.recordsHandler = recordsHandler;
    }

    public Scripter getScripter() {
        return scripter;
    }

    public void setScripter( Scripter scripter ) {
        this.scripter = scripter;
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
            bizLogger.info( "Handling record:\n{}", record );

            ServiceResult checkResult = checkRecordForUpdatability();
            if( checkResult.getStatus() != UpdateStatusEnum.OK ) {
                bizLogger.error( "Unable to update record: {}", checkResult );
                return checkResult;
            }

            List<DoubleRecordCheckingAction> doubleRecordActions = new ArrayList<>();

            AuthenticateRecordAction authenticateRecordAction = new AuthenticateRecordAction( this.record );
            authenticateRecordAction.setAuthenticator( this.authenticator );
            authenticateRecordAction.setAuthentication( this.authentication );
            this.children.add( authenticateRecordAction );

            MarcRecordReader updReader = new MarcRecordReader( record );
            String updRecordId = updReader.recordId();
            Integer updAgencyId = updReader.agencyIdAsInteger();

            bizLogger.info( "Split record into records to store in rawrepo." );
            List<MarcRecord> records = recordsHandler.recordDataForRawRepo( record, authentication.getUserIdAut(), authentication.getGroupIdAut() );

            for( MarcRecord rec : records ) {
                bizLogger.info( "" );
                bizLogger.info( "Create sub actions for record:\n{}", rec );

                MarcRecordReader reader = new MarcRecordReader( rec );
                String recordId = reader.recordId();
                Integer agencyId = reader.agencyIdAsInteger();

                if( reader.markedForDeletion() && !rawRepo.recordExists( recordId, agencyId ) ) {
                    String message = String.format( messages.getString( "operation.delete.non.existing.record" ), recordId, agencyId );
                    return ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message );
                }

                if( agencyId.equals( RawRepo.RAWREPO_COMMON_LIBRARY ) ) {
                    if ( !updReader.markedForDeletion() &&
                         ! openAgencyService.hasFeature( authentication.getGroupIdAut(), LibraryRuleHandler.Rule.AUTH_CREATE_COMMON_RECORD ) &&
                         !rawRepo.recordExists( updRecordId, updAgencyId ) ) {
                        String message = String.format( messages.getString( "common.record.creation.not.allowed" ), authentication.getGroupIdAut() );
                        return ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message );
                    }
                    UpdateCommonRecordAction action = new UpdateCommonRecordAction( rawRepo, rec );
                    action.setGroupId( Integer.valueOf( this.authentication.getGroupIdAut(), 10 ) );
                    action.setRecordsHandler( recordsHandler );
                    action.setOpenAgencyService( openAgencyService );
                    action.setSolrService( solrService );
                    action.setHoldingsItems( holdingsItems );
                    action.setSettings( settings );

                    children.add( action );
                }
                else if( agencyId.equals( RawRepo.SCHOOL_COMMON_AGENCY ) ) {
                    UpdateSchoolCommonRecord action = new UpdateSchoolCommonRecord( rawRepo, rec );
                    action.setRecordsHandler( recordsHandler );
                    action.setHoldingsItems( holdingsItems );
                    action.setSolrService( solrService );
                    action.setProviderId( settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ) );

                    children.add( action );
                }
                else {
                    if( commonRecordExists( records, rec ) &&
                        ( agencyId.equals( RawRepo.COMMON_LIBRARY ) ||
                          openAgencyService.hasFeature( this.authentication.getGroupIdAut(), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS ) ) )
                    {
                        UpdateEnrichmentRecordAction action;
                        if( RawRepo.isSchoolEnrichment( agencyId ) ) {
                            action = new UpdateSchoolEnrichmentRecordAction( rawRepo, rec );
                        }
                        else {
                            action = new UpdateEnrichmentRecordAction( rawRepo, rec );
                        }

                        action.setRecordsHandler( recordsHandler );
                        action.setHoldingsItems( holdingsItems );
                        action.setSolrService( solrService );
                        action.setProviderId( settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ) );

                        children.add( action );
                    }
                    else {
                        UpdateLocalRecordAction action = new UpdateLocalRecordAction( rawRepo, rec );
                        action.setHoldingsItems( holdingsItems );
                        action.setOpenAgencyService( openAgencyService );
                        action.setProviderId( settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ) );

                        children.add( action );
                    }
                }
            }
            bizLogger.info( "Delete ?:{}", updReader.markedForDeletion() );
            bizLogger.info( "isDBC ?:{}", updReader.isDBCRecord() );
            bizLogger.info( "rawr exist ?:{}", rawRepo.recordExists( updRecordId, updAgencyId));
            bizLogger.info( "ag id ?:{}", updAgencyId);
            if( !updReader.markedForDeletion() && ! updReader.isDBCRecord() &&
                !rawRepo.recordExists( updRecordId, updAgencyId ) && updAgencyId.equals( RawRepo.COMMON_LIBRARY ) ) {
                DoubleRecordCheckingAction doubleRecordCheckingAction = new DoubleRecordCheckingAction( record );
                doubleRecordCheckingAction.setSettings( settings );
                doubleRecordCheckingAction.setScripter( scripter );
                doubleRecordActions.add( doubleRecordCheckingAction );
            }

            children.addAll( doubleRecordActions );
            return result = ServiceResult.newOkResult();
        }
        catch( ScripterException | OpenAgencyException ex ) {
            return result = ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, ex.getMessage() );
        }
        finally {
            logger.exit( result );
        }
    }

    private boolean commonRecordExists( List<MarcRecord> records, MarcRecord rec ) throws UpdateException {
        logger.entry();

        try {
            MarcRecordReader reader = new MarcRecordReader( rec );
            String recordId = reader.recordId();

            if( rawRepo.recordExists( recordId, RawRepo.RAWREPO_COMMON_LIBRARY ) ) {
                return true;
            }

            for( MarcRecord record : records ) {
                MarcRecordReader recordReader = new MarcRecordReader( record );
                String checkRecordId = recordReader.recordId();
                Integer checkAgencyId = recordReader.agencyIdAsInteger();

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
            MarcRecordReader reader = new MarcRecordReader( record );
            if( !reader.markedForDeletion() ) {
                return ServiceResult.newOkResult();
            }

            String recordId = reader.recordId();
            int agencyId = reader.agencyIdAsInteger();
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
            MarcRecordReader reader1 = new MarcRecordReader( o1 );
            MarcRecordReader reader2 = new MarcRecordReader( o2 );

            Integer agency1 = reader1.agencyIdAsInteger();
            Integer agency2 = reader2.agencyIdAsInteger();

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

            if( reader1.markedForDeletion() || reader2.markedForDeletion() ) {
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
     * Class to give access to the OpenAgency web service
     */
    private OpenAgencyService openAgencyService;

    /**
     * Class to give access to lookups for the rawrepo in solr.
     */
    private SolrService solrService;

    /**
     * Class to give access to the JavaScript engine to handle records.
     * <p>
     *      The LibraryRecordsHandler is used to check records for changes in
     *      classifications.
     * </p>
     */
    private LibraryRecordsHandler recordsHandler;
    private Scripter scripter;
    private Properties settings;

    private ResourceBundle messages;
}
