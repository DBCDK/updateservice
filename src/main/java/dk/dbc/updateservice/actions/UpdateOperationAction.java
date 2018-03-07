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
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.openagency.client.OpenAgencyException;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.SolrException;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import dk.dbc.updateservice.update.UpdateException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.Set;

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
    UpdateOperationAction(GlobalActionState globalActionState, Properties properties) {
        super(UpdateOperationAction.class.getSimpleName(), globalActionState);
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
    public ServiceResult performAction() throws UpdateException, SolrException {
        logger.entry();
        ServiceResult result = null;
        try {
            logger.info("Handling record: {}", LogUtils.base64Encode(record));
            ServiceResult serviceResult = checkRecordForUpdatability();
            if (serviceResult.getStatus() != UpdateStatusEnumDTO.OK) {
                logger.error("Unable to update record: {}", serviceResult);
                return serviceResult;
            }
            MarcRecordReader reader = new MarcRecordReader(record);
            create001dForFBSRecords(reader);
            children.add(new AuthenticateRecordAction(state, record));
            handleSetCreateOverwriteDate();
            MarcRecordReader updReader = state.getMarcRecordReader();
            String updRecordId = updReader.getRecordId();
            int updAgencyId = updReader.getAgencyIdAsInt();

            // Perform check of 002a and b,c - 870970 only
            if (RawRepo.COMMON_AGENCY == updAgencyId) {
                String validatePreviousFaustMessage = validatePreviousFaust(updReader);
                if (StringUtils.isNotEmpty(validatePreviousFaustMessage)) {
                    return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, validatePreviousFaustMessage, state);
                }
            }
            addDoubleRecordFrontendActionIfNecessary();

            logger.info("Split record into records to store in rawrepo. LibraryGroup is {}", state.getLibraryGroup().toString());

            List<MarcRecord> records = state.getLibraryRecordsHandler().recordDataForRawRepo(record, state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), state.getLibraryGroup(), state.getMessages());
            logger.info("Got {} records from LibraryRecordsHandler.recordDataForRawRepo", records.size());
            for (MarcRecord rec : records) {
                logger.info("Create sub actions for record:\n{}", rec);
                reader = new MarcRecordReader(rec);
                String recordId = reader.getRecordId();
                int agencyId = reader.getAgencyIdAsInt();

                if (reader.markedForDeletion() && !rawRepo.recordExists(recordId, agencyId)) {
                    String message = String.format(state.getMessages().getString("operation.delete.non.existing.record"), recordId, agencyId);
                    return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
                }

                if (RawRepo.DBC_AGENCY_LIST.contains(Integer.toString(agencyId))) {
                    if (!updReader.markedForDeletion() &&
                            !state.getOpenAgencyService().hasFeature(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), LibraryRuleHandler.Rule.AUTH_CREATE_COMMON_RECORD) &&
                            !rawRepo.recordExists(updRecordId, updAgencyId)) {
                        String message = String.format(state.getMessages().getString("common.record.creation.not.allowed"), state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId());
                        return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
                    }
                    children.add(new UpdateCommonRecordAction(state, settings, rec));
                } else if (agencyId == RawRepo.SCHOOL_COMMON_AGENCY) {
                    children.add(new UpdateSchoolCommonRecord(state, settings, rec));
                } else {
                    if (agencyId == RawRepo.DBC_ENRICHMENT && commonRecordExists(records, rec, updAgencyId)) {
                        if (RawRepo.isSchoolEnrichment(agencyId)) {
                            children.add(new UpdateSchoolEnrichmentRecordAction(state, settings, rec));
                        } else {
                            children.add(new UpdateEnrichmentRecordAction(state, settings, rec, updAgencyId));
                        }
                    } else if (state.getOpenAgencyService().hasFeature(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS)) {
                        if (commonRecordExists(records, rec)) {
                            if (RawRepo.isSchoolEnrichment(agencyId)) {
                                children.add(new UpdateSchoolEnrichmentRecordAction(state, settings, rec));
                            } else {
                                children.add(new UpdateEnrichmentRecordAction(state, settings, rec, RawRepo.COMMON_AGENCY));
                            }
                        } else {
                            if (checkForDeletedCommonRecord(recordId)) {
                                String message = String.format(state.getMessages().getString("record.not.allowed.deleted.common.record"), state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), recordId);
                                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
                            } else {
                                children.add(new UpdateLocalRecordAction(state, settings, rec));
                            }
                        }
                    } else {
                        children.add(new UpdateLocalRecordAction(state, settings, rec));
                    }
                }
            }
            logRecordInfo(updReader);
            if (state.isDoubleRecordPossible()) {
                if (state.getLibraryGroup().isFBS() && StringUtils.isNotEmpty(state.getUpdateServiceRequestDTO().getDoubleRecordKey())) {
                    boolean test = state.getUpdateStore().doesDoubleRecordKeyExist(state.getUpdateServiceRequestDTO().getDoubleRecordKey());
                    if (test) {
                        children.add(new DoubleRecordCheckingAction(state, settings, record));
                    } else {
                        String message = String.format(state.getMessages().getString("double.record.frontend.unknown.key"), state.getUpdateServiceRequestDTO().getDoubleRecordKey());
                        return result = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
                    }
                } else if (state.getLibraryGroup().isFBS() || state.getLibraryGroup().isDBC() && StringUtils.isEmpty(state.getUpdateServiceRequestDTO().getDoubleRecordKey())) {
                    children.add(new DoubleRecordCheckingAction(state, settings, record));
                }
            }
            return result = ServiceResult.newOkResult();
        } catch (OpenAgencyException | UnsupportedEncodingException e) {
            return result = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, e.getMessage(), state);
        } finally {
            logger.exit(result);
        }
    }

    /**
     * Checks if the record id exists as a common record and if not checks if there is a 002a link to the id
     *
     * @param recordId The recordId to check for
     * @return true if the recordId exists as common record
     * @throws UpdateException
     * @throws SolrException
     */
    private boolean checkForDeletedCommonRecord(String recordId) throws UpdateException, SolrException {
        if (state.getRawRepo().recordExistsMaybeDeleted(recordId, RawRepo.COMMON_AGENCY)) {
            return true;
        }

        String solrQuery = getSolrQuery002a(false, recordId, recordId);
        return state.getSolrService().hasDocuments(solrQuery);
    }


    private void create001dForFBSRecords(MarcRecordReader reader) throws UpdateException {
        if (state.getLibraryGroup().isFBS()) {
            String valOf001 = reader.getValue("001", "d");
            if (StringUtils.isEmpty(valOf001)) {
                MarcRecordWriter writer = new MarcRecordWriter(record);
                writer.setCreationTimestamp();
                logger.info("Adding new date to field 001 , subfield d : " + record);
            }
        }
    }

    private void addDoubleRecordFrontendActionIfNecessary() throws UpdateException {
        Boolean doubleRecordPossible = state.isDoubleRecordPossible();
        Boolean fbsMode = state.getLibraryGroup().isFBS();
        Boolean doubleRecordKeyEmpty = StringUtils.isEmpty(state.getUpdateServiceRequestDTO().getDoubleRecordKey());
        if (doubleRecordPossible && fbsMode && doubleRecordKeyEmpty) {
            // This action must be run before the rest of the actions because we do not use xa compatible postgres connections
            children.add(new DoubleRecordFrontendAction(state, settings));
        }
    }

    private void logRecordInfo(MarcRecordReader updReader) throws UpdateException {
        logger.info("Delete?..................: " + updReader.markedForDeletion());
        logger.info("Library group?...........: " + state.getLibraryGroup());
        logger.info("Schema name?.............: " + state.getSchemaName());
        logger.info("RR record exists?........: " + rawRepo.recordExists(updReader.getRecordId(), updReader.getAgencyIdAsInt()));
        logger.info("agency id?...............: " + updReader.getAgencyIdAsInt());
        logger.info("RR common library?.......: " + (updReader.getAgencyIdAsInt() == RawRepo.COMMON_AGENCY));
        logger.info("DBC agency?..............: " + RawRepo.DBC_AGENCY_LIST.contains(updReader.getAgencyId()));
        logger.info("isDoubleRecordPossible?..: " + state.isDoubleRecordPossible());
    }

    private boolean commonRecordExists(List<MarcRecord> records, MarcRecord rec) throws UpdateException {
        return commonRecordExists(records, rec, RawRepo.COMMON_AGENCY);
    }

    private boolean commonRecordExists(List<MarcRecord> records, MarcRecord rec, int parentAgencyId) throws UpdateException {
        logger.entry();
        try {
            MarcRecordReader reader = new MarcRecordReader(rec);
            String recordId = reader.getRecordId();
            if (rawRepo == null) {
                logger.info("UpdateOperationAction.commonRecordExists(), rawRepo is NULL");
            }
            if (rawRepo.recordExists(recordId, parentAgencyId)) {
                return true;
            }
            for (MarcRecord record : records) {
                MarcRecordReader recordReader = new MarcRecordReader(record);
                String checkRecordId = recordReader.getRecordId();
                int checkAgencyId = recordReader.getAgencyIdAsInt();
                if (recordId.equals(checkRecordId) && parentAgencyId == checkAgencyId) {
                    return true;
                }
            }
            return false;
        } finally {
            logger.exit();
        }
    }

    private ServiceResult checkRecordForUpdatability() throws UpdateException, SolrException {
        logger.entry();
        try {
            MarcRecordReader reader = new MarcRecordReader(record);
            if (!reader.markedForDeletion()) {
                return ServiceResult.newOkResult();
            }
            String recordId = reader.getRecordId();
            String motherRecordId = state.getSolrService().getOwnerOf002(SolrServiceIndexer.createGetOwnerOf002QueryDBCOnly("002a", recordId));
            if (!motherRecordId.equals("")) {
                return ServiceResult.newOkResult();
            }
            int agencyId = reader.getAgencyIdAsInt();
            int rawRepoAgencyId = agencyId;
            if (agencyId == RawRepo.DBC_ENRICHMENT) {
                rawRepoAgencyId = RawRepo.COMMON_AGENCY;
            }
            RecordId newRecordId = new RecordId(recordId, rawRepoAgencyId);
            logger.debug("UpdateOperationAction.checkRecordForUpdatability().newRecordId: " + newRecordId);
            Set<RecordId> recordIdSet = rawRepo.children(newRecordId);
            logger.debug("UpdateOperationAction.checkRecordForUpdatability().recordIdSet: " + recordIdSet);
            if (!recordIdSet.isEmpty()) {
                String message = String.format(state.getMessages().getString("delete.record.children.error"), recordId);
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
            }
            return ServiceResult.newOkResult();
        } finally {
            logger.exit();
        }
    }

    /**
     * The responsibility of this function is to test the input record for any validation errors caused by the 002 field
     *
     * @param reader MarcRecordReader of the record to be checked
     * @return validation error message or null if no error was found
     * @throws UpdateException              when something goes wrong
     * @throws UnsupportedEncodingException when UTF8 doesn't work
     */
    private String validatePreviousFaust(MarcRecordReader reader) throws UpdateException, UnsupportedEncodingException, SolrException {
        logger.entry();
        try {
            String readerRecordId = reader.getRecordId();
            int readerAgencyId = reader.getAgencyIdAsInt();
            if (reader.markedForDeletion()) {
                // Handle deletion of existing record
                if (rawRepo.recordExists(readerRecordId, readerAgencyId)) {
                    Record existingRecord = rawRepo.fetchRecord(readerRecordId, readerAgencyId);
                    MarcRecord existingMarc = RecordContentTransformer.decodeRecord(existingRecord.getContent());
                    MarcRecordReader existingRecordReader = new MarcRecordReader(existingMarc);

                    // Deletion of 002a - check for holding on 001a
                    Set<Integer> holdingAgencies001 = state.getHoldingsItems().getAgenciesThatHasHoldingsForId(readerRecordId);
                    if (holdingAgencies001.size() > 0) {
                        for (String previousFaust : existingRecordReader.getCentralAliasIds()) {
                            if (!state.getSolrService().hasDocuments(SolrServiceIndexer.createSubfieldQueryDBCOnly("001a", previousFaust))) {
                                return state.getMessages().getString("delete.record.holdings.on.002a");
                            }
                        }
                    }

                    // Deletion of 002a - check for holding on 002a - if there is, then check whether the 002a record exist - if not, fail
                    for (String previousFaust : existingRecordReader.getCentralAliasIds()) {
                        Set<Integer> holdingAgencies002 = state.getHoldingsItems().getAgenciesThatHasHoldingsForId(previousFaust);
                        if (holdingAgencies002.size() > 0) {
                            if (!rawRepo.recordExists(previousFaust, readerAgencyId)) {
                                return state.getMessages().getString("delete.record.holdings.on.002a");
                            }
                        }
                    }
                }
            } else {
                // Handle either new record or update of existing record
                Boolean recordExists = rawRepo.recordExists(readerRecordId, readerAgencyId);

                // Compare new 002a with existing 002a
                for (String aValue : reader.getCentralAliasIds()) {
                    String solrQuery = getSolrQuery002a(recordExists, aValue, readerRecordId);

                    if (state.getSolrService().hasDocuments(solrQuery)) {
                        return state.getMessages().getString("update.record.with.002.links");
                    }
                }

                // Compare new 002b & c with existing 002b & c
                for (HashMap<String, String> bcValues : reader.getDecentralAliasIds()) {
                    String solrQuery = getSolrQuery002bc(recordExists, bcValues.get("b"), bcValues.get("c"), readerRecordId);

                    if (state.getSolrService().hasDocuments(solrQuery)) {
                        return state.getMessages().getString("update.record.with.002.links");
                    }
                }

                // Removal of 002a reference is allowed if either the referenced post still exists (no matter if there is holding or not)
                // or if the referenced record is deleted/non-existent and does NOT have holdings
                if (recordExists) {
                    Record currentRecord = rawRepo.fetchRecord(readerRecordId, readerAgencyId);
                    MarcRecord currentMarc = RecordContentTransformer.decodeRecord(currentRecord.getContent());
                    MarcRecordReader currentReader = new MarcRecordReader(currentMarc);
                    List<String> currentPreviousFaustList = currentReader.getCentralAliasIds();
                    List<String> previousFaustList = reader.getCentralAliasIds();

                    // The 002 field is repeatable but *a is not
                    // So we need to compare the list of 002a fields between new and current
                    List<String> removedPreviousFaust = new ArrayList<>();
                    for (String f : currentPreviousFaustList) {
                        if (!previousFaustList.contains(f)) {
                            removedPreviousFaust.add(f);
                        }
                    }

                    for (String m : removedPreviousFaust) {
                        if (state.getRawRepo().recordDoesNotExistOrIsDeleted(m, RawRepo.COMMON_AGENCY) &&
                                state.getHoldingsItems().getAgenciesThatHasHoldingsForId(m).size() > 0) {
                            return state.getMessages().getString("update.record.holdings.on.002a");
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
            // this is belt and braces - can only be relevant if there are a 002a=<aValue> in both current and another record.
            // Though this would be an error that this exact check is supposed to prevent.
            return SolrServiceIndexer.createSubfieldQueryWithExcludeDBCOnly("002a", aValue, "001a", recordId);
        } else {
            return SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", aValue);
        }
    }

    private String getSolrQuery002bc(Boolean recordExists, String bValue, String cValue, String recordId) {
        if (recordExists) {
            // this is belt and braces - can only be relevant if there are a 002bc=<aValue> in both current and another record.
            // Though this would be an error that this exact check is supposed to prevent.
            return SolrServiceIndexer.createSubfieldQueryDualWithExcludeDBCOnly("002b", bValue, "002c", cValue, "001a", recordId);
        } else {
            return SolrServiceIndexer.createSubfieldQueryDualDBCOnly("002b", bValue, "002c", cValue);
        }
    }

    /**
     * In some cases the input record will have a n55*a field containing a date.
     * If that's the case the value of that field should be used as value for created date on the rawrepo row
     * <p>
     * As the n55 field is a temporary field that shouldn't be saved in rawrepo it is removed from the record before saving.
     */
    private void handleSetCreateOverwriteDate() throws UpdateException {
        logger.debug("Checking for n55 field");
        MarcRecordReader reader = new MarcRecordReader(record);
        MarcRecordWriter writer = new MarcRecordWriter(record);

        if (reader.hasSubfield("n55", "a")) {
            String dateString = reader.getValue("n55", "a");
            if (dateString != null && !dateString.isEmpty()) {
                DateFormat formatter = new SimpleDateFormat("yyyyMMdd");
                try {
                    Date date = formatter.parse(dateString);
                    boolean recordExists = rawRepo.recordExistsMaybeDeleted(reader.getRecordId(), reader.getAgencyIdAsInt());
                    // We only want to set the created date to a specific value if the record is new
                    if (!recordExists) {
                        state.setCreateOverwriteDate(date);
                        logger.info("Found overwrite create date value: {}. Field has been removed from the record", date);
                    }
                } catch (ParseException e) {
                    logger.error("Caught ParseException trying to parse " + dateString + " as a date", e);
                    throw new UpdateException("Caught ParseException trying to parse " + dateString + " as a date");
                }
            }

            // Always remove n55 *a as we don't ever want that field in rawrepo.
            writer.removeField("n55");
        }
    }

}
