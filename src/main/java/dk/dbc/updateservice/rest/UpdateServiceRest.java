package dk.dbc.updateservice.rest;

import dk.dbc.commons.jsonb.JSONBContext;
import dk.dbc.commons.jsonb.JSONBException;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.rawrepo.dto.RecordEntryDTO;
import dk.dbc.updateservice.actions.GlobalActionState;
import dk.dbc.updateservice.actions.ServiceResult;
import dk.dbc.updateservice.dto.AuthenticationDTO;
import dk.dbc.updateservice.dto.OptionEnumDTO;
import dk.dbc.updateservice.dto.SchemasRequestDTO;
import dk.dbc.updateservice.dto.SchemasResponseDTO;
import dk.dbc.updateservice.dto.UpdateRecordResponseDTO;
import dk.dbc.updateservice.dto.UpdateServiceRequestDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.dto.writers.UpdateRecordResponseDTOWriter;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.update.UpdateServiceCore;
import dk.dbc.updateservice.utils.DeferredLogger;
import dk.dbc.updateservice.validate.Validator;
import dk.dbc.util.Timed;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.ejb.EJBException;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.annotation.RegistryType;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.MDC;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dk.dbc.updateservice.rest.ApplicationConfig.LOG_DURATION_THRESHOLD_MS;
import static dk.dbc.updateservice.utils.MDCUtil.MDC_TRACKING_ID_LOG_CONTEXT;


@Stateless
@Path("/api")
public class UpdateServiceRest {
    private static final DeferredLogger LOGGER = new DeferredLogger(UpdateServiceRest.class);
    private final JSONBContext jsonbContext = new JSONBContext();
    private GlobalActionState globalActionState;

    @EJB
    UpdateServiceCore updateServiceCore;

    @Inject
    @RegistryType(type = MetricRegistry.Type.APPLICATION)
    MetricRegistry metricRegistry;

    static final Metadata getSchemasTimerMetadata = Metadata.builder()
            .withName("update_getschemas_timer")
            .withDescription("Duration of getschemas")
            .withType(MetricType.SIMPLE_TIMER)
            .withUnit(MetricUnits.MILLISECONDS).build();

    static final Metadata getSchemasErrorCounterMetadata = Metadata.builder()
            .withName("update_getschemas_error_counter")
            .withDescription("Number of errors caught in method getSchemas")
            .withType(MetricType.COUNTER)
            .withUnit("errors").build();

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

    static final Metadata groupIdCounterMetaData = Metadata.builder()
            .withName("update_groupid_requests_counter")
            .withDescription("Number of requests per agency/group id")
            .withType(MetricType.COUNTER)
            .withUnit("requests").build();

    static final Metadata putRecordCounterMetaData = Metadata.builder()
            .withName("update_put_record_requests_counter")
            .withDescription("Number of requests to updaterecord")
            .withType(MetricType.COUNTER)
            .withUnit("requests").build();

    static final Metadata putRecordDurationMetaData = Metadata.builder()
            .withName("update_put_record_requests_timer")
            .withDescription("Duration of updaterecord in milliseconds")
            .withType(MetricType.SIMPLE_TIMER)
            .withUnit(MetricUnits.MILLISECONDS).build();

    @PostConstruct
    protected void init() {
        globalActionState = new GlobalActionState();
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
    public UpdateRecordResponseDTO updateRecord(@Context HttpServletRequest request,
                                                UpdateServiceRequestDTO updateRecordRequest) {
        final StopWatch watch = new Log4JStopWatch().setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        return LOGGER.call(log -> {
            MDC.put(MDC_TRACKING_ID_LOG_CONTEXT, updateRecordRequest.getTrackingId());
            UpdateRecordResponseDTO updateRecordResponseDTO = null;
            try {
                log.infoImmediately("updateRecord REST received: {}", updateRecordRequest);

                if (!updateServiceCore.isServiceReady()) {
                    log.info("Updateservice not ready yet, leaving");
                    return null;
                }
                globalActionState.setRequest(request);
                updateRecordResponseDTO = updateServiceCore.updateRecord(updateRecordRequest, globalActionState);

                return updateRecordResponseDTO;
            } catch (Throwable e) {
                log.error("Caught unexpected exception during updateRecord", e);
                final ServiceResult serviceResult = ServiceResult.newFatalResult(UpdateStatusEnumDTO.FAILED, "Caught unexpected exception");
                return UpdateRecordResponseDTOWriter.newInstance(serviceResult);
            } finally {
                final String validateOnly = updateRecordRequest.getOptionsDTO() != null &&
                        updateRecordRequest.getOptionsDTO().getOption().contains(OptionEnumDTO.VALIDATE_ONLY) ? "yes" : "no";
                watch.stop(UpdateServiceCore.UPDATERECORD_STOPWATCH);
                log.infoImmediately("updateRecord REST returns: {}", updateRecordResponseDTO);

                metricRegistry.counter(updateRecordCounterMetaData,
                                new Tag("schemaName", updateRecordRequest.getSchemaName()),
                                new Tag("validateOnly", validateOnly))
                        .inc();

                metricRegistry.simpleTimer(updateRecordDurationMetaData,
                                new Tag("schemaName", updateRecordRequest.getSchemaName()),
                                new Tag("validateOnly", validateOnly))
                        .update(Duration.ofMillis(watch.getElapsedTime()));

                incrementGroupIdCounter(updateRecordRequest);

                MDC.clear();
            }
        });
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
        StopWatch watch = new Log4JStopWatch().setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        MDC.put(MDC_TRACKING_ID_LOG_CONTEXT, schemasRequestDTO.getTrackingId());
        return LOGGER.call(log -> {
            SchemasResponseDTO schemasResponseDTO = null;
            final SimpleTimer getSchemasTimer = metricRegistry.simpleTimer(getSchemasTimerMetadata);
            final Counter getSchemasErrorCounter = metricRegistry.counter(getSchemasErrorCounterMetadata);

            try {
                log.infoImmediately("getSchemas REST received: {}", schemasRequestDTO);

                if (!updateServiceCore.isServiceReady()) {
                    log.info("Updateservice (getSchemas) not ready yet.");
                    return null;
                }
                schemasResponseDTO = updateServiceCore.getSchemas(schemasRequestDTO);
                return schemasResponseDTO;
            } catch (Throwable e) {
                log.error("Caught unexpected exception during getSchemas", e);
                schemasResponseDTO = new SchemasResponseDTO();
                schemasResponseDTO.setUpdateStatusEnumDTO(UpdateStatusEnumDTO.FAILED);
                schemasResponseDTO.setErrorMessage("Caught unexpected exception");
                schemasResponseDTO.setError(true);
                getSchemasErrorCounter.inc();
                return schemasResponseDTO;
            } finally {
                log.infoImmediately("getSchemas REST returns: {}", schemasResponseDTO);
                watch.stop(UpdateServiceCore.GET_SCHEMAS_STOPWATCH);
                MDC.clear();
                if (schemasResponseDTO != null && schemasResponseDTO.getUpdateStatusEnumDTO() != UpdateStatusEnumDTO.OK) {
                    getSchemasErrorCounter.inc();
                }

                incrementGroupIdCounter(schemasRequestDTO);

                getSchemasTimer.update(Duration.ofMillis(watch.getElapsedTime()));
            }
        });
    }

    @PUT
    @Path("v1/kafka/{key}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response putRecord(@PathParam("key") String key,
                              String body) {
        StopWatch watch = new Log4JStopWatch().setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        try {
            splitKey(key); // Just check if the key has the correct format
            final RecordEntryDTO recordDTO = jsonbContext.unmarshall(body, RecordEntryDTO.class);

            updateServiceCore.updateRecord(recordDTO);
            return Response.ok().build();
        } catch (UpdateException | JSONBException ex) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build();
        } finally {
            metricRegistry.counter(updateRecordCounterMetaData)
                    .inc();

            metricRegistry.simpleTimer(updateRecordDurationMetaData)
                    .update(Duration.ofMillis(watch.getElapsedTime()));
        }
    }

    @DELETE
    @Path("v1/kafka/{key}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response deleteRecord() {
        return Response.status(Response.Status.NOT_IMPLEMENTED).build();
    }

    private RecordId splitKey(String key) throws UpdateException {
        final String patternString = "(\\d{6}):(.*)";
        final Pattern r = Pattern.compile(patternString);
        final Matcher matcher = r.matcher(key);

        if (!matcher.matches()) {
            throw new UpdateException(String.format("Unknown format of key: '%s'. Doesn't match %s", key, patternString));
        }

        final int agencyId = Integer.parseInt(matcher.group(1));
        final String bibliographicRecordId = matcher.group(2);

        return new RecordId(bibliographicRecordId, agencyId);
    }

    private void incrementGroupIdCounter(UpdateServiceRequestDTO updateServiceRequestDTO) {
        Optional.ofNullable(updateServiceRequestDTO)
                .map(UpdateServiceRequestDTO::getAuthenticationDTO)
                .map(AuthenticationDTO::getGroupId)
                .filter(groupId -> !"010100".equals(groupId) || RawRepo.DBC_AGENCY_ALL.contains(groupId))
                .ifPresent(groupId -> metricRegistry.counter(groupIdCounterMetaData, new Tag("groupId", groupId)).inc());
    }

    private void incrementGroupIdCounter(SchemasRequestDTO schemasRequestDTO) {
        Optional.ofNullable(schemasRequestDTO)
                .map(SchemasRequestDTO::getAuthenticationDTO)
                .map(AuthenticationDTO::getGroupId)
                .filter(groupId -> !"010100".equals(groupId) || RawRepo.DBC_AGENCY_ALL.contains(groupId))
                .ifPresent(groupId -> metricRegistry.counter(groupIdCounterMetaData, new Tag("groupId", groupId)).inc());
    }
}
