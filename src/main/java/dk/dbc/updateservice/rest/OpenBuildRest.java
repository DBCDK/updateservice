/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.rest;

import dk.dbc.jsonb.JSONBContext;
import dk.dbc.jsonb.JSONBException;
import dk.dbc.log.DBCTrackedLogContext;
import dk.dbc.updateservice.dto.BuildRequestDTO;
import dk.dbc.updateservice.dto.BuildResponseDTO;
import dk.dbc.updateservice.dto.BuildStatusEnumDTO;
import dk.dbc.updateservice.update.OpenBuildCore;
import dk.dbc.util.Timed;
import java.time.Duration;
import javax.inject.Inject;
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

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

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
        StopWatch watch = new Log4JStopWatch("OpenBuildRest.build");
        final SimpleTimer buildTimer = metricRegistry.simpleTimer(buildTimerMetadata);

        new DBCTrackedLogContext(OpenBuildCore.createTrackingId());
        LOGGER.entry();
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
            LOGGER.exit();
            DBCTrackedLogContext.remove();
            buildTimer.update(Duration.ofMillis(watch.getElapsedTime()));
        }
    }
}
