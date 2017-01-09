package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcField;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.records.MarcRecordWriter;
import dk.dbc.rawrepo.Record;
import dk.dbc.updateservice.update.RawRepoDecoder;
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
public class  DeleteRecordAction extends StoreRecordAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(DeleteRecordAction.class);

    public DeleteRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord record) {
        super(globalActionState, properties, record);
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
    public MarcRecord recordToStore() throws UpdateException, UnsupportedEncodingException{
        logger.entry();
        MarcRecord result = null;
        try {
            MarcRecordReader reader = new MarcRecordReader(record);

            if (record.getFields().size() == 2 && reader.hasField("001") && reader.hasField("004")) {
                result = loadCurrentRecord();

                new MarcRecordWriter(result).markForDeletion();
            } else {
                result = record;
            }

            return result;
        } finally {
            logger.exit(result);
        }
    }

    /**
     * Factory method to create a DeleteRecordAction.
     */
    public static DeleteRecordAction newDeleteRecordAction(GlobalActionState globalActionState,Properties properties, MarcRecord record, String mimetype) {
        logger.entry(globalActionState, record, mimetype);
        try {
            DeleteRecordAction deleteRecordAction = new DeleteRecordAction(globalActionState, properties, record);
            deleteRecordAction.setMimetype(mimetype);
            return deleteRecordAction;
        } finally {
            logger.exit();
        }
    }

    MarcRecord loadCurrentRecord() throws UpdateException, UnsupportedEncodingException {
        logger.entry();
        MarcRecord result = null;
        try {
            MarcRecordReader reader = new MarcRecordReader(record);
            String recordId = reader.recordId();
            Integer agencyId = reader.agencyIdAsInteger();

            Record record = rawRepo.fetchRecord(recordId, agencyId);
            return result = new RawRepoDecoder().decodeRecord(record.getContent());
        } finally {
            logger.exit(result);
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
