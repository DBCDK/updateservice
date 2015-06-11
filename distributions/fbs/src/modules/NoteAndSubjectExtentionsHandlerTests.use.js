//-----------------------------------------------------------------------------
use( "FBSAuthenticator" );
use( "NoteAndSubjectExtentionsHandler" );
use( "RawRepoClientCore" );
use( "ResourceBundle" );
use( "ResourceBundleFactory" );
use( "UnitTest" );

//-----------------------------------------------------------------------------
UnitTest.addFixture( "NoteAndSubjectExtentionsHandler.isNationalCommonRecord", function() {
    var record;

    record = new Record();

    record.fromString(
        "004 00 *a xxx\n" +
        "996 00 *a DBC"
    );
    Assert.equalValue( "Not 032", NoteAndSubjectExtentionsHandler.isNationalCommonRecord( record ), false );

    record.fromString(
        "032 00 *x xxx\n" +
        "996 00 *a DBC"
    );
    Assert.equalValue( "032: No *a", NoteAndSubjectExtentionsHandler.isNationalCommonRecord( record ), false );

    record.fromString(
        "032 00 *a xxx\n" +
        "996 00 *a DBC"
    );
    Assert.equalValue( "032: *a and no *x", NoteAndSubjectExtentionsHandler.isNationalCommonRecord( record ), true );

    record.fromString(
        "032 00 *a xxx *x BKM201417\n" +
        "996 00 *a DBC"
    );
    Assert.equalValue( "032: *a and *x BKM", NoteAndSubjectExtentionsHandler.isNationalCommonRecord( record ), false );

    record.fromString(
        "032 00 *a xxx *x NET201417\n" +
        "996 00 *a DBC"
    );
    Assert.equalValue( "032: *a and *x NET", NoteAndSubjectExtentionsHandler.isNationalCommonRecord( record ), false );

    record.fromString(
        "032 00 *a xxx *x SF201417\n" +
        "996 00 *a DBC"
    );
    Assert.equalValue( "032: *a and *x SF", NoteAndSubjectExtentionsHandler.isNationalCommonRecord( record ), false );
} );

UnitTest.addFixture( "NoteAndSubjectExtentionsHandler.authenticateExtentions", function() {
    var bundle = ResourceBundleFactory.getBundle( FBSAuthenticator.__BUNDLE_NAME );
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
        StringUtil.sprintf( "001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.COMMON_AGENCYID ) +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "666 00 *0 *e emneord\n" +
        "996 00 *a DBC"
    );
    Assert.equalValue( "No existing record in rawrepo",
        NoteAndSubjectExtentionsHandler.authenticateExtentions( record, "700400" ), [] );

    curRecord = new Record();
    curRecord.fromString(
        StringUtil.sprintf( "001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.RAWREPO_COMMON_AGENCYID ) +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "666 00 *0 *e emneord\n" +
        "996 00 *a DBC"
    );
    RawRepoClientCore.addRecord( curRecord );
    Assert.equalValue( "National common record with no changes",
                       NoteAndSubjectExtentionsHandler.authenticateExtentions( curRecord, "700400" ), [] );
    RawRepoClientCore.clear();

    curRecord = new Record();
    curRecord.fromString(
        StringUtil.sprintf( "001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.RAWREPO_COMMON_AGENCYID ) +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "666 00 *0 *e emneord\n" +
        "996 00 *a DBC"
    );
    RawRepoClientCore.addRecord( curRecord );

    record = new Record();
    record.fromString(
        StringUtil.sprintf( "001 00 *b %s *a 1 234 567 8 \n", UpdateConstants.COMMON_AGENCYID ) +
        "004 00 *r n *a e\n" +
        "032 00 *a xxx *x\n" +
        "666 00 *0 *e emneord\n" +
        "996 00 *a DBC"
    );
    Assert.equalValue( "National common record with no changes, but with different order of subfields",
        NoteAndSubjectExtentionsHandler.authenticateExtentions( record, "700400" ), [] );
    RawRepoClientCore.clear();

    curRecord = new Record();
    curRecord.fromString(
        StringUtil.sprintf( "001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.RAWREPO_COMMON_AGENCYID ) +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "666 00 *0 *e emneord\n" +
        "996 00 *a DBC"
    );
    RawRepoClientCore.addRecord( curRecord );

    record = new Record();
    record.fromString(
        StringUtil.sprintf( "001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.COMMON_AGENCYID ) +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "504 00 *a xxx\n" +
        "666 00 *0 *e emneord\n" +
        "996 00 *a DBC"
    );
    Assert.equalValue( "New extension field",
        NoteAndSubjectExtentionsHandler.authenticateExtentions( record, "700400" ), [] );
    RawRepoClientCore.clear();

    curRecord = new Record();
    curRecord.fromString(
        StringUtil.sprintf( "001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.RAWREPO_COMMON_AGENCYID ) +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "504 00 *& 751000 *a xxx\n" +
        "996 00 *a DBC"
    );
    RawRepoClientCore.addRecord( curRecord );

    record = new Record();
    record.fromString(
        StringUtil.sprintf( "001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.COMMON_AGENCYID ) +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "504 00 *a yyy\n" +
        "996 00 *a DBC"
    );
    Assert.equalValue( "Edit extension field",
                       NoteAndSubjectExtentionsHandler.authenticateExtentions( record, "700400" ), [] );
    RawRepoClientCore.clear();

    curRecord = new Record();
    curRecord.fromString(
        StringUtil.sprintf( "001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.RAWREPO_COMMON_AGENCYID ) +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "504 00 *& 751000 *a xxx\n" +
        "996 00 *a DBC"
    );
    RawRepoClientCore.addRecord( curRecord );

    record = new Record();
    record.fromString(
        StringUtil.sprintf( "001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.COMMON_AGENCYID ) +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "996 00 *a DBC"
    );
    Assert.equalValue( "Delete extension field",
        NoteAndSubjectExtentionsHandler.authenticateExtentions( record, "700400" ), [] );
    RawRepoClientCore.clear();

    curRecord = new Record();
    curRecord.fromString(
        StringUtil.sprintf( "001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.RAWREPO_COMMON_AGENCYID ) +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "504 00 *& 751000 *a xxx\n" +
        "996 00 *a DBC"
    );
    RawRepoClientCore.addRecord( curRecord );

    record = new Record();
    record.fromString(
        StringUtil.sprintf( "001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.COMMON_AGENCYID ) +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "300 00 *a xxx *x\n" +
        "504 00 *a xxx\n" +
        "996 00 *a DBC"
    );
    Assert.equalValue( "New non extension field",
                       NoteAndSubjectExtentionsHandler.authenticateExtentions( record, "700400" ),
                       [ ValidateErrors.recordError( "", ResourceBundle.getStringFormat( bundle, "notes.subjects.edit.field.error", "700400", "300", "1 234 567 8" ) ) ] );
    RawRepoClientCore.clear();

    curRecord = new Record();
    curRecord.fromString(
        StringUtil.sprintf( "001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.RAWREPO_COMMON_AGENCYID ) +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "300 00 *a xxx *x\n" +
        "504 00 *& 751000 *a xxx\n" +
        "996 00 *a DBC"
    );
    RawRepoClientCore.addRecord( curRecord );

    record = new Record();
    record.fromString(
        StringUtil.sprintf( "001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.COMMON_AGENCYID ) +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "300 00 *a yyy *x\n" +
        "504 00 *a xxx\n" +
        "996 00 *a DBC"
    );
    Assert.equalValue( "Edit non extension field",
                       NoteAndSubjectExtentionsHandler.authenticateExtentions( record, "700400" ),
                       [ ValidateErrors.recordError( "", ResourceBundle.getStringFormat( bundle, "notes.subjects.edit.field.error", "700400", "300", "1 234 567 8" ) ) ] );
    RawRepoClientCore.clear();

    curRecord = new Record();
    curRecord.fromString(
        StringUtil.sprintf( "001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.RAWREPO_COMMON_AGENCYID ) +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "300 00 *a xxx *x\n" +
        "504 00 *& 751000 *a xxx\n" +
        "996 00 *a DBC"
    );
    RawRepoClientCore.addRecord( curRecord );

    record = new Record();
    record.fromString(
        StringUtil.sprintf( "001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.COMMON_AGENCYID ) +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "504 00 *a xxx\n" +
        "996 00 *a DBC"
    );
    Assert.equalValue( "Delete non extension field",
                       NoteAndSubjectExtentionsHandler.authenticateExtentions( record, "700400" ),
                       [ ValidateErrors.recordError( "", ResourceBundle.getStringFormat( bundle, "notes.subjects.delete.field.error", "700400", "300", "1 234 567 8" ) ) ] );
    RawRepoClientCore.clear();
} );

UnitTest.addFixture( "NoteAndSubjectExtentionsHandler.recordDataForRawRepo", function() {
    var curRecord;
    var record;
    var expected;

    record = new Record();
    record.fromString(
        StringUtil.sprintf( "001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.COMMON_AGENCYID ) +
        "004 00 *a e *r n"
    );
    Assert.equalValue( "Not national common record",
        NoteAndSubjectExtentionsHandler.recordDataForRawRepo( record, "netpunkt", "700400").toString(), record.toString() );

    record = new Record();
    record.fromString(
        StringUtil.sprintf( "001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.COMMON_AGENCYID ) +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "666 00 *0 *e emneord" +
        "996 00 *a DBC"
    );
    Assert.equalValue( "No existing record in rawrepo",
        NoteAndSubjectExtentionsHandler.recordDataForRawRepo( record, "netpunkt", "700400" ).toString(), record.toString() );

    curRecord = new Record();
    curRecord.fromString(
        StringUtil.sprintf( "001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.RAWREPO_COMMON_AGENCYID ) +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "666 00 *0 *e emneord\n" +
        "996 00 *a DBC"
    );
    RawRepoClientCore.addRecord( curRecord );

    record = new Record();
    record.fromString(
        StringUtil.sprintf( "001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.RAWREPO_COMMON_AGENCYID ) +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "666 00 *0 *e emneord\n" +
        "996 00 *a DBC"
    );
    Assert.equalValue( "National common record with no changes",
        NoteAndSubjectExtentionsHandler.recordDataForRawRepo( curRecord, "netpunkt", "700400" ).toString(), record.toString() );
    RawRepoClientCore.clear();

    curRecord = new Record();
    curRecord.fromString(
        StringUtil.sprintf( "001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.RAWREPO_COMMON_AGENCYID ) +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "666 00 *0 *e emneord 1\n" +
        "996 00 *a DBC"
    );
    RawRepoClientCore.addRecord( curRecord );

    record = new Record();
    record.fromString(
        StringUtil.sprintf( "001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.RAWREPO_COMMON_AGENCYID ) +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "666 00 *0 *e emneord 1\n" +
        "666 00 *0 *e emneord 2\n" +
        "996 00 *a DBC"
    );

    expected = new Record();
    expected.fromString(
        StringUtil.sprintf( "001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.RAWREPO_COMMON_AGENCYID ) +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "666 00 *0 *e emneord 1\n" +
        "666 00 *& 700400 *0 *e emneord 2\n" +
        "996 00 *a DBC"
    );
    Assert.equalValue( "National common record with new subject extention",
        NoteAndSubjectExtentionsHandler.recordDataForRawRepo( record, "netpunkt", "700400" ).toString(), expected.toString() );
    RawRepoClientCore.clear();

    curRecord = new Record();
    curRecord.fromString(
        StringUtil.sprintf( "001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.RAWREPO_COMMON_AGENCYID ) +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "666 00 *& 700100 *0 *e emneord 1\n" +
        "996 00 *a DBC"
    );
    RawRepoClientCore.addRecord( curRecord );

    record = new Record();
    record.fromString(
        StringUtil.sprintf( "001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.RAWREPO_COMMON_AGENCYID ) +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "666 00 *& 700100 *0 *e emneord 1\n" +
        "666 00 *0 *e emneord 2\n" +
        "996 00 *a DBC"
    );

    expected = new Record();
    expected.fromString(
        StringUtil.sprintf( "001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.RAWREPO_COMMON_AGENCYID ) +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "666 00 *& 700100 *0 *e emneord 1\n" +
        "666 00 *& 700400 *0 *e emneord 2\n" +
        "996 00 *a DBC"
    );
    Assert.equalValue( "National common record with new subject extention with existing extentions",
        NoteAndSubjectExtentionsHandler.recordDataForRawRepo( record, "netpunkt", "700400" ).toString(), expected.toString() );
    RawRepoClientCore.clear();

    curRecord = new Record();
    curRecord.fromString(
        StringUtil.sprintf( "001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.RAWREPO_COMMON_AGENCYID ) +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "666 00 *0 *e emneord 1\n" +
        "666 00 *& 700400 *0 *e emneord 2\n" +
        "996 00 *a DBC"
    );
    RawRepoClientCore.addRecord( curRecord );

    record = new Record();
    record.fromString(
        StringUtil.sprintf( "001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.RAWREPO_COMMON_AGENCYID ) +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "666 00 *0 *e emneord 1\n" +
        "666 00 *& 700400 *0 *e emneord 3\n" +
        "996 00 *a DBC"
    );

    expected = new Record();
    expected.fromString(
        StringUtil.sprintf( "001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.RAWREPO_COMMON_AGENCYID ) +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "666 00 *0 *e emneord 1\n" +
        "666 00 *& 700400 *0 *e emneord 3\n" +
        "996 00 *a DBC"
    );
    Assert.equalValue( "National common record with edit subject extention for same library",
        NoteAndSubjectExtentionsHandler.recordDataForRawRepo( record, "netpunkt", "700400" ).toString(), expected.toString() );
    RawRepoClientCore.clear();

    curRecord = new Record();
    curRecord.fromString(
        StringUtil.sprintf( "001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.RAWREPO_COMMON_AGENCYID ) +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "666 00 *0 *e emneord 1\n" +
        "666 00 *& 700800 *0 *e emneord 2\n" +
        "996 00 *a DBC"
    );
    RawRepoClientCore.addRecord( curRecord );

    record = new Record();
    record.fromString(
        StringUtil.sprintf( "001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.RAWREPO_COMMON_AGENCYID ) +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "666 00 *0 *e emneord 1\n" +
        "666 00 *& 700800 *0 *e emneord 3\n" +
        "996 00 *a DBC"
    );

    expected = new Record();
    expected.fromString(
        StringUtil.sprintf( "001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.RAWREPO_COMMON_AGENCYID ) +
        "004 00 *a e *r n\n" +
        "032 00 *a xxx *x\n" +
        "666 00 *0 *e emneord 1\n" +
        "666 00 *& 700400 *0 *e emneord 3\n" +
        "996 00 *a DBC"
    );
    Assert.equalValue( "National common record with edit subject extention for other library",
        NoteAndSubjectExtentionsHandler.recordDataForRawRepo( record, "netpunkt", "700400").toString(), expected.toString() );
    RawRepoClientCore.clear();
} );
