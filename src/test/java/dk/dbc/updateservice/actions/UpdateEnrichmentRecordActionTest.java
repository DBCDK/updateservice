package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordFactory;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.records.MarcRecordWriter;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.HoldingsItems;
import dk.dbc.updateservice.update.LibraryRecordsHandler;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.RawRepoDecoder;
import dk.dbc.updateservice.update.RawRepoEncoder;
import dk.dbc.updateservice.update.RawRepoRecordMock;
import dk.dbc.updateservice.update.SolrService;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import dk.dbc.updateservice.update.UpdateException;
import org.junit.Assert;
import org.junit.Test;

import javax.xml.bind.JAXBException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.ResourceBundle;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UpdateEnrichmentRecordActionTest {
    private static final String COMMON_RECORD_RESOURCE = "/dk/dbc/updateservice/actions/common_enrichment.marc";
    private static final String ENRICHMENT_RECORD_RESOURCE = "/dk/dbc/updateservice/actions/enrichment.marc";

    private ResourceBundle messages;

    public UpdateEnrichmentRecordActionTest() {
        this.messages = ResourceBundles.getBundle(this, "actions");
    }

    /**
     * Test UpdateEnrichmentRecordAction constructor.
     */
    @Test
    public void testConstructor() throws Exception {
        InputStream is = getClass().getResourceAsStream(ENRICHMENT_RECORD_RESOURCE);
        MarcRecord record = MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));
        RawRepo rawRepo = mock(RawRepo.class);

        UpdateEnrichmentRecordAction instance = new UpdateEnrichmentRecordAction(rawRepo, record);
        assertThat(instance, notNullValue());
        assertThat(instance.getHoldingsItems(), nullValue());
    }

    /**
     * Test UpdateEnrichmentRecordAction.performAction(): Create enrichment record with no 002 links
     * from other records.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with a common record.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update an enrichment record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>StoreRecordAction: Store the record</li>
     * <li>LinkRecordAction: Link the record to the common record</li>
     * <li>EnqueueRecordAction: Put the record in queue</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_CreateRecord_No002Links() throws Exception {
        InputStream is = getClass().getResourceAsStream(ENRICHMENT_RECORD_RESOURCE);
        MarcRecord record = MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));

        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.recordId();
        Integer agencyId = reader.agencyIdAsInteger();

        is = getClass().getResourceAsStream(COMMON_RECORD_RESOURCE);
        MarcRecord commonRecordData = MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));

        RawRepo rawRepo = mock(RawRepo.class);
        Record commonRecord = createRawRepoRecord(commonRecordData, MarcXChangeMimeType.MARCXCHANGE);
        when(rawRepo.recordExists(eq(commonRecord.getId().getBibliographicRecordId()), eq(commonRecord.getId().getAgencyId()))).thenReturn(true);
        when(rawRepo.fetchRecord(eq(commonRecord.getId().getBibliographicRecordId()), eq(commonRecord.getId().getAgencyId()))).thenReturn(commonRecord);
        when(rawRepo.recordExists(eq(recordId), eq(agencyId))).thenReturn(false);

        LibraryRecordsHandler recordsHandler = mock(LibraryRecordsHandler.class);
        when(recordsHandler.correctLibraryExtendedRecord(eq(commonRecordData), eq(record))).thenReturn(record);

        SolrService solrService = mock(SolrService.class);
        when(solrService.hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(false);

        UpdateEnrichmentRecordAction instance = new UpdateEnrichmentRecordAction(rawRepo, record);
        instance.setHoldingsItems(mock(HoldingsItems.class));
        instance.setRecordsHandler(recordsHandler);
        instance.setSolrService(solrService);
        instance.setProviderId("xxx");

        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = instance.children();
        Assert.assertThat(children.size(), is(3));

        ServiceAction child = children.get(0);
        assertTrue(child.getClass() == StoreRecordAction.class);

        StoreRecordAction storeRecordAction = (StoreRecordAction) child;
        assertThat(storeRecordAction.getRawRepo(), is(rawRepo));
        assertThat(storeRecordAction.getRecord(), is(record));
        assertThat(storeRecordAction.getMimetype(), equalTo(UpdateEnrichmentRecordAction.MIMETYPE));

        child = children.get(1);
        assertTrue(child.getClass() == LinkRecordAction.class);

        LinkRecordAction linkRecordAction = (LinkRecordAction) child;
        assertThat(linkRecordAction.getRawRepo(), is(rawRepo));
        assertThat(linkRecordAction.getRecord(), is(record));
        assertThat(linkRecordAction.getLinkToRecordId(), equalTo(new RecordId(recordId, RawRepo.RAWREPO_COMMON_LIBRARY)));

        AssertActionsUtil.assertEnqueueRecordAction(children.get(2), rawRepo, record, instance.getProviderId(), UpdateEnrichmentRecordAction.MIMETYPE);
    }

    /**
     * Test UpdateEnrichmentRecordAction.performAction(): Create enrichment record with 002 links
     * from other records.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with a common record.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update an enrichment record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>StoreRecordAction: Store the record</li>
     * <li>LinkRecordAction: Link the record to the common record</li>
     * <li>EnqueueRecordAction: Put the record in queue</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_CreateRecord_With002Links() throws Exception {
        InputStream is = getClass().getResourceAsStream(ENRICHMENT_RECORD_RESOURCE);
        MarcRecord record = MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));

        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.recordId();
        Integer agencyId = reader.agencyIdAsInteger();

        is = getClass().getResourceAsStream(COMMON_RECORD_RESOURCE);
        MarcRecord commonRecordData = MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));

        RawRepo rawRepo = mock(RawRepo.class);
        Record commonRecord = createRawRepoRecord(commonRecordData, MarcXChangeMimeType.MARCXCHANGE);
        when(rawRepo.recordExists(eq(commonRecord.getId().getBibliographicRecordId()), eq(commonRecord.getId().getAgencyId()))).thenReturn(true);
        when(rawRepo.fetchRecord(eq(commonRecord.getId().getBibliographicRecordId()), eq(commonRecord.getId().getAgencyId()))).thenReturn(commonRecord);
        when(rawRepo.recordExists(eq(recordId), eq(agencyId))).thenReturn(false);

        LibraryRecordsHandler recordsHandler = mock(LibraryRecordsHandler.class);
        when(recordsHandler.correctLibraryExtendedRecord(eq(commonRecordData), eq(record))).thenReturn(record);

        SolrService solrService = mock(SolrService.class);
        when(solrService.hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(true);

        UpdateEnrichmentRecordAction instance = new UpdateEnrichmentRecordAction(rawRepo, record);
        instance.setHoldingsItems(mock(HoldingsItems.class));
        instance.setRecordsHandler(recordsHandler);
        instance.setSolrService(solrService);
        instance.setProviderId("xxx");

        String message = messages.getString("update.record.with.002.links");
        assertThat(instance.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message)));
        assertThat(instance.children().isEmpty(), is(true));
    }

    /**
     * Test UpdateEnrichmentRecordAction.performAction(): Update enrichment record with no 002 links
     * from other records.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with a common record.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update an enrichment record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>StoreRecordAction: Store the record</li>
     * <li>LinkRecordAction: Link the record to the common record</li>
     * <li>EnqueueRecordAction: Put the record in queue</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_UpdateRecord_No002Links() throws Exception {
        InputStream is = getClass().getResourceAsStream(ENRICHMENT_RECORD_RESOURCE);
        MarcRecord record = MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));

        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.recordId();
        Integer agencyId = reader.agencyIdAsInteger();

        is = getClass().getResourceAsStream(COMMON_RECORD_RESOURCE);
        MarcRecord commonRecordData = MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));

        RawRepo rawRepo = mock(RawRepo.class);
        Record commonRecord = createRawRepoRecord(commonRecordData, MarcXChangeMimeType.MARCXCHANGE);
        when(rawRepo.recordExists(eq(commonRecord.getId().getBibliographicRecordId()), eq(commonRecord.getId().getAgencyId()))).thenReturn(true);
        when(rawRepo.fetchRecord(eq(commonRecord.getId().getBibliographicRecordId()), eq(commonRecord.getId().getAgencyId()))).thenReturn(commonRecord);
        when(rawRepo.recordExists(eq(recordId), eq(agencyId))).thenReturn(true);

        LibraryRecordsHandler recordsHandler = mock(LibraryRecordsHandler.class);
        when(recordsHandler.correctLibraryExtendedRecord(eq(commonRecordData), eq(record))).thenReturn(record);

        SolrService solrService = mock(SolrService.class);
        when(solrService.hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(false);

        UpdateEnrichmentRecordAction instance = new UpdateEnrichmentRecordAction(rawRepo, record);
        instance.setHoldingsItems(mock(HoldingsItems.class));
        instance.setRecordsHandler(recordsHandler);
        instance.setSolrService(solrService);
        instance.setProviderId("xxx");

        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = instance.children();
        Assert.assertThat(children.size(), is(3));

        ServiceAction child = children.get(0);
        assertTrue(child.getClass() == StoreRecordAction.class);

        StoreRecordAction storeRecordAction = (StoreRecordAction) child;
        assertThat(storeRecordAction.getRawRepo(), is(rawRepo));
        assertThat(storeRecordAction.getRecord(), is(record));
        assertThat(storeRecordAction.getMimetype(), equalTo(UpdateEnrichmentRecordAction.MIMETYPE));

        child = children.get(1);
        assertTrue(child.getClass() == LinkRecordAction.class);

        LinkRecordAction linkRecordAction = (LinkRecordAction) child;
        assertThat(linkRecordAction.getRawRepo(), is(rawRepo));
        assertThat(linkRecordAction.getRecord(), is(record));
        assertThat(linkRecordAction.getLinkToRecordId(), equalTo(new RecordId(recordId, RawRepo.RAWREPO_COMMON_LIBRARY)));

        AssertActionsUtil.assertEnqueueRecordAction(children.get(2), rawRepo, record, instance.getProviderId(), UpdateEnrichmentRecordAction.MIMETYPE);
    }

    /**
     * Test UpdateEnrichmentRecordAction.performAction(): Update enrichment record with 002 links
     * from other records.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with a common record.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update an enrichment record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>StoreRecordAction: Store the record</li>
     * <li>LinkRecordAction: Link the record to the common record</li>
     * <li>EnqueueRecordAction: Put the record in queue</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_UpdateRecord_With002Links() throws Exception {
        InputStream is = getClass().getResourceAsStream(ENRICHMENT_RECORD_RESOURCE);
        MarcRecord record = MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));

        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.recordId();
        Integer agencyId = reader.agencyIdAsInteger();

        is = getClass().getResourceAsStream(COMMON_RECORD_RESOURCE);
        MarcRecord commonRecordData = MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));

        RawRepo rawRepo = mock(RawRepo.class);
        Record commonRecord = createRawRepoRecord(commonRecordData, MarcXChangeMimeType.MARCXCHANGE);
        when(rawRepo.recordExists(eq(commonRecord.getId().getBibliographicRecordId()), eq(commonRecord.getId().getAgencyId()))).thenReturn(true);
        when(rawRepo.fetchRecord(eq(commonRecord.getId().getBibliographicRecordId()), eq(commonRecord.getId().getAgencyId()))).thenReturn(commonRecord);
        when(rawRepo.recordExists(eq(recordId), eq(agencyId))).thenReturn(true);

        LibraryRecordsHandler recordsHandler = mock(LibraryRecordsHandler.class);
        when(recordsHandler.correctLibraryExtendedRecord(eq(commonRecordData), eq(record))).thenReturn(record);

        SolrService solrService = mock(SolrService.class);
        when(solrService.hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(true);

        UpdateEnrichmentRecordAction instance = new UpdateEnrichmentRecordAction(rawRepo, record);
        instance.setHoldingsItems(mock(HoldingsItems.class));
        instance.setRecordsHandler(recordsHandler);
        instance.setSolrService(solrService);
        instance.setProviderId("xxx");

        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = instance.children();
        Assert.assertThat(children.size(), is(3));

        ServiceAction child = children.get(0);
        assertTrue(child.getClass() == StoreRecordAction.class);

        StoreRecordAction storeRecordAction = (StoreRecordAction) child;
        assertThat(storeRecordAction.getRawRepo(), is(rawRepo));
        assertThat(storeRecordAction.getRecord(), is(record));
        assertThat(storeRecordAction.getMimetype(), equalTo(UpdateEnrichmentRecordAction.MIMETYPE));

        child = children.get(1);
        assertTrue(child.getClass() == LinkRecordAction.class);

        LinkRecordAction linkRecordAction = (LinkRecordAction) child;
        assertThat(linkRecordAction.getRawRepo(), is(rawRepo));
        assertThat(linkRecordAction.getRecord(), is(record));
        assertThat(linkRecordAction.getLinkToRecordId(), equalTo(new RecordId(recordId, RawRepo.RAWREPO_COMMON_LIBRARY)));

        AssertActionsUtil.assertEnqueueRecordAction(children.get(2), rawRepo, record, instance.getProviderId(), UpdateEnrichmentRecordAction.MIMETYPE);
    }

    /**
     * Test UpdateEnrichmentRecordAction.performAction(): Update enrichment record to a common record that
     * does not exist.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * An empty rawrepo.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update an enrichment record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return status: FAILED_UPDATE_INTERNAL_ERROR
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_UpdateRecord_CommonRecordDoesNotExist() throws Exception {
        MarcRecord commonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(enrichmentRecord);

        String commonRecordId = AssertActionsUtil.getRecordId(commonRecord);

        RawRepo rawRepo = mock(RawRepo.class);
        when(rawRepo.recordExists(eq(commonRecordId), eq(RawRepo.RAWREPO_COMMON_LIBRARY))).thenReturn(false);

        LibraryRecordsHandler recordsHandler = mock(LibraryRecordsHandler.class);

        SolrService solrService = mock(SolrService.class);
        when(solrService.hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(false);

        UpdateEnrichmentRecordAction instance = new UpdateEnrichmentRecordAction(rawRepo, enrichmentRecord);
        instance.setHoldingsItems(mock(HoldingsItems.class));
        instance.setRecordsHandler(recordsHandler);
        instance.setSolrService(solrService);
        instance.setProviderId("xxx");

        String message = String.format(messages.getString("record.does.not.exist"), commonRecordId);
        assertThat(instance.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message)));
    }

    /**
     * Test UpdateEnrichmentRecordAction.performAction(): Update enrichment record that is optimize to an
     * empty record by LibraryRecordsHandler.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with a common record.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update an enrichment record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions to delete the record:
     * <ol>
     * <li>RemoveLinksAction: Remove any existing links to other records</li>
     * <li>DeleteRecordAction: Deletes the record</li>
     * <li>EnqueueRecordAction: Put the record in queue</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_UpdateRecord_OptimizedToEmpty() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        Integer agencyId = AssertActionsUtil.getAgencyId(record);

        MarcRecord commonRecordData = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);

        RawRepo rawRepo = mock(RawRepo.class);
        Record commonRecord = createRawRepoRecord(commonRecordData, MarcXChangeMimeType.MARCXCHANGE);
        when(rawRepo.recordExists(eq(commonRecord.getId().getBibliographicRecordId()), eq(commonRecord.getId().getAgencyId()))).thenReturn(true);
        when(rawRepo.recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(rawRepo.fetchRecord(eq(commonRecord.getId().getBibliographicRecordId()), eq(commonRecord.getId().getAgencyId()))).thenReturn(commonRecord);

        HoldingsItems holdingsItems = mock(HoldingsItems.class);
        when(holdingsItems.getAgenciesThatHasHoldingsForId(eq(recordId))).thenReturn(new HashSet<Integer>());

        LibraryRecordsHandler recordsHandler = mock(LibraryRecordsHandler.class);
        when(recordsHandler.correctLibraryExtendedRecord(eq(commonRecordData), eq(record))).thenReturn(new MarcRecord());

        SolrService solrService = mock(SolrService.class);
        when(solrService.hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(false);

        UpdateEnrichmentRecordAction instance = new UpdateEnrichmentRecordAction(rawRepo, record);
        instance.setHoldingsItems(holdingsItems);
        instance.setRecordsHandler(recordsHandler);
        instance.setSolrService(solrService);
        instance.setProviderId("xxx");

        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = instance.children().listIterator();

        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), rawRepo, record);
        AssertActionsUtil.assertDeleteRecordAction(iterator.next(), rawRepo, record, instance.MIMETYPE);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), rawRepo, record, instance.getProviderId(), instance.MIMETYPE);

        assertThat(iterator.hasNext(), is(false));
    }

    /**
     * Test UpdateEnrichmentRecordAction.performAction(): Update enrichment record where
     * the common record can not be decoded.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with a common record, that can not be decoded.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update an enrichment record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Throw UpdateException that encapsulates the UnsupportedEncodingException.
     * </dd>
     * </dl>
     */
    @Test(expected = UpdateException.class)
    public void testPerformAction_UpdateRecord_EncodingException() throws Exception {
        InputStream is = getClass().getResourceAsStream(ENRICHMENT_RECORD_RESOURCE);
        MarcRecord record = MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));
        String recordId = AssertActionsUtil.getRecordId(record);

        is = getClass().getResourceAsStream(COMMON_RECORD_RESOURCE);
        MarcRecord commonRecordData = MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));

        RawRepo rawRepo = mock(RawRepo.class);
        Record commonRecord = createRawRepoRecord(commonRecordData, MarcXChangeMimeType.MARCXCHANGE);
        when(rawRepo.recordExists(eq(commonRecord.getId().getBibliographicRecordId()), eq(commonRecord.getId().getAgencyId()))).thenReturn(true);
        when(rawRepo.fetchRecord(eq(commonRecord.getId().getBibliographicRecordId()), eq(commonRecord.getId().getAgencyId()))).thenReturn(commonRecord);

        RawRepoDecoder decoder = mock(RawRepoDecoder.class);
        when(decoder.decodeRecord(eq(commonRecord.getContent()))).thenThrow(new UnsupportedEncodingException("error"));

        LibraryRecordsHandler recordsHandler = mock(LibraryRecordsHandler.class);
        when(recordsHandler.correctLibraryExtendedRecord(eq(commonRecordData), eq(record))).thenReturn(record);

        SolrService solrService = mock(SolrService.class);
        when(solrService.hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(false);

        UpdateEnrichmentRecordAction instance = new UpdateEnrichmentRecordAction(rawRepo, record);
        instance.setDecoder(decoder);
        instance.setRecordsHandler(recordsHandler);
        instance.setSolrService(solrService);

        instance.performAction();
    }

    /**
     * Test UpdateEnrichmentRecordAction.performAction(): Update enrichment record where
     * the JavaScript engine throws a ScripterException.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with a common record.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update an enrichment record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Throw UpdateException that encapsulates the ScripterException.
     * </dd>
     * </dl>
     */
    @Test(expected = UpdateException.class)
    public void testPerformAction_UpdateRecord_ScripterException() throws Exception {
        InputStream is = getClass().getResourceAsStream(ENRICHMENT_RECORD_RESOURCE);
        MarcRecord record = MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));
        String recordId = AssertActionsUtil.getRecordId(record);

        is = getClass().getResourceAsStream(COMMON_RECORD_RESOURCE);
        MarcRecord commonRecordData = MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));

        RawRepo rawRepo = mock(RawRepo.class);
        Record commonRecord = createRawRepoRecord(commonRecordData, MarcXChangeMimeType.MARCXCHANGE);
        when(rawRepo.recordExists(eq(commonRecord.getId().getBibliographicRecordId()), eq(commonRecord.getId().getAgencyId()))).thenReturn(true);
        when(rawRepo.fetchRecord(eq(commonRecord.getId().getBibliographicRecordId()), eq(commonRecord.getId().getAgencyId()))).thenReturn(commonRecord);

        LibraryRecordsHandler recordsHandler = mock(LibraryRecordsHandler.class);
        when(recordsHandler.correctLibraryExtendedRecord(eq(commonRecordData), eq(record))).thenThrow(new ScripterException("error"));

        SolrService solrService = mock(SolrService.class);
        when(solrService.hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(false);

        UpdateEnrichmentRecordAction instance = new UpdateEnrichmentRecordAction(rawRepo, record);
        instance.setRecordsHandler(recordsHandler);
        instance.setSolrService(solrService);

        instance.performAction();
    }

    /**
     * Test UpdateEnrichmentRecordAction.performAction(): Delete an enrichment record with no holdings.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with a common record.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Delete an enrichment record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions to delete the record:
     * <ol>
     * <li>RemoveLinksAction: Remove any existing links to other records</li>
     * <li>DeleteRecordAction: Deletes the record</li>
     * <li>EnqueueRecordAction: Put the record in queue</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_DeleteRecord_NoHoldings() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        Integer agencyId = AssertActionsUtil.getAgencyId(record);

        String providerId = "xxx";

        new MarcRecordWriter(record).markForDeletion();

        RawRepo rawRepo = mock(RawRepo.class);
        when(rawRepo.recordExists(eq(recordId), eq(agencyId))).thenReturn(true);

        LibraryRecordsHandler recordsHandler = mock(LibraryRecordsHandler.class);

        HoldingsItems holdingsItems = mock(HoldingsItems.class);
        when(holdingsItems.getAgenciesThatHasHoldingsForId(eq(recordId))).thenReturn(new HashSet<Integer>());

        SolrService solrService = mock(SolrService.class);
        when(solrService.hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(false);

        UpdateEnrichmentRecordAction instance = new UpdateEnrichmentRecordAction(rawRepo, record);
        instance.setRecordsHandler(recordsHandler);
        instance.setHoldingsItems(holdingsItems);
        instance.setSolrService(solrService);
        instance.setProviderId(providerId);

        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = instance.children().listIterator();

        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), rawRepo, record);
        AssertActionsUtil.assertDeleteRecordAction(iterator.next(), rawRepo, record, UpdateEnrichmentRecordAction.MIMETYPE);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), rawRepo, record, providerId, UpdateEnrichmentRecordAction.MIMETYPE);

        assertFalse(iterator.hasNext());
    }

    /**
     * Test UpdateEnrichmentRecordAction.performAction(): Delete enrichment record with with holdings.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with a common record.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Delete an enrichment record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return status: FAILED_VALIDATION_INTERNAL_ERROR.
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_DeleteRecord_WithHoldings() throws Exception {
        InputStream is = getClass().getResourceAsStream(ENRICHMENT_RECORD_RESOURCE);
        MarcRecord record = MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));

        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.recordId();
        Integer agencyId = reader.agencyIdAsInteger();

        String providerId = "xxx";

        new MarcRecordWriter(record).markForDeletion();

        RawRepo rawRepo = mock(RawRepo.class);
        when(rawRepo.recordExists(eq(recordId), eq(agencyId))).thenReturn(true);

        LibraryRecordsHandler recordsHandler = mock(LibraryRecordsHandler.class);

        HoldingsItems holdingsItems = mock(HoldingsItems.class);
        when(holdingsItems.getAgenciesThatHasHoldingsFor(eq(record))).thenReturn(AssertActionsUtil.createAgenciesSet(agencyId));

        SolrService solrService = mock(SolrService.class);
        when(solrService.hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(false);

        UpdateEnrichmentRecordAction instance = new UpdateEnrichmentRecordAction(rawRepo, record);
        instance.setRecordsHandler(recordsHandler);
        instance.setHoldingsItems(holdingsItems);
        instance.setSolrService(solrService);
        instance.setProviderId(providerId);

        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = instance.children().listIterator();

        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), rawRepo, record);
        AssertActionsUtil.assertDeleteRecordAction(iterator.next(), rawRepo, record, UpdateEnrichmentRecordAction.MIMETYPE);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), rawRepo, record, providerId, UpdateEnrichmentRecordAction.MIMETYPE);

        assertFalse(iterator.hasNext());
    }

    private Record createRawRepoRecord(MarcRecord record, String mimetype) throws JAXBException, UnsupportedEncodingException {
        RawRepoRecordMock rawRepoRecord = new RawRepoRecordMock();

        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.recordId();
        Integer agencyId = reader.agencyIdAsInteger();

        rawRepoRecord.setId(new RecordId(recordId, agencyId));
        rawRepoRecord.setDeleted(false);
        rawRepoRecord.setMimeType(mimetype);
        rawRepoRecord.setContent(new RawRepoEncoder().encodeRecord(record));

        return rawRepoRecord;
    }
}
