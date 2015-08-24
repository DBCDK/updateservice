//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------

import dk.dbc.iscrum.records.MarcReader;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordFactory;
import dk.dbc.iscrum.records.MarcWriter;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.auth.Authenticator;
import dk.dbc.updateservice.service.api.Authentication;
import dk.dbc.updateservice.update.*;
import org.junit.Assert;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertTrue;

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
    public static final String ENRICHMENT_SINGLE_RECORD_RESOURCE = "enrichment.marc";
    public static final String LOCAL_SINGLE_RECORD_RESOURCE = "book.marc";

    //-------------------------------------------------------------------------
    //              Records
    //-------------------------------------------------------------------------

    public static MarcRecord loadRecord( String filename ) throws IOException {
        InputStream is = AssertActionsUtil.class.getResourceAsStream( "/dk/dbc/updateservice/actions/" + filename );
        return MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );
    }

    public static MarcRecord loadRecordAndMarkForDeletion( String filename ) throws IOException {
        MarcRecord record = loadRecord( filename );
        MarcWriter.addOrReplaceSubfield( record, "004", "r", "d" );

        return record;
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
        return MarcReader.getRecordValue( record, "001", "a" );
    }

    public static Integer getAgencyId( MarcRecord record ) {
        return Integer.parseInt( MarcReader.getRecordValue( record, "001", "b" ), 10 );
    }

    //-------------------------------------------------------------------------
    //              Asserts
    //-------------------------------------------------------------------------

    public static void assertAuthenticateRecordAction( ServiceAction action, MarcRecord record, Authenticator authenticator, Authentication authentication ) {
        assertTrue( action.getClass() == AuthenticateRecordAction.class );

        AuthenticateRecordAction authenticateRecordAction = (AuthenticateRecordAction)action;
        Assert.assertThat( authenticateRecordAction.getRecord(), is( record ) );
        Assert.assertThat( authenticateRecordAction.getAuthenticator(), is( authenticator ) );
        Assert.assertThat( authenticateRecordAction.getAuthentication(), is( authentication ) );
    }

    public static void assertUpdateLocalRecordAction( ServiceAction action, RawRepo rawRepo, MarcRecord record, HoldingsItems holdingsItems ) {
        assertTrue( action.getClass() == UpdateLocalRecordAction.class );

        UpdateLocalRecordAction updateLocalRecordAction = (UpdateLocalRecordAction)action;
        Assert.assertThat( updateLocalRecordAction.getRawRepo(), is( rawRepo ) );
        Assert.assertThat( updateLocalRecordAction.getRecord(), is( record ) );
        Assert.assertThat( updateLocalRecordAction.getHoldingsItems(), is( holdingsItems ) );
    }

    public static void assertUpdateEnrichmentRecordAction( ServiceAction action, RawRepo rawRepo, MarcRecord record, LibraryRecordsHandler recordsHandler, HoldingsItems holdingsItems ) {
        assertTrue( action.getClass() == UpdateEnrichmentRecordAction.class );

        UpdateEnrichmentRecordAction updateEnrichmentRecordAction = (UpdateEnrichmentRecordAction)action;
        Assert.assertThat( updateEnrichmentRecordAction.getRawRepo(), is( rawRepo ) );
        Assert.assertThat( updateEnrichmentRecordAction.getRecord(), is( record ) );
        Assert.assertThat( updateEnrichmentRecordAction.getRecordsHandler(), is( recordsHandler ) );
        Assert.assertThat( updateEnrichmentRecordAction.getHoldingsItems(), is( holdingsItems ) );
    }

    public static void assertUpdateCommonRecordAction( ServiceAction action, RawRepo rawRepo, MarcRecord record, Integer groupId, LibraryRecordsHandler recordsHandler, HoldingsItems holdingsItems ) {
        assertTrue( action.getClass() == UpdateCommonRecordAction.class );

        UpdateCommonRecordAction updateCommonRecordAction = (UpdateCommonRecordAction)action;
        Assert.assertThat( updateCommonRecordAction.getRawRepo(), is( rawRepo ) );
        Assert.assertThat( updateCommonRecordAction.getRecord(), is( record ) );
        Assert.assertThat( updateCommonRecordAction.getGroupId(), equalTo( groupId ) );
        Assert.assertThat( updateCommonRecordAction.getRecordsHandler(), is( recordsHandler ) );
        Assert.assertThat( updateCommonRecordAction.getHoldingsItems(), is( holdingsItems ) );
    }

    public static void assertStoreRecordAction( ServiceAction action, RawRepo rawRepo, MarcRecord record ) {
        assertTrue( action.getClass() == StoreRecordAction.class );

        StoreRecordAction storeRecordAction = (StoreRecordAction)action;
        assertThat( storeRecordAction.getRawRepo(), is( rawRepo ) );
        assertThat( storeRecordAction.getRecord(), is( record ) );
        assertThat( storeRecordAction.getMimetype(), equalTo( UpdateCommonRecordAction.MIMETYPE ) );
    }

    public static void assertDeleteRecordAction( ServiceAction action, RawRepo rawRepo, MarcRecord record ) {
        assertTrue( action.getClass() == DeleteRecordAction.class );

        DeleteRecordAction deleteRecordAction = (DeleteRecordAction) action;
        assertThat( deleteRecordAction.getRawRepo(), is( rawRepo ) );
        assertThat( deleteRecordAction.getRecord(), is( record ) );
        assertThat( deleteRecordAction.getMimetype(), equalTo( UpdateCommonRecordAction.MIMETYPE ) );
    }

    public static void assertLinkRecordAction( ServiceAction action, RawRepo rawRepo, MarcRecord record, MarcRecord target ) {
        assertTrue( action.getClass() == LinkRecordAction.class );

        LinkRecordAction linkRecordAction = (LinkRecordAction)action;
        assertThat( linkRecordAction.getRawRepo(), is( rawRepo ) );
        assertThat( linkRecordAction.getRecord(), is( record ) );

        String recordId = getRecordId( target );
        Integer agencyId = getAgencyId( target );
        assertThat( linkRecordAction.getLinkToRecordId(), equalTo( new RecordId( recordId, agencyId ) ) );
    }

    public static void assertRemoveLinksAction( ServiceAction action, RawRepo rawRepo, MarcRecord record ) {
        assertTrue( action.getClass() == RemoveLinksAction.class );

        RemoveLinksAction removeLinksAction = (RemoveLinksAction)action;
        assertThat( removeLinksAction.getRawRepo(), is( rawRepo ) );
        assertThat( removeLinksAction.getRecord(), is( record ) );
    }

    public static void assertCreateEnrichmentAction( ServiceAction action, RawRepo rawRepo, MarcRecord commonRecord, Integer agencyId ) {
        assertTrue( action.getClass() == CreateEnrichmentRecordWithClassificationsAction.class );

        CreateEnrichmentRecordWithClassificationsAction createEnrichmentRecordWithClassificationsAction = (CreateEnrichmentRecordWithClassificationsAction)action;
        assertThat( createEnrichmentRecordWithClassificationsAction.getRawRepo(), is( rawRepo ) );
        assertThat( createEnrichmentRecordWithClassificationsAction.getUpdatingCommonRecord(), equalTo( commonRecord ) );
        assertThat( createEnrichmentRecordWithClassificationsAction.getAgencyId(), equalTo( agencyId ) );
    }

    public static void assertUpdateEnrichmentAction( ServiceAction action, RawRepo rawRepo, MarcRecord commonRecord, MarcRecord record ) {
        assertTrue( action.getClass() == UpdateClassificationsInEnrichmentRecordAction.class );

        UpdateClassificationsInEnrichmentRecordAction updateClassificationsInEnrichmentRecordAction = (UpdateClassificationsInEnrichmentRecordAction)action;
        assertThat( updateClassificationsInEnrichmentRecordAction.getRawRepo(), is( rawRepo ) );
        assertThat( updateClassificationsInEnrichmentRecordAction.getUpdatingCommonRecord(), equalTo( commonRecord ) );
        assertThat( updateClassificationsInEnrichmentRecordAction.getEnrichmentRecord(), equalTo( record ) );
    }

    public static void assertEnqueueRecordAction( ServiceAction action, RawRepo rawRepo, MarcRecord record, String providerId, String mimetype ) {
        assertTrue( action.getClass() == EnqueueRecordAction.class );

        EnqueueRecordAction enqueueRecordAction = (EnqueueRecordAction)action;
        assertThat( enqueueRecordAction.getRawRepo(), is( rawRepo ) );
        assertThat( enqueueRecordAction.getRecord(), is( record ) );
        assertThat( enqueueRecordAction.getProviderId(), equalTo( providerId ) );
        assertThat( enqueueRecordAction.getMimetype(), equalTo( mimetype ) );
    }
}
