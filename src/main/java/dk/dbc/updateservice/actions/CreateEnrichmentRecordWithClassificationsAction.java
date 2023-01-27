package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.common.records.utils.LogUtils;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.utils.DeferredLogger;
import dk.dbc.updateservice.utils.MDCUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Action to create a new enrichment record from a common record.
 * <p>
 * This action handles the case where we need to create a new enrichment
 * record.
 * This case is triggered when there is a classification difference in current
 * and updating records.
 * </p>
 * <p>
 * If the target common record differs from the calling updating record, this
 * can be set via setTargetRecordId.
 * </p>
 * <p>
 * The creation of the enrichment record is done by calling the JavaScript
 * engine to produce the actual enrichment record.
 * </p>
 */
public class CreateEnrichmentRecordWithClassificationsAction extends AbstractAction {
    private static final DeferredLogger LOGGER = new DeferredLogger(CreateEnrichmentRecordWithClassificationsAction.class);
    private static final String RECATEGORIZATION_STRING = "UPDATE posttypeskift";
    private static final String RECLASSIFICATION_STRING = "UPDATE opstillingsændring";

    String agencyId;
    private final Properties settings;
    MarcRecord currentCommonRecord = null;
    MarcRecord updatingCommonRecord = null;
    private String targetRecordId = null;
    private List<String> reclassificationMessages = new ArrayList<>();

    public CreateEnrichmentRecordWithClassificationsAction(GlobalActionState globalActionState, Properties properties, String agencyIdInput) {
        super(CreateEnrichmentRecordWithClassificationsAction.class.getSimpleName(), globalActionState);
        settings = properties;
        agencyId = agencyIdInput;
    }

    void setCurrentCommonRecord(MarcRecord currentCommonRecord) {
        this.currentCommonRecord = currentCommonRecord;
    }

    MarcRecord getUpdatingCommonRecord() {
        return updatingCommonRecord;
    }

    void setUpdatingCommonRecord(MarcRecord updatingCommonRecord) {
        this.updatingCommonRecord = updatingCommonRecord;
    }

    String getTargetRecordId() {
        return targetRecordId;
    }

    void setTargetRecordId(String targetRecordId) {
        this.targetRecordId = targetRecordId;
    }

    public List<String> getReclassificationMessages() {
        return reclassificationMessages;
    }

    public void setReclassificationMessages(List<String> reclassificationMessages) {
        this.reclassificationMessages = reclassificationMessages;
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
            if (log.isInfoEnabled()) {
                log.info("AgencyId: {}, Current common record.:\n{}\n Updating common record:\n{}", agencyId, LogUtils.base64Encode(currentCommonRecord), LogUtils.base64Encode(updatingCommonRecord));
            }

            final MarcRecord enrichmentRecord = createRecord();
            if (enrichmentRecord.getFields().isEmpty()) {
                log.info("No sub actions to create for an empty enrichment record.");
                return ServiceResult.newOkResult();
            }
            log.info("Creating sub actions to store new enrichment record.");
            log.info("Enrichment record:\n{}", enrichmentRecord);

            final String recordId = new MarcRecordReader(enrichmentRecord).getRecordId();

            final StoreRecordAction storeRecordAction = new StoreRecordAction(state, settings, enrichmentRecord);
            storeRecordAction.setMimetype(MarcXChangeMimeType.ENRICHMENT);
            children.add(storeRecordAction);

            final LinkRecordAction linkRecordAction = new LinkRecordAction(state, enrichmentRecord);
            linkRecordAction.setLinkToRecordId(new RecordId(recordId, RawRepo.COMMON_AGENCY));
            children.add(linkRecordAction);

            final EnqueueRecordAction enqueueRecordAction = new EnqueueRecordAction(state, settings, enrichmentRecord);
            children.add(enqueueRecordAction);

            return ServiceResult.newOkResult();
        });
    }

    @Override
    public void setupMDCContext() {
        MDCUtil.setupContextForEnrichmentRecord(updatingCommonRecord, agencyId);
    }

    public MarcRecord createRecord() throws UpdateException {
        LOGGER.use(log -> log.debug("entering createRecord"));
        final MarcRecord result = state.getLibraryRecordsHandler().createLibraryExtendedRecord(currentCommonRecord, updatingCommonRecord, agencyId);
        final MarcRecordWriter writer = new MarcRecordWriter(result);
        final MarcRecordReader reader = new MarcRecordReader(result);

        // Fix for story #1910 , 1911
        if (!reader.hasValue("y08", "a", RECATEGORIZATION_STRING)) {
            final StringBuilder sb = new StringBuilder();
            sb.append(RECLASSIFICATION_STRING);
            if (!reclassificationMessages.isEmpty()) {
                final List<String> translatedReclassificationMessages = new ArrayList<>();
                for (String reclassificationMessage : reclassificationMessages) {
                    final String translatedReclassificationMessage = state.getMessages().getString(reclassificationMessage);
                    // The translated messages are used elsewhere, so we can't change them now
                    // Since we want a slightly different message displayed in y08 we have to use string replace
                    translatedReclassificationMessages.add(translatedReclassificationMessage.replace(" er ændret", ""));
                }
                sb.append(" pga. ");
                sb.append(String.join(", ", translatedReclassificationMessages));
            }
            writer.addOrReplaceSubfield("y08", "a", sb.toString());
        }

        // While tempting, this cannot be done by changing the agency in the createLibraryExtendedRecord call - it will give a null result
        if (targetRecordId != null) {
            writer.addOrReplaceSubfield("001", "a", targetRecordId);
        }
        return result;
    }
}
