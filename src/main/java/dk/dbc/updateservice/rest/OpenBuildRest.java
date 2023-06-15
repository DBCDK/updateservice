package dk.dbc.updateservice.rest;

import dk.dbc.commons.jsonb.JSONBContext;
import dk.dbc.commons.jsonb.JSONBException;
import dk.dbc.log.DBCTrackedLogContext;
import dk.dbc.updateservice.dto.BuildRequestDTO;
import dk.dbc.updateservice.dto.BuildResponseDTO;
import dk.dbc.updateservice.dto.BuildStatusEnumDTO;
import dk.dbc.updateservice.update.OpenBuildCore;
import dk.dbc.util.Timed;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.annotation.RegistryType;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.Duration;

import static dk.dbc.updateservice.rest.ApplicationConfig.LOG_DURATION_THRESHOLD_MS;

@Stateless
@Path("/api")
public class OpenBuildRest {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(OpenBuildRest.class);
    private static final JSONBContext jsonbContext = new JSONBContext();

    @EJB
    OpenBuildCore openBuildCore;

    @Inject
    @RegistryType(type = MetricRegistry.Type.APPLICATION)
    MetricRegistry metricRegistry;

    static final Metadata buildTimerMetadata = Metadata.builder()
            .withName("update_build_timer")
            .withDescription("Duration of build")
            .withType(MetricType.SIMPLE_TIMER)
            .withUnit(MetricUnits.MILLISECONDS).build();

    static final Metadata builErrorCounterMetadata = Metadata.builder()
            .withName("update_build_error_counter")
            .withDescription("Number of errors caught in method build")
            .withType(MetricType.COUNTER)
            .withUnit("errors").build();

    @POST
    @Path("v1/openbuildservice")
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_JSON})
    @Timed
    public String build(BuildRequestDTO buildRequestDTO) throws JSONBException {
        final StopWatch watch = new Log4JStopWatch("OpenBuildRest.build").setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        final SimpleTimer buildTimer = metricRegistry.simpleTimer(buildTimerMetadata);
        final DBCTrackedLogContext dbcTrackedLogContext = new DBCTrackedLogContext(OpenBuildCore.createTrackingId());

        BuildResponseDTO buildResponseDTO = null;
        try {
            LOGGER.info("Build request: {}", buildRequestDTO);

            buildResponseDTO = openBuildCore.build(buildRequestDTO);
            if (buildResponseDTO != null && buildResponseDTO.getBuildStatusEnumDTO() != BuildStatusEnumDTO.OK) {
                metricRegistry.counter(builErrorCounterMetadata,
                        new Tag("status", buildResponseDTO.getBuildStatusEnumDTO().toString())).inc();
            }
            return jsonbContext.marshall(buildResponseDTO);
        } finally {
            LOGGER.info("Build response: {}", buildResponseDTO);
            watch.stop();
            dbcTrackedLogContext.close();
            buildTimer.update(Duration.ofMillis(watch.getElapsedTime()));
        }
    }
}
