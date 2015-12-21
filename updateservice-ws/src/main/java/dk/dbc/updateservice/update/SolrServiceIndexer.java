//-----------------------------------------------------------------------------
package dk.dbc.updateservice.update;

//-----------------------------------------------------------------------------
/**
 * This class has en responsibility to create query string to be used to make
 * lookup with the SolrService EJB.
 */
public class SolrServiceIndexer {
    public static String createSubfieldQuery( String fieldAndSubfield, String value ) {
        return String.format( SUBFIELD_QUERY, fieldAndSubfield, value );
    }

    //-------------------------------------------------------------------------
    //              Members
    //-------------------------------------------------------------------------

    private static String SUBFIELD_QUERY = "marc.%s=\"%s\"";
}
