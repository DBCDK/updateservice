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
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.Properties;
import java.util.Set;


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

    public EnqueueRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord record) {
        super(EnqueueRecordAction.class.getSimpleName(), globalActionState, record);
        this.settings = properties;
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
                return result = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, state.getMessages().getString("provider.id.not.set"));
            }

            logger.info("Enqueuing record: {}:{} using provider '{}' with priority {}", recId, agencyId, providerId, priority);
            rawRepo.changedRecord(providerId, new RecordId(recId, agencyId), priority);

            // Hack for handling missing enqueue of article (870971) records with child articles.
            // The way changedRecord enqueues records with children in general is that if there are any children then
            // the record itself is enqueued with leaf = false which means the record isn't queued for certain workers.
            // That normally isn't a problem because of the way the records are dequeued by the different workers as the
            // workers retrieve the necessary hierarchy for the child record.
            // However there is a hole when it comes to article records as articles does not retrieve their parent
            // article record during queue processing. Until this case is handled by changedRecord we have to explicit
            // enqueue the parent article
            logger.info("reader.getAgencyIdAsInt() == RawRepo.ARTICLE_AGENCY? {}", reader.getAgencyIdAsInt() == RawRepo.ARTICLE_AGENCY);
            logger.info("rawRepo.children(new RecordId(recId, agencyId)).size()? {}", rawRepo.children(new RecordId(recId, agencyId)).size());
            if (reader.getAgencyIdAsInt() == RawRepo.ARTICLE_AGENCY) {
                Set<RecordId> articleChildren = rawRepo.children(new RecordId(recId, agencyId));
                logger.info("Article children: {}", articleChildren);
                if (articleChildren.size() > 0) {
                    logger.info("Found more than 0 children for article record, so enqueue that record explict");
                    rawRepo.enqueue(new RecordId(recId, agencyId), providerId, true, true, priority);
                }
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
}
