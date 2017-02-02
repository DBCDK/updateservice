use("Authenticator");
use("RawRepoClientCore");
use("UnitTest");

UnitTest.addFixture("Authenticator.authenticateRecord", function () {
    var LOGIN_AGENCY_ID = "010100";

    var curRecord;
    var record;

    function callFunction(record, userId, groupId, settings) {

        var recordObject = DanMarc2Converter.convertFromDanMarc2(record);
        var recordJson = JSON.stringify(recordObject);
        var result = Authenticator.authenticateRecord(recordJson, userId, groupId, settings);

        return JSON.parse(result);

    }

    //-----------------------------------------------------------------------------
    //                  Test new records
    //-----------------------------------------------------------------------------

    OpenAgencyClientCore.addFeatures(LOGIN_AGENCY_ID, [UpdateConstants.AUTH_ROOT_FEATURE]);

    record = new Record();
    record.fromString(
        StringUtil.sprintf("001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.COMMON_AGENCYID) +
        "004 00 *a e *r n"
    );
    Assert.equalValue("New record without authentication",
        callFunction(record, "netpunkt", LOGIN_AGENCY_ID), []);

    record = new Record();
    record.fromString(
        StringUtil.sprintf("001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.COMMON_AGENCYID) +
        "004 00 *a e *r n\n" +
        StringUtil.sprintf("996 00 *a %s", UpdateConstants.COMMON_AGENCYID)
    );
    Assert.equalValue("New record with 996",
        callFunction(record, "netpunkt", LOGIN_AGENCY_ID),
        []);

    record = new Record();
    record.fromString(
        StringUtil.sprintf("001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.COMMON_AGENCYID) +
        "004 00 *a e *r n\n" +
        StringUtil.sprintf("996 00 *a %s", UpdateConstants.COMMON_AGENCYID)
    );
    Assert.equalValue("New record with 996",
        callFunction(record, "netpunkt", LOGIN_AGENCY_ID),
        []);

    //-----------------------------------------------------------------------------
    //                  Test update DBC records
    //-----------------------------------------------------------------------------

    curRecord = new Record();
    curRecord.fromString(
        StringUtil.sprintf("001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.COMMON_AGENCYID) +
        "004 00 *a e *r n\n"
    );
    RawRepoClientCore.addRecord(curRecord);

    record = new Record();
    record.fromString(
        StringUtil.sprintf("001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.COMMON_AGENCYID) +
        "004 00 *a e *r n"
    );
    Assert.equalValue("Update record without authentication in current or new record",
        callFunction(record, "netpunkt", LOGIN_AGENCY_ID), []);
    RawRepoClientCore.clear();

    curRecord = new Record();
    curRecord.fromString(
        StringUtil.sprintf("001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.COMMON_AGENCYID) +
        "004 00 *a e *r n\n" +
        StringUtil.sprintf("996 00 *a %s", UpdateConstants.COMMON_AGENCYID)
    );
    RawRepoClientCore.addRecord(curRecord);
    curRecord = new Record();
    curRecord.fromString(
        StringUtil.sprintf("001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.RAWREPO_DBC_ENRICHMENT_AGENCY_ID) +
        StringUtil.sprintf("996 00 *a %s", UpdateConstants.COMMON_AGENCYID)
    );
    RawRepoClientCore.addRecord(curRecord);

    record = new Record();
    record.fromString(
        StringUtil.sprintf("001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.COMMON_AGENCYID) +
        "004 00 *a e *r n" +
        StringUtil.sprintf("996 00 *a %s", UpdateConstants.COMMON_AGENCYID)
    );
    Assert.equalValue("Update record with 996 in current record. Only 996 is presented and unchanged in new record.",
        callFunction(record, "netpunkt", LOGIN_AGENCY_ID),
        []);
    RawRepoClientCore.clear();

    curRecord = new Record();
    curRecord.fromString(
        StringUtil.sprintf("001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.COMMON_AGENCYID) +
        "004 00 *a e *r n\n" +
        StringUtil.sprintf("996 00 *a %s", UpdateConstants.COMMON_AGENCYID)
    );
    RawRepoClientCore.addRecord(curRecord);
    curRecord = new Record();
    curRecord.fromString(
        StringUtil.sprintf("001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.RAWREPO_DBC_ENRICHMENT_AGENCY_ID) +
        StringUtil.sprintf("996 00 *a %s", UpdateConstants.COMMON_AGENCYID)
    );
    RawRepoClientCore.addRecord(curRecord);

    record = new Record();
    record.fromString(
        StringUtil.sprintf("001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.COMMON_AGENCYID) +
        "004 00 *a e *r n" +
        StringUtil.sprintf("996 00 *a %s", UpdateConstants.COMMON_AGENCYID)
    );
    Assert.equalValue("Update record with 996 in current record. Only 996 is presented and unchanged in new record.",
        callFunction(record, "netpunkt", LOGIN_AGENCY_ID),
        []);
    RawRepoClientCore.clear();

    curRecord = new Record();
    curRecord.fromString(
        StringUtil.sprintf("001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.COMMON_AGENCYID) +
        "004 00 *a e *r n\n" +
        StringUtil.sprintf("996 00 *a %s", UpdateConstants.COMMON_AGENCYID)
    );
    RawRepoClientCore.addRecord(curRecord);
    curRecord = new Record();
    curRecord.fromString(
        StringUtil.sprintf("001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.RAWREPO_DBC_ENRICHMENT_AGENCY_ID) +
        StringUtil.sprintf("996 00 *a %s", UpdateConstants.COMMON_AGENCYID)
    );
    RawRepoClientCore.addRecord(curRecord);

    record = new Record();
    record.fromString(
        StringUtil.sprintf("001 00 *a 1 234 567 8 *b %s\n", UpdateConstants.COMMON_AGENCYID) +
        "004 00 *a e *r n\n" +
        StringUtil.sprintf("996 00 *a %s", UpdateConstants.COMMON_AGENCYID)
    );
    Assert.equalValue("Update record with 996 in current record. 996 is presented and unchanged in new record.",
        callFunction(record, "netpunkt", LOGIN_AGENCY_ID),
        []);
    RawRepoClientCore.clear();

    OpenAgencyClientCore.clearFeatures();
});