package dk.dbc.updateservice.update;

import dk.dbc.iscrum.records.MarcField;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.utils.json.Json;
import dk.dbc.updateservice.actions.ServiceResult;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.javascript.ScripterException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.type.TypeFactory;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

/**
 * Class to manipulate library records for a local library. Local records and
 * local extended records.
 * <p>
 *
 * @author stp
 */
public class LibraryRecordsHandler {
    private static final XLogger logger = XLoggerFactory.getXLogger(LibraryRecordsHandler.class);
    static final String CREATE_ENRICHMENT_RECORDS_FUNCTION_NAME = "shouldCreateEnrichmentRecords";

    private Scripter scripter;

    public LibraryRecordsHandler(Scripter scripter) {
        this.scripter = scripter;
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

            logger.trace("Result from JS ({}): {}", jsResult.getClass().getName(), jsResult);

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

            logger.trace("Result from JS ({}): {}", jsResult.getClass().getName(), jsResult);

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
            Object jsResult = scripter.callMethod(CREATE_ENRICHMENT_RECORDS_FUNCTION_NAME,
                    settings, Json.encode(currentCommonRecord), Json.encode(updatingCommonRecord));

            logger.trace("Result from JS ({}): {}", jsResult.getClass().getName(), jsResult);

            if (jsResult instanceof String) {
                return result = Json.decode(jsResult.toString(), ServiceResult.class);
            }

            throw new ScripterException(String.format("The JavaScript function %s must return a String value.", CREATE_ENRICHMENT_RECORDS_FUNCTION_NAME));
        } catch (IOException ex) {
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
    public MarcRecord createLibraryExtendedRecord(MarcRecord currentCommonRecord, MarcRecord updatingCommonRecord, int agencyId) throws ScripterException {
        logger.entry(currentCommonRecord, updatingCommonRecord, agencyId);

        Object jsResult = null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            String jsonCurrentCommonRecord = mapper.writeValueAsString(currentCommonRecord);
            String jsonUpdatingCommonRecord = mapper.writeValueAsString(updatingCommonRecord);

            jsResult = scripter.callMethod("createLibraryExtendedRecord", jsonCurrentCommonRecord, jsonUpdatingCommonRecord, agencyId);

            logger.trace("Result from JS ({}): {}", jsResult.getClass().getName(), jsResult);

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

            logger.trace("Result from JS ({}): {}", jsResult.getClass().getName(), jsResult);

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

            logger.trace("Result from JS ({}): {}", jsResult.getClass().getName(), jsResult);

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

    public List<MarcRecord> recordDataForRawRepo(MarcRecord record, String userId, String groupId) throws ScripterException {
        logger.entry(record);

        List<MarcRecord> result = null;
        try {
            Object jsResult;
            ObjectMapper mapper = new ObjectMapper();
            String jsonRecord = mapper.writeValueAsString(record);

            try {
                jsResult = scripter.callMethod("recordDataForRawRepo", jsonRecord, userId, groupId);
            } catch (IllegalStateException ex) {
                logger.error("Error when executing JavaScript function: recordDataForRawRepo", ex);
                jsResult = false;
            }

            logger.debug("Result from JS ({}): {}", jsResult.getClass().getName(), jsResult);

            if (jsResult instanceof String) {
                result = mapper.readValue(jsResult.toString(), TypeFactory.defaultInstance().constructCollectionType(List.class, MarcRecord.class));
                return result;
            }

            throw new ScripterException("The JavaScript function %s must return a String value.", "recordDataForRawRepo");
        } catch (IOException ex) {
            throw new ScripterException("Error when executing JavaScript function: changeUpdateRecordForUpdate", ex);
        } finally {
            logger.exit(result);
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
                throw new ScripterException("Error when executing JavaScript function: hasClassificationData", ex);
            }
            logger.debug("Result from JS ({}): {}", jsResult.getClass().getName(), jsResult);

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
