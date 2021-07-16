/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.utils.LogUtils;
import dk.dbc.rawrepo.RawRepoException;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.Properties;

/**
 * Action to overwrite an existing volume record.
 */
public class OverwriteVolumeRecordAction extends OverwriteSingleRecordAction {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(OverwriteVolumeRecordAction.class);

    public OverwriteVolumeRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord marcRecord) {
        super(globalActionState, properties, marcRecord);
        setName("OverwriteVolumeRecordAction");
        state = globalActionState;
    }

    /**
     * Performs this actions and may create any child actions.
     *
     * @return A list of ValidationError to be reported in the web service response.
     * @throws UpdateException In case of an error.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        LOGGER.entry();
        ServiceResult result = ServiceResult.newOkResult();
        try {
            LOGGER.info("Handling record: {}", LogUtils.base64Encode(marcRecord));
            MarcRecordReader reader = new MarcRecordReader(marcRecord);
            if (RawRepo.DBC_PRIVATE_AGENCY_LIST.contains(reader.getAgencyId())) {
                performActionDBCRecord();
            } else {
                result = performActionDefault();
            }
            return result;

        } catch (RawRepoException | UnsupportedEncodingException ex) {
            return result = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, ex.getMessage());
        } finally {
            LOGGER.exit(result);
        }
    }

    private ServiceResult performActionDefault() throws UnsupportedEncodingException, UpdateException, RawRepoException {
        MarcRecordReader reader = new MarcRecordReader(marcRecord);
        String recordId = reader.getRecordId();
        String parentId = reader.getParentRecordId();
        int agencyId = reader.getAgencyIdAsInt();
        int parentAgencyId = reader.getParentAgencyIdAsInt();

        if (recordId.equals(parentId)) {
            int errorAgencyId = agencyId;
            if (errorAgencyId == RawRepo.COMMON_AGENCY) {
                errorAgencyId = RawRepo.DBC_ENRICHMENT;
            }
            String message = String.format(state.getMessages().getString("parent.point.to.itself"), recordId, errorAgencyId);
            LOGGER.error("Unable to create sub actions due to an error: {}", message);
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
        }

        if (!rawRepo.recordExists(parentId, parentAgencyId)) {
            String message = String.format(state.getMessages().getString("reference.record.not.exist"), recordId, agencyId, parentId, parentAgencyId);
            LOGGER.error("Unable to create sub actions due to an error: {}", message);
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
        }

        MarcRecord currentExpandedRecord = loadCurrentRecord();
        MarcRecord newExpandedRecord = expandRecord();

        children.add(StoreRecordAction.newStoreMarcXChangeAction(state, settings, marcRecord));
        children.add(new RemoveLinksAction(state, marcRecord));
        children.add(LinkRecordAction.newLinkParentAction(state, marcRecord));
        children.addAll(createActionsForCreateOrUpdateEnrichments(newExpandedRecord, currentExpandedRecord));
        children.add(new LinkAuthorityRecordsAction(state, marcRecord));
        children.add(EnqueueRecordAction.newEnqueueAction(state, marcRecord, settings));
        children.addAll(getEnqueuePHHoldingsRecordActions(state, marcRecord));

        return ServiceResult.newOkResult();
    }
}
