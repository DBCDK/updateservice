/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;


import dk.dbc.common.records.MarcRecord;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.JNDIResources;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.utils.DeferredLogger;

import java.util.Properties;

public class EnqueuePHHoldingsRecordAction extends AbstractRawRepoAction {
    private static final DeferredLogger LOGGER = new DeferredLogger(EnqueuePHHoldingsRecordAction.class);
    Properties settings;
    private final RecordId recordId;

    public EnqueuePHHoldingsRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord marcRecord, RecordId recordId) {
        super(EnqueuePHHoldingsRecordAction.class.getSimpleName(), globalActionState, marcRecord);
        this.settings = properties;
        this.recordId = recordId;
    }

    @Override
    public ServiceResult performAction() throws UpdateException {
        final String providerId = JNDIResources.RAWREPO_PROVIDER_ID_PH_HOLDINGS;

        if (settings.getProperty(providerId) == null) {
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, state.getMessages().getString("provider.id.not.set"));
        }

        rawRepo.changedRecord(settings.getProperty(providerId), recordId);
        LOGGER.use(log -> log.info("The record {}:{} with provider '{}' was successfully enqueued", recordId.getBibliographicRecordId(), recordId.getAgencyId(), settings.getProperty(providerId)));

        return ServiceResult.newOkResult();
    }
}
