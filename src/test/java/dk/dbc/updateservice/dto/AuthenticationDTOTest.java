/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.dto;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


class AuthenticationDTOTest {

    @Test
    void testToStringObfuscatesPassword() {
        AuthenticationDTO dto = new AuthenticationDTO();
        dto.setUserId("user");
        dto.setGroupId("groupid");
        dto.setPassword("password");

        assertThat(dto.toString(), is("AuthenticationDTO{userId='user', groupId='groupid', password='****'}"));
    }

}
