/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.utils;

import org.junit.jupiter.api.Test;

import java.util.ResourceBundle;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class CharSetControlTest {

    @Test
    void testResourceBundlesGetBundle() {
        ResourceBundle instance = ResourceBundles.getBundle(this, "messages");
        assertThat(instance.getString("latin1.letters"), is("Christian"));
        assertThat(instance.getString("danish.letters"), is("æøåÆØÅ"));
    }
}
