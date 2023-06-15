package dk.dbc.updateservice.actions;

import dk.dbc.commons.jsonb.JSONBException;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.marc.reader.MarcReaderException;
import dk.dbc.opencat.connector.OpencatBusinessConnectorException;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.utils.DeferredLogger;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.MDC;

import static dk.dbc.updateservice.rest.ApplicationConfig.LOG_DURATION_THRESHOLD_MS;
import static dk.dbc.updateservice.utils.MDCUtil.MDC_TRACKING_ID_LOG_CONTEXT;

/**
 * This action is responsible for performing preprocessing of incoming records
 */
public class PreProcessingAction extends AbstractRawRepoAction {
    private static final DeferredLogger LOGGER = new DeferredLogger(PreProcessingAction.class);

    public PreProcessingAction(GlobalActionState globalActionState, MarcRecord marcRecord) {
        super(PreProcessingAction.class.getSimpleName(), globalActionState, marcRecord);
    }

    @Override
    public ServiceResult performAction() throws UpdateException {
        final StopWatch watch = new Log4JStopWatch("opencatBusiness.preprocess").setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        try {
            final String trackingId = MDC.get(MDC_TRACKING_ID_LOG_CONTEXT);
            // Check for empty record. Opencat-business will throw all kinds of errors when receiving a null record
            // Which means it is better to not send the record in the first place.
            if (!marcRecord.getFields().isEmpty()) {
                final MarcRecord preprocessedMarcRecord = state.getOpencatBusiness().preprocess(state.getMarcRecord(), trackingId);
                // It doesn't work to reassign the object, instead we just overwrite the fields
                marcRecord.getFields().clear();
                marcRecord.getFields().addAll(preprocessedMarcRecord.getFields());
            }

            return ServiceResult.newOkResult();
        } catch (JSONBException | OpencatBusinessConnectorException | MarcReaderException ex) {
            LOGGER.use(log -> log.error("Error during pre-processing", ex));
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, ex.getMessage());
        } finally {
            watch.stop();
        }
    }

}
