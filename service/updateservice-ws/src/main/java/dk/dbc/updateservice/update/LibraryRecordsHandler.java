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
     * Tests if the classifications has changed between 2 records.
     * <p>
     * This method is mainly used to checks for changes between 2 versions of
     * the same record.
     * 
     * @param oldRecord The old record.
     * @param newRecord The new record.
     * 
     * @return <code>true</code> if there is changes in the classifications, 
     *         <code>false</code> otherwise.
     */
    public boolean hasClassificationsChanged( MarcRecord oldRecord, MarcRecord newRecord ) {
        throw new UnsupportedOperationException( "LibraryRecordsHandler.hasClassificationsChanged is not supported yet." );
    }
    
    /**
     * Creates an extended library record based on the bibliographic 
     * classification elements of the record from DBC
     * 
     * @param dbcRecord The record from DBC.
     * @param libraryId The library id for the library, that the extended 
     *                  record will be created for.
     * 
     * @return Returns the library record after it has been updated. 
     *         <code>libraryRecord</code> may have changed.
     */
    public MarcRecord createLibraryExtendedRecord( MarcRecord dbcRecord, int libraryId ) {
        throw new UnsupportedOperationException( "LibraryRecordsHandler.updateLibraryExtendedRecord is not supported yet." );
    }
    
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
        throw new UnsupportedOperationException( "LibraryRecordsHandler.updateLibraryExtendedRecord is not supported yet." );
    }
}
