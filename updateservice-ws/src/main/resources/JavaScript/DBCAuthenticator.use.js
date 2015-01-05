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
        return [];
    }

    return {
        'canAuthenticate': canAuthenticate,
        'authenticateRecord': authenticateRecord
    }
}();
