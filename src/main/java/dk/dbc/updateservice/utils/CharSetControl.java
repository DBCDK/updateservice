/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.utils;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

/**
 * ResourceBundle.Control extention to read resource bundles in any charset.
 */
public class CharSetControl extends ResourceBundle.Control {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(CharSetControl.class);
    private static final String DEFAULT_CHARSET = "UTF-8";
    private final String charset;

    public CharSetControl() {
        this(DEFAULT_CHARSET);
    }

    public CharSetControl(String charset) {
        super();
        this.charset = charset;
    }

    /**
     * Default implementation to read resource bundle files in any charset.
     * <p/>
     * The implementation is taken from http://stackoverflow.com/questions/4659929/how-to-use-utf-8-in-resource-properties-with-resourcebundle
     * and changed to read files in any charset.
     *
     * @param baseName the base bundle name of the resource bundle, a fully qualified class name
     * @param locale   the locale for which the resource bundle should be instantiated
     * @param format   the resource bundle format to be loaded
     * @param loader   the ClassLoader to use to load the bundle
     * @param reload   the flag to indicate bundle reloading; true if reloading an expired resource bundle, false otherwise
     * @return the resource bundle instance, or null if none could be found.
     * @throws IOException            if an error occurred when reading resources using any I/O operations
     */
    @Override
    public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader, boolean reload)
            throws IOException {
        ResourceBundle bundle = null;
        final String bundleName = toBundleName(baseName, locale);
        final String resourceName = toResourceName(bundleName, "properties");
        InputStream stream = null;
        if (reload) {
            URL url = loader.getResource(resourceName);
            if (url != null) {
                URLConnection connection = url.openConnection();
                if (connection != null) {
                    connection.setUseCaches(false);
                    stream = connection.getInputStream();
                }
            }
        } else {
            stream = loader.getResourceAsStream(resourceName);
        }
        if (stream != null) {
            try {
                LOGGER.trace("Reading properties with charset {}", this.charset);
                bundle = new PropertyResourceBundle(new InputStreamReader(stream, this.charset));
            } finally {
                stream.close();
            }
        }
        return bundle;
    }

}
