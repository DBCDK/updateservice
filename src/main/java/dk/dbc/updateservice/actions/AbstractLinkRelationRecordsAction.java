/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.utils.DeferredLogger;

public abstract class AbstractLinkRelationRecordsAction extends AbstractRawRepoAction {
    private static final DeferredLogger LOGGER = new DeferredLogger(AbstractLinkRelationRecordsAction.class);

    protected AbstractLinkRelationRecordsAction(String name, GlobalActionState globalActionState, MarcRecord marcRecord) {
        super(name, globalActionState, marcRecord);
    }

    protected ServiceResult checkIfReferenceExists(String bibliographicRecordId, int agencyId) throws UpdateException {
        if (!state.getRawRepo().recordExists(bibliographicRecordId, agencyId)) {
            final String message = String.format(state.getMessages().getString("ref.record.doesnt.exist"), bibliographicRecordId, agencyId);
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
        } else {
            return null;
        }
    }

    protected void appendLinkReference(RecordId source, RecordId target) throws UpdateException {
        LOGGER.use(log -> log.info("Set relation from [{}:{}] -> [{}:{}]",
                source.getBibliographicRecordId(),
                source.getAgencyId(),
                target.getBibliographicRecordId(),
                target.getAgencyId()));
        state.getRawRepo().linkRecordAppend(source, target);
    }

}
