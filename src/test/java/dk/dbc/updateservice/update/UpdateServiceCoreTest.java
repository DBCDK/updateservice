/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.holdingitems.content.HoldingsItemsConnector;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.updateservice.actions.AssertActionsUtil;
import dk.dbc.updateservice.dto.AuthenticationDTO;
import dk.dbc.updateservice.dto.BibliographicRecordDTO;
import dk.dbc.updateservice.dto.RecordDataDTO;
import dk.dbc.updateservice.dto.SchemasRequestDTO;
import dk.dbc.updateservice.dto.UpdateRecordResponseDTO;
import dk.dbc.updateservice.dto.UpdateServiceRequestDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UpdateServiceCoreTest {

    private final static String CONTENT_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><record xmlns=\"info:lc/xmlns/marcxchange-v1\" xsi:schemaLocation=\"http://www.loc.gov/standards/iso25577/marcxchange-1-1.xsd\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><leader>00000n    2200000   4500</leader>";
    private final static String CONTENT_FOOTER = "</record>";


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
        new MarcRecordWriter(marcRecord).addOrReplaceSubfield("z98", "b", "Minus påhængspost");
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
        new MarcRecordWriter(marcRecord).addOrReplaceSubfield("652", "m", "Ny titel");
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
        new MarcRecordWriter(marcRecord).addOrReplaceSubfield("652", "m", "Uden klassemærke");
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

        final UpdateServiceCore updateServiceCore =new UpdateServiceCore();
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

}
