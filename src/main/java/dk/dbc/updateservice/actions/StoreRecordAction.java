package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.rawrepo.Record;
import dk.dbc.updateservice.dto.UpdateStatusEnumDto;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.update.RawRepoEncoder;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.ws.UpdateService;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.MDC;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Properties;

/**
 * Action to store a record in the rawrepo.
 */
public class StoreRecordAction extends AbstractRawRepoAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(StoreRecordAction.class);
    private static final String ENTRY_POINT = "sortRecord";

    RawRepoEncoder encoder = new RawRepoEncoder();
    private String mimetype;
    Properties properties;

    public StoreRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord record) {
        super(StoreRecordAction.class.getSimpleName(), globalActionState, record);
        this.properties = properties;
    }

    String getMimetype() {
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
            logger.info("Handling record:\n{}", record);
            MarcRecordReader reader = new MarcRecordReader(record);
            String recId = reader.recordId();
            Integer agencyId = reader.agencyIdAsInteger();
            MarcRecord recordToStore = recordToStore();
            recordToStore = sortRecord(recordToStore);
            final Record rawRepoRecord = rawRepo.fetchRecord(recId, agencyId);
            rawRepoRecord.setContent(encoder.encodeRecord(recordToStore));
            rawRepoRecord.setMimeType(mimetype);
            rawRepoRecord.setDeleted(deletionMarkToStore());
            rawRepoRecord.setTrackingId(MDC.get(UpdateService.MDC_TRACKING_ID_LOG_CONTEXT));
            rawRepo.saveRecord(rawRepoRecord);
            logger.info("Save record [{}:{}]", rawRepoRecord.getId().getBibliographicRecordId(), rawRepoRecord.getId().getAgencyId());
            logger.debug("Details about record: mimeType: '{}', deleted: {}, trackingId: '{}'", rawRepoRecord.getMimeType(), rawRepoRecord.isDeleted(), rawRepoRecord.getTrackingId());
            return result = ServiceResult.newOkResult();
        } catch (UnsupportedEncodingException | JAXBException ex) {
            logger.error("Error when trying to save record. ", ex);
            return result = ServiceResult.newErrorResult(UpdateStatusEnumDto.FAILED, ex.getMessage(), state);
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
    public MarcRecord recordToStore() throws UpdateException, UnsupportedEncodingException {
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
    static StoreRecordAction newStoreAction(GlobalActionState globalActionState, Properties properties, MarcRecord record, String mimetype) {
        logger.entry(globalActionState, record, mimetype);
        try {
            StoreRecordAction storeRecordAction = new StoreRecordAction(globalActionState, properties, record);
            storeRecordAction.setMimetype(mimetype);
            return storeRecordAction;
        } finally {
            logger.exit();
        }
    }

    public MarcRecord sortRecord(MarcRecord record) {
        logger.entry();
        MarcRecord result = record;
        ObjectMapper mapper = new ObjectMapper();
        String jsonRecord;
        try {
            jsonRecord = mapper.writeValueAsString(record);
            Object jsResult = state.getScripter().callMethod(ENTRY_POINT, state.getSchemaName(), jsonRecord, properties);
            if (jsResult instanceof String) {
                result = mapper.readValue(jsResult.toString(), MarcRecord.class);
            }
            return result;
        } catch (IOException | ScripterException ex) {
            logger.error("Error when trying to sort the record. ", ex);
            return record;
        } finally {
            logger.exit();
        }
    }

}
