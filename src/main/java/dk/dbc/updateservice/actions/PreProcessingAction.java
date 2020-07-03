/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.PreProcessingHandler;
import dk.dbc.updateservice.update.UpdateException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

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
            final PreProcessingHandler preprocessingHandler = new PreProcessingHandler(state.getRawRepo(), state.getMessages());
            preprocessingHandler.preProcess(state.getMarcRecord());

            return ServiceResult.newOkResult();
        } catch (UpdateException ex) {
            LOGGER.error("Error during pre-processing", ex);
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, ex.getMessage());
        } catch (UnsupportedEncodingException ex) {
            throw new UpdateException("Caught unexpected exception: " + ex.toString());
        } finally {
            LOGGER.exit();
        }
    }

}
