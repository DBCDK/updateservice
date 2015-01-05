//-----------------------------------------------------------------------------
use( "DBCAuthenticator" );
use( "UnitTest" );

//-----------------------------------------------------------------------------
UnitTest.addFixture( "DBCAuthenticator.canAuthenticate", function() {
    Assert.equalValue( "Found groupId", DBCAuthenticator.canAuthenticate( undefined, "netpunkt", "010100" ), true );
    Assert.equalValue( "groupId not found", DBCAuthenticator.canAuthenticate( undefined, "netpunkt", "700400" ), false );
} );

UnitTest.addFixture( "DBCAuthenticator.authenticateRecord", function() {
    Assert.equalValue( "No errors", DBCAuthenticator.authenticateRecord( undefined, "netpunkt", "010100" ), [] );
} );
