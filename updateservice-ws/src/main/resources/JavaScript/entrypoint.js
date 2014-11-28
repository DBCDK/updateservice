//-----------------------------------------------------------------------------
use( "ClassificationData" );
use( "Marc" );
use( "TemplateContainer" );
use( "UpdaterEntryPoint" );
use( "Validator" );

//-----------------------------------------------------------------------------
//                  Entry points for validator EJB
//-----------------------------------------------------------------------------

/**
 * Gets the names of the templates as an Array
 * 
 * @return {JSON} A json with the names of the templates. The names is returned
 *                as an Array.
 */
function getValidateSchemas() {
    return JSON.stringify( TemplateContainer.getTemplateNames() ); 
}

/**
 * Checks if a template exists by its name.
 * 
 * @param {String} name The name of the template.
 * 
 * @return {Boolean} true if the template exists, false otherwise.
 */
function checkTemplate( name ) {
    Log.trace( StringUtil.sprintf( "Enter - checkTemplate( '%s' )", name ) );

    var result = null;
    try {
        result = name === "dataio" || TemplateContainer.getUnoptimized( name ) !== undefined;
        return result;
    }
    finally {
        Log.trace( "Exit - checkTemplate(): " + result );
    }
}

/**
 * Validates a record with a given template.
 * 
 * @param {String} templateName The name of the template to use.
 * @param {String} record       The record to validator as a json.
 * 
 * @return {String} A json string with an array of validation errors.
 */
function validateRecord( templateName, record, settings ) {
    Log.trace( "Enter - validateRecord()" );

    try {
        var rec = JSON.parse( record );
        var templateProvider = function () {
            return TemplateContainer.get(templateName);
        };

        var result = null;

        try {
            result = Validator.validateRecord( rec, templateProvider, settings );
        }
        catch( ex ) {
            result = [ ValidateErrors.recordError( "", StringUtil.sprintf( "Systemfejl ved validering: %s", ex ) ) ];
        }

        return JSON.stringify( result );
    }
    finally {
        Log.trace( "Exit - validateRecord()" );
    }
}

//-----------------------------------------------------------------------------
//                  Entry points for update EJB
//-----------------------------------------------------------------------------

function hasClassificationData( marc ) {
    return UpdaterEntryPoint.hasClassificationData( marc );    
}

/**
 * Checks if the classifications has changed between two records.
 * 
 * @param {String} oldRecord The old record as a json.
 * @param {String} newRecord The new record as a json.
 * 
 * @return {Boolean} true if the classifications has changed, false otherwise.
 */
function hasClassificationsChanged( oldRecord, newRecord ) {
    return UpdaterEntryPoint.hasClassificationsChanged( oldRecord, newRecord );
}

/**
 * Creates a new library extended record based on a DBC record.
 * 
 * @param {String} dbcRecord The DBC record as a json.
 * @param {int}    libraryId Library id for the local library.
 * 
 * @return {String} A json with the new record.
 */
function createLibraryExtendedRecord( dbcRecord, libraryId ) {
    return UpdaterEntryPoint.createLibraryExtendedRecord( dbcRecord, libraryId );
}

/**
 * Updates a library extended record with the classifications from 
 * a DBC record.
 * 
 * @param {String} dbcRecord The DBC record as a json.
 * @param {String} libraryRecord The library record to update as a json.
 * 
 * @return {String} A json with the updated record.
 */
function updateLibraryExtendedRecord( dbcRecord, libraryRecord ) {
    return UpdaterEntryPoint.updateLibraryExtendedRecord( dbcRecord, libraryRecord );
}

function correctLibraryExtendedRecord( dbcRecord, libraryRecord ) {
    return UpdaterEntryPoint.correctLibraryExtendedRecord( dbcRecord, libraryRecord );    
}

function changeUpdateRecordForUpdate( dbcRecord ) {
    return UpdaterEntryPoint.changeUpdateRecordForUpdate( dbcRecord );
}
