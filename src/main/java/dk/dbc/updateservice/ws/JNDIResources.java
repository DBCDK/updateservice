/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.ws;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains public accessible contants of all the JNDI resources, that is used
 * be all application.
 *
 * This also includes keys from Properties resources.
 */
public class JNDIResources {
    // Managed Executor Service Resources
    public static final String RAWREPO_CACHE_EXECUTOR_SERVICE = "concurrent/rawrepo";

    // JDBC Resources
    public static final String JDBC_RAW_REPO_READONLY_NAME = "jdbc/updateservice/raw-repo-readonly";
    public static final String JDBC_RAW_REPO_WRITABLE_NAME = "jdbc/updateservice/raw-repo-writable";
    public static final String JDBC_HOLDINGITEMS_NAME = "jdbc/updateservice/holdingitems";
    public static final String JDBC_UPDATE_STORE_NAME = "jdbc/updateservice/updateservicestore";

    // Settings resource
    public static final String JNDI_NAME_UPDATESERVICE = "updateservice/settings";
    public static final String JNDI_NAME_BUILDSERVICE = "env/iscrum/build/properties";

    public static final String SOLR_URL_KEY = "solr.url";
    public static final String FORSRIGHTS_URL_KEY = "forsrights.url";
    public static final String OPENAGENCY_URL_KEY = "openagency.url";
    public static final String OPENAGENCY_CACHE_AGE_KEY = "openagency.cache.age";
    public static final String AUTH_PRODUCT_NAME_KEY = "auth.product.name";
    public static final String AUTH_USE_IP_KEY = "auth.use.ip";

    public static final String JAVASCRIPT_BASEDIR_KEY = "javascript.basedir";
    public static final String JAVASCRIPT_POOL_SIZE_KEY = "javascript.pool.size";

    public static final String DOUBLE_RECORD_MAIL_HOST_KEY = "double.record.mail.host";
    public static final String DOUBLE_RECORD_MAIL_PORT_KEY = "double.record.mail.port";
    public static final String DOUBLE_RECORD_MAIL_USER_KEY = "double.record.mail.user";
    public static final String DOUBLE_RECORD_MAIL_PASSWORD_KEY = "double.record.mail.password";
    public static final String DOUBLE_RECORD_MAIL_FROM_KEY = "double.record.mail.from";
    public static final String DOUBLE_RECORD_MAIL_RECIPIENT_KEY = "double.record.mail.recipients";

    public static final String RAWREPO_PROVIDER_ID_DBC = "rawrepo.provider.id.dbc";
    public static final String RAWREPO_PROVIDER_ID_DBC_SOLR = "rawrepo.provider.id.dbc.solr";
    public static final String RAWREPO_PROVIDER_ID_FBS = "rawrepo.provider.id.fbs";
    public static final String RAWREPO_PROVIDER_ID_PH = "rawrepo.provider.id.ph";
    public static final String RAWREPO_PROVIDER_ID_PH_HOLDINGS = "rawrepo.provider.id.ph.holdings";
    public static final String RAWREPO_PROVIDER_ID_OVERRIDE = "rawrepo.provider.id.override";
    public static final String RAWREPO_PRIORITY_OVERRIDE = "rawrepo.priority.override";

    public static final String UPDATE_PROD_STATE_KEY = "prod.state";

    /**
     * Defines SRU constant for RecordSchema tag to accept marcXChange 1.1.
     */
    public static final String RECORD_SCHEMA_MARCXCHANGE_1_1 = "info:lc/xmlns/marcxchange-v1";

    /**
     * Defines SRU constant for RecordPacking tag to accept xml.
     */
    public static final String RECORD_PACKING_XML = "xml";

    /**
     * Defines MarcXchange v2 schema location.
     */
    public static final String MARCXCHANGE_1_1_SCHEMA_LOCATION = "http://www.loc.gov/standards/iso25577/marcxchange-1-1.xsd";

    public static final String PROP_OPENNUMBERROLL_URL = "opennumberroll.url";
    public static final String PROP_OPENNUMBERROLL_NAME_FAUST_8 = "opennumberroll.name.faust8";
    public static final String PROP_OPENNUMBERROLL_NAME_FAUST = "opennumberroll.name.faust";

    // List of required resources
    public static List<String> getListOfRequiredJNDIResources() {
        List<String> res = new ArrayList<>();
        return res;
    }
}
