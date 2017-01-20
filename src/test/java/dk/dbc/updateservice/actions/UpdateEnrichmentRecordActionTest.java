package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.records.MarcRecordWriter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.RawRepoDecoder;
import dk.dbc.updateservice.update.RawRepoEncoder;
import dk.dbc.updateservice.update.RawRepoRecordMock;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.ws.JNDIResources;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UpdateEnrichmentRecordActionTest {
    private GlobalActionState state;
    private Properties settings;

    @Before
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
        settings = new UpdateTestUtils().getSettings();
    }

    /**
     * Test UpdateEnrichmentRecordAction constructor.
     */
    // TODO - WHY?!?!
    @Test
    public void testConstructor() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        UpdateEnrichmentRecordAction updateEnrichmentRecordAction = new UpdateEnrichmentRecordAction(state, settings, record);
        assertThat(updateEnrichmentRecordAction, notNullValue());
        assertThat(updateEnrichmentRecordAction.state.getHoldingsItems(), is(state.getHoldingsItems()));
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
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.recordId();
        Integer agencyId = reader.agencyIdAsInteger();
        MarcRecord commonRecordData = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);

        Record commonRecord = createRawRepoRecord(commonRecordData, MarcXChangeMimeType.MARCXCHANGE);
        when(state.getRawRepo().recordExists(eq(commonRecord.getId().getBibliographicRecordId()), eq(commonRecord.getId().getAgencyId()))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(commonRecord.getId().getBibliographicRecordId()), eq(commonRecord.getId().getAgencyId()))).thenReturn(commonRecord);
        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(false);
        when(state.getLibraryRecordsHandler().correctLibraryExtendedRecord(eq(commonRecordData), eq(record))).thenReturn(record);
        when(state.getSolrService().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(false);

        UpdateEnrichmentRecordAction updateEnrichmentRecordAction = new UpdateEnrichmentRecordAction(state, settings, record);
        assertThat(updateEnrichmentRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateEnrichmentRecordAction.children();
        Assert.assertThat(children.size(), is(3));

        ServiceAction child = children.get(0);
        assertTrue(child.getClass() == StoreRecordAction.class);
        StoreRecordAction storeRecordAction = (StoreRecordAction) child;
        assertThat(storeRecordAction.getRawRepo(), is(state.getRawRepo()));
        assertThat(storeRecordAction.getRecord(), is(record));
        assertThat(storeRecordAction.getMimetype(), equalTo(MarcXChangeMimeType.ENRICHMENT));

        child = children.get(1);
        assertTrue(child.getClass() == LinkRecordAction.class);
        LinkRecordAction linkRecordAction = (LinkRecordAction) child;
        assertThat(linkRecordAction.getRawRepo(), is(state.getRawRepo()));
        assertThat(linkRecordAction.getRecord(), is(record));
        assertThat(linkRecordAction.getLinkToRecordId(), equalTo(new RecordId(recordId, RawRepo.RAWREPO_COMMON_LIBRARY)));
        AssertActionsUtil.assertEnqueueRecordAction(children.get(2), state.getRawRepo(), record, settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID), MarcXChangeMimeType.ENRICHMENT);
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
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.recordId();
        Integer agencyId = reader.agencyIdAsInteger();
        MarcRecord commonRecordData = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);

        Record commonRecord = createRawRepoRecord(commonRecordData, MarcXChangeMimeType.MARCXCHANGE);
        when(state.getRawRepo().recordExists(eq(commonRecord.getId().getBibliographicRecordId()), eq(commonRecord.getId().getAgencyId()))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(commonRecord.getId().getBibliographicRecordId()), eq(commonRecord.getId().getAgencyId()))).thenReturn(commonRecord);
        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(false);
        when(state.getLibraryRecordsHandler().correctLibraryExtendedRecord(eq(commonRecordData), eq(record))).thenReturn(record);
        when(state.getSolrService().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(true);

        UpdateEnrichmentRecordAction updateEnrichmentRecordAction = new UpdateEnrichmentRecordAction(state, settings, record);
        String message = state.getMessages().getString("update.record.with.002.links");
        assertThat(updateEnrichmentRecordAction.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state)));
        assertThat(updateEnrichmentRecordAction.children().isEmpty(), is(true));
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
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.recordId();
        Integer agencyId = reader.agencyIdAsInteger();
        MarcRecord commonRecordData = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);

        Record commonRecord = createRawRepoRecord(commonRecordData, MarcXChangeMimeType.MARCXCHANGE);
        when(state.getRawRepo().recordExists(eq(commonRecord.getId().getBibliographicRecordId()), eq(commonRecord.getId().getAgencyId()))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(commonRecord.getId().getBibliographicRecordId()), eq(commonRecord.getId().getAgencyId()))).thenReturn(commonRecord);
        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getLibraryRecordsHandler().correctLibraryExtendedRecord(eq(commonRecordData), eq(record))).thenReturn(record);
        when(state.getSolrService().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(false);

        UpdateEnrichmentRecordAction updateEnrichmentRecordAction = new UpdateEnrichmentRecordAction(state, settings, record);
        assertThat(updateEnrichmentRecordAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateEnrichmentRecordAction.children();
        Assert.assertThat(children.size(), is(3));

        ServiceAction child = children.get(0);
        assertTrue(child.getClass() == StoreRecordAction.class);
        StoreRecordAction storeRecordAction = (StoreRecordAction) child;
        assertThat(storeRecordAction.getRawRepo(), is(state.getRawRepo()));
        assertThat(storeRecordAction.getRecord(), is(record));
        assertThat(storeRecordAction.getMimetype(), equalTo(MarcXChangeMimeType.ENRICHMENT));

        child = children.get(1);
        assertTrue(child.getClass() == LinkRecordAction.class);
        LinkRecordAction linkRecordAction = (LinkRecordAction) child;
        assertThat(linkRecordAction.getRawRepo(), is(state.getRawRepo()));
        assertThat(linkRecordAction.getRecord(), is(record));
        assertThat(linkRecordAction.getLinkToRecordId(), equalTo(new RecordId(recordId, RawRepo.RAWREPO_COMMON_LIBRARY)));
        AssertActionsUtil.assertEnqueueRecordAction(children.get(2), state.getRawRepo(), record, settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID), MarcXChangeMimeType.ENRICHMENT);
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
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.recordId();
        Integer agencyId = reader.agencyIdAsInteger();
        MarcRecord commonRecordData = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);

        Record commonRecord = createRawRepoRecord(commonRecordData, MarcXChangeMimeType.MARCXCHANGE);
        when(state.getRawRepo().recordExists(eq(commonRecord.getId().getBibliographicRecordId()), eq(commonRecord.getId().getAgencyId()))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(commonRecord.getId().getBibliographicRecordId()), eq(commonRecord.getId().getAgencyId()))).thenReturn(commonRecord);
        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getLibraryRecordsHandler().correctLibraryExtendedRecord(eq(commonRecordData), eq(record))).thenReturn(record);
        when(state.getSolrService().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(true);

        UpdateEnrichmentRecordAction instance = new UpdateEnrichmentRecordAction(state, settings, record);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = instance.children();
        Assert.assertThat(children.size(), is(3));

        ServiceAction child = children.get(0);
        assertTrue(child.getClass() == StoreRecordAction.class);
        StoreRecordAction storeRecordAction = (StoreRecordAction) child;
        assertThat(storeRecordAction.getRawRepo(), is(state.getRawRepo()));
        assertThat(storeRecordAction.getRecord(), is(record));
        assertThat(storeRecordAction.getMimetype(), equalTo(MarcXChangeMimeType.ENRICHMENT));

        child = children.get(1);
        assertTrue(child.getClass() == LinkRecordAction.class);
        LinkRecordAction linkRecordAction = (LinkRecordAction) child;
        assertThat(linkRecordAction.getRawRepo(), is(state.getRawRepo()));
        assertThat(linkRecordAction.getRecord(), is(record));
        assertThat(linkRecordAction.getLinkToRecordId(), equalTo(new RecordId(recordId, RawRepo.RAWREPO_COMMON_LIBRARY)));
        AssertActionsUtil.assertEnqueueRecordAction(children.get(2), state.getRawRepo(), record, settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID), MarcXChangeMimeType.ENRICHMENT);
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

        when(state.getRawRepo().recordExists(eq(commonRecordId), eq(RawRepo.RAWREPO_COMMON_LIBRARY))).thenReturn(false);
        when(state.getSolrService().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(false);

        UpdateEnrichmentRecordAction instance = new UpdateEnrichmentRecordAction(state, settings, enrichmentRecord);
        String message = String.format(state.getMessages().getString("record.does.not.exist"), commonRecordId);
        assertThat(instance.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state)));
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
        Integer agencyId = AssertActionsUtil.getAgencyIdAsInteger(record);
        MarcRecord commonRecordData = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);

        Record commonRecord = createRawRepoRecord(commonRecordData, MarcXChangeMimeType.MARCXCHANGE);
        when(state.getRawRepo().recordExists(eq(commonRecord.getId().getBibliographicRecordId()), eq(commonRecord.getId().getAgencyId()))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(commonRecord.getId().getBibliographicRecordId()), eq(commonRecord.getId().getAgencyId()))).thenReturn(commonRecord);
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsForId(eq(recordId))).thenReturn(new HashSet<>());
        when(state.getLibraryRecordsHandler().correctLibraryExtendedRecord(eq(commonRecordData), eq(record))).thenReturn(new MarcRecord());
        when(state.getSolrService().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(false);

        UpdateEnrichmentRecordAction instance = new UpdateEnrichmentRecordAction(state, settings, record);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = instance.children().listIterator();

        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertDeleteRecordAction(iterator.next(), state.getRawRepo(), record, MarcXChangeMimeType.ENRICHMENT);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID), MarcXChangeMimeType.ENRICHMENT);
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
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        MarcRecord commonRecordData = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        RawRepoDecoder decoder = mock(RawRepoDecoder.class);

        Record commonRecord = createRawRepoRecord(commonRecordData, MarcXChangeMimeType.MARCXCHANGE);
        when(state.getRawRepo().recordExists(eq(commonRecord.getId().getBibliographicRecordId()), eq(commonRecord.getId().getAgencyId()))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(commonRecord.getId().getBibliographicRecordId()), eq(commonRecord.getId().getAgencyId()))).thenReturn(commonRecord);
        when(decoder.decodeRecord(eq(commonRecord.getContent()))).thenThrow(new UnsupportedEncodingException("error"));
        when(state.getLibraryRecordsHandler().correctLibraryExtendedRecord(eq(commonRecordData), eq(record))).thenReturn(record);
        when(state.getSolrService().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(false);

        UpdateEnrichmentRecordAction updateEnrichmentRecordAction = new UpdateEnrichmentRecordAction(state, settings, record);
        updateEnrichmentRecordAction.decoder = decoder;
        updateEnrichmentRecordAction.performAction();
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
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        MarcRecord commonRecordData = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);

        Record commonRecord = createRawRepoRecord(commonRecordData, MarcXChangeMimeType.MARCXCHANGE);
        when(state.getRawRepo().recordExists(eq(commonRecord.getId().getBibliographicRecordId()), eq(commonRecord.getId().getAgencyId()))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq(commonRecord.getId().getBibliographicRecordId()), eq(commonRecord.getId().getAgencyId()))).thenReturn(commonRecord);
        when(state.getLibraryRecordsHandler().correctLibraryExtendedRecord(eq(commonRecordData), eq(record))).thenThrow(new ScripterException("error"));
        when(state.getSolrService().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(false);

        UpdateEnrichmentRecordAction updateEnrichmentRecordAction = new UpdateEnrichmentRecordAction(state, settings, record);
        updateEnrichmentRecordAction.performAction();
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
        Integer agencyId = AssertActionsUtil.getAgencyIdAsInteger(record);
        new MarcRecordWriter(record).markForDeletion();

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsForId(eq(recordId))).thenReturn(new HashSet<>());
        when(state.getSolrService().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(false);

        UpdateEnrichmentRecordAction instance = new UpdateEnrichmentRecordAction(state, settings, record);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertDeleteRecordAction(iterator.next(), state.getRawRepo(), record, MarcXChangeMimeType.ENRICHMENT);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID), MarcXChangeMimeType.ENRICHMENT);
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
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.recordId();
        Integer agencyId = reader.agencyIdAsInteger();
        new MarcRecordWriter(record).markForDeletion();

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsFor(eq(record))).thenReturn(AssertActionsUtil.createAgenciesSet(agencyId));
        when(state.getSolrService().hasDocuments(eq(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId)))).thenReturn(false);

        UpdateEnrichmentRecordAction instance = new UpdateEnrichmentRecordAction(state, settings, record);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), record);
        AssertActionsUtil.assertDeleteRecordAction(iterator.next(), state.getRawRepo(), record, MarcXChangeMimeType.ENRICHMENT);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), record, settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID), MarcXChangeMimeType.ENRICHMENT);
        assertFalse(iterator.hasNext());
    }

    private Record createRawRepoRecord(MarcRecord record, String mimetype) throws JAXBException, UnsupportedEncodingException {
        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.recordId();
        Integer agencyId = reader.agencyIdAsInteger();
        RawRepoRecordMock rawRepoRecord = new RawRepoRecordMock(recordId, agencyId);
        rawRepoRecord.setDeleted(false);
        rawRepoRecord.setMimeType(mimetype);
        rawRepoRecord.setContent(new RawRepoEncoder().encodeRecord(record));
        return rawRepoRecord;
    }
}
