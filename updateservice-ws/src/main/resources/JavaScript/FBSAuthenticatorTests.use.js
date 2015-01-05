//-----------------------------------------------------------------------------
use( "FBSAuthenticator" );
use( "UnitTest" );

//-----------------------------------------------------------------------------
UnitTest.addFixture( "FBSAuthenticator.canAuthenticate", function() {
    Assert.equalValue( "Found groupId", FBSAuthenticator.canAuthenticate( undefined, "netpunkt", "700400" ), true );
    Assert.equalValue( "groupId not found", FBSAuthenticator.canAuthenticate( undefined, "netpunkt", "870970" ), false );
} );

UnitTest.addFixture( "FBSAuthenticator.authenticateRecord", function() {
    var record;

    record = new Record();
    record.fromString(
        "001 00 *a 1 234 567 8 *b 700400\n" +
        "004 00 *a e *r n"
    );
    Assert.equalValue( "No errors", FBSAuthenticator.authenticateRecord( record, "netpunkt", "700400" ), [] );

    record = new Record();
    record.fromString(
        "001 00 *a 1 234 567 8 *b 870970\n" +
        "004 00 *a e *r n"
    );
    Assert.equalValue( "Wrong agency id", FBSAuthenticator.authenticateRecord( record, "netpunkt", "700400" ),
        [ ValidateErrors.recordError( "", "Brugeren '700400' har ikke ret til at opdatere posten '1 234 567 8'" ) ] );
} );
