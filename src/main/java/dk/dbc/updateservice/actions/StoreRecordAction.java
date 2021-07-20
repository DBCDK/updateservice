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
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(StoreRecordAction.class);

    Encoder encoder = new Encoder();
    private String mimetype;
    Properties properties;

    private static final DateTimeFormatter modifiedFormatter = new DateTimeFormatterBuilder()
            .appendPattern("yyyyMMddHHmmss")
            .parseDefaulting(ChronoField.NANO_OF_DAY, 0)
            .toFormatter()
            .withZone(ZoneId.of("Europe/Copenhagen"));

    public StoreRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord marcRecord) {
        super(StoreRecordAction.class.getSimpleName(), globalActionState, marcRecord);
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
    static class Encoder {
        byte[] encodeRecord(MarcRecord marcRecord) throws JAXBException, UnsupportedEncodingException {
            return RecordContentTransformer.encodeRecord(marcRecord);
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
        try {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Handling record: {}", LogUtils.base64Encode(marcRecord));
            }

            final MarcRecordReader reader = new MarcRecordReader(marcRecord);
            final String recId = reader.getRecordId();
            final int agencyId = reader.getAgencyIdAsInt();
            MarcRecord recordToStore = recordToStore();
            recordToStore = state.getRecordSorter().sortRecord(recordToStore);
            updateModifiedDate(recordToStore);
            final Record rawRepoRecord = rawRepo.fetchRecord(recId, agencyId);
            rawRepoRecord.setContent(encoder.encodeRecord(recordToStore));
            if (mimetype != null && !mimetype.isEmpty()) {
                rawRepoRecord.setMimeType(mimetype);
            }
            rawRepoRecord.setDeleted(deletionMarkToStore());
            if (state.getCreateOverwriteDate() != null) {
                rawRepoRecord.setCreated(state.getCreateOverwriteDate());
            }
            rawRepoRecord.setTrackingId(MDC.get(MDCUtil.MDC_TRACKING_ID_LOG_CONTEXT));
            rawRepo.saveRecord(rawRepoRecord);
            LOGGER.info("Save record [{}:{}]", rawRepoRecord.getId().getBibliographicRecordId(), rawRepoRecord.getId().getAgencyId());
            LOGGER.debug("Details about record: mimeType: '{}', deleted: {}, trackingId: '{}'", rawRepoRecord.getMimeType(), rawRepoRecord.isDeleted(), rawRepoRecord.getTrackingId());
            return ServiceResult.newOkResult();
        } catch (UnsupportedEncodingException | JAXBException ex) {
            LOGGER.error("Error when trying to save record. ", ex);
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, ex.getMessage());
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
        return marcRecord;
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

    static StoreRecordAction newStoreMarcXChangeAction(GlobalActionState globalActionState, Properties properties, MarcRecord marcRecord) {
        final StoreRecordAction storeRecordAction = new StoreRecordAction(globalActionState, properties, marcRecord);
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);
            storeRecordAction.setMimetype(getMarcXChangeMimetype(reader.getAgencyIdAsInt()));

        return storeRecordAction;
    }

    static String getMarcXChangeMimetype(int agencyId) {
        if (RawRepo.ARTICLE_AGENCY == agencyId ||
                RawRepo.RETRO_AGENCY == agencyId) {
            return MarcXChangeMimeType.ARTICLE;
        } else if (RawRepo.AUTHORITY_AGENCY == agencyId) {
            return MarcXChangeMimeType.AUTHORITY;
        } else if (RawRepo.LITTOLK_AGENCY == agencyId) {
            return MarcXChangeMimeType.LITANALYSIS;
        } else if (RawRepo.MATVURD_AGENCY == agencyId) {
            return MarcXChangeMimeType.MATVURD;
        } else if (RawRepo.HOSTPUB_AGENCY == agencyId) {
            return MarcXChangeMimeType.HOSTPUB;
        } else if (RawRepo.SIMPLE_AGENCIES.contains(agencyId)) {
            return MarcXChangeMimeType.SIMPLE;
        } else {
            return MarcXChangeMimeType.MARCXCHANGE;
        }
    }

    static StoreRecordAction newStoreEnrichmentAction(GlobalActionState globalActionState, Properties properties, MarcRecord marcRecord) {
        final StoreRecordAction storeRecordAction = new StoreRecordAction(globalActionState, properties, marcRecord);
        storeRecordAction.setMimetype(MarcXChangeMimeType.ENRICHMENT);
        return storeRecordAction;
    }

    void updateModifiedDate(MarcRecord marcRecord) {
        final MarcRecordReader reader = new MarcRecordReader(this.marcRecord);

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
