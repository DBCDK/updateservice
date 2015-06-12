//-----------------------------------------------------------------------------
use( "SafeAssert" );
use( "RawRepoClient" );
use( "UnitTest" );
use( "Log" );
use( "Marc" );
use ( "LookUpRecord");
//-----------------------------------------------------------------------------

UnitTest.addFixture( "LookUpRecord", function( ) {
    var bundle = ResourceBundleFactory.getBundle( LookUpRecord.__BUNDLE_NAME );

    Log.trace( "Enter - LookupRecord UnitTest Fixture" );

    // creating common record in rawrepo
    var trueMarcRec = new Record( );
    var field001 = new Field( "001", "00" );
    field001.append ( new Subfield ( "a", "a1Val" ));
    field001.append (new Subfield ( "b", "191919" ));
    trueMarcRec.append (field001);
    var field004 = new Field( "004", "00" );
    field004.append ( new Subfield ( "a", "a1" ));
    field004.append (new Subfield ( "a", "a2" ));
    field004.append (new Subfield ( "b", "b1" ));
    trueMarcRec.append (field004);
    RawRepoClientCore.addRecord( trueMarcRec );

    // creating local record in rawrepo
    var trueMarcRec = new Record( );
    var field001 = new Field( "001", "00" );
    field001.append ( new Subfield ( "a", "a1Val" ));
    field001.append (new Subfield ( "b", "400800" ));
    trueMarcRec.append (field001);
    var field004 = new Field( "004", "00" );
    field004.append ( new Subfield ( "a", "a1" ));
    field004.append (new Subfield ( "a", "a2" ));
    field004.append (new Subfield ( "b", "b1" ));
    trueMarcRec.append (field004);
    RawRepoClientCore.addRecord( trueMarcRec );

    var record = {
        fields: [
            {
                name: '001', indicator: '00',
                subfields: [
                    { name: "a", value: "awrong" },
                    { name: "b", value: "870970" },
                    { name: "c", value: "c1Val" }
                ]
            }
        ]
    };
    var field = record.fields[ 0 ];
    var subfield = field.subfields[ 0 ];

    var errorMessage = ResourceBundle.getStringFormat( bundle, "lookup.record.does.not.exist", "awrong" );
    var errors1a = [{type:"ERROR", params:{url:"", message:errorMessage}}];

    SafeAssert.equal( "001a og 001b mismatch, findes ikke i repo" , LookUpRecord.validateSubfield( record, field, subfield, {}), errors1a );


    record = {
        fields: [{
            name: '001', indicator: '00', subfields: [{
                name: "a", value: "a1Val"
            }, {
                name: "b", value: "870970"
            }, {
                name: "c", value: "c1Val"
            }]
        }]
    };
    field = record.fields[ 0 ];
    subfield = field.subfields[ 0 ];

    SafeAssert.equal( "Common record findes i repo" ,  LookUpRecord.validateSubfield( record, field, subfield, {}), [] );

    record = {
        fields: [{
            name: '001', indicator: '00', subfields: [{
                name: "a", value: "a1Val"
            }, {
                name: "b", value: "870970"
            }, {
                name: "c", value: "c1Val"
            }]
        }]
    };
    field = record.fields[ 0 ];
    subfield = field.subfields[ 0 ];

    SafeAssert.equal( "Local record findes i repo" ,  LookUpRecord.validateSubfield( record, field, subfield, {}), [] );


    record = {
        fields: [{
            name: '001', indicator: '00', subfields: [{
                name: "a", value: "awrong"
            }, {
                name: "b", value: "870970"
            }, {
                name: "c", value: "c1Val"
            }]
        }]
    };
    field = record.fields[ 0 ];
    subfield = field.subfields[ 0 ];

    var errorMessage = ResourceBundle.getStringFormat( bundle, "lookup.record.does.not.exist", "awrong", "b1Val" );
    var errors1a = [{type:"ERROR", params:{url:"", message:errorMessage}}];
    SafeAssert.equal( "001 a mismatch" ,  LookUpRecord.validateSubfield( record, field, subfield, {}), errors1a );

    record = {
        fields: [{
            name: '001', indicator: '00', subfields: [{
                name: "a", value: "a1Val"
            }, {
                name: "b", value: "700600"
            }, {
                name: "c", value: "c1Val"
            }]
        }]
    };
    field = record.fields[ 0 ];
    subfield = field.subfields[ 0 ];

    var errorMessage = ResourceBundle.getStringFormat( bundle, "lookup.record.does.not.exist", "a1Val", "bwrong" );
    var errors1a = [{type:"ERROR", params:{url:"", message:errorMessage}}];
    SafeAssert.equal( "001 b mismatch" ,  LookUpRecord.validateSubfield( record, field, subfield, {}), errors1a );

    record = {
        fields: [{
            name: '001', indicator: '00', subfields: [{
                name: "a", value: "a1Val"
            }, {
                name: "b", value: "870970"
            }, {
                name: "c", value: "c1Val"
            }]
        }]
    };
    field = record.fields[ 0 ];
    subfield = field.subfields[ 0 ];

    var params = {"agencyId" :"600200"};
    var errorMessage = ResourceBundle.getStringFormat( bundle, "lookup.record.does.not.exist", "a1Val", "paramsNoMatch" );
    var errors1a = [{type:"ERROR", params:{url:"", message:errorMessage}}];
    SafeAssert.equal( "med ikke matchende params" ,  LookUpRecord.validateSubfield( record, field, subfield, params), errors1a );


    record = {
        fields: [{
            name: '001', indicator: '00', subfields: [{
                name: "a", value: "a1Val"
            }]
        }]
    };
    field = record.fields[ 0 ];
    subfield = field.subfields[ 0 ];

    var params = {"agencyId" :"191919"};
    SafeAssert.equal( "med matchende params" ,  LookUpRecord.validateSubfield( record, field, subfield, params), [] );

    var record = {
        fields: [{
            name: '001', indicator: '00', subfields: [{
                name: "a", value: "a1Val"
            }]
        }]
    };
    field = record.fields[ 0 ];
    subfield = field.subfields[ 0 ];

    var params = {"agencyId" :"191919", "allowedSubfieldValues" : ["a1" ,"a2" ,"a3"] ,"requiredFieldAndSubfield" : "004a" };
    SafeAssert.equal( "med valide allowedSubfieldValues og requiredFieldAndSubfield" ,  LookUpRecord.validateSubfield( record, field, subfield, params), [] );


    var record = {
        fields: [{
            name: '001', indicator: '00', subfields: [{
                name: "a", value: "a1Val"
            }]
        }]
    };
    field = record.fields[ 0 ];
    subfield = field.subfields[ 0 ];

    var params = {"agencyId" :"191919", "allowedSubfieldValues" : ["nonValidValue1" ,"nonValidValue2" ,"nonValidValue3"] ,"requiredFieldAndSubfield" : "004a" };
    var errorMessage = ResourceBundle.getStringFormat( bundle, "lookup.record.missing.values", "a1Val", "nonValidValue1,nonValidValue2,nonValidValue3", "004a" );
    var err = [{type:"ERROR", params:{url:"", message:errorMessage}}];
    SafeAssert.equal( "med ikke valide allowedSubfieldValues men valid requiredFieldAndSubfield" ,  LookUpRecord.validateSubfield( record, field, subfield, params), err );

    var record = {
        fields: [{
            name: '001', indicator: '00', subfields: [{
                name: "a", value: "a1Val"
            }]
        }]
    };
    field = record.fields[ 0 ];
    subfield = field.subfields[ 0 ];

    var params = {"agencyId" :"191919", "allowedSubfieldValues" : ["nonValidValue1" ,"nonValidValue2" ,"a2"] ,"requiredFieldAndSubfield" : "004a" };
    SafeAssert.equal( "med valid allowedSubfieldValues og valid requiredFieldAndSubfield med check af andet subfield" ,  LookUpRecord.validateSubfield( record, field, subfield, params), [] );

    var record = {
        fields: [{
            name: '001', indicator: '00', subfields: [{
                name: "a", value: "a1Val"
            }]
        }]
    };
    field = record.fields[ 0 ];
    subfield = field.subfields[ 0 ];

    var params = {"agencyId" :"191919", "allowedSubfieldValues" : ["a1" ,"a2" ,"a3"] ,"requiredFieldAndSubfield" : "005a" };
    var errorMessage = ResourceBundle.getStringFormat( bundle, "lookup.record.missing.values", "a1Val", "a1,a2,a3", "005a" );
    err = [{type:"ERROR", params:{url:"", message:errorMessage}}];
    SafeAssert.equal( "med valid allowedSubfieldValues men ikke valide requiredFieldAndSubfield" ,  LookUpRecord.validateSubfield( record, field, subfield, params), err );

    var record = {
        fields: [{
            name: '001', indicator: '00', subfields: [{
                name: "a", value: "a1Val"
            }]
        }]
    };
    field = record.fields[ 0 ];
    subfield = field.subfields[ 0 ];

    var params = {"agencyId" :"191919", "allowedSubfieldValues" : ["a1" ,"a2" ,"a3"]  };
    var errorMessage = 'Params attributten allowedSubfieldValues er angivet men requiredFieldAndSubfield mangler';
    err = [{type:"ERROR", params:{url:"", message:errorMessage}}];
    SafeAssert.equal( "med valid allowedSubfieldValues mangler requiredFieldAndSubfield" ,  LookUpRecord.validateSubfield( record, field, subfield, params), err );

    var record = {
        fields: [{
            name: '001', indicator: '00', subfields: [{
                name: "a", value: "a1Val"
            }]
        }]
    };
    field = record.fields[ 0 ];
    subfield = field.subfields[ 0 ];

    var params = {"agencyId" :"191919", "requiredFieldAndSubfield" : "005a" };
    var errorMessage = 'Params attributten requiredFieldAndSubfield er angivet men allowedSubfieldValues mangler';
    err = [{type:"ERROR", params:{url:"", message:errorMessage}}];
    SafeAssert.equal( "med valid requiredFieldAndSubfield mangler allowedSubfieldValues " ,  LookUpRecord.validateSubfield( record, field, subfield, params), err );

    var record = {
        fields: [{
            name: '001', indicator: '00', subfields: [{
                name: "a", value: "a1Val"
            }]
        }]
    };
    field = record.fields[ 0 ];
    subfield = field.subfields[ 0 ];

    var params = {"agencyId" :"191919", "allowedSubfieldValues" :{} ,"requiredFieldAndSubfield" : "005a" };
    var errorMessage = 'Params attributten allowedSubfieldValues er ikke af typen array';
    err = [{type:"ERROR", params:{url:"", message:errorMessage}}];
    SafeAssert.equal( "med valid allowedSubfieldValues men ikke valide requiredFieldAndSubfield" ,  LookUpRecord.validateSubfield( record, field, subfield, params), err );

    var record = {
        fields: [{
            name: '001', indicator: '00', subfields: [{
                name: "a", value: "a1Val"
            }]
        }]
    };
    field = record.fields[ 0 ];
    subfield = field.subfields[ 0 ];

    var params = {"agencyId" :"191919", "allowedSubfieldValues" :[] ,"requiredFieldAndSubfield" : "005a" };
    var errorMessage = 'Params attributten allowedSubfieldValues skal minimum indeholde een v\u00E6rdi';
    err = [{type:"ERROR", params:{url:"", message:errorMessage}}];
    SafeAssert.equal( "med valid allowedSubfieldValues men ikke valide requiredFieldAndSubfield" ,  LookUpRecord.validateSubfield( record, field, subfield, params), err );

    var record = {
        fields: [{
            name: '001', indicator: '00', subfields: [{
                name: "a", value: "a1Val"
            }]
        }]
    };
    field = record.fields[ 0 ];
    subfield = field.subfields[ 0 ];

    var params = {"agencyId" :"191919", "allowedSubfieldValues" : ["a1" ,"a2" ,"a3"] ,"requiredFieldAndSubfield" : {} };
    var errorMessage = 'Params attributten requiredFieldAndSubfield er ikke af typen string';
    err = [{type:"ERROR", params:{url:"", message:errorMessage}}];
    SafeAssert.equal( "med valid allowedSubfieldValues men ikke valid requiredFieldAndSubfield type" ,  LookUpRecord.validateSubfield( record, field, subfield, params), err );

} );