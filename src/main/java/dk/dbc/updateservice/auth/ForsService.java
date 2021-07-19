/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.auth;

import dk.dbc.commons.metricshandler.CounterMetric;
import dk.dbc.commons.metricshandler.MetricsHandlerBean;
import dk.dbc.commons.metricshandler.SimpleTimerMetric;
import dk.dbc.forsrights.client.ForsRights;
import dk.dbc.forsrights.client.ForsRightsException;
import dk.dbc.forsrights.client.ForsRightsServiceFromURL;
import dk.dbc.updateservice.actions.GlobalActionState;
import dk.dbc.updateservice.update.JNDIResources;
import java.time.Duration;
import javax.inject.Inject;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.PostConstruct;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.Singleton;
import java.util.Properties;

/**
 * This class encapsulate calls to the Forsrights service using SOAP.
 *
 * @author stp
 */
@Singleton
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
public class ForsService {

    @Inject
    MetricsHandlerBean metricsHandlerBean;

    private static class ForsServiceErrorCounterMetrics implements CounterMetric {
        private final Metadata metadata;

        public ForsServiceErrorCounterMetrics(Metadata metadata) {
            this.metadata = validateMetadata(metadata);
        }

        @Override
        public Metadata getMetadata() {
            return metadata;
        }
    }

    private static final class ForsServiceTimingMetrics implements SimpleTimerMetric {
        private final Metadata metadata;

        public ForsServiceTimingMetrics(Metadata metadata) {
            this.metadata = validateMetadata(metadata);
        }

        @Override
        public Metadata getMetadata() {
            return metadata;
        }
    }

    static final ForsServiceTimingMetrics forsServiceTimingMetrics =
            new ForsServiceTimingMetrics(Metadata.builder()
                    .withName("update_forsservice_timer")
                    .withDescription("Duration of various forsservice calls")
                    .withType(MetricType.SIMPLE_TIMER)
                    .withUnit(MetricUnits.MILLISECONDS).build());

    static final ForsServiceErrorCounterMetrics forsServiceErrorCounterMetrics = new ForsServiceErrorCounterMetrics(Metadata.builder()
            .withName("update_forsservice_error_counter")
            .withDescription("Number of errors caught in forsservice calls")
            .withType(MetricType.COUNTER)
            .withUnit("requests").build());

    private static final String METHOD_NAME_KEY = "method";
    protected static final String ERROR_TYPE = "errortype";

    private static final XLogger LOGGER = XLoggerFactory.getXLogger(ForsService.class);
    private static final long CACHE_ENTRY_TIMEOUT = 10 * 60 * 1000;
    private static final int CONNECT_TIMEOUT = 1 * 60 * 1000;
    private static final int REQUEST_TIMEOUT = 3 * 60 * 1000;

    private final Properties settings = JNDIResources.getProperties();

    private ForsRights forsRights;

    @PostConstruct
    public void init() {
        final StopWatch watch = new Log4JStopWatch("service.forsrights.init");
        try {
            final ForsRights.RightsCache forsRightsCache = new ForsRights.RightsCache(CACHE_ENTRY_TIMEOUT);
            ForsRightsServiceFromURL.Builder builder = ForsRightsServiceFromURL.builder();
            builder = builder.connectTimeout(CONNECT_TIMEOUT).requestTimeout(REQUEST_TIMEOUT);
            forsRights = builder.build(settings.getProperty(JNDIResources.FORSRIGHTS_URL)).forsRights(forsRightsCache);
        } finally {
            watch.stop();
        }
    }

    /**
     * Calls the forsrights service
     *
     * @param globalActionState global state object
     * @return A response from forsrights.
     */
    public ForsRights.RightSet forsRights(GlobalActionState globalActionState) throws ForsRightsException {
        final StopWatch watch = new Log4JStopWatch("service.forsrights.rights");
        final Tag methodTag = new Tag(METHOD_NAME_KEY, "lookupRight");

        try {
            LOGGER.info("Authenticating user {}/{} against forsright at {}", globalActionState.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId(), globalActionState.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), settings.getProperty(JNDIResources.FORSRIGHTS_URL));
            return forsRights.lookupRight(globalActionState.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId(), globalActionState.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), globalActionState.getUpdateServiceRequestDTO().getAuthenticationDTO().getPassword(), null);
        } catch (Exception e) {
            metricsHandlerBean.increment(forsServiceErrorCounterMetrics,
                    methodTag,
                    new Tag(ERROR_TYPE, e.getMessage().toLowerCase()));
            throw e;
        } finally {
            watch.stop();
            metricsHandlerBean.update(forsServiceTimingMetrics,
                    Duration.ofMillis(watch.getElapsedTime()),
                    methodTag);
        }
    }

    /**
     * Calls the forsrights service with an IP address.
     *
     * @param globalActionState global state object
     * @param ipAddress IP-address from the caller of this web service.
     * @return A response from forsrights.
     */
    public ForsRights.RightSet forsRightsWithIp(GlobalActionState globalActionState, String ipAddress) throws ForsRightsException {
        final StopWatch watch = new Log4JStopWatch("service.forsrights.rightsWithIp");
        final Tag methodTag = new Tag(METHOD_NAME_KEY, "forsRightsWithIp");
        try {
            LOGGER.info("Authenticating user {}/{} with ip-address {} against forsright at {}", globalActionState.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId(), globalActionState.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), ipAddress, settings.getProperty(JNDIResources.FORSRIGHTS_URL));
            return forsRights.lookupRight(globalActionState.getUpdateServiceRequestDTO().getAuthenticationDTO().getUserId(), globalActionState.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), globalActionState.getUpdateServiceRequestDTO().getAuthenticationDTO().getPassword(), ipAddress);
        } catch (Exception e) {
            metricsHandlerBean.increment(forsServiceErrorCounterMetrics,
                    methodTag,
                    new Tag(ERROR_TYPE, e.getMessage().toLowerCase()));
            throw e;
        } finally {
            watch.stop();
            metricsHandlerBean.update(forsServiceTimingMetrics,
                     Duration.ofMillis(watch.getElapsedTime()), methodTag);
        }
    }
}
