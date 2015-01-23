function hasClassificationData( marc ) {
    return true;
}

function hasClassificationsChanged( oldRecord, newRecord ) {
    return false;
}

function createLibraryExtendedRecord( dbcRecord, libraryId ) {
    return JSON.stringify( [] );
}

function updateLibraryExtendedRecord( dbcRecord, libraryRecord ) {
    return libraryRecord;
}

function correctLibraryExtendedRecord( dbcRecord, libraryRecord ) {
    return libraryRecord;
}

function changeUpdateRecordForUpdate( dbcRecord, userId, groupId ) {
    return dbcRecord;
}
