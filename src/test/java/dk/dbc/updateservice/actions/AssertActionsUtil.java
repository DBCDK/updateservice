//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordFactory;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.records.MarcRecordWriter;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.auth.Authenticator;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.service.api.Authentication;
import dk.dbc.updateservice.update.*;
import org.junit.Assert;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

/**
 * Utility functions to reuse code in different test classes.
 */
public class AssertActionsUtil {
    //-------------------------------------------------------------------------
    //              Constants
    //-------------------------------------------------------------------------

    public static final String COMMON_SINGLE_RECORD_RESOURCE = "common_enrichment.marc";
    public static final String COMMON_MAIN_RECORD_RESOURCE = "rawrepo-main.marc";
    public static final String COMMON_MAIN_ENRICHMENT_RECORD_RESOURCE = "rawrepo-main-enrichment.marc";
    public static final String COMMON_VOLUME_RECORD_RESOURCE = "rawrepo-volume.marc";
    public static final String LOCAL_MAIN_RECORD_RESOURCE = "rawrepo-local-main.marc";
    public static final String LOCAL_VOLUME_RECORD_RESOURCE = "rawrepo-local-volume.marc";
    public static final String ENRICHMENT_SINGLE_RECORD_RESOURCE = "enrichment.marc";
    public static final String LOCAL_SINGLE_RECORD_RESOURCE = "book.marc";

    public static final String COMMON_SCHOOL_RECORD_RESOURCE = "common_school_enrichment.marc";
    public static final String SCHOOL_RECORD_RESOURCE = "school_enrichment.marc";

    //-------------------------------------------------------------------------
    //              Records
    //-------------------------------------------------------------------------

    public static MarcRecord loadRecord( String filename ) throws IOException {
        InputStream is = AssertActionsUtil.class.getResourceAsStream( "/dk/dbc/updateservice/actions/" + filename );
        return MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );
    }

    public static MarcRecord loadRecord( String filename, String newRecordId ) throws IOException {
        logger.entry();

        MarcRecord record = null;
        try {
            record = loadRecord( filename );
            new MarcRecordWriter( record ).addOrReplaceSubfield( "001", "a", newRecordId );

            return record;
        }
        finally {
            logger.exit( record );
        }
    }

    public static MarcRecord loadRecordAndMarkForDeletion( String filename ) throws IOException {
        MarcRecord record = loadRecord( filename );
        new MarcRecordWriter( record ).markForDeletion();

        return record;
    }

    public static MarcRecord loadRecordAndMarkForDeletion( String filename, String newRecordId ) throws IOException {
        logger.entry();

        MarcRecord record = null;
        try {
            record = loadRecordAndMarkForDeletion( filename );
            new MarcRecordWriter( record ).addOrReplaceSubfield( "001", "a", newRecordId );

            return record;
        }
        finally {
            logger.exit( record );
        }
    }

    public static Set<RecordId> createRecordSet( MarcRecord... records ) {
        Set<RecordId> result = new HashSet<>();

        for( MarcRecord record : records ) {
            result.add( new RecordId( getRecordId( record ), getAgencyId( record ) ) );
        }

        return result;
    }

    public static Set<Integer> createAgenciesSet( Integer... agencies ) {
        Set<Integer> result = new HashSet<>();

        for( Integer agencyId : agencies ) {
            result.add( agencyId );
        }

        return result;
    }

    public static Record createRawRepoRecord( MarcRecord record, String mimetype ) throws JAXBException, UnsupportedEncodingException {
        RawRepoRecordMock result = new RawRepoRecordMock( getRecordId( record ), getAgencyId( record ) );
        result.setMimeType( mimetype );
        result.setDeleted( false );
        result.setContent( new RawRepoEncoder().encodeRecord( record ) );

        return result;
    }

    public static String getRecordId( MarcRecord record ) {
        return new MarcRecordReader( record ).recordId();
    }

    public static Integer getAgencyId( MarcRecord record ) {
        return new MarcRecordReader( record ).agencyIdAsInteger();
    }

    //-------------------------------------------------------------------------
    //              Asserts
    //-------------------------------------------------------------------------

    public static void assertAuthenticateRecordAction( ServiceAction action, MarcRecord record, Authenticator authenticator, Authentication authentication ) {
        assertThat( action, notNullValue() );
        assertThat( action.getClass().getName(), equalTo( AuthenticateRecordAction.class.getName() ) );

        AuthenticateRecordAction authenticateRecordAction = (AuthenticateRecordAction)action;
        Assert.assertThat( authenticateRecordAction.getRecord(), is( record ) );
        Assert.assertThat( authenticateRecordAction.getAuthenticator(), is( authenticator ) );
        Assert.assertThat( authenticateRecordAction.getAuthentication(), is( authentication ) );
    }

    public static void assertUpdateCommonRecordAction( ServiceAction action, RawRepo rawRepo, MarcRecord record, Integer groupId, LibraryRecordsHandler recordsHandler, HoldingsItems holdingsItems, OpenAgencyService openAgencyService ) {
        assertThat( action, notNullValue() );
        assertThat( action.getClass().getName(), equalTo( UpdateCommonRecordAction.class.getName() ) );

        UpdateCommonRecordAction updateCommonRecordAction = (UpdateCommonRecordAction)action;
        Assert.assertThat( updateCommonRecordAction.getRawRepo(), is( rawRepo ) );
        Assert.assertThat( updateCommonRecordAction.getRecord(), is( record ) );
        Assert.assertThat( updateCommonRecordAction.getGroupId(), equalTo( groupId ) );
        Assert.assertThat( updateCommonRecordAction.getRecordsHandler(), is( recordsHandler ) );
        Assert.assertThat( updateCommonRecordAction.getHoldingsItems(), is( holdingsItems ) );
        Assert.assertThat( updateCommonRecordAction.getOpenAgencyService(), is( openAgencyService ) );
    }

    public static void assertCreateSingleRecordAction( ServiceAction action, RawRepo rawRepo, MarcRecord record, SolrService solrService, String providerId ) {
        assertThat( action, notNullValue() );
        assertThat( action.getClass().getName(), equalTo( CreateSingleRecordAction.class.getName() ) );

        CreateSingleRecordAction act = (CreateSingleRecordAction)action;
        Assert.assertThat( act.getRawRepo(), is( rawRepo ) );
        Assert.assertThat( act.getRecord(), is( record ) );
        Assert.assertThat( act.getSolrService(), is( solrService ) );
        Assert.assertThat( act.getProviderId(), equalTo( providerId ) );
    }

    public static void assertCreateVolumeRecordAction( ServiceAction action, RawRepo rawRepo, MarcRecord record, HoldingsItems holdingsItems, SolrService solrService, String providerId ) {
        assertThat( action, notNullValue() );
        assertThat( action.getClass().getName(), equalTo( CreateVolumeRecordAction.class.getName() ) );

        CreateVolumeRecordAction act = (CreateVolumeRecordAction)action;
        Assert.assertThat( act.getRawRepo(), is( rawRepo ) );
        Assert.assertThat( act.getRecord(), is( record ) );
        Assert.assertThat( act.getHoldingsItems(), is( holdingsItems ) );
        Assert.assertThat( act.getSolrService(), is( solrService ) );
        Assert.assertThat( act.getProviderId(), equalTo( providerId ) );
    }

    public static void assertUpdateLocalRecordAction( ServiceAction action, RawRepo rawRepo, MarcRecord record, HoldingsItems holdingsItems ) {
        assertThat( action, notNullValue() );
        assertThat( action.getClass().getName(), equalTo( UpdateLocalRecordAction.class.getName() ) );

        UpdateLocalRecordAction updateLocalRecordAction = (UpdateLocalRecordAction)action;
        Assert.assertThat( updateLocalRecordAction.getRawRepo(), is( rawRepo ) );
        Assert.assertThat( updateLocalRecordAction.getRecord(), is( record ) );
        Assert.assertThat( updateLocalRecordAction.getHoldingsItems(), is( holdingsItems ) );
    }

    public static void assertUpdateEnrichmentRecordAction( ServiceAction action, RawRepo rawRepo, MarcRecord record, LibraryRecordsHandler recordsHandler, HoldingsItems holdingsItems ) {
        assertThat( action, notNullValue() );
        assertThat( action.getClass().getName(), equalTo( UpdateEnrichmentRecordAction.class.getName() ) );

        UpdateEnrichmentRecordAction updateEnrichmentRecordAction = (UpdateEnrichmentRecordAction)action;
        Assert.assertThat( updateEnrichmentRecordAction.getRawRepo(), is( rawRepo ) );
        Assert.assertThat( updateEnrichmentRecordAction.getRecord(), is( record ) );
        Assert.assertThat( updateEnrichmentRecordAction.getRecordsHandler(), is( recordsHandler ) );
        Assert.assertThat( updateEnrichmentRecordAction.getHoldingsItems(), is( holdingsItems ) );
    }

    public static void assertSchoolCommonRecordAction( ServiceAction action, RawRepo rawRepo, MarcRecord record, LibraryRecordsHandler recordsHandler, HoldingsItems holdingsItems, String providerId ) {
        assertThat( action, notNullValue() );
        assertThat( action.getClass().getName(), equalTo( UpdateSchoolCommonRecord.class.getName() ) );

        UpdateSchoolCommonRecord updateSchoolCommonRecord = (UpdateSchoolCommonRecord)action;
        Assert.assertThat( updateSchoolCommonRecord.getRawRepo(), is( rawRepo ) );
        Assert.assertThat( updateSchoolCommonRecord.getRecord(), is( record ) );
        Assert.assertThat( updateSchoolCommonRecord.getRecordsHandler(), is( recordsHandler ) );
        Assert.assertThat( updateSchoolCommonRecord.getHoldingsItems(), is( holdingsItems ) );
        Assert.assertThat( updateSchoolCommonRecord.getProviderId(), equalTo( providerId ) );
    }

    public static void assertSchoolEnrichmentRecordAction( ServiceAction action, RawRepo rawRepo, MarcRecord record, LibraryRecordsHandler recordsHandler, HoldingsItems holdingsItems, String providerId ) {
        assertThat( action, notNullValue() );
        assertThat( action.getClass().getName(), equalTo( UpdateSchoolEnrichmentRecordAction.class.getName() ) );

        UpdateSchoolEnrichmentRecordAction updateSchoolEnrichmentRecordAction = (UpdateSchoolEnrichmentRecordAction)action;
        Assert.assertThat( updateSchoolEnrichmentRecordAction.getRawRepo(), is( rawRepo ) );
        Assert.assertThat( updateSchoolEnrichmentRecordAction.getRecord(), is( record ) );
        Assert.assertThat( updateSchoolEnrichmentRecordAction.getRecordsHandler(), is( recordsHandler ) );
        Assert.assertThat( updateSchoolEnrichmentRecordAction.getHoldingsItems(), is( holdingsItems ) );
        Assert.assertThat( updateSchoolEnrichmentRecordAction.getProviderId(), equalTo( providerId ) );
    }

    public static void assertStoreRecordAction( ServiceAction action, RawRepo rawRepo, MarcRecord record ) {
        assertThat( action, notNullValue() );
        assertThat( action.getClass().getName(), equalTo( StoreRecordAction.class.getName() ) );

        StoreRecordAction storeRecordAction = (StoreRecordAction)action;
        assertThat( storeRecordAction.getRawRepo(), is( rawRepo ) );
        assertThat( storeRecordAction.getRecord(), is( record ) );
        assertThat( storeRecordAction.getMimetype(), equalTo( UpdateCommonRecordAction.MIMETYPE ) );
    }

    public static void assertDeleteRecordAction( ServiceAction action, RawRepo rawRepo, MarcRecord record, String mimetype ) {
        assertThat( action, notNullValue() );
        assertThat( action.getClass().getName(), equalTo( DeleteRecordAction.class.getName() ) );

        DeleteRecordAction deleteRecordAction = (DeleteRecordAction) action;
        assertThat( deleteRecordAction.getRawRepo(), is( rawRepo ) );
        assertThat( deleteRecordAction.getRecord(), is( record ) );
        assertThat( deleteRecordAction.getMimetype(), equalTo( mimetype ) );
    }

    public static void assertCommonDeleteRecordAction( ServiceAction action, RawRepo rawRepo, MarcRecord record, LibraryRecordsHandler recordsHandler, HoldingsItems holdingsItems, String providerId ) {
        assertThat( action, notNullValue() );
        assertThat( action.getClass().getName(), equalTo( DeleteCommonRecordAction.class.getName() ) );

        DeleteCommonRecordAction deleteCommonRecordAction = (DeleteCommonRecordAction) action;
        assertThat( deleteCommonRecordAction.getRawRepo(), is( rawRepo ) );
        assertThat( deleteCommonRecordAction.getRecord(), is( record ) );
        assertThat( deleteCommonRecordAction.getRecordsHandler(), is( recordsHandler ) );
        assertThat( deleteCommonRecordAction.getHoldingsItems(), is( holdingsItems ) );
        assertThat( deleteCommonRecordAction.getProviderId(), equalTo( providerId ) );
    }

    public static void assertLinkRecordAction( ServiceAction action, RawRepo rawRepo, MarcRecord record, MarcRecord target ) {
        assertThat( action, notNullValue() );
        assertThat( action.getClass().getName(), equalTo( LinkRecordAction.class.getName() ) );

        LinkRecordAction linkRecordAction = (LinkRecordAction)action;
        assertThat( linkRecordAction.getRawRepo(), is( rawRepo ) );
        assertThat( linkRecordAction.getRecord(), is( record ) );

        String recordId = getRecordId( target );
        Integer agencyId = getAgencyId( target );
        assertThat( linkRecordAction.getLinkToRecordId(), equalTo( new RecordId( recordId, agencyId ) ) );
    }

    public static void assertRemoveLinksAction( ServiceAction action, RawRepo rawRepo, MarcRecord record ) {
        assertThat( action, notNullValue() );
        assertThat( action.getClass().getName(), equalTo( RemoveLinksAction.class.getName() ) );

        RemoveLinksAction removeLinksAction = (RemoveLinksAction)action;
        assertThat( removeLinksAction.getRawRepo(), is( rawRepo ) );
        assertThat( removeLinksAction.getRecord(), is( record ) );
    }

    public static void assertCreateEnrichmentAction( ServiceAction action, RawRepo rawRepo, MarcRecord commonRecord, Integer agencyId, String commonRecordId ) {
        assertThat( action, notNullValue() );
        assertThat( action.getClass().getName(), equalTo( CreateEnrichmentRecordWithClassificationsAction.class.getName() ) );

        CreateEnrichmentRecordWithClassificationsAction createEnrichmentRecordWithClassificationsAction = (CreateEnrichmentRecordWithClassificationsAction)action;
        assertThat( createEnrichmentRecordWithClassificationsAction.getRawRepo(), is( rawRepo ) );
        assertThat( createEnrichmentRecordWithClassificationsAction.getUpdatingCommonRecord(), equalTo( commonRecord ) );
        assertThat( createEnrichmentRecordWithClassificationsAction.getAgencyId(), equalTo( agencyId ) );
        assertThat( createEnrichmentRecordWithClassificationsAction.getCommonRecordId(), equalTo( commonRecordId) );
    }

    public static void assertUpdateEnrichmentRecordAction( ServiceAction action, RawRepo rawRepo, MarcRecord record, LibraryRecordsHandler recordsHandler, HoldingsItems holdingsItems, String providerId ) {
        assertThat( action, notNullValue() );
        assertThat( action.getClass().getName(), equalTo( UpdateEnrichmentRecordAction.class.getName() ) );

        UpdateEnrichmentRecordAction updateEnrichmentRecordAction = (UpdateEnrichmentRecordAction)action;
        assertThat( updateEnrichmentRecordAction.getRawRepo(), is( rawRepo ) );
        assertThat( updateEnrichmentRecordAction.getRecord(), equalTo( record ) );
        assertThat( updateEnrichmentRecordAction.getRecordsHandler(), is( recordsHandler ) );
        assertThat( updateEnrichmentRecordAction.getHoldingsItems(), is( holdingsItems ) );
        assertThat( updateEnrichmentRecordAction.getProviderId(), is( providerId ) );
    }

    public static void assertUpdateClassificationsInEnrichmentRecordAction( ServiceAction action, RawRepo rawRepo, MarcRecord commonRecord, MarcRecord record ) {
        assertThat( action, notNullValue() );
        assertThat( action.getClass().getName(), equalTo( UpdateClassificationsInEnrichmentRecordAction.class.getName() ) );

        UpdateClassificationsInEnrichmentRecordAction updateClassificationsInEnrichmentRecordAction = (UpdateClassificationsInEnrichmentRecordAction)action;
        assertThat( updateClassificationsInEnrichmentRecordAction.getRawRepo(), is( rawRepo ) );
        assertThat( updateClassificationsInEnrichmentRecordAction.getUpdatingCommonRecord(), equalTo( commonRecord ) );
        assertThat( updateClassificationsInEnrichmentRecordAction.getEnrichmentRecord(), equalTo( record ) );
    }

    public static void assertDoubleRecordCheckingAction( ServiceAction action, MarcRecord record, Scripter scripter ) {
        assertThat( action, notNullValue() );
        assertThat( action.getClass().getName(), equalTo( DoubleRecordCheckingAction.class.getName() ) );

        DoubleRecordCheckingAction doubleRecordCheckingAction = (DoubleRecordCheckingAction)action;
        Assert.assertThat( doubleRecordCheckingAction.getRecord(), is( record ) );
        Assert.assertThat( doubleRecordCheckingAction.getScripter(), is( scripter ) );
    }

    public static void assertEnqueueRecordAction( ServiceAction action, RawRepo rawRepo, MarcRecord record, String providerId, String mimetype ) {
        assertThat( action, notNullValue() );
        assertThat( action.getClass().getName(), equalTo( EnqueueRecordAction.class.getName() ) );

        EnqueueRecordAction enqueueRecordAction = (EnqueueRecordAction)action;
        assertThat( enqueueRecordAction.getRawRepo(), is( rawRepo ) );
        assertThat( enqueueRecordAction.getRecord(), is( record ) );
        assertThat( enqueueRecordAction.getProviderId(), equalTo( providerId ) );
        assertThat( enqueueRecordAction.getMimetype(), equalTo( mimetype ) );
    }

    public static void assertMoveEnrichmentRecordAction( ServiceAction action, RawRepo rawRepo, MarcRecord record, MarcRecord commonRecord, Properties settings ) {
        assertThat( action, notNullValue() );
        assertThat( action.getClass().getName(), equalTo( MoveEnrichmentRecordAction.class.getName() ) );

        MoveEnrichmentRecordAction moveEnrichmentRecordAction = (MoveEnrichmentRecordAction)action;
        assertThat( moveEnrichmentRecordAction.getRawRepo(), is( rawRepo ) );
        assertThat( moveEnrichmentRecordAction.getRecord(), is( record ) );
        assertThat( moveEnrichmentRecordAction.getCommonRecord(), is( commonRecord ) );
        assertThat( moveEnrichmentRecordAction.getSettings(), equalTo( settings ) );
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final XLogger logger = XLoggerFactory.getXLogger( AssertActionsUtil.class );
}
