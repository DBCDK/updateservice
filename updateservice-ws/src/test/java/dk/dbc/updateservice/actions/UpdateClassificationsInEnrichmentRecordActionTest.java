//-----------------------------------------------------------------------------
package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordFactory;
import dk.dbc.iscrum.records.MarcWriter;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.updateservice.update.LibraryRecordsHandler;
import dk.dbc.updateservice.update.RawRepo;
import org.junit.Test;

import java.io.InputStream;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

//-----------------------------------------------------------------------------
public class UpdateClassificationsInEnrichmentRecordActionTest {
    /**
     * Test UpdateClassificationsInEnrichmentRecordAction.createRecord(): Update enrichment record
     * with wrong state.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          Missing common record.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update enrichment record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Throw exception: IllegalStateException
     *      </dd>
     * </dl>
     */
    @Test( expected = IllegalStateException.class )
    public void testCreateRecord_CommonRecordIsNull() throws Exception {
        InputStream is = getClass().getResourceAsStream( ENRICHMENT_RECORD_RESOURCE );
        MarcRecord enrichmentRecord = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );

        RawRepo rawRepo = mock( RawRepo.class );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );

        UpdateClassificationsInEnrichmentRecordAction instance = new UpdateClassificationsInEnrichmentRecordAction( rawRepo );
        instance.setRecordsHandler( recordsHandler );
        instance.setCommonRecord( null );
        instance.setEnrichmentRecord( enrichmentRecord );

        instance.createRecord();
    }

    /**
     * Test UpdateClassificationsInEnrichmentRecordAction.createRecord(): Update enrichment record
     * with wrong state.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          Missing enrichment record.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update enrichment record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Throw exception: IllegalStateException
     *      </dd>
     * </dl>
     */
    @Test( expected = IllegalStateException.class )
    public void testCreateRecord_EnrichmentRecordIsNull() throws Exception {
        InputStream is = getClass().getResourceAsStream( COMMON_RECORD_RESOURCE );
        MarcRecord commonRecord = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );

        RawRepo rawRepo = mock( RawRepo.class );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );

        UpdateClassificationsInEnrichmentRecordAction instance = new UpdateClassificationsInEnrichmentRecordAction( rawRepo );
        instance.setRecordsHandler( recordsHandler );
        instance.setCommonRecord( commonRecord );
        instance.setEnrichmentRecord( null );

        instance.createRecord();
    }

    /**
     * Test UpdateClassificationsInEnrichmentRecordAction.createRecord(): Update enrichment record
     * with wrong state.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          Missing records handler.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update enrichment record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Throw exception: IllegalStateException
     *      </dd>
     * </dl>
     */
    @Test( expected = IllegalStateException.class )
    public void testCreateRecord_RecordsHandlerIsNull() throws Exception {
        InputStream is = getClass().getResourceAsStream( COMMON_RECORD_RESOURCE );
        MarcRecord commonRecord = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );

        is = getClass().getResourceAsStream( ENRICHMENT_RECORD_RESOURCE );
        MarcRecord enrichmentRecord = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );

        RawRepo rawRepo = mock( RawRepo.class );

        UpdateClassificationsInEnrichmentRecordAction instance = new UpdateClassificationsInEnrichmentRecordAction( rawRepo );
        instance.setRecordsHandler( null );
        instance.setCommonRecord( commonRecord );
        instance.setEnrichmentRecord( enrichmentRecord );

        instance.createRecord();
    }

    /**
     * Test UpdateClassificationsInEnrichmentRecordAction.createRecord(): Update enrichment record
     * succesfully.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          Common record and enrichment record.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update classifications in enrichment record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          Returns a new enrichment record with extra 504 field.
     *      </dd>
     * </dl>
     */
    @Test
    public void testCreateRecord() throws Exception {
        InputStream is = getClass().getResourceAsStream( COMMON_RECORD_RESOURCE );
        MarcRecord commonRecord = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );

        is = getClass().getResourceAsStream( ENRICHMENT_RECORD_RESOURCE );
        MarcRecord enrichmentRecord = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );

        is = getClass().getResourceAsStream( ENRICHMENT_RECORD_RESOURCE );
        MarcRecord newEnrichmentRecord = MarcRecordFactory.readRecord( IOUtils.readAll( is, "UTF-8" ) );
        MarcWriter.addOrReplaceSubfield( newEnrichmentRecord, "504", "a", "Ny Note" );

        RawRepo rawRepo = mock( RawRepo.class );

        LibraryRecordsHandler recordsHandler = mock( LibraryRecordsHandler.class );
        when( recordsHandler.updateLibraryExtendedRecord( eq( commonRecord ), eq( enrichmentRecord ) ) ).thenReturn( newEnrichmentRecord );

        UpdateClassificationsInEnrichmentRecordAction instance = new UpdateClassificationsInEnrichmentRecordAction( rawRepo, enrichmentRecord );
        instance.setCommonRecord( commonRecord );
        instance.setRecordsHandler( recordsHandler );

        assertThat( instance.createRecord(), equalTo( newEnrichmentRecord ) );
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static final String COMMON_RECORD_RESOURCE = "/dk/dbc/updateservice/actions/common_enrichment.marc";
    private static final String ENRICHMENT_RECORD_RESOURCE = "/dk/dbc/updateservice/actions/enrichment.marc";
}
