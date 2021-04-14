/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.common.records.utils.LogUtils;
import dk.dbc.common.records.utils.RecordContentTransformer;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.Record;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.utils.MDCUtil;
import org.slf4j.MDC;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.xml.bind.JAXBException;
import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Properties;

/**
 * Action to store a record in the rawrepo.
 */
public class StoreRecordAction extends AbstractRawRepoAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(StoreRecordAction.class);

    Encoder encoder = new Encoder();
    private String mimetype;
    Properties properties;

    private static final DateTimeFormatter modifiedFormatter = new DateTimeFormatterBuilder()
            .appendPattern("yyyyMMddHHmmss")
            .parseDefaulting(ChronoField.NANO_OF_DAY, 0)
            .toFormatter()
            .withZone(ZoneId.of("Europe/Copenhagen"));

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
     * Class used for mocking during unit test
     */
    class Encoder {
        byte[] encodeRecord(MarcRecord record) throws JAXBException, UnsupportedEncodingException {
            return RecordContentTransformer.encodeRecord(record);
        }

        MarcRecord decodeRecord(byte[] bytes) throws UnsupportedEncodingException {
            return RecordContentTransformer.decodeRecord(bytes);
        }
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
            if (mimetype == null || mimetype.isEmpty()) {
                throw new UpdateException("MimeType must be set");
            }

            logger.info("Handling record: {}", LogUtils.base64Encode(record));
            MarcRecordReader reader = new MarcRecordReader(record);
            String recId = reader.getRecordId();
            int agencyId = reader.getAgencyIdAsInt();
            MarcRecord recordToStore = recordToStore();
            recordToStore = state.getRecordSorter().sortRecord(recordToStore);
            updateModifiedDate(recordToStore);
            final Record rawRepoRecord = rawRepo.fetchRecord(recId, agencyId);
            rawRepoRecord.setContent(encoder.encodeRecord(recordToStore));
            rawRepoRecord.setMimeType(mimetype);
            rawRepoRecord.setDeleted(deletionMarkToStore());
            if (state.getCreateOverwriteDate() != null) {
                rawRepoRecord.setCreated(state.getCreateOverwriteDate());
            }
            rawRepoRecord.setTrackingId(MDC.get(MDCUtil.MDC_TRACKING_ID_LOG_CONTEXT));
            rawRepo.saveRecord(rawRepoRecord);
            logger.info("Save record [{}:{}]", rawRepoRecord.getId().getBibliographicRecordId(), rawRepoRecord.getId().getAgencyId());
            logger.debug("Details about record: mimeType: '{}', deleted: {}, trackingId: '{}'", rawRepoRecord.getMimeType(), rawRepoRecord.isDeleted(), rawRepoRecord.getTrackingId());
            return result = ServiceResult.newOkResult();
        } catch (UnsupportedEncodingException | JAXBException ex) {
            logger.error("Error when trying to save record. ", ex);
            return result = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, ex.getMessage());
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

    static StoreRecordAction newStoreMarcXChangeAction(GlobalActionState globalActionState, Properties properties, MarcRecord record) {
        logger.entry(globalActionState, record);
        try {
            StoreRecordAction storeRecordAction = new StoreRecordAction(globalActionState, properties, record);

            MarcRecordReader reader = new MarcRecordReader(record);

            if (RawRepo.ARTICLE_AGENCY == reader.getAgencyIdAsInt()) {
                storeRecordAction.setMimetype(MarcXChangeMimeType.ARTICLE);
            } else if (RawRepo.AUTHORITY_AGENCY == reader.getAgencyIdAsInt()) {
                storeRecordAction.setMimetype(MarcXChangeMimeType.AUTHORITY);
            } else if (RawRepo.LITTOLK_AGENCY == reader.getAgencyIdAsInt()) {
                storeRecordAction.setMimetype(MarcXChangeMimeType.LITANALYSIS);
            } else if (RawRepo.MATVURD_AGENCY == reader.getAgencyIdAsInt()) {
                storeRecordAction.setMimetype(MarcXChangeMimeType.MATVURD);
            } else if (RawRepo.HOSTPUB_AGENCY == reader.getAgencyIdAsInt()) {
                storeRecordAction.setMimetype(MarcXChangeMimeType.HOSTPUB);
            } else if (RawRepo.SIMPLE_AGENCIES.contains(reader.getAgencyIdAsInt())) {
                storeRecordAction.setMimetype(MarcXChangeMimeType.SIMPLE);
            } else {
                storeRecordAction.setMimetype(MarcXChangeMimeType.MARCXCHANGE);
            }

            return storeRecordAction;
        } finally {
            logger.exit();
        }
    }

    static StoreRecordAction newStoreEnrichmentAction(GlobalActionState globalActionState, Properties properties, MarcRecord record) {
        logger.entry(globalActionState, record);
        try {
            StoreRecordAction storeRecordAction = new StoreRecordAction(globalActionState, properties, record);
            storeRecordAction.setMimetype(MarcXChangeMimeType.ENRICHMENT);
            return storeRecordAction;
        } finally {
            logger.exit();
        }
    }

    void updateModifiedDate(MarcRecord marcRecord) {
        final MarcRecordReader reader = new MarcRecordReader(record);

        if (RawRepo.DBC_AGENCY_ALL.contains(reader.getAgencyId())) {
            final String modified = getModifiedDate();
            final MarcRecordWriter writer = new MarcRecordWriter(marcRecord);
            writer.addOrReplaceSubfield("001", "c", modified);
        }
    }

    // The purpose of this function is to make testing/mocking easier
    protected String getModifiedDate() {
        return modifiedFormatter.format(Instant.now());
    }

}
