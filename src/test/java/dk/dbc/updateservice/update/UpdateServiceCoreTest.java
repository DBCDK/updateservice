/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.updateservice.dto.AuthenticationDTO;
import dk.dbc.updateservice.dto.SchemasRequestDTO;
import dk.dbc.updateservice.dto.UpdateServiceRequestDTO;
import dk.dbc.updateservice.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

class UpdateServiceCoreTest {

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
    void scrambleSchemasRequestDTOTest() throws Exception {
        final SchemasRequestDTO schemasRequestDTO = new SchemasRequestDTO();
        schemasRequestDTO.setAuthenticationDTO(new AuthenticationDTO());
        schemasRequestDTO.getAuthenticationDTO().setPassword("super secret password");

        final String actual = UpdateServiceCore.scramblePassword(JsonMapper.encodePretty(schemasRequestDTO));
        final String expected = "{\n  \"authenticationDTO\" : {\n    \"userId\" : null,\n    \"groupId\" : null,\n    \"password\" : \"****\"\n  },\n  \"trackingId\" : null\n}";
        assertThat(actual, is(expected));
    }

}
