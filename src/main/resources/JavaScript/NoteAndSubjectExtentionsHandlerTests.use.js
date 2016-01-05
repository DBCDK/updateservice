//-----------------------------------------------------------------------------
use( "NoteAndSubjectExtentionsHandler" );
use( "RawRepoClientCore" );
use( "UnitTest" );

//-----------------------------------------------------------------------------
UnitTest.addFixture( "NoteAndSubjectExtentionsHandler.isNationalCommonRecord", function() {
    var record;

    record = new Record();

    record.fromString( "004 00 *a xxx" );
    Assert.equalValue( "Not 032", NoteAndSubjectExtentionsHandler.isNationalCommonRecord( record ), false );

    record.fromString( "032 00 *x xxx" );
    Assert.equalValue( "032: No *a", NoteAndSubjectExtentionsHandler.isNationalCommonRecord( record ), false );

    record.fromString( "032 00 *a xxx" );
    Assert.equalValue( "032: *a and no *x", NoteAndSubjectExtentionsHandler.isNationalCommonRecord( record ), true );

    record.fromString( "032 00 *a xxx *x BKM201417" );
    Assert.equalValue( "032: *a and *x BKM", NoteAndSubjectExtentionsHandler.isNationalCommonRecord( record ), false );

    record.fromString( "032 00 *a xxx *x NET201417" );
    Assert.equalValue( "032: *a and *x NET", NoteAndSubjectExtentionsHandler.isNationalCommonRecord( record ), false );

    record.fromString( "032 00 *a xxx *x SF201417" );
    Assert.equalValue( "032: *a and *x SF", NoteAndSubjectExtentionsHandler.isNationalCommonRecord( record ), false );
} );

UnitTest.addFixture( "NoteAndSubjectExtentionsHandler.authenticateExtentions", function() {
    var curRecord;
    var record;

    record = new Record();
    record.fromString(
        "001 00 *a 1 234 567 8 *b 700400\n" +
        "004 00 *a e *r n"
    );
    Assert.equalValue( "Not national common record",
                       NoteAndSubjectExtentionsHandler.authenticateExtentions( record, "700400" ), [] );

    record = new Record();
    record.fromString(
        "001 00 *a 1 234 567 8 *b 700400\n" +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "666 00 *0 *e emneord"
    );
    Assert.equalValue( "No existing record in rawrepo",
        NoteAndSubjectExtentionsHandler.authenticateExtentions( record, "700400" ), [] );

    curRecord = new Record();
    curRecord.fromString(
        "001 00 *a 1 234 567 8 *b 700400\n" +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "666 00 *0 *e emneord"
    );
    RawRepoClientCore.addRecord( curRecord );
    Assert.equalValue( "National common record with no changes",
                       NoteAndSubjectExtentionsHandler.authenticateExtentions( curRecord, "700400" ), [] );
    RawRepoClientCore.clear();

    curRecord = new Record();
    curRecord.fromString(
        "001 00 *a 1 234 567 8 *b 700400\n" +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "666 00 *0 *e emneord"
    );
    RawRepoClientCore.addRecord( curRecord );

    record = new Record();
    record.fromString(
        "001 00 *b 700400 *a 1 234 567 8 \n" +
        "004 00 *r n *a e\n" +
        "032 00 *a xxx *x\n" +
        "666 00 *0 *e emneord"
    );
    Assert.equalValue( "National common record with no changes, but with different order of subfields",
        NoteAndSubjectExtentionsHandler.authenticateExtentions( record, "700400" ), [] );
    RawRepoClientCore.clear();

    curRecord = new Record();
    curRecord.fromString(
        "001 00 *a 1 234 567 8 *b 700400\n" +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "666 00 *0 *e emneord"
    );
    RawRepoClientCore.addRecord( curRecord );

    record = new Record();
    record.fromString(
        "001 00 *a 1 234 567 8 *b 700400\n" +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "504 00 *a xxx\n" +
        "666 00 *0 *e emneord\n"
    );
    Assert.equalValue( "New extension field",
        NoteAndSubjectExtentionsHandler.authenticateExtentions( record, "700400" ), [] );
    RawRepoClientCore.clear();

    curRecord = new Record();
    curRecord.fromString(
        "001 00 *a 1 234 567 8 *b 700400\n" +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "504 00 *& 751000 *a xxx\n"
    );
    RawRepoClientCore.addRecord( curRecord );

    record = new Record();
    record.fromString(
        "001 00 *a 1 234 567 8 *b 700400\n" +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "504 00 *a yyy\n"
    );
    Assert.equalValue( "Edit extension field",
                       NoteAndSubjectExtentionsHandler.authenticateExtentions( record, "700400" ), [] );
    RawRepoClientCore.clear();

    curRecord = new Record();
    curRecord.fromString(
        "001 00 *a 1 234 567 8 *b 700400\n" +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "504 00 *& 751000 *a xxx\n"
    );
    RawRepoClientCore.addRecord( curRecord );

    record = new Record();
    record.fromString(
        "001 00 *a 1 234 567 8 *b 700400\n" +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n"
    );
    Assert.equalValue( "Delete extension field",
        NoteAndSubjectExtentionsHandler.authenticateExtentions( record, "700400" ), [] );
    RawRepoClientCore.clear();

    curRecord = new Record();
    curRecord.fromString(
        "001 00 *a 1 234 567 8 *b 700400\n" +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "504 00 *& 751000 *a xxx\n"
    );
    RawRepoClientCore.addRecord( curRecord );

    record = new Record();
    record.fromString(
        "001 00 *a 1 234 567 8 *b 700400\n" +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "300 00 *a xxx *x\n" +
        "504 00 *a xxx\n"
    );
    Assert.equalValue( "New non extension field",
                       NoteAndSubjectExtentionsHandler.authenticateExtentions( record, "700400" ),
                       [ ValidateErrors.recordError( "", "Brugeren '700400' har ikke ret til at rette/tilføje feltet '300' i posten '1 234 567 8'" ) ] );
    RawRepoClientCore.clear();

    curRecord = new Record();
    curRecord.fromString(
        "001 00 *a 1 234 567 8 *b 700400\n" +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "300 00 *a xxx *x\n" +
        "504 00 *& 751000 *a xxx\n"
    );
    RawRepoClientCore.addRecord( curRecord );

    record = new Record();
    record.fromString(
        "001 00 *a 1 234 567 8 *b 700400\n" +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "300 00 *a yyy *x\n" +
        "504 00 *a xxx\n"
    );
    Assert.equalValue( "Edit non extension field",
                       NoteAndSubjectExtentionsHandler.authenticateExtentions( record, "700400" ),
                       [ ValidateErrors.recordError( "", "Brugeren '700400' har ikke ret til at rette/tilføje feltet '300' i posten '1 234 567 8'" ) ] );
    RawRepoClientCore.clear();

    curRecord = new Record();
    curRecord.fromString(
        "001 00 *a 1 234 567 8 *b 700400\n" +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "300 00 *a xxx *x\n" +
        "504 00 *& 751000 *a xxx\n"
    );
    RawRepoClientCore.addRecord( curRecord );

    record = new Record();
    record.fromString(
        "001 00 *a 1 234 567 8 *b 700400\n" +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "504 00 *a xxx\n"
    );
    Assert.equalValue( "Delete non extension field",
                       NoteAndSubjectExtentionsHandler.authenticateExtentions( record, "700400" ),
                       [ ValidateErrors.recordError( "", "Brugeren '700400' har ikke ret til at slette feltet '300' i posten '1 234 567 8'" ) ] );
    RawRepoClientCore.clear();
} );

UnitTest.addFixture( "NoteAndSubjectExtentionsHandler.updateExtentions", function() {
    var curRecord;
    var record;

    record = new Record();
    record.fromString(
        "001 00 *a 1 234 567 8 *b 700400\n" +
        "004 00 *a e *r n"
    );
    Assert.equalValue( "Not national common record",
        NoteAndSubjectExtentionsHandler.changeUpdateRecordForUpdate( record, "700400" ), record );

    record = new Record();
    record.fromString(
        "001 00 *a 1 234 567 8 *b 700400\n" +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "666 00 *0 *e emneord"
    );
    Assert.equalValue( "No existing record in rawrepo",
        NoteAndSubjectExtentionsHandler.changeUpdateRecordForUpdate( record, "700400" ), record );

    curRecord = new Record();
    curRecord.fromString(
        "001 00 *a 1 234 567 8 *b 700400\n" +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "666 00 *0 *e emneord"
    );
    RawRepoClientCore.addRecord( curRecord );

    record = new Record();
    record.fromString(
        "001 00 *a 1 234 567 8 *b 700400\n" +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "666 00 *& 700400 *0 *e emneord"
    );
    Assert.equalValue( "National common record with no changes",
        NoteAndSubjectExtentionsHandler.changeUpdateRecordForUpdate( curRecord, "700400" ), record );
    RawRepoClientCore.clear();
} );