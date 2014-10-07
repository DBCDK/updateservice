//-----------------------------------------------------------------------------
/**
 * This core module gives direct access to RawRepo thought the UpdateService.
 * It can only be used with the web service.
 * 
 * To use it add this folder to the modules path in the setup of the JS 
 * environment.
 */

//-----------------------------------------------------------------------------
use( "Log" );
use( "MarcFactory" );

//-----------------------------------------------------------------------------
EXPORTED_SYMBOLS = [ 'RawRepoCore' ];

//-----------------------------------------------------------------------------
var RawRepoCore = function() {
    function recordExists( recordId, libraryNo ) {
    	Log.trace( "Enter RawRepoCore.recordExists()" );
    
    	try {
	    	var result = new Packages.dk.dbc.updateservice.javascript.UpdaterRawRepo.recordExists( recordId, libraryNo );
	    	
	    	Log.trace( "Exit RawRepoCore.recordExists(): " + result );
	        return result;
    	}
    	catch( ex ) {
			Log.warn( ex );
			throw ex;
		}
    }

    function fetchRecord( recordId, libraryNo ) {
    	Log.trace( "Enter RawRepoCore.fetchRecord()" );
    
    	try {
    		var record = new Packages.dk.dbc.updateservice.javascript.UpdaterRawRepo.fetchRecord( recordId, libraryNo ); 
	    	var result = new Record();
	    	for( var i = 0; i < record.getFields().size(); i++ ) {
	    		var recField = record.getFields().get( i );
	    		var field = new Field( recField.getName(), recField.getIndicator() );
	    		for( var k = 0; k < recField.getSubfields().size(); k++ ) {
	    			var subfield = recField.getSubfields().get( k );
	    			field.append( new Subfield( subfield.getName(), subfield.getValue() ) );
	    		}
	    		
	    		result.append( field );
	    	}
	    	
	    	Log.trace( "Exit RawRepoCore.fetchRecord(): " + result );
	        return result;
    	}
    	catch( ex ) {
			Log.warn( ex );
			throw ex;
		}
    }
    
    return {
    	'recordExists': recordExists,
    	'fetchRecord': fetchRecord
    }
}();
