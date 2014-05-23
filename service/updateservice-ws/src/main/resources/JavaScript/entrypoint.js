//-----------------------------------------------------------------------------
use( "RecordRules" );
use( "TemplateContainer" );
use( "Validator" );

//-----------------------------------------------------------------------------
// TODO: JSDoc
function checkTemplate( name ) {
    return name === "dataio" || TemplateContainer.get( name ) !== undefined;
}

//-----------------------------------------------------------------------------
// TODO: JSDoc
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
