//-----------------------------------------------------------------------------
use( "DanMarc2Converter" );
use( "Marc" );
use( "MarcClasses" );
use( "Log" );

//-----------------------------------------------------------------------------
EXPORTED_SYMBOLS = [ 'ClassificationData' ];

//-----------------------------------------------------------------------------
var ClassificationData = function() {
    function create( fieldsRegExp )  {
        return {
            fields: fieldsRegExp
        }
    }

    function hasClassificationData( instance, marc ) {
        Log.trace( "Enter - ClassificationData.hasClassificationData()" );

        var result = null;
        try {
            Log.trace( "Fields: " + instance.fields );
            Log.trace( "Record: " + marc );

            return result = marc.existField(instance.fields);
        }
        finally {
            Log.trace( "Exit - ClassificationData.hasClassificationData(): " + result );
        }
    }
    
    function hasClassificationsChanged( instance, oldMarc, newMarc ) {
        Log.trace( "Enter - ClassificationData.hasClassificationsChanged()" );

        var result = false;
        var reason = undefined;
        try {
            Log.trace("    oldMarc: " + oldMarc);
            Log.trace("    newMarc: " + newMarc);

            if (instance.fields.test("008")) {
                if (__hasSubfieldChangedMatcher(oldMarc, newMarc, /008/, /t/, /m|s/, /p/)) {
                    reason = "008t m|s -> p";
                    return result = true;
                }
            }

            if (instance.fields.test("009")) {
                if (__hasSubfieldJustChanged(oldMarc, newMarc, __value, /009/, /a/)) {
                    reason = "009a";
                    return result = true;
                }
                if (__hasSubfieldJustChanged(oldMarc, newMarc, __value, /009/, /g/)) {
                    reason = "009g";
                    return result = true;
                }
            }

            if (instance.fields.test("038") || instance.fields.test("039")) {
                var selectFields = /038|039/;
                if (__hasRecordChanged(__createSubRecord(oldMarc, selectFields),
                        __createSubRecord(newMarc, selectFields), __value)) {
                    reason = "038|039";
                    return result = true;
                }
            }

            if (instance.fields.test("100")) {
                if (__hasFieldByNameChanged(oldMarc, newMarc, "100", __stripValue, /[^a|h|k|e|f]/)) {
                    reason = "100 Delfelterne ahkef";
                    return result = true;
                }
            }

            if (instance.fields.test("110")) {
                if (__hasFieldByNameChanged(oldMarc, newMarc, "110", __stripValue, /[^s|a|c|e|i|k|j]/ ) ) {
                    reason = "110 saceikj";
                    return result = true;
                }
            }

            if (instance.fields.test("239")) {
                var checkField = true;

                if( oldMarc.matchValue( /001/, /b/, RegExp( UpdateConstants.RAWREPO_COMMON_AGENCYID ) ) &&
                    newMarc.matchValue( /001/, /b/, RegExp( UpdateConstants.RAWREPO_COMMON_AGENCYID ) ) ) {

                    checkField = oldMarc.getValue( /245/, /a/ ) !== newMarc.getValue( /239/, /t/ );
                }

                if( checkField ) {
                    if( __hasFieldByNameChanged(oldMarc, newMarc, "239", __stripValueLength10, /[^a|h|k|e|f|t|\u00F8]/ ) ) {
                        reason = "239 ahkeft\u00F8";
                        return result = true;
                    }
                }
            }

            if (instance.fields.test("245")) {
                var checkField = true;

                if( oldMarc.existField( new MatchField( /239/, undefined, /a|h|k|e|f|t|\u00F8/ ) ) ||
                    newMarc.existField( new MatchField( /239/, undefined, /a|h|k|e|f|t|\u00F8/ ) ) )
                {
                    checkField = __hasFieldByNameChanged(oldMarc, newMarc, "239", __stripValueLength10, /[^ahkeft\u00F8]/ );
                }

                if( checkField === true ) {
                    if (__hasSubfieldJustChanged(oldMarc, newMarc, __stripValueLength10, /245/, /a/)) {
                        if( newMarc.matchValue( /004/, /a/, /s/ ) ) {
                            if( !newMarc.existField( new MatchField( /245/, undefined, /n/ ) ) ) {
                                reason = "245a: 004a=s and !245n";
                                return result = true;
                            }
                        }
                        else if( newMarc.matchValue( /004/, /a/, /b/ ) ) {
                            if( !newMarc.existField( new MatchField( /245/, undefined, /g/ ) ) ) {
                                reason = "245a: 004a=b and !245g";
                                return result = true;
                            }
                        }
                        else {
                            reason = "245a";
                            return result = true;
                        }
                    }
                    if (__hasSubfieldJustChanged(oldMarc, newMarc, __stripValueLength10, /245/, /g/)) {
                        reason = "245g";
                        return result = true;
                    }
                    if (__hasSubfieldJustChanged(oldMarc, newMarc, __value, /245/, /m/)) {
                        reason = "245m";
                        return result = true;
                    }
                    if (__hasSubfieldJustChanged(oldMarc, newMarc, __stripValue, /245/, /n/)) {
                        reason = "245n";
                        return result = true;
                    }
                    if (__hasSubfieldJustChanged(oldMarc, newMarc, __stripValueLength10, /245/, /o/)) {
                        reason = "245o";
                        return result = true;
                    }
                    if (__hasSubfieldJustChanged(oldMarc, newMarc, __stripValueLength10, /245/, /y/)) {
                        reason = "245y";
                        return result = true;
                    }
                    if (__hasSubfieldJustChanged(oldMarc, newMarc, __stripValueLength10, /245/, /\u00E6/)) {
                        reason = "245\u00E6";
                        return result = true;
                    }
                    if (__hasSubfieldJustChanged(oldMarc, newMarc, __stripValueLength10, /245/, /\u00F8/)) {
                        reason = "245\u00F8";
                        return result = true;
                    }
                }
            }

            if (instance.fields.test("652")) {
                if (__hasSubfieldJustChanged(oldMarc, newMarc, __stripValueLength10, /652/, /a/)) {
                    reason = "652 Delfelt a changed";
                    return result = true;
                }
                if (__hasSubfieldJustChanged(oldMarc, newMarc, __stripValueLength10, /652/, /b/)) {
                    reason = "652 Delfelt b changed";
                    return result = true;
                }
                if (__hasSubfieldJustChanged(oldMarc, newMarc, __stripValue, new MatchField(/652/, undefined, /m|o/), /e/)) {
                    reason = "652m|o Delfelt e changed";
                    return result = true;
                }
                if (__hasSubfieldJustChanged(oldMarc, newMarc, __stripValue, new MatchField(/652/, undefined, /m|o/), /f/)) {
                    reason = "652m|o Delfelt f changed";
                    return result = true;
                }
                if (__hasSubfieldJustChanged(oldMarc, newMarc, __stripValue, new MatchField(/652/, undefined, /m|o/), /h/)) {
                    reason = "652m|o Delfelt h changed";
                    return result = true;
                }
                if (__hasSubfieldJustChanged(oldMarc, newMarc, __stripValue, /652/, /m/)) {
                    reason = "652m";
                    return result = true;
                }
                if (__hasSubfieldJustChanged(oldMarc, newMarc, __stripValue, /652/, /o/)) {
                    reason = "652o";
                    return result = true;
                }
            }

            return false;
        }
        finally {
            if( result === true ) {
                Log.info( "Classifications has changed because of: " + reason );
            }
            Log.trace( "Exit - ClassificationData.hasClassificationsChanged(): " + result );
        }
    }
    
    function updateClassificationsInRecord( instance, currentCommonMarc, updatingCommonMarc, libraryRecord ) {
        Log.trace( "Enter - ClassificationData.updateClassificationsInRecord()" );

        var result;
        try {
            Log.trace("    currentCommonMarc: " + currentCommonMarc);
            Log.trace("    updatingCommonMarc: " + updatingCommonMarc);
            Log.trace("    libraryRecord: " + libraryRecord);

            result = libraryRecord.clone();

            if (!hasClassificationData(instance, libraryRecord)) {
                currentCommonMarc.eachField(instance.fields, function (field) {
                    result.append(field);
                });
            }

            return result;
        }
        finally {
            Log.trace("Exit - ClassificationData.updateClassificationsInRecord(): " + result );
        }
    }
    
    function removeClassificationsFromRecord( instance, record ) {
        Log.trace( "Enter - ClassificationData.updateClassificationsInRecord()" );

        var result = undefined;
        try {
            Log.trace("    record: " + record);

            var result = new Record;
            record.eachField(/./, function (field) {
                if (!instance.fields.test(field.name)) {
                    result.append(field);
                }
            });

            return result;
        }
        finally {
            Log.trace("Exit - ClassificationData.updateClassificationsInRecord(): " + result);
        }
    }
    
    /**
     * Creates a Record from an existing record with all fields matching a 
     * fieldmatcher.
     * 
     * @param {Record}        record       Input record.
     * @param {RegExp|Object} fieldmatcher Field matcher.
     * 
     * @returns {Record} The new subset record.
     */
    function __createSubRecord( record, fieldmatcher ) {
        Log.trace( "Enter - ClassificationData.__createSubRecord()" );

        var result = undefined;
        try {
            Log.trace("    record: " + record);
            Log.trace("    fieldmatcher: " + fieldmatcher);
            var result = new Record;

            record.eachField(fieldmatcher, function (field) {
                result.append(field);
            });

            return result;
        }
        finally {
            Log.trace("Exit - ClassificationData.__createSubRecord(): " + result);
        }
    }
    
    function __hasRecordChanged( oldRecord, newRecord, valueFunc ) {
        Log.trace( "Enter - ClassificationData.__hasRecordChanged()" );

        var result = undefined;
        try {
            Log.trace("    oldRecord: " + oldRecord);
            Log.trace("    newRecord: " + newRecord);

            if (oldRecord.size() !== newRecord.size()) {
                return result = true;
            }

            result = false;

            oldRecord.eachField(/./, function (oldField) {
                if (result === true) {
                    return result;
                }

                var isFieldChanged = true;

                newRecord.eachField(/./, function (newField) {
                    if (!__hasFieldChanged(oldField, newField, valueFunc)) {
                        isFieldChanged = false;
                    }
                });

                if (isFieldChanged) {
                    result = true;
                }
            });

            return result;
        }
        finally {
            Log.trace( "Exit - ClassificationData.__hasRecordChanged(): " + result );
        }
    }
    
    function __hasFieldChanged( oldField, newField, valueFunc, ignoreSubfieldsMatcher ) {
        Log.trace( "Enter - ClassificationData.__hasFieldChanged()" );

        var result = undefined;
        try {
            Log.trace("    oldField: " + oldField);
            Log.trace("    newField: " + newField);
            Log.trace("    ignoreSubfieldsMatcher: " + ignoreSubfieldsMatcher);

            if (oldField === undefined && newField === undefined) {
                return result = false;
            }
            else if (oldField !== undefined && newField === undefined) {
                return result = true;
            }
            else if (oldField === undefined && newField !== undefined) {
                return result = true;
            }

            var msf = getMatchSubField(ignoreSubfieldsMatcher);
            if (ignoreSubfieldsMatcher === undefined) {
                msf = {
                    matchSubField: function (f, sf) {
                        return false;
                    }
                };
            }

            var result = false;
            oldField.eachSubField(/./, function (field, subfield) {
                if (result) {
                    return;
                }

                if (msf.matchSubField(field, subfield)) {
                    return;
                }

                var sfMatcher = {
                    matchSubField: function (f, sf) {
                        if( subfield.value === "" ) {
                            return true;
                        }

                        return sf.name === subfield.name && valueFunc(sf.value) === valueFunc(subfield.value);
                    }
                };

                if (!newField.exists(sfMatcher)) {
                    result = true;
                }
            });

            return result;
        }
        finally {
            Log.trace("Exit - ClassificationData.__hasFieldChanged(): " + result);
        }
    }
    
    function __hasFieldByNameChanged( oldMarc, newMarc, fieldname, valueFunc, ignoreSubfieldsMatcher ) {
        Log.trace( "Enter - ClassificationData.__hasFieldByNameChanged()" );

        var result = undefined;
        try {
            var oldField = undefined;
            var newField = undefined;

            if (oldMarc.existField(new RegExp(fieldname))) {
                oldField = oldMarc.field(fieldname);
            }
            if (newMarc.existField(new RegExp(fieldname))) {
                newField = newMarc.field(fieldname);
            }

            Log.debug( "fieldname: ", fieldname );
            Log.debug( "ignoreSubfieldsMatcher: ", uneval( ignoreSubfieldsMatcher ) );
            Log.debug( "oldField: ", oldField === undefined ? "undefined" : oldField.toString() );
            Log.debug( "newField: ", newField === undefined ? "undefined" : newField.toString() );

            return result = __hasFieldChanged(oldField, newField, valueFunc, ignoreSubfieldsMatcher) ||
                            __hasFieldChanged(newField, oldField, valueFunc, ignoreSubfieldsMatcher);
        }
        finally {
            Log.trace( "Exit - ClassificationData.__hasFieldByNameChanged(): ", result );
        }
    }
    
    function __hasSubfieldChangedMatcher( oldMarc, newMarc, fieldmatcher, subfieldmatcher, oldValueMatcher, newValueMatcher ) {
        Log.trace( "Enter - ClassificationData.__hasSubfieldChangedMatcher()" );

        var result = undefined;
        try {
            Log.trace("    oldMarc: " + oldMarc);
            Log.trace("    newMarc: " + newMarc);
            Log.trace("    fieldmatcher: " + fieldmatcher);
            Log.trace("    subfieldmatcher: " + subfieldmatcher);
            Log.trace("    oldValueMatcher: " + oldValueMatcher);
            Log.trace("    newValueMatcher: " + newValueMatcher);

            var result = oldMarc.matchValue(fieldmatcher, subfieldmatcher, oldValueMatcher) &&
                newMarc.matchValue(fieldmatcher, subfieldmatcher, newValueMatcher);

            return result;
        }
        finally {
            Log.trace("Exit - ClassificationData.__hasSubfieldChangedMatcher(): " + result);
        }
    }
    
    function __hasSubfieldJustChanged( oldMarc, newMarc, valueFunc, fieldmatcher, subfieldmatcher ) {
        Log.trace( "Enter - ClassificationData.__hasSubfieldJustChanged()" );

        var result = undefined;
        try {
            Log.trace("    oldMarc: " + oldMarc);
            Log.trace("    newMarc: " + newMarc);
            Log.trace("    fieldmatcher: " + fieldmatcher);
            Log.trace("    subfieldmatcher: " + subfieldmatcher);

            return result = valueFunc(oldMarc.getValue(fieldmatcher, subfieldmatcher)) !== valueFunc(newMarc.getValue(fieldmatcher, subfieldmatcher));
        }
        finally {
            Log.trace("Exit - ClassificationData.__hasSubfieldJustChanged():" + result);
        }
    }

    function __hasStripedValueChanged( a, b, len ) {
        Log.trace( "Enter - ClassificationData.__hasStripedValueChanged()" );

        var result = undefined;
        try {
            Log.trace("    a: " + a);
            Log.trace("    b: " + b);
            Log.trace("    len: " + len);

            a = __stripValue(a.substr(0, len));
            b = __stripValue(b.substr(0, len));

            return result = a !== b
        }
        finally {
            Log.trace("Exit - ClassificationData.__hasStripedValueChanged():" + result);
        }
    }
    
    function __value( v ) {
        return v;
    }
    
    function __stripValue( v ) {
        Log.trace( "Enter - ClassificationData.__stripValue()" );

        try {
            var Normalizer = Java.type("java.text.Normalizer");

            Log.trace("    v: " + v);

            v = v.replace(/\s|\[|\]|\u00A4/g, "");
            v = Normalizer.normalize( v, Normalizer.Form.NFD ).replaceAll( "[\\p{InCombiningDiacriticalMarks}]", "" );

            return v;
        }
        finally {
            Log.trace("Exit - ClassificationData.__stripValue():" + v);
        }
    }

    function __stripValueLength10( v ) {
        v = __stripValue( v.substr( 0, 10 ) );
        return v;
    }
    
    return {
        'create': create,
        'hasClassificationData': hasClassificationData,
        'hasClassificationsChanged': hasClassificationsChanged,
        'updateClassificationsInRecord': updateClassificationsInRecord,
        'removeClassificationsFromRecord': removeClassificationsFromRecord,
        '__stripValue': __stripValue
    };
    
}();
