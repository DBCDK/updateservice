/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.commons.metricshandler.CounterMetric;
import dk.dbc.commons.metricshandler.MetricsHandlerBean;
import dk.dbc.commons.metricshandler.SimpleTimerMetric;
import dk.dbc.holdingsitems.HoldingsItemsDAO;
import dk.dbc.holdingsitems.HoldingsItemsException;
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

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * EJB to provide access to the HoldingsItems database.
 */
@Stateless
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class HoldingsItems {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(HoldingsItems.class);

    @Inject
    MetricsHandlerBean metricsHandlerBean;

    @Resource(lookup = "jdbc/holdingsitems")
    private final DataSource dataSource;

    private static final class HoldingsItemsErrorCounterMetrics implements CounterMetric {
        private final Metadata metadata;

        public HoldingsItemsErrorCounterMetrics(Metadata metadata) {
            this.metadata = validateMetadata(metadata);
        }

        @Override
        public Metadata getMetadata() {
            return metadata;
        }
    }


    private static final class HoldingsItemsTimingMetrics implements SimpleTimerMetric {
        private final Metadata metadata;

        public HoldingsItemsTimingMetrics(Metadata metadata) {
            this.metadata = validateMetadata(metadata);
        }

        @Override
        public Metadata getMetadata() {
            return metadata;
        }
    }

    protected static final HoldingsItemsErrorCounterMetrics holdingsItemsErrorCounterMetrics =
            new HoldingsItemsErrorCounterMetrics(Metadata.builder()
                    .withName("update_holdingsitems_error_counter")
                    .withDescription("Number of errors caught in various holdingsitems calls")
                    .withType(MetricType.COUNTER)
                    .withUnit("requests").build());

    static final HoldingsItemsTimingMetrics holdingsItemsTimingMetrics =
            new HoldingsItemsTimingMetrics(Metadata.builder()
                    .withName("update_holdingsitems_timer")
                    .withDescription("Duration of various various holdingsitems calls")
                    .withUnit(MetricUnits.MILLISECONDS)
                    .withType(MetricType.SIMPLE_TIMER).build());

    protected static final String METHOD_NAME_KEY = "method";
    protected static final String ERROR_TYPE = "errortype";

    public HoldingsItems() {
        this.dataSource = null;
    }

    public HoldingsItems(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Set<Integer> getAgenciesThatHasHoldingsFor(MarcRecord marcRecord) throws UpdateException {
        LOGGER.entry(marcRecord);
        StopWatch watch = new Log4JStopWatch();
        Set<Integer> result = new HashSet<>();
        try {
            result.addAll(getAgenciesThatHasHoldingsForId(new MarcRecordReader(marcRecord).getRecordId()));
            MarcRecordReader mm = new MarcRecordReader(marcRecord);
            List<String> aliasIds = mm.getCentralAliasIds();
            for (String s : aliasIds) {
                result.addAll(getAgenciesThatHasHoldingsForId(s));
            }
            return result;
        } finally {
            watch.stop("holdingsItems.getAgenciesThatHasHoldingsForId.MarcRecord");
            LOGGER.exit(result);
        }
    }

    public Set<Integer> getAgenciesThatHasHoldingsForId(String recordId) throws UpdateException {
        Tag methodTag = new Tag(METHOD_NAME_KEY, "getAgenciesThatHasHoldingsForId");
        LOGGER.entry(recordId);
        LOGGER.info("getAgenciesThatHasHoldingsForId: {}", recordId);
        StopWatch watch = new Log4JStopWatch();
        Set<Integer> result = new HashSet<>();
        try (Connection conn = dataSource.getConnection()) {
            HoldingsItemsDAO dao = createDAO(conn);
            result = dao.getAgenciesThatHasHoldingsFor(recordId);
            return result;
        } catch (SQLException | HoldingsItemsException ex) {
            LOGGER.error(ex.getMessage(), ex);
            metricsHandlerBean.increment(holdingsItemsErrorCounterMetrics,
                    methodTag,
                    new Tag(ERROR_TYPE, ex.getMessage().toLowerCase()));
            throw new UpdateException(ex.getMessage(), ex);
        } catch (Exception e) {
            metricsHandlerBean.increment(holdingsItemsErrorCounterMetrics,
                    methodTag,
                    new Tag(ERROR_TYPE, e.getMessage().toLowerCase()));
            throw e;
        } finally {
            watch.stop("holdingsItems.getAgenciesThatHasHoldingsForId.String");
            metricsHandlerBean.update(holdingsItemsTimingMetrics,
                Duration.ofMillis(watch.getElapsedTime()),
                methodTag);

            LOGGER.exit(result);
        }
    }

    protected HoldingsItemsDAO createDAO(Connection conn) throws HoldingsItemsException {
        StopWatch watch = new Log4JStopWatch();
        try {
            return HoldingsItemsDAO.newInstance(conn);
        } finally {
            watch.stop("holdingsItems.createDAO");
        }
    }
}
