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
			Log.fatal( ex );
			throw ex;
		}
    }

    function fetchRecord( recordId, libraryNo ) {
    	Log.trace( "Enter RawRepoCore.fetchRecord()" );
    
    	try {
	    	var result = new Packages.dk.dbc.updateservice.javascript.UpdaterRawRepo.fetchRecord( recordId, libraryNo );
	    	
	    	Log.trace( "Exit RawRepoCore.fetchRecord(): " + result );
	        return result;
    	}
    	catch( ex ) {
			Log.fatal( ex );
			throw ex;
		}
    }
}
