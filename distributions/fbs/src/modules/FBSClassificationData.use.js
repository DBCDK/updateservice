//-----------------------------------------------------------------------------
//use( "ClassificationData" );
//use( "Marc" );
//use( "MarcClasses" );
//use( "Log" );
//use( "RecordLookupField" );

//-----------------------------------------------------------------------------
EXPORTED_SYMBOLS = [ 'FBSClassificationData' ];

//-----------------------------------------------------------------------------
var FBSClassificationData = function() {
    function create( fieldsRegExp )  {
        return {
            fields: fieldsRegExp
        }
    }

    function hasClassificationData( instance, marc ) {
        return ClassificationData.hasClassificationData( instance, marc );
    }

    function hasClassificationsChanged(instance, oldMarc, newMarc) {
        Log.trace("Enter - FBSClassificationData.hasClassificationsChanged( ", oldMarc, ", ", newMarc, " )");
        Log.info("THL Enter - FBSClassificationData.hasClassificationsChanged( ", oldMarc, ", ", newMarc, " )");

        var result = null;
        try {
            var lookup = RecordLookupField.createFromRecord(oldMarc, instance.fields);
            Log.info("THL lookup:\n" + JSON.stringify(lookup, undefined, 3));

            for (var i = 0; i < newMarc.numberOfFields(); i++) {
                var newField = newMarc.field(i);

                if (!instance.fields.test(newField.name)) {
                    continue;
                }
                if (!RecordLookupField.containsField(lookup, newField)) {
                    Log.debug("Unable to find field: ", newField);
                    Log.debug("Lookup record:\n", oldMarc);
                    return result = true;
                }
            }
            return result = false;
        } finally {
            Log.trace("Exit - FBSClassificationData.hasClassificationsChanged(): ", result);
        }
    }

    function updateClassificationsInRecord( instance, currentCommonMarc, updatingCommonMarc, libraryRecord ) {
        return ClassificationData.updateClassificationsInRecord( instance, currentCommonMarc, updatingCommonMarc, libraryRecord );
    }

    function removeClassificationsFromRecord( instance, record ) {
        return ClassificationData.removeClassificationsFromRecord( instance, record );
    }

    return {
        'create': create,
        'hasClassificationData': hasClassificationData,
        'hasClassificationsChanged': hasClassificationsChanged,
        'updateClassificationsInRecord': updateClassificationsInRecord,
        'removeClassificationsFromRecord': removeClassificationsFromRecord
    };

}();
