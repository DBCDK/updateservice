package dk.dbc.updateservice.update;

import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.commons.jsonb.JSONBContext;
import dk.dbc.holdingitems.content.HoldingsItemsConnector;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.rawrepo.dto.RecordEntryDTO;
import dk.dbc.updateservice.actions.AssertActionsUtil;
import dk.dbc.updateservice.dto.AuthenticationDTO;
import dk.dbc.updateservice.dto.BibliographicRecordDTO;
import dk.dbc.updateservice.dto.RecordDTOMapper;
import dk.dbc.updateservice.dto.RecordDataDTO;
import dk.dbc.updateservice.dto.SchemasRequestDTO;
import dk.dbc.updateservice.dto.UpdateRecordResponseDTO;
import dk.dbc.updateservice.dto.UpdateServiceRequestDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.json.JsonMapper;
import dk.dbc.vipcore.libraryrules.VipCoreLibraryRulesConnector;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UpdateServiceCoreTest {

    private static final JSONBContext jsonbContext = new JSONBContext();

    @Test
    void scrambleUpdateServiceRequestDTOTest() throws Exception {
        final UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(new AuthenticationDTO());
        updateServiceRequestDTO.getAuthenticationDTO().setPassword("super secret password");

        final String actual = UpdateServiceCore.scramblePassword(JsonMapper.encodePretty(updateServiceRequestDTO));
        final String expected = "{\n  \"authenticationDTO\" : {\n    \"userId\" : null,\n    \"groupId\" : null,\n    \"password\" : \"****\"\n  },\n  \"schemaName\" : null,\n  \"bibliographicRecordDTO\" : null,\n  \"optionsDTO\" : null,\n  \"trackingId\" : null,\n  \"doubleRecordKey\" : null\n}";
        assertThat(actual, is(expected));
    }

    @Test
    void scrambleUpdateServiceRequestDTOTest_WeirdChars() throws Exception {
        final UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(new AuthenticationDTO());
        updateServiceRequestDTO.getAuthenticationDTO().setPassword("sdf93\"\n{},'");

        final String actual = UpdateServiceCore.scramblePassword(JsonMapper.encodePretty(updateServiceRequestDTO));
        final String expected = "{\n  \"authenticationDTO\" : {\n    \"userId\" : null,\n    \"groupId\" : null,\n    \"password\" : \"****\"\n  },\n  \"schemaName\" : null,\n  \"bibliographicRecordDTO\" : null,\n  \"optionsDTO\" : null,\n  \"trackingId\" : null,\n  \"doubleRecordKey\" : null\n}";
        assertThat(actual, is(expected));
    }

    @Test
    void scrambleUpdateServiceRequestDTOTest_EmptyPassword() throws Exception {
        final UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setAuthenticationDTO(new AuthenticationDTO());
        updateServiceRequestDTO.getAuthenticationDTO().setPassword("");

        final String actual = UpdateServiceCore.scramblePassword(JsonMapper.encodePretty(updateServiceRequestDTO));
        final String expected = "{\n  \"authenticationDTO\" : {\n    \"userId\" : null,\n    \"groupId\" : null,\n    \"password\" : \"\"\n  },\n  \"schemaName\" : null,\n  \"bibliographicRecordDTO\" : null,\n  \"optionsDTO\" : null,\n  \"trackingId\" : null,\n  \"doubleRecordKey\" : null\n}";
        assertThat(actual, is(expected));
    }

    @Test
    void scrambleSchemasRequestDTOTest() throws Exception {
        final SchemasRequestDTO schemasRequestDTO = new SchemasRequestDTO();
        schemasRequestDTO.setAuthenticationDTO(new AuthenticationDTO());
        schemasRequestDTO.getAuthenticationDTO().setPassword("super secret password");

        final String actual = UpdateServiceCore.scramblePassword(JsonMapper.encodePretty(schemasRequestDTO));
        final String expected = "{\n  \"authenticationDTO\" : {\n    \"userId\" : null,\n    \"groupId\" : null,\n    \"password\" : \"****\"\n  },\n  \"trackingId\" : null\n}";
        assertThat(actual, is(expected));
    }

    @Test
    void testClassificationCheck_NoRecord() {
        final BibliographicRecordDTO bibliographicRecordDTO = new BibliographicRecordDTO();
        bibliographicRecordDTO.setRecordSchema("info:lc/xmlns/marcxchange-v1");
        bibliographicRecordDTO.setRecordPacking("xml");
        bibliographicRecordDTO.setRecordDataDTO(new RecordDataDTO());
        bibliographicRecordDTO.getRecordDataDTO().setContent(Arrays.asList("", "", ""));

        final UpdateServiceCore updateServiceCore = new UpdateServiceCore();

        final UpdateRecordResponseDTO updateRecordResponseDTO = updateServiceCore.classificationCheck(bibliographicRecordDTO);
        assertThat(updateRecordResponseDTO.getUpdateStatusEnumDTO(), is(UpdateStatusEnumDTO.FAILED));
        assertThat(updateRecordResponseDTO.getMessageEntryDTOS().size(), is(1));
        assertThat(updateRecordResponseDTO.getMessageEntryDTOS().get(0).getMessage(), is("No record data found in request"));
    }

    @Test
    void testClassificationCheck_MinusEnrichment() throws Exception {
        final MarcRecord marcRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(marcRecord).addOrReplaceSubField("z98", 'b', "Minus påhængspost");
        final BibliographicRecordDTO bibliographicRecordDTO = AssertActionsUtil.constructBibliographicRecordDTO(marcRecord, null);

        final UpdateServiceCore updateServiceCore = new UpdateServiceCore();

        final UpdateRecordResponseDTO updateRecordResponseDTO = updateServiceCore.classificationCheck(bibliographicRecordDTO);
        assertThat(updateRecordResponseDTO.getUpdateStatusEnumDTO(), is(UpdateStatusEnumDTO.OK));
    }

    @Test
    void testClassificationCheck_NoExistingRecord() throws Exception {
        final MarcRecord marcRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        final BibliographicRecordDTO bibliographicRecordDTO = AssertActionsUtil.constructBibliographicRecordDTO(marcRecord, null);
        final MarcRecordReader recordReader = new MarcRecordReader(marcRecord);
        final String bibliographicRecordId = recordReader.getRecordId();
        final int agencyId = recordReader.getAgencyIdAsInt();

        final UpdateServiceCore updateServiceCore = new UpdateServiceCore();
        updateServiceCore.rawRepo = mock(RawRepo.class);

        when(updateServiceCore.rawRepo.recordExists(bibliographicRecordId, agencyId)).thenReturn(false);

        final UpdateRecordResponseDTO updateRecordResponseDTO = updateServiceCore.classificationCheck(bibliographicRecordDTO);
        assertThat(updateRecordResponseDTO.getUpdateStatusEnumDTO(), is(UpdateStatusEnumDTO.OK));
    }

    @Test
    void testClassificationCheck_RecordExisting_NoHoldings() throws Exception {
        final MarcRecord marcRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        final BibliographicRecordDTO bibliographicRecordDTO = AssertActionsUtil.constructBibliographicRecordDTO(marcRecord, null);
        final MarcRecordReader recordReader = new MarcRecordReader(marcRecord);
        final String bibliographicRecordId = recordReader.getRecordId();
        final int agencyId = recordReader.getAgencyIdAsInt();

        final UpdateServiceCore updateServiceCore = new UpdateServiceCore();
        updateServiceCore.rawRepo = mock(RawRepo.class);
        updateServiceCore.holdingsItems = mock(HoldingsItemsConnector.class);

        when(updateServiceCore.rawRepo.recordExists(bibliographicRecordId, agencyId)).thenReturn(true);
        when(updateServiceCore.rawRepo.fetchRecord(bibliographicRecordId, agencyId)).thenReturn(AssertActionsUtil.createRawRepoRecord(marcRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(updateServiceCore.holdingsItems.getAgenciesWithHoldings(bibliographicRecordId)).thenReturn(Collections.emptySet());

        final UpdateRecordResponseDTO updateRecordResponseDTO = updateServiceCore.classificationCheck(bibliographicRecordDTO);
        assertThat(updateRecordResponseDTO.getUpdateStatusEnumDTO(), is(UpdateStatusEnumDTO.OK));
    }

    @Test
    void testClassificationCheck_RecordExisting_Holdings_NyTitle() throws Exception {
        final MarcRecord marcRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(marcRecord).addOrReplaceSubField("652", 'm', "Ny titel");
        final BibliographicRecordDTO bibliographicRecordDTO = AssertActionsUtil.constructBibliographicRecordDTO(marcRecord, null);
        final MarcRecordReader recordReader = new MarcRecordReader(marcRecord);
        final String bibliographicRecordId = recordReader.getRecordId();
        final int agencyId = recordReader.getAgencyIdAsInt();

        final UpdateServiceCore updateServiceCore = new UpdateServiceCore();
        updateServiceCore.rawRepo = mock(RawRepo.class);
        updateServiceCore.holdingsItems = mock(HoldingsItemsConnector.class);
        updateServiceCore.libraryRecordsHandler = mock(LibraryRecordsHandler.class);

        when(updateServiceCore.rawRepo.recordExists(bibliographicRecordId, agencyId)).thenReturn(true);
        when(updateServiceCore.rawRepo.fetchRecord(bibliographicRecordId, agencyId)).thenReturn(AssertActionsUtil.createRawRepoRecord(marcRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(updateServiceCore.holdingsItems.getAgenciesWithHoldings(bibliographicRecordId)).thenReturn(new HashSet<>(Arrays.asList(1, 2, 3)));
        when(updateServiceCore.libraryRecordsHandler.hasClassificationsChanged(any(), any(), eq(new ArrayList<>()))).thenReturn(true);

        final UpdateRecordResponseDTO updateRecordResponseDTO = updateServiceCore.classificationCheck(bibliographicRecordDTO);
        assertThat(updateRecordResponseDTO.getUpdateStatusEnumDTO(), is(UpdateStatusEnumDTO.OK));
    }

    @Test
    void testClassificationCheck_RecordExisting_Holdings_UdenKlassemaerke() throws Exception {
        final MarcRecord marcRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(marcRecord).addOrReplaceSubField("652", 'm', "Uden klassemærke");
        final BibliographicRecordDTO bibliographicRecordDTO = AssertActionsUtil.constructBibliographicRecordDTO(marcRecord, null);
        final MarcRecordReader recordReader = new MarcRecordReader(marcRecord);
        final String bibliographicRecordId = recordReader.getRecordId();
        final int agencyId = recordReader.getAgencyIdAsInt();

        final UpdateServiceCore updateServiceCore = new UpdateServiceCore();
        updateServiceCore.rawRepo = mock(RawRepo.class);
        updateServiceCore.holdingsItems = mock(HoldingsItemsConnector.class);
        updateServiceCore.libraryRecordsHandler = mock(LibraryRecordsHandler.class);

        when(updateServiceCore.rawRepo.recordExists(bibliographicRecordId, agencyId)).thenReturn(true);
        when(updateServiceCore.rawRepo.fetchRecord(bibliographicRecordId, agencyId)).thenReturn(AssertActionsUtil.createRawRepoRecord(marcRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(updateServiceCore.holdingsItems.getAgenciesWithHoldings(bibliographicRecordId)).thenReturn(new HashSet<>(Arrays.asList(1, 2, 3)));
        when(updateServiceCore.libraryRecordsHandler.hasClassificationsChanged(any(), any(), eq(new ArrayList<>()))).thenReturn(true);

        final UpdateRecordResponseDTO updateRecordResponseDTO = updateServiceCore.classificationCheck(bibliographicRecordDTO);
        assertThat(updateRecordResponseDTO.getUpdateStatusEnumDTO(), is(UpdateStatusEnumDTO.OK));
    }

    @Test
    void testClassificationCheck_RecordExisting_Holdings() throws Exception {
        final MarcRecord marcRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        final BibliographicRecordDTO bibliographicRecordDTO = AssertActionsUtil.constructBibliographicRecordDTO(marcRecord, null);
        final MarcRecordReader recordReader = new MarcRecordReader(marcRecord);
        final String bibliographicRecordId = recordReader.getRecordId();
        final int agencyId = recordReader.getAgencyIdAsInt();

        final UpdateServiceCore updateServiceCore = new UpdateServiceCore();
        updateServiceCore.rawRepo = mock(RawRepo.class);
        updateServiceCore.holdingsItems = mock(HoldingsItemsConnector.class);
        updateServiceCore.libraryRecordsHandler = mock(LibraryRecordsHandler.class);

        when(updateServiceCore.rawRepo.recordExists(bibliographicRecordId, agencyId)).thenReturn(true);
        when(updateServiceCore.rawRepo.fetchRecord(bibliographicRecordId, agencyId)).thenReturn(AssertActionsUtil.createRawRepoRecord(marcRecord, MarcXChangeMimeType.MARCXCHANGE));
        when(updateServiceCore.holdingsItems.getAgenciesWithHoldings(bibliographicRecordId)).thenReturn(new HashSet<>(Arrays.asList(1, 2, 3)));
        when(updateServiceCore.libraryRecordsHandler.hasClassificationsChanged(any(), any(), eq(new ArrayList<>()))).thenReturn(true);

        final UpdateRecordResponseDTO updateRecordResponseDTO = updateServiceCore.classificationCheck(bibliographicRecordDTO);
        assertThat(updateRecordResponseDTO.getUpdateStatusEnumDTO(), is(UpdateStatusEnumDTO.FAILED));
        assertThat(updateRecordResponseDTO.getMessageEntryDTOS().size(), is(1));
        assertThat(updateRecordResponseDTO.getMessageEntryDTOS().get(0).getMessage(), is("Count: 3"));
    }

    @Test
    void testPutNew191919EnrichmentNoParent() throws Exception {
        final String dtoString = loadJson("135010529-191919.json");
        final RecordEntryDTO recordDTO = jsonbContext.unmarshall(dtoString, RecordEntryDTO.class);

        final UpdateServiceCore updateServiceCore = new UpdateServiceCore();
        updateServiceCore.rawRepo = mock(RawRepo.class);
        updateServiceCore.holdingsItems = mock(HoldingsItemsConnector.class);
        updateServiceCore.libraryRecordsHandler = mock(LibraryRecordsHandler.class);
        updateServiceCore.vipCoreService = mock(VipCoreService.class);
        when(updateServiceCore.rawRepo.fetchRecord("135010529", 191919)).thenReturn(new RawRepoRecordMock("20611529", 191919));
        when(updateServiceCore.vipCoreService.hasFeature("191919", VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)).thenReturn(true);

        when(updateServiceCore.rawRepo.agenciesForRecordAll("135010529")).thenReturn(new HashSet<>());

        UpdateException ex = assertThrows(UpdateException.class, () -> updateServiceCore.updateRecord(recordDTO));
        assertThat(ex.getMessage(), is("Unable to determine parent agency id for enrichment record 135010529:191919"));
    }

    @Test
    void testPutNewFBSEnrichmentNoParent() throws Exception {
        final String dtoString = loadJson("135010529-763000.json");
        final RecordEntryDTO recordDTO = jsonbContext.unmarshall(dtoString, RecordEntryDTO.class);
        final RecordId enrichmentRecordId = new RecordId("135010529", 763000);
        final RecordId commonRecordId = new RecordId("135010529", 870970);

        final UpdateServiceCore updateServiceCore = new UpdateServiceCore();
        updateServiceCore.settings.setProperty(JNDIResources.RAWREPO_PROVIDER_ID_FBS, "fbs-provider");
        updateServiceCore.rawRepo = mock(RawRepo.class);
        updateServiceCore.holdingsItems = mock(HoldingsItemsConnector.class);
        updateServiceCore.libraryRecordsHandler = mock(LibraryRecordsHandler.class);
        updateServiceCore.vipCoreService = mock(VipCoreService.class);
        when(updateServiceCore.rawRepo.fetchRecord("135010529", 763000)).thenReturn(new RawRepoRecordMock("135010529", 763000));
        when(updateServiceCore.rawRepo.fetchRecord("135010529", 870970)).thenReturn(new RawRepoRecordMock("135010529", 870970));
        when(updateServiceCore.vipCoreService.hasFeature("763000", VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)).thenReturn(true);
        when(updateServiceCore.vipCoreService.getLibraryGroup("763000")).thenReturn(LibraryGroup.fromRule("fbs").get());

        updateServiceCore.updateRecord(recordDTO);
        final MarcRecord enrichmentMarcRecord = RecordDTOMapper.getMarcRecord(recordDTO);

        final ArgumentCaptor<Record> enrichmentRecordCaptor = ArgumentCaptor.forClass(Record.class);
        final ArgumentCaptor<Record> commonRecordCaptor = ArgumentCaptor.forClass(Record.class);

        final InOrder inOrder = Mockito.inOrder(updateServiceCore.rawRepo);
        inOrder.verify(updateServiceCore.rawRepo).fetchRecord("135010529", 763000);
        inOrder.verify(updateServiceCore.rawRepo).saveRecord(enrichmentRecordCaptor.capture());
        inOrder.verify(updateServiceCore.rawRepo).removeLinks(enrichmentRecordId);
        Record enrichmentRecord = enrichmentRecordCaptor.getValue();
        assertThat(enrichmentRecord.getId(), is(enrichmentRecordId));
        assertThat(enrichmentRecord.getContent(), is(UpdateRecordContentTransformer.encodeRecord(enrichmentMarcRecord)));
        assertThat(enrichmentRecord.getContentJson(), is(UpdateRecordContentTransformer.encodeRecordToJson(enrichmentMarcRecord)));
        assertThat(enrichmentRecord.getMimeType(), is(MarcXChangeMimeType.ENRICHMENT));

        inOrder.verify(updateServiceCore.rawRepo).fetchRecord("135010529", 870970);
        inOrder.verify(updateServiceCore.rawRepo).saveRecord(commonRecordCaptor.capture());
        Record commonRecord = commonRecordCaptor.getValue();
        assertThat(commonRecord.getId(), is(commonRecordId));
        assertThat(commonRecord.getContent(), is(new byte[0]));
        assertThat(commonRecord.getContentJson(), is("{}".getBytes()));
        assertThat(commonRecord.getMimeType(), is(MarcXChangeMimeType.MARCXCHANGE));

        inOrder.verify(updateServiceCore.rawRepo).linkRecordAppend(enrichmentRecordId, commonRecordId);
        inOrder.verify(updateServiceCore.rawRepo).enqueue(enrichmentRecordId, "fbs-provider", true, false, 500);

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void testPutNewEnrichmentWithParent() throws Exception {
        final String dtoString = loadJson("135010529-191919.json");
        final RecordEntryDTO recordDTO = jsonbContext.unmarshall(dtoString, RecordEntryDTO.class);
        final RecordId enrichmentRecordId = new RecordId("135010529", 191919);
        final RecordId commonRecordId = new RecordId("135010529", 870970);

        final UpdateServiceCore updateServiceCore = new UpdateServiceCore();
        updateServiceCore.settings.setProperty(JNDIResources.RAWREPO_PROVIDER_ID_DBC, "dbc-provider");
        updateServiceCore.rawRepo = mock(RawRepo.class);
        updateServiceCore.holdingsItems = mock(HoldingsItemsConnector.class);
        updateServiceCore.libraryRecordsHandler = mock(LibraryRecordsHandler.class);
        updateServiceCore.vipCoreService = mock(VipCoreService.class);
        when(updateServiceCore.vipCoreService.hasFeature("191919", VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)).thenReturn(true);
        when(updateServiceCore.vipCoreService.getAgencyPriority(191919)).thenReturn(Collections.singletonList(870970));
        when(updateServiceCore.vipCoreService.getLibraryGroup("191919")).thenReturn(LibraryGroup.fromRule("dbc").get());
        when(updateServiceCore.rawRepo.fetchRecord("135010529", 191919)).thenReturn(new RawRepoRecordMock("135010529", 191919));
        when(updateServiceCore.rawRepo.fetchRecord("135010529", 870970)).thenReturn(new RawRepoRecordMock("135010529", 870970));
        when(updateServiceCore.rawRepo.agenciesForRecordAll("135010529")).thenReturn(Collections.singleton(870970));

        updateServiceCore.updateRecord(recordDTO);
        final MarcRecord enrichmentMarcRecord = RecordDTOMapper.getMarcRecord(recordDTO);

        final ArgumentCaptor<Record> enrichmentRecordCaptor = ArgumentCaptor.forClass(Record.class);
        final ArgumentCaptor<Record> commonRecordCaptor = ArgumentCaptor.forClass(Record.class);

        final InOrder inOrder = Mockito.inOrder(updateServiceCore.rawRepo);
        inOrder.verify(updateServiceCore.rawRepo).fetchRecord("135010529", 191919);
        inOrder.verify(updateServiceCore.rawRepo).agenciesForRecordAll("135010529");
        inOrder.verify(updateServiceCore.rawRepo).saveRecord(enrichmentRecordCaptor.capture());
        inOrder.verify(updateServiceCore.rawRepo).removeLinks(enrichmentRecordId);
        Record enrichmentRecord = enrichmentRecordCaptor.getValue();
        assertThat(enrichmentRecord.getId(), is(enrichmentRecordId));
        assertThat(enrichmentRecord.getContent(), is(UpdateRecordContentTransformer.encodeRecord(enrichmentMarcRecord)));
        assertThat(enrichmentRecord.getContentJson(), is(UpdateRecordContentTransformer.encodeRecordToJson(enrichmentMarcRecord)));
        assertThat(enrichmentRecord.getMimeType(), is(MarcXChangeMimeType.ENRICHMENT));

        inOrder.verify(updateServiceCore.rawRepo).fetchRecord("135010529", 870970);
        inOrder.verify(updateServiceCore.rawRepo).saveRecord(commonRecordCaptor.capture());
        Record commonRecord = commonRecordCaptor.getValue();
        assertThat(commonRecord.getId(), is(commonRecordId));
        assertThat(commonRecord.getContent(), is(new byte[0]));
        assertThat(commonRecord.getContentJson(), is("{}".getBytes()));
        assertThat(commonRecord.getMimeType(), is(MarcXChangeMimeType.MARCXCHANGE));

        inOrder.verify(updateServiceCore.rawRepo).linkRecordAppend(enrichmentRecordId, commonRecordId);
        inOrder.verify(updateServiceCore.rawRepo).enqueue(enrichmentRecordId, "dbc-provider", true, false, 500);

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void testPutUpdateEnrichment() throws Exception {
        final String dtoString = loadJson("135010529-191919.json");
        final RecordEntryDTO recordDTO = jsonbContext.unmarshall(dtoString, RecordEntryDTO.class);
        final RecordId enrichmentRecordId = new RecordId("135010529", 191919);
        final RecordId commonRecordId = new RecordId("135010529", 870970);

        final UpdateServiceCore updateServiceCore = new UpdateServiceCore();
        updateServiceCore.settings.setProperty(JNDIResources.RAWREPO_PROVIDER_ID_DBC, "dbc-provider");
        updateServiceCore.rawRepo = mock(RawRepo.class);
        updateServiceCore.holdingsItems = mock(HoldingsItemsConnector.class);
        updateServiceCore.libraryRecordsHandler = mock(LibraryRecordsHandler.class);
        updateServiceCore.vipCoreService = mock(VipCoreService.class);
        when(updateServiceCore.vipCoreService.hasFeature("191919", VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)).thenReturn(true);
        when(updateServiceCore.vipCoreService.getAgencyPriority(191919)).thenReturn(Collections.singletonList(870970));
        when(updateServiceCore.vipCoreService.getLibraryGroup("191919")).thenReturn(LibraryGroup.fromRule("dbc").get());
        when(updateServiceCore.rawRepo.fetchRecord("135010529", 191919)).thenReturn(new RawRepoRecordMock("135010529", 191919));
        when(updateServiceCore.rawRepo.fetchRecord("135010529", 870970)).thenReturn(new RawRepoRecordMock("135010529", 870970));
        when(updateServiceCore.rawRepo.agenciesForRecordAll("135010529")).thenReturn(Collections.singleton(870970));

        updateServiceCore.updateRecord(recordDTO);
        final MarcRecord enrichmentMarcRecord = RecordDTOMapper.getMarcRecord(recordDTO);

        final ArgumentCaptor<Record> enrichmentRecordCaptor = ArgumentCaptor.forClass(Record.class);

        final InOrder inOrder = Mockito.inOrder(updateServiceCore.rawRepo);
        inOrder.verify(updateServiceCore.rawRepo).fetchRecord("135010529", 191919);
        inOrder.verify(updateServiceCore.rawRepo).agenciesForRecordAll("135010529");
        inOrder.verify(updateServiceCore.rawRepo).saveRecord(enrichmentRecordCaptor.capture());
        inOrder.verify(updateServiceCore.rawRepo).removeLinks(enrichmentRecordId);
        Record enrichmentRecord = enrichmentRecordCaptor.getValue();
        assertThat(enrichmentRecord.getId(), is(enrichmentRecordId));
        assertThat(enrichmentRecord.getContent(), is(UpdateRecordContentTransformer.encodeRecord(enrichmentMarcRecord)));
        assertThat(enrichmentRecord.getContentJson(), is(UpdateRecordContentTransformer.encodeRecordToJson(enrichmentMarcRecord)));
        assertThat(enrichmentRecord.getMimeType(), is(MarcXChangeMimeType.ENRICHMENT));

        inOrder.verify(updateServiceCore.rawRepo).linkRecordAppend(enrichmentRecordId, commonRecordId);
        inOrder.verify(updateServiceCore.rawRepo).enqueue(enrichmentRecordId, "dbc-provider", true, false, 500);

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void testPutNewCommonRecordWithAuthority() throws Exception {
        final String dtoString = loadJson("135010529-870970.json");
        final RecordEntryDTO recordDTO = jsonbContext.unmarshall(dtoString, RecordEntryDTO.class);
        final RecordId commonRecordId = new RecordId("135010529", 870970);

        final UpdateServiceCore updateServiceCore = new UpdateServiceCore();
        updateServiceCore.settings.setProperty(JNDIResources.RAWREPO_PROVIDER_ID_DBC, "dbc-provider");
        updateServiceCore.rawRepo = mock(RawRepo.class);
        updateServiceCore.holdingsItems = mock(HoldingsItemsConnector.class);
        updateServiceCore.libraryRecordsHandler = mock(LibraryRecordsHandler.class);
        updateServiceCore.vipCoreService = mock(VipCoreService.class);
        when(updateServiceCore.vipCoreService.hasFeature("870970", VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)).thenReturn(true);
        when(updateServiceCore.vipCoreService.getAgencyPriority(870970)).thenReturn(Collections.singletonList(870970));
        when(updateServiceCore.vipCoreService.getLibraryGroup("870970")).thenReturn(LibraryGroup.fromRule("dbc").get());
        when(updateServiceCore.rawRepo.fetchRecord("135010529", 870970)).thenReturn(new RawRepoRecordMock("135010529", 870970));
        when(updateServiceCore.rawRepo.agenciesForRecordAll("135010529")).thenReturn(Collections.singleton(870970));

        when(updateServiceCore.rawRepo.fetchRecord("69599087", 870979)).thenReturn(new RawRepoRecordMock("69599087", 870979));
        when(updateServiceCore.rawRepo.fetchRecord("90058169", 870979)).thenReturn(new RawRepoRecordMock("90058169", 870979));
        when(updateServiceCore.rawRepo.fetchRecord("93131134", 870979)).thenReturn(new RawRepoRecordMock("93131134", 870979));
        when(updateServiceCore.rawRepo.fetchRecord("90609807", 870979)).thenReturn(new RawRepoRecordMock("90609807", 870979));
        when(updateServiceCore.rawRepo.fetchRecord("92761673", 870979)).thenReturn(new RawRepoRecordMock("92761673", 870979));

        updateServiceCore.updateRecord(recordDTO);
        final MarcRecord commonRecord = RecordDTOMapper.getMarcRecord(recordDTO);

        final ArgumentCaptor<Record> commonRecordCaptor = ArgumentCaptor.forClass(Record.class);
        final ArgumentCaptor<Record> parentRecordCaptor = ArgumentCaptor.forClass(Record.class);

        final InOrder inOrder = Mockito.inOrder(updateServiceCore.rawRepo);
        inOrder.verify(updateServiceCore.rawRepo).fetchRecord("135010529", 870970);
        inOrder.verify(updateServiceCore.rawRepo).saveRecord(commonRecordCaptor.capture());
        inOrder.verify(updateServiceCore.rawRepo).removeLinks(commonRecordId);
        Record commonRecordCaptorValue = commonRecordCaptor.getValue();
        assertThat(commonRecordCaptorValue.getId(), is(commonRecordId));
        assertThat(commonRecordCaptorValue.getContent(), is(UpdateRecordContentTransformer.encodeRecord(commonRecord)));
        assertThat(commonRecordCaptorValue.getContentJson(), is(UpdateRecordContentTransformer.encodeRecordToJson(commonRecord)));
        assertThat(commonRecordCaptorValue.getMimeType(), is(MarcXChangeMimeType.MARCXCHANGE));

        inOrder.verify(updateServiceCore.rawRepo).fetchRecord("69599087", 870979);
        inOrder.verify(updateServiceCore.rawRepo).saveRecord(parentRecordCaptor.capture());
        inOrder.verify(updateServiceCore.rawRepo).linkRecordAppend(commonRecordId, new RecordId("69599087", 870979));
        Record parentRecord = parentRecordCaptor.getValue();
        assertThat(parentRecord.getId(), is(new RecordId("69599087", 870979)));
        assertThat(parentRecord.getContent(), is(new byte[0]));
        assertThat(parentRecord.getContentJson(), is("{}".getBytes()));
        assertThat(parentRecord.getMimeType(), is(MarcXChangeMimeType.AUTHORITY));

        inOrder.verify(updateServiceCore.rawRepo).fetchRecord("90058169", 870979);
        inOrder.verify(updateServiceCore.rawRepo).saveRecord(parentRecordCaptor.capture());
        inOrder.verify(updateServiceCore.rawRepo).linkRecordAppend(commonRecordId, new RecordId("90058169", 870979));
        parentRecord = parentRecordCaptor.getValue();
        assertThat(parentRecord.getId(), is(new RecordId("90058169", 870979)));
        assertThat(parentRecord.getContent(), is(new byte[0]));
        assertThat(parentRecord.getContentJson(), is("{}".getBytes()));
        assertThat(parentRecord.getMimeType(), is(MarcXChangeMimeType.AUTHORITY));

        inOrder.verify(updateServiceCore.rawRepo).fetchRecord("93131134", 870979);
        inOrder.verify(updateServiceCore.rawRepo).saveRecord(parentRecordCaptor.capture());
        inOrder.verify(updateServiceCore.rawRepo).linkRecordAppend(commonRecordId, new RecordId("93131134", 870979));
        parentRecord = parentRecordCaptor.getValue();
        assertThat(parentRecord.getId(), is(new RecordId("93131134", 870979)));
        assertThat(parentRecord.getContent(), is(new byte[0]));
        assertThat(parentRecord.getContentJson(), is("{}".getBytes()));
        assertThat(parentRecord.getMimeType(), is(MarcXChangeMimeType.AUTHORITY));

        inOrder.verify(updateServiceCore.rawRepo).fetchRecord("90609807", 870979);
        inOrder.verify(updateServiceCore.rawRepo).saveRecord(parentRecordCaptor.capture());
        inOrder.verify(updateServiceCore.rawRepo).linkRecordAppend(commonRecordId, new RecordId("90609807", 870979));
        parentRecord = parentRecordCaptor.getValue();
        assertThat(parentRecord.getId(), is(new RecordId("90609807", 870979)));
        assertThat(parentRecord.getContent(), is(new byte[0]));
        assertThat(parentRecord.getContentJson(), is("{}".getBytes()));
        assertThat(parentRecord.getMimeType(), is(MarcXChangeMimeType.AUTHORITY));

        inOrder.verify(updateServiceCore.rawRepo).fetchRecord("92761673", 870979);
        inOrder.verify(updateServiceCore.rawRepo).saveRecord(parentRecordCaptor.capture());
        inOrder.verify(updateServiceCore.rawRepo).linkRecordAppend(commonRecordId, new RecordId("92761673", 870979));
        parentRecord = parentRecordCaptor.getValue();
        assertThat(parentRecord.getId(), is(new RecordId("92761673", 870979)));
        assertThat(parentRecord.getContent(), is(new byte[0]));
        assertThat(parentRecord.getContentJson(), is("{}".getBytes()));
        assertThat(parentRecord.getMimeType(), is(MarcXChangeMimeType.AUTHORITY));

        inOrder.verify(updateServiceCore.rawRepo).enqueue(commonRecordId, "dbc-provider", true, false, 500);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void testPutUndeleteEnrichment() throws Exception {
        final String dtoString = loadJson("135010529-191919.json");
        final RecordId enrichmentRecordId = new RecordId("135010529", 191919);
        final RecordId commonRecordId = new RecordId("135010529", 870970);
        final RecordEntryDTO recordDTO = jsonbContext.unmarshall(dtoString, RecordEntryDTO.class);

        final Record deletedParentRecord = new RawRepoRecordMock("135010529", 870970);
        deletedParentRecord.setDeleted(true);
        deletedParentRecord.setContent("not empty byte array".getBytes());

        final UpdateServiceCore updateServiceCore = new UpdateServiceCore();
        updateServiceCore.settings.setProperty(JNDIResources.RAWREPO_PROVIDER_ID_DBC, "dbc-provider");
        updateServiceCore.rawRepo = mock(RawRepo.class);
        updateServiceCore.holdingsItems = mock(HoldingsItemsConnector.class);
        updateServiceCore.libraryRecordsHandler = mock(LibraryRecordsHandler.class);
        updateServiceCore.vipCoreService = mock(VipCoreService.class);
        when(updateServiceCore.rawRepo.agenciesForRecordAll("135010529")).thenReturn(new HashSet<>(Arrays.asList(191919, 870970)));
        when(updateServiceCore.rawRepo.fetchRecord("135010529", 191919)).thenReturn(new RawRepoRecordMock("135010529", 191919));
        when(updateServiceCore.rawRepo.fetchRecord("135010529", 870970)).thenReturn(deletedParentRecord);
        when(updateServiceCore.vipCoreService.hasFeature("191919", VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)).thenReturn(true);
        when(updateServiceCore.vipCoreService.getAgencyPriority(191919)).thenReturn(new ArrayList<>(Arrays.asList(191919, 870970)));
        when(updateServiceCore.vipCoreService.getLibraryGroup("191919")).thenReturn(LibraryGroup.fromRule("dbc").get());

        updateServiceCore.updateRecord(recordDTO);
        final MarcRecord enrichment = RecordDTOMapper.getMarcRecord(recordDTO);

        final ArgumentCaptor<Record> enrichmentRecordCaptor = ArgumentCaptor.forClass(Record.class);
        final ArgumentCaptor<Record> commonRecordCaptor = ArgumentCaptor.forClass(Record.class);

        final InOrder inOrder = Mockito.inOrder(updateServiceCore.rawRepo);
        inOrder.verify(updateServiceCore.rawRepo).fetchRecord("135010529", 191919);
        inOrder.verify(updateServiceCore.rawRepo).saveRecord(enrichmentRecordCaptor.capture());
        inOrder.verify(updateServiceCore.rawRepo).removeLinks(enrichmentRecordId);
        final Record enrichmentRecordCaptorValue = enrichmentRecordCaptor.getValue();
        assertThat(enrichmentRecordCaptorValue.getId(), is(enrichmentRecordId));
        assertThat(enrichmentRecordCaptorValue.getContent(), is(UpdateRecordContentTransformer.encodeRecord(enrichment)));
        assertThat(enrichmentRecordCaptorValue.getContentJson(), is(UpdateRecordContentTransformer.encodeRecordToJson(enrichment)));
        assertThat(enrichmentRecordCaptorValue.getMimeType(), is(MarcXChangeMimeType.ENRICHMENT));

        inOrder.verify(updateServiceCore.rawRepo).fetchRecord("135010529", 870970);
        inOrder.verify(updateServiceCore.rawRepo).saveRecord(commonRecordCaptor.capture());
        final Record commonRecordCaptorValue = commonRecordCaptor.getValue();
        assertThat(commonRecordCaptorValue.getId(), is(commonRecordId));
        assertThat(commonRecordCaptorValue.isDeleted(), is(false));
        assertThat(commonRecordCaptorValue.getContent(), is("not empty byte array".getBytes()));
        assertThat(commonRecordCaptorValue.getMimeType(), is(MarcXChangeMimeType.MARCXCHANGE));

        inOrder.verify(updateServiceCore.rawRepo).linkRecordAppend(enrichmentRecordId, commonRecordId);
        inOrder.verify(updateServiceCore.rawRepo).enqueue(enrichmentRecordId, "dbc-provider", true, false, 500);
        inOrder.verifyNoMoreInteractions();
    }

    private String loadJson(String filename) throws IOException {
        try (InputStream is = UpdateServiceCoreTest.class.getResourceAsStream("/dk/dbc/updateservice/update/" + filename)) {
            assert is != null;

            return new String(is.readAllBytes());
        }
    }

}

