/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

public class CreateEnrichmentRecordWithClassificationsActionTest {
    private GlobalActionState state;
    private Properties settings;

    @Before
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
        settings = new UpdateTestUtils().getSettings();
    }

    /**
     * Test CreateEnrichmentRecordActionForlinkedRecords.performAction(): Create enrichment record.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with a common record.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Perform actions to create a new enrichment record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>StoreRecordAction: Store the record</li>
     * <li>LinkRecordAction: Link the new enrichment record to the common record</li>
     * <li>EnqueueRecordAction: Put the enrichment record in queue</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_CommonRecordIdIsNull() throws Exception {
        MarcRecord commonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(enrichmentRecord);
        String recordId = reader.getRecordId();
        String agencyId = reader.getAgencyId();

        when(state.getLibraryRecordsHandler().createLibraryExtendedRecord(isNull(), eq(commonRecord), eq(agencyId))).thenReturn(enrichmentRecord);

        CreateEnrichmentRecordWithClassificationsAction createEnrichmentRecordWithClassificationsAction = new CreateEnrichmentRecordWithClassificationsAction(state, settings, agencyId);
        createEnrichmentRecordWithClassificationsAction.setUpdatingCommonRecord(commonRecord);

        assertThat(createEnrichmentRecordWithClassificationsAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = createEnrichmentRecordWithClassificationsAction.children();
        assertThat(children.size(), is(3));

        ServiceAction child = children.get(0);
        assertSame(child.getClass(), StoreRecordAction.class);

        StoreRecordAction storeRecordAction = (StoreRecordAction) child;
        assertThat(storeRecordAction.getRawRepo(), is(state.getRawRepo()));
        assertThat(storeRecordAction.getRecord(), is(enrichmentRecord));
        assertThat(storeRecordAction.getMimetype(), equalTo(MarcXChangeMimeType.ENRICHMENT));

        child = children.get(1);
        assertSame(child.getClass(), LinkRecordAction.class);

        LinkRecordAction linkRecordAction = (LinkRecordAction) child;
        assertThat(linkRecordAction.getRawRepo(), is(state.getRawRepo()));
        assertThat(linkRecordAction.getRecord(), is(enrichmentRecord));
        assertThat(linkRecordAction.getLinkToRecordId(), equalTo(new RecordId(recordId, RawRepo.COMMON_AGENCY)));

        child = children.get(2);
        assertSame(child.getClass(), EnqueueRecordAction.class);

        EnqueueRecordAction enqueueRecordAction = (EnqueueRecordAction) child;
        assertThat(enqueueRecordAction.getRawRepo(), is(state.getRawRepo()));
        assertThat(enqueueRecordAction.getRecord(), is(enrichmentRecord));
    }

    /**
     * Test CreateEnrichmentRecordActionForlinkedRecords.performAction(): Create enrichment record.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with a common record.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Perform actions to create a new enrichment record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>StoreRecordAction: Store the record</li>
     * <li>LinkRecordAction: Link the new enrichment record to the common record</li>
     * <li>EnqueueRecordAction: Put the enrichment record in queue</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_CommonRecordIdIsSet() throws Exception {
        String commonRecordId = "3 456 789 4";
        MarcRecord commonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE, commonRecordId);
        MarcRecordReader reader = new MarcRecordReader(enrichmentRecord);
        String recordId = reader.getRecordId();
        String agencyId = reader.getAgencyId();

        when(state.getLibraryRecordsHandler().createLibraryExtendedRecord(isNull(), eq(commonRecord), eq(agencyId))).thenReturn(enrichmentRecord);

        CreateEnrichmentRecordWithClassificationsAction instance = new CreateEnrichmentRecordWithClassificationsAction(state, settings, agencyId);
        instance.setUpdatingCommonRecord(commonRecord);
        instance.setTargetRecordId(commonRecordId);

        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = instance.children();
        assertThat(children.size(), is(3));

        ServiceAction child = children.get(0);
        assertSame(child.getClass(), StoreRecordAction.class);

        StoreRecordAction storeRecordAction = (StoreRecordAction) child;
        assertThat(storeRecordAction.getRawRepo(), is(state.getRawRepo()));
        assertThat(storeRecordAction.getRecord(), is(enrichmentRecord));
        assertThat(storeRecordAction.getMimetype(), equalTo(MarcXChangeMimeType.ENRICHMENT));

        child = children.get(1);
        assertSame(child.getClass(), LinkRecordAction.class);

        LinkRecordAction linkRecordAction = (LinkRecordAction) child;
        assertThat(linkRecordAction.getRawRepo(), is(state.getRawRepo()));
        assertThat(linkRecordAction.getRecord(), is(enrichmentRecord));
        assertThat(linkRecordAction.getLinkToRecordId(), equalTo(new RecordId(recordId, RawRepo.COMMON_AGENCY)));

        child = children.get(2);
        assertSame(child.getClass(), EnqueueRecordAction.class);

        EnqueueRecordAction enqueueRecordAction = (EnqueueRecordAction) child;
        assertThat(enqueueRecordAction.getRawRepo(), is(state.getRawRepo()));
        assertThat(enqueueRecordAction.getRecord(), is(enrichmentRecord));
    }

    /**
     * Test CreateEnrichmentRecordActionForlinkedRecords.performAction(): Create enrichment record.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with a common record.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Perform actions to create a new enrichment record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>StoreRecordAction: Store the record</li>
     * <li>LinkRecordAction: Link the new enrichment record to the common record</li>
     * <li>EnqueueRecordAction: Put the enrichment record in queue</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test(expected = UpdateException.class)
    public void testPerformAction_ScripterException() throws Exception {
        MarcRecord commonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);

        MarcRecordReader reader = new MarcRecordReader(enrichmentRecord);
        String agencyId = reader.getAgencyId();

        when(state.getLibraryRecordsHandler().createLibraryExtendedRecord(isNull(), eq(commonRecord), eq(agencyId))).thenThrow(new UpdateException("Script error"));

        CreateEnrichmentRecordWithClassificationsAction instance = new CreateEnrichmentRecordWithClassificationsAction(state, settings, agencyId);
        instance.setUpdatingCommonRecord(commonRecord);
        instance.performAction();
    }
}
