package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.AgencyNumber;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.openagency.client.OpenAgencyException;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.Properties;

/**
 * Action to create, overwrite or delete a single record.
 */
public class UpdateSingleRecord extends AbstractRawRepoAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(UpdateSingleRecord.class);

    protected Properties settings;

    public UpdateSingleRecord(GlobalActionState globalActionState, Properties properties, MarcRecord record) {
        super(UpdateSingleRecord.class.getSimpleName(), globalActionState, record);
        settings = properties;
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
            logger.info("Handling record:\n{}", record);
            MarcRecordReader reader = new MarcRecordReader(record);
            String recordId = reader.recordId();
            Integer agencyId = reader.agencyIdAsInteger();

            if (!rawRepo.recordExists(recordId, agencyId)) {
                children.add(createCreateRecordAction());
                return ServiceResult.newOkResult();
            }
            if (reader.markedForDeletion()) {
                boolean hasHoldings = !state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record).isEmpty();
                if (hasHoldings) {
                    AgencyNumber groupAgencyNumber = new AgencyNumber(state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId());
                    logger.info("Found holdings for agency '{}'", groupAgencyNumber);
                    boolean hasAuthExportHoldings = state.getOpenAgencyService().hasFeature(groupAgencyNumber.toString(), LibraryRuleHandler.Rule.AUTH_EXPORT_HOLDINGS);
                    if (hasAuthExportHoldings) {
                        logger.info("Agency '{}' has feature '{}'", groupAgencyNumber, LibraryRuleHandler.Rule.AUTH_EXPORT_HOLDINGS);
                        String solrQuery = SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId);
                        boolean has002Links = state.getSolrService().hasDocuments(solrQuery);
                        if (!has002Links) {
                            String message = state.getMessages().getString("delete.common.with.holdings.error");
                            logger.info("Record '{}:{}' has no 002 links. Returning error: {}", recordId, reader.agencyId(), message);
                            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
                        }
                    } else {
                        logger.info("Agency '{}' does not has feature '{}'. Accepting deletion.", groupAgencyNumber, LibraryRuleHandler.Rule.AUTH_EXPORT_HOLDINGS);
                    }
                }
                children.add(createDeleteRecordAction());
                return ServiceResult.newOkResult();
            }
            children.add(createOverwriteRecordAction());
            return ServiceResult.newOkResult();
        } catch (OpenAgencyException e) {
            throw new UpdateException(e.getMessage(), e);
        } finally {
            logger.exit();
        }
    }

    /**
     * Factory method to construct the ServiceAction to create a new record.
     */
    protected ServiceAction createCreateRecordAction() {
        return new CreateSingleRecordAction(state, settings, record);
    }

    /**
     * Factory method to construct the ServiceAction to overwrite an existing record.
     */
    protected ServiceAction createOverwriteRecordAction() {
        return new OverwriteSingleRecordAction(state, settings, record);
    }

    /**
     * Factory method to construct the ServiceAction to delete a record.
     */
    protected ServiceAction createDeleteRecordAction() {
        return new DeleteCommonRecordAction(state, settings, record);
    }
}
