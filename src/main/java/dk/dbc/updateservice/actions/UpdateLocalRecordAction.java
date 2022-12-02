/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.AgencyNumber;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.common.records.utils.LogUtils;
import dk.dbc.common.records.utils.RecordContentTransformer;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.vipcore.exception.VipCoreException;
import dk.dbc.vipcore.libraryrules.VipCoreLibraryRulesConnector;
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
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(UpdateLocalRecordAction.class);

    private final Properties settings;

    public UpdateLocalRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord marcRecord) {
        super(UpdateLocalRecordAction.class.getSimpleName(), globalActionState, marcRecord);
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
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Handling record: {}", LogUtils.base64Encode(marcRecord));
        }

        MarcRecordReader reader = new MarcRecordReader(this.marcRecord);
        String parentId = reader.getParentRecordId();
        if (reader.markedForDeletion()) {
            if (parentId == null) {
                return performSingleDeleteAction();
            }
            return performVolumeDeleteAction();
        }

        if (parentId == null) {
            return performUpdateSingleRecordAction();
        }

        return performUpdateVolumeRecordAction(parentId);
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
     */
    private ServiceResult performUpdateSingleRecordAction() {
        final StoreRecordAction storeRecordAction = new StoreRecordAction(state, settings, marcRecord);
        storeRecordAction.setMimetype(MarcXChangeMimeType.MARCXCHANGE);
        children.add(storeRecordAction);
        children.add(new RemoveLinksAction(state, marcRecord));
        final EnqueueRecordAction enqueueRecordAction = new EnqueueRecordAction(state, settings, marcRecord);
        children.add(enqueueRecordAction);

        return ServiceResult.newOkResult();
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
        final MarcRecordReader reader = new MarcRecordReader(this.marcRecord);
        final String recordId = reader.getRecordId();
        final int agencyId = reader.getAgencyIdAsInt();
        if (recordId.equals(parentId)) {
            final String message = String.format(state.getMessages().getString("parent.point.to.itself"), recordId, agencyId);
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
        }
        if (!rawRepo.recordExists(parentId, agencyId)) {
            final String message = String.format(state.getMessages().getString("reference.record.not.exist"), recordId, agencyId, parentId, agencyId);
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
        }
        final StoreRecordAction storeRecordAction = new StoreRecordAction(state, settings, marcRecord);
        storeRecordAction.setMimetype(MarcXChangeMimeType.MARCXCHANGE);
        children.add(storeRecordAction);

        final LinkRecordAction linkRecordAction = new LinkRecordAction(state, marcRecord);
        linkRecordAction.setLinkToRecordId(new RecordId(parentId, agencyId));
        children.add(linkRecordAction);

        final EnqueueRecordAction enqueueRecordAction = new EnqueueRecordAction(state, settings, marcRecord);
        children.add(enqueueRecordAction);

        return ServiceResult.newOkResult();
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
        try {
            if (!rawRepo.children(recordId).isEmpty()) {
                final String message = String.format(state.getMessages().getString("delete.record.children.error"), recordId.getBibliographicRecordId());
                return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
            }
            if (!state.getAgenciesWithHoldings(marcRecord).isEmpty()) {
                final AgencyNumber agencyNumber = new AgencyNumber(new MarcRecordReader(marcRecord).getAgencyId());
                if (state.getVipCoreService().hasFeature(agencyNumber.toString(), VipCoreLibraryRulesConnector.Rule.AUTH_EXPORT_HOLDINGS)) {
                    final String message = state.getMessages().getString("delete.local.with.holdings.error");
                    return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
                }
            }
            children.add(new RemoveLinksAction(state, marcRecord));

            final DeleteRecordAction deleteRecordAction = new DeleteRecordAction(state, settings, marcRecord);
            deleteRecordAction.setMimetype(MarcXChangeMimeType.MARCXCHANGE);
            children.add(deleteRecordAction);

            final EnqueueRecordAction enqueueRecordAction = new EnqueueRecordAction(state, settings, marcRecord);
            children.add(enqueueRecordAction);

            return ServiceResult.newOkResult();
        } catch (VipCoreException ex) {
            throw new UpdateException(ex.getMessage(), ex);
        }
    }

    private ServiceResult performVolumeDeleteAction() throws UpdateException {
        try {
            final ServiceResult result = performSingleDeleteAction();
            if (result.getStatus() != UpdateStatusEnumDTO.OK) {
                return result;
            }
            final MarcRecordReader reader = new MarcRecordReader(this.marcRecord);
            final String parentRecordId = reader.getParentRecordId();
            final int parentAgencyId = reader.getParentAgencyIdAsInt();
            final Set<RecordId> recordIdChildrenList = rawRepo.children(new RecordId(parentRecordId, parentAgencyId));
            if (recordIdChildrenList.size() != 1) {
                return ServiceResult.newOkResult();
            }
            final MarcRecord mainRecord = RecordContentTransformer.decodeRecord(rawRepo.fetchRecord(parentRecordId, parentAgencyId).getContent());
            final MarcRecordWriter writer = new MarcRecordWriter(mainRecord);
            writer.markForDeletion();
            final UpdateLocalRecordAction action = new UpdateLocalRecordAction(state, settings, mainRecord);
            children.add(action);

            return ServiceResult.newOkResult();
        } catch (UnsupportedEncodingException ex) {
            throw new UpdateException(ex.getMessage(), ex);
        }
    }
}
