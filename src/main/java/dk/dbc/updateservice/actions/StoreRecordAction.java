package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.rawrepo.Record;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.RawRepoEncoder;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.ws.UpdateService;
import org.slf4j.MDC;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.xml.bind.JAXBException;
import java.io.UnsupportedEncodingException;

/**
 * Action to store a record in the rawrepo.
 */
public class StoreRecordAction extends AbstractRawRepoAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(StoreRecordAction.class);
    private static final XLogger bizLogger = XLoggerFactory.getXLogger(BusinessLoggerFilter.LOGGER_NAME);
    private RawRepoEncoder encoder;
    private String mimetype;

    public StoreRecordAction(RawRepo rawRepo, MarcRecord record) {
        super(StoreRecordAction.class.getSimpleName(), rawRepo, record);

        this.encoder = new RawRepoEncoder();
        this.mimetype = null;
    }

    public void setEncoder(RawRepoEncoder encoder) {
        this.encoder = encoder;
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
            bizLogger.info("Handling record:\n{}", record);

            MarcRecordReader reader = new MarcRecordReader(record);
            String recId = reader.recordId();
            Integer agencyId = reader.agencyIdAsInteger();

            final Record rawRepoRecord = rawRepo.fetchRecord(recId, agencyId);
            rawRepoRecord.setContent(encoder.encodeRecord(recordToStore()));
            rawRepoRecord.setMimeType(mimetype);
            rawRepoRecord.setDeleted(deletionMarkToStore());
            rawRepoRecord.setTrackingId(MDC.get(UpdateService.MDC_TRACKING_ID_LOG_CONTEXT));
            rawRepo.saveRecord(rawRepoRecord);

            bizLogger.info("Save record [{}:{}]", rawRepoRecord.getId().getBibliographicRecordId(), rawRepoRecord.getId().getAgencyId());
            logger.debug("Details about record: mimeType: '{}', deleted: {}, trackingId: '{}'", rawRepoRecord.getMimeType(), rawRepoRecord.isDeleted(), rawRepoRecord.getTrackingId());

            return result = ServiceResult.newOkResult();
        } catch (UnsupportedEncodingException | JAXBException ex) {
            logger.error("Error when trying to save record. ", ex);
            return result = ServiceResult.newErrorResult(UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, ex.getMessage());
        } finally {
            logger.exit(result);
        }
    }

    /**
     * Returns the record that should be stored in the rawrepo.
     * <p>
     * This implementation simply returns <code>this.record</code>.
     * </p>
     *
     * @return The record to store.
     */
    public MarcRecord recordToStore() {
        return record;
    }

    /**
     * Returns the deletion mark to store with the record in rawrepo.
     * <p>
     * This implementation always returns <code>false</code>.
     * </p>
     */
    public boolean deletionMarkToStore() {
        return false;
    }

    /**
     * Factory method to create a StoreRecordAction.
     */
    public static StoreRecordAction newStoreAction(RawRepo rawRepo, MarcRecord record, String mimetype) {
        logger.entry(rawRepo, record, mimetype);

        try {
            StoreRecordAction storeRecordAction = new StoreRecordAction(rawRepo, record);
            storeRecordAction.setMimetype(mimetype);

            return storeRecordAction;
        } finally {
            logger.exit();
        }
    }
}
