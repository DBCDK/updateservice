/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.utils.LogUtils;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.javascript.ScripterException;
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
    private static final XLogger logger = XLoggerFactory.getXLogger(OverwriteVolumeRecordAction.class);

    GlobalActionState state;

    public OverwriteVolumeRecordAction(GlobalActionState globalActionState, Properties properties, MarcRecord record) {
        super(globalActionState, properties, record);
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
        logger.entry();
        ServiceResult result = ServiceResult.newOkResult();
        try {
            logger.info("Handling record: {}", LogUtils.base64Encode(record));
            MarcRecordReader reader = new MarcRecordReader(record);
            if (RawRepo.DBC_PRIVATE_AGENCY_LIST.contains(reader.getAgencyId())) {
                return result = performActionDBCRecord();
            } else {
                return result = performActionDefault();
            }

        } catch (ScripterException | UnsupportedEncodingException ex) {
            return result = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, ex.getMessage(), state);
        } finally {
            logger.exit(result);
        }
    }

    private ServiceResult performActionDefault() throws UnsupportedEncodingException, UpdateException, ScripterException {
        ServiceResult result;
        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.getRecordId();
        String parentId = reader.getParentRecordId();
        Integer agencyId = reader.getAgencyIdAsInteger();
        Integer parentAgencyId = reader.getParentAgencyIdAsInteger();

        if (recordId.equals(parentId)) {
            Integer errorAgencyId = agencyId;
            if (errorAgencyId.equals(RawRepo.COMMON_AGENCY)) {
                errorAgencyId = RawRepo.DBC_ENRICHMENT;
            }
            String message = String.format(state.getMessages().getString("parent.point.to.itself"), recordId, errorAgencyId);
            logger.error("Unable to create sub actions due to an error: {}", message);
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
        }

        if (!rawRepo.recordExists(parentId, parentAgencyId)) {
            String message = String.format(state.getMessages().getString("reference.record.not.exist"), recordId, agencyId, parentId, parentAgencyId);
            logger.error("Unable to create sub actions due to an error: {}", message);
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
        }

        MarcRecord currentRecord = loadCurrentRecord();
        children.add(StoreRecordAction.newStoreMarcXChangeAction(state, settings, record));
        children.add(new RemoveLinksAction(state, record));
        children.add(LinkRecordAction.newLinkParentAction(state, record));
        children.addAll(createActionsForCreateOrUpdateEnrichments(record, currentRecord));

        result = performActionsFor002Links();
        children.add(new LinkAuthorityRecordsAction(state, record));
        children.add(EnqueueRecordAction.newEnqueueAction(state, record, settings));
        children.addAll(getEnqueuePHHoldingsRecordActions(state, record));

        return result;
    }
}
