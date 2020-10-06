/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.jsonb.JSONBException;
import dk.dbc.opencat.connector.OpencatBusinessConnectorException;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.xml.bind.JAXBException;
import java.io.UnsupportedEncodingException;

/**
 * This action is responsible for performing preprocessing of incoming records
 */
public class PreProcessingAction extends AbstractRawRepoAction {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(UpdateRequestAction.class);

    public PreProcessingAction(GlobalActionState globalActionState) {
        super(PreProcessingAction.class.getSimpleName(), globalActionState);
    }

    @Override
    public ServiceResult performAction() throws UpdateException {
        LOGGER.entry();
        try {
            // Check for empty record. Opencat-business will throw all kinds of errors when receiving a null record
            // so it is better to not send the record in the first place.
            if (state.getMarcRecord().getFields().size() > 0) {
                final MarcRecord marcRecord = state.getOpencatBusiness().preprocess(state.getMarcRecord());
                // It doesn't work to reassign the object so instead we just overwrite the fields
                state.getMarcRecord().setFields(marcRecord.getFields());
            }

            return ServiceResult.newOkResult();
        } catch (UnsupportedEncodingException | JAXBException | JSONBException | OpencatBusinessConnectorException ex) {
            LOGGER.error("Error during pre-processing", ex);
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, ex.getMessage());
        } finally {
            LOGGER.exit();
        }
    }

}
