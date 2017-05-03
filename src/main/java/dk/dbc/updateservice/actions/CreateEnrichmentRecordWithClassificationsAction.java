/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.records.MarcRecordWriter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.ws.MDCUtil;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.Properties;

/**
 * Action to create a new enrichment record from a common record.
 * <p>
 * This action handles to case where we need to create a new enrichment
 * of copy classification data into it from a common record. This case is
 * triggered when the classification data is updated in a common record and
 * there is no enrichment record for a agency that has holdings for the
 * common record.
 * </p>
 * <p>
 * The creation of the enrichment record is done by calling the JavaScript
 * engine (thought LibraryRecordsHandler) to produce the actual enrichment
 * record.
 * </p>
 */
public class CreateEnrichmentRecordWithClassificationsAction extends AbstractAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(CreateEnrichmentRecordWithClassificationsAction.class);
    private static final String RECATEGORIZATION_STRING = "UPDATE posttypeskift";
    private static final String RECLASSIFICATION_STRING = "UPDATE opstillingsændring";

    String agencyId;
    private Properties settings;
    protected MarcRecord currentCommonRecord = null;
    protected MarcRecord updatingCommonRecord = null;
    protected String commonRecordId = null;

    public CreateEnrichmentRecordWithClassificationsAction(GlobalActionState globalActionState, Properties properties, String agencyIdInput) {
        super(CreateEnrichmentRecordWithClassificationsAction.class.getSimpleName(), globalActionState);
        settings = properties;
        agencyId = agencyIdInput;
    }

    public void setCurrentCommonRecord(MarcRecord currentCommonRecord) {
        this.currentCommonRecord = currentCommonRecord;
    }

    public MarcRecord getUpdatingCommonRecord() {
        return updatingCommonRecord;
    }

    public void setUpdatingCommonRecord(MarcRecord updatingCommonRecord) {
        this.updatingCommonRecord = updatingCommonRecord;
    }

    public String getCommonRecordId() {
        return commonRecordId;
    }

    public void setCommonRecordId(String commonRecordId) {
        this.commonRecordId = commonRecordId;
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
        try {
            logger.info("AgencyId..............: " + agencyId);
            logger.info("Current common record.:\n{}", currentCommonRecord);
            logger.info("Updating common record:\n{}", updatingCommonRecord);

            MarcRecord enrichmentRecord = createRecord();
            if (enrichmentRecord.getFields().isEmpty()) {
                logger.info("No sub actions to create for an empty enrichment record.");
                return ServiceResult.newOkResult();
            }
            logger.info("Creating sub actions to store new enrichment record.");
            logger.info("Enrichment record:\n{}", enrichmentRecord);

            String recordId = new MarcRecordReader(enrichmentRecord).recordId();

            StoreRecordAction storeRecordAction = new StoreRecordAction(state, settings, enrichmentRecord);
            storeRecordAction.setMimetype(MarcXChangeMimeType.ENRICHMENT);
            children.add(storeRecordAction);

            LinkRecordAction linkRecordAction = new LinkRecordAction(state, enrichmentRecord);
            linkRecordAction.setLinkToRecordId(new RecordId(recordId, RawRepo.RAWREPO_COMMON_LIBRARY));
            children.add(linkRecordAction);

            EnqueueRecordAction enqueueRecordAction = new EnqueueRecordAction(state, settings, enrichmentRecord);
            children.add(enqueueRecordAction);

            return ServiceResult.newOkResult();
        } catch (ScripterException ex) {
            logger.error("Update error: " + ex.getMessage(), ex);
            throw new UpdateException(ex.getMessage(), ex);
        } finally {
            logger.exit();
        }
    }

    @Override
    public void setupMDCContext() {
        MDCUtil.setupContextForEnrichmentRecord(updatingCommonRecord, agencyId);
    }

    public MarcRecord createRecord() throws UpdateException, ScripterException {
        logger.entry();
        logger.debug("entering createRecord");
        MarcRecord result = null;
        try {
            result = state.getLibraryRecordsHandler().createLibraryExtendedRecord(currentCommonRecord, updatingCommonRecord, agencyId);
            MarcRecordWriter writer = new MarcRecordWriter(result);
            MarcRecordReader reader = new MarcRecordReader(result);

            // Fix for story #1910 , 1911
            if (!reader.hasValue("y08", "a", RECATEGORIZATION_STRING)) {
                writer.addOrReplaceSubfield("y08", "a", RECLASSIFICATION_STRING);
            }

            if (commonRecordId != null) {
                writer.addOrReplaceSubfield("001", "a", commonRecordId);
            }
            return result;
        } finally {
            logger.exit(result);
        }
    }
}
