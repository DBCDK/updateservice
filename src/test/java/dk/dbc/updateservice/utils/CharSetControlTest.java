/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.utils;

import org.junit.Test;

import java.util.ResourceBundle;

import static org.junit.Assert.assertEquals;


public class CharSetControlTest {
    @Test
    public void testResourceBundlesGetBundle() {
        ResourceBundle instance = ResourceBundles.getBundle(this, "messages");
        assertEquals("Christian", instance.getString("latin1.letters"));
        assertEquals("æøåÆØÅ", instance.getString("danish.letters"));
    }
}
