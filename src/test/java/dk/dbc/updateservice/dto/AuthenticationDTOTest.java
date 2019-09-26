/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.dto;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AuthenticationDTOTest {

    @Test
    public void testToStringObfuscatesPassword() {
        AuthenticationDTO dto = new AuthenticationDTO();
        dto.setUserId("user");
        dto.setGroupId("groupid");
        dto.setPassword("password");

        assertEquals("AuthenticationDTO{userId='user', groupId='groupid', password='****'}", dto.toString());
    }

}
