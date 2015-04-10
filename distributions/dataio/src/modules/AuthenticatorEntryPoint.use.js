//-----------------------------------------------------------------------------
use( "BasisSplitter" );
use( "DanMarc2Converter" );
use( "Log" );
use( "Marc" );
use( "MarcClasses" );
use( "UpdateConstants" );

//-----------------------------------------------------------------------------
EXPORTED_SYMBOLS = [ 'AuthenticatorEntryPoint' ];

//-----------------------------------------------------------------------------
/**
 * Module to contain entry points for the authenticator API between Java and
 * JavaScript.
 *
 * @namespace
 * @name AuthenticatorEntryPoint
 */
var AuthenticatorEntryPoint = function() {
    /**
     * Authenticates a record.
     *
     * @param record Record
     * @param userId User id - not used.
     * @param groupId Group id.
     *
     * @returns {Array} Array of authentication errors. We use the same structure
     *                  as for validation errors.
     *
     * @name AuthenticatorEntryPoint#authenticateRecord
     */
    function authenticateRecord( record, userId, groupId ) {
        Log.trace( "Enter - AuthenticatorEntryPoint.authenticateRecord()" );

        try {
            if( UpdateConstants.DBC_AGENCY_IDS.indexOf( groupId ) == -1 ) {
                Log.warn( "Unknown record/user." );
                Log.warn( "User/group: ", userId, " / ", groupId );
                Log.warn( "Posten:\n", record );

                return JSON.stringify( [ ValidateErrors.recordError( "", "Ukendt post eller bruger." ) ] );
            }

            return JSON.stringify( [] );
        }
        finally {
            Log.trace( "Exit - AuthenticatorEntryPoint.authenticateRecord()" );
        }
    }

    /**
     * Converts a record to the actual records that should be stored in the RawRepo.
     *
     * @param {Record} record The record.
     *
     * @returns {Array} A list of records of type Record.
     */
    function recordDataForRawRepo( record, userId, groupId ) {
        Log.trace( "Enter - AuthenticatorEntryPoint.recordDataForRawRepo()" );

        try {
            if( UpdateConstants.DBC_AGENCY_IDS.indexOf( groupId ) == -1 ) {
                Log.warn( "Unknown record/user." );
                Log.warn( "User/group: ", userId, " / ", groupId );
                Log.warn( "Posten:\n", record );
                return [ record ];
            }

            return BasisSplitter.splitCompleteBasisRecord( record );
        }
        finally {
            Log.trace( "Exit - AuthenticatorEntryPoint.recordDataForRawRepo()" );
        }
    }

    return {
        'authenticateRecord': authenticateRecord,
        'recordDataForRawRepo': recordDataForRawRepo
    }
}();
