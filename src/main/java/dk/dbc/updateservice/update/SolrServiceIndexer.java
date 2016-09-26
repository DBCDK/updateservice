package dk.dbc.updateservice.update;

/**
 * This class has en responsibility to create query string to be used to make
 * lookup with the SolrService EJB.
 */
public class SolrServiceIndexer {
    private static String SUBFIELD_QUERY = "marc.%s:\"%s\" AND marc.001b:870970";
    private static String SUBFIELD_QUERY_DUAL = "marc.%s:\"%s\" AND marc.%s:\"%s\" AND marc.001b:870970";


    public static String createSubfieldQueryDBCOnly(String fieldAndSubfield, String value) {
        return String.format(SUBFIELD_QUERY, fieldAndSubfield, value);
    }

    public static String createSubfieldQueryDualDBCOnly(String fieldAndSubfield1, String value1, String fieldAndSubfield2, String value2) {
        return String.format(SUBFIELD_QUERY_DUAL, fieldAndSubfield1, value1, fieldAndSubfield2, value2);
    }
}
