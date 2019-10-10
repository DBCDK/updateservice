/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.common.records.MarcSubField;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.rawrepo.Record;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.OpenAgencyService;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Properties;
import java.util.Set;

import static junit.framework.TestCase.assertFalse;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// TODO : Pt indeholder testposterne i de fleste test ikke noget der hænger sammen - det bør rettes op
// TODO da det kan virke ret forvirrende. Senest hvis det skal opensources.
public class UpdateOperationActionTest {
    private GlobalActionState state;
    private Properties settings = new UpdateTestUtils().getSettings();
    private static final String GROUP_ID = "700000";
    private OpenAgencyService.LibraryGroup libraryGroupDBC = OpenAgencyService.LibraryGroup.DBC;
    private OpenAgencyService.LibraryGroup libraryGroupFBS = OpenAgencyService.LibraryGroup.FBS;

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
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        state.setLibraryGroup(libraryGroupFBS);
        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(false);
        when(state.getRawRepo().recordExists(eq(recordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature(Integer.toString(agencyId), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS)).thenReturn(true);
        List<MarcRecord> rawRepoRecords = Collections.singletonList(record);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(record), eq(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId()), eq(libraryGroupFBS), eq(state.getMessages()))).thenReturn(rawRepoRecords);

        UpdateOperationAction updateOperationAction = new UpdateOperationAction(state, settings);
        assertThat(updateOperationAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateOperationAction.children();
        assertThat(children.size(), is(2));
        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction(iterator.next(), record, state.getAuthenticator(), state.getUpdateServiceRequestDTO().getAuthenticationDTO());
        AssertActionsUtil.assertUpdateLocalRecordAction(iterator.next(), state.getRawRepo(), record, state.getHoldingsItems());
    }

    @Test
    public void testPerformAction_LocalRecordWith_n55() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        state.setMarcRecord(record);
        String recordId = AssertActionsUtil.getRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);
        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.addOrReplaceSubfield("n55", "a", "20170602");

        state.setLibraryGroup(libraryGroupFBS);
        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(false);
        when(state.getRawRepo().recordExists(eq(recordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature(Integer.toString(agencyId), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS)).thenReturn(true);
        List<MarcRecord> rawRepoRecords = Collections.singletonList(record);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(record), eq(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId()), eq(libraryGroupFBS), eq(state.getMessages()))).thenReturn(rawRepoRecords);

        UpdateOperationAction updateOperationAction = new UpdateOperationAction(state, settings);
        assertThat(updateOperationAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateOperationAction.children();
        assertThat(children.size(), is(2));
        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction(iterator.next(), record, state.getAuthenticator(), state.getUpdateServiceRequestDTO().getAuthenticationDTO());
        AssertActionsUtil.assertUpdateLocalRecordAction(iterator.next(), state.getRawRepo(), record, state.getHoldingsItems());

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        Date expectedOverwriteDate = sdf.parse("02/06/2017");
        assertThat(state.getCreateOverwriteDate(), equalTo(expectedOverwriteDate.toInstant()));

        MarcRecordReader reader = new MarcRecordReader(updateOperationAction.getRecord());
        assertFalse(reader.hasSubfield("n55", "a"));
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
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);
        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        state.setMarcRecord(enrichmentRecord);
        int enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInt(enrichmentRecord);
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId(Integer.toString(enrichmentAgencyId));

        state.setLibraryGroup(libraryGroupFBS);
        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(recordId), eq(enrichmentAgencyId))).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature(eq(Integer.toString(enrichmentAgencyId)), eq(LibraryRuleHandler.Rule.CREATE_ENRICHMENTS))).thenReturn(true);
        List<MarcRecord> rawRepoRecords = Collections.singletonList(enrichmentRecord);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(enrichmentRecord), eq(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId()), eq(libraryGroupFBS), eq(state.getMessages()))).thenReturn(rawRepoRecords);
        when(state.getRawRepo().fetchRecord(recordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));

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
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);
        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        state.setMarcRecord(enrichmentRecord);
        int enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInt(enrichmentRecord);
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId(Integer.toString(enrichmentAgencyId));

        state.setLibraryGroup(libraryGroupFBS);
        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(recordId), eq(enrichmentAgencyId))).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature(Integer.toString(enrichmentAgencyId), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS)).thenReturn(false);
        List<MarcRecord> rawRepoRecords = Collections.singletonList(enrichmentRecord);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(enrichmentRecord), eq(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId()), eq(libraryGroupFBS), eq(state.getMessages()))).thenReturn(rawRepoRecords);
        when(state.getRawRepo().fetchRecord(recordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));

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
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        // Load an enrichment record. Set the library to 870970 in 001*b
        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter writer = new MarcRecordWriter(enrichmentRecord);
        writer.addOrReplaceSubfield("001", "b", Integer.toString(RawRepo.DBC_ENRICHMENT));
        int enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInt(enrichmentRecord);

        // Load the updating record - set the library to 870970 in 001*b
        MarcRecord updateRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        state.setMarcRecord(updateRecord);
        MarcRecordWriter updWriter = new MarcRecordWriter(updateRecord);
        updWriter.addOrReplaceSubfield("001", "a", "206111600");
        updWriter.addOrReplaceSubfield("001", "b", Integer.toString(RawRepo.COMMON_AGENCY));

        state.setLibraryGroup(libraryGroupDBC);
        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(recordId), eq(enrichmentAgencyId))).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS)).thenReturn(true);
        when(state.getOpenAgencyService().hasFeature(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), LibraryRuleHandler.Rule.AUTH_CREATE_COMMON_RECORD)).thenReturn(true);
        List<MarcRecord> rawRepoRecords = Arrays.asList(record, enrichmentRecord);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(updateRecord), eq(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId()), eq(libraryGroupDBC), eq(state.getMessages()))).thenReturn(rawRepoRecords);
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
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        // Load an enrichment record. Set the library to 870970 in 001*b
        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter writer = new MarcRecordWriter(enrichmentRecord);
        writer.addOrReplaceSubfield("001", "b", Integer.toString(RawRepo.DBC_ENRICHMENT));
        int enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInt(enrichmentRecord);

        // Load the updating record - set the library to 870970 in 001*b
        MarcRecord updateRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        state.setMarcRecord(updateRecord);
        MarcRecordWriter updWriter = new MarcRecordWriter(updateRecord);
        updWriter.addOrReplaceSubfield("001", "a", "206111600");
        //updWriter.addOrReplaceSubfield("001", "b", Integer.toString(RawRepo.COMMON_AGENCY));

        state.setLibraryGroup(libraryGroupFBS);
        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(recordId), eq(enrichmentAgencyId))).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS)).thenReturn(true);
        when(state.getOpenAgencyService().hasFeature(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), LibraryRuleHandler.Rule.AUTH_CREATE_COMMON_RECORD)).thenReturn(true);
        List<MarcRecord> rawRepoRecords = Arrays.asList(record, enrichmentRecord);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(updateRecord), eq(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId()), eq(libraryGroupFBS), eq(state.getMessages()))).thenReturn(rawRepoRecords);

        // TEST 2 - REMEMBER - this test doesn't say anything about the success or failure of the create - just that the correct actions are created !!!!
        // Same as before but owner of updating record set to 810010
        // this shall create an doublerecord action
        updWriter.addOrReplaceSubfield("996", "a", "810010");

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
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        // Load an enrichment record. Set the library to 870970 in 001*b
        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter writer = new MarcRecordWriter(enrichmentRecord);
        writer.addOrReplaceSubfield("001", "b", Integer.toString(RawRepo.DBC_ENRICHMENT));
        int enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInt(enrichmentRecord);

        // Load the updating record - set the library to 870970 in 001*b
        MarcRecord updateRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        state.setMarcRecord(updateRecord);
        MarcRecordWriter updWriter = new MarcRecordWriter(updateRecord);
        updWriter.addOrReplaceSubfield("001", "a", "206111600");
        updWriter.addOrReplaceSubfield("001", "b", Integer.toString(RawRepo.COMMON_AGENCY));

        state.setLibraryGroup(libraryGroupFBS);
        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(recordId), eq(enrichmentAgencyId))).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS)).thenReturn(true);
        when(state.getOpenAgencyService().hasFeature(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), LibraryRuleHandler.Rule.AUTH_CREATE_COMMON_RECORD)).thenReturn(true);
        List<MarcRecord> rawRepoRecords = Arrays.asList(record, enrichmentRecord);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(updateRecord), eq(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId()), eq(libraryGroupFBS), eq(state.getMessages()))).thenReturn(rawRepoRecords);

        // TEST 3 - Double record frontend, forced update, key found
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
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        // Load an enrichment record. Set the library to 870970 in 001*b
        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter writer = new MarcRecordWriter(enrichmentRecord);
        writer.addOrReplaceSubfield("001", "b", Integer.toString(RawRepo.DBC_ENRICHMENT));
        int enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInt(enrichmentRecord);

        // Load the updating record - set the library to 870970 in 001*b
        MarcRecord updateRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        state.setMarcRecord(updateRecord);
        MarcRecordWriter updWriter = new MarcRecordWriter(updateRecord);
        updWriter.addOrReplaceSubfield("001", "a", "206111600");
        updWriter.addOrReplaceSubfield("001", "b", Integer.toString(RawRepo.COMMON_AGENCY));

        state.setLibraryGroup(libraryGroupFBS);
        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(recordId), eq(enrichmentAgencyId))).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS)).thenReturn(true);
        when(state.getOpenAgencyService().hasFeature(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), LibraryRuleHandler.Rule.AUTH_CREATE_COMMON_RECORD)).thenReturn(true);
        List<MarcRecord> rawRepoRecords = Arrays.asList(record, enrichmentRecord);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(updateRecord), eq(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId()), eq(libraryGroupFBS), eq(state.getMessages()))).thenReturn(rawRepoRecords);

        // TEST 4 - Double record frontend, forced update, key not found
        String doubleRecordKey = "8d83dc66-87df-4ef5-a50f-82e9e870c66c";
        state.getUpdateServiceRequestDTO().setDoubleRecordKey(doubleRecordKey);
        when(state.getUpdateStore().doesDoubleRecordKeyExist(eq(doubleRecordKey))).thenReturn(false);
        UpdateOperationAction updateOperationAction = new UpdateOperationAction(state, settings);
        String message = String.format(state.getMessages().getString("double.record.frontend.unknown.key"), doubleRecordKey);
        assertThat(updateOperationAction.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message)));
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
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);
        MarcRecordWriter recordWriter = new MarcRecordWriter(record);
        recordWriter.markForDeletion();
        state.setMarcRecord(record);

        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter enrichmentWriter = new MarcRecordWriter(enrichmentRecord);
        enrichmentWriter.addOrReplaceSubfield("001", "b", Integer.toString(RawRepo.DBC_ENRICHMENT));
        int enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInt(enrichmentRecord);

        state.setLibraryGroup(libraryGroupFBS);
        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(recordId), eq(enrichmentAgencyId))).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature(Integer.toString(agencyId), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS)).thenReturn(true);
        List<MarcRecord> rawRepoRecords = Arrays.asList(record, enrichmentRecord);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(record), eq(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId()), eq(libraryGroupFBS), eq(state.getMessages()))).thenReturn(rawRepoRecords);
        when(state.getRawRepo().fetchRecord(eq(recordId), eq(agencyId))).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getSolrFBS().getOwnerOf002(SolrServiceIndexer.createGetOwnerOf002QueryDBCOnly("002a", recordId))).thenReturn("");

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
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);
        MarcRecordWriter recordWriter = new MarcRecordWriter(record);
        recordWriter.markForDeletion();
        state.setMarcRecord(record);
        state.setLibraryGroup(libraryGroupFBS);

        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter enrichmentWriter = new MarcRecordWriter(enrichmentRecord);
        enrichmentWriter.addOrReplaceSubfield("001", "b", Integer.toString(RawRepo.DBC_ENRICHMENT));
        int enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInt(enrichmentRecord);

        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(false);
        when(state.getRawRepo().recordExists(eq(recordId), eq(enrichmentAgencyId))).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature(Integer.toString(agencyId), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS)).thenReturn(true);
        List<MarcRecord> rawRepoRecords = Arrays.asList(record, enrichmentRecord);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(record), eq(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId()), eq(libraryGroupFBS), eq(state.getMessages()))).thenReturn(rawRepoRecords);
        when(state.getRawRepo().fetchRecord(eq(recordId), eq(agencyId))).thenReturn(null);
        when(state.getSolrFBS().getOwnerOf002(SolrServiceIndexer.createGetOwnerOf002QueryDBCOnly("002a", recordId))).thenReturn("");

        UpdateOperationAction updateOperationAction = new UpdateOperationAction(state, settings);
        String message = state.getMessages().getString("operation.delete.non.existing.record");
        assertThat(updateOperationAction.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message)));
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

        when(state.getRawRepo().recordExists(eq(recordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(recordId), eq(RawRepo.SCHOOL_COMMON_AGENCY))).thenReturn(true);
        List<MarcRecord> rawRepoRecords = Collections.singletonList(commonSchoolRecord);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(commonSchoolRecord), eq(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId()), eq(libraryGroupFBS), eq(state.getMessages()))).thenReturn(rawRepoRecords);
        when(state.getRawRepo().fetchRecord(recordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(commonRecord, MarcXChangeMimeType.MARCXCHANGE));
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

        when(state.getRawRepo().recordExists(eq(recordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(recordId), eq(RawRepo.SCHOOL_COMMON_AGENCY))).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature(groupId, LibraryRuleHandler.Rule.CREATE_ENRICHMENTS)).thenReturn(true);
        List<MarcRecord> rawRepoRecords = Collections.singletonList(schoolRecord);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(schoolRecord), eq(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId()), eq(libraryGroupFBS), eq(state.getMessages()))).thenReturn(rawRepoRecords);
        when(state.getRawRepo().fetchRecord(recordId, RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(commonRecord, MarcXChangeMimeType.MARCXCHANGE));

        UpdateOperationAction instance = new UpdateOperationAction(state, settings);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        ListIterator<ServiceAction> iterator = instance.children().listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction(iterator.next(), schoolRecord, state.getAuthenticator(), state.getUpdateServiceRequestDTO().getAuthenticationDTO());
        AssertActionsUtil.assertSchoolEnrichmentRecordAction(iterator.next(), state.getRawRepo(), schoolRecord, state.getLibraryRecordsHandler(), state.getHoldingsItems(), settings.getProperty(state.getRawRepoProviderId()));
        assertThat(iterator.hasNext(), is(false));
    }

    @Test
    public void testPerformAction_FBSLocalRecordNoCommonRecordNo002() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.getRecordId();
        String agencyId = reader.getAgencyId();

        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId(agencyId);
        state.setMarcRecord(record);
        state.setLibraryGroup(libraryGroupFBS);

        when(state.getRawRepo().recordExists(eq(recordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(false);
        when(state.getRawRepo().recordExistsMaybeDeleted(recordId, RawRepo.COMMON_AGENCY)).thenReturn(false);
        List<MarcRecord> rawRepoRecords = Collections.singletonList(record);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(record), eq(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId()), eq(libraryGroupFBS), eq(state.getMessages()))).thenReturn(rawRepoRecords);
        when(state.getOpenAgencyService().hasFeature(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS)).thenReturn(true);

        UpdateOperationAction instance = new UpdateOperationAction(state, settings);

        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));
        List<ServiceAction> children = instance.children();
        assertThat(children.size(), is(2));
        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction(iterator.next(), record, state.getAuthenticator(), state.getUpdateServiceRequestDTO().getAuthenticationDTO());
        AssertActionsUtil.assertUpdateLocalRecordAction(iterator.next(), state.getRawRepo(), record, state.getHoldingsItems());

    }

    @Test
    public void testPerformAction_FBSLocalRecordNoCommonRecordHas002() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.getRecordId();
        String agencyId = reader.getAgencyId();

        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId(agencyId);
        state.setMarcRecord(record);
        state.setLibraryGroup(libraryGroupFBS);

        when(state.getRawRepo().recordExists(eq(recordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(false);
        when(state.getRawRepo().recordExistsMaybeDeleted(recordId, RawRepo.COMMON_AGENCY)).thenReturn(false);
        List<MarcRecord> rawRepoRecords = Collections.singletonList(record);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(record), eq(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId()), eq(libraryGroupFBS), eq(state.getMessages()))).thenReturn(rawRepoRecords);
        when(state.getOpenAgencyService().hasFeature(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS)).thenReturn(true);

        String solrRequest = "marc.002a:\"20611529\" AND marc.001b:870970";
        when(state.getSolrFBS().hasDocuments(solrRequest)).thenReturn(true);

        UpdateOperationAction instance = new UpdateOperationAction(state, settings);

        String message = String.format(state.getMessages().getString("record.not.allowed.deleted.common.record"), recordId);
        assertThat(instance.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message)));
    }

    @Test
    public void testPerformAction_FBSLocalRecordDeletedCommonRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.LOCAL_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.getRecordId();
        String agencyId = reader.getAgencyId();

        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId(agencyId);
        state.setMarcRecord(record);
        state.setLibraryGroup(libraryGroupFBS);

        Record commonRecord = mock(Record.class);
        when(commonRecord.isDeleted()).thenReturn(true);

        when(state.getRawRepo().recordExists(eq(recordId), eq(RawRepo.COMMON_AGENCY))).thenReturn(false);
        when(state.getRawRepo().recordExistsMaybeDeleted(recordId, RawRepo.COMMON_AGENCY)).thenReturn(true);
        List<MarcRecord> rawRepoRecords = Collections.singletonList(record);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(record), eq(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId()), eq(libraryGroupFBS), eq(state.getMessages()))).thenReturn(rawRepoRecords);
        when(state.getOpenAgencyService().hasFeature(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(recordId, RawRepo.COMMON_AGENCY)).thenReturn(commonRecord);

        UpdateOperationAction instance = new UpdateOperationAction(state, settings);

        String message = String.format(state.getMessages().getString("record.not.allowed.deleted.common.record"), recordId);
        assertThat(instance.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message)));
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
        when(state.getRawRepo().fetchRecord(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(null);

        UpdateOperationAction instance = new UpdateOperationAction(state, settings);
        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));
    }

    @Test
    public void testPreviousFaust_UpdateRecord_NoMatch() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        state.setMarcRecord(record);
        state.setLibraryGroup(libraryGroupFBS);
        when(state.getRawRepo().fetchRecord(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(null);
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

        when(state.getRawRepo().recordExists(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getSolrFBS().hasDocuments(SolrServiceIndexer.createSubfieldQueryWithExcludeDBCOnly("002a", "12345678", "001a", reader.getRecordId()))).thenReturn(true);

        UpdateOperationAction instance = new UpdateOperationAction(state, settings);
        String message = state.getMessages().getString("update.record.with.002.links");
        assertThat(instance.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message)));
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

        when(state.getRawRepo().recordExists(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(false);
        when(state.getSolrFBS().hasDocuments(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", "12345678"))).thenReturn(true);

        UpdateOperationAction instance = new UpdateOperationAction(state, settings);
        String message = state.getMessages().getString("update.record.with.002.links");
        assertThat(instance.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message)));
    }

    @Test
    public void testPreviousFaust_UpdateRecord_Match002x_CreateRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        List<MarcField> fields = record.getFields();
        MarcField field002 = new MarcField("002", "00");
        List<MarcSubField> subfields = new ArrayList<>();
        subfields.add(new MarcSubField("b", "766100"));
        subfields.add(new MarcSubField("c", "90014110"));
        subfields.add(new MarcSubField("x", "76610090014110"));
        field002.setSubfields(subfields);
        fields.add(field002);
        state.setMarcRecord(record);
        state.setLibraryGroup(libraryGroupFBS);

        when(state.getRawRepo().recordExists(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(record, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getSolrFBS().hasDocuments(SolrServiceIndexer.createSubfieldQueryWithExcludeDBCOnly("002x", "76610090014110", "001a", reader.getRecordId()))).thenReturn(true);

        UpdateOperationAction instance = new UpdateOperationAction(state, settings);
        String message = state.getMessages().getString("update.record.with.002.links");
        assertThat(instance.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message)));
    }

    @Test
    public void testPreviousFaust_UpdateRecord_Match002x_UpdateRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        List<MarcField> fields = record.getFields();
        MarcField field002 = new MarcField("002", "00");
        List<MarcSubField> subfields = new ArrayList<>();
        subfields.add(new MarcSubField("b", "766100"));
        subfields.add(new MarcSubField("c", "90014110"));
        subfields.add(new MarcSubField("x", "76610090014110"));
        field002.setSubfields(subfields);
        fields.add(field002);
        state.setMarcRecord(record);
        state.setLibraryGroup(libraryGroupFBS);

        when(state.getRawRepo().recordExists(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(false);
        when(state.getSolrFBS().hasDocuments(SolrServiceIndexer.createSubfieldQueryDBCOnly("002x", "76610090014110"))).thenReturn(true);

        UpdateOperationAction instance = new UpdateOperationAction(state, settings);
        String message = state.getMessages().getString("update.record.with.002.links");
        assertThat(instance.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message)));
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

        when(state.getRawRepo().fetchRecord(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(AssertActionsUtil.createRawRepoRecord(existingRecord, MarcXChangeMimeType.MARCXCHANGE));
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

        MarcRecord previousMarcRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter previousWriter = new MarcRecordWriter(previousMarcRecord);
        previousWriter.addFieldSubfield("001", "a", "12345678");
        previousWriter.markForDeletion();


        Set<Integer> holdingList = new HashSet<>();
        holdingList.add(123);
        holdingList.add(456);
        state.setMarcRecord(record);
        state.setLibraryGroup(libraryGroupFBS);

        when(state.getRawRepo().recordExists(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(AssertActionsUtil.createRawRepoRecord(existingRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getRawRepo().recordDoesNotExistOrIsDeleted("12345678", RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsForId("12345678")).thenReturn(holdingList);

        UpdateOperationAction instance = new UpdateOperationAction(state, settings);
        String message = state.getMessages().getString("update.record.holdings.on.002a");
        assertThat(instance.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message)));
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

        when(state.getRawRepo().recordExists(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(AssertActionsUtil.createRawRepoRecord(existingRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsForId("12345678")).thenReturn(holdingList);
        when(state.getSolrFBS().getOwnerOf002(SolrServiceIndexer.createGetOwnerOf002QueryDBCOnly("002a", "20611529"))).thenReturn("");

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

        when(state.getRawRepo().recordExists(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(AssertActionsUtil.createRawRepoRecord(existingRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsForId("12345678")).thenReturn(holdingList);
        when(state.getSolrFBS().hasDocuments(SolrServiceIndexer.createSubfieldQueryDBCOnly("001a", reader.getRecordId()))).thenReturn(false);
        when(state.getSolrFBS().getOwnerOf002(SolrServiceIndexer.createGetOwnerOf002QueryDBCOnly("002a", "20611529"))).thenReturn("");

        UpdateOperationAction instance = new UpdateOperationAction(state, settings);
        String message = state.getMessages().getString("delete.record.holdings.on.002a");
        assertThat(instance.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message)));
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

        when(state.getRawRepo().recordExists(reader.getRecordId(), RawRepo.COMMON_AGENCY)).thenReturn(true);
        when(state.getRawRepo().fetchRecord(reader.getRecordId(), reader.getAgencyIdAsInt())).thenReturn(AssertActionsUtil.createRawRepoRecord(existingRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsForId(reader.getRecordId())).thenReturn(holdingList001);
        when(state.getHoldingsItems().getAgenciesThatHasHoldingsForId("12345678")).thenReturn(holdingList002);
        when(state.getSolrFBS().getOwnerOf002(SolrServiceIndexer.createGetOwnerOf002QueryDBCOnly("002a", "20611529"))).thenReturn("");

        UpdateOperationAction instance = new UpdateOperationAction(state, settings);
        String message = state.getMessages().getString("delete.record.holdings.on.002a");
        assertThat(instance.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message)));
    }

    @Test
    public void testSetCreatedDateOverwriteWithExisting() throws Exception {
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId("010100");

        MarcRecord existing = constructRecordWith001("20611529", "870970", "20001234", "20182221");
        MarcRecord record = constructRecordWith001("20611529", "870970", "20001234", "19001234");
        MarcRecord expected = constructRecordWith001("20611529", "870970", "20001234", "20182221");
        when(state.getRawRepo().recordExists(eq("20611529"), eq(870970))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq("20611529"), eq(870970))).thenReturn(AssertActionsUtil.createRawRepoRecord(existing, MarcXChangeMimeType.MARCXCHANGE));
        state.setMarcRecord(record);
        UpdateOperationAction instance = new UpdateOperationAction(state, settings);
        instance.setCreatedDate(new MarcRecordReader(record));

        assertThat(instance.getRecord(), equalTo(expected));
    }

    @Test
    public void testSetCreatedDateNewRecordWith001d() throws Exception {
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId("010100");

        MarcRecord record = constructRecordWith001("20611529", "870970", "20001234", "19001234");
        MarcRecord expected = constructRecordWith001("20611529", "870970", "20001234", "19001234");

        when(state.getRawRepo().recordExists(eq("20611529"), eq(870970))).thenReturn(false);
        state.setMarcRecord(record);
        UpdateOperationAction instance = new UpdateOperationAction(state, settings);
        instance.setCreatedDate(new MarcRecordReader(record));

        assertThat(instance.getRecord(), equalTo(expected));
    }

    @Test
    public void testSetCreatedDateNewRecordWithout001d() throws Exception {
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId("010100");

        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyyMMdd");

        MarcRecord record = constructRecordWith001("20611529", "870970", "20001234", null);
        MarcRecord expected = constructRecordWith001("20611529", "870970", "20001234", format.format(LocalDateTime.now()));

        when(state.getRawRepo().recordExists(eq("20611529"), eq(870970))).thenReturn(false);
        state.setMarcRecord(record);
        UpdateOperationAction instance = new UpdateOperationAction(state, settings);
        instance.setCreatedDate(new MarcRecordReader(record));

        assertThat(instance.getRecord(), equalTo(expected));
    }

    @Test
    public void testSetCreatedDateAdmin() throws Exception {
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId("010100");

        MarcRecord record = constructRecordWith001("20611529", "870970", "20001234", "19001234");
        MarcRecord expected = constructRecordWith001("20611529", "870970", "20001234", "19001234");

        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setUserId("ADMIN");
        state.setMarcRecord(record);
        UpdateOperationAction instance = new UpdateOperationAction(state, settings);
        instance.setCreatedDate(new MarcRecordReader(record));

        assertThat(instance.getRecord(), equalTo(expected));
    }

    @Test
    public void testSetCreatedDateFFUWithoutCreated() throws Exception {
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId("222222");

        MarcRecord record = constructRecordWith001("20611529", "222222", "20001234", null);
        MarcRecord expected = constructRecordWith001("20611529", "222222", "20001234", null);

        state.setMarcRecord(record);
        UpdateOperationAction instance = new UpdateOperationAction(state, settings);
        instance.setCreatedDate(new MarcRecordReader(record));

        assertThat(instance.getRecord(), equalTo(expected));
    }

    @Test
    public void testSetCreatedDateFBSOverwriteWithExisting() throws Exception {
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId("333333");

        MarcRecord existing = constructRecordWith001("20611529", "870970", "20001234", "20182221");
        MarcRecord record = constructRecordWith001("20611529", "870970", "20001234", "19001234");
        MarcRecord expected = constructRecordWith001("20611529", "870970", "20001234", "20182221");
        when(state.getRawRepo().recordExists(eq("20611529"), eq(870970))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq("20611529"), eq(870970))).thenReturn(AssertActionsUtil.createRawRepoRecord(existing, MarcXChangeMimeType.MARCXCHANGE));
        state.setMarcRecord(record);
        UpdateOperationAction instance = new UpdateOperationAction(state, settings);
        instance.setCreatedDate(new MarcRecordReader(record));

        assertThat(instance.getRecord(), equalTo(expected));
    }

    @Test
    public void testSetCreatedDateFBSNewEnrichmentWith001d() throws Exception {
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId("710100");

        MarcRecord record = constructRecordWith001("20611529", "710100", "20001234", "20190327");
        MarcRecord expected = constructRecordWith001("20611529", "710100", "20001234", "20190327");
        when(state.getRawRepo().recordExists(eq("20611529"), eq(710100))).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature(eq("710100"), eq(LibraryRuleHandler.Rule.USE_ENRICHMENTS))).thenReturn(true);
        state.setMarcRecord(record);
        UpdateOperationAction instance = new UpdateOperationAction(state, settings);
        instance.setCreatedDate(new MarcRecordReader(record));

        assertThat(instance.getRecord(), equalTo(expected));
    }

    @Test
    public void testSetCreatedDateFBSNewEnrichmentWithout001d() throws Exception {
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId("710100");

        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyyMMdd");
        LocalDateTime dateTime = LocalDateTime.now();
        String today = dateTime.format(format);

        MarcRecord record = constructRecordWith001("20611529", "710100", "20001234", null);
        MarcRecord expected = constructRecordWith001("20611529", "710100", "20001234", today);
        when(state.getRawRepo().recordExists(eq("20611529"), eq(710100))).thenReturn(false);
        when(state.getOpenAgencyService().hasFeature(eq("710100"), eq(LibraryRuleHandler.Rule.USE_ENRICHMENTS))).thenReturn(true);
        state.setMarcRecord(record);
        UpdateOperationAction instance = new UpdateOperationAction(state, settings);
        instance.setCreatedDate(new MarcRecordReader(record));

        assertThat(instance.getRecord(), equalTo(expected));
    }

    @Test
    public void testSetCreatedDateFBSExistingEnrichmentWithout001d() throws Exception {
        state.getUpdateServiceRequestDTO().getAuthenticationDTO().setGroupId("710100");

        MarcRecord existing = constructRecordWith001("20611529", "710100", "20001234", "20182221");
        MarcRecord record = constructRecordWith001("20611529", "710100", "20001234", null);
        MarcRecord expected = constructRecordWith001("20611529", "710100", "20001234", "20182221");

        when(state.getRawRepo().recordExists(eq("20611529"), eq(710100))).thenReturn(true);
        when(state.getRawRepo().fetchRecord(eq("20611529"), eq(710100))).thenReturn(AssertActionsUtil.createRawRepoRecord(existing, MarcXChangeMimeType.MARCXCHANGE));
        when(state.getOpenAgencyService().hasFeature(eq("710100"), eq(LibraryRuleHandler.Rule.USE_ENRICHMENTS))).thenReturn(true);
        state.setMarcRecord(record);
        UpdateOperationAction instance = new UpdateOperationAction(state, settings);
        instance.setCreatedDate(new MarcRecordReader(record));

        assertThat(instance.getRecord(), equalTo(expected));
    }

    private MarcRecord constructRecordWith001(String bibliographicRecordId, String agencyId, String modified, String created) {
        MarcField field = new MarcField("001", "00");
        field.getSubfields().add(new MarcSubField("a", bibliographicRecordId));
        field.getSubfields().add(new MarcSubField("b", agencyId));
        field.getSubfields().add(new MarcSubField("c", modified));

        if (created != null) {
            field.getSubfields().add(new MarcSubField("d", created));
        }

        MarcRecord record = new MarcRecord();
        record.getFields().add(field);

        return record;
    }

    @Test
    public void testPerformAction_RemoveD09zNoAction() throws Exception {

        // Load a 870970 record - this is the rawrepo record
        MarcRecord mergedRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter mergeWriter = new MarcRecordWriter(mergedRecord);
        mergeWriter.addOrReplaceSubfield("d09", "z", "LIT123456");

        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.addOrReplaceSubfield("001", "b", Integer.toString(RawRepo.COMMON_AGENCY));
        String recordId = AssertActionsUtil.getRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        // Load an enrichment record. Set the library to 191919 in 001*b
        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter enrichmentWriter = new MarcRecordWriter(enrichmentRecord);
        enrichmentWriter.addOrReplaceSubfield("001", "b", Integer.toString(RawRepo.DBC_ENRICHMENT));
        enrichmentWriter.addOrReplaceSubfield("d09", "z", "LIT123456");
        int enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInt(enrichmentRecord);

        // Load the updating record - set the library to 870970 in 001*b
        MarcRecord updateRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        state.setMarcRecord(updateRecord);
        MarcRecordWriter updWriter = new MarcRecordWriter(updateRecord);
        updWriter.addOrReplaceSubfield("001", "a", "206111600");
        updWriter.addOrReplaceSubfield("001", "b", Integer.toString(RawRepo.COMMON_AGENCY));
        updWriter.addOrReplaceSubfield("d09", "z", "LIT123456");

        state.setLibraryGroup(libraryGroupDBC);
        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(recordId), eq(enrichmentAgencyId))).thenReturn(true);
        when(state.getOpenAgencyService().hasFeature(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS)).thenReturn(true);
        when(state.getOpenAgencyService().hasFeature(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), LibraryRuleHandler.Rule.AUTH_CREATE_COMMON_RECORD)).thenReturn(true);
        // Get existing record merged
        when(state.getRawRepo().fetchMergedDBCRecord(eq(recordId), eq(RawRepo.DBC_ENRICHMENT))).thenReturn(AssertActionsUtil.createRawRepoRecord(mergedRecord, MarcXChangeMimeType.MARCXCHANGE));
        List<MarcRecord> rawRepoRecords = Arrays.asList(record, enrichmentRecord);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(updateRecord), eq(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId()),
                eq(libraryGroupDBC), eq(state.getMessages()))).thenReturn(rawRepoRecords);

        UpdateOperationAction updateOperationAction = new UpdateOperationAction(state, settings);
        assertThat(updateOperationAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateOperationAction.children();
        assertThat(children.size(), is(3));

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction(iterator.next(), updateRecord, state.getAuthenticator(), state.getUpdateServiceRequestDTO().getAuthenticationDTO());
        AssertActionsUtil.assertUpdateCommonRecordAction(iterator.next(), state.getRawRepo(), record, UpdateTestUtils.GROUP_ID, state.getLibraryRecordsHandler(),
                state.getHoldingsItems(), state.getOpenAgencyService());
        AssertActionsUtil.assertUpdateEnrichmentRecordAction(iterator.next(), state.getRawRepo(), enrichmentRecord, state.getLibraryRecordsHandler(), state.getHoldingsItems());
        assertThat(iterator.hasNext(), is(false));
    }

    @Test
    public void testPerformAction_RemoveD09zNoD09InIncoming() throws Exception {

        // Load a 870970 record and create a merged version
        MarcRecord mergedRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter mergeWriter = new MarcRecordWriter(mergedRecord);
        mergeWriter.addOrReplaceSubfield("d09", "z", "LIT123456");

        // Load a 870970 record - this is the common record
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.addOrReplaceSubfield("001", "b", Integer.toString(RawRepo.COMMON_AGENCY));
        String recordId = AssertActionsUtil.getRecordId(record);
        int agencyId = AssertActionsUtil.getAgencyIdAsInt(record);

        // Load an enrichment record. Set the library to 191919 in 001*b
        MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter enrichmentWriter = new MarcRecordWriter(enrichmentRecord);
        enrichmentWriter.addOrReplaceSubfield("001", "b", Integer.toString(RawRepo.DBC_ENRICHMENT));
        int enrichmentAgencyId = AssertActionsUtil.getAgencyIdAsInt(enrichmentRecord);

        // Load the updating record - set the library to 870970 in 001*b
        MarcRecord updateRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        state.setMarcRecord(updateRecord);
        MarcRecordWriter updWriter = new MarcRecordWriter(updateRecord);
        updWriter.addOrReplaceSubfield("001", "a", "206111600");
        updWriter.addOrReplaceSubfield("001", "b", Integer.toString(RawRepo.COMMON_AGENCY));

        // Existing littolk records that will be deleted
        MarcRecord littolkCommon = AssertActionsUtil.loadRecord(AssertActionsUtil.LITTOLK_COMMON);
        MarcRecordWriter littCoWriter = new MarcRecordWriter(littolkCommon);
        littCoWriter.markForDeletion();
        MarcRecord littolkEnrichment = AssertActionsUtil.loadRecord(AssertActionsUtil.LITTOLK_ENRICHMENT);
        MarcRecordWriter littEnWriter = new MarcRecordWriter(littolkEnrichment);
        littEnWriter.addOrReplaceSubfield("001", "b", Integer.toString(RawRepo.DBC_ENRICHMENT));
        littEnWriter.markForDeletion();
        String littolkRecordId = AssertActionsUtil.getRecordId(littolkCommon);

        state.setLibraryGroup(libraryGroupDBC);
        when(state.getRawRepo().recordExists(eq(recordId), eq(agencyId))).thenReturn(true);
        when(state.getRawRepo().recordExists(eq(recordId), eq(enrichmentAgencyId))).thenReturn(true);
        when(state.getOpenAgencyService().hasFeature(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS)).thenReturn(true);
        when(state.getOpenAgencyService().hasFeature(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), LibraryRuleHandler.Rule.AUTH_CREATE_COMMON_RECORD)).thenReturn(true);
        // Get existing record merged
        when(state.getRawRepo().fetchMergedDBCRecord(eq(recordId), eq(RawRepo.DBC_ENRICHMENT))).thenReturn(AssertActionsUtil.createRawRepoRecord(mergedRecord, MarcXChangeMimeType.MARCXCHANGE));
        List<MarcRecord> rawRepoRecords = Arrays.asList(record, enrichmentRecord);
        when(state.getLibraryRecordsHandler().recordDataForRawRepo(eq(updateRecord), eq(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId()),
                eq(libraryGroupDBC), eq(state.getMessages()))).thenReturn(rawRepoRecords);

        when(state.getRawRepo().children(eq(mergedRecord))).thenReturn(AssertActionsUtil.createRecordSet(littolkCommon));
        when(state.getRawRepo().fetchRecord(eq(littolkRecordId), eq(RawRepo.DBC_ENRICHMENT))).thenReturn(AssertActionsUtil.createRawRepoRecord(littolkEnrichment, MarcXChangeMimeType.ENRICHMENT));
        when(state.getRawRepo().fetchRecord(eq(littolkRecordId), eq(RawRepo.LITTOLK_AGENCY))).thenReturn(AssertActionsUtil.createRawRepoRecord(littolkCommon, MarcXChangeMimeType.MARCXCHANGE));

        UpdateOperationAction updateOperationAction = new UpdateOperationAction(state, settings);
        assertThat(updateOperationAction.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = updateOperationAction.children();
        assertThat(children.size(), is(5));

        ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertAuthenticateRecordAction(iterator.next(), updateRecord, state.getAuthenticator(), state.getUpdateServiceRequestDTO().getAuthenticationDTO());
        AssertActionsUtil.assertUpdateCommonRecordAction(iterator.next(), state.getRawRepo(), record, UpdateTestUtils.GROUP_ID, state.getLibraryRecordsHandler(),
                state.getHoldingsItems(), state.getOpenAgencyService());
        AssertActionsUtil.assertUpdateEnrichmentRecordAction(iterator.next(), state.getRawRepo(), littolkEnrichment, state.getLibraryRecordsHandler(), state.getHoldingsItems());
        AssertActionsUtil.assertDeleteCommonRecordAction(iterator.next(), state.getRawRepo(), littolkCommon, state.getLibraryRecordsHandler(), state.getHoldingsItems(),
                settings.getProperty(state.getRawRepoProviderId()));
        AssertActionsUtil.assertUpdateEnrichmentRecordAction(iterator.next(), state.getRawRepo(), enrichmentRecord, state.getLibraryRecordsHandler(), state.getHoldingsItems());
        assertThat(iterator.hasNext(), is(false));

    }

}
