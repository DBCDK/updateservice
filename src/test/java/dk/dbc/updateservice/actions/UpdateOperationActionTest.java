package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.*;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.OpenAgencyService;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.*;

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
    OpenAgencyService.LibraryGroup libraryGroupDBC = OpenAgencyService.LibraryGroup.DBC;
    OpenAgencyService.LibraryGroup libraryGroupFBS = OpenAgencyService.LibraryGroup.FBS;

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
        state.setMarcRecord(record);
        String recordId = AssertActionsUtil.getRecordId(record);
        Integer agencyId = AssertActionsUtil.getAgencyIdAsInteger(record);

        state.setLibraryGroup(libraryGroupFBS);
        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(false);
        when(state.getRawRepo().recordExists(eq(recordId), eq(RawRepo.RAWREPO_COMMON_LIBRARY))).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature(agencyId.toString(), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS)).thenReturn(true);
        List<MarcRecord> rawRepoRecords = Collections.singletonList(record);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(record), eq(state.getUpdateServiceRequestDTO().getAuthenticationDTO()), eq(libraryGroupFBS))).thenReturn(rawRepoRecords);

        UpdateOperationAction updateOperationAction = new UpdateOperationAction(state, settings);
        assertThat(updateOperationAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateOperationAction.children();
        assertThat(children.size(), is(2));
        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction(iterator.next(), record, state.getAuthenticator(), state.getUpdateServiceRequestDTO().getAuthenticationDTO());
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
        state.setMarcRecord(enrichmentRecord);
        Integer enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInteger(enrichmentRecord);
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId(enrichmentAgencyId.toString());

        state.setLibraryGroup(libraryGroupFBS);
        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(recordId), eq(enrichmentAgencyId))).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature(eq(enrichmentAgencyId.toString()), eq(LibraryRuleHandler.Rule.CREATE_ENRICHMENTS))).thenReturn(true);
        List<MarcRecord> rawRepoRecords = Collections.singletonList(enrichmentRecord);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(enrichmentRecord), eq(state.getUpdateServiceRequestDTO().getAuthenticationDTO()), eq(libraryGroupFBS))).thenReturn(rawRepoRecords);
        when(state.getRawRepo().fetchRecord(recordId, RawRepo.RAWREPO_COMMON_LIBRARY)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));

        UpdateOperationAction instance = new UpdateOperationAction(state, settings);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = instance.children();
        assertThat(children.size(), is(2));
        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction(iterator.next(), enrichmentRecord, state.getAuthenticator(), state.getUpdateServiceRequestDTO().getAuthenticationDTO());
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
        state.setMarcRecord(enrichmentRecord);
        Integer enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInteger(enrichmentRecord);
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId(enrichmentAgencyId.toString());

        state.setLibraryGroup(libraryGroupFBS);
        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(recordId), eq(enrichmentAgencyId))).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature(enrichmentAgencyId.toString(), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS)).thenReturn(false);
        List<MarcRecord> rawRepoRecords = Collections.singletonList(enrichmentRecord);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(enrichmentRecord), eq(state.getUpdateServiceRequestDTO().getAuthenticationDTO()), eq(libraryGroupFBS))).thenReturn(rawRepoRecords);
        when(state.getRawRepo().fetchRecord(recordId, RawRepo.RAWREPO_COMMON_LIBRARY)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));

        state.setMarcRecord(enrichmentRecord);
        UpdateOperationAction updateOperationAction = new UpdateOperationAction(state, settings);
        assertThat(updateOperationAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateOperationAction.children();
        assertThat(children.size(), is(2));
        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction(iterator.next(), enrichmentRecord, state.getAuthenticator(), state.getUpdateServiceRequestDTO().getAuthenticationDTO());
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
    public void testPerformAction_CreateCommonRecord_test1() throws Exception {
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
        state.setMarcRecord(updateRecord);
        MarcRecordWriter updwriter = new MarcRecordWriter(updateRecord);
        updwriter.addOrReplaceSubfield("001", "a", "206111600");
        updwriter.addOrReplaceSubfield("001", "b", RawRepo.RAWREPO_COMMON_LIBRARY.toString());

        state.setLibraryGroup(libraryGroupDBC);
        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(recordId), eq(enrichmentAgencyId))).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS)).thenReturn(true);
        when(state.getOpenAgencyService().hasFeature(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), LibraryRuleHandler.Rule.AUTH_CREATE_COMMON_RECORD)).thenReturn(true);
        List<MarcRecord> rawRepoRecords = Arrays.asList(record, enrichmentRecord);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(updateRecord), eq(state.getUpdateServiceRequestDTO().getAuthenticationDTO()), eq(libraryGroupDBC))).thenReturn(rawRepoRecords);

        // TEST 1 - REMEMBER - this test doesn't say anything about the success or failure of the create - just that the correct actions are created !!!!
        // Test environment is : common rec owned by DBC, enrichment owned by 723000, update record owned by DBC
        // this shall not create an doublerecord action
        UpdateOperationAction updateOperationAction = new UpdateOperationAction(state, settings);
        assertThat(updateOperationAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateOperationAction.children();
        assertThat(children.size(), is(3));
        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction(iterator.next(), updateRecord, state.getAuthenticator(), state.getUpdateServiceRequestDTO().getAuthenticationDTO());
        AssertActionsUtil.assertUpdateCommonRecordAction(iterator.next(), state.getRawRepo(), record, UpdateTestUtils.GROUP_ID, state.getLibraryRecordsHandler(), state.getHoldingsItems(), state.getOpenAgencyService());
        AssertActionsUtil.assertUpdateEnrichmentRecordAction(iterator.next(), state.getRawRepo(), enrichmentRecord, state.getLibraryRecordsHandler(), state.getHoldingsItems());
        assertThat(iterator.hasNext(), is(false));
    }

    @Test
    public void testPerformAction_CreateCommonRecord_test2() throws Exception {
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
        state.setMarcRecord(updateRecord);
        MarcRecordWriter updwriter = new MarcRecordWriter(updateRecord);
        updwriter.addOrReplaceSubfield("001", "a", "206111600");
        //updwriter.addOrReplaceSubfield("001", "b", RawRepo.RAWREPO_COMMON_LIBRARY.toString());

        state.setLibraryGroup(libraryGroupFBS);
        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(recordId), eq(enrichmentAgencyId))).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS)).thenReturn(true);
        when(state.getOpenAgencyService().hasFeature(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), LibraryRuleHandler.Rule.AUTH_CREATE_COMMON_RECORD)).thenReturn(true);
        List<MarcRecord> rawRepoRecords = Arrays.asList(record, enrichmentRecord);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(updateRecord), eq(state.getUpdateServiceRequestDTO().getAuthenticationDTO()), eq(libraryGroupFBS))).thenReturn(rawRepoRecords);

        // TEST 2 - REMEMBER - this test doesn't say anything about the success or failure of the create - just that the correct actions are created !!!!
        // Same as before but owner of updating record set to 810010
        // this shall create an doublerecord action
        updwriter.addOrReplaceSubfield("996", "a", "810010");

        UpdateOperationAction updateOperationAction = new UpdateOperationAction(state, settings);
        assertThat(updateOperationAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateOperationAction.children();
        System.out.println("children = " + children);
        assertThat(children.size(), is(5));
        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction(iterator.next(), updateRecord, state.getAuthenticator(), state.getUpdateServiceRequestDTO().getAuthenticationDTO());
        AssertActionsUtil.assertDoubleRecordFrontendAction(iterator.next(), updateRecord, state.getScripter());
        AssertActionsUtil.assertUpdateCommonRecordAction(iterator.next(), state.getRawRepo(), record, UpdateTestUtils.GROUP_ID, state.getLibraryRecordsHandler(), state.getHoldingsItems(), state.getOpenAgencyService());
        AssertActionsUtil.assertUpdateEnrichmentRecordAction(iterator.next(), state.getRawRepo(), enrichmentRecord, state.getLibraryRecordsHandler(), state.getHoldingsItems());
        AssertActionsUtil.assertDoubleRecordCheckingAction(iterator.next(), updateRecord, state.getScripter());
        assertThat(iterator.hasNext(), is(false));
    }

    @Test
    public void testPerformAction_CreateCommonRecord_test3() throws Exception {
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
        state.setMarcRecord(updateRecord);
        MarcRecordWriter updwriter = new MarcRecordWriter(updateRecord);
        updwriter.addOrReplaceSubfield("001", "a", "206111600");
        updwriter.addOrReplaceSubfield("001", "b", RawRepo.RAWREPO_COMMON_LIBRARY.toString());

        state.setLibraryGroup(libraryGroupFBS);
        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(recordId), eq(enrichmentAgencyId))).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS)).thenReturn(true);
        when(state.getOpenAgencyService().hasFeature(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), LibraryRuleHandler.Rule.AUTH_CREATE_COMMON_RECORD)).thenReturn(true);
        List<MarcRecord> rawRepoRecords = Arrays.asList(record, enrichmentRecord);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(updateRecord), eq(state.getUpdateServiceRequestDTO().getAuthenticationDTO()), eq(libraryGroupFBS))).thenReturn(rawRepoRecords);

        // TEST 3 - Doublepost frontend, forced update, key found
        String doubleRecordKey = "8d83dc66-87df-4ef5-a50f-82e9e870c66c";
        state.getUpdateServiceRequestDTO().setDoubleRecordKey(doubleRecordKey);
        when(state.getUpdateStore().doesDoubleRecordKeyExist(eq(doubleRecordKey))).thenReturn(true);

        UpdateOperationAction updateOperationAction = new UpdateOperationAction(state, settings);
        assertThat(updateOperationAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateOperationAction.children();
        assertThat(children.size(), is(4));
        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction(iterator.next(), updateRecord, state.getAuthenticator(), state.getUpdateServiceRequestDTO().getAuthenticationDTO());
        AssertActionsUtil.assertUpdateCommonRecordAction(iterator.next(), state.getRawRepo(), record, UpdateTestUtils.GROUP_ID, state.getLibraryRecordsHandler(), state.getHoldingsItems(), state.getOpenAgencyService());
        AssertActionsUtil.assertUpdateEnrichmentRecordAction(iterator.next(), state.getRawRepo(), enrichmentRecord, state.getLibraryRecordsHandler(), state.getHoldingsItems());
        AssertActionsUtil.assertDoubleRecordCheckingAction(iterator.next(), updateRecord, state.getScripter());
        assertThat(iterator.hasNext(), is(false));
    }

    @Test
    public void testPerformAction_CreateCommonRecord_test4() throws Exception {
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
        state.setMarcRecord(updateRecord);
        MarcRecordWriter updwriter = new MarcRecordWriter(updateRecord);
        updwriter.addOrReplaceSubfield("001", "a", "206111600");
        updwriter.addOrReplaceSubfield("001", "b", RawRepo.RAWREPO_COMMON_LIBRARY.toString());

        state.setLibraryGroup(libraryGroupFBS);
        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(recordId), eq(enrichmentAgencyId))).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS)).thenReturn(true);
        when(state.getOpenAgencyService().hasFeature(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), LibraryRuleHandler.Rule.AUTH_CREATE_COMMON_RECORD)).thenReturn(true);
        List<MarcRecord> rawRepoRecords = Arrays.asList(record, enrichmentRecord);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(updateRecord), eq(state.getUpdateServiceRequestDTO().getAuthenticationDTO()), eq(libraryGroupFBS))).thenReturn(rawRepoRecords);

        // TEST 4 - Doublepost frontend, forced update, key not found
        String doubleRecordKey = "8d83dc66-87df-4ef5-a50f-82e9e870c66c";
        state.getUpdateServiceRequestDTO().setDoubleRecordKey(doubleRecordKey);
        when(state.getUpdateStore().doesDoubleRecordKeyExist(eq(doubleRecordKey))).thenReturn(false);
        UpdateOperationAction updateOperationAction = new UpdateOperationAction(state, settings);
        String message = String.format(state.getMessages().getString("double.record.frontend.unknown.key"), doubleRecordKey);
        assertThat(updateOperationAction.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state)));
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
        state.setMarcRecord(record);

        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter enrichmentWriter = new MarcRecordWriter(enrichmentRecord);
        enrichmentWriter.addOrReplaceSubfield("001", "b", RawRepo.COMMON_LIBRARY.toString());
        Integer enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInteger(enrichmentRecord);

        state.setLibraryGroup(libraryGroupFBS);
        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(recordId), eq(enrichmentAgencyId))).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature(agencyId.toString(), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS)).thenReturn(true);
        List<MarcRecord> rawRepoRecords = Arrays.asList(record, enrichmentRecord);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(record), eq(state.getUpdateServiceRequestDTO().getAuthenticationDTO()), eq(libraryGroupFBS))).thenReturn(rawRepoRecords);
        when(state.getRawRepo().fetchRecord(eq(recordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));

        UpdateOperationAction updateOperationAction = new UpdateOperationAction(state, settings);
        assertThat(updateOperationAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateOperationAction.children();
        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction(iterator.next(), record, state.getAuthenticator(), state.getUpdateServiceRequestDTO().getAuthenticationDTO());
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
        state.setMarcRecord(record);
        state.setLibraryGroup(libraryGroupFBS);

        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter enrichmentWriter = new MarcRecordWriter(enrichmentRecord);
        enrichmentWriter.addOrReplaceSubfield("001", "b", RawRepo.COMMON_LIBRARY.toString());
        Integer enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInteger(enrichmentRecord);

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(false);
        when(state.getRawRepo().recordExists(eq(recordId), eq(enrichmentAgencyId))).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature(agencyId.toString(), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS)).thenReturn(true);
        List<MarcRecord> rawRepoRecords = Arrays.asList(record, enrichmentRecord);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(record), eq(state.getUpdateServiceRequestDTO().getAuthenticationDTO()), eq(libraryGroupFBS))).thenReturn(rawRepoRecords);
        when(state.getRawRepo().fetchRecord(eq(recordId), eq(agencyId))).thenReturn(null);

        UpdateOperationAction updateOperationAction = new UpdateOperationAction(state, settings);
        String message = state.getMessages().getString("operation.delete.non.existing.record");
        assertThat(updateOperationAction.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state)));
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
        state.setMarcRecord(commonSchoolRecord);
        state.setLibraryGroup(libraryGroupFBS);

        when(state.getRawRepo().recordExists(eq(recordId), eq(RawRepo.RAWREPO_COMMON_LIBRARY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(recordId), eq(RawRepo.SCHOOL_COMMON_AGENCY))).thenReturn(true);
        List<MarcRecord> rawRepoRecords = Collections.singletonList(commonSchoolRecord);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(commonSchoolRecord), eq(state.getUpdateServiceRequestDTO().getAuthenticationDTO()), eq(libraryGroupFBS))).thenReturn(rawRepoRecords);
        when(state.getRawRepo().fetchRecord(recordId, RawRepo.RAWREPO_COMMON_LIBRARY)).thenReturn(AssertActionsUtil.createRawRepoRecord(commonRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().fetchRecord(recordId, RawRepo.SCHOOL_COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(commonSchoolRecord, MarcXChangeMimeType.MARCXCHANGE));

        UpdateOperationAction updateOperationAction = new UpdateOperationAction(state, settings);
        assertThat(updateOperationAction.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = updateOperationAction.children().listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction(iterator.next(), commonSchoolRecord, state.getAuthenticator(), state.getUpdateServiceRequestDTO().getAuthenticationDTO());
        AssertActionsUtil.assertSchoolCommonRecordAction(iterator.next(), state.getRawRepo(), commonSchoolRecord, state.getLibraryRecordsHandler(), state.getHoldingsItems(), settings.getProperty(state.getRawRepoProviderId()));
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
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId(groupId);
        state.setMarcRecord(schoolRecord);
        state.setLibraryGroup(libraryGroupFBS);

        when(state.getRawRepo().recordExists(eq(recordId), eq(RawRepo.RAWREPO_COMMON_LIBRARY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(recordId), eq(RawRepo.SCHOOL_COMMON_AGENCY))).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.CREATE_ENRICHMENTS)).thenReturn(true);
        List<MarcRecord> rawRepoRecords = Collections.singletonList(schoolRecord);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(schoolRecord), eq(state.getUpdateServiceRequestDTO().getAuthenticationDTO()), eq(libraryGroupFBS))).thenReturn(rawRepoRecords);
        when(state.getRawRepo().fetchRecord(recordId, RawRepo.RAWREPO_COMMON_LIBRARY)).thenReturn(AssertActionsUtil.createRawRepoRecord(commonRecord, MarcXChangeMimeType.MARCXCHANGE));

        UpdateOperationAction instance = new UpdateOperationAction(state, settings);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction(iterator.next(), schoolRecord, state.getAuthenticator(), state.getUpdateServiceRequestDTO().getAuthenticationDTO());
        AssertActionsUtil.assertSchoolEnrichmentRecordAction(iterator.next(), state.getRawRepo(), schoolRecord, state.getLibraryRecordsHandler(), state.getHoldingsItems(), settings.getProperty(state.getRawRepoProviderId()));
        assertThat(iterator.hasNext(), is(false));
    }

    @Test
    public void testPreviousFaust_NewRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        List<MarcField> fields = record.getFields();

        MarcField field002 = new MarcField("002", "00");
        List<MarcSubField> subfields = new ArrayList<>();
        subfields.add(new MarcSubField("a", "12345678"));
        subfields.add(new MarcSubField("b", "12345678"));
        subfields.add(new MarcSubField("c", "12345678"));
        field002.setSubfields(subfields);
        fields.add(field002);
        state.setMarcRecord(record);
        state.setLibraryGroup(libraryGroupFBS);
        when(state.getRawRepo().fetchRecord(reader.recordId(), reader.agencyIdAsInteger())).thenReturn(null);

        UpdateOperationAction instance = new UpdateOperationAction(state, settings);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));
    }

    @Test
    public void testPreviousFaust_UpdateRecord_NoMatch() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        state.setMarcRecord(record);
        state.setLibraryGroup(libraryGroupFBS);
        when(state.getRawRepo().fetchRecord(reader.recordId(), reader.agencyIdAsInteger())).thenReturn(null);
        UpdateOperationAction instance = new UpdateOperationAction(state, settings);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));
    }

    @Test
    public void testPreviousFaust_UpdateRecord_Match002a() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        List<MarcField> fields = record.getFields();
        MarcField field002 = new MarcField("002", "00");
        List<MarcSubField> subfields = new ArrayList<>();
        subfields.add(new MarcSubField("a", "12345678"));
        field002.setSubfields(subfields);
        fields.add(field002);
        state.setMarcRecord(record);
        state.setLibraryGroup(libraryGroupFBS);

        when(state.getRawRepo().recordExists(reader.recordId(), RawRepo.RAWREPO_COMMON_LIBRARY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.recordId(), RawRepo.RAWREPO_COMMON_LIBRARY)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getSolrService().hasDocuments(SolrServiceIndexer.createSubfieldQueryWithExcludeDBCOnly("002a", "12345678", "001a", reader.recordId()))).thenReturn(true);

        UpdateOperationAction instance = new UpdateOperationAction(state, settings);
        String message = state.getMessages().getString("update.record.with.002.links");
        assertThat(instance.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state)));
    }

    @Test
    public void testPreviousFaust_UpdateRecord_Match002a_NoExisting() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        List<MarcField> fields = record.getFields();
        MarcField field002 = new MarcField("002", "00");
        List<MarcSubField> subfields = new ArrayList<>();
        subfields.add(new MarcSubField("a", "12345678"));
        field002.setSubfields(subfields);
        fields.add(field002);
        state.setMarcRecord(record);
        state.setLibraryGroup(libraryGroupFBS);

        when(state.getRawRepo().recordExists(reader.recordId(), RawRepo.RAWREPO_COMMON_LIBRARY)).thenReturn(false);
        when(state.getSolrService().hasDocuments(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", "12345678"))).thenReturn(true);

        UpdateOperationAction instance = new UpdateOperationAction(state, settings);
        String message = state.getMessages().getString("update.record.with.002.links");
        assertThat(instance.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state)));
    }

    @Test
    public void testPreviousFaust_UpdateRecord_Match002bc() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        List<MarcField> fields = record.getFields();
        MarcField field002 = new MarcField("002", "00");
        List<MarcSubField> subfields = new ArrayList<>();
        subfields.add(new MarcSubField("b", "12345678"));
        subfields.add(new MarcSubField("c", "12345678"));
        field002.setSubfields(subfields);
        fields.add(field002);
        state.setMarcRecord(record);
        state.setLibraryGroup(libraryGroupFBS);

        when(state.getRawRepo().recordExists(reader.recordId(), RawRepo.RAWREPO_COMMON_LIBRARY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.recordId(), RawRepo.RAWREPO_COMMON_LIBRARY)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getSolrService().hasDocuments(SolrServiceIndexer.createSubfieldQueryDualWithExcludeDBCOnly("002b", "12345678", "002c", "12345678", "001a", reader.recordId()))).thenReturn(true);

        UpdateOperationAction instance = new UpdateOperationAction(state, settings);
        String message = state.getMessages().getString("update.record.with.002.links");
        assertThat(instance.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state)));
    }

    @Test
    public void testPreviousFaust_UpdateRecord_Match002bc_NoExisting() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        List<MarcField> fields = record.getFields();
        MarcField field002 = new MarcField("002", "00");
        List<MarcSubField> subfields = new ArrayList<>();
        subfields.add(new MarcSubField("b", "12345678"));
        subfields.add(new MarcSubField("c", "12345678"));
        field002.setSubfields(subfields);
        fields.add(field002);
        state.setMarcRecord(record);
        state.setLibraryGroup(libraryGroupFBS);

        when(state.getRawRepo().recordExists(reader.recordId(), RawRepo.RAWREPO_COMMON_LIBRARY)).thenReturn(false);
        when(state.getSolrService().hasDocuments(SolrServiceIndexer.createSubfieldQueryDualDBCOnly("002b", "12345678", "002c", "12345678"))).thenReturn(true);

        UpdateOperationAction instance = new UpdateOperationAction(state, settings);
        String message = state.getMessages().getString("update.record.with.002.links");
        assertThat(instance.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state)));
    }

    @Test
    public void testPreviousFaust_UpdateRecordRemove002_NoConflict() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        MarcRecord existingRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        List<MarcField> fields = existingRecord.getFields();
        MarcField field002 = new MarcField("002", "00");
        List<MarcSubField> subfields = new ArrayList<>();
        subfields.add(new MarcSubField("a", "12345678"));
        field002.setSubfields(subfields);
        fields.add(field002);
        state.setMarcRecord(record);
        state.setLibraryGroup(libraryGroupFBS);

        when(state.getRawRepo().fetchRecord(reader.recordId(), reader.agencyIdAsInteger())).thenReturn(AssertActionsUtil.createRawRepoRecord(existingRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsForId("12345678")).thenReturn(new HashSet<>());

        UpdateOperationAction instance = new UpdateOperationAction(state, settings);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));
    }

    @Test
    public void testPreviousFaust_UpdateRecordRemove002_Conflict() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        MarcRecord existingRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        List<MarcField> fields = existingRecord.getFields();
        MarcField field002 = new MarcField("002", "00");
        List<MarcSubField> subfields = new ArrayList<>();
        subfields.add(new MarcSubField("a", "12345678"));
        field002.setSubfields(subfields);
        fields.add(field002);
        existingRecord.setFields(fields);
        Set<Integer> holdingList = new HashSet<>();
        holdingList.add(123);
        holdingList.add(456);
        state.setMarcRecord(record);
        state.setLibraryGroup(libraryGroupFBS);

        when(state.getRawRepo().recordExists(reader.recordId(), RawRepo.RAWREPO_COMMON_LIBRARY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.recordId(), RawRepo.RAWREPO_COMMON_LIBRARY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsForId("12345678")).thenReturn(holdingList);

        UpdateOperationAction instance = new UpdateOperationAction(state, settings);
        String message = state.getMessages().getString("update.record.holdings.on.002a");
        assertThat(instance.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state)));
    }

    @Test
    public void testPreviousFaust_DeleteRecord_Ok() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.markForDeletion();
        MarcRecord existingRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        Set<Integer> holdingList = new HashSet<>();
        state.setMarcRecord(record);
        state.setLibraryGroup(libraryGroupFBS);

        when(state.getRawRepo().recordExists(reader.recordId(), RawRepo.RAWREPO_COMMON_LIBRARY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.recordId(), reader.agencyIdAsInteger())).thenReturn(AssertActionsUtil.createRawRepoRecord(existingRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsForId("12345678")).thenReturn(holdingList);

        UpdateOperationAction instance = new UpdateOperationAction(state, settings);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));
    }

    @Test
    public void testPreviousFaust_DeleteRecord_HoldingOn001() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.markForDeletion();
        MarcRecord existingRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        List<MarcField> fields = existingRecord.getFields();
        MarcField field002 = new MarcField("002", "00");
        List<MarcSubField> subfields = new ArrayList<>();
        subfields.add(new MarcSubField("a", "12345678"));
        field002.setSubfields(subfields);
        fields.add(field002);
        Set<Integer> holdingList = new HashSet<>();
        holdingList.add(123);
        holdingList.add(456);
        state.setMarcRecord(record);
        state.setLibraryGroup(libraryGroupFBS);

        when(state.getRawRepo().recordExists(reader.recordId(), RawRepo.RAWREPO_COMMON_LIBRARY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.recordId(), reader.agencyIdAsInteger())).thenReturn(AssertActionsUtil.createRawRepoRecord(existingRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsForId("12345678")).thenReturn(holdingList);
        when(state.getSolrService().hasDocuments(SolrServiceIndexer.createSubfieldQueryDBCOnly("001a", reader.recordId()))).thenReturn(false);

        UpdateOperationAction instance = new UpdateOperationAction(state, settings);
        String message = state.getMessages().getString("delete.record.holdings.on.002a");
        assertThat(instance.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state)));
    }

    @Test
    public void testPreviousFaust_DeleteRecord_HoldingOn002() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.markForDeletion();
        MarcRecord existingRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        List<MarcField> fields = existingRecord.getFields();
        MarcField field002 = new MarcField("002", "00");
        List<MarcSubField> subfields = new ArrayList<>();
        subfields.add(new MarcSubField("a", "12345678"));
        field002.setSubfields(subfields);
        fields.add(field002);
        Set<Integer> holdingList001 = new HashSet<>();
        Set<Integer> holdingList002 = new HashSet<>();
        holdingList002.add(123);
        holdingList002.add(456);
        state.setMarcRecord(record);
        state.setLibraryGroup(libraryGroupFBS);

        when(state.getRawRepo().recordExists(reader.recordId(), RawRepo.RAWREPO_COMMON_LIBRARY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.recordId(), reader.agencyIdAsInteger())).thenReturn(AssertActionsUtil.createRawRepoRecord(existingRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsForId(reader.recordId())).thenReturn(holdingList001);
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsForId("12345678")).thenReturn(holdingList002);

        UpdateOperationAction instance = new UpdateOperationAction(state, settings);
        String message = state.getMessages().getString("delete.record.holdings.on.002a");
        assertThat(instance.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state)));
    }
}
