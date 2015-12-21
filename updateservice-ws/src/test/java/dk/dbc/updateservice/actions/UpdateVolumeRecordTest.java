//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.updateservice.update.*;
import dk.dbc.updateservice.ws.JNDIResources;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

//-----------------------------------------------------------------------------
public class UpdateVolumeRecordTest {
    /**
     * Test performAction(): Create new single common record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          An empty rawrepo.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update a common record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>CreateSingleRecordAction: Creates the new record</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_CreateRecord() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE );
        String mainRecordId = AssertActionsUtil.getRecordId( mainRecord );
        Integer agencyId = AssertActionsUtil.getAgencyId( mainRecord );

        MarcRecord volumeRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE );
        String volumeRecordId = AssertActionsUtil.getRecordId( volumeRecord );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( mainRecordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( volumeRecordId ), eq( agencyId ) ) ).thenReturn( false );

        UpdateVolumeRecord instance = new UpdateVolumeRecord( rawRepo, volumeRecord );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 1 ) );

        CreateVolumeRecordAction action = (CreateVolumeRecordAction)children.get( 0 );
        assertThat( action, notNullValue() );
        assertThat( action.getRawRepo(), is( rawRepo ) );
        assertThat( action.getRecord(), is( volumeRecord ) );
    }

    /**
     * Test performAction(): Update an existing single common record with no
     * classifications.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with a single common record and a DBC enrichment
     *          record.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update the common record, but without any classification data.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>OverwriteSingleRecordAction: Action to overwrite the record.</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_OverwriteRecord() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE );
        String mainRecordId = AssertActionsUtil.getRecordId( mainRecord );
        Integer agencyId = AssertActionsUtil.getAgencyId( mainRecord );

        MarcRecord volumeRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE );
        String volumeRecordId = AssertActionsUtil.getRecordId( volumeRecord );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( mainRecordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( volumeRecordId ), eq( agencyId ) ) ).thenReturn( true );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( volumeRecord ) ).thenReturn( AssertActionsUtil.createAgenciesSet() );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.hasClassificationData( volumeRecord ) ).thenReturn( false );

        UpdateVolumeRecord instance = new UpdateVolumeRecord( rawRepo, volumeRecord );
        instance.setGroupId( 700000 );
        instance.setHoldingsItems( holdingsItems );
        instance.setRecordsHandler( recordsHandler );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        List<ServiceAction> children = instance.children();
        Assert.assertThat( children.size(), is( 1 ) );

        OverwriteVolumeRecordAction action = (OverwriteVolumeRecordAction)children.get( 0 );
        assertThat( action, notNullValue() );
        assertThat( action.getRawRepo(), is( rawRepo ) );
        assertThat( action.getRecord(), is( volumeRecord ) );
        assertThat( action.getGroupId(), equalTo( 700000 ) );
        assertThat( action.getHoldingsItems(), is( holdingsItems ) );
        assertThat( action.getRecordsHandler(), is( recordsHandler ) );
    }

    /**
     * Test performAction(): Delete single common record with no enrichments and no children.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with a common record and no enrichments or children.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update a common record that is marked for deletion.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Create child actions:
     *          <ol>
     *              <li>DeleteCommonRecordAction: Action to delete a common record</li>
     *          </ol>
     *          Return status: OK
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_DeleteRecord() throws Exception {
        MarcRecord mainRecord = AssertActionsUtil.loadRecord( AssertActionsUtil.COMMON_MAIN_RECORD_RESOURCE );
        String mainRecordId = AssertActionsUtil.getRecordId( mainRecord );
        Integer agencyId = AssertActionsUtil.getAgencyId( mainRecord );

        MarcRecord volumeRecord = AssertActionsUtil.loadRecordAndMarkForDeletion( AssertActionsUtil.COMMON_VOLUME_RECORD_RESOURCE );
        String volumeRecordId = AssertActionsUtil.getRecordId( volumeRecord );

        Properties settings = new Properties();
        settings.put( JNDIResources.RAWREPO_PROVIDER_ID, "xxx" );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( mainRecordId ), eq( agencyId ) ) ).thenReturn( true );
        when( rawRepo.recordExists( eq( volumeRecordId ), eq( agencyId ) ) ).thenReturn( true );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );

        HoldingsItems holdingsItems = mock( HoldingsItems.class );
        when( holdingsItems.getAgenciesThatHasHoldingsFor( volumeRecord ) ).thenReturn( new HashSet<Integer>() );

        SolrService solrService = mock( SolrService.class );
        when( solrService.hasDocuments( eq( SolrServiceIndexer.createSubfieldQuery( "002a", volumeRecordId ) ) ) ).thenReturn( false );

        UpdateVolumeRecord instance = new UpdateVolumeRecord( rawRepo, volumeRecord );
        instance.setRecordsHandler( recordsHandler );
        instance.setHoldingsItems( holdingsItems );
        instance.setSolrService( solrService );
        instance.setSettings( settings );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertCommonDeleteRecordAction( iterator.next(), rawRepo, volumeRecord, recordsHandler, holdingsItems, settings.getProperty( JNDIResources.RAWREPO_PROVIDER_ID ) );

        assertThat( iterator.hasNext(), is( false ) );
    }
}
