//-----------------------------------------------------------------------------
package dk.dbc.updateservice.ws;

//-----------------------------------------------------------------------------
/**
 * Contains public accessible contants of all the JNDI resources, that is used
 * be all application.
 *
 * This also includes keys from Properties resources.
 */
public class JNDIResources {
    //-------------------------------------------------------------------------
    //              JDBC Resources
    //-------------------------------------------------------------------------

    public static final String JDBC_RAW_REPO_READONLY_NAME = "jdbc/updateservice/raw-repo-readonly";
    public static final String JDBC_RAW_REPO_WRITABLE_NAME = "jdbc/updateservice/raw-repo-writable";
    public static final String JDBC_HOLDINGITEMS_NAME = "jdbc/updateservice/holdingitems";

    //-------------------------------------------------------------------------
    //              Settings resource
    //-------------------------------------------------------------------------

    public static final String SETTINGS_NAME = "updateservice/settings";
    public static final String SOLR_URL_KEY = "solr.url";
    public static final String FORSRIGHTS_URL_KEY = "forsrights.url";
    public static final String AUTH_PRODUCT_NAME_KEY = "auth.product.name";
    public static final String AUTH_USE_IP_KEY = "auth.use.ip";

    public static final String JAVASCRIPT_BASEDIR_KEY = "javascript.basedir";
    public static final String JAVASCRIPT_INSTALL_NAME_KEY = "javascript.install.name";
}
