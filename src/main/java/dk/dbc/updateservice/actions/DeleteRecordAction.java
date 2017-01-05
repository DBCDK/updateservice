package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcField;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordWriter;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

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
     * Note 2017-01-04: It has been decided that deletion of posts should not be filtered and instead the entire input
     * record should be passed to rawrepo.
     *
     * Returns the record that should be stored in the rawrepo.
     * <p>
     * This implementation constructs a new record with the fields
     * <code>001</code> and <code>004</code> from <code>this.record</code>.
     * <code>004r</code> is overwritten with a <code>d</code> value.
     * </p>
     *
     * @return The record to store.
     */
    //@Override
    public MarcRecord recordToStoreDeprecated() {
        logger.entry();
        MarcRecord result = null;
        try {
            result = new MarcRecord();
            for (MarcField field : record.getFields()) {
                if (field.getName().equals("001")) {
                    result.getFields().add(field);
                }
                if (field.getName().equals("004")) {
                    result.getFields().add(field);
                }
                if (field.getName().equals("248")) {
                    result.getFields().add(field);
                }
            }
            MarcRecordWriter writer = new MarcRecordWriter(result);
            writer.markForDeletion();
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
