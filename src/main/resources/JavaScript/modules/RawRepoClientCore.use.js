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
EXPORTED_SYMBOLS = [ 'RawRepoClientCore' ];

//-----------------------------------------------------------------------------
var RawRepoClientCore = function() {
	function recordExists( recordId, libraryNo ) {
    	Log.trace( "Enter RawRepoClientCore.recordExists()" );

		var result = false;
    	try {
	    	result = new Packages.dk.dbc.updateservice.javascript.UpdaterRawRepo.recordExists( recordId, libraryNo ).booleanValue();
	        return result;
    	}
    	catch( ex ) {
			Log.warn( ex );
			throw ex;
		}
		finally {
			Log.trace( "Exit RawRepoClientCore.recordExists(): ", result );
		}
    }

    function fetchRecord( recordId, libraryNo ) {
    	Log.trace( "Enter RawRepoClientCore.fetchRecord()" );
    
    	try {
    		var record = new Packages.dk.dbc.updateservice.javascript.UpdaterRawRepo.fetchRecord( recordId, libraryNo ); 
	    	var result = __convertRecord( record );

	    	Log.trace( "Exit RawRepoClientCore.fetchRecord(): " + result );
	        return result;
    	}
    	catch( ex ) {
			Log.warn( ex );
			throw ex;
		}
		finally {
			Log.trace( "Exit RawRepoClientCore.fetchRecord()" );
		}
    }

	function getRelationsChildren( recordId, libraryNo ) {
		Log.trace( "Enter RawRepoClientCore.getRelationsChildren()" );

		try {
			var result = [];

			var records = new Packages.dk.dbc.updateservice.javascript.UpdaterRawRepo.getRelationsChildren( recordId, libraryNo );
			for( var i = 0; i < records.size(); i++ ) {
				result.push( __convertRecord( records.get( i ) ) );
			}

			return result;
		}
		catch( ex ) {
			Log.warn( ex );
			throw ex;
		}
		finally {
			Log.trace( "Exit RawRepoClientCore.getRelationsChildren()" );
		}
	}

	/**
	 * Converts a Java record type to a Record.
	 *
	 * @param javaRecord The Java record to convert.
	 *
	 * @returns {Record} The result.
	 *
	 * @private
	 */
	function __convertRecord( javaRecord ) {
		Log.trace( "Enter - RawRepoClientCore.__convertRecord" );

		try {
			var result = new Record();
			for( var i = 0; i < javaRecord.getFields().size(); i++ ) {
				var recField = javaRecord.getFields().get( i );
				var field = new Field( recField.getName(), recField.getIndicator() );
				for( var k = 0; k < recField.getSubfields().size(); k++ ) {
					var subfield = recField.getSubfields().get( k );
					field.append( new Subfield( subfield.getName(), subfield.getValue() ) );
				}

				result.append( field );
			}

			return result;
		}
		finally {
			Log.trace( "Exit - RawRepoClientCore.__convertRecord" );
		}
	}

    return {
    	'recordExists': recordExists,
    	'fetchRecord': fetchRecord,
		'getRelationsChildren': getRelationsChildren
    }
}();