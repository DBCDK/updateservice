package dk.dbc.updateservice.actions;

import dk.dbc.updateservice.dto.UpdateStatusEnumDto;
import dk.dbc.updateservice.update.UpdateException;
import org.apache.commons.lang3.StringUtils;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.MDC;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Engine to execute a single ServiceAction including all of its children.
 * </p>
 */
public class ServiceEngine {
    private static final XLogger logger = XLoggerFactory.getXLogger(ServiceEngine.class);

    private Map<String, String> loggerKeys = new HashMap<>();

    public ServiceEngine() {
    }

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
     * If multiple ServiceAction's has been called then the results are concated
     * together to a single result.
     *
     * @param action ServiceAction to execute.
     * @return A concated list of ValidationError that is returned by all called
     * actions.
     * @throws UpdateException Throwed in case of an error.
     */
    public ServiceResult executeAction(ServiceAction action) throws UpdateException {
        logger.entry();
        try {
            if (action == null) {
                String message = String.format("%s.executeAction can not be called with (null)", getClass().getName());
                throw new IllegalArgumentException(message);
            }
            MDC.setContextMap(loggerKeys);
            action.setupMDCContext();

            printActionHeader(action);

            StopWatch watch = new Log4JStopWatch();
            ServiceResult serviceResult = action.performAction();
            watch.stop("action." + action.name());
            action.setTimeElapsed(watch.getElapsedTime());
            action.setServiceResult(serviceResult);

            logger.info("");
            if (stopExecution(serviceResult)) {
                logger.error("Action failed before sub actions: {}", serviceResult);
                return serviceResult;
            } else {
                logger.info("Action success before sub actions: {}", serviceResult);
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
            throw new UpdateException(ex.getMessage(), ex);
        } finally {
            MDC.setContextMap(loggerKeys);
            logger.exit();
        }
    }

    /**
     * Checks if <code>list</code> contains a ValidationError with type
     * <code>ERROR</code>
     */
    private boolean stopExecution(ServiceResult actionResult) {
        logger.entry();
        try {
            if (actionResult == null) {
                throw new IllegalArgumentException("actionResult must not be (null)");
            }
            if (actionResult.getServiceErrorList() != null) {
                return true;
            }
            if (actionResult.getStatus() == UpdateStatusEnumDto.OK) {
                return false;
            }
            return true;
        } finally {
            logger.exit();
        }
    }

    public void printActionHeader(ServiceAction action) {
        String line = StringUtils.repeat("=", 50);
        logger.info("");
        logger.info("Action: {}", action.name());
        logger.info(line);
    }

    public void printActions(ServiceAction action) {
        printActions(action, "");
    }

    private void printActions(ServiceAction action, String indent) {
        logger.entry();
        try {
            logger.info(indent + action.name() + " in " + action.getTimeElapsed() + " ms: " + action.getServiceResult());
            List<ServiceAction> children = action.children();
            if (children != null) {
                for (ServiceAction child : children) {
                    printActions(child, indent + "  ");
                }
            }
        } finally {
            logger.exit();
        }
    }
}
