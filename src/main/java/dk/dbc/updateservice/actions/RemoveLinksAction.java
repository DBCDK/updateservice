/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.utils.LogUtils;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

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
    private static final XLogger logger = XLoggerFactory.getXLogger(RemoveLinksAction.class);

    public RemoveLinksAction(GlobalActionState globalActionState, MarcRecord record) {
        super(RemoveLinksAction.class.getSimpleName(), globalActionState, record);
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
            logger.info("Handling record: {}", LogUtils.base64Encode(record));

            MarcRecordReader reader = new MarcRecordReader(record);
            String recId = reader.getRecordId();
            int agencyId = reader.getAgencyIdAsInt();

            rawRepo.removeLinks(new RecordId(recId, agencyId));
            logger.info("Removed all links for record {{}:{}} successfully", recId, agencyId);

            return result = ServiceResult.newOkResult();
        } finally {
            logger.exit(result);
        }
    }
}
