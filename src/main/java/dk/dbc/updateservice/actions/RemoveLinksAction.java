package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.utils.DeferredLogger;

/**
 * Action to remove all links from a record to all other records.
 * <p>
 * This action is used in these common cases:
 * <ol>
 * <li>If a record change status from a volume record to a single record.</li>
 * <li>The record is deleted.</li>
 * </ol>
 * </p>
 */
public class RemoveLinksAction extends AbstractRawRepoAction {
    private static final DeferredLogger LOGGER = new DeferredLogger(RemoveLinksAction.class);

    public RemoveLinksAction(GlobalActionState globalActionState, MarcRecord marcRecord) {
        super(RemoveLinksAction.class.getSimpleName(), globalActionState, marcRecord);
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
            final String recId = reader.getRecordId();
            final int agencyId = reader.getAgencyIdAsInt();

            rawRepo.removeLinks(new RecordId(recId, agencyId));
            log.info("Removed all links for record {{}:{}} successfully", recId, agencyId);

            return ServiceResult.newOkResult();
        });
    }
}
