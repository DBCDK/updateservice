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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
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

    private static final DateTimeFormatter formatter = new DateTimeFormatterBuilder()
            .appendPattern("yyyyMMdd")
            .parseDefaulting(ChronoField.NANO_OF_DAY, 0)
            .toFormatter()
            .withZone(ZoneId.of("Europe/Copenhagen"));

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

            // Enrich the record in case the template is the metakompas template with only field 001, 004 and 665
            if ("metakompas".equals(state.getUpdateServiceRequestDTO().getSchemaName())) {
                try {
                    record = enrichMetaCompassRecord(record);
                } catch (UpdateException ex) {
                    String message = String.format(state.getMessages().getString("record.does.not.exist.or.deleted"), reader.getRecordId(), reader.getAgencyId());
                    return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
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
                            if (checkForExistingCommonFaust(recordId)) {
                                String message = String.format(state.getMessages().getString("record.not.allowed.deleted.common.record"), recordId);
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
    private boolean checkForExistingCommonFaust(String recordId) throws UpdateException, SolrException {
        if (state.getRawRepo().recordExistsMaybeDeleted(recordId, RawRepo.COMMON_AGENCY)) {
            return true;
        }

        String solrQuery = createSolrQuery(false, recordId, "002a", recordId);
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

    /**
     * This function handles the situation where metacompass sends a minimal record updateservice
     * <p>
     * The metacompass templates only allow fields 001, 004 and 665. The template is used only by the metacompass application.
     * <p>
     * When metacompass template is used we need to load the existing record and then use that with replaced 665 field from the input
     *
     * @return The record to be used for the rest if the execution
     * @throws UpdateException
     * @throws UnsupportedEncodingException
     */
    private MarcRecord enrichMetaCompassRecord(MarcRecord record) throws UnsupportedEncodingException, UpdateException {
        logger.info("Got metakompas template so updated the request record.");
        logger.info("Input metakompas record: \n{}", record);

        MarcRecordReader reader = new MarcRecordReader(record);

        if (!rawRepo.recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())) {
            throw new UpdateException("In order to update field 665 the record must exist");
        }

        MarcRecord existingRecord = RecordContentTransformer.decodeRecord(rawRepo.fetchRecord(reader.getRecordId(), reader.getAgencyIdAsInt()).getContent());

        MarcRecord existingRecordWith665 = new MarcRecord(existingRecord);
        MarcRecordWriter existingRecordWith665Writer = new MarcRecordWriter(existingRecordWith665);
        existingRecordWith665Writer.removeField("665");
        existingRecordWith665.getFields().addAll(reader.getFieldAll("665"));
        existingRecordWith665Writer.sort();

        logger.info("Output metakompas record: \n{}", existingRecordWith665);

        return existingRecordWith665;
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
                        if (holdingAgencies002.size() > 0 && !rawRepo.recordExists(previousFaust, readerAgencyId)) {
                            return state.getMessages().getString("delete.record.holdings.on.002a");
                        }
                    }
                }
            } else {
                // Handle either new record or update of existing record
                Boolean recordExists = rawRepo.recordExists(readerRecordId, readerAgencyId);

                for (String aValue : reader.getValues("002", "a")) {
                    String solrQuery = createSolrQuery(recordExists, readerRecordId, "002a", aValue);

                    if (state.getSolrService().hasDocuments(solrQuery)) {
                        return state.getMessages().getString("update.record.with.002.links");
                    }
                }

                for (String xValue : reader.getValues("002", "x")) {
                    String solrQuery = createSolrQuery(recordExists, readerRecordId, "002x", xValue);

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

    private String createSolrQuery(Boolean recordExists, String recordId, String subfieldName, String subfieldValue) {
        if (recordExists) {
            return SolrServiceIndexer.createSubfieldQueryWithExcludeDBCOnly(subfieldName, subfieldValue, "001a", recordId);
        } else {
            return SolrServiceIndexer.createSubfieldQueryDBCOnly(subfieldName, subfieldValue);
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
                boolean recordExists = rawRepo.recordExistsMaybeDeleted(reader.getRecordId(), reader.getAgencyIdAsInt());
                // We only want to set the created date to a specific value if the record is new
                if (!recordExists) {
                    Instant instant = formatter.parse(dateString, Instant::from);

                    state.setCreateOverwriteDate(instant);
                    logger.info("Found overwrite create date value: {}. Field has been removed from the record", instant);
                }
            }

            // Always remove n55 *a as we don't ever want that field in rawrepo.
            writer.removeField("n55");
        }
    }

}
