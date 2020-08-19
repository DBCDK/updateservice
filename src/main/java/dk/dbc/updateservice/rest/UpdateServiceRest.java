package dk.dbc.updateservice.rest;

import dk.dbc.updateservice.actions.GlobalActionState;
import dk.dbc.updateservice.actions.ServiceResult;
import dk.dbc.updateservice.dto.OptionEnumDTO;
import dk.dbc.updateservice.dto.SchemasRequestDTO;
import dk.dbc.updateservice.dto.SchemasResponseDTO;
import dk.dbc.updateservice.dto.UpdateRecordResponseDTO;
import dk.dbc.updateservice.dto.UpdateServiceRequestDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.dto.writers.UpdateRecordResponseDTOWriter;
import dk.dbc.updateservice.update.UpdateServiceCore;
import dk.dbc.updateservice.validate.Validator;
import dk.dbc.util.Timed;
import java.time.Duration;
import javax.inject.Inject;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.annotation.RegistryType;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.MDC;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.xml.ws.WebServiceContext;

import static dk.dbc.updateservice.utils.MDCUtil.MDC_TRACKING_ID_LOG_CONTEXT;


@Stateless
@Path("/api")
public class UpdateServiceRest {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(UpdateServiceRest.class);
    private GlobalActionState globalActionState;

    @EJB
    UpdateServiceCore updateServiceCore;

    @Context
    private WebServiceContext wsContext;

    @Context
    private HttpServletRequest request;

    @Inject
    @RegistryType(type = MetricRegistry.Type.APPLICATION)
    MetricRegistry metricRegistry;

    static final Metadata updateRecordCounterMetaData = Metadata.builder()
            .withName("update_updaterecord_requests_counter")
            .withDescription("Number of requests to updaterecord")
            .withType(MetricType.COUNTER)
            .withUnit("requests").build();

    static final Metadata updateRecordDurationMetaData = Metadata.builder()
            .withName("update_updaterecord_requests_timer")
            .withDescription("Duration of updaterecord in milliseconds")
            .withType(MetricType.SIMPLE_TIMER)
            .withUnit(MetricUnits.MILLISECONDS).build();

    @PostConstruct
    protected void init() {
        globalActionState = new GlobalActionState();
        globalActionState.setWsContext(wsContext);
        globalActionState.setRequest(request);
    }

    /**
     * Update or validate a bibliographic record to the rawrepo.
     * <p>
     * This operation has 2 uses:
     * <ol>
     * <li>Validation of the record only.</li>
     * <li>Validation and update of the record</li>
     * </ol>
     * The actual operation is specified in the request by Options object
     *
     * @param updateRecordRequest The request.
     * @return Returns an instance of UpdateRecordResponseDTO with the status and result of the update.
     * @throws EJBException in the case of an error.
     */
    @POST
    @Path("v1/updateservice")
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_JSON})
    @Timed
    public UpdateRecordResponseDTO updateRecord(UpdateServiceRequestDTO updateRecordRequest) {
        LOGGER.entry();
        final StopWatch watch = new Log4JStopWatch();
        MDC.put(MDC_TRACKING_ID_LOG_CONTEXT, updateRecordRequest.getTrackingId());
        UpdateRecordResponseDTO updateRecordResponseDTO = null;
        try {
            LOGGER.info("updateRecord REST received: {}", updateRecordRequest);

            if (!updateServiceCore.isServiceReady(globalActionState)) {
                LOGGER.info("Updateservice not ready yet, leaving");
                return null;
            }

            updateRecordResponseDTO = updateServiceCore.updateRecord(updateRecordRequest, globalActionState);

            return updateRecordResponseDTO;
        } catch (Throwable e) {
            LOGGER.error("Caught unexpected exception during updateRecord", e);
            final ServiceResult serviceResult = ServiceResult.newFatalResult(UpdateStatusEnumDTO.FAILED, "Caught unexpected exception");
            return UpdateRecordResponseDTOWriter.newInstance(serviceResult);
        } finally {
            final String validateOnly = updateRecordRequest.getOptionsDTO() != null &&
                    updateRecordRequest.getOptionsDTO().getOption().contains(OptionEnumDTO.VALIDATE_ONLY)?"yes":"no";
            watch.stop(UpdateServiceCore.UPDATERECORD_STOPWATCH);
            LOGGER.info("updateRecord REST returns: {}", updateRecordResponseDTO);

            metricRegistry.counter(updateRecordCounterMetaData,
                    new Tag("authAgency", updateRecordRequest.getAuthenticationDTO().getGroupId()),
                    new Tag("schemaName", updateRecordRequest.getSchemaName()),
                    new Tag("validateOnly", validateOnly))
                    .inc();

            metricRegistry.simpleTimer(updateRecordDurationMetaData,
                    new Tag("authAgency", updateRecordRequest.getAuthenticationDTO().getGroupId()),
                    new Tag("schemaName", updateRecordRequest.getSchemaName()),
                    new Tag("validateOnly", validateOnly))
                    .update(Duration.ofMillis(watch.getElapsedTime()));

            LOGGER.exit();
            MDC.clear();
        }
    }

    /**
     * Returns a list of validation schemes.
     * <p>
     * The actual lookup of validation schemes is done by the Validator EJB
     * ({@link Validator#getValidateSchemas ()})
     *
     * @param schemasRequestDTO The request.
     * @return Returns an instance of SchemasResponseDTO with the list of
     * validation schemes.
     * @throws EJBException In case of an error.
     */
    @POST
    @Path("v1/updateservice/getschemas")
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_JSON})
    @Timed
    public SchemasResponseDTO getSchemas(SchemasRequestDTO schemasRequestDTO) {
        LOGGER.entry();
        StopWatch watch = new Log4JStopWatch();
        MDC.put(MDC_TRACKING_ID_LOG_CONTEXT, schemasRequestDTO.getTrackingId());
        SchemasResponseDTO schemasResponseDTO = null;

        try {
            LOGGER.info("getSchemas REST received: {}", schemasRequestDTO);

            if (!updateServiceCore.isServiceReady(globalActionState)) {
                LOGGER.info("Updateservice (getSchemas) not ready yet.");
                return null;
            }
            schemasResponseDTO = updateServiceCore.getSchemas(schemasRequestDTO);
            return schemasResponseDTO;
        } catch (Throwable e) {
            LOGGER.error("Caught unexpected exception during getSchemas", e);
            schemasResponseDTO = new SchemasResponseDTO();
            schemasResponseDTO.setUpdateStatusEnumDTO(UpdateStatusEnumDTO.FAILED);
            schemasResponseDTO.setErrorMessage("Caught unexpected exception");
            schemasResponseDTO.setError(true);

            return schemasResponseDTO;
        } finally {
            LOGGER.info("getSchemas REST returns: {}", schemasResponseDTO);
            watch.stop(UpdateServiceCore.GET_SCHEMAS_STOPWATCH);
            LOGGER.exit();
            MDC.clear();
        }
    }
}
