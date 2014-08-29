//-----------------------------------------------------------------------------
use( "MarcClasses" );
use( "UnitTest" );
use( "Log" );

//-----------------------------------------------------------------------------
EXPORTED_SYMBOLS = [ 'MarcFactory' ];

//-----------------------------------------------------------------------------
/**
 * Module with factory functions to create Record, Field and Subfield objects.
 * 
 * @namespace
 * @name MarcFactory
 * 
 */
var MarcFactory = function() {
    /**
     * Create a new Record from a json string.
     * 
     * @param {String}   json The json string.
     * 
     * @return {Record} The new Record object.
     * 
     * @name MarcFactory#createRrecordFromJson
     */
    function createRecordFromJson( json ) {
        Log.info( "Enter - MarcFactory.createRecordFromJson()" )
        
        var record = JSON.parse( json );
        var result = new Record;
        
        if( record.fields === undefined ) {
            Log.info( "Exit - MarcFactory.createRecordFromJson() - " + result );
            return result;
        }
        
        for( var i = 0; i < record.fields.length; i++ ) {
            result.append( __createFieldFromObject( record.fields[ i ] ) );
        }
        
        Log.info( "Exit - MarcFactory.createRecordFromJson() - " + result );
        return result;
    }
    
    function createJsonFromRecord( record ) {
        Log.info( "Enter - MarcFactory.createJsonFromRecord()" )

        var jsonObject = {
            fields: []
        };
        
        record.eachField( /./, function( field ) {
            var jsonField = {
                name: field.name,
                indicator: field.indicator,
                subfields: []
            };
            
            field.eachSubField( /./, function( field, subfield ) {
                jsonField.subfields.push( { name: subfield.name, value: subfield.value } );
            });
            
            jsonObject.fields.push( jsonField );
        } );
        
        var result = JSON.stringify( jsonObject );
        
        Log.info( "Exit - MarcFactory.createJsonFromRecord() - " + result );
        return result;
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
    function createSubRecord( record, fieldmatcher ) {
        Log.info( "Enter - MarcFactory.createSubRecord()" );
        Log.info( "    record: " + record );
        Log.info( "    fieldmatcher: " + fieldmatcher );
        var result = new Record;
        
        record.eachField( fieldmatcher, function( field ) { 
            result.append( field ); 
        } );
        
        Log.info( "Exit - MarcFactory.createSubRecord(): " + result );
        return result;
    }
    
    /**
     * Creates a new Field from an field object.
     * 
     * @param {Object} field Object with an array (named subfields) of subfield 
     *                       objects. name and indicator properties are also 
     *                       required.
     * @returns {Field} A new Field that is equvalent to field.
     */
    function __createFieldFromObject( field ) {
        var result = new Field( field.name, field.indicator );
        
        if( field.subfields === undefined ) {
            return result;
        }
        
        for( var i = 0; i < field.subfields.length; i++ ) {
            var subfield = field.subfields[ i ];
            result.append( new Subfield( subfield.name, subfield.value ) );
        }
        
        return result;
    }
    
    return {
        'createRecordFromJson': createRecordFromJson,
        'createJsonFromRecord': createJsonFromRecord,
        'createSubRecord': createSubRecord
    };
}();
