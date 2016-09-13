package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordWriter;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.ws.JNDIResources;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

// TODO : Pt indeholder testposterne i de fleste test ikke noget der hænger sammen - det bør rettes op
// da det kan virke ret forvirrende. Senest hvis det skal opensources.
public class UpdateOperationActionTest {
    private GlobalActionState state;
    private Properties settings = new UpdateTestUtils().getSettings();

    @Before
    public void before() throws IOException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
    }

    /**
     * Test performAction(): Update a local record.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * <ol>
     * <li>A local single record.</li>
     * </ol>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update the record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>AuthenticateRecordAction: Authentication of the record</li>
     * <li>UpdateLocalRecordAction: Update the local record</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_LocalRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        Integer agencyId = AssertActionsUtil.getAgencyIdAsInteger(record);

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(false);
        when(state.getRawRepo().recordExists(eq(recordId), eq(RawRepo.RAWREPO_COMMON_LIBRARY))).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature(agencyId.toString(), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS)).thenReturn(true);
        List<MarcRecord> rawRepoRecords = Collections.singletonList(record);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(record), eq(UpdateTestUtils.USER_ID), eq(UpdateTestUtils.GROUP_ID))).thenReturn(rawRepoRecords);

        UpdateOperationAction updateOperationAction = new UpdateOperationAction(state, settings, record);
        assertThat(updateOperationAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateOperationAction.children();
        assertThat(children.size(), is(2));
        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction(iterator.next(), record, state.getAuthenticator(), state.getUpdateRecordRequest().getAuthentication());
        AssertActionsUtil.assertUpdateLocalRecordAction(iterator.next(), state.getRawRepo(), record, state.getHoldingsItems());
    }

    /**
     * Test performAction(): Update an enrichment record.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * <ol>
     * <li>A local single record.</li>
     * <li>The library for the record being updated has the feature 'create_enrichments'</li>
     * </ol>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update the record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>AuthenticateRecordAction: Authentication of the record</li>
     * <li>UpdateEnrichmentRecordAction: Update the local record</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_EnrichmentRecord_WithFeature_CreateEnrichments() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        Integer agencyId = AssertActionsUtil.getAgencyIdAsInteger(record);
        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        Integer enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInteger(enrichmentRecord);

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(recordId), eq(enrichmentAgencyId))).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature(eq(UpdateTestUtils.GROUP_ID), eq(LibraryRuleHandler.Rule.CREATE_ENRICHMENTS))).thenReturn(true);
        List<MarcRecord> rawRepoRecords = Collections.singletonList(enrichmentRecord);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(enrichmentRecord), eq(UpdateTestUtils.USER_ID), eq(UpdateTestUtils.GROUP_ID))).thenReturn(rawRepoRecords);

        UpdateOperationAction instance = new UpdateOperationAction(state, settings, enrichmentRecord);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = instance.children();
        assertThat(children.size(), is(2));
        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction(iterator.next(), enrichmentRecord, state.getAuthenticator(), state.getUpdateRecordRequest().getAuthentication());
        AssertActionsUtil.assertUpdateEnrichmentRecordAction(iterator.next(), state.getRawRepo(), enrichmentRecord, state.getLibraryRecordsHandler(), state.getHoldingsItems());
    }

    /**
     * Test performAction(): Update an enrichment record.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * <ol>
     * <li>A local single record.</li>
     * <li>The library for the record being updated has not the feature 'create_enrichments'</li>
     * </ol>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update the record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>AuthenticateRecordAction: Authentication of the record</li>
     * <li>UpdateLocalRecordAction: Update the local record</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_EnrichmentRecord_NotWithFeature_CreateEnrichments() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        Integer agencyId = AssertActionsUtil.getAgencyIdAsInteger(record);
        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        Integer enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInteger(enrichmentRecord);

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(recordId), eq(enrichmentAgencyId))).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature(enrichmentAgencyId.toString(), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS)).thenReturn(false);
        List<MarcRecord> rawRepoRecords = Collections.singletonList(enrichmentRecord);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(enrichmentRecord), eq(UpdateTestUtils.USER_ID), eq(UpdateTestUtils.GROUP_ID))).thenReturn(rawRepoRecords);

        UpdateOperationAction updateOperationAction = new UpdateOperationAction(state, settings, enrichmentRecord);
        assertThat(updateOperationAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateOperationAction.children();
        assertThat(children.size(), is(2));
        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction(iterator.next(), enrichmentRecord, state.getAuthenticator(), state.getUpdateRecordRequest().getAuthentication());
        AssertActionsUtil.assertUpdateLocalRecordAction(iterator.next(), state.getRawRepo(), enrichmentRecord, state.getHoldingsItems());
    }

    /**
     * Test performAction(): Create a new common record.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * Empty rawrepo.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Create a new common record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>AuthenticateRecordAction: Authentication of the record</li>
     * <li>UpdateCommonRecordAction: Update common record</li>
     * <li>UpdateEnrichmentRecordAction: Update DBC enrichment record</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_CreateCommonRecord() throws Exception {
        // Load a 191919 record - this is the rawrepo record
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        Integer agencyId = AssertActionsUtil.getAgencyIdAsInteger(record);

        // Load an enrichment record. Set the library to 870970 in 001*b
        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter writer = new MarcRecordWriter(enrichmentRecord);
        writer.addOrReplaceSubfield("001", "b", RawRepo.COMMON_LIBRARY.toString());
        Integer enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInteger(enrichmentRecord);

        // Load the updating record - set the library to 870970 in 001*b
        MarcRecord updateRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter updwriter = new MarcRecordWriter(updateRecord);
        updwriter.addOrReplaceSubfield("001", "a", "206111600");
        updwriter.addOrReplaceSubfield("001", "b", RawRepo.COMMON_LIBRARY.toString());

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(recordId), eq(enrichmentAgencyId))).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature(state.getUpdateRecordRequest().getAuthentication().getGroupIdAut(), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS)).thenReturn(true);
        when(state.getOpenAgencyService().hasFeature(state.getUpdateRecordRequest().getAuthentication().getGroupIdAut(), LibraryRuleHandler.Rule.AUTH_CREATE_COMMON_RECORD)).thenReturn(true);
        List<MarcRecord> rawRepoRecords = Arrays.asList(record, enrichmentRecord);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(updateRecord), eq(UpdateTestUtils.USER_ID), eq(UpdateTestUtils.GROUP_ID))).thenReturn(rawRepoRecords);

        // TEST 1 - REMEMBER - this test doesn't say anything about the success or failure of the create - just that the correct actions are created !!!!
        // Test environment is : common rec owned by DBC, enrichment owned by 723000, update record owned by DBC
        // this shall not create an doublerecord action
        settings.put(JNDIResources.JAVASCRIPT_INSTALL_NAME_KEY, "dataio");
        updwriter.addOrReplaceSubfield("001", "b", RawRepo.RAWREPO_COMMON_LIBRARY.toString());

        UpdateOperationAction updateOperationAction = new UpdateOperationAction(state, settings, updateRecord);
        assertThat(updateOperationAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateOperationAction.children();
        assertThat(children.size(), is(3));
        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction(iterator.next(), updateRecord, state.getAuthenticator(), state.getUpdateRecordRequest().getAuthentication());
        AssertActionsUtil.assertUpdateCommonRecordAction(iterator.next(), state.getRawRepo(), record, UpdateTestUtils.GROUP_ID, state.getLibraryRecordsHandler(), state.getHoldingsItems(), state.getOpenAgencyService());
        AssertActionsUtil.assertUpdateEnrichmentRecordAction(iterator.next(), state.getRawRepo(), enrichmentRecord, state.getLibraryRecordsHandler(), state.getHoldingsItems());
        assertThat(iterator.hasNext(), is(false));

        // TEST 2 - REMEMBER - this test doesn't say anything about the success or failure of the create - just that the correct actions are created !!!!
        // Same as before but owner of updating record set to 810010
        // this shall create an doublerecord action
        settings.put(JNDIResources.JAVASCRIPT_INSTALL_NAME_KEY, "fbs");
        updwriter.addOrReplaceSubfield("996", "a", "810010");

        updateOperationAction = new UpdateOperationAction(state, settings, updateRecord);
        assertThat(updateOperationAction.performAction(), equalTo(ServiceResult.newOkResult()));
        children = updateOperationAction.children();
        assertThat(children.size(), is(5));
        iterator = children.listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction(iterator.next(), updateRecord, state.getAuthenticator(), state.getUpdateRecordRequest().getAuthentication());
        AssertActionsUtil.assertDoubleRecordFrontendAction(iterator.next(), updateRecord, state.getScripter());
        AssertActionsUtil.assertUpdateCommonRecordAction(iterator.next(), state.getRawRepo(), record, UpdateTestUtils.GROUP_ID, state.getLibraryRecordsHandler(), state.getHoldingsItems(), state.getOpenAgencyService());
        AssertActionsUtil.assertUpdateEnrichmentRecordAction(iterator.next(), state.getRawRepo(), enrichmentRecord, state.getLibraryRecordsHandler(), state.getHoldingsItems());
        AssertActionsUtil.assertDoubleRecordCheckingAction(iterator.next(), updateRecord, state.getScripter());
        assertThat(iterator.hasNext(), is(false));

        // TEST 3 - Doublepost frontend, forced update, key found
        String doubleRecordKey = "8d83dc66-87df-4ef5-a50f-82e9e870c66c";
        state.getUpdateRecordRequest().setDoubleRecordKey(doubleRecordKey);
        when(state.getUpdateStore().doesDoubleRecordKeyExist(eq(doubleRecordKey))).thenReturn(true);
        updateOperationAction = new UpdateOperationAction(state, settings, updateRecord);
        assertThat(updateOperationAction.performAction(), equalTo(ServiceResult.newOkResult()));
        children = updateOperationAction.children();
        assertThat(children.size(), is(4));
        iterator = children.listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction(iterator.next(), updateRecord, state.getAuthenticator(), state.getUpdateRecordRequest().getAuthentication());
        AssertActionsUtil.assertUpdateCommonRecordAction(iterator.next(), state.getRawRepo(), record, UpdateTestUtils.GROUP_ID, state.getLibraryRecordsHandler(), state.getHoldingsItems(), state.getOpenAgencyService());
        AssertActionsUtil.assertUpdateEnrichmentRecordAction(iterator.next(), state.getRawRepo(), enrichmentRecord, state.getLibraryRecordsHandler(), state.getHoldingsItems());
        AssertActionsUtil.assertDoubleRecordCheckingAction(iterator.next(), updateRecord, state.getScripter());
        assertThat(iterator.hasNext(), is(false));

        // TEST 4 - Doublepost frontend, forced update, key not found
        when(state.getUpdateStore().doesDoubleRecordKeyExist(eq(doubleRecordKey))).thenReturn(false);
        updateOperationAction = new UpdateOperationAction(state, settings, updateRecord);
        String message = String.format(state.getMessages().getString("double.record.frontend.unknown.key"), doubleRecordKey);
        assertThat(updateOperationAction.performAction(), equalTo(ServiceResult.newDoubleRecordErrorResult(UpdateStatusEnum.FAILED, message, state)));
    }

    /**
     * Test performAction(): Deletes a common record that exists.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A rawrepo with a common record.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Try to delete the record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>AuthenticateRecordAction: Authentication of the record</li>
     * <li>UpdateCommonRecordAction: Update common record</li>
     * <li>UpdateEnrichmentRecordAction: Update DBC enrichment record</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_DeleteCommonRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        Integer agencyId = AssertActionsUtil.getAgencyIdAsInteger(record);
        MarcRecordWriter recordWriter = new MarcRecordWriter(record);
        recordWriter.markForDeletion();

        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter enrichmentWriter = new MarcRecordWriter(enrichmentRecord);
        enrichmentWriter.addOrReplaceSubfield("001", "b", RawRepo.COMMON_LIBRARY.toString());
        Integer enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInteger(enrichmentRecord);

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(recordId), eq(enrichmentAgencyId))).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature(agencyId.toString(), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS)).thenReturn(true);
        List<MarcRecord> rawRepoRecords = Arrays.asList(record, enrichmentRecord);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(record), eq(UpdateTestUtils.USER_ID), eq(UpdateTestUtils.GROUP_ID))).thenReturn(rawRepoRecords);

        UpdateOperationAction updateOperationAction = new UpdateOperationAction(state, settings, record);
        assertThat(updateOperationAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateOperationAction.children();
        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction(iterator.next(), record, state.getAuthenticator(), state.getUpdateRecordRequest().getAuthentication());
        AssertActionsUtil.assertUpdateCommonRecordAction(iterator.next(), state.getRawRepo(), record, UpdateTestUtils.GROUP_ID, state.getLibraryRecordsHandler(), state.getHoldingsItems(), state.getOpenAgencyService());
        AssertActionsUtil.assertUpdateEnrichmentRecordAction(iterator.next(), state.getRawRepo(), enrichmentRecord, state.getLibraryRecordsHandler(), state.getHoldingsItems());
        assertThat(iterator.hasNext(), is(false));
    }

    /**
     * Test performAction(): Deletes a common record that does not exist.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * An empty rawrepo.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Try to delete a common record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_DeleteCommonRecord_NotExist() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(record);
        Integer agencyId = AssertActionsUtil.getAgencyIdAsInteger(record);
        MarcRecordWriter recordWriter = new MarcRecordWriter(record);
        recordWriter.markForDeletion();

        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter enrichmentWriter = new MarcRecordWriter(enrichmentRecord);
        enrichmentWriter.addOrReplaceSubfield("001", "b", RawRepo.COMMON_LIBRARY.toString());
        Integer enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInteger(enrichmentRecord);

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(false);
        when(state.getRawRepo().recordExists(eq(recordId), eq(enrichmentAgencyId))).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature(agencyId.toString(), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS)).thenReturn(true);
        List<MarcRecord> rawRepoRecords = Arrays.asList(record, enrichmentRecord);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(record), eq(UpdateTestUtils.USER_ID), eq(UpdateTestUtils.GROUP_ID))).thenReturn(rawRepoRecords);

        UpdateOperationAction updateOperationAction = new UpdateOperationAction(state, settings, record);
        String message = state.getMessages().getString("operation.delete.non.existing.record");
        assertThat(updateOperationAction.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnum.FAILED, message, state)));
    }

    /**
     * Test performAction(): Create a new common school enrichment record.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * Rawrepo with:
     * <ul>
     * <li>A common record <code>c1</code></li>
     * <li>A common school record <code>s1</code></li>
     * </ul>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update the common school record <code>s1</code> with new data.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>UpdateSchoolCommonRecord: Update the common school record</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_CreateCommonSchoolEnrichment() throws Exception {
        MarcRecord commonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(commonRecord);
        MarcRecord commonSchoolRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SCHOOL_RECORD_RESOURCE);

        when(state.getRawRepo().recordExists(eq(recordId), eq(RawRepo.RAWREPO_COMMON_LIBRARY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(recordId), eq(RawRepo.SCHOOL_COMMON_AGENCY))).thenReturn(true);
        List<MarcRecord> rawRepoRecords = Collections.singletonList(commonSchoolRecord);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(commonSchoolRecord), eq(UpdateTestUtils.USER_ID), eq(UpdateTestUtils.GROUP_ID))).thenReturn(rawRepoRecords);

        UpdateOperationAction updateOperationAction = new UpdateOperationAction(state, settings, commonSchoolRecord);
        assertThat(updateOperationAction.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = updateOperationAction.children().listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction(iterator.next(), commonSchoolRecord, state.getAuthenticator(), state.getUpdateRecordRequest().getAuthentication());
        AssertActionsUtil.assertSchoolCommonRecordAction(iterator.next(), state.getRawRepo(), commonSchoolRecord, state.getLibraryRecordsHandler(), state.getHoldingsItems(), settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID));
        assertThat(iterator.hasNext(), is(false));
    }

    /**
     * Test performAction(): Create a new school enrichment record.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * Rawrepo with:
     * <ul>
     * <li>A common record <code>c1</code></li>
     * </ul>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update the school enrichment record <code>s1</code> with new data.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Create child actions:
     * <ol>
     * <li>UpdateSchoolEnrichmentRecordAction: Update the common school record</li>
     * </ol>
     * Return status: OK
     * </dd>
     * </dl>
     */
    @Test
    public void testPerformAction_CreateSchoolEnrichment() throws Exception {
        MarcRecord commonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        String recordId = AssertActionsUtil.getRecordId(commonRecord);
        MarcRecord schoolRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.SCHOOL_RECORD_RESOURCE);
        String groupId = AssertActionsUtil.getAgencyId(schoolRecord);
        state.getUpdateRecordRequest().getAuthentication().setGroupIdAut(groupId);

        when(state.getRawRepo().recordExists(eq(recordId), eq(RawRepo.RAWREPO_COMMON_LIBRARY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(recordId), eq(RawRepo.SCHOOL_COMMON_AGENCY))).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.CREATE_ENRICHMENTS)).thenReturn(true);
        List<MarcRecord> rawRepoRecords = Collections.singletonList(schoolRecord);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(schoolRecord), eq(UpdateTestUtils.USER_ID), eq(groupId))).thenReturn(rawRepoRecords);

        UpdateOperationAction instance = new UpdateOperationAction(state, settings, schoolRecord);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction(iterator.next(), schoolRecord, state.getAuthenticator(), state.getUpdateRecordRequest().getAuthentication());
        AssertActionsUtil.assertSchoolEnrichmentRecordAction(iterator.next(), state.getRawRepo(), schoolRecord, state.getLibraryRecordsHandler(), state.getHoldingsItems(), settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID));
        assertThat(iterator.hasNext(), is(false));
    }
}
