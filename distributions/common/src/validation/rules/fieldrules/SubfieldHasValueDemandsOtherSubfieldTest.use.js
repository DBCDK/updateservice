use("SafeAssert");
use("SubfieldHasValueDemandsOtherSubfield");
use("UnitTest");

UnitTest.addFixture("SubfieldHasValueDemandsOtherSubfield", function () {
    var bundle = ResourceBundleFactory.getBundle(SubfieldHasValueDemandsOtherSubfield.__BUNDLE_NAME);

    var record = {};
    var field1 = {
        "name": "001", "indicator": "00", subfields: [{
            "name": "a", "value": "b"
        }, {
            "name": "c", "value": "42"
        }]
    };
    record.fields = [field1];
    var field2 = {
        "name": "002", "indicator": "00", subfields: [{
            "name": "d", "value": "e"
        }, {
            "name": "f", "value": "g"
        }]
    };
    record.fields = [field2];
    var params = {
        "subfieldConditional": "a",
        "subfieldConditionalValue": "b",
        "fieldMandatory": "002",
        "subfieldMandatory": "d"
    };
    SafeAssert.equal("1 subfieldHasValueDemandsOtherSubfield - ok", SubfieldHasValueDemandsOtherSubfield.validateField(record, field1, params), []);

    record = {};
    record.fields = [field1];
    var field3 = {
        "name": "003", "indicator": "00", subfields: [{
            "name": "d", "value": "e"
        }, {
            "name": "f", "value": "g"
        }]
    };
    record.fields = [field3];
    var msg = ResourceBundle.getStringFormat(bundle, "subfield.has.value.demands.other.subfield.rule.error", "a", "001", "b", "002", "d");
    var errorMsg = [ValidateErrors.fieldError("TODO:fixurl", msg)];
    SafeAssert.equal("2 subfieldHasValueDemandsOtherSubfield - not ok", SubfieldHasValueDemandsOtherSubfield.validateField(record, field1, params), errorMsg);

    record = {};
    record.fields = [field1];
    var field4 = {
        "name": "002", "indicator": "00", subfields: [{
            "name": "e", "value": "d"
        }, {
            "name": "f", "value": "g"
        }]
    };
    record.fields = [field4];
    msg = ResourceBundle.getStringFormat(bundle, "subfield.has.value.demands.other.subfield.rule.error", "a", "001", "b", "002", "d");
    errorMsg = [ValidateErrors.fieldError("TODO:fixurl", msg)];
    SafeAssert.equal("3 subfieldHasValueDemandsOtherSubfield - not ok", SubfieldHasValueDemandsOtherSubfield.validateField(record, field1, params), errorMsg);

    record = {};
    record.fields = [field1];
    errorMsg = [ValidateErrors.fieldError("TODO:fixurl", msg)];
    SafeAssert.equal("4 subfieldHasValueDemandsOtherSubfield - not ok", SubfieldHasValueDemandsOtherSubfield.validateField(record, field1, params), errorMsg);
});
