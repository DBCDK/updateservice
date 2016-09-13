package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
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
    private static final XLogger logger = XLoggerFactory.getXLogger(LinkRecordAction.class);
    private static final XLogger bizLogger = XLoggerFactory.getXLogger(BusinessLoggerFilter.LOGGER_NAME);

    private RecordId linkToRecordId;

    public LinkRecordAction(GlobalActionState globalActionState, MarcRecord record) {
        super(LinkRecordAction.class.getSimpleName(), globalActionState, record);
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
        logger.entry();
        ServiceResult result = null;
        try {
            bizLogger.info("Handling record:\n{}", record);
            MarcRecordReader reader = new MarcRecordReader(record);
            String recId = reader.recordId();
            Integer agencyId = reader.agencyIdAsInteger();
            if (!rawRepo.recordExists(linkToRecordId.getBibliographicRecordId(), linkToRecordId.getAgencyId())) {
                String message = String.format(state.getMessages().getString("reference.record.not.exist"), recId, agencyId, linkToRecordId.getBibliographicRecordId(), linkToRecordId.getAgencyId());
                return result = ServiceResult.newErrorResult(UpdateStatusEnum.FAILED, message, state);
            }
            rawRepo.linkRecord(new RecordId(recId, agencyId), linkToRecordId);
            bizLogger.info("Set relation from [{}:{}] -> [{}:{}]", recId, agencyId, linkToRecordId.getBibliographicRecordId(), linkToRecordId.getAgencyId());
            return result = ServiceResult.newOkResult();
        } finally {
            logger.exit(result);
        }
    }

    /**
     * Factory method to create a StoreRecordAction.
     */
    public static LinkRecordAction newLinkParentAction(GlobalActionState globalActionState, MarcRecord record) {
        logger.entry();
        try {
            LinkRecordAction linkRecordAction = new LinkRecordAction(globalActionState, record);
            MarcRecordReader reader = new MarcRecordReader(record);
            String parentId = reader.parentId();
            Integer agencyId = reader.agencyIdAsInteger();
            linkRecordAction.setLinkToRecordId(new RecordId(parentId, agencyId));
            return linkRecordAction;
        } finally {
            logger.exit();
        }
    }
}
