/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.commons.metricshandler.CounterMetric;
import dk.dbc.commons.metricshandler.MetricsHandlerBean;
import dk.dbc.commons.metricshandler.SimpleTimerMetric;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.utils.DeferredLogger;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.MDC;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dk.dbc.updateservice.rest.ApplicationConfig.LOG_DURATION_THRESHOLD_MS;

/**
 * Engine to execute a single ServiceAction including all of its children.
 * </p>
 */
public class ServiceEngine {
    private static final DeferredLogger LOGGER = new DeferredLogger(ServiceEngine.class);

    private Map<String, String> loggerKeys = new HashMap<>();
    MetricsHandlerBean metricsHandlerBean;

    public ServiceEngine(MetricsHandlerBean metricsHandlerBean) {
        this.metricsHandlerBean = metricsHandlerBean;
    }


    private static class ServiceEngineErrorCounterMetrics implements CounterMetric {
        private final Metadata metadata;

        ServiceEngineErrorCounterMetrics(Metadata metadata) {
            this.metadata = validateMetadata(metadata);
        }

        @Override
        public Metadata getMetadata() {
            return metadata;
        }
    }

    private static class ServiceEngineTimingMetrics implements SimpleTimerMetric {
        private final Metadata metadata;

        ServiceEngineTimingMetrics(Metadata metadata) {
            this.metadata = validateMetadata(metadata);
        }

        @Override
        public Metadata getMetadata() {
            return metadata;
        }
    }

    static final String METHOD_NAME_KEY = "method";
    static final String ERROR_TYPE = "errortype";

    static final ServiceEngineErrorCounterMetrics serviceEngineErrorCounterMetrics =
            new ServiceEngineErrorCounterMetrics(Metadata.builder()
                    .withName("update_actions_error_counter")
                    .withDescription("Number of errors caught in actions")
                    .withType(MetricType.COUNTER)
                    .withUnit("actions").build());

    static final ServiceEngineTimingMetrics serviceEngineTimingMetrics =
            new ServiceEngineTimingMetrics(Metadata.builder()
                    .withName("update_actions_timer")
                    .withDescription("Duration of various actions")
                    .withType(MetricType.SIMPLE_TIMER)
                    .withUnit(MetricUnits.MILLISECONDS).build());


    public void setLoggerKeys(Map<String, String> loggerKeys) {
        this.loggerKeys = loggerKeys;
    }

    /**
     * Executes an action including any child actions that the <code>action</code>
     * may create.
     * <p/>
     * The execution stops if an action returns a ValidationError of type
     * <code>ERROR</code>.
     * <p/>
     * If multiple ServiceAction's has been called then the results are concatenated
     * together to a single result.
     *
     * @param action ServiceAction to execute.
     * @return A concatenated list of ValidationError that is returned by all called
     * actions.
     * @throws UpdateException thrown in case of an error.
     */
    public ServiceResult executeAction(ServiceAction action) throws UpdateException {
        StopWatch watch = new Log4JStopWatch().setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        final Tag methodTag;
        if (action != null && action.name() != null) {
            methodTag = new Tag(METHOD_NAME_KEY, action.name());
        } else {
            methodTag = new Tag(METHOD_NAME_KEY, "unknown");
        }

        try {
            if (action == null) {
                String message = String.format("%s.executeAction can not be called with (null)", getClass().getName());
                throw new IllegalArgumentException(message);
            }
            MDC.setContextMap(loggerKeys);
            action.setupMDCContext();

            printActionHeader(action);

            ServiceResult serviceResult = action.performAction();
            watch.stop("action." + action.name());
            action.setTimeElapsed(watch.getElapsedTime());
            action.setServiceResult(serviceResult);

            if (stopExecution(serviceResult)) {
                LOGGER.use(log -> log.info("Action failed before sub actions: {}", serviceResult));
                return serviceResult;
            } else {
                LOGGER.use(log -> log.info("Action success before sub actions: {}", serviceResult));
            }
            List<ServiceAction> children = action.children();
            if (children != null) {
                for (ServiceAction child : children) {
                    ServiceResult childResult = executeAction(child);
                    serviceResult.addServiceResult(childResult);
                    if (stopExecution(childResult)) {
                        serviceResult.setStatus(childResult.getStatus());
                        return serviceResult;
                    }
                }
            }
            return serviceResult;
        } catch (IllegalStateException ex) {
            metricsHandlerBean.increment(serviceEngineErrorCounterMetrics,
                    methodTag,
                    new Tag(ERROR_TYPE, ex.getMessage().toLowerCase()));

            throw new UpdateException(ex.getMessage(), ex);
        } finally {
            MDC.setContextMap(loggerKeys);
            metricsHandlerBean.update(serviceEngineTimingMetrics,
                    Duration.ofMillis(watch.getElapsedTime()),
                    methodTag);
        }
    }

    /**
     * Checks if <code>list</code> contains a ValidationError with type
     * <code>ERROR</code>
     */
    private boolean stopExecution(ServiceResult actionResult) {
        if (actionResult == null) {
            throw new IllegalArgumentException("actionResult must not be (null)");
        }
        if (actionResult.getServiceErrorList() != null) {
            return true;
        }

        return actionResult.getStatus() != UpdateStatusEnumDTO.OK;
    }

    public void printActionHeader(ServiceAction action) {
        LOGGER.use(log -> {
            if (log.isInfoEnabled()) {
                final String line = StringUtils.repeat("=", 50);
                log.info("");
                log.info("Action: {}", action.name());
                log.info(line);
            }
        });
    }

    public void printActions(ServiceAction action) {
        printActions(action, "");
    }

    private void printActions(ServiceAction action, String indent) {
        LOGGER.use(log -> log.info("{}{} in {} ms: {}", indent, action.name(), action.getTimeElapsed(), action.getServiceResult()));
        List<ServiceAction> children = action.children();
        if (children != null) {
            for (ServiceAction child : children) {
                printActions(child, indent + "  ");
            }
        }
    }
}
