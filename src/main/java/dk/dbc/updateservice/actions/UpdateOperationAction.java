package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.records.MarcRecordWriter;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.openagency.client.OpenAgencyException;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.RawRepoDecoder;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.ws.JNDIResources;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Action to perform an Update Operation for a record.
 * <p/>
 * This action needs the following to be able to authenticate a record:
 * <ol>
 * <li>The record to authenticate, <code>record</code></li>
 * <li>
 * The name of the template that contains the validation rules to check against the record,
 * <code>schemaName</code>
 * </li>
 * <li>
 * An Authenticator that do the actual authentication, <code>authenticator</code>.
 * </li>
 * <li>
 * Login information to be parsed to <code>authenticator</code>.
 * </li>
 * </ol>
 */
class UpdateOperationAction extends AbstractRawRepoAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(UpdateOperationAction.class);
    private static final XLogger bizLogger = XLoggerFactory.getXLogger(BusinessLoggerFilter.LOGGER_NAME);

    Properties settings;

    /**
     * Constructs an instance with a template name and a record.
     * <p/>
     * The JavaScript logic needs an JavaScript environment and a set of
     * settings to work properly. These can be set though the properties <code>scripter</code>
     * and <code>settings</code>. This constructor initialize these to null.
     *
     * @param globalActionState State object containing data with data from request.
     */
    UpdateOperationAction(GlobalActionState globalActionState, Properties properties, MarcRecord marcRecord) {
        super(UpdateOperationAction.class.getSimpleName(), globalActionState, marcRecord);
        settings = properties;
    }

    // This is needed
    public void setSettings(Properties settings) {
        this.settings = settings;
    }

    /**
     * Updates the record in rawrepo.
     * <p/>
     * The operation is performed by adding these child actions:
     * <ol>
     * <li>
     * AuthenticateRecordAction: To authenticate the record against the user calling
     * the web service.
     * </li>
     * </ol>
     *
     * @return ServiceResult with status UpdateStatusEnum.OK
     * @throws UpdateException Never thrown.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        logger.entry();
        ServiceResult result = null;
        try {
            bizLogger.info("Handling record:\n{}", record);
            ServiceResult checkResult = checkRecordForUpdatability();
            if (checkResult.getStatus() != UpdateStatusEnum.OK) {
                bizLogger.error("Unable to update record: {}", checkResult);
                return checkResult;
            }
            MarcRecordReader reader = new MarcRecordReader(record);
            addDatefieldTo001d(reader);
            children.add(new AuthenticateRecordAction(state, record));
            MarcRecordReader updReader = new MarcRecordReader(record);
            String updRecordId = updReader.recordId();
            Integer updAgencyId = updReader.agencyIdAsInteger();

            // Perform check of 002a and b,c
            String validatePreviousFaustMessage = validatePreviousFaust(updReader);
            if (StringUtils.isNotEmpty(validatePreviousFaustMessage)) {
                return ServiceResult.newErrorResult(UpdateStatusEnum.FAILED, validatePreviousFaustMessage, state);
            }

            if (isDoubleRecordPossible(updReader, updRecordId, updAgencyId) && isFbsMode() && StringUtils.isEmpty(state.getUpdateRecordRequest().getDoubleRecordKey())) {
                // This action must be run before the rest of the actions because we do not use xa compatible postgres connections
                children.add(new DoubleRecordFrontendAction(state, settings, record));
            }
            bizLogger.info("Split record into records to store in rawrepo.");
            List<MarcRecord> records = state.getLibraryRecordsHandler().recordDataForRawRepo(record, state.getUpdateRecordRequest().getAuthentication().getUserIdAut(), state.getUpdateRecordRequest().getAuthentication().getGroupIdAut());
            for (MarcRecord rec : records) {
                bizLogger.info("");
                bizLogger.info("Create sub actions for record:\n{}", rec);
                reader = new MarcRecordReader(rec);
                String recordId = reader.recordId();
                Integer agencyId = reader.agencyIdAsInteger();
                if (reader.markedForDeletion() && !rawRepo.recordExists(recordId, agencyId)) {
                    String message = String.format(state.getMessages().getString("operation.delete.non.existing.record"), recordId, agencyId);
                    return ServiceResult.newErrorResult(UpdateStatusEnum.FAILED, message, state);
                }
                if (agencyId.equals(RawRepo.RAWREPO_COMMON_LIBRARY)) {
                    if (!updReader.markedForDeletion() &&
                            !state.getOpenAgencyService().hasFeature(state.getUpdateRecordRequest().getAuthentication().getGroupIdAut(), LibraryRuleHandler.Rule.AUTH_CREATE_COMMON_RECORD) &&
                            !rawRepo.recordExists(updRecordId, updAgencyId)) {
                        String message = String.format(state.getMessages().getString("common.record.creation.not.allowed"), state.getUpdateRecordRequest().getAuthentication().getGroupIdAut());
                        return ServiceResult.newErrorResult(UpdateStatusEnum.FAILED, message, state);
                    }
                    children.add(new UpdateCommonRecordAction(state, settings, rec));
                } else if (agencyId.equals(RawRepo.SCHOOL_COMMON_AGENCY)) {
                    children.add(new UpdateSchoolCommonRecord(state, settings, rec));
                } else {
                    if (commonRecordExists(records, rec) && (agencyId.equals(RawRepo.COMMON_LIBRARY) || state.getOpenAgencyService().hasFeature(state.getUpdateRecordRequest().getAuthentication().getGroupIdAut(), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS))) {
                        if (RawRepo.isSchoolEnrichment(agencyId)) {
                            children.add(new UpdateSchoolEnrichmentRecordAction(state, settings, rec));
                        } else {
                            children.add(new UpdateEnrichmentRecordAction(state, settings, rec));
                        }
                    } else {
                        children.add(new UpdateLocalRecordAction(state, settings, rec));
                    }
                }
            }
            bizLoggerOutput(updReader, updRecordId, updAgencyId);
            if (isDoubleRecordPossible(updReader, updRecordId, updAgencyId)) {
                if (isFbsMode() && StringUtils.isNotEmpty(state.getUpdateRecordRequest().getDoubleRecordKey())) {
                    boolean test = state.getUpdateStore().doesDoubleRecordKeyExist(state.getUpdateRecordRequest().getDoubleRecordKey());
                    if (test) {
                        children.add(new DoubleRecordCheckingAction(state, settings, record));
                    } else {
                        String message = String.format(state.getMessages().getString("double.record.frontend.unknown.key"), state.getUpdateRecordRequest().getDoubleRecordKey());
                        return result = ServiceResult.newDoubleRecordErrorResult(UpdateStatusEnum.FAILED, message, state);
                    }
                } else if (isFbsMode() || isDataioMode() && StringUtils.isEmpty(state.getUpdateRecordRequest().getDoubleRecordKey())) {
                    children.add(new DoubleRecordCheckingAction(state, settings, record));
                }
            }
            return result = ServiceResult.newOkResult();
        } catch (ScripterException | OpenAgencyException e) {
            return result = ServiceResult.newErrorResult(UpdateStatusEnum.FAILED, e.getMessage(), state);
        } catch (UnsupportedEncodingException e) {
            return result = ServiceResult.newErrorResult(UpdateStatusEnum.FAILED, e.getMessage(), state);
        } finally {
            logger.exit(result);
        }
    }

    private void addDatefieldTo001d(MarcRecordReader reader) {
        String valOf001 = reader.getValue("001", "d");
        if (StringUtils.isEmpty(valOf001)) {
            if (isFbsMode()) {
                MarcRecordWriter writer = new MarcRecordWriter(record);
                writer.addOrReplaceSubfield("001", "d", new SimpleDateFormat("yyyyMMdd").format(new Date()));
                logger.info("Adding new date to field 001 , subfield d : " + record);
            }
        }
    }

    private void bizLoggerOutput(MarcRecordReader updReader, String updRecordId, Integer updAgencyId) throws UpdateException {
        bizLogger.info("Delete?................: " + updReader.markedForDeletion());
        bizLogger.info("isDBC?.................: " + updReader.isDBCRecord());
        bizLogger.info("RR record exists?......: " + rawRepo.recordExists(updRecordId, updAgencyId));
        bizLogger.info("agency id?.............: " + updAgencyId);
        bizLogger.info("RR common library?.....: " + updAgencyId.equals(RawRepo.RAWREPO_COMMON_LIBRARY));
        bizLogger.info("isDoubleRecordPossible?: " + isDoubleRecordPossible(updReader, updRecordId, updAgencyId));
    }

    private boolean isDoubleRecordPossible(MarcRecordReader updReader, String updRecordId, Integer updAgencyId) throws UpdateException {
        return !updReader.markedForDeletion() && !updReader.isDBCRecord() && !rawRepo.recordExists(updRecordId, updAgencyId) && updAgencyId.equals(RawRepo.RAWREPO_COMMON_LIBRARY);
    }

    private boolean commonRecordExists(List<MarcRecord> records, MarcRecord rec) throws UpdateException {
        logger.entry();
        try {
            MarcRecordReader reader = new MarcRecordReader(rec);
            String recordId = reader.recordId();
            if (rawRepo == null) {
                logger.info("UpdateOperationAction.commonRecordExists(), rawRepo is NULL");
            }
            if (rawRepo.recordExists(recordId, RawRepo.RAWREPO_COMMON_LIBRARY)) {
                return true;
            }
            for (MarcRecord record : records) {
                MarcRecordReader recordReader = new MarcRecordReader(record);
                String checkRecordId = recordReader.recordId();
                Integer checkAgencyId = recordReader.agencyIdAsInteger();
                if (checkRecordId.equals(recordId) && checkAgencyId.equals(RawRepo.RAWREPO_COMMON_LIBRARY)) {
                    return true;
                }
            }
            return false;
        } finally {
            logger.exit();
        }
    }

    private ServiceResult checkRecordForUpdatability() throws UpdateException {
        logger.entry();
        try {
            MarcRecordReader reader = new MarcRecordReader(record);
            if (!reader.markedForDeletion()) {
                return ServiceResult.newOkResult();
            }
            String recordId = reader.recordId();
            int agencyId = reader.agencyIdAsInteger();
            int rawRepoAgencyId = agencyId;
            if (agencyId == RawRepo.COMMON_LIBRARY) {
                rawRepoAgencyId = RawRepo.RAWREPO_COMMON_LIBRARY;
            }
            RecordId newRecordId = new RecordId(recordId, rawRepoAgencyId);
            logger.debug("UpdateOperationAction.checkRecordForUpdatability().newRecordId: " + newRecordId);
            Set<RecordId> recordIdSet = rawRepo.children(newRecordId);
            logger.debug("UpdateOperationAction.checkRecordForUpdatability().recordIdSet: " + recordIdSet);
            if (!recordIdSet.isEmpty()) {
                String message = String.format(state.getMessages().getString("delete.record.children.error"), recordId);
                return ServiceResult.newErrorResult(UpdateStatusEnum.FAILED, message, state);
            }
            return ServiceResult.newOkResult();
        } finally {
            logger.exit();
        }
    }

    private boolean isFbsMode() {
        boolean res = false;
        String mode = settings.getProperty(JNDIResources.JAVASCRIPT_INSTALL_NAME_KEY);
        if (mode != null && mode.equals("fbs")) {
            res = true;
        }
        return res;
    }

    private boolean isDataioMode() {
        boolean res = false;
        String mode = settings.getProperty(JNDIResources.JAVASCRIPT_INSTALL_NAME_KEY);
        if (mode != null && mode.equals("dataio")) {
            res = true;
        }
        return res;
    }

    /**
     * The responsibility of this function is to test the input record for any validation errors caused by the 002 field
     *
     * @param reader MarcRecordReader of the record to be checked
     * @return validation error message or null if no error was found
     * @throws UpdateException
     * @throws UnsupportedEncodingException
     */
    private String validatePreviousFaust(MarcRecordReader reader) throws UpdateException, UnsupportedEncodingException {
        logger.entry();

        try {
            if (reader.markedForDeletion()) {
                // Handle deletion of existing record
                if (rawRepo.recordExists(reader.recordId(), reader.agencyIdAsInteger())) {
                    Record existingRecord = rawRepo.fetchRecord(reader.recordId(), reader.agencyIdAsInteger());
                    MarcRecord existingMarc = new RawRepoDecoder().decodeRecord(existingRecord.getContent());
                    MarcRecordReader existingRecordReader = new MarcRecordReader(existingMarc);

                    // Deletion of 002a - check for holding on 001a
                    Set<Integer> holdingAgencies001 = state.getHoldingsItems().getAgenciesThatHasHoldingsForId(reader.recordId());
                    if (holdingAgencies001.size() > 0) {
                        for (String previousFaust : existingRecordReader.centralAliasIds()) {
                            if (!state.getSolrService().hasDocuments(SolrServiceIndexer.createSubfieldQueryDBCOnly("001a", previousFaust))) {
                                return state.getMessages().getString("delete.record.holdings.on.002a");
                            }
                        }
                    }

                    // Deletion of 002a - check for holding on 002a
                    for (String previousFaust : existingRecordReader.centralAliasIds()) {
                        Set<Integer> holdingAgencies002 = state.getHoldingsItems().getAgenciesThatHasHoldingsForId(previousFaust);
                        if (holdingAgencies002.size() > 0) {
                            return state.getMessages().getString("delete.record.holdings.on.002a");
                        }
                    }
                }
            } else {
                // Handle either new record or update of existing record
                Boolean recordExists = rawRepo.recordExists(reader.recordId(), reader.agencyIdAsInteger());

                // Compare new 002a with existing 002a
                for (String aValue : reader.centralAliasIds()) {
                    String solrQuery = getSolrQuery002a(recordExists, aValue, reader.recordId());

                    if (state.getSolrService().hasDocuments(solrQuery)) {
                        return state.getMessages().getString("update.record.with.002.links");
                    }
                }

                // Compare new 002b & c with existing 002b & c
                for (HashMap<String, String> bcValues : reader.decentralAliasIds()) {
                    String solrQuery = getSolrQuery002bc(recordExists, bcValues.get("b"), bcValues.get("c"), reader.recordId());

                    if (state.getSolrService().hasDocuments(solrQuery)) {
                        return state.getMessages().getString("update.record.with.002.links");
                    }
                }

                if (recordExists) {
                    Record existingRecord = rawRepo.fetchRecord(reader.recordId(), reader.agencyIdAsInteger());
                    MarcRecord existingMarc = new RawRepoDecoder().decodeRecord(existingRecord.getContent());
                    MarcRecordReader existingRecordReader = new MarcRecordReader(existingMarc);

                    // The input record has no 002a field so check if an existing record does
                    if (reader.centralAliasIds().size() == 0 && existingRecordReader.hasSubfield("002", "a")) {
                        for (String previousFaust : existingRecordReader.centralAliasIds()) {
                            Set<Integer> holdingAgencies = state.getHoldingsItems().getAgenciesThatHasHoldingsForId(previousFaust);
                            if (holdingAgencies.size() > 0) {
                                return state.getMessages().getString("update.record.holdings.on.002a");
                            }
                        }
                    }
                }
            }

            return null;
        } finally {
            logger.exit();
        }
    }

    private String getSolrQuery002a(Boolean recordExists, String aValue, String recordId) {
        if (recordExists) {
            return SolrServiceIndexer.createSubfieldQueryWithExcludeDBCOnly("002a", aValue, "001a", recordId);
        } else {
            return SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", aValue);
        }
    }

    private String getSolrQuery002bc(Boolean recordExists, String bValue, String cValue, String recordId){
        if (recordExists) {
            return SolrServiceIndexer.createSubfieldQueryDualWithExcludeDBCOnly("002b", bValue, "002c", cValue, "001a", recordId);
        } else {
            return SolrServiceIndexer.createSubfieldQueryDualDBCOnly("002b", bValue, "002c", cValue);
        }
    }

}
