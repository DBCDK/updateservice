function getTestValue( record ) {
    for( var i = 0; i < record.fields.length; i++ ) {
        var field = record.fields[ i ];

        if( field.name === "tst" ) {
            for( var j = 0; j < field.subfields.length; j++ ) {
                var subfield = field.subfields[ j ];
                if( subfield.name === "t" ) {
                    return subfield.value;
                }
            }
        }
    }

    return "";
}

function getValidateSchemas() {
    return JSON.stringify( [
        {
            schemaName: "name",
            schemaInfo: "info"
        }
    ] );
}

function checkTemplate( name, settings ) {
    return name !== "Unknown-Schema";
}

function validateRecord( templateName, record, settings ) {
    var rec = JSON.parse( record );

    var result = [];
    switch( getTestValue( rec ) ) {
        case "validate_error": {
            result = [ {
                type: "ERROR",
                params: {
                    url: "http://www.url.dk",
                    fieldno: -1,
                    subfieldno: -1,
                    message: "message"
                }
            } ];
            break;
        }
    }

    return JSON.stringify( result );
}
