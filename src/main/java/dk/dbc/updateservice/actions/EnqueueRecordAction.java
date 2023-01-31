/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.JNDIResources;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.utils.DeferredLogger;

import java.util.Properties;


/**
 * Action to enqueue a record in rawrepo.
 * <p>
 * When a record is updated or created in the rawrepo it needs to be
 * enqueued so other components in the flow of services can be notified
 * about the changed record.
 * </p>
 * <p>
 * This action does exactly that for a single record.
 * </p>
 * <p>
 * <strong>Note:</strong> For this to work properly the record need to has
 * its links to other record setup correctly. If they are
 * missing, not all records that points to this record will be enqueued
 * correctly.
 * </p>
 */
public class EnqueueRecordAction extends AbstractRawRepoAction {
    private static final DeferredLogger LOGGER = new DeferredLogger(EnqueueRecordAction.class);
    Properties settings;
    private final RecordId recordId;

    public EnqueueRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord marcRecord) {
        super(EnqueueRecordAction.class.getSimpleName(), globalActionState, marcRecord);
        this.settings = properties;
        final MarcRecordReader reader = new MarcRecordReader(marcRecord);
        final String bibliographicRecordId = reader.getRecordId();
        final int agencyId = reader.getAgencyIdAsInt();
        this.recordId = new RecordId(bibliographicRecordId, agencyId);
    }

    public EnqueueRecordAction(GlobalActionState globalActionState, Properties properties, RecordId recordId) {
        super(EnqueueRecordAction.class.getSimpleName(), globalActionState, recordId);
        this.settings = properties;
        this.recordId = recordId;
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
            String providerId;

            int priority = RawRepo.ENQUEUE_PRIORITY_DEFAULT;

            if (settings.getProperty(JNDIResources.RAWREPO_PRIORITY_OVERRIDE) != null) {
                priority = Integer.parseInt(settings.getProperty(JNDIResources.RAWREPO_PRIORITY_OVERRIDE));
                log.info("Using override priority {}", priority);
            }

            if (settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_OVERRIDE) != null) {
                providerId = settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_OVERRIDE);
            } else if (state.getLibraryGroup().isDBC() || state.getLibraryGroup().isSBCI()) {
                providerId = settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_DBC);
            } else if (state.getLibraryGroup().isPH()) {
                providerId = settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_PH);
            } else {
                providerId = settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_FBS);
            }

            if (providerId == null) {
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, state.getMessages().getString("provider.id.not.set"));
            }

            // It looks like 191919 enqueues everything the parent record does in addition to itself. This is a waste of
            // time. So if the record is 191919 we only enqueue that specific record.
            if (recordId.getAgencyId() == RawRepo.DBC_ENRICHMENT) {
                rawRepo.enqueue(recordId, providerId, true, true, priority);
                return ServiceResult.newOkResult();
            }

            log.info("Enqueuing record: {}:{} using provider '{}' with priority {}", recordId.getBibliographicRecordId(), recordId.getAgencyId(), providerId, priority);
            rawRepo.changedRecord(providerId, recordId, priority);

            // Hack for handling missing enqueue of article (870971) records with child articles.
            // The way changedRecord enqueues records with children in general is that if there are any children then
            // the record itself is enqueued with leaf = false which means the record isn't queued for certain workers.
            // That normally isn't a problem because of the way the records are dequeued by the different workers as the
            // workers retrieve the necessary hierarchy for the child record.
            // However, there is a hole when it comes to article records as articles does not retrieve their parent
            // article record during queue processing. Until this case is handled by changedRecord we have to explicit
            // enqueue the parent article
            if (recordId.getAgencyId() == RawRepo.ARTICLE_AGENCY && !state.getRawRepo().children(recordId).isEmpty()) {
                log.info("Found children for article record, so enqueuing that record explict");
                log.info("Enqueuing record: {}:{} using provider '{}' with priority {}", recordId.getBibliographicRecordId(), RawRepo.DBC_ENRICHMENT, providerId, priority);
                rawRepo.enqueue(new RecordId(recordId.getBibliographicRecordId(), RawRepo.DBC_ENRICHMENT), providerId, true, true, priority);
            }

            return ServiceResult.newOkResult();
        });
    }

    /**
     * Factory method to create a EnqueueRecordAction.
     */
    public static EnqueueRecordAction newEnqueueAction(GlobalActionState globalActionState, MarcRecord marcRecord, Properties properties) {
        return new EnqueueRecordAction(globalActionState, properties, marcRecord);
    }

    public static EnqueueRecordAction newEnqueueAction(GlobalActionState globalActionState, RecordId recordId, Properties properties) {
        return new EnqueueRecordAction(globalActionState, properties, recordId);
    }

}
