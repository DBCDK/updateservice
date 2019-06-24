/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.utils.LogUtils;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.SolrException;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.Properties;

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
    public ServiceResult performAction() throws UpdateException, SolrException {
        logger.entry();
        try {
            logger.info("Handling record: {}", LogUtils.base64Encode(record));
            MarcRecordReader reader = new MarcRecordReader(record);
            String recordId = reader.getRecordId();
            int agencyId = reader.getAgencyIdAsInt();
            String parentRecordId = reader.getParentRecordId();
            int parentAgencyId = reader.getParentAgencyIdAsInt();

            if (recordId.equals(parentRecordId)) {
                String message = String.format(state.getMessages().getString("parent.point.to.itself"), recordId, agencyId);

                logger.error("Unable to create sub actions due to an error: {}", message);
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
            }

            if (!rawRepo.recordExists(parentRecordId, parentAgencyId)) {
                String message = String.format(state.getMessages().getString("reference.record.not.exist"), recordId, agencyId, parentRecordId, parentAgencyId);

                logger.error("Unable to create sub actions due to an error: {}", message);
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
            }

            if (!CreateSingleRecordAction.checkIfRecordCanBeRestored(state, record)) {
                String message = state.getMessages().getString("create.record.with.locals");
                logger.error("Unable to create sub actions due to an error: {}", message);
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
            }

            if (state.getSolrFBS().hasDocuments(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId))) {
                String message = state.getMessages().getString("update.record.with.002.links");
                logger.error("Unable to create sub actions due to an error: {}", message);
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
            }
            children.add(StoreRecordAction.newStoreMarcXChangeAction(state, settings, record));
            children.add(new RemoveLinksAction(state, record));
            children.add(LinkRecordAction.newLinkParentAction(state, record));
            children.add(new LinkAuthorityRecordsAction(state, record));
            children.add(EnqueueRecordAction.newEnqueueAction(state, record, settings));
            return ServiceResult.newOkResult();
        } finally {
            logger.exit();
        }
    }
}
