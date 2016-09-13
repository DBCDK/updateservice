package dk.dbc.updateservice.ws;

import dk.dbc.updateservice.service.api.Entry;
import dk.dbc.updateservice.service.api.Type;
import dk.dbc.updateservice.actions.UpdateTestUtils;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author stp
 */
public class UpdateResponseWriterTest {

    public UpdateResponseWriterTest() {
    }

    //TODO: VERSION2: this test doesn't make sense
    @Test
    public void testAddValidateResults() {
        UpdateResponseWriter instance = new UpdateResponseWriter();
        List<Entry> err = UpdateTestUtils.createEntryList(Type.ERROR, "error");
        instance.addValidateEntries(err);
        assertTrue(instance != null);
    }

    //TODO: VERSION2: this test doesn't make sense
    @Test
    public void testBoolean() {
        assertEquals("true", Boolean.toString(true));
        assertEquals("false", Boolean.toString(false));
        assertTrue(Boolean.valueOf("True"));
        assertFalse(Boolean.valueOf("False"));
    }
}
