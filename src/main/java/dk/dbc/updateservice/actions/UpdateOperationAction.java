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
            if (!validatePreviousFaustMessage.isEmpty()) {
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

    private String validatePreviousFaust(MarcRecordReader reader) throws UpdateException, UnsupportedEncodingException {
        logger.entry();

        try {
            // Either new record or update of existing record
            if (!reader.markedForDeletion()) {
                logger.info("GRYDESTEG - new/existing record!");

                Boolean recordExists = rawRepo.recordExists(reader.recordId(), reader.agencyIdAsInteger());

                logger.info("GRYDESTEG - check 002a");
                // Compare new 002a with existing 002a
                for (String aValue : reader.centralAliasIds()) {
                    logger.info("GRYDESTEG - aValue: " + aValue);
                    String solrQuery = recordExists ?
                            SolrServiceIndexer.createSubfieldQueryWithExcludeDBCOnly("002a", aValue, "001a", reader.recordId()) :
                            SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", aValue);
                    if (state.getSolrService().hasDocuments(solrQuery)) {
                        return state.getMessages().getString("update.record.with.002.links");
                    }
                }
                logger.info("GRYDESTEG - check 002a DONE");

                logger.info("GRYDESTEG - check 002bc");
                // Compare new 002b & c with existing 002b & c
                for (HashMap<String, String> bcValues : reader.decentralAliasIds()) {
                    String solrQuery = recordExists ?
                            SolrServiceIndexer.createSubfieldQueryDualWithExcludeDBCOnly("002b", bcValues.get("b"), "002c", bcValues.get("c"), "001a", reader.recordId()) :
                            SolrServiceIndexer.createSubfieldQueryDualDBCOnly("002b", bcValues.get("b"), "002c", bcValues.get("c"));
                    if (state.getSolrService().hasDocuments(solrQuery)) {
                        return state.getMessages().getString("update.record.with.002.links");
                    }
                }
                logger.info("GRYDESTEG - check 002bc DONE");

                logger.info("GRYDESTEG - checking existing record");
                if (recordExists) {

                    Record existingRecord = rawRepo.fetchRecord(reader.recordId(), reader.agencyIdAsInteger());
                    logger.info("GRYDESTEG - existing record is null? " + (existingRecord == null));
                    MarcRecord existingMarc = new RawRepoDecoder().decodeRecord(existingRecord.getContent());
                    MarcRecordReader existingRecordReader = new MarcRecordReader(existingMarc);

                    logger.info("GRYDESTEG - existingRecord: ");
                    logger.info("\n" + existingMarc.toString());

                    logger.info("GRYDESTEG - reader.centralAliasIds().size(): " + reader.centralAliasIds().size());
                    logger.info("GRYDESTEG - existingRecordReader.hasSubfield(\"002\", \"a\"): " + existingRecordReader.hasSubfield("002", "a"));

                    // The input record has no 002a field so check if an existing record does
                    if (reader.centralAliasIds().size() == 0 && existingRecordReader.hasSubfield("002", "a")) {
                        logger.info("GRYDESTEG 1");
                        for (String previousFaust : existingRecordReader.centralAliasIds()) {
                            logger.info("GRYDESTEG 2");
                            Set<Integer> holdingAgencies = state.getHoldingsItems().getAgenciesThatHasHoldingsForId(previousFaust);
                            if (holdingAgencies.size() > 0) {
                                logger.info("GRYDESTEG 3");
                                return state.getMessages().getString("update.record.holdings.on.002a");
                            }
                        }
                    }
                }
            } else {
                logger.info("GRYDESTEG - deletion of record!");

                if (rawRepo.recordExists(reader.recordId(), reader.agencyIdAsInteger())) {
                    logger.info("GRYDESTEG - rawRepo.recordExists");
                    Record existingRecord = rawRepo.fetchRecord(reader.recordId(), reader.agencyIdAsInteger());
                    MarcRecord existingMarc = new RawRepoDecoder().decodeRecord(existingRecord.getContent());
                    MarcRecordReader existingRecordReader = new MarcRecordReader(existingMarc);

                    logger.info("GRYDESTEG - holding on 001a");
                    // Deletion of 002a - check for holding on 001a
                    Set<Integer> holdingAgencies001 = state.getHoldingsItems().getAgenciesThatHasHoldingsForId(reader.recordId());
                    if (holdingAgencies001.size() > 0) {
                        logger.info("GRYDESTEG - holding > 0");
                        for (String previousFaust : existingRecordReader.centralAliasIds()) {
                            logger.info("GRYDESTEG - solr lookup on 001a = " + previousFaust);
                            if (!state.getSolrService().hasDocuments(SolrServiceIndexer.createSubfieldQueryDBCOnly("001a", previousFaust))) {
                                return state.getMessages().getString("delete.record.holdings.on.002a");
                            }
                        }
                    }

                    logger.info("GRYDESTEG - holding on 002a");
                    // Deletion of 002a - check for holding on 002a
                    for (String previousFaust : existingRecordReader.centralAliasIds()) {
                        logger.info("GRYDESTEG - checking holding " + previousFaust);
                        Set<Integer> holdingAgencies002 = state.getHoldingsItems().getAgenciesThatHasHoldingsForId(previousFaust);
                        if (holdingAgencies002.size() > 0) {
                            return state.getMessages().getString("delete.record.holdings.on.002a");
                        }
                    }
                }

            }

            return "";
        } finally {
            logger.exit();
        }
    }

    /**
     * Class to sort the records returned from JavaScript in the order they should be
     * processed.
     * <p/>
     * The records are sorted in this order:
     * <ol>
     * <li>Common records are processed before local and enrichment records.</li>
     * <li>
     * If one of the records has the deletion mark in 004r then the process order
     * is reversed.
     * </li>
     * </ol>
     */
    private class ProcessOrder implements Comparator<MarcRecord> {

        /**
         * Compares its two arguments for order.  Returns a negative integer, zero, or a positive integer as the first
         * argument is less than, equal to, or greater than the second.<p>
         * <p/>
         * In the foregoing description, the notation <tt>sgn(</tt><i>expression</i><tt>)</tt> designates the mathematical
         * <i>signum</i> function, which is defined to return one of <tt>-1</tt>, <tt>0</tt>, or <tt>1</tt> according to
         * whether the value of <i>expression</i> is negative, zero or positive.<p>
         * <p/>
         * The implementor must ensure that <tt>sgn(compare(x, y)) == -sgn(compare(y, x))</tt> for all <tt>x</tt> and
         * <tt>y</tt>.  (This implies that <tt>compare(x, y)</tt> must throw an exception if and only if <tt>compare(y,
         * x)</tt> throws an exception.)<p>
         * <p/>
         * The implementor must also ensure that the relation is transitive: <tt>((compare(x, y)&gt;0) &amp;&amp;
         * (compare(y, z)&gt;0))</tt> implies <tt>compare(x, z)&gt;0</tt>.<p>
         * <p/>
         * Finally, the implementor must ensure that <tt>compare(x, y)==0</tt> implies that <tt>sgn(compare(x,
         * z))==sgn(compare(y, z))</tt> for all <tt>z</tt>.<p>
         * <p/>
         * It is generally the case, but <i>not</i> strictly required that <tt>(compare(x, y)==0) == (x.equals(y))</tt>.
         * Generally speaking, any comparator that violates this condition should clearly indicate this fact.  The
         * recommended language is "Note: this comparator imposes orderings that are inconsistent with equals."
         *
         * @param o1 the first object to be compared.
         * @param o2 the second object to be compared.
         * @return a negative integer, zero, or a positive integer as the first argument is less than, equal to, or greater
         * than the second.
         * @throws NullPointerException if an argument is null and this comparator does not permit null arguments
         * @throws ClassCastException   if the arguments' types prevent them from being compared by this comparator.
         */
        @Override
        public int compare(MarcRecord o1, MarcRecord o2) {
            MarcRecordReader reader1 = new MarcRecordReader(o1);
            MarcRecordReader reader2 = new MarcRecordReader(o2);
            Integer agency1 = reader1.agencyIdAsInteger();
            Integer agency2 = reader2.agencyIdAsInteger();
            int result;
            if (agency1.equals(agency2)) {
                result = 0;
            } else if (agency1.equals(RawRepo.RAWREPO_COMMON_LIBRARY)) {
                result = -1;
            } else {
                result = 1;
            }
            if (reader1.markedForDeletion() || reader2.markedForDeletion()) {
                return result * -1;
            }
            return result;
        }
    }

}
