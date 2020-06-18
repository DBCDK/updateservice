package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.updateservice.client.BibliographicRecordExtraData;
import dk.dbc.updateservice.dto.AuthenticationDTO;
import dk.dbc.updateservice.dto.BibliographicRecordDTO;
import dk.dbc.updateservice.dto.ExtraRecordDataDTO;
import dk.dbc.updateservice.dto.RecordDataDTO;
import dk.dbc.updateservice.dto.UpdateServiceRequestDTO;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

public class GlobalActionStateTest {
    private GlobalActionState state;

    @Test
    public void testGetMarcRecordAndExtraDataWithProviderAndPriority() throws Exception {
        final UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setSchemaName("dbc");

        final AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId("010100");
        authenticationDTO.setPassword("");
        authenticationDTO.setUserId("");
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);

        final BibliographicRecordDTO bibliographicRecordDTO = new BibliographicRecordDTO();
        bibliographicRecordDTO.setRecordPacking("xml");
        bibliographicRecordDTO.setRecordSchema("info:lc/xmlns/marcxchange-v1");

        final RecordDataDTO recordDataDTO = new RecordDataDTO();
        recordDataDTO.setContent(Arrays.asList(
                "\n",
                "<?xml version=\"1.0\" encoding=\"UTF-16\"?><record xmlns=\"info:lc/xmlns/marcxchange-v1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.loc.gov/standards/iso25577/marcxchange-1-1.xsd\">\n  <leader>00000n    2200000   4500</leader>\n  <datafield ind1=\"0\" ind2=\"0\" tag=\"001\"><subfield code=\"a\">55129010</subfield><subfield code=\"b\">870970</subfield><subfield code=\"c\">20181105122644</subfield><subfield code=\"d\">20181105</subfield><subfield code=\"f\">a</subfield>\n  </datafield>\n  <datafield ind1=\"0\" ind2=\"0\" tag=\"004\"><subfield code=\"r\">n</subfield><subfield code=\"a\">e</subfield>\n  </datafield>\n  <datafield ind1=\"0\" ind2=\"0\" tag=\"008\"><subfield code=\"t\">m</subfield><subfield code=\"v\">7</subfield>\n  </datafield>\n  <datafield ind1=\"0\" ind2=\"0\" tag=\"009\"><subfield code=\"a\">a</subfield><subfield code=\"g\">xx</subfield>\n  </datafield>\n  <datafield ind1=\"0\" ind2=\"0\" tag=\"021\"><subfield code=\"e\">9788703084558</subfield><subfield code=\"c\">hf.</subfield>\n  </datafield>\n  <datafield ind1=\"0\" ind2=\"0\" tag=\"032\"><subfield code=\"x\">ACC201845</subfield>\n  </datafield>\n  <datafield ind1=\"0\" ind2=\"0\" tag=\"245\"><subfield code=\"a\">Jul i stalden</subfield>\n  </datafield>\n  <datafield ind1=\"0\" ind2=\"0\" tag=\"260\"><subfield code=\"b\">Gyldendals Bogklubber</subfield>\n  </datafield>\n  <datafield ind1=\"0\" ind2=\"0\" tag=\"300\"><subfield code=\"a\">sider</subfield>\n  </datafield>\n  <datafield ind1=\"0\" ind2=\"0\" tag=\"652\"><subfield code=\"m\">NY TITEL</subfield>\n  </datafield>\n  <datafield ind1=\"0\" ind2=\"0\" tag=\"996\"><subfield code=\"a\">DBC</subfield>\n  </datafield>\n  <datafield ind1=\"0\" ind2=\"0\" tag=\"d08\"><subfield code=\"f\">cko</subfield>\n  </datafield>\n  <datafield ind1=\"0\" ind2=\"0\" tag=\"z98\"><subfield code=\"a\">Minus korrekturprint</subfield>\n  </datafield>\n  <datafield ind1=\"0\" ind2=\"0\" tag=\"z99\"><subfield code=\"a\">cko</subfield>\n  </datafield>\n</record>",
                "\n"
        ));

        bibliographicRecordDTO.setRecordDataDTO(recordDataDTO);

        final ExtraRecordDataDTO extraRecordDataDTO = new ExtraRecordDataDTO();
        extraRecordDataDTO.setContent(Arrays.asList(
                "\n",
                "<?xml version=\"1.0\" encoding=\"UTF-16\"?><cat:updateRecordExtraData xmlns:cat=\"http://oss.dbc.dk/ns/catalogingUpdate\">\n<providerName>bulk-broend</providerName>\n<priority>42</priority>\n</cat:updateRecordExtraData>",
                "\n"
        ));
        bibliographicRecordDTO.setExtraRecordDataDTO(extraRecordDataDTO);

        updateServiceRequestDTO.setBibliographicRecordDTO(bibliographicRecordDTO);

        state = new UpdateTestUtils().getGlobalActionStateMockObject(updateServiceRequestDTO);

        final MarcRecord expectedMarcRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.GLOBAL_ACTION_STATE);
        final BibliographicRecordExtraData expectedBibliographicRecordExtraData = new BibliographicRecordExtraData();
        expectedBibliographicRecordExtraData.setProviderName("bulk-broend");
        expectedBibliographicRecordExtraData.setPriority(42);

        assertThat(state.readRecord(), equalTo(expectedMarcRecord));
        assertThat(state.getMarcRecord(), equalTo(expectedMarcRecord));
        assertThat(state.getRecordExtraData(), equalTo(expectedBibliographicRecordExtraData));
    }

    @Test
    public void testGetMarcRecordWithDataButNoExtraData() throws Exception {
        final UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setSchemaName("dbc");

        final AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId("010100");
        authenticationDTO.setPassword("");
        authenticationDTO.setUserId("");
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);

        final BibliographicRecordDTO bibliographicRecordDTO = new BibliographicRecordDTO();
        bibliographicRecordDTO.setRecordPacking("xml");
        bibliographicRecordDTO.setRecordSchema("info:lc/xmlns/marcxchange-v1");

        final RecordDataDTO recordDataDTO = new RecordDataDTO();
        recordDataDTO.setContent(Arrays.asList(
                "\n",
                "<?xml version=\"1.0\" encoding=\"UTF-16\"?><record xmlns=\"info:lc/xmlns/marcxchange-v1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.loc.gov/standards/iso25577/marcxchange-1-1.xsd\">\n  <leader>00000n    2200000   4500</leader>\n  <datafield ind1=\"0\" ind2=\"0\" tag=\"001\"><subfield code=\"a\">55129010</subfield><subfield code=\"b\">870970</subfield><subfield code=\"c\">20181105122644</subfield><subfield code=\"d\">20181105</subfield><subfield code=\"f\">a</subfield>\n  </datafield>\n  <datafield ind1=\"0\" ind2=\"0\" tag=\"004\"><subfield code=\"r\">n</subfield><subfield code=\"a\">e</subfield>\n  </datafield>\n  <datafield ind1=\"0\" ind2=\"0\" tag=\"008\"><subfield code=\"t\">m</subfield><subfield code=\"v\">7</subfield>\n  </datafield>\n  <datafield ind1=\"0\" ind2=\"0\" tag=\"009\"><subfield code=\"a\">a</subfield><subfield code=\"g\">xx</subfield>\n  </datafield>\n  <datafield ind1=\"0\" ind2=\"0\" tag=\"021\"><subfield code=\"e\">9788703084558</subfield><subfield code=\"c\">hf.</subfield>\n  </datafield>\n  <datafield ind1=\"0\" ind2=\"0\" tag=\"032\"><subfield code=\"x\">ACC201845</subfield>\n  </datafield>\n  <datafield ind1=\"0\" ind2=\"0\" tag=\"245\"><subfield code=\"a\">Jul i stalden</subfield>\n  </datafield>\n  <datafield ind1=\"0\" ind2=\"0\" tag=\"260\"><subfield code=\"b\">Gyldendals Bogklubber</subfield>\n  </datafield>\n  <datafield ind1=\"0\" ind2=\"0\" tag=\"300\"><subfield code=\"a\">sider</subfield>\n  </datafield>\n  <datafield ind1=\"0\" ind2=\"0\" tag=\"652\"><subfield code=\"m\">NY TITEL</subfield>\n  </datafield>\n  <datafield ind1=\"0\" ind2=\"0\" tag=\"996\"><subfield code=\"a\">DBC</subfield>\n  </datafield>\n  <datafield ind1=\"0\" ind2=\"0\" tag=\"d08\"><subfield code=\"f\">cko</subfield>\n  </datafield>\n  <datafield ind1=\"0\" ind2=\"0\" tag=\"z98\"><subfield code=\"a\">Minus korrekturprint</subfield>\n  </datafield>\n  <datafield ind1=\"0\" ind2=\"0\" tag=\"z99\"><subfield code=\"a\">cko</subfield>\n  </datafield>\n</record>",
                "\n"
        ));

        bibliographicRecordDTO.setRecordDataDTO(recordDataDTO);

        updateServiceRequestDTO.setBibliographicRecordDTO(bibliographicRecordDTO);

        state = new UpdateTestUtils().getGlobalActionStateMockObject(updateServiceRequestDTO);

        final MarcRecord expectedMarcRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.GLOBAL_ACTION_STATE);

        assertThat(state.readRecord(), equalTo(expectedMarcRecord));
        assertThat(state.getMarcRecord(), equalTo(expectedMarcRecord));
        assertThat(state.getRecordExtraData(), equalTo(null));
    }

    @Test
    public void testGetMarcRecordAndExtraDataWithNoData() {
        final UpdateServiceRequestDTO updateServiceRequestDTO = new UpdateServiceRequestDTO();
        updateServiceRequestDTO.setSchemaName("dbc");

        final AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setGroupId("010100");
        authenticationDTO.setPassword("");
        authenticationDTO.setUserId("");
        updateServiceRequestDTO.setAuthenticationDTO(authenticationDTO);

        final BibliographicRecordDTO bibliographicRecordDTO = new BibliographicRecordDTO();
        bibliographicRecordDTO.setRecordPacking("xml");
        bibliographicRecordDTO.setRecordSchema("info:lc/xmlns/marcxchange-v1");

        final ExtraRecordDataDTO extraRecordDataDTO = new ExtraRecordDataDTO();
        extraRecordDataDTO.setContent(Collections.emptyList());
        bibliographicRecordDTO.setExtraRecordDataDTO(extraRecordDataDTO);

        updateServiceRequestDTO.setBibliographicRecordDTO(bibliographicRecordDTO);

        state = new UpdateTestUtils().getGlobalActionStateMockObject(updateServiceRequestDTO);

        assertThat(state.readRecord(), equalTo(null));
        assertThat(state.getMarcRecord(), equalTo(null));
        assertThat(state.getRecordExtraData(), equalTo(null));
    }

}
