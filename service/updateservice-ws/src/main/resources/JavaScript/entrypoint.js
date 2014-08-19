//-----------------------------------------------------------------------------
use( "MarcFactory" );
use( "TemplateContainer" );
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
    return name === "dataio" || TemplateContainer.get( name ) !== undefined;
}

/**
 * Validates a record with a given template.
 * 
 * @param {String} templateName The name of the template to use.
 * @param {String} record       The record to validator as a json.
 * 
 * @return {String} A json string with an array of validation errors.
 */
function validateRecord( templateName, record ) {
    if( templateName === "dataio" ) {
        return JSON.stringify( [] );
    };
    
    var rec = JSON.parse( record );
    var templateProvider = function() { 
        return TemplateContainer.get( templateName ); 
    };
    
    var result = Validator.validateRecord( rec, templateProvider );    
    return JSON.stringify( result );
}

//-----------------------------------------------------------------------------
//                  Entry points for update EJB
//-----------------------------------------------------------------------------

/**
 * Checks if the classifications has changed between two records.
 * 
 * @param {String} oldRecord The old record as a json.
 * @param {String} newRecord The new record as a json.
 * 
 * @return {Boolean} true if the classifications has changed, false otherwise.
 */
function hasClassificationsChanged( oldRecord, newRecord ) {
    return false;
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
    
}
