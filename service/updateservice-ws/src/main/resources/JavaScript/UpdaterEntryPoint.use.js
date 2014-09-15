//-----------------------------------------------------------------------------
use( "ClassificationData" );
use( "Log" );
use( "Marc" );
use( "MarcFactory" );

//-----------------------------------------------------------------------------
EXPORTED_SYMBOLS = [ 'UpdaterEntryPoint' ];

//-----------------------------------------------------------------------------
var UpdaterEntryPoint = function() {
    /**
     * Checks if a record contains any classification data
     * 
     * @param {String} jsonMarc The record as a json.
     * 
     * @return {Boolean} true if classification data exists in the record, false otherwise.
     */
    function hasClassificationData( jsonMarc ) {
        var marc = MarcFactory.createRecordFromJson( jsonMarc );
        
        return ClassificationData.hasClassificationData( marc );
    }
    
    /**
     * Checks if the classifications has changed between two records.
     * 
     * @param {String} oldRecord The old record as a json.
     * @param {String} newRecord The new record as a json.
     * 
     * @return {Boolean} true if the classifications has changed, false otherwise.
     */
    function hasClassificationsChanged( oldRecord, newRecord ) {
        var oldMarc = MarcFactory.createRecordFromJson( oldRecord );
        var newMarc = MarcFactory.createRecordFromJson( newRecord );

        return ClassificationData.hasClassificationsChanged( oldMarc, newMarc );    
    }

    /**
     * Creates a new library extended record based on a DBC record.
     * 
     * @param {String} dbcRecord The DBC record as a json.
     * @param {int}    libraryId Library id for the local library.
     * 
     * @return {String} A json with the new record.
     */
    function createLibraryExtendedRecord( dbcRecord, libraryId ) {
        var dbcMarc = MarcFactory.createRecordFromJson( dbcRecord );
        var result = new Record;

        var curDate = new Date();
        var curDateStr = curDate.getFullYear().toString() + 
                         curDate.getMonth().toString() + 
                         curDate.getDay().toString();
        var curTimeStr = curDate.getHours().toString() + 
                         curDate.getMinutes().toString() + 
                         curDate.getSeconds().toString();

        var idField = new Field( "001", "00" );
        idField.append( new Subfield( "a", dbcMarc.getValue( /001/, /a/ ) ) );
        idField.append( new Subfield( "b", libraryId.toString() ) );
        idField.append( new Subfield( "c", curDateStr + curTimeStr ) );
        idField.append( new Subfield( "d", curDateStr ) );
        idField.append( new Subfield( "f", "a" ) );
        result.append( idField );

        return updateLibraryExtendedRecord( dbcRecord, MarcFactory.createJsonFromRecord( result ) );
    }
    
    /**
     * Updates a library extended record with the classifications from 
     * a DBC record.
     * 
     * @param {String} dbcRecord The DBC record as a json.
     * @param {String} libraryRecord The library record to update as a json.
     * 
     * @return {String} A json with the updated record.
     */
    function updateLibraryExtendedRecord( dbcRecord, libraryRecord ) {
        var dbcMarc = MarcFactory.createRecordFromJson( dbcRecord );
        var libraryMarc = MarcFactory.createRecordFromJson( libraryRecord );

        return MarcFactory.createJsonFromRecord( ClassificationData.updateClassificationsInRecord( dbcMarc, libraryMarc ) );
    }
     
    function correctLibraryExtendedRecord( dbcRecord, libraryRecord ) {
        Log.info( "Enter - ClassificationData.__hasFieldChanged()" );
        var dbcMarc = MarcFactory.createRecordFromJson( dbcRecord );
        var libraryMarc = MarcFactory.createRecordFromJson( libraryRecord );

        Log.info( "    dbcMarc: " + dbcMarc );
        Log.info( "    libraryMarc: " + libraryMarc );

        if( ClassificationData.hasClassificationData( dbcMarc ) ) {
            if( !ClassificationData.hasClassificationsChanged( dbcMarc, libraryMarc ) ) {
                Log.info( "Classifications is the same. Removing it from library record." );
                libraryMarc = ClassificationData.removeClassificationsFromRecord( libraryMarc );
            }
            else {
                Log.info( "Classifications has changed." );                
            }
        }
        else {
            Log.info( "Common record has no classifications." );            
        }
        
        if( libraryMarc.size() === 1 && libraryMarc.field( 0 ).name === "001" ) {
            libraryMarc = new Record;
        }

        Log.info( "Exit - ClassificationData.correctLibraryExtendedRecord(): " + libraryMarc );
        return MarcFactory.createJsonFromRecord( libraryMarc );
    }
    
    return {
        'hasClassificationData': hasClassificationData,
        'hasClassificationsChanged': hasClassificationsChanged,
        'createLibraryExtendedRecord': createLibraryExtendedRecord,
        'updateLibraryExtendedRecord': updateLibraryExtendedRecord,
        'correctLibraryExtendedRecord': correctLibraryExtendedRecord
    };

}();
