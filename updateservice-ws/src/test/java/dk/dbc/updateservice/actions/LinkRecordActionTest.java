//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcReader;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordFactory;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.RawRepo;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.InputStream;
import java.util.ResourceBundle;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

//-----------------------------------------------------------------------------
public class LinkRecordActionTest {
    public LinkRecordActionTest() {
        this.messages = ResourceBundles.getBundle( this, "actions" );
    }

    /**
     * Test LinkRecord.performAction() to create a link to an existing record
     * in the rawrepo.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          A rawrepo with a record.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Create a link to the record that is already in the rawrepo.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          The rawrepo is called to create the link to the existing record.
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_LinkedRecordExist() throws Exception {
        InputStream is = getClass().getResourceAsStream( VOLUME_RECORD_RESOURCE );
        MarcRecord record = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );

        String recordId = MarcReader.getRecordValue( record, "001", "a" );
        Integer agencyId = Integer.valueOf( MarcReader.getRecordValue( record, "001", "b" ), 10 );
        String parentId = MarcReader.getRecordValue( record, "014", "a" );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( parentId ), eq( agencyId ) ) ).thenReturn( true );

        LinkRecordAction instance = new LinkRecordAction( rawRepo, record );
        instance.setLinkToRecordId( new RecordId( parentId, agencyId ) );

        assertThat( instance.performAction(), equalTo( ServiceResult.newOkResult() ) );

        ArgumentCaptor<String> argRecordId = ArgumentCaptor.forClass( String.class );
        ArgumentCaptor<Integer> argAgencyId = ArgumentCaptor.forClass( Integer.class );
        verify( rawRepo ).recordExists( argRecordId.capture(), argAgencyId.capture() );
        assertThat( argRecordId.getValue(), equalTo( parentId ) );
        assertThat( argAgencyId.getValue(), equalTo( agencyId ) );

        ArgumentCaptor<RecordId> argFrom = ArgumentCaptor.forClass( RecordId.class );
        ArgumentCaptor<RecordId> argTo = ArgumentCaptor.forClass( RecordId.class );
        verify( rawRepo ).linkRecord( argFrom.capture(), argTo.capture() );
        assertThat( argFrom.getValue(), equalTo( new RecordId( recordId, agencyId ) ) );
        assertThat( argTo.getValue(), equalTo( instance.getLinkToRecordId() ) );
    }

    /**
     * Test LinkRecord.performAction() to create a link to an non existing record
     * in the rawrepo.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          An empty rawrepo.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Create a link to a record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          The rawrepo is called to create the link the existing record and
     *          an error is returned.
     *      </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_LinkedRecordNotExist() throws Exception {
        InputStream is = getClass().getResourceAsStream( VOLUME_RECORD_RESOURCE );
        MarcRecord record = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );

        String recordId = MarcReader.getRecordValue( record, "001", "a" );
        Integer agencyId = Integer.valueOf( MarcReader.getRecordValue( record, "001", "b" ), 10 );
        String parentId = MarcReader.getRecordValue( record, "014", "a" );

        RawRepo rawRepo = mock( RawRepo.class );
        when( rawRepo.recordExists( eq( parentId ), eq( agencyId ) ) ).thenReturn( false );

        LinkRecordAction instance = new LinkRecordAction( rawRepo, record );
        instance.setLinkToRecordId( new RecordId( parentId, agencyId ) );

        String message = String.format( messages.getString( "reference.record.not.exist" ), recordId, agencyId, parentId, agencyId );
        assertThat( instance.performAction(), equalTo( ServiceResult.newErrorResult( UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message ) ) );

        ArgumentCaptor<String> argRecordId = ArgumentCaptor.forClass( String.class );
        ArgumentCaptor<Integer> argAgencyId = ArgumentCaptor.forClass( Integer.class );
        verify( rawRepo ).recordExists( argRecordId.capture(), argAgencyId.capture() );
        assertThat( argRecordId.getValue(), equalTo( parentId ) );
        assertThat( argAgencyId.getValue(), equalTo( agencyId ) );

        verify( rawRepo, never() ).linkRecord( any( RecordId.class ), any( RecordId.class ) );
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final String VOLUME_RECORD_RESOURCE = "/dk/dbc/updateservice/actions/volume.marc";

    private ResourceBundle messages;
}
