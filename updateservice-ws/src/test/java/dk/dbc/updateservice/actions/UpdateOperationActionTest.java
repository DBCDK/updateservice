//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.updateservice.auth.Authenticator;
import dk.dbc.updateservice.service.api.Authentication;
import dk.dbc.updateservice.update.HoldingsItems;
import dk.dbc.updateservice.update.LibraryRecordsHandler;
import dk.dbc.updateservice.update.RawRepo;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

//-----------------------------------------------------------------------------
public class UpdateOperationActionTest {
    /**
     * Test performAction(): Update a local record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A local single record.
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

        Authenticator authenticator = mock( Authenticator.class );

        Authentication authentication = mock( Authentication.class );
        when( authentication.getGroupIdAut() ).thenReturn( GROUP_ID );
        when( authentication.getUserIdAut() ).thenReturn( USER_ID );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( agencyId ) ) ).thenReturn( false );
        when( rawRepo.recordExists( eq( recordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( false );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );

        List<MarcRecord> rawRepoRecords = Arrays.asList( record );
        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.recordDataForRawRepo( eq( record ), eq( USER_ID ), eq( GROUP_ID ) ) ).thenReturn( rawRepoRecords );

        UpdateOperationAction instance = new UpdateOperationAction( rawRepo, record );
        instance.setAuthenticator( authenticator );
        instance.setAuthentication( authentication );
        instance.setHoldingsItems( holdingsItems );
        instance.setRecordsHandler( recordsHandler );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        assertThat( children.size(), is( 2 ) );

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction( iterator.next(), record, authenticator, authentication );
        AssertActionsUtil.assertUpdateLocalRecordAction( iterator.next(), rawRepo, record, holdingsItems );
    }

    /**
     * Test performAction(): Update a local record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A local single record.
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
    public void testPerformAction_EnrichmentRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        String recordId = AssertActionsUtil.getRecordId( record );
        Integer agencyId = AssertActionsUtil.getAgencyId( record );

        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE );
        Integer enrichmentAgencyId = AssertActionsUtil.getAgencyId( enrichmentRecord );

        Authenticator authenticator = mock( Authenticator.class );

        Authentication authentication = mock( Authentication.class );
        when( authentication.getGroupIdAut() ).thenReturn( GROUP_ID );
        when( authentication.getUserIdAut() ).thenReturn( USER_ID );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( recordId ), eq( enrichmentAgencyId ) ) ).thenReturn( false );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );

        List<MarcRecord> rawRepoRecords = Arrays.asList( enrichmentRecord );
        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.recordDataForRawRepo( eq( enrichmentRecord ), eq( USER_ID ), eq( GROUP_ID ) ) ).thenReturn( rawRepoRecords );

        UpdateOperationAction instance = new UpdateOperationAction( rawRepo, enrichmentRecord );
        instance.setAuthenticator( authenticator );
        instance.setAuthentication( authentication );
        instance.setHoldingsItems( holdingsItems );
        instance.setRecordsHandler( recordsHandler );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        assertThat( children.size(), is( 2 ) );

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction( iterator.next(), enrichmentRecord, authenticator, authentication );
        AssertActionsUtil.assertUpdateEnrichmentRecordAction( iterator.next(), rawRepo, enrichmentRecord, recordsHandler, holdingsItems );
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
        Integer enrichmentAgencyId = AssertActionsUtil.getAgencyId( enrichmentRecord );

        Authenticator authenticator = mock( Authenticator.class );

        Authentication authentication = mock( Authentication.class );
        when( authentication.getGroupIdAut() ).thenReturn( GROUP_ID );
        when( authentication.getUserIdAut() ).thenReturn( USER_ID );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( recordId ), eq( agencyId ) ) ).thenReturn( false );
        when( rawRepo.recordExists( eq( recordId ), eq( enrichmentAgencyId ) ) ).thenReturn( false );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );

        List<MarcRecord> rawRepoRecords = Arrays.asList( record, enrichmentRecord );
        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.recordDataForRawRepo( eq( record ), eq( USER_ID ), eq( GROUP_ID ) ) ).thenReturn( rawRepoRecords );

        UpdateOperationAction instance = new UpdateOperationAction( rawRepo, record );
        instance.setAuthenticator( authenticator );
        instance.setAuthentication( authentication );
        instance.setHoldingsItems( holdingsItems );
        instance.setRecordsHandler( recordsHandler );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        assertThat( children.size(), is( 3 ) );

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction( iterator.next(), record, authenticator, authentication );
        AssertActionsUtil.assertUpdateCommonRecordAction( iterator.next(), rawRepo, record, Integer.valueOf( GROUP_ID, 10 ), recordsHandler, holdingsItems );
        AssertActionsUtil.assertUpdateEnrichmentRecordAction( iterator.next(), rawRepo, enrichmentRecord, recordsHandler, holdingsItems );
    }

    private String GROUP_ID = "700100";
    private String USER_ID = "netpunkt";
}
