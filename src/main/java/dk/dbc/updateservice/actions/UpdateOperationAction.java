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
import dk.dbc.jsonb.JSONBException;
import dk.dbc.opencat.connector.OpencatBusinessConnectorException;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.MetakompasHandler;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.SolrException;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.vipcore.exception.VipCoreException;
import dk.dbc.vipcore.libraryrules.VipCoreLibraryRulesConnector;
import org.apache.commons.lang3.StringUtils;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import static dk.dbc.updateservice.utils.MDCUtil.MDC_TRACKING_ID_LOG_CONTEXT;

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
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(UpdateOperationAction.class);

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
        try {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Handling record: {}", LogUtils.base64Encode(marcRecord));
            }
            final ServiceResult serviceResult = checkRecordForUpdatability();
            if (serviceResult.getStatus() != UpdateStatusEnumDTO.OK) {
                LOGGER.info("Unable to update record: {}", serviceResult);
                return serviceResult;
            }
            MarcRecordReader reader = new MarcRecordReader(marcRecord);
            setCreatedDate(reader);
            children.add(new AuthenticateRecordAction(state, marcRecord));
            handleSetCreateOverwriteDate();
            final MarcRecordReader updReader = state.getMarcRecordReader();
            final String updRecordId = updReader.getRecordId();
            final int updAgencyId = updReader.getAgencyIdAsInt();

            // Perform check of 002a and b,c - 870970 only
            if (RawRepo.COMMON_AGENCY == updAgencyId) {
                final String validatePreviousFaustMessage = validatePreviousFaust(updReader);
                if (StringUtils.isNotEmpty(validatePreviousFaustMessage)) {
                    return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, validatePreviousFaustMessage);
                }
            }

            // Enrich the record in case the template is the metakompas template with only field 001, 004 and 665
            if ("metakompas".equals(state.getUpdateServiceRequestDTO().getSchemaName()) && !marcRecord.getFields().isEmpty()) {
                final StopWatch watch = new Log4JStopWatch("opencatBusiness.metacompass");
                try {
                    final String trackingId = MDC.get(MDC_TRACKING_ID_LOG_CONTEXT);
                    marcRecord = state.getOpencatBusiness().metacompass(marcRecord, trackingId);
                    MetakompasHandler.createMetakompasSubjectRecords(children, state, rawRepo, marcRecord, settings);
                } catch (UpdateException | OpencatBusinessConnectorException ex) {
                    return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, ex.getMessage());
                } finally {
                    watch.stop();
                }
            }

            addDoubleRecordFrontendActionIfNecessary();

            LOGGER.info("Split record into records to store in rawrepo. LibraryGroup is {}", state.getLibraryGroup().toString());

            List<MarcRecord> records = state.getLibraryRecordsHandler().recordDataForRawRepo(marcRecord, state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), state.getLibraryGroup(), state.getMessages(), state.isAdmin());
            LOGGER.info("Got {} records from LibraryRecordsHandler.recordDataForRawRepo", records.size());
            for (MarcRecord rec : records) {
                LOGGER.info("Create sub actions for record:\n{}", rec);
                reader = new MarcRecordReader(rec);
                final String recordId = reader.getRecordId();
                final int agencyId = reader.getAgencyIdAsInt();

                if (reader.markedForDeletion() && !rawRepo.recordExists(recordId, agencyId)) {
                    final String message = String.format(state.getMessages().getString("operation.delete.non.existing.record"), recordId, agencyId);
                    return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
                }

                if (RawRepo.DBC_AGENCY_LIST.contains(Integer.toString(agencyId))) {
                    if (!updReader.markedForDeletion() &&
                            !state.getVipCoreService().hasFeature(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), VipCoreLibraryRulesConnector.Rule.AUTH_CREATE_COMMON_RECORD) &&
                            !rawRepo.recordExists(updRecordId, updAgencyId)) {
                        final String message = String.format(state.getMessages().getString("common.record.creation.not.allowed"), state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId());
                        return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
                    }
                    children.add(new UpdateCommonRecordAction(state, settings, rec));
                } else if (agencyId == RawRepo.SCHOOL_COMMON_AGENCY) {
                    children.add(new UpdateSchoolCommonRecord(state, settings, rec));
                } else {
                    if (agencyId == RawRepo.DBC_ENRICHMENT && commonRecordExists(records, rec, updAgencyId)) {
                        if (RawRepo.isSchoolEnrichment(agencyId)) {
                            children.add(new UpdateSchoolEnrichmentRecordAction(state, settings, rec));
                        } else {
                            performActionsForRemovedLITWeekNumber(rec);
                            children.add(new UpdateEnrichmentRecordAction(state, settings, rec, updAgencyId));
                        }
                    } else if (state.getVipCoreService().hasFeature(Integer.toString(agencyId), VipCoreLibraryRulesConnector.Rule.CREATE_ENRICHMENTS) ||
                            state.getVipCoreService().hasFeature(Integer.toString(agencyId), VipCoreLibraryRulesConnector.Rule.AUTH_METACOMPASS)) {
                        if (commonRecordExists(records, rec)) {
                            if (RawRepo.isSchoolEnrichment(agencyId)) {
                                children.add(new UpdateSchoolEnrichmentRecordAction(state, settings, rec));
                            } else {
                                children.add(new UpdateEnrichmentRecordAction(state, settings, rec, RawRepo.COMMON_AGENCY));
                            }
                        } else {
                            if (checkForExistingCommonFaust(recordId)) {
                                String message = String.format(state.getMessages().getString("record.not.allowed.deleted.common.record"), recordId);
                                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
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
                        children.add(new DoubleRecordCheckingAction(state, settings, marcRecord));
                    } else {
                        String message = String.format(state.getMessages().getString("double.record.frontend.unknown.key"), state.getUpdateServiceRequestDTO().getDoubleRecordKey());
                        return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
                    }
                } else if (state.getLibraryGroup().isFBS() || state.getLibraryGroup().isDBC() && StringUtils.isEmpty(state.getUpdateServiceRequestDTO().getDoubleRecordKey())) {
                    children.add(new DoubleRecordCheckingAction(state, settings, marcRecord));
                }
            }
            return ServiceResult.newOkResult();
        } catch (VipCoreException | UnsupportedEncodingException | JAXBException | JSONBException e) {
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, e.getMessage());
        }
    }

    /**
     * Checks if the record id exists as a common record and if not checks if there is a 002a link to the id
     *
     * @param recordId The recordId to check for
     * @return true if the recordId exists as common record
     * @throws UpdateException Update error
     * @throws SolrException   Solr error
     */
    private boolean checkForExistingCommonFaust(String recordId) throws UpdateException, SolrException {
        if (state.getRawRepo().recordExistsMaybeDeleted(recordId, RawRepo.COMMON_AGENCY)) {
            return true;
        }

        String solrQuery = createSolrQuery(false, recordId, "002a", recordId);
        return state.getSolrFBS().hasDocuments(solrQuery);
    }


    /**
     * This function handles the creation date (001 *d).
     * If the record already exists, and there is a creation date on the existing record then use that value
     * If the record is new then set creation date to the current date
     * <p>
     * Only applicable for agencies which uses enrichments (i.e. FFU and lokbib are ignored)
     *
     * @param reader MarcRecordReader of the record to be checked
     * @throws UpdateException              Update error
     * @throws UnsupportedEncodingException some conversion of a record went wrong
     */
    void setCreatedDate(MarcRecordReader reader) throws UpdateException, UnsupportedEncodingException, VipCoreException {
        LOGGER.info("Original record creation date (001 *d): '{}'", reader.getValue("001", "d"));

        // If it is a DBC record then the creation date can't be changed unless the user has admin privileges
        final String groupId = state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId();
        if (RawRepo.DBC_AGENCY_LIST.contains(reader.getAgencyId())) {
            if (!state.isAdmin()) {
                if (rawRepo.recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())) {
                    setCreationDateToExistingCreationDate(marcRecord);
                } else {
                    // For specifically 870974 (literature analysis) must have a creation date equal to the parent
                    // record creation date
                    if ("870974".equals(reader.getAgencyId())) {
                        setCreationDateToParentCreationDate(marcRecord);
                    } else {
                        setCreationDateToToday(marcRecord);
                    }
                }
            }
        } else if (state.getVipCoreService().hasFeature(groupId, VipCoreLibraryRulesConnector.Rule.USE_ENRICHMENTS)) {
            // If input record doesn't have 001 *d, agency id FBS and the record is new, so set 001 *d
            if (!reader.hasSubfield("001", "d") &&
                    rawRepo.recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())) {
                setCreationDateToExistingCreationDate(marcRecord);
            } else {
                setCreationDateToToday(marcRecord);
            }
        }

        LOGGER.info("Adjusted record creation date (001 *d): '{}'", reader.getValue("001", "d"));
    }

    // Set 001 *d equal to that field in the existing record if the existing record as a 001 *d value
    private void setCreationDateToExistingCreationDate(MarcRecord marcRecord) throws UpdateException, UnsupportedEncodingException {
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);
        final MarcRecord existingRecord = RecordContentTransformer.decodeRecord(rawRepo.fetchRecord(reader.getRecordId(), reader.getAgencyIdAsInt()).getContent());

        final MarcRecordReader existingReader = new MarcRecordReader(existingRecord);
        final String existingCreatedDate = existingReader.getValue("001", "d");
        if (!StringUtils.isEmpty(existingCreatedDate)) {
            new MarcRecordWriter(marcRecord).addOrReplaceSubfield("001", "d", existingCreatedDate);
        }
    }

    // Set 001 *d to today's date if the field doesn't have a value
    private void setCreationDateToToday(MarcRecord marcRecord) {
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);
        final String createdDate = reader.getValue("001", "d");
        if (StringUtils.isEmpty(createdDate)) {
            new MarcRecordWriter(marcRecord).setCreationTimestamp();
        }
    }

    private void setCreationDateToParentCreationDate(MarcRecord marcRecord) throws UpdateException, UnsupportedEncodingException {
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);
        final MarcRecord existingRecord = RecordContentTransformer.decodeRecord(rawRepo.fetchRecord(reader.getParentRecordId(), reader.getParentAgencyIdAsInt()).getContent());

        final MarcRecordReader existingReader = new MarcRecordReader(existingRecord);
        final String existingCreatedDate = existingReader.getValue("001", "d");
        if (!StringUtils.isEmpty(existingCreatedDate)) {
            new MarcRecordWriter(marcRecord).addOrReplaceSubfield("001", "d", existingCreatedDate);
        }
    }

    private void addDoubleRecordFrontendActionIfNecessary() throws UpdateException {
        final boolean doubleRecordPossible = state.isDoubleRecordPossible();
        final boolean fbsMode = state.getLibraryGroup().isFBS();
        final boolean doubleRecordKeyEmpty = StringUtils.isEmpty(state.getUpdateServiceRequestDTO().getDoubleRecordKey());
        if (doubleRecordPossible && fbsMode && doubleRecordKeyEmpty) {
            // This action must be run before the rest of the actions because we do not use xa compatible postgres connections
            children.add(new DoubleRecordFrontendAction(state, settings));
        }
    }

    private void logRecordInfo(MarcRecordReader updReader) throws UpdateException {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Delete?..................: " + updReader.markedForDeletion());
            LOGGER.info("Library group?...........: " + state.getLibraryGroup());
            LOGGER.info("Schema name?.............: " + state.getSchemaName());
            LOGGER.info("RR record exists?........: " + rawRepo.recordExists(updReader.getRecordId(), updReader.getAgencyIdAsInt()));
            LOGGER.info("agency id?...............: " + updReader.getAgencyIdAsInt());
            LOGGER.info("RR common library?.......: " + (updReader.getAgencyIdAsInt() == RawRepo.COMMON_AGENCY));
            LOGGER.info("DBC agency?..............: " + RawRepo.DBC_AGENCY_LIST.contains(updReader.getAgencyId()));
            LOGGER.info("isDoubleRecordPossible?..: " + state.isDoubleRecordPossible());
            LOGGER.info("User is admin?...........: " + state.isAdmin());
        }
    }

    private boolean commonRecordExists(List<MarcRecord> records, MarcRecord rec) throws UpdateException {
        return commonRecordExists(records, rec, RawRepo.COMMON_AGENCY);
    }

    private boolean commonRecordExists(List<MarcRecord> records, MarcRecord rec, int parentAgencyId) throws UpdateException {
        final MarcRecordReader reader = new MarcRecordReader(rec);
        final String recordId = reader.getRecordId();
        if (rawRepo.recordExists(recordId, parentAgencyId)) {
            return true;
        }
        for (MarcRecord marcRecord : records) {
            final MarcRecordReader recordReader = new MarcRecordReader(marcRecord);
            final String checkRecordId = recordReader.getRecordId();
            final int checkAgencyId = recordReader.getAgencyIdAsInt();
            if (recordId.equals(checkRecordId) && parentAgencyId == checkAgencyId) {
                return true;
            }
        }
        return false;
    }

    private ServiceResult checkRecordForUpdatability() throws UpdateException, SolrException {
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);
        if (!reader.markedForDeletion()) {
            return ServiceResult.newOkResult();
        }
        final String recordId = reader.getRecordId();
        final String motherRecordId = state.getSolrFBS().getOwnerOf002(SolrServiceIndexer.createGetOwnerOf002QueryDBCOnly("002a", recordId));
        if (!motherRecordId.equals("")) {
            return ServiceResult.newOkResult();
        }
        final int agencyId = reader.getAgencyIdAsInt();
        int rawRepoAgencyId = agencyId;
        if (agencyId == RawRepo.DBC_ENRICHMENT) {
            rawRepoAgencyId = RawRepo.COMMON_AGENCY;
        }
        final RecordId newRecordId = new RecordId(recordId, rawRepoAgencyId);
        LOGGER.debug(String.format("UpdateOperationAction.checkRecordForDeleteability().newRecordId: %s", newRecordId));
        final Set<RecordId> recordIdSet = rawRepo.children(newRecordId);
        LOGGER.debug(String.format("UpdateOperationAction.checkRecordForDeleteability().recordIdSet: %s", recordIdSet));
        if (!recordIdSet.isEmpty()) {
            for (RecordId childRecordId : recordIdSet) {
                // If all child records are 870974 littolk then the record can be deleted anyway
                // The littolk children will be marked for deletion later in the flow
                if (childRecordId.getAgencyId() != 870974) {
                    String message = String.format(state.getMessages().getString("delete.record.children.error"), recordId);
                    return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
                }
            }
        }
        return ServiceResult.newOkResult();
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
        final String readerRecordId = reader.getRecordId();
        final int readerAgencyId = reader.getAgencyIdAsInt();
        if (reader.markedForDeletion()) {
            // Handle deletion of existing record
            if (rawRepo.recordExists(readerRecordId, readerAgencyId)) {
                final Record existingRecord = rawRepo.fetchRecord(readerRecordId, readerAgencyId);
                final MarcRecord existingMarc = RecordContentTransformer.decodeRecord(existingRecord.getContent());
                final MarcRecordReader existingRecordReader = new MarcRecordReader(existingMarc);

                // Deletion of 002a - check for holding on 001a
                final Set<Integer> holdingAgencies001 = state.getHoldingsItems().getAgenciesThatHasHoldingsForId(readerRecordId);
                if (!holdingAgencies001.isEmpty()) {
                    for (String previousFaust : existingRecordReader.getCentralAliasIds()) {
                        if (!state.getSolrFBS().hasDocuments(SolrServiceIndexer.createSubfieldQueryDBCOnly("001a", previousFaust))) {
                            return state.getMessages().getString("delete.record.holdings.on.002a");
                        }
                    }
                }

                // Deletion of 002a - check for holding on 002a - if there is, then check whether the 002a record exist - if not, fail
                for (String previousFaust : existingRecordReader.getCentralAliasIds()) {
                    final Set<Integer> holdingAgencies002 = state.getHoldingsItems().getAgenciesThatHasHoldingsForId(previousFaust);
                    if (!holdingAgencies002.isEmpty() && !rawRepo.recordExists(previousFaust, readerAgencyId)) {
                        return state.getMessages().getString("delete.record.holdings.on.002a");
                    }
                }
            }
        } else {
            // No matter if it's a new record or updating an existing, none of eventual 002a may contain the records id
            for (String aValue : reader.getValues("002", "a")) {
                if (aValue.equals(readerRecordId)) {
                    return state.getMessages().getString("update.record.with.001.equals.002a.links");
                }
            }
            // Handle either new record or update of existing record
            final boolean recordExists = rawRepo.recordExists(readerRecordId, readerAgencyId);

            for (String aValue : reader.getValues("002", "a")) {
                final String solrQuery = createSolrQuery(recordExists, readerRecordId, "002a", aValue);

                if (state.getSolrFBS().hasDocuments(solrQuery)) {
                    return state.getMessages().getString("update.record.with.002.links");
                }
            }

            for (String xValue : reader.getValues("002", "x")) {
                final String solrQuery = createSolrQuery(recordExists, readerRecordId, "002x", xValue);

                if (state.getSolrFBS().hasDocuments(solrQuery)) {
                    return state.getMessages().getString("update.record.with.002.links");
                }
            }

            // Removal of 002a reference is allowed if either the referenced post still exists (no matter if there is holding or not)
            // or if the referenced record is deleted/non-existent and does NOT have holdings
            if (recordExists) {
                final Record currentRecord = rawRepo.fetchRecord(readerRecordId, readerAgencyId);
                final MarcRecord currentMarc = RecordContentTransformer.decodeRecord(currentRecord.getContent());
                final MarcRecordReader currentReader = new MarcRecordReader(currentMarc);
                final List<String> currentPreviousFaustList = currentReader.getCentralAliasIds();
                final List<String> previousFaustList = reader.getCentralAliasIds();

                // The 002 field is repeatable but *a is not
                // So we need to compare the list of 002a fields between new and current
                final List<String> removedPreviousFaust = new ArrayList<>();
                for (String f : currentPreviousFaustList) {
                    if (!previousFaustList.contains(f)) {
                        removedPreviousFaust.add(f);
                    }
                }

                for (String m : removedPreviousFaust) {
                    if (state.getRawRepo().recordDoesNotExistOrIsDeleted(m, RawRepo.COMMON_AGENCY) &&
                            !state.getHoldingsItems().getAgenciesThatHasHoldingsForId(m).isEmpty()) {
                        return state.getMessages().getString("update.record.holdings.on.002a");
                    }
                }
            }
        }

        return null;
    }

    private String createSolrQuery(boolean recordExists, String recordId, String subfieldName, String subfieldValue) {
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
        LOGGER.debug("Checking for n55 field");
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);
        final MarcRecordWriter writer = new MarcRecordWriter(marcRecord);

        if (reader.hasSubfield("n55", "a")) {
            final String dateString = reader.getValue("n55", "a");
            if (dateString != null && !dateString.isEmpty()) {
                final boolean recordExists = rawRepo.recordExistsMaybeDeleted(reader.getRecordId(), reader.getAgencyIdAsInt());
                // We only want to set the created date to a specific value if the record is new
                if (!recordExists) {
                    final Instant instant = formatter.parse(dateString, Instant::from);

                    state.setCreateOverwriteDate(instant);
                    LOGGER.info("Found overwrite create date value: {}. Field has been removed from the record", instant);
                }
            }

            // Always remove n55 *a as we don't ever want that field in rawrepo.
            writer.removeField("n55");
        }
    }

    /**
     * If the record already exists and d09 *z with LIT<week number> has been removed in this new version,
     * then all linked 870974 littolk records must be deleted
     * Because d09, which contains the LIT code is placed in the 191919 record, we only do it for such.
     *
     * @throws UpdateException              In case of an error.
     * @throws UnsupportedEncodingException If the record can't be decoded
     */
    private void performActionsForRemovedLITWeekNumber(MarcRecord marcRecord) throws UpdateException, UnsupportedEncodingException {
        try {
            final MarcRecordReader reader = new MarcRecordReader(marcRecord);
            LOGGER.debug("GOT REC {}", marcRecord);

            // Check if a 191919 record
            if (RawRepo.DBC_ENRICHMENT != reader.getAgencyIdAsInt()) {
                LOGGER.debug("Not a 191919");
                return;
            }

            if (!rawRepo.recordExists(reader.getRecordId(), RawRepo.DBC_ENRICHMENT)) {
                LOGGER.debug("No existing record");
                return;
            }

            Pattern p = Pattern.compile("^LIT[0-9]{6}");
            // There is a d09zLIT in incoming record
            if (!reader.getSubfieldValueMatchers("d09", "z", p).isEmpty()) {
                LOGGER.debug("there is a d09");
                return;
            }

            final MarcRecord existingRecord = RecordContentTransformer.decodeRecord(rawRepo.fetchMergedDBCRecord(reader.getRecordId(), RawRepo.DBC_ENRICHMENT).getContent());
            final MarcRecordReader existingReader = new MarcRecordReader(existingRecord);
            // There isn't a d09zLIT in incoming record and there is one in existing record
            if (!existingReader.getSubfieldValueMatchers("d09", "z", p).isEmpty()) {
                final Set<RecordId> childrenRecords = state.getRawRepo().children(existingRecord);
                for (RecordId recordId : childrenRecords) {
                    if (recordId.getAgencyId() == RawRepo.LITTOLK_AGENCY) {
                        final MarcRecord littolkEnrichment = RecordContentTransformer.decodeRecord(state.getRawRepo().
                                fetchRecord(recordId.getBibliographicRecordId(), RawRepo.DBC_ENRICHMENT).getContent());
                        new MarcRecordWriter(littolkEnrichment).markForDeletion();
                        children.add(new UpdateEnrichmentRecordAction(state, settings, littolkEnrichment));

                        final MarcRecord littolkRecord = RecordContentTransformer.decodeRecord(state.getRawRepo().
                                fetchRecord(recordId.getBibliographicRecordId(), RawRepo.LITTOLK_AGENCY).getContent());
                        new MarcRecordWriter(littolkRecord).markForDeletion();
                        children.add(new DeleteCommonRecordAction(state, settings, littolkRecord));
                    }
                }

            }
        } catch (Throwable e) {
            LOGGER.info("performActionsForRemovedLITWeekNumber fails with : {}", e.toString());
            throw e;
        }
    }
}
