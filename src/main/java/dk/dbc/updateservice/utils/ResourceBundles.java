/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.utils;

import java.util.Locale;
import java.util.ResourceBundle;

public class ResourceBundles {
    public static final Locale DANISH = new Locale("da", "DK");

    public static ResourceBundle getBundle(String bundleName) {
        return getBundle(bundleName, DANISH);
    }

    public static ResourceBundle getBundle(String bundleName, Locale locale) {
        return ResourceBundle.getBundle(bundleName, locale, new CharSetControl());
    }

    public static ResourceBundle getBundle(ClassLoader classloader, String bundleName) {
        return getBundle(classloader, bundleName, DANISH);
    }

    public static ResourceBundle getBundle(ClassLoader classloader, String bundleName, Locale locale) {
        return ResourceBundle.getBundle(bundleName, locale, classloader, new CharSetControl());
    }

    /**
     * Loads a resource bundle from the same package as <code>owner</code>.
     *
     * @param owner      Object that "owns" the resource bundle.
     * @param bundleName Name of the bundle.
     * @return The resource bundle.
     */
    public static ResourceBundle getBundle(Object owner, String bundleName) {
        return getBundle(owner.getClass().getClassLoader(), owner.getClass().getPackage().getName() + "." + bundleName);
    }
}
