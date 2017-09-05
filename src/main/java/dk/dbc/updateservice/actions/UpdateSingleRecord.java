/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.utils.LogUtils;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.openagency.client.OpenAgencyException;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.SolrException;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.Properties;
import java.util.Set;

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
    public ServiceResult performAction() throws UpdateException, SolrException {
        logger.entry();
        try {
            logger.info("Handling record: {}", LogUtils.base64Encode(record));
            MarcRecordReader reader = new MarcRecordReader(record);
            String recordId = reader.getRecordId();
            Integer agencyId = reader.getAgencyIdAsInteger();

            if (!rawRepo.recordExists(recordId, agencyId)) {
                children.add(createCreateRecordAction());
                return ServiceResult.newOkResult();
            }
            if (reader.markedForDeletion()) {
                // If it is deletion and a 870970 record then the group is always 010100
                // Which means we are only interested in the other libraries with holdings
                Set<Integer> agenciesWithHoldings = state.getHoldingsItems().getAgenciesThatHasHoldingsFor(record);
                if (RawRepo.COMMON_AGENCY.equals(reader.getAgencyIdAsInteger()) && !agenciesWithHoldings.isEmpty()) {
                    for (Integer agencyWithHoldings : agenciesWithHoldings) {
                        logger.info("Found holdings for agency '{}'", agencyWithHoldings);
                        boolean hasAuthExportHoldings = state.getOpenAgencyService().hasFeature(agencyWithHoldings.toString(), LibraryRuleHandler.Rule.AUTH_EXPORT_HOLDINGS);
                        if (hasAuthExportHoldings) {
                            logger.info("Agency '{}' has feature '{}'", agencyWithHoldings, LibraryRuleHandler.Rule.AUTH_EXPORT_HOLDINGS);
                            String solrQuery = SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId);
                            boolean has002Links = state.getSolrService().hasDocuments(solrQuery);
                            if (!has002Links) {
                                String message = String.format(state.getMessages().getString("delete.common.with.holdings.error"), recordId, agencyId, agencyWithHoldings);

                                logger.info("Record '{}:{}' has no 002 links. Returning error: {}", recordId, reader.getAgencyId(), message);
                                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
                            }
                        } else {
                            logger.info("Agency '{}' does not has feature '{}'. Accepting deletion.", agencyWithHoldings, LibraryRuleHandler.Rule.AUTH_EXPORT_HOLDINGS);
                        }
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
