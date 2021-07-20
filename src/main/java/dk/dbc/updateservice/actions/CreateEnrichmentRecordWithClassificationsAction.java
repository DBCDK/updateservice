/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.common.records.utils.LogUtils;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.utils.MDCUtil;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

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
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(CreateEnrichmentRecordWithClassificationsAction.class);
    private static final String RECATEGORIZATION_STRING = "UPDATE posttypeskift";
    private static final String RECLASSIFICATION_STRING = "UPDATE opstillingsændring";

    String agencyId;
    private final Properties settings;
    MarcRecord currentCommonRecord = null;
    MarcRecord updatingCommonRecord = null;
    private String targetRecordId = null;

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

    /**
     * Performs this actions and may create any child actions.
     *
     * @return A list of ValidationError to be reported in the web service response.
     * @throws UpdateException In case of an error.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("AgencyId..............: " + agencyId);
            LOGGER.info("Current common record.: {}", LogUtils.base64Encode(currentCommonRecord));
            LOGGER.info("Updating common record: {}", LogUtils.base64Encode(updatingCommonRecord));
        }

        final MarcRecord enrichmentRecord = createRecord();
        if (enrichmentRecord.getFields().isEmpty()) {
            LOGGER.info("No sub actions to create for an empty enrichment record.");
            return ServiceResult.newOkResult();
        }
        LOGGER.info("Creating sub actions to store new enrichment record.");
        LOGGER.info("Enrichment record:\n{}", enrichmentRecord);

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
    }

    @Override
    public void setupMDCContext() {
        MDCUtil.setupContextForEnrichmentRecord(updatingCommonRecord, agencyId);
    }

    public MarcRecord createRecord() throws UpdateException {
        LOGGER.debug("entering createRecord");
        final MarcRecord result = state.getLibraryRecordsHandler().createLibraryExtendedRecord(currentCommonRecord, updatingCommonRecord, agencyId);
        final MarcRecordWriter writer = new MarcRecordWriter(result);
        final MarcRecordReader reader = new MarcRecordReader(result);

        // Fix for story #1910 , 1911
        if (!reader.hasValue("y08", "a", RECATEGORIZATION_STRING)) {
            writer.addOrReplaceSubfield("y08", "a", RECLASSIFICATION_STRING);
        }

        // While tempting, this cannot be done by changing the agency in the createLibraryExtendedRecord call - it will give a null result
        if (targetRecordId != null) {
            writer.addOrReplaceSubfield("001", "a", targetRecordId);
        }
        return result;
    }
}
