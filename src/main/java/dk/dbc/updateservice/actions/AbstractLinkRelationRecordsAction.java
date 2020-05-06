/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

public abstract class AbstractLinkRelationRecordsAction extends AbstractRawRepoAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(AbstractLinkRelationRecordsAction.class);

    public AbstractLinkRelationRecordsAction(String name, GlobalActionState globalActionState, MarcRecord record) {
        super(name, globalActionState, record);
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
        logger.info("Set relation from [{}:{}] -> [{}:{}]",
                source.getBibliographicRecordId(),
                source.getAgencyId(),
                target.getBibliographicRecordId(),
                target.getAgencyId());
        state.getRawRepo().linkRecordAppend(source, target);
    }

}
