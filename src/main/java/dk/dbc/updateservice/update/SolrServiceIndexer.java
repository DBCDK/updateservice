/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

/**
 * This class has en responsibility to create query string to be used to make
 * lookup with the SolrService EJB.
 */
public class SolrServiceIndexer {
    private static String GET_SUBJECT_HITS_QUERY = "%s:\"%s\" AND marc.001b:190004";

    private static String GET_OWNER_OF_002_QUERY = "marc.%s:\"%s\" AND marc.001b:870970";

    private static String SUBFIELD_QUERY = "marc.%s:\"%s\" AND marc.001b:870970";
    private static String SUBFIELD_QUERY_WITH_EXCLUDE = "marc.%s:\"%s\" AND -marc.%s:\"%s\" AND marc.001b:870970";

    private static String SUBFIELD_QUERY_DUAL = "marc.%s:\"%s\" AND marc.%s:\"%s\" AND marc.001b:870970";
    private static String SUBFIELD_QUERY_DUAL_WITH_EXCLUDE = "marc.%s:\"%s\" AND marc.%s:\"%s\" AND -marc.%s:\"%s\" AND marc.001b:870970";

    public static String createGetSubjectHits(String validIndex, String value) {
        return String.format(GET_SUBJECT_HITS_QUERY, validIndex, value);
    }

    public static String createGetOwnerOf002QueryDBCOnly(String fieldAndSubfield, String value) {
        return String.format(GET_OWNER_OF_002_QUERY, fieldAndSubfield, value);
    }

    public static String createSubfieldQueryDBCOnly(String fieldAndSubfield, String value) {
        return String.format(SUBFIELD_QUERY, fieldAndSubfield, value);
    }

    public static String createSubfieldQueryWithExcludeDBCOnly(String fieldAndSubfield, String value, String fieldAndSubfieldExclude, String valueExclude) {
        return String.format(SUBFIELD_QUERY_WITH_EXCLUDE, fieldAndSubfield, value, fieldAndSubfieldExclude, valueExclude);
    }

    public static String createSubfieldQueryDualDBCOnly(String fieldAndSubfield1, String value1, String fieldAndSubfield2, String value2) {
        return String.format(SUBFIELD_QUERY_DUAL, fieldAndSubfield1, value1, fieldAndSubfield2, value2);
    }

    public static String createSubfieldQueryDualWithExcludeDBCOnly(String fieldAndSubfield1, String value1, String fieldAndSubfield2, String value2, String fieldAndSubfieldExclude, String valueExclude) {
        return String.format(SUBFIELD_QUERY_DUAL_WITH_EXCLUDE, fieldAndSubfield1, value1, fieldAndSubfield2, value2, fieldAndSubfieldExclude, valueExclude);
    }
}
