/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.jsonb.JSONBException;
import dk.dbc.opencat.connector.OpencatBusinessConnector;
import dk.dbc.opencat.connector.OpencatBusinessConnectorException;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.MDC;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.xml.bind.JAXBException;
import java.io.UnsupportedEncodingException;
import java.util.Properties;

import static dk.dbc.updateservice.utils.MDCUtil.MDC_TRACKING_ID_LOG_CONTEXT;

public class RecordSorter {
    private static final XLogger logger = XLoggerFactory.getXLogger(RecordSorter.class);

    private OpencatBusinessConnector opencatBusinessConnector;
    private String schemaName;

    // Default constructor used for mocking
    protected RecordSorter() {
    }

    public RecordSorter(OpencatBusinessConnector opencatBusinessConnector, String schemaName) {
        this.opencatBusinessConnector = opencatBusinessConnector;
        this.schemaName = schemaName;
    }

    public MarcRecord sortRecord(MarcRecord record, Properties properties) throws UpdateException {
        logger.entry();
        final StopWatch watch = new Log4JStopWatch("opencatBusiness.sortRecord");

        try {
            final String trackingId = MDC.get(MDC_TRACKING_ID_LOG_CONTEXT);

            return opencatBusinessConnector.sortRecord(schemaName, record, trackingId);
        } catch (OpencatBusinessConnectorException | JSONBException | JAXBException | UnsupportedEncodingException ex) {
            logger.error("Error when trying to sort the record. ", ex);
            return record;
        } finally {
            watch.stop();
            logger.exit();
        }
    }
}
