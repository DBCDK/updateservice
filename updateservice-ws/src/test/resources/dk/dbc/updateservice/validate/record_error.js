//-----------------------------------------------------------------------------
function checkTemplate( name ) {
    return true;
}

//-----------------------------------------------------------------------------
function validateRecord( templateName, record ) {
    return JSON.stringify( [
        {
            type: "CDTOValidationResponseSuccessEntryCollectionRecordUnstructuredValidationError",
            params: {
                url: "http://url.dbc.dk/path/doc.html",
                message: "Problemer med posten."
            }
        }
    ] );
}
