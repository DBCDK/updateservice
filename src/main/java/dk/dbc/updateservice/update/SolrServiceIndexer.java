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
    private static final String GET_SUBJECT_ID_QUERY = "%s:\"%s\" AND marc.001b:190004";

    private static final String GET_OWNER_OF_002_QUERY = "marc.%s:\"%s\" AND marc.001b:870970";

    private static final String SUBFIELD_QUERY = "marc.%s:\"%s\" AND marc.001b:870970";
    private static final String SUBFIELD_QUERY_WITH_EXCLUDE = "marc.%s:\"%s\" AND -marc.%s:\"%s\" AND marc.001b:870970";

    public static String createGetSubjectId(String validIndex, String value) {
        return String.format(GET_SUBJECT_ID_QUERY, validIndex, value);
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

}
