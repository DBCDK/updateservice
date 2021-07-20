/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.common.records.utils.RecordContentTransformer;
import dk.dbc.rawrepo.Record;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.Properties;

/**
 * This action stores a record in the rawrepo and marks it as deleted.
 * <p>
 * This action is used when we need to delete different kind of records.
 * </p>
 * <p>
 * <string>Note:</string> This action does not handle special cases when
 * we need to delete enrichments, common record or other special cases.
 * These cases must be handled by other actions.
 * </p>
 */
public class DeleteRecordAction extends StoreRecordAction {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(DeleteRecordAction.class);

    public DeleteRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord marcRecord) {
        super(globalActionState, properties, marcRecord);
        setName(DeleteRecordAction.class.getSimpleName());
    }

    /**
     * Returns the record that should be stored in the rawrepo.
     * <p>
     * If the record is a simple delete record containing only 001 and 004 then this function gets the full record
     * and returns that record with 004 *r = d.
     * This is done in order to not remove all the fields of the record.
     * </p>
     *
     * @return The record to store.
     */
    @Override
    public MarcRecord recordToStore() throws UpdateException, UnsupportedEncodingException {
        LOGGER.entry();
        MarcRecord result = null;
        try {
            result = loadCurrentRecord();
            MarcRecordWriter currentWriter = new MarcRecordWriter(result);
            MarcRecordReader currentReader = new MarcRecordReader(result);
            if (currentReader.getField("004") == null) {
                // This is done because the database by historical reasons are pestered with
                // a large number of records without field 004
                currentWriter.copyFieldFromRecord("004", marcRecord);
            }
            currentWriter.markForDeletion();
            currentWriter.setChangedTimestamp();

            return result;
        } finally {
            LOGGER.exit(result);
        }
    }

    /**
     * Factory method to create a DeleteRecordAction.
     */
    public static DeleteRecordAction newDeleteRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord record) {
        LOGGER.entry(globalActionState, record);
        try {
            DeleteRecordAction deleteRecordAction = new DeleteRecordAction(globalActionState, properties, record);

            MarcRecordReader reader = new MarcRecordReader(record);

            deleteRecordAction.setMimetype(getMarcXChangeMimetype(reader.getAgencyIdAsInt()));

            return deleteRecordAction;
        } finally {
            LOGGER.exit();
        }
    }

    private MarcRecord loadCurrentRecord() throws UpdateException, UnsupportedEncodingException {
        LOGGER.entry();
        MarcRecord result = null;
        try {
            MarcRecordReader reader = new MarcRecordReader(marcRecord);
            String recordId = reader.getRecordId();
            int agencyId = reader.getAgencyIdAsInt();

            Record record = rawRepo.fetchRecord(recordId, agencyId);
            return result = RecordContentTransformer.decodeRecord(record.getContent());
        } finally {
            LOGGER.exit(result);
        }
    }

    /**
     * Returns the deletion mark to store with the record in rawrepo.
     * <p>
     * This implementation always returns <code>true</code>.
     * </p>
     */
    @Override
    public boolean deletionMarkToStore() {
        return true;
    }
}
