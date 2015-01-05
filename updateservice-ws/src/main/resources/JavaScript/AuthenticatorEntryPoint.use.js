//-----------------------------------------------------------------------------
use( "DanMarc2Converter" );
use( "DBCAuthenticator" );
use( "FBSAuthenticator" );
use( "Marc" );
use( "MarcClasses" );
use( "Log" );

//-----------------------------------------------------------------------------
EXPORTED_SYMBOLS = [ 'AuthenticatorEntryPoint' ];

//-----------------------------------------------------------------------------
var AuthenticatorEntryPoint = function() {
    var authenticators = [
        DBCAuthenticator,
        FBSAuthenticator
    ];

    function authenticateRecord( record, userId, groupId ) {
        Log.trace( "Enter - AuthenticatorEntryPoint.authenticateRecord()" );

        try {
            var marc = DanMarc2Converter.convertToDanMarc2( JSON.parse( record ) );

            for( var i = 0; i < authenticators.length; i++ ) {
                var authenticator = authenticators[ i ];
                if( authenticator.canAuthenticate( marc, userId, groupId ) === true ) {
                    return JSON.stringify( authenticator.authenticateRecord( marc, userId, groupId ) );
                }
            }

            return JSON.stringify( [ ValidateErrors.recordError( "", "Der eksistere ikke en authenticator for denne post eller bruger." ) ] );
        }
        finally {
            Log.trace( "Exit - AuthenticatorEntryPoint.authenticateRecord()" );
        }
    }

    return {
        'authenticateRecord': authenticateRecord
    }
}();
