package dk.dbc.updateservice.utils;

import org.junit.Test;

import java.util.ResourceBundle;

import static org.junit.Assert.assertEquals;

/**
 * Created by stp on 09/04/15.
 */
public class CharSetControlTest {
    @Test
    public void testResourceBundlesGetBundle() {
        ResourceBundle instance = ResourceBundles.getBundle(this, "messages");
        assertEquals("Christian", instance.getString("latin1.letters"));
        assertEquals("æøåÆØÅ", instance.getString("danish.letters"));
    }
}
