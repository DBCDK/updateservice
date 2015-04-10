//-----------------------------------------------------------------------------
use( "Log" );
use( "Marc" );
use( "RawRepoClient" );
use( "UpdateConstants" );
use( "ValidateErrors" );

//-----------------------------------------------------------------------------
EXPORTED_SYMBOLS = [ 'NoteAndSubjectExtentionsHandler' ];

//-----------------------------------------------------------------------------
/**
 * Module to handle the authentication of changing notes or subjects by a FBS
 * library.
 *
 * @namespace
 * @name NoteAndSubjectExtentionsHandler
 */
var NoteAndSubjectExtentionsHandler = function() {
    /**
     * Authenticates extentions in a record.
     *
     * @param record Record
     * @param groupId Group id.
     *
     * @returns {Array} Array of authentication errors. We use the same structure
     *                  as for validation errors.
     *
     * @name NoteAndSubjectExtentionsHandler#authenticateExtentions
     */
    function authenticateExtentions( record, groupId ) {
        Log.trace( StringUtil.sprintf( "Enter - NoteAndSubjectExtentionsHandler.authenticateExtentions( %s, %s )", uneval( record ), uneval( groupId ) ) );

        var result = null;
        try {
            var recId = record.getValue(/001/, /a/);
            if (!RawRepoClient.recordExists(recId, UpdateConstants.RAWREPO_COMMON_AGENCYID)) {
                return result = [];
            }

            var curRecord = RawRepoClient.fetchRecord(recId, UpdateConstants.RAWREPO_COMMON_AGENCYID);
            curRecord.field( "001" ).subfield( "b" ).value = record.getValue( /001/, /b/ );
            if (!isNationalCommonRecord(curRecord)) {
                return result = [];
            }

            var authResult = [];
            record.eachField(/./, function (field) {
                if (!UpdateConstants.EXTENTABLE_NOTE_FIELDS.test(field.name)) {
                    if (__isFieldChangedInOtherRecord(field, curRecord)) {
                        authResult.push(ValidateErrors.recordError("", StringUtil.sprintf("Brugeren '%s' har ikke ret til at rette/tilføje feltet '%s' i posten '%s'", groupId, field.name, recId)));
                    }
                }
            });

            curRecord.eachField(/./, function (field) {
                if (!UpdateConstants.EXTENTABLE_NOTE_FIELDS.test(field.name)) {
                    if (__isFieldChangedInOtherRecord(field, record)) {
                        if( curRecord.count( field.name ) !== record.count( field.name ) ) {
                            authResult.push(ValidateErrors.recordError("", StringUtil.sprintf("Brugeren '%s' har ikke ret til at slette feltet '%s' i posten '%s'", groupId, field.name, recId)));
                        }
                    }
                }
            });

            return result = authResult;
        }
        finally {
            Log.trace( "Exit - NoteAndSubjectExtentionsHandler.authenticateExtentions(): ", result );
        }
    }

    /**
     * Changes the content of a record for update.
     *
     * @param record Record
     * @param userId User id - not used.
     * @param groupId Group id.
     *
     * @returns {Record} A new record with the new content.
     *
     * @name NoteAndSubjectExtentionsHandler#changeUpdateRecordForUpdate
     */
    function recordDataForRawRepo( record, userId, groupId ) {
        Log.trace( StringUtil.sprintf( "Enter - NoteAndSubjectExtentionsHandler.recordDataForRawRepo( %s, %s, %s )", record, userId, groupId ) );

        var result = null;
        try {
            var recId = record.getValue(/001/, /a/);

            if (!RawRepoClient.recordExists(recId, UpdateConstants.RAWREPO_COMMON_AGENCYID)) {
                return result = record;
            }

            var curRecord = RawRepoClient.fetchRecord(recId, UpdateConstants.RAWREPO_COMMON_AGENCYID);
            if (!isNationalCommonRecord(curRecord)) {
                return result = record;
            }

            record.eachField(/./, function (field) {
                if (UpdateConstants.EXTENTABLE_NOTE_FIELDS.test(field.name)) {
                    field.insert( 0, "&", groupId );
                }
            });

            return result = record;
        }
        finally {
            Log.trace( "Exit - NoteAndSubjectExtentionsHandler.recordDataForRawRepo():", result );
        }
    }

    /**
     * Checks if this record is a national common record.
     *
     * @param record Record.
     *
     * @returns {Boolean} True / False.
     *
     * @name NoteAndSubjectExtentionsHandler#isNationalCommonRecord
     */
    function isNationalCommonRecord( record ) {
        Log.trace( StringUtil.sprintf( "Enter - NoteAndSubjectExtentionsHandler.isNationalCommonRecord( %s )", record ) );

        var result = null;
        try {
            if( !record.matchValue( /996/, /a/, /DBC/ ) ) {
                return result = false;
            }

            for (var i = 0; i < record.size(); i++) {
                if (__isFieldNationalCommonRecord(record.field(i))) {
                    return result = true;
                }
            }

            return result = false;
        }
        finally {
            Log.trace( "Exit - NoteAndSubjectExtentionsHandler.isNationalCommonRecord(): ", result );
        }
    }

    /**
     * Checks if a field specifies that a record is a national common record.
     *
     * @param {Field} Field.
     *
     * @returns {Boolean} True / False.
     *
     * @private
     * @name NoteAndSubjectExtentionsHandler#__isFieldNationalCommonRecord
     */
    function __isFieldNationalCommonRecord( field ) {
        Log.trace( StringUtil.sprintf( "Enter - NoteAndSubjectExtentionsHandler.__isFieldNationalCommonRecord( %s )", field ) );

        var result = null;
        try {
            var fieldName = field.name.toString() + '';
            if ( fieldName !== "032") {
                result = StringUtil.sprintf( "x1: '%s': false", fieldName );
                return false;
            }

            if (!field.exists(/a/)) {
                result = "x2: false";
                return false;
            }

            result = "x3: " + !field.matchValue(/x/, /BKM*|NET*|SF*/);
            return !field.matchValue(/x/, /BKM*|NET*|SF*/);
        }
        finally {
            Log.trace( "Exit - NoteAndSubjectExtentionsHandler.__isFieldNationalCommonRecord(): ", result );
        }
    }

    /**
     * Checks if a field is changed compared to another record.
     *
     * @param {Field} Field.
     * @param {Record} record.
     *
     * @returns {Boolean} True / False.
     *
     * @private
     * @name NoteAndSubjectExtentionsHandler#__isFieldChangedInOtherRecord
     */
    function __isFieldChangedInOtherRecord( field, record ) {
        Log.trace( StringUtil.sprintf( "Enter - NoteAndSubjectExtentionsHandler.__isFieldChangedInOtherRecord( %s, %s )", field, record ) );

        var result = null;
        try {
            var compareRecord = record;
            if( field.name === "001" ) {
                compareRecord = record.clone();
                if( field.exists( /c/ ) ) {
                    RecordUtil.addOrReplaceSubfield( compareRecord, "001", "c", field.getValue( /c/ ) );
                }
                if( field.exists( /d/ ) ) {
                    RecordUtil.addOrReplaceSubfield( compareRecord, "001", "d", field.getValue( /d/ ) );
                }
            }

            for( var i = 0; i < compareRecord.size(); i++ ) {
                if( __isFieldsEqual( field, compareRecord.field( i ) ) ) {
                    return result = false;
                }
            }

            return result = true;
        }
        finally {
            Log.trace( "Exit - NoteAndSubjectExtentionsHandler.__isFieldChangedInOtherRecord(): ", result );
        }
    }

    /**
     * Checks if 2 fields contains the same information.
     *
     * @param {Field} srcField
     * @param {Field} destField
     *
     * @returns {Boolean} True / False.
     *
     * @private
     * @name NoteAndSubjectExtentionsHandler#__isFieldsEqual
     */
    function __isFieldsEqual( srcField, destField ) {
        Log.trace( StringUtil.sprintf( "Enter - NoteAndSubjectExtentionsHandler.__isFieldsEqual( %s, %s )", srcField, destField ) );

        var result = null;
        try {
            Log.debug( "srcField.name: '", srcField.name, "'" );
            Log.debug( "typeof( srcField.name ): ", typeof( srcField.name ) );
            Log.debug( "srcField.name === \"001\": ", srcField.name === "001" );
            Log.debug( "srcField.name === '001': ", srcField.name === '001' );

            if (srcField.name !== destField.name) {
                return result = false;
            }

            if (srcField.indicator !== destField.indicator) {
                return result = false;
            }

            if (srcField.size() !== destField.size()) {
                return result = false;
            }

            var srcSubfields = [];
            var destSubfields = [];
            for (var i = 0; i < srcField.size(); i++) {
                srcSubfields.push(srcField.subfield(i));
                destSubfields.push(destField.subfield(i));
            }

            srcSubfields.sort(__sortSubfield);
            destSubfields.sort(__sortSubfield);

            for (i = 0; i < srcSubfields.length; i++) {
                if (srcSubfields[i].name !== destSubfields[i].name) {
                    return result = false;
                }
                if (srcSubfields[i].value !== destSubfields[i].value) {
                    return result = false;
                }
            }

            return true;
        }
        finally {
            Log.trace( "Exit - NoteAndSubjectExtentionsHandler.__isFieldsEqual(): ", result );
        }
    }

    /**
     * Compares two subfields are order by name and value.
     *
     * @param {Subfield} a
     * @param {Subfield} b
     *
     * @returns {Integer} Compare integer.
     *
     * @private
     * @name NoteAndSubjectExtentionsHandler#__sortSubfield
     */
    function __sortSubfield( a, b ) {
        Log.trace( StringUtil.sprintf( "Enter - NoteAndSubjectExtentionsHandler.__sortSubfield( %s, %s )", a, b ) );

        var result = null;
        try {
            if (a.name < b.name) {
                return result = -1;
            }
            else if (a.name > b.name) {
                return result = 1;
            }
            else {
                if (a.value < b.value) {
                    return result = -1;
                }
                else if (a.value > b.value) {
                    return result = 1;
                }
            }

            return result = 0;
        }
        finally {
            Log.trace( "Exit - NoteAndSubjectExtentionsHandler.__sortSubfield(): ", result );
        }
    }

    return {
        'authenticateExtentions': authenticateExtentions,
        'recordDataForRawRepo': recordDataForRawRepo,
        'isNationalCommonRecord': isNationalCommonRecord
    }

}();
