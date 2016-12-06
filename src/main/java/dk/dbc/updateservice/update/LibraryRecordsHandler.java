package dk.dbc.updateservice.update;

import dk.dbc.iscrum.records.*;
import dk.dbc.iscrum.utils.json.Json;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.openagency.client.OpenAgencyException;
import dk.dbc.updateservice.actions.ServiceResult;
import dk.dbc.updateservice.actions.UpdateMode;
import dk.dbc.updateservice.dto.AuthenticationDto;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.ws.JNDIResources;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Class to manipulate library records for a local library. Local records and
 * local extended records.
 * <p>
 */
@Stateless
public class LibraryRecordsHandler {
    private static final XLogger logger = XLoggerFactory.getXLogger(LibraryRecordsHandler.class);
    static final String CREATE_ENRICHMENT_RECORDS_FUNCTION_NAME = "shouldCreateEnrichmentRecords";

    @EJB
    private Scripter scripter;

    @EJB
    private OpenAgencyService openAgencyService;

    @EJB
    private RawRepo rawRepo;

    @Resource(lookup = JNDIResources.SETTINGS_NAME)
    private Properties settings;

    public LibraryRecordsHandler() {
    }

    protected LibraryRecordsHandler(Scripter scripter) {
        this.scripter = scripter;
    }

    /**
     * Tests if a record is published
     *
     * @param record The record.
     * @return <code>true</code> if published
     * <code>false</code> otherwise.
     * @throws ScripterException
     */
    public boolean isRecordInProduction(MarcRecord record) throws ScripterException {
        logger.entry(record);
        Object jsResult = null;
        try {
            try {
                ObjectMapper mapper = new ObjectMapper();
                String json = mapper.writeValueAsString(record);
                logger.trace("Kalder IRP - data {}", json);
                jsResult = scripter.callMethod("isRecordInProduction", json);
            } catch (IOException ex) {
                throw new ScripterException("Error when executing JavaScript function: isRecordInProduction", ex);
            }
            logger.trace("Result from isRecordInProduction JS ({}): {}", jsResult.getClass().getName(), jsResult);
            if (jsResult instanceof Boolean) {
                logger.exit();
                return ((Boolean) jsResult);
            }
            throw new ScripterException("The JavaScript function %s must return a boolean value.", "isRecordInProduction");
        } finally {
            logger.exit(jsResult);
        }
    }

    /**
     * Tests if a record contains any classification data.
     *
     * @param record The record.
     * @return <code>true</code> if classifications where found,
     * <code>false</code> otherwise.
     * @throws ScripterException
     */
    public boolean hasClassificationData(MarcRecord record) throws ScripterException {
        logger.entry(record);
        Object jsResult = null;
        try {
            try {
                ObjectMapper mapper = new ObjectMapper();
                String json = mapper.writeValueAsString(record);
                jsResult = scripter.callMethod("hasClassificationData", json);
            } catch (IOException ex) {
                throw new ScripterException("Error when executing JavaScript function: hasClassificationData", ex);
            }
            logger.trace("Result from hasClassificationData JS ({}): {}", jsResult.getClass().getName(), jsResult);
            if (jsResult instanceof Boolean) {
                logger.exit();
                return ((Boolean) jsResult);
            }
            throw new ScripterException("The JavaScript function %s must return a boolean value.", "hasClassificationData");
        } finally {
            logger.exit(jsResult);
        }
    }

    /**
     * Tests if the classifications has changed between 2 records.
     * <p>
     * This method is mainly used to checks for changes between 2 versions of
     * the same record.
     *
     * @param oldRecord The old record.
     * @param newRecord The new record.
     * @return <code>true</code> if there is changes in the classifications,
     * <code>false</code> otherwise.
     * @throws ScripterException
     */
    public boolean hasClassificationsChanged(MarcRecord oldRecord, MarcRecord newRecord) throws ScripterException {
        logger.entry(oldRecord, newRecord);
        Object jsResult = null;
        try {
            try {
                ObjectMapper mapper = new ObjectMapper();
                String jsonOldRecord = mapper.writeValueAsString(oldRecord);
                String jsonNewRecord = mapper.writeValueAsString(newRecord);
                jsResult = scripter.callMethod("hasClassificationsChanged", jsonOldRecord, jsonNewRecord);
            } catch (IOException ex) {
                throw new ScripterException("Error when executing JavaScript function: hasClassificationsChanged", ex);
            }
            logger.debug("Result from hasClassificationsChanged JS ({}): {}", jsResult.getClass().getName(), jsResult);
            if (jsResult instanceof Boolean) {
                return ((Boolean) jsResult);
            }
            throw new ScripterException("The JavaScript function %s must return a boolean value.", "hasClassificationsChanged");
        } finally {
            logger.exit(jsResult);
        }
    }

    /**
     * Tests if we should create new enrichment records for a common record.
     * <p>
     * This function is implementated by calling the JavaScript function: shouldCreateEnrichmentRecords
     * </p>
     * <p>
     * This function returns a ServiceResult with status <code>OK</code> if we should create enrichment
     * records. Otherwise we should not create enrichment records. An entry in the result is added to
     * explain why enrichment records should not be created.
     * </p>
     * <p>
     * This feature is provided so the ServiceAction has a change to write to the business log why enrichment
     * records should not be created.
     * </p>
     *
     * @param currentCommonRecord  The current common record in rawrepo.
     * @param updatingCommonRecord The common record to create enrichment records for.
     * @return ServiceResult with the result.
     * @throws ScripterException In case of an error from JavaScript.
     */
    public ServiceResult shouldCreateEnrichmentRecords(Properties settings, MarcRecord currentCommonRecord, MarcRecord updatingCommonRecord) throws ScripterException {
        logger.entry(settings, currentCommonRecord, updatingCommonRecord);
        ServiceResult result = null;
        try {
            Object jsResult = scripter.callMethod(CREATE_ENRICHMENT_RECORDS_FUNCTION_NAME, settings, Json.encode(currentCommonRecord), Json.encode(updatingCommonRecord));
            logger.debug("Result from " + CREATE_ENRICHMENT_RECORDS_FUNCTION_NAME + " JS (" + jsResult.getClass().getName() + "): " + jsResult);
            if (jsResult instanceof String) {
                return result = Json.decode(jsResult.toString(), ServiceResult.class);
            }
            throw new ScripterException(String.format("The JavaScript function %s must return a String value.", CREATE_ENRICHMENT_RECORDS_FUNCTION_NAME));
        } catch (IOException ex) {
            logger.catching(ex);
            throw new ScripterException("Error when executing JavaScript function: " + CREATE_ENRICHMENT_RECORDS_FUNCTION_NAME, ex);
        } finally {
            logger.exit(result);
        }
    }

    /**
     * Creates an extended library record based on the bibliographic
     * classification elements of the record from DBC
     *
     * @param currentCommonRecord The record from DBC.
     * @param agencyId            The library id for the library, that the extended
     *                            record will be created for.
     * @return Returns the library record after it has been updated.
     * <code>libraryRecord</code> may have changed.
     * @throws ScripterException
     */
    public MarcRecord createLibraryExtendedRecord(MarcRecord currentCommonRecord, MarcRecord updatingCommonRecord, String agencyId) throws ScripterException {
        logger.entry(currentCommonRecord, updatingCommonRecord, agencyId);
        Object jsResult = null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            String jsonCurrentCommonRecord = mapper.writeValueAsString(currentCommonRecord);
            String jsonUpdatingCommonRecord = mapper.writeValueAsString(updatingCommonRecord);
            jsResult = scripter.callMethod("createLibraryExtendedRecord", jsonCurrentCommonRecord, jsonUpdatingCommonRecord, Integer.valueOf(agencyId));
            logger.debug("Result from createLibraryExtendedRecord JS ({}): {}", jsResult.getClass().getName(), jsResult);
            if (jsResult instanceof String) {
                return mapper.readValue(jsResult.toString(), MarcRecord.class);
            }
            throw new ScripterException(String.format("The JavaScript function %s must return a String value.", "createLibraryExtendedRecord"));
        } catch (IOException ex) {
            throw new ScripterException("Error when executing JavaScript function: createLibraryExtendedRecord", ex);
        } finally {
            logger.exit(jsResult);
        }
    }

    /**
     * This method updates an extended library record based on the bibliographic
     * classification elements of the record from DBC
     *
     * @param currentCommonRecord  The record from DBC.
     * @param updatingCommonRecord The record that is being updated.
     * @param enrichmentRecord     The library extended record.
     * @return Returns the library record after it has been updated.
     * <code>enrichmentRecord</code> may have changed.
     * @throws ScripterException
     */
    public MarcRecord updateLibraryExtendedRecord(MarcRecord currentCommonRecord, MarcRecord updatingCommonRecord, MarcRecord enrichmentRecord) throws ScripterException {
        logger.entry(currentCommonRecord, updatingCommonRecord, enrichmentRecord);
        Object jsResult = null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            String jsonCurrentCommonRecord = mapper.writeValueAsString(currentCommonRecord);
            String jsonUpdatingCommonRecord = mapper.writeValueAsString(updatingCommonRecord);
            String jsonEnrichmentRecord = mapper.writeValueAsString(enrichmentRecord);

            jsResult = scripter.callMethod("updateLibraryExtendedRecord", jsonCurrentCommonRecord, jsonUpdatingCommonRecord, jsonEnrichmentRecord);

            logger.debug("Result from updateLibraryExtendedRecord JS ({}): {}", jsResult.getClass().getName(), jsResult);

            if (jsResult instanceof String) {
                return mapper.readValue(jsResult.toString(), MarcRecord.class);
            }

            throw new ScripterException(String.format("The JavaScript function %s must return a String value.", "updateLibraryExtendedRecord"));
        } catch (IOException ex) {
            throw new ScripterException("Error when executing JavaScript function: updateLibraryExtendedRecord", ex);
        } finally {
            logger.exit(jsResult);
        }
    }

    public MarcRecord correctLibraryExtendedRecord(MarcRecord commonRecord, MarcRecord enrichmentRecord) throws ScripterException {
        logger.entry(commonRecord, enrichmentRecord);
        Object jsResult = null;

        try {
            ObjectMapper mapper = new ObjectMapper();
            String jsonCommonRecord = mapper.writeValueAsString(commonRecord);
            String jsonEnrichmentRecord = mapper.writeValueAsString(enrichmentRecord);

            jsResult = scripter.callMethod("correctLibraryExtendedRecord", jsonCommonRecord, jsonEnrichmentRecord);

            logger.debug("Result from correctLibraryExtendedRecord JS ({}): {}", jsResult.getClass().getName(), jsResult);

            if (jsResult instanceof String) {
                return mapper.readValue(jsResult.toString(), MarcRecord.class);
            }

            throw new ScripterException(String.format("The JavaScript function %s must return a String value.", "correctLibraryExtendedRecord"));
        } catch (IOException ex) {
            throw new ScripterException("Error when executing JavaScript function: correctLibraryExtendedRecord", ex);
        } finally {
            logger.exit(jsResult);
        }
    }

    /**
     * This function will split (if necessary) the input record into command record and DBC enrichment record
     *
     * @param record            The record to be updated
     * @param authenticationDto Auth DTO from the ws request
     * @param updateMode        Whether it is a FBS or DataIO template
     * @return
     * @throws OpenAgencyException
     * @throws UnsupportedEncodingException
     * @throws UpdateException
     */
    public List<MarcRecord> recordDataForRawRepo(MarcRecord record, AuthenticationDto authenticationDto, UpdateMode updateMode) throws OpenAgencyException, UnsupportedEncodingException, UpdateException {
        logger.entry(record, authenticationDto, updateMode);

        List<MarcRecord> result = new ArrayList<>();

        try {
            if (updateMode.isFBSMode()) {
                result = recordDataForRawRepoFBS(record, authenticationDto.getGroupId());
            } else { // Assuming DataIO mode
                result = recordDataForRawRepoDataIO(record, authenticationDto.getGroupId());
            }

            return result;
        } finally {
            logger.exit(result);
        }
    }

    List<MarcRecord> recordDataForRawRepoFBS(MarcRecord record, String groupId) throws OpenAgencyException, UpdateException, UnsupportedEncodingException {
        logger.entry(record, groupId);

        List<MarcRecord> result = new ArrayList<>();

        try {
            DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
            LocalDateTime dateTime = LocalDateTime.now();

            logger.info("New date for 001 *c is {}", dateTime.format(format));

            result = splitRecordFBS(record, groupId);

            for (MarcRecord r : result) {
                MarcRecordWriter writer = new MarcRecordWriter(r);
                writer.addOrReplaceSubfield("001", "c", dateTime.format(format));
            }

            return result;
        } finally {
            logger.exit(result);
        }
    }

    List<MarcRecord> recordDataForRawRepoDataIO(MarcRecord record, String groupId) throws OpenAgencyException {
        logger.entry(record, groupId);

        List<MarcRecord> result = new ArrayList<>();

        try {
            if (openAgencyService.hasFeature(groupId, LibraryRuleHandler.Rule.USE_ENRICHMENTS) ||
                    openAgencyService.hasFeature(groupId, LibraryRuleHandler.Rule.AUTH_ROOT)) {

                logger.info("Record has either USE_ENRICHMENT or AUTH_ROOT so calling splitCompleteBasisRecord");
                result = splitRecordDataIO(record);
            } else {
                logger.info("Record has neither USE_ENRICHMENT nor AUTH_ROOT so returning record");
                result = Arrays.asList(record);
            }

            return result;

        } finally {
            logger.exit(result);
        }
    }

    /**
     * If the FBS record is an existing common (870970) record then split it into updated common record and
     * DBC enrichment record
     *
     * @param record  The record to be updated
     * @param groupId The groupId from the ws request
     * @return List containing common and DBC record
     * @throws OpenAgencyException
     * @throws UpdateException
     * @throws UnsupportedEncodingException
     */
    List<MarcRecord> splitRecordFBS(MarcRecord record, String groupId) throws OpenAgencyException, UpdateException, UnsupportedEncodingException {
        logger.entry(record, groupId);

        try {
            MarcRecordReader reader = new MarcRecordReader(record);
            if (!reader.agencyIdAsInteger().equals(RawRepo.RAWREPO_COMMON_LIBRARY)) {
                logger.info("Agency id of record is not 870970 - returning same record");
                return Arrays.asList(record);
            }

            NoteAndSubjectExtensionsHandler noteAndSubjectExtensionsHandler = new NoteAndSubjectExtensionsHandler(openAgencyService, rawRepo);

            MarcRecord correctedRecord = noteAndSubjectExtensionsHandler.recordDataForRawRepo(record, groupId);

            MarcRecordReader correctedRecordReader = new MarcRecordReader(correctedRecord);
            MarcRecord dbcEnrichmentRecord;

            String recId = correctedRecordReader.recordId();
            Integer agencyId = RawRepo.RAWREPO_COMMON_LIBRARY;

            MarcRecord curRecord;

            if (rawRepo.recordExists(recId, RawRepo.RAWREPO_COMMON_LIBRARY)) {
                curRecord = new RawRepoDecoder().decodeRecord(rawRepo.fetchRecord(recId, agencyId).getContent());
                correctedRecord = UpdateOwnership.mergeRecord(correctedRecord, curRecord);
                logger.info("correctedRecord after mergeRecord\n{}", correctedRecord);
            } else {
                logger.info("Common record [{}:{}] does not exist", recId, RawRepo.RAWREPO_COMMON_LIBRARY);
            }

            String owner = correctedRecordReader.getValue("996", "a");

            if (owner == null) {
                logger.debug("No owner in record.");

                return Arrays.asList(correctedRecord);
            } else {
                logger.info("Owner of record is {}", owner);
            }

            if (!rawRepo.recordExists(recId, RawRepo.COMMON_LIBRARY)) {
                logger.debug("DBC enrichment record [{}:{}] does not exist.", recId, RawRepo.COMMON_LIBRARY);
                dbcEnrichmentRecord = new MarcRecord();
                MarcField corrected001Field = new MarcField(correctedRecordReader.getField("001"));
                dbcEnrichmentRecord.getFields().add(corrected001Field);

                new MarcRecordWriter(dbcEnrichmentRecord).addOrReplaceSubfield("001", "b", RawRepo.COMMON_LIBRARY.toString());
            } else {
                logger.debug("DBC enrichment record [{}:{}] found.", recId, RawRepo.COMMON_LIBRARY);
                dbcEnrichmentRecord = new RawRepoDecoder().decodeRecord(rawRepo.fetchRecord(recId, RawRepo.COMMON_LIBRARY).getContent());
            }

            String recordStatus = correctedRecordReader.getValue("004", "r");
            if (recordStatus != null) {
                logger.debug("Replace 004 *r in DBC enrichment record with: {}", recordStatus);
                new MarcRecordWriter(dbcEnrichmentRecord).addOrReplaceSubfield("004", "r", recordStatus);
            }

            String recordType = correctedRecordReader.getValue("004", "a");
            if (recordType != null) {
                logger.debug("Replace 004 *a in DBC enrichment record with: {}", recordType);
                new MarcRecordWriter(dbcEnrichmentRecord).addOrReplaceSubfield("004", "a", recordType);
            }

            logger.info("correctedRecord\n{}", correctedRecord);
            logger.info("dbcEnrichmentRecord\n{}", dbcEnrichmentRecord);

            return Arrays.asList(correctedRecord, dbcEnrichmentRecord);
        } finally {
            logger.exit();
        }
    }

    /**
     * Split the input record into two record:
     * One with all normal Marc fields (001 - 999) and a new record with DBC fields
     *
     * @param record The record to be updated
     * @return List containing common and DBC record
     */
    List<MarcRecord> splitRecordDataIO(MarcRecord record) {
        logger.entry(record);

        try {
            MarcRecord dbcRecord = new MarcRecord();
            MarcRecord commonRecord = new MarcRecord();

            for (int i = 0; i < record.getFields().size(); i++) {
                MarcField field = record.getFields().get(i);
                if (field.getName().equals("001")) {
                    MarcField dbcField = new MarcField(field);
                    for (int d = 0; d < dbcField.getSubfields().size(); d++) {
                        if (dbcField.getSubfields().get(d).getName().equals("b")) {
                            dbcField.getSubfields().get(d).setValue(RawRepo.COMMON_LIBRARY.toString());
                        }
                    }
                    dbcRecord.getFields().add(dbcField);

                    MarcField commonField = new MarcField(field);
                    for (int c = 0; c < commonField.getSubfields().size(); c++) {
                        if (commonField.getSubfields().get(c).getName().equals("b")) {
                            commonField.getSubfields().get(c).setValue(RawRepo.RAWREPO_COMMON_LIBRARY.toString());
                        }
                    }
                    commonRecord.getFields().add(commonField);

                } else if (field.getName().equals("004")) {
                    dbcRecord.getFields().add(field);
                    commonRecord.getFields().add(field);
                } else if (field.getName().matches("[a-z].*")) {
                    dbcRecord.getFields().add(field);
                } else {
                    commonRecord.getFields().add(field);
                }
            }


            logger.info("commonRecord\n{}", commonRecord);
            logger.info("dbcRecord\n{}", dbcRecord);

            return Arrays.asList(commonRecord, dbcRecord);
        } finally {
            logger.exit();
        }
    }


    /**
     * Creates a 512 notefield from one record.
     * utilizes the create512 notefield functionality , which nexpects two records, one current, one updating
     * In this case we are not updating , but just wants a 512 field from existing data.
     *
     * @param record The record.
     * @return MarcField containing 512 data
     * @throws ScripterException
     */

    public MarcField fetchNoteField(MarcRecord record) throws ScripterException {
        logger.entry(record);

        // TODO create function for this, very similar code in all of the above
        MarcField mf = null;
        Object jsResult;
        ObjectMapper mapper = new ObjectMapper();
        try {
            try {
                String json = mapper.writeValueAsString(record);
                jsResult = scripter.callMethod("recategorizationNoteFieldFactory", json);
            } catch (IOException ex) {
                throw new ScripterException("Error when executing JavaScript function: fetchNoteField", ex);
            }
            logger.debug("Result from recategorizationNoteFieldFactory JS ({}): {}", jsResult.getClass().getName(), jsResult);

            if (!(jsResult instanceof String)) {
                throw new ScripterException("The JavaScript function %s must return a String value.", "recordDataForRawRepo");
            }
            return mf = mapper.readValue((String) jsResult, MarcField.class);

        } catch (IOException ex) {
            throw new ScripterException("Error when executing JavaScript function: changeUpdateRecordForUpdate", ex);
        } finally {
            logger.exit(mf);
        }
    }
}
