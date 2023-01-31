/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.rawrepo.RawRepoException;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.utils.DeferredLogger;

import java.util.Properties;

/**
 * Action to overwrite an existing volume record.
 */
public class OverwriteVolumeRecordAction extends OverwriteSingleRecordAction {
    private static final DeferredLogger LOGGER = new DeferredLogger(OverwriteVolumeRecordAction.class);

    public OverwriteVolumeRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord marcRecord) {
        super(globalActionState, properties, marcRecord);
        setName("OverwriteVolumeRecordAction");
        state = globalActionState;
    }

    /**
     * Performs this actions and may create any child actions.
     *
     * @return A list of ValidationError to be reported in the web service response.
     * @throws UpdateException In case of an error.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        try {
            final MarcRecordReader reader = new MarcRecordReader(marcRecord);
            if (RawRepo.DBC_PRIVATE_AGENCY_LIST.contains(reader.getAgencyId())) {
                performActionDBCRecord();
                return ServiceResult.newOkResult();
            } else {
                return performActionDefault();
            }
        } catch (RawRepoException ex) {
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, ex.getMessage());
        }
    }

    private ServiceResult performActionDefault() throws UpdateException, RawRepoException {
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);
        final String recordId = reader.getRecordId();
        final String parentId = reader.getParentRecordId();
        final int agencyId = reader.getAgencyIdAsInt();
        final int parentAgencyId = reader.getParentAgencyIdAsInt();

        if (recordId.equals(parentId)) {
            int errorAgencyId = agencyId;
            if (errorAgencyId == RawRepo.COMMON_AGENCY) {
                errorAgencyId = RawRepo.DBC_ENRICHMENT;
            }
            final String message = String.format(state.getMessages().getString("parent.point.to.itself"), recordId, errorAgencyId);
            LOGGER.use(log -> log.error("Unable to create sub actions due to an error: {}", message));
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
        }

        if (!rawRepo.recordExists(parentId, parentAgencyId)) {
            final String message = String.format(state.getMessages().getString("reference.record.not.exist"), recordId, agencyId, parentId, parentAgencyId);
            LOGGER.use(log -> log.error("Unable to create sub actions due to an error: {}", message));
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
        }

        final MarcRecord currentExpandedRecord = loadCurrentRecord();
        final MarcRecord newExpandedRecord = expandRecord();

        children.add(StoreRecordAction.newStoreMarcXChangeAction(state, settings, marcRecord));
        children.add(new RemoveLinksAction(state, marcRecord));
        children.add(LinkRecordAction.newLinkParentAction(state, marcRecord));
        children.addAll(createActionsForCreateOrUpdateEnrichments(newExpandedRecord, currentExpandedRecord));
        children.add(new LinkAuthorityRecordsAction(state, marcRecord));
        children.add(EnqueueRecordAction.newEnqueueAction(state, marcRecord, settings));
        children.addAll(getEnqueuePHHoldingsRecordActions(state, marcRecord));

        return ServiceResult.newOkResult();
    }
}
