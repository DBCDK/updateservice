package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.records.MarcRecordWriter;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.openagency.client.OpenAgencyException;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.HoldingsItems;
import dk.dbc.updateservice.update.LibraryRecordsHandler;
import dk.dbc.updateservice.update.OpenAgencyService;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.RawRepoDecoder;
import dk.dbc.updateservice.update.SolrService;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.ws.JNDIResources;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Created by stp on 10/08/15.
 */
public class OverwriteSingleRecordAction extends AbstractRawRepoAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(OverwriteSingleRecordAction.class);
    private static final XLogger bizLogger = XLoggerFactory.getXLogger(BusinessLoggerFilter.LOGGER_NAME);

    static final String MIMETYPE = MarcXChangeMimeType.MARCXCHANGE;

    /**
     * Group id of the user.
     */
    private Integer groupId;

    /**
     * Class to give access to the holdings database.
     */
    private HoldingsItems holdingsItems;

    /**
     * Class to give access to the OpenAgency web service
     */
    private OpenAgencyService openAgencyService;

    /**
     * Class to give access to the JavaScript engine to handle records.
     * <p>
     * The LibraryRecordsHandler is used to check records for changes in
     * classifications.
     * </p>
     */
    private LibraryRecordsHandler recordsHandler;

    /**
     * Class to give access to lookups for the rawrepo in solr.
     */
    private SolrService solrService;

    private Properties settings;

    public OverwriteSingleRecordAction(RawRepo rawRepo, MarcRecord record) {
        super("OverwriteSingleRecordAction", rawRepo, record);
        this.groupId = null;
        this.holdingsItems = null;
        this.openAgencyService = null;
        this.recordsHandler = null;
        this.solrService = null;
        this.settings = null;
    }

    public Integer getGroupId() {
        return groupId;
    }

    public void setGroupId(Integer groupId) {
        this.groupId = groupId;
    }

    public HoldingsItems getHoldingsItems() {
        return holdingsItems;
    }

    public void setHoldingsItems(HoldingsItems holdingsItems) {
        this.holdingsItems = holdingsItems;
    }

    public OpenAgencyService getOpenAgencyService() {
        return openAgencyService;
    }

    public void setOpenAgencyService(OpenAgencyService openAgencyService) {
        this.openAgencyService = openAgencyService;
    }

    public LibraryRecordsHandler getRecordsHandler() {
        return recordsHandler;
    }

    public void setRecordsHandler(LibraryRecordsHandler recordsHandler) {
        this.recordsHandler = recordsHandler;
    }

    public SolrService getSolrService() {
        return solrService;
    }

    public void setSolrService(SolrService solrService) {
        this.solrService = solrService;
    }

    public Properties getSettings() {
        return settings;
    }

    public void setSettings(Properties settings) {
        this.settings = settings;
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
        ServiceResult result = ServiceResult.newOkResult();
        try {
            bizLogger.info("Handling record:\n{}", record);

            MarcRecord currentRecord = loadCurrentRecord();

            children.add(StoreRecordAction.newStoreAction(rawRepo, record, MIMETYPE));
            children.add(new RemoveLinksAction(rawRepo, record));
            children.addAll(createActionsForCreateOrUpdateEnrichments(currentRecord));

            result = performActionsFor002Links();
            children.add(EnqueueRecordAction.newEnqueueAction(rawRepo, record, settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID), MIMETYPE));

            return result;
        } catch (ScripterException | UnsupportedEncodingException ex) {
            return ServiceResult.newErrorResult(UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, ex.getMessage());
        } finally {
            logger.exit(result);
        }
    }

    protected MarcRecord loadCurrentRecord() throws UpdateException, UnsupportedEncodingException {
        logger.entry();
        MarcRecord result = null;
        try {
            MarcRecordReader reader = new MarcRecordReader(record);
            String recordId = reader.recordId();
            Integer agencyId = reader.agencyIdAsInteger();

            Record record = rawRepo.fetchRecord(recordId, agencyId);
            return result = new RawRepoDecoder().decodeRecord(record.getContent());
        } finally {
            logger.exit(result);
        }
    }

    protected MarcRecord loadRecord(String recordId, Integer agencyId) throws UpdateException, UnsupportedEncodingException {
        logger.entry();
        MarcRecord result = null;
        try {
            Record record = rawRepo.fetchRecord(recordId, agencyId);
            return result = new RawRepoDecoder().decodeRecord(record.getContent());
        } finally {
            logger.exit(result);
        }
    }

    protected List<ServiceAction> createActionsForCreateOrUpdateEnrichments(MarcRecord currentRecord) throws ScripterException, UpdateException, UnsupportedEncodingException {
        logger.entry(currentRecord);
        List<ServiceAction> result = new ArrayList<>();
        try {
            MarcRecordReader reader = new MarcRecordReader(record);
            String recordId = reader.recordId();
            Integer agencyId = reader.agencyIdAsInteger();

            if (recordsHandler.hasClassificationData(currentRecord) && recordsHandler.hasClassificationData(record)) {
                if (recordsHandler.hasClassificationsChanged(currentRecord, record)) {
                    logger.info("Classifications was changed for common record [{}:{}]", recordId, agencyId);
                    Set<Integer> holdingsLibraries = holdingsItems.getAgenciesThatHasHoldingsFor(record);
                    logger.info("Found holdings : {}", holdingsLibraries.toString());

                    RawRepoDecoder decoder = new RawRepoDecoder();
                    for (Integer id : holdingsLibraries) {
                        logger.info("Local library for record: {}", id);

                        if (!openAgencyService.hasFeature(id.toString(), LibraryRuleHandler.Rule.USE_ENRICHMENTS)) {
                            continue;
                        }
                        if (rawRepo.recordExists(recordId, id)) {
                            Record extRecord = rawRepo.fetchRecord(recordId, id);
                            MarcRecord extRecordData = decoder.decodeRecord(extRecord.getContent());
                            if (!recordsHandler.hasClassificationData(extRecordData)) {
                                logger.info("Update classifications for extended library record: [{}:{}]", recordId, id);
                                result.add(getUpdateClassificationsInEnrichmentRecordActionData(extRecordData, currentRecord,id));
                            }
                        } else if (groupId.equals(id)) {
                            bizLogger.info("Enrichment record is not created for record [{}:{}], because groupId equals agencyid", recordId, id);
                        } else {
                            ServiceResult serviceResult = recordsHandler.shouldCreateEnrichmentRecords(settings, currentRecord, record);
                            if (serviceResult.getStatus() != UpdateStatusEnum.OK) {
                                bizLogger.info("Enrichment record is not created for reason: {}", serviceResult);
                            } else {
                                logger.info("Create new extended library record: [{}:{}].", recordId, id);
                                result.add(getActionDataForEnrichmentWithClassification(currentRecord, id));
                            }
                        }
                    }
                }
            }
            return result;
        } catch (OpenAgencyException ex) {
            throw new UpdateException(ex.getMessage(), ex);
        } finally {
            logger.exit(result);
        }
    }


    private CreateEnrichmentRecordWithClassificationsAction getUpdateClassificationsInEnrichmentRecordActionData( MarcRecord extRecordData ,  MarcRecord currentRecord, Integer id ){
        logger.entry(extRecordData, currentRecord, id);
        UpdateClassificationsInEnrichmentRecordAction action = null;
        try {
            action = new UpdateClassificationsInEnrichmentRecordAction(rawRepo, extRecordData);
            action.setCurrentCommonRecord(currentRecord);
            action.setUpdatingCommonRecord(record);
            action.setAgencyId(id);
            action.setRecordsHandler(recordsHandler);
            action.setProviderId(settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID));
            return action;
        } finally {
            logger.exit(action);
        }
    }

    private CreateEnrichmentRecordWithClassificationsAction getActionDataForEnrichmentRecord(Integer holdingAgencyId, String destinationCommonRecordId, MarcRecord linkRecord) {
        logger.entry(holdingAgencyId, destinationCommonRecordId, linkRecord);
        CreateEnrichmentRecordWithClassificationsAction action = null;
        try {
            action = new CreateEnrichmentRecordWithClassificationsAction(rawRepo, holdingAgencyId);
            action.setUpdatingCommonRecord(linkRecord);
            action.setCurrentCommonRecord(linkRecord);
            action.setCommonRecordId(destinationCommonRecordId);
            action.setRecordsHandler(recordsHandler);
            action.setProviderId(settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID));
            return action;
        } finally {
            logger.exit(action);
        }
    }

    private CreateEnrichmentRecordWithClassificationsAction getActionDataForEnrichmentWithClassification(MarcRecord currentRecord, Integer holdingAgencyId) {
        logger.entry(holdingAgencyId, currentRecord);
        CreateEnrichmentRecordWithClassificationsAction action = null;
        try {
            action = new CreateEnrichmentRecordWithClassificationsAction(rawRepo, holdingAgencyId);
            action.setCurrentCommonRecord(currentRecord);
            action.setUpdatingCommonRecord(record);
            action.setRecordsHandler(recordsHandler);
            action.setProviderId(settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID));
            return action;
        } finally {
            logger.exit(action);
        }
    }

    private MoveEnrichmentRecordAction getMoveEnrichmentRecordAction(MarcRecord enrichmentRecordData) {
        logger.entry(enrichmentRecordData);
        MoveEnrichmentRecordAction action = null;
        try {
            action = new MoveEnrichmentRecordAction(rawRepo, enrichmentRecordData);
            action.setCommonRecord(record);
            action.setRecordsHandler(recordsHandler);
            action.setHoldingsItems(holdingsItems);
            action.setSolrService(solrService);
            action.setSettings(settings);
            return action;
        } finally {
            logger.exit(action);
        }
    }

    private CreateEnrichmentRecordActionForlinkedRecords getActionDataForEnrichmentRecord(MarcRecord currentRecord, Integer holdingAgencyId, List<MarcRecord> arrayOfRecordsWithHoldings) {
        logger.entry(holdingAgencyId, currentRecord);
        CreateEnrichmentRecordActionForlinkedRecords action = null;
        try {
            action = new CreateEnrichmentRecordActionForlinkedRecords(rawRepo, holdingAgencyId,arrayOfRecordsWithHoldings);
            action.setCurrentCommonRecord(currentRecord);
            action.setUpdatingCommonRecord(record);
            action.setRecordsHandler(recordsHandler);
            action.setProviderId(settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID));
            return action;
        } finally {
            logger.exit(action);
        }
    }

    private List<CreateEnrichmentRecordActionForlinkedRecords> linkForMultipleRecordsIn002(HashMap<String, List<MarcRecord>> enrichmentCandidate) {
        logger.entry();
        List<CreateEnrichmentRecordActionForlinkedRecords> CreateEnrichmentRecordActionList = new ArrayList<>();
        try {
            enrichmentCandidate.forEach((agencyIdString, arrayOfRecordsWithHoldings) -> {
                if (arrayOfRecordsWithHoldings.size() > 1) {
                    CreateEnrichmentRecordActionList.add(getActionDataForEnrichmentRecord(record, Integer.parseInt(agencyIdString), arrayOfRecordsWithHoldings));
                    for (MarcRecord rec : arrayOfRecordsWithHoldings) {
                        MarcRecordWriter curWriter = new MarcRecordWriter(rec);
                        curWriter.markForDeletion();
                    }
                }
            });
            if (CreateEnrichmentRecordActionList.size() > 0) {
                MarcRecordWriter writer = new MarcRecordWriter(record);
                writer.removeSubfield("002", "a");
            }
            return CreateEnrichmentRecordActionList;
        } finally {
            logger.exit();
        }
    }


    protected ServiceResult performActionsFor002Links() throws ScripterException, UpdateException, UnsupportedEncodingException {
        logger.entry();

        ServiceResult result = ServiceResult.newOkResult();
        try {
            MarcRecordReader recordReader = new MarcRecordReader(record);
            String destinationCommonRecordId = recordReader.getValue("001", "a");
            Integer agencyId = Integer.valueOf(recordReader.getValue("001", "b"));

            MarcRecord currentRecord = loadRecord(destinationCommonRecordId, agencyId);
            MarcRecordReader currentRecordReader = new MarcRecordReader(currentRecord);

            List<String> valuesFrom002 = recordReader.getValues("002", "a");
            // Check made due to story #1802, regarding multiple links in one record
            boolean isMultiple002Candidate = valuesFrom002.size() > 1;

            HashMap<String, List<MarcRecord>> enrichmentCandidate = new HashMap<>();

            for (String recordId : valuesFrom002) {
                if (currentRecordReader.hasValue("002", "a", recordId)) {
                    logger.info("002 linked record '{}' is not changed, so it is not handled.", recordId);
                    continue;
                }

                if (!rawRepo.recordExists(recordId, RawRepo.RAWREPO_COMMON_LIBRARY)) {
                    logger.warn("002 linked record '{}' does not exist", recordId);
                    continue;
                }
                MarcRecord linkRecord = loadRecord(recordId, agencyId);
                boolean classificationsChanged = recordsHandler.hasClassificationsChanged(record, linkRecord);
                logger.info("Is classifications changed in record '{{}:{}}': {}", destinationCommonRecordId, agencyId, classificationsChanged);

                if (classificationsChanged) {
                    Set<Integer> holdingAgencies = holdingsItems.getAgenciesThatHasHoldingsForId(recordId);
                    Set<RecordId> enrichmentIds = rawRepo.enrichments(new RecordId(recordId, RawRepo.RAWREPO_COMMON_LIBRARY));

                    if (holdingAgencies.isEmpty()) {
                        logger.info("No holdings found for record id '{}'", recordId);
                    }

                    for (Integer holdingAgencyId : holdingAgencies) {
                        if (!openAgencyService.hasFeature(holdingAgencyId.toString(), LibraryRuleHandler.Rule.USE_ENRICHMENTS)) {
                            bizLogger.info("Ignoring holdings for agency '{}', because they do not have the feature '{}'", holdingAgencyId, LibraryRuleHandler.Rule.USE_ENRICHMENTS);
                            continue;
                        }
                        if (!enrichmentIds.contains(new RecordId(recordId, holdingAgencyId))) {
                            bizLogger.warn("No enrichments found for record '{}' for agency '{}' with holdings", recordId, holdingAgencyId);
                            if (isMultiple002Candidate) {
                                String holdingAgencyIdString = holdingAgencyId.toString();
                                if (enrichmentCandidate.containsKey(holdingAgencyIdString)) {
                                    enrichmentCandidate.get(holdingAgencyIdString).add(linkRecord);
                                } else {
                                    List<MarcRecord> marcRecords = new ArrayList<>();
                                    marcRecords.add(linkRecord);
                                    enrichmentCandidate.put(holdingAgencyIdString, marcRecords);
                                }
                            }
                            if (recordsHandler.shouldCreateEnrichmentRecords(settings, linkRecord, currentRecord).getStatus() == UpdateStatusEnum.OK) {
                                children.add(getActionDataForEnrichmentRecord(holdingAgencyId, destinationCommonRecordId, linkRecord));
                            } else {
                                bizLogger.warn("Enrichment record {{}:{}} was not created, because none of the common records was published.", recordId, holdingAgencyId);
                            }
                        }
                    }
                } else {
                    bizLogger.info("Holdings for linked record '{}' was not checked, because the classifications has not changed.");
                }
                Set<RecordId> enrichmentIds = rawRepo.enrichments(new RecordId(recordId, RawRepo.RAWREPO_COMMON_LIBRARY));

                for (RecordId enrichmentId : enrichmentIds) {
                    if (enrichmentId.getAgencyId() == RawRepo.COMMON_LIBRARY) {
                        continue;
                    }
                    if (!openAgencyService.hasFeature(String.valueOf(enrichmentId.getAgencyId()), LibraryRuleHandler.Rule.USE_ENRICHMENTS)) {
                        bizLogger.info("Ignoring enrichment record for agency '{}', because they do not have the feature '{}'", enrichmentId.getAgencyId(), LibraryRuleHandler.Rule.USE_ENRICHMENTS);
                        continue;
                    }
                    Record enrichmentRecord = rawRepo.fetchRecord(enrichmentId.getBibliographicRecordId(), enrichmentId.getAgencyId());
                    MarcRecord enrichmentRecordData = new RawRepoDecoder().decodeRecord(enrichmentRecord.getContent());
                    children.add(getMoveEnrichmentRecordAction(enrichmentRecordData));
                }
            }
            children.addAll(linkForMultipleRecordsIn002(enrichmentCandidate));
            return result;
        } catch (OpenAgencyException ex) {
            throw new UpdateException(ex.getMessage(), ex);
        } finally {
            logger.exit(result);
        }
    }

}
