package dk.dbc.common.records.utils;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

/**
 * @brief Implements utility functions for IO.
 */
public class IOUtils {

    private IOUtils() {

    }

    /**
     * @param name The name of the resource.
     * @return An InputStream if the resource exists. null otherwise.
     * @brief Returns an input stream for a given resource.
     *<p>
     * The resource is located in the class path and not only by the
     * current jar.
     *
     * @param name The name of the resource.
     *
     * @return An InputStream if the resource exists. null otherwise.
     */
    public static InputStream getResourceAsStream(String name) {
        return IOUtils.class.getClassLoader().getResourceAsStream(name);
    }

    /**
     * @brief Reads all content from a resource file and returns it.
     *
     * The resource is assumed to be a text resource.
     *
     * @param resName The resource name.
     *
     * @return The content of the resource.
     *
     * @throws IOException In case of IO failures.
     * @brief Reads all content from a resource file and returns it.
     *<p>
     * The resource is assumed to be a text resource.
     */
    public static String readAll(String resName) throws IOException {
        return readAll(resName, "UTF-8");
    }

    /**
     * @param resName  The resource name.
     * @param encoding Name for the encoding (charset) to use.
     *
     * @return The content of the resource.
     *
     * @throws IOException In case of IO failures.
     * @brief Reads all content from a resource file and returns it.
     * <p>
     * The resource is assumed to be a text resource.
     */
    public static String readAll(String resName, String encoding) throws IOException {
        return readAll(getResourceAsStream(resName), encoding);
    }

    /**
     * @brief Reads all content from an InputStream and returns it.
     *
     * The InputStream is assumed to be a text resource.
     *
     * @param in       InputStream
     * @param encoding Name for the encoding (charset) to use.
     *
     * @return The content of the resource.
     *
      @throws IOException                  In case of IO failures.
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

        return baos.toString(encoding);
    }

    //-----------------------------------------------------------------------------
    //              Files & Directories
    //-----------------------------------------------------------------------------

    public static boolean exists(File baseDir, String filename) throws IOException {
        return exists(baseDir.getCanonicalPath() + "/" + filename);
    }

    public static boolean exists(String filename) {
        return new File(filename).exists();
    }

}
