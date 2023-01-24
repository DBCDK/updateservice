/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.utils.LogUtils;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.utils.DeferredLogger;

/**
 * Action to link a record to another record.
 * <p>
 * This action is used in these common cases:
 * <ol>
 * <li>Linking multiple works.</li>
 * <li>Linking enrichments to common records or other enrichments</li>
 * </ol>
 * </p>
 */
public class LinkRecordAction extends AbstractRawRepoAction {
    private static final DeferredLogger LOGGER = new DeferredLogger(LinkRecordAction.class);

    private RecordId linkToRecordId;

    public LinkRecordAction(GlobalActionState globalActionState, MarcRecord marcRecord) {
        super(LinkRecordAction.class.getSimpleName(), globalActionState, marcRecord);
    }

    public RecordId getLinkToRecordId() {
        return linkToRecordId;
    }

    public void setLinkToRecordId(RecordId linkToRecordId) {
        this.linkToRecordId = linkToRecordId;
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
            if (log.isInfoEnabled()) {
                log.info("Handling record: {}", LogUtils.base64Encode(marcRecord));
            }
            final MarcRecordReader reader = new MarcRecordReader(marcRecord);
            final String recordId = reader.getRecordId();
            final int agencyId = reader.getAgencyIdAsInt();
            final RecordId recordIdObj = new RecordId(recordId, agencyId);
            if (!rawRepo.recordExists(linkToRecordId.getBibliographicRecordId(), linkToRecordId.getAgencyId())) {
                final String message = String.format(state.getMessages().getString("reference.record.not.exist"), recordId, agencyId, linkToRecordId.getBibliographicRecordId(), linkToRecordId.getAgencyId());
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
            }
            log.info("Set relation from [{}:{}] -> [{}:{}]", recordId, agencyId, linkToRecordId.getBibliographicRecordId(), linkToRecordId.getAgencyId());
            rawRepo.linkRecord(recordIdObj, linkToRecordId);

            return ServiceResult.newOkResult();
        });
    }

    /**
     * Factory method to create a LinkRecordAction.
     */
    public static LinkRecordAction newLinkParentAction(GlobalActionState globalActionState, MarcRecord marcRecord) {
        final LinkRecordAction linkRecordAction = new LinkRecordAction(globalActionState, marcRecord);
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);
        final String parentId = reader.getParentRecordId();
        final int agencyId = reader.getParentAgencyIdAsInt();
        linkRecordAction.setLinkToRecordId(new RecordId(parentId, agencyId));

        return linkRecordAction;
    }
}
