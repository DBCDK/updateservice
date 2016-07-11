package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.AgencyNumber;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.openagency.client.OpenAgencyException;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.*;
import dk.dbc.updateservice.ws.JNDIResources;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.Properties;
import java.util.ResourceBundle;

/**
 * Action to create, overwrite or delete a single record.
 */
public class UpdateSingleRecord extends AbstractRawRepoAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(UpdateSingleRecord.class);
    private static final XLogger bizLogger = XLoggerFactory.getXLogger(BusinessLoggerFilter.LOGGER_NAME);
    static final String MIMETYPE = MarcXChangeMimeType.MARCXCHANGE;

    private Integer groupId;
    private HoldingsItems holdingsItems;
    private OpenAgencyService openAgencyService;
    private SolrService solrService;
    private LibraryRecordsHandler recordsHandler;
    protected Properties settings;
    private ResourceBundle messages;

    public UpdateSingleRecord(RawRepo rawRepo, MarcRecord record) {
        super("UpdateSingleRecord", rawRepo, record);

        this.groupId = null;
        this.holdingsItems = null;
        this.openAgencyService = null;
        this.solrService = null;
        this.recordsHandler = null;
        this.settings = null;

        this.messages = ResourceBundles.getBundle(this, "actions");
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

    public SolrService getSolrService() {
        return solrService;
    }

    public void setSolrService(SolrService solrService) {
        this.solrService = solrService;
    }

    public LibraryRecordsHandler getRecordsHandler() {
        return recordsHandler;
    }

    public void setRecordsHandler(LibraryRecordsHandler recordsHandler) {
        this.recordsHandler = recordsHandler;
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
     * @return A ServiceResult to be reported in the web service response.
     * @throws UpdateException In case of an error.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        logger.entry();

        try {
            bizLogger.info("Handling record:\n{}", record);

            MarcRecordReader reader = new MarcRecordReader(record);
            String recordId = reader.recordId();
            Integer agencyId = reader.agencyIdAsInteger();

            if (!rawRepo.recordExists(recordId, agencyId)) {
                children.add(createCreateRecordAction());
                return ServiceResult.newOkResult();
            }

            if (reader.markedForDeletion()) {
                boolean hasHoldings = !holdingsItems.getAgenciesThatHasHoldingsFor(record).isEmpty();

                if (hasHoldings) {
                    AgencyNumber groupAgencyNumber = new AgencyNumber(groupId);
                    bizLogger.info("Found holdings for agency '{}'", groupAgencyNumber);

                    boolean hasAuthExportHoldings = openAgencyService.hasFeature(groupAgencyNumber.toString(), LibraryRuleHandler.Rule.AUTH_EXPORT_HOLDINGS);

                    if (hasAuthExportHoldings) {
                        bizLogger.info("Agency '{}' has feature '{}'", groupAgencyNumber, LibraryRuleHandler.Rule.AUTH_EXPORT_HOLDINGS);

                        String solrQuery = SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId);
                        boolean has002Links = solrService.hasDocuments(solrQuery);

                        if (!has002Links) {
                            String message = messages.getString("delete.common.with.holdings.error");

                            bizLogger.info("Record '{}:{}' has no 002 links. Returning error: {}", recordId, reader.agencyId(), message);
                            return ServiceResult.newErrorResult(UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, message);
                        }
                    } else {
                        bizLogger.info("Agency '{}' does not has feature '{}'. Accepting deletion.", groupAgencyNumber, LibraryRuleHandler.Rule.AUTH_EXPORT_HOLDINGS);
                    }
                }

                children.add(createDeleteRecordAction());
                return ServiceResult.newOkResult();
            }

            children.add(createOverwriteRecordAction());
            return ServiceResult.newOkResult();
        } catch (OpenAgencyException ex) {
            throw new UpdateException(ex.getMessage(), ex);
        } finally {
            logger.exit();
        }
    }

    /**
     * Factory method to construct the ServiceAction to create a new record.
     */
    protected ServiceAction createCreateRecordAction() {
        logger.entry();

        try {
            CreateSingleRecordAction action = new CreateSingleRecordAction(rawRepo, record);
            action.setSolrService(solrService);
            action.setProviderId(settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID));

            return action;
        } finally {
            logger.exit();
        }
    }

    /**
     * Factory method to construct the ServiceAction to overwrite an existing record.
     */
    protected ServiceAction createOverwriteRecordAction() {
        logger.entry();

        try {
            OverwriteSingleRecordAction action = new OverwriteSingleRecordAction(rawRepo, record);
            action.setGroupId(groupId);
            action.setHoldingsItems(holdingsItems);
            action.setOpenAgencyService(openAgencyService);
            action.setRecordsHandler(recordsHandler);
            action.setSolrService(solrService);
            action.setSettings(settings);

            return action;
        } finally {
            logger.exit();
        }
    }

    /**
     * Factory method to construct the ServiceAction to delete a record.
     */
    protected ServiceAction createDeleteRecordAction() {
        logger.entry();

        try {
            DeleteCommonRecordAction action = new DeleteCommonRecordAction(rawRepo, record);
            action.setRecordsHandler(recordsHandler);
            action.setHoldingsItems(holdingsItems);
            action.setSolrService(solrService);
            action.setProviderId(settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID));

            return action;
        } finally {
            logger.exit();
        }
    }
}
