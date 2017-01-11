use("RecategorizationNoteFieldFactory");
use("Log");
use("Marc");
use("RecordUtil");
use("ResourceBundleFactory");
use("ResourceBundle");

EXPORTED_SYMBOLS = ['DefaultEnrichmentRecordHandler'];

/**
 * Module to implement entrypoints for the update logic in DBC and FBS
 * installations.
 *
 * @namespace
 * @name DefaultEnrichmentRecordHandler
 */
var DefaultEnrichmentRecordHandler = function () {
    function create(classificationDataInstance, classificationDataModule) {
        return {
            classifications: {
                instance: classificationDataInstance,
                module: classificationDataModule
            }
        }
    }

    /**
     * Creates a new enrichment record based on a common record.
     *
     * @param {Object} instance Instance returned by create().
     * @param {Record} commonRecord The DBC record.
     * @param {int}    agencyId    Library id for the local library.
     *
     * @return {Record} The new enrichment record.
     */
    function createRecord(instance, currentCommonRecord, updatingCommonMarc, agencyId) {
        Log.trace("Enter - DefaultEnrichmentRecordHandler.createRecord()");
        var result;
        try {
            result = new Record;
            var idField = new Field("001", "00");
            idField.append(new Subfield("a", updatingCommonMarc.getValue(/001/, /a/)));
            idField.append(new Subfield("b", agencyId.toString()));
            idField.append(new Subfield("c", RecordUtil.currentAjustmentTime()));
            idField.append(new Subfield("d", RecordUtil.currentAjustmentDate()));
            idField.append(new Subfield("f", "a"));
            result.append(idField);
            result = updateRecord(instance, currentCommonRecord, updatingCommonMarc, result);
            return result;
        } finally {
            Log.trace("Exit - DefaultEnrichmentRecordHandler.createRecord(): " + result);
        }
    }

    // Fix for story #1911 ,
    // adding a y08 field with subfield value *a UPDATE posttypeskift
    function __getY08PosttypeSkiftField() {
        Log.trace("Enter - __getY08PosttypeSkift()");
        var ret;
        try {
            var y08Field = new Field("y08", "00");
            y08Field.append(new Subfield("a", "UPDATE posttypeskift"));
            return ret = y08Field;
        } finally {
            Log.trace("Exit - __getY08PosttypeSkift() : " + ret.toString());
        }
    }

    /**
     * Updates an enrichment record with the classifications from
     * a DBC record.
     *
     * @param {Object} instance Instance returned by create().
     * @param {Record} commonRecord The DBC record.
     * @param {Record} enrichmentRecord The enrichment record to updated.
     *
     * @return {Record} The new enrichment record.
     */
    function updateRecord(instance, currentCommonMarc, updatingCommonMarc, enrichmentRecord) {
        Log.trace("Enter - DefaultEnrichmentRecordHandler.updateRecord()");
        var result;
        try {
            var record = instance.classifications.module.updateClassificationsInRecord(instance.classifications.instance, currentCommonMarc, updatingCommonMarc, enrichmentRecord);
            if (__isRecategorization(currentCommonMarc, updatingCommonMarc)) {
                Log.info("Record is a recategorization.");
                record = instance.classifications.module.removeClassificationsFromRecord(instance.classifications.instance, record);
                var field = RecategorizationNoteFieldFactory.newNoteField(currentCommonMarc, updatingCommonMarc);
                if (field !== undefined) {
                    record = RecordSorter.insertField(record, field);
                    record = RecordSorter.insertField(record, __getY08PosttypeSkiftField())
                }
            } else {
                Log.info("Record is not a recategorization.");
            }
            record.removeAll("004");
            updatingCommonMarc.eachField(/004/, function (field) {
                field.eachSubField(/./, function (field, subfield) {
                    record = RecordUtil.addOrReplaceSubfield(record, field.name, subfield.name, subfield.value);
                })
            });
            result = __correctRecordIfEmpty(record);
            return result;
        } finally {
            Log.trace("Exit - DefaultEnrichmentRecordHandler.updateRecord(): " + result);
        }
    }

    function correctRecord(instance, commonRecord, enrichmentRecord) {
        Log.trace("    commonRecord: " + commonRecord);
        Log.trace("    enrichmentRecord: " + enrichmentRecord);
        var result = null;

        // TODO erstatte hasClassificationData med et kald til java (og så alligevel ikke - det er noget knald at skulle konvertere
        // poster tilbage for at kunne kalde java - må afvente at correctRecord er flyttet til java.
        if (ClassificationData.hasClassificationData(instance.classifications.instance, commonRecord)) {
            if (!ClassificationData.hasClassificationsChanged(instance.classifications.instance, commonRecord, enrichmentRecord)) {
                Log.info("Classifications are the same. Removing them from library record.");
                result = instance.classifications.module.removeClassificationsFromRecord(instance.classifications.instance, enrichmentRecord);
            } else {
                Log.info("Classifications has changed.");
                result = enrichmentRecord;
            }
        } else {
            Log.info("Common record has no classifications.");
        }

        if (result === null) {
            result = enrichmentRecord.clone();
        }
        result = __cleanupEnrichmentRecord(result, commonRecord, instance.classifications.instance.fields);
        return __correctRecordIfEmpty(result);
    }

    // Method for cleaning up unneeded fields from an enrichment record by comparing it the the common record.
    function __cleanupEnrichmentRecord(enrichmentRecord, commonRecord, classificationRegexList) {
        var classificationList = __convertClassificationRegexListToRegularList(classificationRegexList);
        var alwaysKeepFieldsList = ["001", "004", "996"];
        var combinedKeepFieldsList = classificationList.concat(alwaysKeepFieldsList);
        combinedKeepFieldsList = combinedKeepFieldsList.sort();
        var newCleanedEnrichmentRecord = new Record();

        enrichmentRecord.eachField(/./, function (field) {
            if (__shouldEnrichmentRecordFieldBeKept(field, commonRecord, combinedKeepFieldsList, enrichmentRecord) === true) {
                newCleanedEnrichmentRecord.append(field);
            }
        });
        return newCleanedEnrichmentRecord;
    }

    function __convertClassificationRegexListToRegularList(classificationRegexList) {
        var classificationRegexListString = classificationRegexList.toString();
        classificationRegexListString = classificationRegexListString.slice(1, -1); // Remove leading and trailing slashes
        return classificationRegexListString.split("|");
    }

    // This function checks if a specific enrichment field should be kept, by examine the following:
    // (1) if the field nbr. is in the list of always keep fields (001, 004, 996 + classification fields)
    // (2) if field is not found in the common record from RawRepo
    // (3) if the field is a reference field that points to either a field from (1) or (2)
    // Returns true if fields should be kept otherwise false
    function __shouldEnrichmentRecordFieldBeKept(enrichmentField, commonRecord, listOfAlwaysKeepFields, alreadyProcessedEnrichmentFields) {
        if (listOfAlwaysKeepFields.indexOf(enrichmentField.name) !== -1) {
            return true;
        } else {
            if (commonRecord.existField(enrichmentField.name)) {
                if (UpdateConstants.REFERENCE_FIELDS.indexOf(enrichmentField.name) !== -1) {
                    return __isEnrichmentReferenceFieldPresentInAlreadyProcessedFields(enrichmentField, alreadyProcessedEnrichmentFields);
                } else {
                    var commonRecordFieldList = commonRecord.selectFields(enrichmentField.name);
                    return !__isEnrichmentFieldPresentInCommonFieldList(enrichmentField, commonRecordFieldList);
                }
            } else {
                return true;
            }
        }
    }

    // Returns true if the current reference enrichment field points to a enrichment fields that has been kept,
    // otherwise false.
    function __isEnrichmentReferenceFieldPresentInAlreadyProcessedFields(enrichmentField, alreadyProcessedEnrichmentFields) {
        var result = false;
        var subfieldZ = enrichmentField.getValue("z");
        if (subfieldZ !== undefined && subfieldZ.length > 4) {
            subfieldZ = subfieldZ.slice(0, 3);
        }
        if (subfieldZ !== undefined) {
            alreadyProcessedEnrichmentFields.eachField(subfieldZ, function (field) {
                if (field.name === subfieldZ) {
                    result = true;
                }
            });
        }
        return result;
    }

    // Returns true if the current enrichmentField is already present in the common record and therefore should NOT
    // be kept, otherwise false (the enrichfield should be kept).
    function __isEnrichmentFieldPresentInCommonFieldList(enrichmentField, commonFieldList) {
        var cleanedEnrichmentField = __createRecordFieldWithoutIgnorableSubfields(enrichmentField);
        var cleanedCommonFieldList = __createRecordFieldListWithoutIgnorableSubfields(commonFieldList);
        return __isFieldPresentInList(cleanedEnrichmentField, cleanedCommonFieldList);
    }

    function __createRecordFieldWithoutIgnorableSubfields(inputField) {
        var listOfIgnorableSubfields = ["&", "0", "1", "4"];
        var cleanedField = new Field(inputField.name, inputField.indicator);
        inputField.eachSubField(/./, function (field, subfield) {
            if (listOfIgnorableSubfields.indexOf(subfield.name) < 0) {
                cleanedField.append(subfield.name, subfield.value);
            }
        });
        return cleanedField;
    }

    function __createRecordFieldListWithoutIgnorableSubfields(notYetCleandedInputFieldList) {
        var cleanedFieldList = [];
        for (var i = 0; i < notYetCleandedInputFieldList.length; i++) {
            cleanedFieldList.push(__createRecordFieldWithoutIgnorableSubfields(notYetCleandedInputFieldList[i]));
        }
        return cleanedFieldList;
    }

    function __isFieldPresentInList(enrichmentField, commonRecordFieldList) {
        var result = false;
        var cleanedEnrichmentField = enrichmentField.toString().trim();
        for (var i = 0; i < commonRecordFieldList.length; i++) {
            if (commonRecordFieldList[i] !== undefined && cleanedEnrichmentField === commonRecordFieldList[i].toString().trim()) {
                result = true;
                break;
            }
        }
        return result;
    }

    function __correctRecordIfEmpty(record) {
        Log.trace("Enter - DefaultEnrichmentRecordHandler.__correctRecordIfEmpty");
        Log.debug("Record: ", record.toString());
        var result = record;
        try {
            var libraryId = record.getValue(/001/, /b/);
            if (libraryId === UpdateConstants.RAWREPO_DBC_ENRICHMENT_AGENCY_ID ||
                libraryId === UpdateConstants.RAWREPO_COMMON_AGENCYID) {
                Log.debug("Return full record for " + libraryId);
                return record;
            }

            for (var i = 0; i < record.size(); i++) {
                var field = record.field(i);
                if (!(field.name.match(/00[1|4]|996/))) {
                    Log.debug("Return full record.");
                    return result;
                }
            }
            result = new Record;
            Log.debug("Return empty record.");
            return result;
        } finally {
            Log.trace("Exit - DefaultEnrichmentRecordHandler.__correctRecordIfEmpty: " + result.toString());
        }
    }

    function __isRecategorization(currentCommonRecord, updatingCommonRecord) {
        Log.trace("Enter - DefaultEnrichmentRecordHandler.__isRecategorization()");
        var result;
        try {
            if (updatingCommonRecord.matchValue(/004/, /a/, /e/)) {
                if (currentCommonRecord.matchValue(/004/, /a/, /b/)) {
                    return result = true;
                }
            }

            if (updatingCommonRecord.matchValue(/004/, /a/, /b/)) {
                if (currentCommonRecord.matchValue(/004/, /a/, /e/)) {
                    return result = true;
                }
            }

            if (updatingCommonRecord.matchValue(/008/, /t/, /p/)) {
                if (!currentCommonRecord.matchValue(/008/, /t/, /p/)) {
                    return result = true;
                }
            }
            else if (currentCommonRecord.matchValue(/008/, /t/, /p/)) {
                return result = true;
            }

            var bundle = ResourceBundleFactory.getBundle("categorization-codes");
            var currentMaterialField = RecategorizationNoteFieldProvider.loadFieldRecursiveReplaceValue(bundle, currentCommonRecord, "009", /a|g/);
            var updatingMaterialField = RecategorizationNoteFieldProvider.loadFieldRecursiveReplaceValue(bundle, updatingCommonRecord, "009", /a|g/);

            if (currentMaterialField === undefined && updatingMaterialField === undefined) {
                return result = false;
            }
            if (currentMaterialField === undefined && updatingMaterialField !== undefined) {
                return result = true;
            }
            if (currentMaterialField !== undefined && updatingMaterialField === undefined) {
                return result = true;
            }

            // At this point 009 a|g is defined in both records
            var currentA = [], currentG = [];
            var updatingA = [], updatingG = [];

            currentMaterialField.eachSubField(/a/, function (field, subField) {
                currentA.push(subField.value);
            });

            currentMaterialField.eachSubField(/g/, function (field, subField) {
                currentG.push(subField.value);
            });

            updatingMaterialField.eachSubField(/a/, function (field, subField) {
                updatingA.push(subField.value);
            });

            updatingMaterialField.eachSubField(/g/, function (field, subField) {
                updatingG.push(subField.value);
            });

            var aDiffers = currentA.sort().concat().join("") !== updatingA.sort().concat().join("");
            var gDiffers = currentG.sort().concat().join("") !== updatingG.sort().concat().join("");

            if (currentA.length !== updatingA.length || currentG.length !== updatingG.length || aDiffers || gDiffers) {
                return result = true;
            }

            var record_lookup = RecordLookupField.createFromField(currentMaterialField);
            if (RecordLookupField.containsField(record_lookup, updatingMaterialField)) {
                return result = false;
            }
            return result = false;
        } finally {
            Log.trace("Exit - DefaultEnrichmentRecordHandler.__isRecategorization(): ", result);
        }
    }

    return {
        'create': create,
        'createRecord': createRecord,
        'updateRecord': updateRecord,
        'correctRecord': correctRecord
    }
}();
