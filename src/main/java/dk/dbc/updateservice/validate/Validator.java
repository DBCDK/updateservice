package dk.dbc.updateservice.validate;

import dk.dbc.commons.jsonb.JSONBException;
import dk.dbc.opencat.connector.OpencatBusinessConnector;
import dk.dbc.opencat.connector.OpencatBusinessConnectorException;
import dk.dbc.updateservice.dto.SchemaDTO;
import dk.dbc.updateservice.update.UpdateException;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.MDC;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static dk.dbc.updateservice.rest.ApplicationConfig.LOG_DURATION_THRESHOLD_MS;
import static dk.dbc.updateservice.utils.MDCUtil.MDC_TRACKING_ID_LOG_CONTEXT;

@Stateless
public class Validator {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(Validator.class);

    @Inject
    private OpencatBusinessConnector opencatBusinessConnector;

    public List<SchemaDTO> getValidateSchemas(String templateGroup, Set<String> allowedLibraryRules) throws UpdateException {
        final StopWatch watch = new Log4JStopWatch("opencatBusiness.getValidateSchemas").setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        try {
            final String trackingId = MDC.get(MDC_TRACKING_ID_LOG_CONTEXT);
            final List<SchemaDTO> names = opencatBusinessConnector.getValidateSchemas(templateGroup, allowedLibraryRules, trackingId);
            final List<SchemaDTO> result = new ArrayList<>(names);
            LOGGER.trace("Number of templates: {}", result.size());
            return result;
        } catch (OpencatBusinessConnectorException | JSONBException ex) {
            throw new UpdateException("Error when executing OpencatBusinessConnector function: getValidateSchemas", ex);
        } finally {
            watch.stop();
        }
    }
}
