//-----------------------------------------------------------------------------
/**
 * 
 */

// -----------------------------------------------------------------------------
use( "Log" );
use( "StringUtil" );
use( "ValidationUtil" );
use( "ValueCheck" );

// -----------------------------------------------------------------------------
EXPORTED_SYMBOLS = [ 'RawRepoClientCore' ];

// -----------------------------------------------------------------------------
/**
 * This module implements access to a raw-repo of records that is saved locally
 * in a Array.
 * 
 * The purpose of this core module is to be able to use the RawRepo module for
 * unittests in a JS environment without access to an actual rawrepo.
 * 
 * @namespace
 * @name RawRepoClientCore
 */
var RawRepoClientCore = function() {
    /**
     * Hash map of records.
     * 
     * The key is generated by concat of 001a and 001b in the record with a ":"
     * in between.
     */
    var records = {};

    /**
     * Adds a new record to the local hashmap of records.
     * 
     * This function is only used in unittests to be able to run tests outside a
     * Java EE environment.
     * 
     * @param {Record} marcRecord Record to add it this "rawrepo".
     * 
     * @name RawRepoCore#addRecord
     */
    function addRecord( marcRecord ) {
        Log.trace( "Enter - RawRepoClientCore.addRecord" );

        try {
            var key = generateKey(marcRecord.getValue(/001/, /a/),
                marcRecord.getValue(/001/, /b/));

            Log.debug( "Add record [", key, "]" );
            records[key] = marcRecord;
        }
        finally {
            Log.trace( "Exit - RawRepoClientCore.addRecord" );
        }
    }

    /**
     * Clears the local hashmap of records.
     * 
     * This function is only used in unittests to be able to run tests outside a
     * Java EE environment.
     * 
     * @name RawRepoCore#clear
     */
    function clear() {
        records = {};
    }

    /**
     * Checks if a record exists in the local Array.
     * 
     * @param {String}
     *            recordId Record id.
     * @param {Number}
     *            libraryNo Library no.
     * 
     * @return {Boolean} true if the record exists, false otherwise.
     * 
     * @name RawRepoCore#recordExists
     */
    function recordExists( recordId, libraryNo ) {
        __checkRecordIds( recordId, libraryNo );

        return records[ generateKey( recordId, libraryNo ) ] !== undefined;
    }

    /**
     * Fetches a record from the internal Array of records.
     * 
     * @param {String}
     *            recordId Record id.
     * @param {Number}
     *            libraryNo Lib rary no.
     * 
     * @return {Record} The record from the rawrepo if it can be founded,
     *         undefined otherwise.
     * 
     * @name RawRepoCore#fetchRecord
     */
    function fetchRecord( recordId, libraryNo ) {
        __checkRecordIds( recordId, libraryNo );

        return records[ generateKey( recordId, libraryNo ) ];
    }

    /**
     * Returns all records that points to a specific record.
     *
     * @param {String}
     *          recordId  The record id of the record, that the records should be children of.
     * @param {Number}
     *          libraryNo The library number of the record, that the records should be children of.
     *
     * @returns {Array}
     *          Array of found childrens or an empty array if none could be found.
     *
     * @name RawRepoCore#getRelationsChildren
     */
    function getRelationsChildren( recordId, libraryNo ) {
        Log.trace( "Enter - RawRepoClientCore.getRelationsChildren" );

        try {
            __checkRecordIds( recordId, libraryNo );

            var result = [];

            Log.debug( "Trying to lookup children records for [", recordId, ":", libraryNo, "]" );
            for( var key in records ) {
                var record = records[ key ];

                var parentId = record.getValue( /014/, /a/ );
                var recid = record.getValue( /001/, /a/ );
                var libno = record.getValue( /001/, /b/ );

                Log.debug( "Checking record [", recid, ":", libno, "] -> [", parentId, ":", libno, "]" );
                if( recordId === parentId && libraryNo === libno ) {
                    Log.debug( "Found children record [", parentId, ":", libno, "]" );
                    result.push( record );
                }
            }

            return result;
        }
        finally {
            Log.trace( "Exit - RawRepoClientCore.getRelationsChildren" );
        }
    }

    /**
     * Generates a hash key for a record.
     * 
     * The key is generated by concat of recordId and libraryId a ":" in
     * between.
     * 
     * @param {String}
     *            recordId The record id to use in the key.
     * @param {String}
     *            libraryNo The library no to use in the key.
     * 
     * @return {String} The key as a string.
     * @name RawRepoClientCore#generateKey
     */
    function generateKey( recordId, libraryNo ) {
        return recordId + ":" + libraryNo;
    }

    function __checkRecordIds( recordId, agencyId ) {
        ValueCheck.checkThat( "recordId", recordId ).type( 'string' );
        ValueCheck.checkThat( "agencyId", agencyId ).type( 'string' );

        if( !ValidationUtil.isNumber( agencyId ) ) {
            throw "The value '" + agencyId + "' is not a number";
        }
    }

    return {
        'addRecord': addRecord,
        'clear': clear,
        'recordExists': recordExists,
        'fetchRecord': fetchRecord,
        'getRelationsChildren': getRelationsChildren
    };
}();