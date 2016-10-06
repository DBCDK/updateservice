package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.AgencyNumber;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.records.MarcRecordWriter;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.openagency.client.LibraryRuleHandler;
import dk.dbc.openagency.client.OpenAgencyException;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.RawRepoDecoder;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.Properties;
import java.util.Set;

/**
 * Action to update a local record.
 * <p>
 * This action does not actual update the local record, but creates child
 * actions to do the actual update. The record is checked for integrity so
 * the data model is not violated.
 * </p>
 */
public class UpdateLocalRecordAction extends AbstractRawRepoAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(UpdateLocalRecordAction.class);
    private static final XLogger bizLogger = XLoggerFactory.getXLogger(BusinessLoggerFilter.LOGGER_NAME);

    private Properties settings;

    public UpdateLocalRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord record) {
        super(UpdateLocalRecordAction.class.getSimpleName(), globalActionState, record);
        settings = properties;
    }

    /**
     * Creates child actions to update a local record.
     *
     * @return Status OK or FAILED_UPDATE_INTERNAL_ERROR if the record can
     * not be updated.
     * @throws UpdateException In case of critical errors.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        logger.entry();
        ServiceResult res = null;
        try {
            bizLogger.info("Handling record:\n{}", record);

            MarcRecordReader reader = new MarcRecordReader(this.record);
            String parentId = reader.parentId();
            if (reader.markedForDeletion()) {
                if (parentId == null) {
                    return res = performSingleDeleteAction();
                }
                return res = performVolumeDeleteAction();
            }

            if (parentId == null) {
                return res = performUpdateSingleRecordAction();
            }

            return res = performUpdateVolumeRecordAction(parentId);
        } finally {
            logger.exit(res);
        }
    }

    /**
     * Creates child actions to update a single record that is not
     * marked for deletion.
     * <p>
     * Singles records are updated as follows:
     * <ol>
     * <li>Store the record.</li>
     * <li>Remove existing links to other records.</li>
     * <li>Enqueue the record.</li>
     * </ol>
     * </p>
     *
     * @return OK.
     * @throws UpdateException In case of critical errors.
     */
    private ServiceResult performUpdateSingleRecordAction() throws UpdateException {
        logger.entry();

        try {
            StoreRecordAction storeRecordAction = new StoreRecordAction(state, settings, record);
            storeRecordAction.setMimetype(MarcXChangeMimeType.MARCXCHANGE);
            children.add(storeRecordAction);

            children.add(new RemoveLinksAction(state, record));

            EnqueueRecordAction enqueueRecordAction = new EnqueueRecordAction(state, settings, record);
            enqueueRecordAction.setMimetype(MarcXChangeMimeType.MARCXCHANGE);
            children.add(enqueueRecordAction);

            return ServiceResult.newOkResult();
        } finally {
            logger.exit();
        }
    }

    /**
     * Creates child actions to update a volume record that is not
     * marked for deletion.
     * <p>
     * Volume records are updated as follows:
     * <ol>
     * <li>Store the record.</li>
     * <li>Link it to the parent record.</li>
     * <li>Enqueue the record.</li>
     * </ol>
     * </p>
     *
     * @return OK.
     * @throws UpdateException In case of critical errors.
     */
    private ServiceResult performUpdateVolumeRecordAction(String parentId) throws UpdateException {
        logger.entry();

        try {
            MarcRecordReader reader = new MarcRecordReader(this.record);
            String recordId = reader.recordId();
            Integer agencyId = reader.agencyIdAsInteger();
            if (recordId.equals(parentId)) {
                String message = String.format(state.getMessages().getString("parent.point.to.itself"), recordId, agencyId);
                return ServiceResult.newErrorResult(UpdateStatusEnum.FAILED, message, state);
            }
            if (!rawRepo.recordExists(parentId, agencyId)) {
                String message = String.format(state.getMessages().getString("reference.record.not.exist"), recordId, agencyId, parentId, agencyId);
                return ServiceResult.newErrorResult(UpdateStatusEnum.FAILED, message, state);
            }
            StoreRecordAction storeRecordAction = new StoreRecordAction(state, settings, record);
            storeRecordAction.setMimetype(MarcXChangeMimeType.MARCXCHANGE);
            children.add(storeRecordAction);

            LinkRecordAction linkRecordAction = new LinkRecordAction(state, record);
            linkRecordAction.setLinkToRecordId(new RecordId(parentId, agencyId));
            children.add(linkRecordAction);

            EnqueueRecordAction enqueueRecordAction = new EnqueueRecordAction(state, settings, record);
            enqueueRecordAction.setMimetype(MarcXChangeMimeType.MARCXCHANGE);
            children.add(enqueueRecordAction);
            return ServiceResult.newOkResult();
        } finally {
            logger.exit();
        }
    }

    /**
     * Creates child actions to update a single record that is
     * marked for deletion.
     * <p>
     * Records are deleted as follows:
     * <ol>
     * <li>Remove existing links to other records.</li>
     * <li>Store the record and mark it as deleted.</li>
     * <li>Enqueue the record.</li>
     * </ol>
     * </p>
     *
     * @return OK.
     * @throws UpdateException In case of critical errors.
     */
    private ServiceResult performSingleDeleteAction() throws UpdateException {
        logger.entry();
        try {
            if (!rawRepo.children(record).isEmpty()) {
                MarcRecordReader reader = new MarcRecordReader(this.record);
                String recordId = reader.recordId();
                String message = String.format(state.getMessages().getString("delete.record.children.error"), recordId);
                return ServiceResult.newErrorResult(UpdateStatusEnum.FAILED, message, state);
            }
            if (!state.getHoldingsItems().getAgenciesThatHasHoldingsFor(this.record).isEmpty()) {
                AgencyNumber agencyNumber = new AgencyNumber(new MarcRecordReader(record).agencyId());
                if (state.getOpenAgencyService().hasFeature(agencyNumber.toString(), LibraryRuleHandler.Rule.AUTH_EXPORT_HOLDINGS)) {
                    String message = state.getMessages().getString("delete.local.with.holdings.error");
                    return ServiceResult.newErrorResult(UpdateStatusEnum.FAILED, message, state);
                }
            }
            children.add(new RemoveLinksAction(state, record));

            DeleteRecordAction deleteRecordAction = new DeleteRecordAction(state, settings, record);
            deleteRecordAction.setMimetype(MarcXChangeMimeType.MARCXCHANGE);
            children.add(deleteRecordAction);

            EnqueueRecordAction enqueueRecordAction = new EnqueueRecordAction(state, settings, record);
            enqueueRecordAction.setMimetype(MarcXChangeMimeType.MARCXCHANGE);
            children.add(enqueueRecordAction);

            return ServiceResult.newOkResult();
        } catch (OpenAgencyException ex) {
            throw new UpdateException(ex.getMessage(), ex);
        } finally {
            logger.exit();
        }
    }

    private ServiceResult performVolumeDeleteAction() throws UpdateException {
        logger.entry();
        ServiceResult result = null;
        try {
            result = performSingleDeleteAction();
            if (result.getStatus() != UpdateStatusEnum.OK) {
                return result;
            }
            MarcRecordReader reader = new MarcRecordReader(this.record);
            String parentId = reader.parentId();
            Integer agencyId = reader.agencyIdAsInteger();
            Set<RecordId> recordIdChildrenList = rawRepo.children(new RecordId(parentId, agencyId));
            if (recordIdChildrenList.size() != 1) {
                return result = ServiceResult.newOkResult();
            }
            MarcRecord mainRecord = new RawRepoDecoder().decodeRecord(rawRepo.fetchRecord(parentId, agencyId).getContent());
            MarcRecordWriter writer = new MarcRecordWriter(mainRecord);
            writer.markForDeletion();
            UpdateLocalRecordAction action = new UpdateLocalRecordAction(state, settings, mainRecord);
            children.add(action);
            return ServiceResult.newOkResult();
        } catch (UnsupportedEncodingException ex) {
            throw new UpdateException(ex.getMessage(), ex);
        } finally {
            logger.exit(result);
        }
    }
}
