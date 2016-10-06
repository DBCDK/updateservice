use("FieldSorting");
use("Print");
use("ReadFile");
use("RecordUtil");
use("ResourceBundle");
use("ResourceBundleFactory");
use("StopWatch");
use("StringUtil");
use("TemplateOptimizer");
use("ValidateErrors");

EXPORTED_SYMBOLS = ['Validator'];

/**
 * Module to optimize a template. Unittests can be found in ValidatorTest.use.js
 *
 * @namespace
 * @name Validator
 *
 */
var Validator = function () {
    var BUNDLE_NAME = "validation";

    /**
     * Validates an entire record.
     *
     * @param {Object} record The record that contains the subfield.
     * @param {Object} templateProvider A function that returns the
     *                 optimized template to use for the validation.
     *
     * @return {Array} An array of validation errors.
     */
    function validateRecord(record, templateProvider, settings) {
        Log.trace("Enter - Validator.validateRecord()");
        var watchFunc = new StopWatch();
        try {
            var bundle = ResourceBundleFactory.getBundle(BUNDLE_NAME);
            watchFunc.lap("javascript.Validator.validateRecord.bundle");
            var result = [];
            var template = templateProvider();
            watchFunc.lap("javascript.Validator.validateRecord.templateProvider");
            if (record.fields !== undefined) {
                for (var i = 0; i < record.fields.length; i++) {
                    var subResult = validateField(record, record.fields[i], templateProvider, settings);
                    var field = record.fields[i];
                    if (template.fields[field.name] && template.fields[field.name].sorting) {
                        FieldSorting.sort(field, template.fields[field.name].sorting);
                    }
                    var val = i + 1;
                    for (var j = 0; j < subResult.length; j++) {
                        subResult[j].params.param.push({key: "fieldno", value: val});
                    }
                    result = result.concat(subResult);
                }
            } else {
                // TODO: Return error.
            }
            if (template.rules instanceof Array) {
                for (var k = 0; k < template.rules.length; k++) {
                    var rule = template.rules[k];
                    Log.debug("Record rule: ", rule.name);
                    try {
                        TemplateOptimizer.setTemplatePropertyOnRule(rule, template);
                        var watch = new StopWatch("javascript.Validator." + rule.name);
                        var valErrors = rule.type(record, rule.params, settings);
                        watch.stop();
                        valErrors = __updateErrorTypeOnValidationResults(rule, valErrors);
                        result = result.concat(valErrors);
                    } catch (e) {
                        throw ResourceBundle.getStringFormat(bundle, "record.execute.error", e);
                    }
                }
            } else {
                // TODO: Return error.
            }
            return result;
        } finally {
            watchFunc.stop("javascript.Validator.validateRecord");
            Log.trace("Exit - Validator.validateRecord()");
        }
    }

    /**
     * Validates a single field in a record.
     *
     * @param {Object} record The record that contains the subfield.
     * @param {Object} field  The field that contains the subfield.
     * @param {Object} templateProvider A function that returns the
     *                 optimized template to use for the validation.
     *
     * @return {Array} An array of validation errors.
     */
    function validateField(record, field, templateProvider, settings) {
        Log.trace("Enter - Validator.validateField()");
        var watchFunc = new StopWatch("javascript.Validator.validateField");
        try {
            var bundle = ResourceBundleFactory.getBundle(BUNDLE_NAME);
            var result = [];
            var template = templateProvider();
            var templateField = template.fields[field.name];
            if (templateField === undefined) {
                return [ValidateErrors.fieldError("", ResourceBundle.getStringFormat(bundle, "wrong.field", field.name), RecordUtil.getRecordPid(record))];
            }
            var i;
            if (field.subfields !== undefined) {
                Log.debug("Field ", field.name, " has ", field.subfields.length, " subfields: ", JSON.stringify(field));
                if (field.subfields.length === 0) {
                    return [ValidateErrors.fieldError("", ResourceBundle.getStringFormat(bundle, "empty.field", field.name), RecordUtil.getRecordPid(record))];
                }
                for (i = 0; i < field.subfields.length; i++) {
                    var subResult = validateSubfield(record, field, field.subfields[i], templateProvider, settings);
                    var val = i + 1;
                    for (var j = 0; j < subResult.length; j++) {
                        subResult[j].params.param.push({key: "subfieldno", value: val});
                    }
                    result = result.concat(subResult);
                }
            }
            if (templateField.rules instanceof Array) {
                for (i = 0; i < templateField.rules.length; i++) {
                    var rule = templateField.rules[i];
                    Log.debug("Field rule [", field.name, "]: ", rule.name);
                    try {
                        TemplateOptimizer.setTemplatePropertyOnRule(rule, template);

                        var watch = new StopWatch("javascript.Validator." + rule.name);
                        var valErrors = rule.type(record, field, rule.params, settings);
                        watch.stop();
                        valErrors = __updateErrorTypeOnValidationResults(rule, valErrors);
                        result = result.concat(valErrors);
                    } catch (ex) {
                        throw ResourceBundle.getStringFormat(bundle, "field.execute.error", field.name, ex);
                    }
                }
            } else {
                // TODO: Return error.
            }
            for (var k = 0; k < result.length; k++) {
                var found = false;
                for (var m = 0; m < result[k].params.param.length && !found; m++) {
                    if (result[k].params.param[m].key === "url" && templateField.url !== undefined && templateField.url !== "") {
                        found = true;
                        result[k].params.param[m].value = templateField.url;
                    }
                }
                if (!found) {
                    result[k].params.param.push({key: "url", value: templateField.url});
                }
            }
            return result;
        } finally {
            watchFunc.stop();
            Log.trace("Exit - Validator.validateField()");
        }
    }

    /**
     * Validates a single subfield in a record.
     *
     * @param {Object} record The record that contains the subfield.
     * @param {Object} field  The field that contains the subfield.
     * @param {Object} subfield The subfield itself.
     * @param {Object} templateProvider A function that returns the
     *                 optimized template to use for the validation.
     *
     * @return {Array} An array of validation errors.
     */
    function validateSubfield(record, field, subfield, templateProvider, settings) {
        Log.trace("Enter - Validator.validateSubfield()");
        var watchFunc = new StopWatch("javascript.Validator.validateSubfield");
        try {
            var bundle = ResourceBundleFactory.getBundle(BUNDLE_NAME);
            var result = [];
            var template = templateProvider();
            var templateField = template.fields[field.name];
            if (templateField === undefined) {
                return [ValidateErrors.fieldError("", ResourceBundle.getStringFormat(bundle, "wrong.field", field.name), RecordUtil.getRecordPid(record))];
            }
            // Skip validation if subfield.name is upper case.
            if (subfield.name !== subfield.name.toLowerCase()) {
                return [];
            }
            var templateSubfield = templateField.subfields[subfield.name];
            if (templateSubfield === undefined) {
                return [ValidateErrors.subfieldError("", ResourceBundle.getStringFormat(bundle, "wrong.subfield", subfield.name, field.name), RecordUtil.getRecordPid(record))];
            }
            if (subfield.value === "") {
                if (UpdateConstants.EMPTY_SUBFIELDS.indexOf(subfield.name) === -1) {
                    return [ValidateErrors.subfieldError("", ResourceBundle.getStringFormat(bundle, "empty.subfield", field.name, subfield.name), RecordUtil.getRecordPid(record))];
                }
            }
            if (templateSubfield instanceof Array) {
                for (var i = 0; i < templateSubfield.length; i++) {
                    var rule = templateSubfield[i];
                    Log.debug("Subfield rule [", field.name, " *", subfield.name, "]: ", rule.name);
                    try {
                        TemplateOptimizer.setTemplatePropertyOnRule(rule, template);

                        var watch = new StopWatch("javascript.Validator." + rule.name);
                        var valErrors = rule.type(record, field, subfield, rule.params, settings);
                        watch.stop();
                        valErrors = __updateErrorTypeOnValidationResults(rule, valErrors);
                        result = result.concat(valErrors);
                    } catch (e) {
                        throw ResourceBundle.getStringFormat(bundle, "subfield.execute.error", field.name, subfield.name, e);
                    }
                }
            } else {
                // TODO: Return error.
            }
            return result;
        } finally {
            watchFunc.stop();
            Log.trace("Exit - Validator.validateSubfield()");
        }
    }

    function __updateErrorTypeOnValidationResults(rule, valErrors) {
        Log.trace("Enter - Validator.__updateErrorTypeOnValidationResults");
        try {
            if (rule.hasOwnProperty("errorType")) {
                for (var i = 0; i < valErrors.length; i++) {
                    Log.trace("Update validation error with type: ", rule.errorType);
                    valErrors[i].type = rule.errorType;
                }
            }
            return valErrors;
        } finally {
            Log.trace("Exit - Validator.__updateErrorTypeOnValidationResults");
        }
    }

    return {
        'BUNDLE_NAME': BUNDLE_NAME,
        'validateRecord': validateRecord,
        'validateField': validateField,
        'validateSubfield': validateSubfield
    };
}();
