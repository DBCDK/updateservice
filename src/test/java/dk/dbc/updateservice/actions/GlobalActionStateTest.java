package dk.dbc.updateservice.actions;

import dk.dbc.updateservice.client.BibliographicRecordExtraData;
import dk.dbc.updateservice.dto.AuthenticationDTO;
import dk.dbc.updateservice.dto.BibliographicRecordDTO;
import dk.dbc.updateservice.dto.ExtraRecordDataDTO;
import dk.dbc.updateservice.dto.UpdateServiceRequestDTO;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

public class GlobalActionStateTest {
    private GlobalActionState state;

    @Test
    public void testGetRecordExtraDataWithProvider() {
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
        extraRecordDataDTO.setContent(Arrays.asList(
                "\n",
                "<?xml version=\"1.0\" encoding=\"UTF-16\"?><cat:updateRecordExtraData xmlns:cat=\"http://oss.dbc.dk/ns/catalogingUpdate\">\n<providerName>bulk-broend</providerName>\n</cat:updateRecordExtraData>",
                "\n"
        ));
        bibliographicRecordDTO.setExtraRecordDataDTO(extraRecordDataDTO);

        updateServiceRequestDTO.setBibliographicRecordDTO(bibliographicRecordDTO);

        state = new UpdateTestUtils().getGlobalActionStateMockObject(updateServiceRequestDTO);

        final BibliographicRecordExtraData expected = new BibliographicRecordExtraData();
        expected.setProviderName("bulk-broend");

        assertThat(state.getRecordExtraData(), equalTo(expected));
    }

    @Test
    public void testGetRecordExtraDataNoData() {
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

        assertThat(state.getRecordExtraData(), equalTo(null));
    }

}
