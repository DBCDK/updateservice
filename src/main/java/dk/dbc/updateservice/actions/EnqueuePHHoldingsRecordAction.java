/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;


import dk.dbc.common.records.MarcRecord;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.ws.JNDIResources;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.Properties;

public class EnqueuePHHoldingsRecordAction extends AbstractRawRepoAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(EnqueuePHHoldingsRecordAction.class);

    Properties settings;
    private RecordId recordId;

    public EnqueuePHHoldingsRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord record, RecordId recordId) {
        super(EnqueuePHHoldingsRecordAction.class.getSimpleName(), globalActionState, record);
        this.settings = properties;
        this.recordId = recordId;
    }

    @Override
    public ServiceResult performAction() throws UpdateException {
        logger.entry();
        ServiceResult result = null;
        try {
            String providerId = JNDIResources.RAWREPO_PROVIDER_ID_PH_HOLDINGS;

            if (settings.getProperty(providerId) == null) {
                return result = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, state.getMessages().getString("provider.id.not.set"));
            }

            rawRepo.changedRecord(settings.getProperty(providerId), recordId);
            logger.info("The record {}:{} with provider '{}' was successfully enqueued", recordId.getBibliographicRecordId(), recordId.getAgencyId(), settings.getProperty(providerId));

            return result = ServiceResult.newOkResult();
        } finally {
            logger.exit(result);
        }
    }
}
