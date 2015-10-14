//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.updateservice.update.HoldingsItems;
import dk.dbc.updateservice.update.LibraryRecordsHandler;
import dk.dbc.updateservice.update.RawRepo;
import org.junit.Assert;
import org.junit.Test;

import java.util.ListIterator;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

//-----------------------------------------------------------------------------
public class MoveEnrichmentRecordActionTest {
    /**
     * Test MoveEnrichmentRecordAction.performAction(): Move enrichment to new common
     * record that exists.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with a common records <code>c1</code> and <code>c2</code> and an enrichment
     *          record <code>e1</code>.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Perform actions to move <code>e1</code> from <code>c1</code> to <code>c2</code>.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          The enrichment record is moved to <code>c2</code> by creating the following child actions:
     *          <ol>
     *              <li>UpdateEnrichmentRecordAction: Store the enrichment record and associate it with <code>c2</code></li>
     *              <li>UpdateEnrichmentRecordAction: To delete the old enrichment record in rawrepo.</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_Success() throws Exception {
        final String c1RecordId = "1 234 567 8";
        final String c2RecordId = "2 345 678 9";

        MarcRecord c1 = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c1RecordId );
        MarcRecord c2 = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE, c2RecordId );
        MarcRecord e1 = AssertActionsUtil.loadRecord( AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE, c1RecordId );

        String e1RecordId = AssertActionsUtil.getRecordId( e1 );
        Integer e1AgencyId = AssertActionsUtil.getAgencyId( e1 );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( c1RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( c2RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( e1RecordId ), eq( e1AgencyId ) ) ).thenReturn( true );
        when( rawRepo.fetchRecord( eq( c1RecordId ), eq( RawRepo.RAWREPO_COMMON_LIBRARY ) ) ).thenReturn( AssertActionsUtil.createRawRepoRecord( c1, MarcXChangeMimeType.MARCXCHANGE ) );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        HoldingsItems holdingsItems = mock( HoldingsItems.class );

        MoveEnrichmentRecordAction instance = new MoveEnrichmentRecordAction( rawRepo, e1 );
        instance.setCommonRecord( c2 );
        instance.setRecordsHandler( recordsHandler );
        instance.setHoldingsItems( holdingsItems );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );
        ListIterator<ServiceAction> iterator = instance.children.listIterator();

        MarcRecord e1Deleted = AssertActionsUtil.loadRecordAndMarkForDeletion( AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE, c1RecordId );
        AssertActionsUtil.assertUpdateEnrichmentRecordAction( iterator.next(), rawRepo, e1Deleted, recordsHandler, holdingsItems, instance.getProviderId() );

        MarcRecord e1Moved = AssertActionsUtil.loadRecord( AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE, c2RecordId );
        AssertActionsUtil.assertUpdateClassificationsInEnrichmentRecordAction( iterator.next(), rawRepo, c1, e1Moved );

        Assert.assertThat( iterator.hasNext(), is( false ) );
    }
}
