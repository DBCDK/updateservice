use("FieldSorting");
use("Print");
use("ReadFile");
use("ResourceBundle");
use("ResourceBundleFactory");
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
     * Checks whether the template provided allow *rd if the record
     * contains such. If it allows it, there are no reason to make a full
     * validation.
     * @param record
     * @param templateProvider
     * @param settings
     * @returns {boolean} True : Either don't have *rd or *rd isn't allowed.
     *                    False : it's a legal delete record
     */
    function isNotLegalDeleteRecord(record, templateProvider, settings) {
        for (var fieldLoopIndex = 0; fieldLoopIndex < record.fields.length; fieldLoopIndex++) {
            var field = record.fields[fieldLoopIndex];
            if (field.name === "004") {
                if (field.subfields !== undefined) {
                    for (var subfieldLoopIndex = 0; subfieldLoopIndex < field.subfields.length; subfieldLoopIndex++) {
                        var subfield = field.subfields[subfieldLoopIndex];
                        if (subfield.name === "r") {
                            if (subfield.value === "d") {
                                var subResult = __validateSubfield(record, field, subfield, templateProvider, settings);
                                if (subResult === []) {
                                    return false;
                                }
                            }
                        }
                    }
                }
                return true;
            }
        }
        return true;
    }

    /**
     * Validates an entire record.
     *
     * @param {Object} record The record that contains the subfield.
     * @param {Object} templateProvider A function that returns the
     *                 optimized template to use for the validation.
     * @param settings properties object
     * @return {Array} An array of validation errors.
     */
    function doValidateRecord(record, templateProvider, settings) {
        Log.trace("Enter - Validator.doValidateRecord()");
        try {
            var bundle = ResourceBundleFactory.getBundle(BUNDLE_NAME);
            var result = [];
            var template = templateProvider();
            if (record.fields !== undefined) {
                // Validation should only be performed if it isn't a legal delete record
                if (isNotLegalDeleteRecord(record, templateProvider, settings)) {
                    for (var i = 0; i < record.fields.length; i++) {
                        var subResult = __validateField(record, record.fields[i], templateProvider, settings);
                        for (var j = 0; j < subResult.length; j++) {
                            subResult[j].ordinalPositionOfField = i;
                        }
                        result = result.concat(subResult);
                    }
                }
            }
            if (template.rules instanceof Array) {
                for (var k = 0; k < template.rules.length; k++) {
                    var rule = template.rules[k];
                    Log.debug("Record rule: ", rule.name === undefined ? "name undefined" : rule.name);
                    try {
                        TemplateOptimizer.setTemplatePropertyOnRule(rule, template);
                        var valErrors = rule.type(record, rule.params, settings);
                        valErrors = __updateErrorTypeOnValidationResults(rule, valErrors);
                        result = result.concat(valErrors);
                    } catch (e) {
                        throw ResourceBundle.getStringFormat(bundle, "record.execute.error", e);
                    }
                }
            }
            return result;
        } finally {
            Log.trace("Exit - Validator.doValidateRecord()");
        }
    }

    /**
     * Validates a single field in a record.
     *
     * @param {Object} record The record that contains the subfield.
     * @param {Object} field  The field that contains the subfield.
     * @param {Object} templateProvider A function that returns the
     *                 optimized template to use for the validation.
     * @param settings properties object
     * @return {Array} An array of validation errors.
     */
    function __validateField(record, field, templateProvider, settings) {
        Log.trace("Enter - Validator.__validateField()");
        try {
            var bundle = ResourceBundleFactory.getBundle(BUNDLE_NAME);
            var result = [];
            var template = templateProvider();
            var templateField = template.fields[field.name];
            if (templateField === undefined) {
                return [ValidateErrors.fieldError("", ResourceBundle.getStringFormat(bundle, "wrong.field", field.name))];
            }
            var i;
            if (field.subfields !== undefined) {
                Log.debug("Field ", field.name, " has ", field.subfields.length, " subfields: ", JSON.stringify(field));
                if (field.subfields.length === 0) {
                    return [ValidateErrors.fieldError("", ResourceBundle.getStringFormat(bundle, "empty.field", field.name))];
                }
                for (i = 0; i < field.subfields.length; i++) {
                    var subResult = __validateSubfield(record, field, field.subfields[i], templateProvider, settings);
                    for (var j = 0; j < subResult.length; j++) {
                        subResult[j].ordinalPositionOfSubfield = i;
                    }
                    result = result.concat(subResult);
                }
            }
            if (templateField.rules instanceof Array) {
                for (i = 0; i < templateField.rules.length; i++) {
                    var rule = templateField.rules[i];
                    // DO NOT make a Log.<whatever>("Field rule ....); For unknown reasons "Field rule" results in no logging.
                    Log.debug("Exec rule [", field.name === undefined ? "field name undefined" : field.name, "]: ", rule.name === undefined ? "rule name undefined" : rule.name);
                    try {
                        TemplateOptimizer.setTemplatePropertyOnRule(rule, template);

                        var valErrors = rule.type(record, field, rule.params, settings);
                        valErrors = __updateErrorTypeOnValidationResults(rule, valErrors);
                        result = result.concat(valErrors);
                    } catch (ex) {
                        throw ResourceBundle.getStringFormat(bundle, "field.execute.error", field.name, ex);
                    }
                }
            }
            for (var k = 0; k < result.length; k++) {
                if (templateField.url !== undefined && templateField.url !== "") {
                    if (result[k].urlForDocumentation === undefined || result[k].urlForDocumentation === "" || result[k].urlForDocumentation === "TODO:fixurl") {
                        result[k].urlForDocumentation = templateField.url;
                    }
                }
            }
            return result;
        } finally {
            Log.trace("Exit - Validator.__validateField()");
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
     * @param settings properties object
     * @return {Array} An array of validation errors.
     */
    function __validateSubfield(record, field, subfield, templateProvider, settings) {
        Log.trace("Enter - Validator.__validateSubfield()");

        try {
            var bundle = ResourceBundleFactory.getBundle(BUNDLE_NAME);
            var result = [];
            var template = templateProvider();
            var templateField = template.fields[field.name];
            if (templateField === undefined) {
                return [ValidateErrors.fieldError("", ResourceBundle.getStringFormat(bundle, "wrong.field", field.name))];
            }
            // Skip validation if subfield.name is upper case.
            if (subfield.name !== subfield.name.toLowerCase()) {
                return [];
            }
            var templateSubfield = templateField.subfields[subfield.name];
            if (templateSubfield === undefined) {
                return [ValidateErrors.subfieldError("", ResourceBundle.getStringFormat(bundle, "wrong.subfield", subfield.name, field.name))];
            }
            if (subfield.value === "") {
                if (UpdateConstants.EMPTY_SUBFIELDS.indexOf(subfield.name) === -1) {
                    return [ValidateErrors.subfieldError("", ResourceBundle.getStringFormat(bundle, "empty.subfield", field.name, subfield.name))];
                }
            }
            if (templateSubfield instanceof Array) {
                for (var i = 0; i < templateSubfield.length; i++) {
                    var rule = templateSubfield[i];
                    Log.debug("field ", field.name === undefined ? "field undefined" : field.name);
                    Log.debug("subfield ", subfield.name === undefined ? "subfield undefined" : subfield.name);
                    Log.debug("rule ", rule.name === undefined ? "rule undefined" : rule.name);
                    try {
                        TemplateOptimizer.setTemplatePropertyOnRule(rule, template);

                        var valErrors = rule.type(record, field, subfield, rule.params, settings);
                        valErrors = __updateErrorTypeOnValidationResults(rule, valErrors);
                        result = result.concat(valErrors);
                    } catch (e) {
                        throw ResourceBundle.getStringFormat(bundle, "subfield.execute.error", field.name, subfield.name, e);
                    }
                }
            }
            return result;
        } finally {
            Log.trace("Exit - Validator.__validateSubfield()");
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
        'doValidateRecord': doValidateRecord,
        '__validateField': __validateField,
        '__validateSubfield': __validateSubfield
    };
}();
