package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.SolrServiceIndexer;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.utils.DeferredLogger;

import java.util.Properties;

/**
 * Action to creates a new volume record.
 * <p>
 * The main difference from CreateSingleRecordAction is that we need to link
 * the volume record with its parent.
 * </p>
 */
public class CreateVolumeRecordAction extends AbstractRawRepoAction {
    private static final DeferredLogger LOGGER = new DeferredLogger(CreateVolumeRecordAction.class);
    private static final String SUB_ACTION_ERROR_MESSAGE = "Unable to create sub actions due to an error: {}";

    Properties settings;

    public CreateVolumeRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord marcRecord) {
        super(CreateVolumeRecordAction.class.getSimpleName(), globalActionState, marcRecord);
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
        return LOGGER.callChecked(log -> {
            final MarcRecordReader reader = new MarcRecordReader(marcRecord);
            final String recordId = reader.getRecordId();
            final int agencyId = reader.getAgencyIdAsInt();
            final String parentRecordId = reader.getParentRecordId();
            final int parentAgencyId = reader.getParentAgencyIdAsInt();

            if (recordId.equals(parentRecordId)) {
                final String message = String.format(state.getMessages().getString("parent.point.to.itself"), recordId, agencyId);
                log.error(SUB_ACTION_ERROR_MESSAGE, message);
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
            }

            if (!rawRepo.recordExists(parentRecordId, parentAgencyId)) {
                final String message = String.format(state.getMessages().getString("reference.record.not.exist"), recordId, agencyId, parentRecordId, parentAgencyId);
                log.error(SUB_ACTION_ERROR_MESSAGE, message);
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
            }

            if (!CreateSingleRecordAction.checkIfRecordCanBeRestored(state, marcRecord)) {
                final String message = state.getMessages().getString("create.record.with.locals");
                log.error(SUB_ACTION_ERROR_MESSAGE, message);
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
            }

            if (state.getSolrFBS().hasDocuments(SolrServiceIndexer.createSubfieldQueryDBCOnly("002a", recordId))) {
                final String message = state.getMessages().getString("update.record.with.002.links");
                log.error(SUB_ACTION_ERROR_MESSAGE, message);
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
            }

            children.add(StoreRecordAction.newStoreMarcXChangeAction(state, settings, marcRecord));
            children.add(new RemoveLinksAction(state, marcRecord));
            children.add(LinkRecordAction.newLinkParentAction(state, marcRecord));
            children.add(new LinkAuthorityRecordsAction(state, marcRecord));
            children.add(EnqueueRecordAction.newEnqueueAction(state, marcRecord, settings));

            return ServiceResult.newOkResult();
        });
    }
}
