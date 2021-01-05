/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Contains public accessible contants of all the JNDI resources, that is used
 * be all application.
 *
 * This also includes keys from Properties resources.
 */
public class JNDIResources {
    public static final String SOLR_URL = "SOLR_URL";
    public static final String SOLR_BASIS_URL = "SOLR_BASIS_URL";
    public static final String FORSRIGHTS_URL = "FORSRIGHTS_URL";
    public static final String AUTH_PRODUCT_NAME = "AUTH_PRODUCT_NAME";
    public static final String AUTH_USE_IP = "AUTH_USE_IP";

    public static final String RAWREPO_PROVIDER_ID_DBC = "RAWREPO_PROVIDER_ID_DBC";
    public static final String RAWREPO_PROVIDER_ID_FBS = "RAWREPO_PROVIDER_ID_FBS";
    public static final String RAWREPO_PROVIDER_ID_PH = "RAWREPO_PROVIDER_ID_PH";
    public static final String RAWREPO_PROVIDER_ID_PH_HOLDINGS = "RAWREPO_PROVIDER_ID_PH_HOLDINGS";
    public static final String RAWREPO_PROVIDER_ID_OVERRIDE = "RAWREPO_PROVIDER_ID_OVERRIDE";
    public static final String RAWREPO_PRIORITY_OVERRIDE = "RAWREPO_PRIORITY_OVERRIDE";

    public static final String UPDATE_PROD_STATE = "UPDATE_PROD_STATE";

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

    public static final String OPENNUMBERROLL_URL = "OPENNUMBERROLL_URL";
    public static final String OPENNUMBERROLL_NAME_FAUST_8 = "OPENNUMBERROLL_NAME_FAUST_8";
    public static final String OPENNUMBERROLL_NAME_FAUST = "OPENNUMBERROLL_NAME_FAUST";

    // List of required resources
    public static List<String> getListOfRequiredJNDIResources() {
        List<String> res = new ArrayList<>();
        return res;
    }

    public static Properties getProperties() {
        Properties properties = new Properties();

        for (String key: System.getenv().keySet()) {
            properties.setProperty(key, System.getenv(key));
        }

        return properties;
    }
}
