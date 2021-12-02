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
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.MDC;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.xml.bind.JAXBException;
import java.io.UnsupportedEncodingException;

import static dk.dbc.updateservice.utils.MDCUtil.MDC_TRACKING_ID_LOG_CONTEXT;

/**
 * This action is responsible for performing preprocessing of incoming records
 */
public class PreProcessingAction extends AbstractRawRepoAction {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(UpdateRequestAction.class);

    public PreProcessingAction(GlobalActionState globalActionState, MarcRecord marcRecord) {
        super(PreProcessingAction.class.getSimpleName(), globalActionState, marcRecord);
    }

    @Override
    public ServiceResult performAction() throws UpdateException {
        final StopWatch watch = new Log4JStopWatch("opencatBusiness.preprocess");
        try {
            final String trackingId = MDC.get(MDC_TRACKING_ID_LOG_CONTEXT);
            // Check for empty record. Opencat-business will throw all kinds of errors when receiving a null record
            // so it is better to not send the record in the first place.
            if (!marcRecord.getFields().isEmpty()) {
                final MarcRecord preprocessedMarcRecord = state.getOpencatBusiness().preprocess(state.getMarcRecord(), trackingId);
                // It doesn't work to reassign the object so instead we just overwrite the fields
                marcRecord.setFields(preprocessedMarcRecord.getFields());
            }

            return ServiceResult.newOkResult();
        } catch (UnsupportedEncodingException | JAXBException | JSONBException | OpencatBusinessConnectorException ex) {
            LOGGER.error("Error during pre-processing", ex);
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, ex.getMessage());
        } finally {
            watch.stop();
        }
    }

}
