//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordWriter;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.updateservice.auth.Authenticator;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.service.api.Authentication;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.*;
import dk.dbc.updateservice.ws.JNDIResources;
import org.junit.Test;

import java.util.*;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// TODO : Pt indeholder testposterne i de fleste test ikke noget der hænger sammen - det bør rettes op
// da det kan virke ret forvirrende. Senest hvis det skal opensources.
//-----------------------------------------------------------------------------
public class UpdateOperationActionTest {
    public UpdateOperationActionTest() {
        this.messages = ResourceBundles.getBundle( this, "actions" );
    }

    /**
     * Test performAction(): Update a local record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          <ol>
     *              <li>A local single record.</li>
     *          </ol>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update the record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>AuthenticateRecordAction: Authentication of the record</li>
     *              <li>UpdateLocalRecordAction: Update the local record</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_LocalRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( record );
        Integer agencyId = AssertActionsUtil.getAgencyId( record );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );

        Authenticator authenticator = mock( Authenticator.class );

        Authentication authentication = mock( Authentication.class );
        when( authentication.getGroupIdAut() ).thenReturn( GROUP_ID );
        when( authentication.getUserIdAut() ).thenReturn( USER_ID );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( agencyId ) ) ).thenReturn( false );
        when( rawRepo.recordExists( eq( recordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( false );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( agencyId.toString(), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS ) ).thenReturn( true );

        List<MarcRecord> rawRepoRecords = Arrays.asList( record );
        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.recordDataForRawRepo( eq( record ), eq( USER_ID ), eq( GROUP_ID ) ) ).thenReturn( rawRepoRecords );

        Scripter scripter = mock( Scripter.class );

        SolrService solrService = mock( SolrService.class );

        UpdateOperationAction instance = new UpdateOperationAction( rawRepo, record );
        instance.setAuthenticator( authenticator );
        instance.setAuthentication( authentication );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setSolrService( solrService );
        instance.setRecordsHandler( recordsHandler );
        instance.setScripter( scripter );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        assertThat( children.size(), is( 2 ) );

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction( iterator.next(), record, authenticator, authentication );
        AssertActionsUtil.assertUpdateLocalRecordAction( iterator.next(), rawRepo, record, holdingsItems );
    }

    /**
     * Test performAction(): Update an enrichment record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          <ol>
     *              <li>A local single record.</li>
     *              <li>The library for the record being updated has the feature 'create_enrichments'</li>
     *          </ol>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update the record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>AuthenticateRecordAction: Authentication of the record</li>
     *              <li>UpdateEnrichmentRecordAction: Update the local record</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_EnrichmentRecord_WithFeature_CreateEnrichments() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( record );
        Integer agencyId = AssertActionsUtil.getAgencyId( record );

        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE );
        Integer enrichmentAgencyId = AssertActionsUtil.getAgencyId( enrichmentRecord );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );

        Authenticator authenticator = mock( Authenticator.class );

        Authentication authentication = mock( Authentication.class );
        when( authentication.getGroupIdAut() ).thenReturn( GROUP_ID );
        when( authentication.getUserIdAut() ).thenReturn( USER_ID );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( recordId ), eq( enrichmentAgencyId ) ) ).thenReturn( false );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( eq( GROUP_ID ), eq( LibraryRuleHandler.Rule.CREATE_ENRICHMENTS ) ) ).thenReturn( true );

        List<MarcRecord> rawRepoRecords = Arrays.asList( enrichmentRecord );
        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.recordDataForRawRepo( eq( enrichmentRecord ), eq( USER_ID ), eq( GROUP_ID ) ) ).thenReturn( rawRepoRecords );

        Scripter scripter = mock( Scripter.class );

        SolrService solrService = mock( SolrService.class );

        UpdateOperationAction instance = new UpdateOperationAction( rawRepo, enrichmentRecord );
        instance.setAuthenticator( authenticator );
        instance.setAuthentication( authentication );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setSolrService( solrService );
        instance.setRecordsHandler( recordsHandler );
        instance.setScripter( scripter );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        assertThat( children.size(), is( 2 ) );

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction( iterator.next(), enrichmentRecord, authenticator, authentication );
        AssertActionsUtil.assertUpdateEnrichmentRecordAction( iterator.next(), rawRepo, enrichmentRecord, recordsHandler, holdingsItems );
    }

    /**
     * Test performAction(): Update an enrichment record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          <ol>
     *              <li>A local single record.</li>
     *              <li>The library for the record being updated has not the feature 'create_enrichments'</li>
     *          </ol>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update the record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>AuthenticateRecordAction: Authentication of the record</li>
     *              <li>UpdateLocalRecordAction: Update the local record</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_EnrichmentRecord_NotWithFeature_CreateEnrichments() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( record );
        Integer agencyId = AssertActionsUtil.getAgencyId( record );

        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE );
        Integer enrichmentAgencyId = AssertActionsUtil.getAgencyId( enrichmentRecord );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );

        Authenticator authenticator = mock( Authenticator.class );

        Authentication authentication = mock( Authentication.class );
        when( authentication.getGroupIdAut() ).thenReturn( GROUP_ID );
        when( authentication.getUserIdAut() ).thenReturn( USER_ID );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( recordId ), eq( enrichmentAgencyId ) ) ).thenReturn( false );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( enrichmentAgencyId.toString(), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS ) ).thenReturn( false );

        List<MarcRecord> rawRepoRecords = Arrays.asList( enrichmentRecord );
        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.recordDataForRawRepo( eq( enrichmentRecord ), eq( USER_ID ), eq( GROUP_ID ) ) ).thenReturn( rawRepoRecords );

        Scripter scripter = mock( Scripter.class );

        SolrService solrService = mock( SolrService.class );

        UpdateOperationAction instance = new UpdateOperationAction( rawRepo, enrichmentRecord );
        instance.setAuthenticator( authenticator );
        instance.setAuthentication( authentication );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setSolrService( solrService );
        instance.setRecordsHandler( recordsHandler );
        instance.setScripter( scripter );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        assertThat( children.size(), is( 2 ) );

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction( iterator.next(), enrichmentRecord, authenticator, authentication );
        AssertActionsUtil.assertUpdateLocalRecordAction( iterator.next(), rawRepo, enrichmentRecord, holdingsItems );
    }

    /**
     * Test performAction(): Create a new common record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          Empty rawrepo.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Create a new common record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>AuthenticateRecordAction: Authentication of the record</li>
     *              <li>UpdateCommonRecordAction: Update common record</li>
     *              <li>UpdateEnrichmentRecordAction: Update DBC enrichment record</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_CreateCommonRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( record );
        Integer agencyId = AssertActionsUtil.getAgencyId( record );

        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE );
        MarcRecordWriter writer = new MarcRecordWriter( enrichmentRecord );
        writer.addOrReplaceSubfield( "001", "b", RawRepo.COMMON_LIBRARY.toString() );
        Integer enrichmentAgencyId = AssertActionsUtil.getAgencyId( enrichmentRecord );

        MarcRecord updateRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        MarcRecordWriter updwriter = new MarcRecordWriter( updateRecord );
        updwriter.addOrReplaceSubfield( "001", "b", RawRepo.COMMON_LIBRARY.toString() );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );

        Authenticator authenticator = mock( Authenticator.class );

        Authentication authentication = mock( Authentication.class );
        when( authentication.getGroupIdAut() ).thenReturn( GROUP_ID );
        when( authentication.getUserIdAut() ).thenReturn( USER_ID );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( agencyId ) ) ).thenReturn( false );
        when( rawRepo.recordExists( eq( recordId ), eq( enrichmentAgencyId ) ) ).thenReturn( false );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( agencyId.toString(), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS ) ).thenReturn( true );

        List<MarcRecord> rawRepoRecords = Arrays.asList( record, enrichmentRecord );
        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.recordDataForRawRepo( eq( updateRecord ), eq( USER_ID ), eq( GROUP_ID ) ) ).thenReturn( rawRepoRecords );

        Scripter scripter = mock( Scripter.class );

        SolrService solrService = mock( SolrService.class );

        UpdateOperationAction instance = new UpdateOperationAction( rawRepo, updateRecord );
        instance.setAuthenticator( authenticator );
        instance.setAuthentication( authentication );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setSolrService( solrService );
        instance.setRecordsHandler( recordsHandler );
        instance.setScripter( scripter );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction( iterator.next(), updateRecord, authenticator, authentication );
        AssertActionsUtil.assertUpdateCommonRecordAction( iterator.next(), rawRepo, record, Integer.valueOf( GROUP_ID, 10 ), recordsHandler, holdingsItems, openAgencyService );
        AssertActionsUtil.assertUpdateEnrichmentRecordAction( iterator.next(), rawRepo, enrichmentRecord, recordsHandler, holdingsItems );

        assertThat( iterator.hasNext(), is( false ) );

        updwriter.addOrReplaceSubfield( "996", "a", "810010");

        UpdateOperationAction instance1 = new UpdateOperationAction( rawRepo, updateRecord );
        instance1.setAuthenticator( authenticator );
        instance1.setAuthentication( authentication );
        instance1.setHoldingsItems( holdingsItems );
        instance1.setOpenAgencyService( openAgencyService );
        instance1.setSolrService( solrService );
        instance1.setRecordsHandler( recordsHandler );
        instance1.setScripter( scripter );
        instance1.setSettings( settings );

        assertThat( instance1.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children1 = instance1.children();

        ListIterator<ServiceAction> iterator1 = children1.listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction( iterator1.next(), updateRecord, authenticator, authentication );
        AssertActionsUtil.assertUpdateCommonRecordAction( iterator1.next(), rawRepo, record, Integer.valueOf( GROUP_ID, 10 ), recordsHandler, holdingsItems, openAgencyService );
        AssertActionsUtil.assertUpdateEnrichmentRecordAction( iterator1.next(), rawRepo, enrichmentRecord, recordsHandler, holdingsItems );
        AssertActionsUtil.assertDoubleRecordCheckingAction( iterator1.next(), updateRecord, scripter );

        assertThat( iterator1.hasNext(), is( false ) );
    }

    /**
     * Test performAction(): Deletes a common record that exists.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with a common record.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Try to delete the record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>AuthenticateRecordAction: Authentication of the record</li>
     *              <li>UpdateCommonRecordAction: Update common record</li>
     *              <li>UpdateEnrichmentRecordAction: Update DBC enrichment record</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_DeleteCommonRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( record );
        Integer agencyId = AssertActionsUtil.getAgencyId( record );

        MarcRecordWriter recordWriter = new MarcRecordWriter( record );
        recordWriter.markForDeletion();

        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE );
        MarcRecordWriter enrichmentWriter = new MarcRecordWriter( enrichmentRecord );
        enrichmentWriter.addOrReplaceSubfield( "001", "b", RawRepo.COMMON_LIBRARY.toString() );
        Integer enrichmentAgencyId = AssertActionsUtil.getAgencyId( enrichmentRecord );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );

        Authenticator authenticator = mock( Authenticator.class );

        Authentication authentication = mock( Authentication.class );
        when( authentication.getGroupIdAut() ).thenReturn( GROUP_ID );
        when( authentication.getUserIdAut() ).thenReturn( USER_ID );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( recordId ), eq( enrichmentAgencyId ) ) ).thenReturn( false );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( agencyId.toString(), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS ) ).thenReturn( true );

        List<MarcRecord> rawRepoRecords = Arrays.asList( record, enrichmentRecord );
        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.recordDataForRawRepo( eq( record ), eq( USER_ID ), eq( GROUP_ID ) ) ).thenReturn( rawRepoRecords );

        Scripter scripter = mock( Scripter.class );

        SolrService solrService = mock( SolrService.class );

        UpdateOperationAction instance = new UpdateOperationAction( rawRepo, record );
        instance.setAuthenticator( authenticator );
        instance.setAuthentication( authentication );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setSolrService( solrService );
        instance.setRecordsHandler( recordsHandler );
        instance.setScripter( scripter );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction( iterator.next(), record, authenticator, authentication );
        AssertActionsUtil.assertUpdateCommonRecordAction( iterator.next(), rawRepo, record, Integer.valueOf( GROUP_ID, 10 ), recordsHandler, holdingsItems, openAgencyService );
        AssertActionsUtil.assertUpdateEnrichmentRecordAction( iterator.next(), rawRepo, enrichmentRecord, recordsHandler, holdingsItems );

        assertThat( iterator.hasNext(), is( false ) );
    }

    /**
     * Test performAction(): Deletes a common record that does not exist.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          An empty rawrepo.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Try to delete a common record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_DeleteCommonRecord_NotExist() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( record );
        Integer agencyId = AssertActionsUtil.getAgencyId( record );

        MarcRecordWriter recordWriter = new MarcRecordWriter( record );
        recordWriter.markForDeletion();

        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE );
        MarcRecordWriter enrichmentWriter = new MarcRecordWriter( enrichmentRecord );
        enrichmentWriter.addOrReplaceSubfield( "001", "b", RawRepo.COMMON_LIBRARY.toString() );
        Integer enrichmentAgencyId = AssertActionsUtil.getAgencyId( enrichmentRecord );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );

        Authenticator authenticator = mock( Authenticator.class );

        Authentication authentication = mock( Authentication.class );
        when( authentication.getGroupIdAut() ).thenReturn( GROUP_ID );
        when( authentication.getUserIdAut() ).thenReturn( USER_ID );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( agencyId ) ) ).thenReturn( false );
        when( rawRepo.recordExists( eq( recordId ), eq( enrichmentAgencyId ) ) ).thenReturn( false );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( agencyId.toString(), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS ) ).thenReturn( true );

        List<MarcRecord> rawRepoRecords = Arrays.asList( record, enrichmentRecord );
        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.recordDataForRawRepo( eq( record ), eq( USER_ID ), eq( GROUP_ID ) ) ).thenReturn( rawRepoRecords );

        Scripter scripter = mock( Scripter.class );

        SolrService solrService = mock( SolrService.class );

        UpdateOperationAction instance = new UpdateOperationAction( rawRepo, record );
        instance.setAuthenticator( authenticator );
        instance.setAuthentication( authentication );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setSolrService( solrService );
        instance.setRecordsHandler( recordsHandler );
        instance.setScripter( scripter );
        instance.setSettings( settings );

        String message = messages.getString( "operation.delete.non.existing.record" );
        assertThat( instance.performAction(), equalTo( ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message ) ) );
    }

    /**
     * Test performAction(): Create a new common school enrichment record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          Rawrepo with:
     *          <ul>
     *              <li>A common record <code>c1</code></li>
     *              <li>A common school record <code>s1</code></li>
     *          </ul>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update the common school record <code>s1</code> with new data.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>UpdateSchoolCommonRecord: Update the common school record</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_CreateCommonSchoolEnrichment() throws Exception {
        MarcRecord commonRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( commonRecord );

        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SCHOOL_RECORD_RESOURCE );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );

        Authenticator authenticator = mock( Authenticator.class );

        Authentication authentication = mock( Authentication.class );
        when( authentication.getGroupIdAut() ).thenReturn( GROUP_ID );
        when( authentication.getUserIdAut() ).thenReturn( USER_ID );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( recordId ), eq( RawRepo.SCHOOL_COMMON_AGENCY ) ) ).thenReturn( true );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );

        List<MarcRecord> rawRepoRecords = Arrays.asList( record );
        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.recordDataForRawRepo( eq( record ), eq( USER_ID ), eq( GROUP_ID ) ) ).thenReturn( rawRepoRecords );

        Scripter scripter = mock( Scripter.class );

        SolrService solrService = mock( SolrService.class );

        UpdateOperationAction instance = new UpdateOperationAction( rawRepo, record );
        instance.setAuthenticator( authenticator );
        instance.setAuthentication( authentication );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setSolrService( solrService );
        instance.setRecordsHandler( recordsHandler );
        instance.setScripter( scripter );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction( iterator.next(), record, authenticator, authentication );
        AssertActionsUtil.assertSchoolCommonRecordAction( iterator.next(), rawRepo, record, recordsHandler, holdingsItems, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ) );

        assertThat( iterator.hasNext(), is( false ) );
    }

    /**
     * Test performAction(): Create a new school enrichment record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          Rawrepo with:
     *          <ul>
     *              <li>A common record <code>c1</code></li>
     *          </ul>
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update the school enrichment record <code>s1</code> with new data.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>UpdateSchoolEnrichmentRecordAction: Update the common school record</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_CreateSchoolEnrichment() throws Exception {

        MarcRecord commonRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( commonRecord );

        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.SCHOOL_RECORD_RESOURCE );

        String userId = USER_ID;
        String groupId = AssertActionsUtil.getAgencyId( record ).toString();

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );

        Authenticator authenticator = mock( Authenticator.class );

        Authentication authentication = mock( Authentication.class );
        when( authentication.getGroupIdAut() ).thenReturn( groupId );
        when( authentication.getUserIdAut() ).thenReturn( userId );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( recordId ), eq( RawRepo.SCHOOL_COMMON_AGENCY ) ) ).thenReturn( false );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );

        OpenAgencyService openAgencyService = mock( OpenAgencyService.class );
        when( openAgencyService.hasFeature( groupId, LibraryRuleHandler.Rule.CREATE_ENRICHMENTS ) ).thenReturn( true );

        List<MarcRecord> rawRepoRecords = Arrays.asList( record );
        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.recordDataForRawRepo( eq( record ), eq( userId ), eq( groupId ) ) ).thenReturn( rawRepoRecords );

        Scripter scripter = mock( Scripter.class );

        SolrService solrService = mock( SolrService.class );

        UpdateOperationAction instance = new UpdateOperationAction( rawRepo, record );
        instance.setAuthenticator( authenticator );
        instance.setAuthentication( authentication );
        instance.setHoldingsItems( holdingsItems );
        instance.setOpenAgencyService( openAgencyService );
        instance.setSolrService( solrService );
        instance.setRecordsHandler( recordsHandler );
        instance.setScripter( scripter );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction( iterator.next(), record, authenticator, authentication );
        AssertActionsUtil.assertSchoolEnrichmentRecordAction( iterator.next(), rawRepo, record, recordsHandler, holdingsItems, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ) );

        assertThat( iterator.hasNext(), is( false ) );
    }

    private String GROUP_ID = "700100";
    private String USER_ID = "netpunkt";

    private ResourceBundle messages;
}
