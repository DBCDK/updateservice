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
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

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
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(LinkRecordAction.class);

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
        LOGGER.entry();
        ServiceResult result = null;
        try {
            LOGGER.info("Handling record: {}", LogUtils.base64Encode(marcRecord));
            MarcRecordReader reader = new MarcRecordReader(marcRecord);
            String recordId = reader.getRecordId();
            int agencyId = reader.getAgencyIdAsInt();
            RecordId recordIdObj = new RecordId(recordId, agencyId);
            if (!rawRepo.recordExists(linkToRecordId.getBibliographicRecordId(), linkToRecordId.getAgencyId())) {
                String message = String.format(state.getMessages().getString("reference.record.not.exist"), recordId, agencyId, linkToRecordId.getBibliographicRecordId(), linkToRecordId.getAgencyId());
                return result = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
            }
            LOGGER.info("Set relation from [{}:{}] -> [{}:{}]", recordId, agencyId, linkToRecordId.getBibliographicRecordId(), linkToRecordId.getAgencyId());
            rawRepo.linkRecord(recordIdObj, linkToRecordId);
            return result = ServiceResult.newOkResult();
        } finally {
            LOGGER.exit(result);
        }
    }

    /**
     * Factory method to create a LinkRecordAction.
     */
    public static LinkRecordAction newLinkParentAction(GlobalActionState globalActionState, MarcRecord marcRecord) {
        LOGGER.entry();
        try {
            LinkRecordAction linkRecordAction = new LinkRecordAction(globalActionState, marcRecord);
            MarcRecordReader reader = new MarcRecordReader(marcRecord);
            String parentId = reader.getParentRecordId();
            int agencyId = reader.getParentAgencyIdAsInt();
            linkRecordAction.setLinkToRecordId(new RecordId(parentId, agencyId));
            return linkRecordAction;
        } finally {
            LOGGER.exit();
        }
    }
}
