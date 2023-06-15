package dk.dbc.updateservice.update;

import dk.dbc.commons.jsonb.JSONBException;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.marc.reader.MarcReaderException;
import dk.dbc.opencat.connector.OpencatBusinessConnector;
import dk.dbc.opencat.connector.OpencatBusinessConnectorException;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.MDC;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import static dk.dbc.updateservice.rest.ApplicationConfig.LOG_DURATION_THRESHOLD_MS;
import static dk.dbc.updateservice.utils.MDCUtil.MDC_TRACKING_ID_LOG_CONTEXT;

public class RecordSorter {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(RecordSorter.class);

    private OpencatBusinessConnector opencatBusinessConnector;
    private String schemaName;

    // Default constructor used for mocking
    protected RecordSorter() {
    }

    public RecordSorter(OpencatBusinessConnector opencatBusinessConnector, String schemaName) {
        this.opencatBusinessConnector = opencatBusinessConnector;
        this.schemaName = schemaName;
    }

    public MarcRecord sortRecord(MarcRecord marcRecord) {
        final StopWatch watch = new Log4JStopWatch("opencatBusiness.sortRecord").setTimeThreshold(LOG_DURATION_THRESHOLD_MS);

        try {
            final String trackingId = MDC.get(MDC_TRACKING_ID_LOG_CONTEXT);
            // There no template called superallowall so we can't use it to sort with, so just return the same record
            if ("superallowall".equals(schemaName)) {
                return marcRecord;
            } else {
                return opencatBusinessConnector.sortRecord(schemaName, marcRecord, trackingId);
            }
        } catch (OpencatBusinessConnectorException | JSONBException | MarcReaderException ex) {
            LOGGER.error("Error when trying to sort the record. ", ex);
            return marcRecord;
        } finally {
            watch.stop();
        }
    }
}
