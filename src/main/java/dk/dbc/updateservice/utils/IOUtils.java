/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.utils;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import java.util.Properties;

/**
 * @brief Implements utility functions for IO.
 */
public class IOUtils {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(IOUtils.class);

    /**
     * @param name The name of the resource.
     * @return An InputStream if the resource exists. null otherwise.
     * @brief Returns an input stream for a given resource.
     * <p>
     * The resource is located in the class path and not only by the
     * current jar.
     */
    public static InputStream getResourceAsStream(String name) {
        return IOUtils.class.getClassLoader().getResourceAsStream(name);
    }

    /**
     * @param name The name of the resource.
     * @return The properties.
     * @throws IOException In case of IO error.
     * @brief Loads properties from a resource.
     */
    public static Properties loadProperties(String name) throws IOException {
        Properties props = new Properties();
        props.load(getResourceAsStream(name));

        return props;
    }

    /**
     * @param sep   Separator.
     * @param names The name of the resources.
     * @return The properties.
     * @throws IOException In case of IO error.
     * @brief Loads properties from multiple resources.
     * <p>
     * The properties from each resource are merged together by using an
     * separator to separate values for keys that exists in multiple resources.
     */
    public static Properties loadProperties(String sep, String... names) throws IOException {
        Properties result = new Properties();

        for (String name : names) {
            Properties props = loadProperties(name);

            Enumeration<?> propertyNames = props.propertyNames();
            while (propertyNames.hasMoreElements()) {
                String propName = propertyNames.nextElement().toString();

                if (result.containsKey(propName)) {
                    result.put(propName, result.getProperty(propName) + sep + props.getProperty(propName));
                } else {
                    result.put(propName, props.getProperty(propName));
                }
            }
        }

        return result;
    }

    /**
     * @param resName The resource name.
     * @return The content of the resource.
     * @throws IOException In case of IO failures.
     * @brief Reads all content from a resource file and returns it.
     * <p>
     * The resource is assumed to be a text resource.
     */
    public static String readAll(String resName) throws IOException {
        return readAll(resName, "UTF-8");
    }

    /**
     * @param resName  The resource name.
     * @param encoding Name for the encoding (charset) to use.
     * @return The content of the resource.
     * @throws IOException In case of IO failures.
     * @brief Reads all content from a resource file and returns it.
     * <p>
     * The resource is assumed to be a text resource.
     */
    public static String readAll(String resName, String encoding) throws IOException {
        return readAll(getResourceAsStream(resName), encoding);
    }

    /**
     * @param in       InputStream
     * @param encoding Name for the encoding (charset) to use.
     * @return The content of the resource.
     * @throws IOException                  In case of IO failures.
     * @throws UnsupportedEncodingException If the encoding is unknown.
     * @brief Reads all content from an InputStream and returns it.
     * <p>
     * The InputStream is assumed to be a text resource.
     */
    public static String readAll(InputStream in, String encoding) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        byte[] buffer = new byte[1024];
        int length;
        while ((length = in.read(buffer)) != -1) {
            baos.write(buffer, 0, length);
        }

        return new String(baos.toByteArray(), encoding);
    }

    public static boolean exists(File baseDir, String filename) throws IOException {
        LOGGER.entry(baseDir, filename);

        try {
            return exists(baseDir.getCanonicalPath() + "/" + filename);
        } finally {
            LOGGER.exit();
        }
    }

    public static boolean exists(String filename) {
        LOGGER.entry(filename);

        try {
            return new File(filename).exists();
        } finally {
            LOGGER.exit();
        }
    }

}
