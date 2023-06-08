package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.update.UpdateRecordContentTransformer;
import dk.dbc.updateservice.utils.DeferredLogger;
import dk.dbc.updateservice.utils.MDCUtil;
import org.slf4j.MDC;

import javax.xml.bind.JAXBException;
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
    private static final DeferredLogger LOGGER = new DeferredLogger(StoreRecordAction.class);

    Encoder encoder = new Encoder();
    private String mimetype;
    Properties properties;
    private final RecordId recordId;

    private static final DateTimeFormatter modifiedFormatter = new DateTimeFormatterBuilder()
            .appendPattern("yyyyMMddHHmmss")
            .parseDefaulting(ChronoField.NANO_OF_DAY, 0)
            .toFormatter()
            .withZone(ZoneId.of("Europe/Copenhagen"));

    public StoreRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord marcRecord) {
        super(StoreRecordAction.class.getSimpleName(), globalActionState, marcRecord);
        this.properties = properties;
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);
        String bibliographicRecordId = reader.getRecordId();
        int agencyId = reader.getAgencyIdAsInt();
        this.recordId = new RecordId(bibliographicRecordId, agencyId);
    }

    public StoreRecordAction(GlobalActionState globalActionState, Properties properties, RecordId recordId) {
        super(StoreRecordAction.class.getSimpleName(), globalActionState, recordId);
        this.properties = properties;
        this.recordId = recordId;
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
        byte[] encodeRecord(MarcRecord marcRecord) throws JAXBException {
            return UpdateRecordContentTransformer.encodeRecord(marcRecord);
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
        return LOGGER.callChecked(log -> {
            try {
                log.info("Handling record: {}:{}", recordId.getBibliographicRecordId(), recordId.getAgencyId());

                final Record rawRepoRecord = rawRepo.fetchRecord(recordId.getBibliographicRecordId(), recordId.getAgencyId());

                MarcRecord recordToStore;
                if (this.marcRecord != null) {
                    recordToStore = recordToStore();
                } else {
                    recordToStore = UpdateRecordContentTransformer.decodeRecord(rawRepoRecord.getContent());

                }
                recordToStore = state.getRecordSorter().sortRecord(recordToStore);
                updateModifiedDate(recordToStore);
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
                log.info("Save record [{}:{}]", rawRepoRecord.getId().getBibliographicRecordId(), rawRepoRecord.getId().getAgencyId());
                log.debug("Details about record: mimeType: '{}', deleted: {}, trackingId: '{}'", rawRepoRecord.getMimeType(), rawRepoRecord.isDeleted(), rawRepoRecord.getTrackingId());
                return ServiceResult.newOkResult();
            } catch (JAXBException ex) {
                log.error("Error when trying to save record {}", recordId, ex);
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, ex.getMessage());
            }
        });
    }

    /**
     * Returns the record that should be stored in the rawrepo.
     * <p>
     * This implementation simply returns <code>this.record</code>.
     * </p>
     *
     * @return The record to store.
     */
    public MarcRecord recordToStore() throws UpdateException {
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

    static StoreRecordAction newStoreMarcXChangeAction(GlobalActionState globalActionState, Properties properties, RecordId recordId) {
        final StoreRecordAction storeRecordAction = new StoreRecordAction(globalActionState, properties, recordId);
        storeRecordAction.setMimetype(getMarcXChangeMimetype(recordId.getAgencyId()));
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

    static StoreRecordAction newStoreEnrichmentAction(GlobalActionState globalActionState, Properties properties, RecordId recordId) {
        final StoreRecordAction storeRecordAction = new StoreRecordAction(globalActionState, properties, recordId);
        storeRecordAction.setMimetype(MarcXChangeMimeType.ENRICHMENT);
        return storeRecordAction;
    }

    void updateModifiedDate(MarcRecord marcRecord) {
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);

        if (RawRepo.DBC_AGENCY_ALL.contains(reader.getAgencyId())) {
            final String modified = getModifiedDate();
            final MarcRecordWriter writer = new MarcRecordWriter(marcRecord);
            writer.addOrReplaceSubField("001", 'c', modified);
        }
    }

    // The purpose of this function is to make testing/mocking easier
    protected String getModifiedDate() {
        return modifiedFormatter.format(Instant.now());
    }

}
