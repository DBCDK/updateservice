//-----------------------------------------------------------------------------
use( "SafeAssert" );
use( "StringUtil" );
use( "SubfieldMandatoryIfSubfieldNotPresentRule" );
use( "UnitTest" );

//-----------------------------------------------------------------------------
UnitTest.addFixture( "SubfieldMandatoryIfSubfieldNotPresentRule.validateField", function() {
    var exceptCallFormat = "SubfieldMandatoryIfSubfieldNotPresentRule.validateField( %s, %s, %s )";

    var recordArg = null;
    var fieldArg = {
        name: "001", indicator: "00",
        subfields: [ { name: "a", value: "xx" } ]
    };
    var paramsArg = { subfield: "m", not_presented_subfield: ["652m"] };
    Assert.exception( "records is null", StringUtil.sprintf( exceptCallFormat, uneval( recordArg ), uneval( fieldArg ), uneval( paramsArg ) ) );

    recordArg = {
        fields: [
            {
                name: "001", indicator: "00",
                subfields: [ { name: "a", value: "xx" } ]
            }
        ]
    };
    fieldArg = null;
    paramsArg = { subfield: "m", not_presented_subfield: ["652m"] };
    Assert.exception( "field is null", StringUtil.sprintf( exceptCallFormat, uneval( recordArg ), uneval( fieldArg ), uneval( paramsArg ) ) );

    recordArg = {
        fields: [
            {
                name: "001", indicator: "00",
                subfields: [ { name: "a", value: "xx" } ]
            }
        ]
    };
    fieldArg = recordArg.fields[0];
    paramsArg = null;
    Assert.exception( "params is null", StringUtil.sprintf( exceptCallFormat, uneval( recordArg ), uneval( fieldArg ), uneval( paramsArg ) ) );
    paramsArg = undefined;
    Assert.exception( "params is undefined", StringUtil.sprintf( exceptCallFormat, uneval( recordArg ), uneval( fieldArg ), uneval( paramsArg ) ) );
    paramsArg = {};
    Assert.exception( "params is empty object", StringUtil.sprintf( exceptCallFormat, uneval( recordArg ), uneval( fieldArg ), uneval( paramsArg ) ) );
    paramsArg = { not_presented_subfield: "652m" };
    Assert.exception( "params.subfield is undefined", StringUtil.sprintf( exceptCallFormat, uneval( recordArg ), uneval( fieldArg ), uneval( paramsArg ) ) );
    paramsArg = { subfield: 45, not_presented_subfield: ["652m"] };
    Assert.exception( "params.subfield is not string", StringUtil.sprintf( exceptCallFormat, uneval( recordArg ), uneval( fieldArg ), uneval( paramsArg ) ) );
    paramsArg = { subfield: "m" };
    Assert.exception( "params.not_presented_subfield is undefined", StringUtil.sprintf( exceptCallFormat, uneval( recordArg ), uneval( fieldArg ), uneval( paramsArg ) ) );
    paramsArg = { subfield: "m", not_presented_subfield: 47 };
    Assert.exception( "params.not_presented_subfield is not array", StringUtil.sprintf( exceptCallFormat, uneval( recordArg ), uneval( fieldArg ), uneval( paramsArg ) ) );
    paramsArg = { subfield: "m", not_presented_subfield: ["042"] };
    Assert.exception( "params.not_presented_subfield is not field/subfield", StringUtil.sprintf( exceptCallFormat, uneval( recordArg ), uneval( fieldArg ), uneval( paramsArg ) ) );

    recordArg = {
        fields: [
            {
                name: "001", indicator: "00",
                subfields: [ { name: "a", value: "xx" } ]
            }
        ]
    };
    fieldArg = recordArg.fields[0];
    paramsArg = { subfield: "a", not_presented_subfield: ["001b"] };
    SafeAssert.equal( "001a: Mandatory without 001b", SubfieldMandatoryIfSubfieldNotPresentRule.validateField( recordArg, fieldArg, paramsArg ), [] );
    paramsArg = { subfield: "m", not_presented_subfield: ["001a"] };
    SafeAssert.equal( "001m: Not mandatory with 001a", SubfieldMandatoryIfSubfieldNotPresentRule.validateField( recordArg, fieldArg, paramsArg ), [] );
    paramsArg = { subfield: "m", not_presented_subfield: ["001b"] };
    SafeAssert.equal( "001m: Mandatory without 001b", SubfieldMandatoryIfSubfieldNotPresentRule.validateField( recordArg, fieldArg, paramsArg ),
        [ ValidateErrors.fieldError( "TODO:url", 'Delfelt "m" mangler i felt "001".' ) ] );

    recordArg = {
        fields: [
            { name: "001", indicator: "00",
                subfields: [ { name: "a", value: "xx" } ] },
            { name: "002", indicator: "00",
                subfields: [ { name: "a", value: "xx" } ] },
            { name: "003", indicator: "00",
                subfields: [ { name: "a", value: "xx" } ] }
        ]
    };
    fieldArg = recordArg.fields[0];
    paramsArg = { subfield: "a", not_presented_subfield: ["042abc", "002z", "001b"] };
    SafeAssert.equal( "Test 1", SubfieldMandatoryIfSubfieldNotPresentRule.validateField( recordArg, fieldArg, paramsArg ), [] );

    paramsArg = { subfield: "m", not_presented_subfield: ["042abc", "002z", "001a"] };
    SafeAssert.equal( "Test 2", SubfieldMandatoryIfSubfieldNotPresentRule.validateField( recordArg, fieldArg, paramsArg ), [] );

    recordArg = {
        fields: [
            { name: "001", indicator: "00",
                subfields: [ { name: "a", value: "xx" } ] },
            { name: "002", indicator: "00",
                subfields: [ { name: "p", value: "xx" } ] },
            { name: "002", indicator: "00",
                subfields: [ { name: "m", value: "xx" } ] },
            { name: "003", indicator: "00",
                subfields: [ { name: "a", value: "xx" } ] }
        ]
    };
    paramsArg = { subfield: "m", not_presented_subfield: ["002o"] };
    fieldArg = recordArg.fields[1];
    SafeAssert.equal( "Test 3", SubfieldMandatoryIfSubfieldNotPresentRule.validateField( recordArg, fieldArg, paramsArg ), [] );

    paramsArg = { subfield: "o", not_presented_subfield: ["002m"] };
    fieldArg = recordArg.fields[1];
    SafeAssert.equal( "Test 4", SubfieldMandatoryIfSubfieldNotPresentRule.validateField( recordArg, fieldArg, paramsArg ), [] );
} );
