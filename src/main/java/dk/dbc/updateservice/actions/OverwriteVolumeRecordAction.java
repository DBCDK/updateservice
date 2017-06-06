/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
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
            logger.info("Handling record:\n{}", record);
            MarcRecordReader reader = new MarcRecordReader(record);
            if (RawRepo.DBC_PRIVATE_AGENCY_LIST.contains(reader.agencyId())) {
                return result = performActionArticle();
            } else {
                return result = performActionDefault();
            }

        } catch (ScripterException | UnsupportedEncodingException ex) {
            return result = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, ex.getMessage(), state);
        } finally {
            logger.exit(result);
        }
    }

    private ServiceResult performActionArticle() throws UnsupportedEncodingException, UpdateException {
        ServiceResult result = ServiceResult.newOkResult();

        children.add(StoreRecordAction.newStoreMarcXChangeAction(state, settings, record));
        children.add(EnqueueRecordAction.newEnqueueAction(state, record, settings));

        return result;
    }

    private ServiceResult performActionDefault() throws UnsupportedEncodingException, UpdateException, ScripterException {
        ServiceResult result;
        MarcRecordReader reader = new MarcRecordReader(record);
        String recordId = reader.recordId();
        String parentId = reader.parentId();
        Integer agencyId = reader.agencyIdAsInteger();

        if (recordId.equals(parentId)) {
            Integer errorAgencyId = agencyId;
            if (errorAgencyId.equals(RawRepo.COMMON_AGENCY)) {
                errorAgencyId = RawRepo.DBC_ENRICHMENT;
            }
            String message = String.format(state.getMessages().getString("parent.point.to.itself"), recordId, errorAgencyId);
            logger.error("Unable to create sub actions due to an error: {}", message);
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
        }

        if (!rawRepo.recordExists(parentId, agencyId)) {
            String message = String.format(state.getMessages().getString("reference.record.not.exist"), recordId, agencyId, parentId, agencyId);
            logger.error("Unable to create sub actions due to an error: {}", message);
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
        }

        MarcRecord currentRecord = loadCurrentRecord();
        children.add(StoreRecordAction.newStoreMarcXChangeAction(state, settings, record));
        children.add(new RemoveLinksAction(state, record));
        children.add(LinkRecordAction.newLinkParentAction(state, record));
        children.addAll(createActionsForCreateOrUpdateEnrichments(currentRecord));

        result = performActionsFor002Links();
        children.add(EnqueueRecordAction.newEnqueueAction(state, record, settings));

        return result;
    }
}
