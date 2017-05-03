/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.Properties;
import java.util.Set;

/**
 * Action to creates a new volume record.
 * <p>
 * The main difference from CreateSingleRecordAction is that we need to link
 * the volume record with its parent.
 * </p>
 */
public class CreateVolumeRecordAction extends AbstractRawRepoAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(CreateVolumeRecordAction.class);

    Properties settings;

    public CreateVolumeRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord record) {
        super(CreateVolumeRecordAction.class.getSimpleName(), globalActionState, record);
        settings = properties;
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
        try {
            logger.info("Handling record:\n{}", record);
            MarcRecordReader reader = new MarcRecordReader(record);
            String recordId = reader.recordId();
            String parentId = reader.parentId();
            Integer agencyId = reader.agencyIdAsInteger();
            if (recordId.equals(parentId)) {
                String message = String.format(state.getMessages().getString("parent.point.to.itself"), recordId, agencyId);

                logger.error("Unable to create sub actions doing to an error: {}", message);
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
            }

            if (!rawRepo.recordExists(parentId, agencyId)) {
                String message = String.format(state.getMessages().getString("reference.record.not.exist"), recordId, agencyId, parentId, agencyId);

                logger.error("Unable to create sub actions doing to an error: {}", message);
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
            }

            // The rule is: FBS and DBC libraries cannot have overlapping records.
            // However, FFU libraries are allowed to have overlapping posts as they never use enrichment posts
            Set<Integer> agenciesForRecord = rawRepo.agenciesForRecord(record);
            if (!agenciesForRecord.isEmpty()) {
                logger.info("The agencies {} was found for {}. Checking if all agencies are FFU - otherwise this action will fail", agenciesForRecord, recordId);
                Set<String> ffuAgencyIds = state.getFFULibraries();
                boolean allAgenciesAreFFU = true;
                for (Integer agencyForRecord : agenciesForRecord) {
                    if (!ffuAgencyIds.contains(agencyForRecord.toString())) {
                        logger.info("The library {} is not a FFU library.", agencyForRecord);
                        allAgenciesAreFFU = false;
                        break;
                    }
                }

                if (!allAgenciesAreFFU) {
                    String message = state.getMessages().getString("create.record.with.locals");

                    logger.error("Unable to create sub actions doing to an error: {}", message);
                    return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
                }
            }

            if (state.getSolrService().hasDocuments(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId))) {
                String message = state.getMessages().getString("update.record.with.002.links");
                logger.error("Unable to create sub actions doing to an error: {}", message);
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
            }
            logger.error("Creating sub actions successfully");
            children.add(StoreRecordAction.newStoreAction(state, settings, record, MarcXChangeMimeType.MARCXCHANGE));
            children.add(new RemoveLinksAction(state, record));
            children.add(LinkRecordAction.newLinkParentAction(state, record));
            children.add(ActionFactory.newEnqueueAction(state, record, settings, MarcXChangeMimeType.MARCXCHANGE));
            return ServiceResult.newOkResult();
        } finally {
            logger.exit();
        }
    }
}
