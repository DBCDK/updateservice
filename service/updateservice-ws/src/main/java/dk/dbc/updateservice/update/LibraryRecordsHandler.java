//-----------------------------------------------------------------------------
package dk.dbc.updateservice.update;

//-----------------------------------------------------------------------------
import dk.dbc.iscrum.records.MarcRecord;

//-----------------------------------------------------------------------------
/**
 * Class to manipulate library records for a local library. Local records and 
 * local extended records.
 * <p>
 * 
 * @author stp
 */
public class LibraryRecordsHandler {
    //-------------------------------------------------------------------------
    //              Constructors
    //-------------------------------------------------------------------------
    
    public LibraryRecordsHandler() {        
    }

    //-------------------------------------------------------------------------
    //              Library records
    //-------------------------------------------------------------------------

    /**
     * This method updates an extended library record based on the bibliographic 
     * classification elements of the record from DBC
     * 
     * @param dbcRecord     The record from DBC.
     * @param libraryRecord The library extended record.
     * 
     * @return Returns the library record after it has been updated. 
     *         <code>libraryRecord</code> may have changed.
     */
    public MarcRecord updateLibraryExtendedRecord( MarcRecord dbcRecord, MarcRecord libraryRecord ) {
        throw new UnsupportedOperationException( "Not supported yet." );
    }
    
}
