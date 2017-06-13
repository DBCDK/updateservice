/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.records.MarcRecordWriter;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.openagency.client.OpenAgencyException;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.RawRepoDecoder;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import dk.dbc.updateservice.update.UpdateException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.ParseException;
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
    public ServiceResult performAction() throws UpdateException {
        logger.entry();
        ServiceResult result = null;
        try {
            logger.info("Handling record:\n{}", record);
            ServiceResult serviceResult = checkRecordForUpdatability();
            if (serviceResult.getStatus() != UpdateStatusEnumDTO.OK) {
                logger.error("Unable to update record: {}", serviceResult);
                return serviceResult;
            }
            MarcRecordReader reader = new MarcRecordReader(record);
            create001dForFBSRecords(reader);
            children.add(new AuthenticateRecordAction(state));
            handleSetCreateOverwriteDate();
            MarcRecordReader updReader = state.getMarcRecordReader();
            String updRecordId = updReader.recordId();
            Integer updAgencyId = updReader.agencyIdAsInteger();

            // Perform check of 002a and b,c - 870970 only
            if (RawRepo.COMMON_AGENCY.equals(updAgencyId)) {
                String validatePreviousFaustMessage = validatePreviousFaust(updReader);
                if (StringUtils.isNotEmpty(validatePreviousFaustMessage)) {
                    return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, validatePreviousFaustMessage, state);
                }
            }
            addDoubleRecordFrontendActionIfNecessary();

            logger.info("Split record into records to store in rawrepo. LibraryGroup is {}", state.getLibraryGroup().toString());

            List<MarcRecord> records = state.getLibraryRecordsHandler().recordDataForRawRepo(record, state.getUpdateServiceRequestDTO().getAuthenticationDTO(), state.getLibraryGroup());
            logger.info("Got {} records from LibraryRecordsHandler.recordDataForRawRepo", records.size());
            for (MarcRecord rec : records) {
                logger.info("Create sub actions for record:\n{}", rec);
                reader = new MarcRecordReader(rec);
                String recordId = reader.recordId();
                Integer agencyId = reader.agencyIdAsInteger();
                if (reader.markedForDeletion() && !rawRepo.recordExists(recordId, agencyId)) {
                    String message = String.format(state.getMessages().getString("operation.delete.non.existing.record"), recordId, agencyId);
                    return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
                }
                if (RawRepo.DBC_AGENCY_LIST.contains(agencyId.toString())) {
                    if (!updReader.markedForDeletion() &&
                            !state.getOpenAgencyService().hasFeature(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), LibraryRuleHandler.Rule.AUTH_CREATE_COMMON_RECORD) &&
                            !rawRepo.recordExists(updRecordId, updAgencyId)) {
                        String message = String.format(state.getMessages().getString("common.record.creation.not.allowed"), state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId());
                        return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
                    }
                    children.add(new UpdateCommonRecordAction(state, settings, rec));
                } else if (agencyId.equals(RawRepo.SCHOOL_COMMON_AGENCY)) {
                    children.add(new UpdateSchoolCommonRecord(state, settings, rec));
                } else {
                    if (agencyId.equals(RawRepo.DBC_ENRICHMENT) && commonRecordExists(records, rec, updAgencyId)) {
                        if (RawRepo.isSchoolEnrichment(agencyId)) {
                            children.add(new UpdateSchoolEnrichmentRecordAction(state, settings, rec));
                        } else {
                            children.add(new UpdateEnrichmentRecordAction(state, settings, rec, updAgencyId));
                        }
                    } else if (commonRecordExists(records, rec) && state.getOpenAgencyService().hasFeature(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), LibraryRuleHandler.Rule.CREATE_ENRICHMENTS)) {
                        if (RawRepo.isSchoolEnrichment(agencyId)) {
                            children.add(new UpdateSchoolEnrichmentRecordAction(state, settings, rec));
                        } else {
                            children.add(new UpdateEnrichmentRecordAction(state, settings, rec, RawRepo.COMMON_AGENCY));
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
        logger.info("RR record exists?........: " + rawRepo.recordExists(updReader.recordId(), updReader.agencyIdAsInteger()));
        logger.info("agency id?...............: " + updReader.agencyIdAsInteger());
        logger.info("RR common library?.......: " + updReader.agencyIdAsInteger().equals(RawRepo.COMMON_AGENCY));
        logger.info("DBC agency?..............: " + RawRepo.DBC_AGENCY_LIST.contains(updReader.agencyId()));
        logger.info("isDoubleRecordPossible?..: " + state.isDoubleRecordPossible());
    }


    private boolean commonRecordExists(List<MarcRecord> records, MarcRecord rec) throws UpdateException {
        return commonRecordExists(records, rec, RawRepo.COMMON_AGENCY);
    }

    private boolean commonRecordExists(List<MarcRecord> records, MarcRecord rec, Integer parentAgencyId) throws UpdateException {
        logger.entry();
        try {
            MarcRecordReader reader = new MarcRecordReader(rec);
            String recordId = reader.recordId();
            if (rawRepo == null) {
                logger.info("UpdateOperationAction.commonRecordExists(), rawRepo is NULL");
            }
            if (rawRepo.recordExists(recordId, parentAgencyId)) {
                return true;
            }
            for (MarcRecord record : records) {
                MarcRecordReader recordReader = new MarcRecordReader(record);
                String checkRecordId = recordReader.recordId();
                Integer checkAgencyId = recordReader.agencyIdAsInteger();
                if (recordId.equals(checkRecordId) && parentAgencyId.equals(checkAgencyId)) {
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

    private String getSolrQuery002bc(Boolean recordExists, String bValue, String cValue, String recordId) {
        if (recordExists) {
            return SolrServiceIndexer.createSubfieldQueryDualWithExcludeDBCOnly("002b", bValue, "002c", cValue, "001a", recordId);
        } else {
            return SolrServiceIndexer.createSubfieldQueryDualDBCOnly("002b", bValue, "002c", cValue);
        }
    }

    /**
     * In some cases the input record will have a n55*a field containing a date.
     * If thats the case the value of that field should be used as value for created date on the rawrepo row
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
                    boolean recordExists = rawRepo.recordExistsMaybeDeleted(reader.recordId(), reader.agencyIdAsInteger());
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
