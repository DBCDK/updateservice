package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.ws.JNDIResources;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.Properties;


/**
 * Action to enqueue a record in rawrepo.
 * <p>
 * When a record is updated or created in the rawrepo it needs to be
 * enqueued so other components in the flow of services can be notified
 * about the changed record.
 * </p>
 * <p>
 * This action does exactly that for a single record.
 * </p>
 * <p>
 * <strong>Note:</strong> For this to work properly the record need to has
 * its links to other record setup correctly. If they are
 * missing, not all records that points to this record will be enqueued
 * correctly.
 * </p>
 */
public class EnqueueRecordAction extends AbstractRawRepoAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(EnqueueRecordAction.class);
    private static final XLogger bizLogger = XLoggerFactory.getXLogger(BusinessLoggerFilter.LOGGER_NAME);

    Properties settings;
    private String mimetype;

    public EnqueueRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord record) {
        super(EnqueueRecordAction.class.getSimpleName(), globalActionState, record);
        settings = properties;
    }

    public String getMimetype() {
        return mimetype;
    }

    public void setMimetype(String mimetype) {
        this.mimetype = mimetype;
    }

    /**
     * Performs this actions and may create any child actions.
     *
     * @return A list of ValidationError to be reported in the web service response.
     * @throws UpdateException In case of an error.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        logger.entry();

        ServiceResult result = null;
        try {
            bizLogger.info("Using provider id: '{}'", settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID));
            bizLogger.info("Handling record:\n{}", record);

            if (settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID) == null) {
                return result = ServiceResult.newErrorResult(UpdateStatusEnum.FAILED, state.getMessages().getString("provider.id.not.set"), state);
            }

            MarcRecordReader reader = new MarcRecordReader(record);
            String recId = reader.recordId();
            Integer agencyId = reader.agencyIdAsInteger();

            rawRepo.changedRecord(settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID), new RecordId(recId, agencyId), this.mimetype);
            bizLogger.info("The record {{}:{}} successfully enqueued", recId, agencyId);

            return result = ServiceResult.newOkResult();
        } finally {
            logger.exit(result);
        }
    }

    // TODO: VERSION 2: factory metode bør være i sin egen klasse
    /**
     * Factory method to create a EnqueueRecordAction.
     */
    public static EnqueueRecordAction newEnqueueAction(GlobalActionState globalActionState, MarcRecord record, Properties properties, String mimetype) {
        logger.entry(globalActionState, record);
        EnqueueRecordAction enqueueRecordAction;
        try {
            enqueueRecordAction = new EnqueueRecordAction(globalActionState, properties, record);
            enqueueRecordAction.setMimetype(mimetype);
            return enqueueRecordAction;
        } finally {
            logger.exit();
        }
    }
}
