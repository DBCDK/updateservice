package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.Properties;

public class ActionFactory {
    private static final XLogger logger = XLoggerFactory.getXLogger(ActionFactory.class);

    /**
     * Factory method to create a EnqueueRecordAction.
     */
    public static EnqueueRecordAction newEnqueueAction(GlobalActionState globalActionState, MarcRecord record, Properties properties, String mimetype) {
        logger.entry(globalActionState, record);
        EnqueueRecordAction enqueueRecordAction;
        try {
            enqueueRecordAction = new EnqueueRecordAction(globalActionState, properties, record);
            return enqueueRecordAction;
        } finally {
            logger.exit();
        }
    }
}
