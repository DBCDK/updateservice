//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcReader;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordFactory;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.update.LibraryRecordsHandler;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

//-----------------------------------------------------------------------------
public class CreateEnrichmentRecordWithClassificationsActionTest {
    /**
     * Test CreateEnrichmentRecordAction.performAction(): Create enrichment record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with a common record.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Perform actions to create a new enrichment record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>StoreRecordAction: Store the record</li>
     *              <li>LinkRecordAction: Link the new enrichment record to the common record</li>
     *              <li>EnqueueRecordAction: Put the enrichment record in queue</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_CommonRecordIdIsNull() throws Exception {
        InputStream is = getClass().getResourceAsStream( COMMON_RECORD_RESOURCE );
        MarcRecord commonRecord = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );

        is = getClass().getResourceAsStream( ENRICHMENT_RECORD_RESOURCE );
        MarcRecord enrichmentRecord = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );

        String recordId = MarcReader.getRecordValue( enrichmentRecord, "001", "a" );
        Integer agencyId = Integer.valueOf( MarcReader.getRecordValue( enrichmentRecord, "001", "b" ), 10 );

        RawRepo rawRepo = mock( RawRepo.class );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.createLibraryExtendedRecord( isNull( MarcRecord.class ), eq( commonRecord ), eq( agencyId ) ) ).thenReturn( enrichmentRecord );

        CreateEnrichmentRecordWithClassificationsAction instance = new CreateEnrichmentRecordWithClassificationsAction( rawRepo, agencyId );
        instance.setRecordsHandler( recordsHandler );
        instance.setCurrentCommonRecord( null );
        instance.setUpdatingCommonRecord( commonRecord );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 3 ) );

        ServiceAction child = children.get( 0 );
        assertTrue( child.getClass() == StoreRecordAction.class );

        StoreRecordAction storeRecordAction = (StoreRecordAction)child;
        assertThat( storeRecordAction.getRawRepo(), is( rawRepo ) );
        assertThat( storeRecordAction.getRecord(), is( enrichmentRecord ) );
        assertThat( storeRecordAction.getMimetype(), equalTo( UpdateEnrichmentRecordAction.MIMETYPE ) );

        child = children.get( 1 );
        assertTrue( child.getClass() == LinkRecordAction.class );

        LinkRecordAction linkRecordAction = (LinkRecordAction)child;
        assertThat( linkRecordAction.getRawRepo(), is( rawRepo ) );
        assertThat( linkRecordAction.getRecord(), is( enrichmentRecord ) );
        assertThat( linkRecordAction.getLinkToRecordId(), equalTo( new RecordId( recordId, RawRepo.RAWREPO_COMMON_LIBRARY ) ) );

        child = children.get( 2 );
        assertTrue( child.getClass() == EnqueueRecordAction.class );

        EnqueueRecordAction enqueueRecordAction = (EnqueueRecordAction)child;
        assertThat( enqueueRecordAction.getRawRepo(), is( rawRepo ) );
        assertThat( enqueueRecordAction.getRecord(), is( enrichmentRecord ) );
        assertThat( enqueueRecordAction.getMimetype(), equalTo( UpdateEnrichmentRecordAction.MIMETYPE ) );
    }

    /**
     * Test CreateEnrichmentRecordAction.performAction(): Create enrichment record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with a common record.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Perform actions to create a new enrichment record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>StoreRecordAction: Store the record</li>
     *              <li>LinkRecordAction: Link the new enrichment record to the common record</li>
     *              <li>EnqueueRecordAction: Put the enrichment record in queue</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_CommonRecordIdIsSet() throws Exception {
        String commonRecordId = "3 456 789 4";

        MarcRecord commonRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE );
        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE, commonRecordId );

        String recordId = MarcReader.getRecordValue( enrichmentRecord, "001", "a" );
        Integer agencyId = Integer.valueOf( MarcReader.getRecordValue( enrichmentRecord, "001", "b" ), 10 );

        RawRepo rawRepo = mock( RawRepo.class );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.createLibraryExtendedRecord( isNull( MarcRecord.class ), eq( commonRecord ), eq( agencyId ) ) ).thenReturn( enrichmentRecord );

        CreateEnrichmentRecordWithClassificationsAction instance = new CreateEnrichmentRecordWithClassificationsAction( rawRepo, agencyId );
        instance.setRecordsHandler( recordsHandler );
        instance.setCurrentCommonRecord( null );
        instance.setUpdatingCommonRecord( commonRecord );
        instance.setCommonRecordId( commonRecordId );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 3 ) );

        ServiceAction child = children.get( 0 );
        assertTrue( child.getClass() == StoreRecordAction.class );

        StoreRecordAction storeRecordAction = (StoreRecordAction)child;
        assertThat( storeRecordAction.getRawRepo(), is( rawRepo ) );
        assertThat( storeRecordAction.getRecord(), is( enrichmentRecord ) );
        assertThat( storeRecordAction.getMimetype(), equalTo( UpdateEnrichmentRecordAction.MIMETYPE ) );

        child = children.get( 1 );
        assertTrue( child.getClass() == LinkRecordAction.class );

        LinkRecordAction linkRecordAction = (LinkRecordAction)child;
        assertThat( linkRecordAction.getRawRepo(), is( rawRepo ) );
        assertThat( linkRecordAction.getRecord(), is( enrichmentRecord ) );
        assertThat( linkRecordAction.getLinkToRecordId(), equalTo( new RecordId( recordId, RawRepo.RAWREPO_COMMON_LIBRARY ) ) );

        child = children.get( 2 );
        assertTrue( child.getClass() == EnqueueRecordAction.class );

        EnqueueRecordAction enqueueRecordAction = (EnqueueRecordAction)child;
        assertThat( enqueueRecordAction.getRawRepo(), is( rawRepo ) );
        assertThat( enqueueRecordAction.getRecord(), is( enrichmentRecord ) );
        assertThat( enqueueRecordAction.getMimetype(), equalTo( UpdateEnrichmentRecordAction.MIMETYPE ) );
    }

    /**
     * Test CreateEnrichmentRecordAction.performAction(): Create enrichment record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with a common record.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Perform actions to create a new enrichment record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>StoreRecordAction: Store the record</li>
     *              <li>LinkRecordAction: Link the new enrichment record to the common record</li>
     *              <li>EnqueueRecordAction: Put the enrichment record in queue</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test( expected = UpdateException.class )
    public void testPerformAction_ScripterException() throws Exception {
        InputStream is = getClass().getResourceAsStream( COMMON_RECORD_RESOURCE );
        MarcRecord commonRecord = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );

        is = getClass().getResourceAsStream( ENRICHMENT_RECORD_RESOURCE );
        MarcRecord enrichmentRecord = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );

        Integer agencyId = Integer.valueOf( MarcReader.getRecordValue( enrichmentRecord, "001", "b" ), 10 );

        RawRepo rawRepo = mock( RawRepo.class );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.createLibraryExtendedRecord( isNull( MarcRecord.class ), eq( commonRecord ), eq( agencyId ) ) ).thenThrow( new ScripterException( "Script error" ) );

        CreateEnrichmentRecordWithClassificationsAction instance = new CreateEnrichmentRecordWithClassificationsAction( rawRepo, agencyId );
        instance.setRecordsHandler( recordsHandler );
        instance.setCurrentCommonRecord( null );
        instance.setUpdatingCommonRecord( commonRecord );

        instance.performAction();
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final String COMMON_RECORD_RESOURCE = "/dk/dbc/updateservice/actions/common_enrichment.marc";
    private static final String ENRICHMENT_RECORD_RESOURCE = "/dk/dbc/updateservice/actions/enrichment.marc";
}
