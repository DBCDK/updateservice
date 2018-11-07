/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.ws.JNDIResources;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

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
    private static final XLogger logger = XLoggerFactory.getXLogger(EnqueueRecordAction.class);

    Properties settings;
    private int parentAgencyId;

    public EnqueueRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord record) {
        super(EnqueueRecordAction.class.getSimpleName(), globalActionState, record);
        this.settings = properties;
    }

    public EnqueueRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord record, int parentAgencyId) {
        super(EnqueueRecordAction.class.getSimpleName(), globalActionState, record);
        this.settings = properties;
        this.parentAgencyId = parentAgencyId;
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
            String providerId;
            MarcRecordReader reader = new MarcRecordReader(record);
            String recId = reader.getRecordId();
            int agencyId = reader.getAgencyIdAsInt();

            int priority = RawRepo.ENQUEUE_PRIORITY_DEFAULT;

            if (settings.getProperty(JNDIResources.RAWREPO_PRIORITY_OVERRIDE) != null) {
                priority = Integer.parseInt(settings.getProperty(JNDIResources.RAWREPO_PRIORITY_OVERRIDE));
                logger.info("Using override priority {}", priority);
            }

            // Enqueuing should be done differently for authority record, so first we have to determine whether
            // this is a authority record
            boolean isAuthorityRecord = RawRepo.AUTHORITY_AGENCY == agencyId ||
                    RawRepo.DBC_ENRICHMENT == agencyId && RawRepo.AUTHORITY_AGENCY == parentAgencyId;

            if (settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_OVERRIDE) != null) {
                providerId = settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_OVERRIDE);
            } else if (state.getLibraryGroup().isDBC()) {
                providerId = settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_DBC);
            } else if (state.getLibraryGroup().isPH()) {
                providerId = settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_PH);
            } else {
                providerId = settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_FBS);
            }

            if (providerId == null) {
                return result = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, state.getMessages().getString("provider.id.not.set"), state);
            }

            logger.info("Using provider id: '{}'", providerId);

            // Authority records should never be send out of rawrepo, but affected records must be.
            // Since that functionality cannot fully be done with configuration of the providers we have to handle this
            // case a bit differently.
            // First queue the authority record + enrichment on one provider, and then all children the standard way.
            // NOTE: We know that a authority record doesn't have any siblings (other than enrichment, which will be
            // enqueued by another action) so only queuing children is fine
            if (isAuthorityRecord) {
                logger.info("Enqueuing authority record: {}:{} using provider '{}' with priority {}", recId, agencyId, settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_DBC_SOLR), priority);
                rawRepo.enqueue(new RecordId(recId, agencyId), settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID_DBC_SOLR), true, false, priority);

                for (RecordId childId : rawRepo.children(record)) {
                    logger.info("Enqueuing child record {}:{} using provider '{}' with priority {}", childId.getBibliographicRecordId(), childId.getAgencyId(), providerId, priority);
                    rawRepo.changedRecord(providerId, childId, priority);
                }
            } else {
                logger.info("Enqueuing record: {}:{} using provider '{}' with priority {}", recId, agencyId, providerId, priority);
                rawRepo.changedRecord(providerId, new RecordId(recId, agencyId), priority);
            }

            return result = ServiceResult.newOkResult();
        } finally {
            logger.exit(result);
        }

    }

    /**
     * Factory method to create a EnqueueRecordAction.
     */
    public static EnqueueRecordAction newEnqueueAction(GlobalActionState globalActionState, MarcRecord record, Properties properties) {
        logger.entry(globalActionState, record);
        EnqueueRecordAction enqueueRecordAction;
        try {
            enqueueRecordAction = new EnqueueRecordAction(globalActionState, properties, record);
            return enqueueRecordAction;
        } finally {
            logger.exit();
        }
    }

    public static EnqueueRecordAction newEnqueueAction(GlobalActionState globalActionState, MarcRecord record, Properties properties, int parentAgencyId) {
        logger.entry(globalActionState, record);
        EnqueueRecordAction enqueueRecordAction;
        try {
            enqueueRecordAction = new EnqueueRecordAction(globalActionState, properties, record, parentAgencyId);
            return enqueueRecordAction;
        } finally {
            logger.exit();
        }
    }
}
