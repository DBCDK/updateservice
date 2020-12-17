/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.utils;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


class IOUtilsTest {

    private static final String RESOURCE_NAME = "dk/dbc/updateservice/utils/file.json";
    private static final String RESOURCE_ENCODING = "UTF-8";

    public IOUtilsTest() {
    }

    @Test
    void testGetResourceAsStream() {
        assertNotNull(IOUtils.getResourceAsStream(RESOURCE_NAME));
    }

    @Test
    void testLoadProperties_String() throws Exception {
        Properties props = IOUtils.loadProperties("dk/dbc/updateservice/utils/props1.properties");

        assertNotNull(props);
        assertEquals("v1", props.get("k1"));
        assertEquals("v2", props.get("k2"));
    }

    @Test
    void testLoadProperties_String_StringArray() throws Exception {
        Properties props = IOUtils.loadProperties(";", "dk/dbc/updateservice/utils/props1.properties", "dk/dbc/updateservice/utils/props2.properties");

        assertNotNull(props);
        assertEquals("v1", props.get("k1"));
        assertEquals("v2;v2", props.get("k2"));
        assertEquals("v3", props.get("k3"));
    }

    @Test
    void testReadAll_String() throws Exception {
        assertEquals("{ \"name\": \"Cookie Monster\" }", IOUtils.readAll(RESOURCE_NAME));
    }

    @Test
    void testReadAll_Class_String() throws Exception {
        assertEquals("{ \"name\": \"Cookie Monster\" }", IOUtils.readAll(RESOURCE_NAME, RESOURCE_ENCODING));
    }

    @Test
    void testReadAll_InputStream_String() throws Exception {
        assertEquals("{ \"name\": \"Cookie Monster\" }", IOUtils.readAll(IOUtils.getResourceAsStream(RESOURCE_NAME), RESOURCE_ENCODING));
    }

}
