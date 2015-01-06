//-----------------------------------------------------------------------------
use( "Log" );

//-----------------------------------------------------------------------------
EXPORTED_SYMBOLS = [ 'DBCAuthenticator' ];

//-----------------------------------------------------------------------------
var DBCAuthenticator = function() {
    var AGENCY_IDS = [ "870970", "010100" ];

    function canAuthenticate( record, userId, groupId ) {
        return AGENCY_IDS.indexOf( groupId ) > -1;
    }

    function authenticateRecord( record, userId, groupId ) {
        Log.trace( "Enter - DBCAuthenticator.authenticateRecord()" );

        try {
            var recId = record.getValue(/001/, /a/);
            var agencyId = record.getValue( /001/, /b/ );

            if( !RawRepoClient.recordExists( recId, agencyId ) ) {
                return [];
            }

            var curRecord = RawRepoClient.fetchRecord( recId, agencyId );
            var curOwner = curRecord.getValue( /996/, /a/ );
            var newOwner = record.getValue( /996/, /a/ );
            if( curOwner !== newOwner ) {
                return [ValidateErrors.recordError("", StringUtil.sprintf("Brugeren '%s' m\u00E5 ikke \u00E6ndret v\u00E6rdien af felt 996a i posten '%s'", groupId, recId))];
            }

            return [];
        }
        finally {
            Log.trace( "Enter - DBCAuthenticator.authenticateRecord()" );

        }
    }

    return {
        'canAuthenticate': canAuthenticate,
        'authenticateRecord': authenticateRecord
    }
}();
