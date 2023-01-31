/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcXchangeFactory;
import dk.dbc.common.records.marcxchange.CollectionType;
import dk.dbc.common.records.marcxchange.ObjectFactory;
import dk.dbc.common.records.marcxchange.RecordType;
import dk.dbc.common.records.utils.RecordContentTransformer;
import dk.dbc.commons.metricshandler.CounterMetric;
import dk.dbc.commons.metricshandler.MetricsHandlerBean;
import dk.dbc.commons.metricshandler.SimpleTimerMetric;
import dk.dbc.marcrecord.ExpandCommonMarcRecord;
import dk.dbc.marcxmerge.FieldRules;
import dk.dbc.marcxmerge.MarcXMerger;
import dk.dbc.marcxmerge.MarcXMergerException;
import dk.dbc.rawrepo.RawRepoDAO;
import dk.dbc.rawrepo.RawRepoException;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.rawrepo.RelationHintsVipCore;
import dk.dbc.vipcore.libraryrules.VipCoreLibraryRulesConnector;
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
import javax.inject.Inject;
import javax.sql.DataSource;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dk.dbc.updateservice.rest.ApplicationConfig.LOG_DURATION_THRESHOLD_MS;

/**
 * EJB to provide access to the RawRepo database.
 */
@Stateless
public class RawRepo {

    @Inject
    MetricsHandlerBean metricsHandler;

    @Inject
    private VipCoreLibraryRulesConnector vipCoreLibraryRulesConnector;

    private static class RawrepoErrorCounterMetrics implements CounterMetric {
        private final Metadata metadata;

        RawrepoErrorCounterMetrics(Metadata metadata) {
            this.metadata = validateMetadata(metadata);
        }

        @Override
        public Metadata getMetadata() {
            return metadata;
        }
    }

    private static class RawrepoDaoTimingMetrics implements SimpleTimerMetric {
        private final Metadata metadata;

        public RawrepoDaoTimingMetrics(Metadata metadata) {
            this.metadata = validateMetadata(metadata);
        }

        @Override
        public Metadata getMetadata() {
            return metadata;
        }
    }

    protected static final String METHOD_NAME_KEY = "method";
    protected static final String ERROR_TYPE = "errortype";

    static final RawrepoDaoTimingMetrics rawrepoDaoTimingMetrics =
            new RawrepoDaoTimingMetrics(Metadata.builder()
                    .withName("update_rawrepodao_timer")
                    .withDescription("Duration of various rawrepodao calls")
                    .withType(MetricType.SIMPLE_TIMER)
                    .withUnit(MetricUnits.MILLISECONDS).build());

    static final RawrepoErrorCounterMetrics rawrepoErrorCounterMetrics = new RawrepoErrorCounterMetrics(Metadata.builder()
            .withName("update_rawrepodao_error_counter")
            .withDescription("Number of errors caught in rawrepodao calls")
            .withType(MetricType.COUNTER)
            .withUnit("requests").build());


    private static final XLogger LOGGER = XLoggerFactory.getXLogger(RawRepo.class);
    public static final int COMMON_AGENCY = 870970;
    public static final int ARTICLE_AGENCY = 870971;
    public static final int LITTOLK_AGENCY = 870974;
    public static final int HOSTPUB_AGENCY = 870975;
    public static final int MATVURD_AGENCY = 870976;
    public static final int RETRO_AGENCY = 870978;
    public static final int AUTHORITY_AGENCY = 870979;
    public static final List<Integer> SIMPLE_AGENCIES = Collections.singletonList(190007);
    public static final List<String> DBC_PRIVATE_AGENCY_LIST = Arrays.asList("870971", "870974", "870975", "870976", "870978", "870979", "190002", "190004", "190007", "190008");
    public static final List<String> DBC_AGENCY_LIST = Stream.concat(Stream.of("870970"), DBC_PRIVATE_AGENCY_LIST.stream()).collect(Collectors.toList());
    public static final List<String> DBC_AGENCY_ALL = Stream.concat(Stream.of("191919"), DBC_AGENCY_LIST.stream()).collect(Collectors.toList());
    public static final int DBC_ENRICHMENT = 191919;
    public static final int SCHOOL_COMMON_AGENCY = 300000;
    public static final int MIN_SCHOOL_AGENCY = SCHOOL_COMMON_AGENCY + 1;
    public static final int MAX_SCHOOL_AGENCY = SCHOOL_COMMON_AGENCY + 99999;
    public static final List<String> AUTHORITY_FIELDS = ExpandCommonMarcRecord.AUTHORITY_FIELD_LIST;
    public static final List<String> MATVURD_FIELDS = Arrays.asList("r01", "r02");

    public static final int ENQUEUE_PRIORITY_DEFAULT = 500;

    @Resource(lookup = "jdbc/rawrepo")
    private DataSource dataSource;

    public RawRepo() {
        this.dataSource = null;
    }

    public RawRepo(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Returns a Set of local agencies for a record.
     * <p/>
     * The agency for common records is not returned in the set nor is deleted records
     *
     * @param bibliographicRecordId The bibliographic record id to lookup local agencies for.
     * @return A Set of agency ids for the local agencies.
     * @throws UpdateException In case of an error from RawRepo or an SQL exception.
     */
    public Set<Integer> agenciesForRecordNotDeleted(String bibliographicRecordId) throws UpdateException {
        final StopWatch watch = new Log4JStopWatch().setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        final Set<Integer> activeAgencies = new HashSet<>();
        try {
            if (bibliographicRecordId == null) {
                throw new IllegalArgumentException("record can not be null");
            }
            final Set<Integer> allAgencies = agenciesForRecord(bibliographicRecordId);

            if (allAgencies != null) {
                for (Integer agencyId : allAgencies) {
                    if (recordExists(bibliographicRecordId, agencyId)) {
                        activeAgencies.add(agencyId);
                    }
                }
            }

            return activeAgencies;
        } finally {
            watch.stop("rawrepo.agenciesForRecordNotDeleted");
        }
    }

    /**
     * Returns a Set of local agencies for an record id.
     * <p/>
     * The agency for common records is not returned in the set.
     *
     * @param recordId The record id to lookup local agencies for.
     * @return A Set of agency ids for the local agencies.
     * @throws UpdateException In case of an error from RawRepo or an SQL exception.
     */
    public Set<Integer> agenciesForRecord(String recordId) throws UpdateException {
        final StopWatch watch = new Log4JStopWatch().setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        Set<Integer> result;
        try {
            result = agenciesForRecordAll(recordId);
            result.remove(COMMON_AGENCY);
            result.remove(ARTICLE_AGENCY);

            return result;
        } finally {
            watch.stop("rawrepo.agenciesForRecord.String");
        }
    }

    public Set<Integer> agenciesForRecordAll(String recordId) throws UpdateException {
        final StopWatch watch = new Log4JStopWatch().setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        Set<Integer> result;
        final String methodName = "allAgenciesForBibliographicRecordId";

        try {
            if (recordId == null) {
                throw new IllegalArgumentException("recordId can not be null");
            }
            if (recordId.isEmpty()) {
                throw new IllegalArgumentException("recordId can not be empty");
            }
            try (Connection conn = dataSource.getConnection()) {
                try {
                    RawRepoDAO dao = createDAO(conn);
                    result = dao.allAgenciesForBibliographicRecordId(recordId);
                    result.remove(DBC_ENRICHMENT);
                    return result;
                } catch (RawRepoException ex) {
                    conn.rollback();
                    LOGGER.error(ex.getMessage(), ex);
                    throw new UpdateException(ex.getMessage(), ex);
                }
            } catch (SQLException e) {
                LOGGER.error(e.getMessage(), e);
                throw new UpdateException(e.getMessage(), e);
            }
        } catch (Exception e) {
            incrementErrorCounterMetric(methodName, e);
            throw e;
        } finally {
            watch.stop("rawrepo.agenciesForRecordAll.String");
            updateSimpleTimerMetric(methodName, watch);
        }
    }

    public Set<RecordId> children(RecordId recordId) throws UpdateException {
        final StopWatch watch = new Log4JStopWatch().setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        final String methodName = "getRelationsChildren";

        try {
            if (recordId == null) {
                throw new IllegalArgumentException("recordId can not be null");
            }

            try (Connection conn = dataSource.getConnection()) {
                try {
                    final RawRepoDAO dao = createDAO(conn);
                    return dao.getRelationsChildren(recordId);
                } catch (RawRepoException ex) {
                    conn.rollback();
                    LOGGER.error(ex.getMessage(), ex);
                    throw new UpdateException(ex.getMessage(), ex);
                }
            } catch (SQLException ex) {
                LOGGER.error(ex.getMessage(), ex);
                throw new UpdateException(ex.getMessage(), ex);
            }
        } catch (Exception e) {
            incrementErrorCounterMetric(methodName, e);
            throw e;
        } finally {
            watch.stop("rawrepo.children.RecordId");
            updateSimpleTimerMetric(methodName, watch);
        }
    }

    public Set<RecordId> parents(RecordId recordId) throws UpdateException {
        final StopWatch watch = new Log4JStopWatch().setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        final String methodName = "parents";

        try {
            if (recordId == null) {
                throw new IllegalArgumentException("recordId can not be null");
            }

            try (Connection conn = dataSource.getConnection()) {
                try {
                    final RawRepoDAO dao = createDAO(conn);
                    return dao.getRelationsParents(recordId);
                } catch (RawRepoException ex) {
                    conn.rollback();
                    LOGGER.error(ex.getMessage(), ex);
                    throw new UpdateException(ex.getMessage(), ex);
                }
            } catch (SQLException ex) {
                LOGGER.error(ex.getMessage(), ex);
                throw new UpdateException(ex.getMessage(), ex);
            }
        } catch (Exception e) {
            incrementErrorCounterMetric(methodName, e);
            throw e;
        } finally {
            watch.stop("rawrepo.parents");
            updateSimpleTimerMetric(methodName, watch);
        }
    }

    public Set<RecordId> enrichments(RecordId recordId) throws UpdateException {
        final StopWatch watch = new Log4JStopWatch().setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        final String methodName = "getRelationsSiblingsToMe";

        try {
            if (recordId == null) {
                throw new IllegalArgumentException("recordId can not be null");
            }

            try (Connection conn = dataSource.getConnection()) {
                try {
                    final RawRepoDAO dao = createDAO(conn);

                    return dao.getRelationsSiblingsToMe(recordId);
                } catch (RawRepoException ex) {
                    conn.rollback();
                    LOGGER.error(ex.getMessage(), ex);
                    throw new UpdateException(ex.getMessage(), ex);
                }
            } catch (SQLException ex) {
                LOGGER.error(ex.getMessage(), ex);
                throw new UpdateException(ex.getMessage(), ex);
            }
        } catch (Exception e) {
            incrementErrorCounterMetric(methodName, e);
            throw e;
        } finally {
            watch.stop("rawrepo.enrichments.RecordId");
            updateSimpleTimerMetric(methodName, watch);
        }
    }

    /**
     * Fetches the RawRepo record for a MarcRecord.
     * <p/>
     * If the record does not exist in the RawRepo then it will be created.
     *
     * @param bibliographicRecordId    String
     * @param agencyId int
     * @return The RawRepo record.
     * @throws UpdateException In case of an error from RawRepo or an SQL exception.
     */
    public Record fetchRecord(String bibliographicRecordId, int agencyId) throws UpdateException {
        final StopWatch watch = new Log4JStopWatch().setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        Record result = null;
        final String methodName = "fetchRecord";

        try {
            if (bibliographicRecordId == null) {
                throw new IllegalArgumentException("bibliographicRecordId can not be null");
            }
            try (Connection conn = dataSource.getConnection()) {
                try {
                    final RawRepoDAO dao = createDAO(conn);

                    result = dao.fetchRecord(bibliographicRecordId, agencyId);
                    return result;
                } catch (RawRepoException ex) {
                    conn.rollback();
                    LOGGER.error(ex.getMessage(), ex);
                    throw new UpdateException(ex.getMessage(), ex);
                }
            } catch (SQLException ex) {
                LOGGER.error(ex.getMessage(), ex);
                throw new UpdateException(ex.getMessage(), ex);
            }
        } catch (Exception e) {
            incrementErrorCounterMetric(methodName, e);
            throw e;
        } finally {
            watch.stop("rawrepo.fetchRecord");
            updateSimpleTimerMetric(methodName, watch);
        }
    }

    public Record fetchMergedRecord(String bibliographicRecordId, int agencyId) throws UpdateException {
        final StopWatch watch = new Log4JStopWatch().setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        final String methodName = "fetchMergedRecord";

        try {
            if (bibliographicRecordId == null) {
                throw new IllegalArgumentException("bibliographicRecordId can not be null");
            }
            try (Connection conn = dataSource.getConnection()) {
                try {
                    final RawRepoDAO dao = createDAO(conn);
                    final MarcXMerger merger = new MarcXMerger();
                    return dao.fetchMergedRecord(bibliographicRecordId, agencyId, merger, false);
                } catch (RawRepoException | MarcXMergerException ex) {
                    conn.rollback();
                    LOGGER.error(ex.getMessage(), ex);
                    throw new UpdateException(ex.getMessage(), ex);
                }
            } catch (SQLException ex) {
                LOGGER.error(ex.getMessage(), ex);
                throw new UpdateException(ex.getMessage(), ex);
            }
        } catch (Exception e) {
            incrementErrorCounterMetric(methodName, e);
            throw e;
        } finally {
            watch.stop("rawrepo.fetchMergedRecord");
            updateSimpleTimerMetric(methodName, watch);
        }
    }

    public Map<String, MarcRecord> fetchRecordCollection(String bibliographicRecordId, int agencyId) throws UpdateException {
        final StopWatch watch = new Log4JStopWatch().setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        final String methodName = "fetchRecordCollection";
        Map<String, MarcRecord> result = null;
        Map<String, Record> recordMap;
        try (Connection conn = dataSource.getConnection()) {
            if (bibliographicRecordId == null) {
                throw new IllegalArgumentException("bibliographicRecordId can not be null");
            }
            try {
                final RawRepoDAO dao = createDAO(conn);
                final MarcXMerger merger = new MarcXMerger();
                recordMap = dao.fetchRecordCollection(bibliographicRecordId, agencyId, merger);
                if (!recordMap.isEmpty()) {
                    result = new HashMap<>();
                    for (Map.Entry<String, Record> entry : recordMap.entrySet()) {
                        final Record record = entry.getValue();
                        result.put(entry.getKey(), RecordContentTransformer.decodeRecord(record.getContent()));
                    }
                }
                return result;
            } catch (RawRepoException | MarcXMergerException ex) {
                conn.rollback();
                LOGGER.error(ex.getMessage(), ex);
                throw new UpdateException(ex.getMessage(), ex);
            }
        } catch (SQLException ex) {
            LOGGER.error(ex.getMessage(), ex);
            throw new UpdateException(ex.getMessage(), ex);
        } catch (Exception e) {
            incrementErrorCounterMetric(methodName, e);
            throw e;
        } finally {
            watch.stop("rawrepo.fetchRecordCollection");
            updateSimpleTimerMetric(methodName, watch);
        }
    }

    /**
     * The function is used to fetch a merged DBC record. The returned record will have the agencyid of the parent
     * record and not of the input record
     *
     * @param bibliographicRecordId The record id for the record to check for.
     * @param agencyId              The agency id for the record to check for.
     * @return A merged DBC record with letter-fields
     * @throws UpdateException In case of an error from RawRepo or an SQL exception.
     */
    public Record fetchMergedDBCRecord(String bibliographicRecordId, int agencyId) throws UpdateException {
        final StopWatch watch = new Log4JStopWatch().setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        final String methodName = "fetchMergedRecord";
        Record result = null;
        try {
            if (bibliographicRecordId == null) {
                throw new IllegalArgumentException("bibliographicRecordId can not be null");
            }
            try (Connection conn = dataSource.getConnection()) {
                try {
                    final RawRepoDAO dao = createDAO(conn);

                    final String immutable = "001;010;020;990;991;996";
                    final String overwrite = "004;005;013;014;017;035;036;240;243;247;300;008 009 038 039 100 110 239 245 652 654";

                    final FieldRules customFieldRules = new FieldRules(immutable, overwrite, FieldRules.INVALID_DEFAULT, FieldRules.VALID_REGEX_DANMARC2);
                    final MarcXMerger merger = new MarcXMerger(customFieldRules, "USE_PARENT_AGENCY");

                    result = dao.fetchMergedRecord(bibliographicRecordId, agencyId, merger, true);
                    return result;
                } catch (RawRepoException | MarcXMergerException ex) {
                    conn.rollback();
                    LOGGER.error(ex.getMessage(), ex);
                    throw new UpdateException(ex.getMessage(), ex);
                }
            } catch (SQLException ex) {
                LOGGER.error(ex.getMessage(), ex);
                throw new UpdateException(ex.getMessage(), ex);
            }
        } catch (Exception e) {
            incrementErrorCounterMetric(methodName, e);
            throw e;
        } finally {
            watch.stop("rawrepo.fetchRecord");
            updateSimpleTimerMetric(methodName, watch);
        }
    }

    /**
     * Checks if a record exists in RawRepo.
     *
     * @param recordId The record id for the record to check for.
     * @param agencyId The agency id for the record to check for.
     * @return <code>true</code> if the record exists, <code>false</code> otherwise.
     * @throws UpdateException In case of an error from RawRepo or an SQL exception.
     */
    public boolean recordExists(String recordId, int agencyId) throws UpdateException {
        LOGGER.info("RawRepo.recordExists, input, recordId={}, agencyId={}", recordId, agencyId);
        final StopWatch watch = new Log4JStopWatch().setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        final String methodName = "recordExists";
        boolean result = false;

        try (Connection conn = dataSource.getConnection()) {
            if (conn == null) {
                LOGGER.info("RawRepo.recordExists, conn == NULL");
            }
            try {
                final RawRepoDAO dao = createDAO(conn);
                if (dao == null) {
                    LOGGER.info("RawRepo.recordExists, dao == NULL");
                }
                result = dao.recordExists(recordId, agencyId);
                return result;
            } catch (RawRepoException e) {
                if (conn != null) {
                    conn.rollback();
                }
                LOGGER.error(e.getMessage(), e);
                throw new UpdateException(e.getMessage(), e);
            }
        } catch (SQLException ex) {
            LOGGER.error(ex.getMessage(), ex);
            throw new UpdateException(ex.getMessage(), ex);
        } catch (Exception e) {
            incrementErrorCounterMetric(methodName, e);
            throw e;
        } finally {
            watch.stop("rawrepo.recordExists");
            updateSimpleTimerMetric(methodName, watch);
        }
    }

    /**
     * Checks if a maybe deleted record exists in RawRepo.
     *
     * @param recordId The record id for the record to check for.
     * @param agencyId The agency id for the record to check for.
     * @return <code>true</code> if the record exists, <code>false</code> otherwise.
     * @throws UpdateException In case of an error from RawRepo or an SQL exception.
     */
    public boolean recordExistsMaybeDeleted(String recordId, int agencyId) throws UpdateException {
        final StopWatch watch = new Log4JStopWatch().setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        final String methodName = "recordExistsMaybeDeleted";

        try (Connection conn = dataSource.getConnection()) {
            try {
                final RawRepoDAO dao = createDAO(conn);

                return dao.recordExistsMaybeDeleted(recordId, agencyId);
            } catch (RawRepoException ex) {
                conn.rollback();
                LOGGER.error(ex.getMessage(), ex);
                throw new UpdateException(ex.getMessage(), ex);
            }
        } catch (SQLException ex) {
            LOGGER.error(ex.getMessage(), ex);
            throw new UpdateException(ex.getMessage(), ex);
        } catch (Exception e) {
            incrementErrorCounterMetric(methodName, e);
            throw e;
        } finally {
            watch.stop("rawrepo.recordExistsMaybeDeleted");
            updateSimpleTimerMetric(methodName, watch);
        }
    }

    /**
     * Checks if a record exists but is deleted in RawRepo.
     *
     * @param recordId The record id for the record to check for.
     * @param agencyId The agency id for the record to check for.
     * @return <code>true</code> if the record exists, <code>false</code> otherwise.
     * @throws UpdateException In case of an error from RawRepo or an SQL exception.
     */
    public boolean recordDoesNotExistOrIsDeleted(String recordId, int agencyId) throws UpdateException {
        final StopWatch watch = new Log4JStopWatch().setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        final String methodName = "recordExistsMaybeDeleted";

        try (Connection conn = dataSource.getConnection()) {
            try {
                RawRepoDAO dao = createDAO(conn);

                if (dao.recordExistsMaybeDeleted(recordId, agencyId)) {
                    Record record = dao.fetchRecord(recordId, agencyId);

                    return record.isDeleted();
                } else {
                    return true;
                }
            } catch (RawRepoException ex) {
                conn.rollback();
                LOGGER.error(ex.getMessage(), ex);
                throw new UpdateException(ex.getMessage(), ex);
            }
        } catch (SQLException ex) {
            LOGGER.error(ex.getMessage(), ex);
            throw new UpdateException(ex.getMessage(), ex);
        } catch (Exception e) {
            incrementErrorCounterMetric(methodName, e);
            throw e;
        } finally {
            watch.stop("rawrepo.recordExistsMaybeDeleted");
            updateSimpleTimerMetric(methodName, watch);
        }
    }

    public void saveRecord(Record record) throws UpdateException {
        StopWatch watch = new Log4JStopWatch().setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        final String methodName = "saveRecord";
        try (Connection conn = dataSource.getConnection()) {
            try {
                RawRepoDAO dao = createDAO(conn);
                if (record.isDeleted()) {
                    dao.setRelationsFrom(record.getId(), new HashSet<>());
                }
                dao.saveRecord(record);
            } catch (RawRepoException e) {
                conn.rollback();
                LOGGER.error(e.getMessage(), e);
                throw new UpdateException(e.getMessage(), e);
            }
        } catch (SQLException ex) {
            LOGGER.error(ex.getMessage(), ex);
            throw new UpdateException(ex.getMessage(), ex);
        } catch (Exception e) {
            incrementErrorCounterMetric(methodName, e);
            throw e;
        } finally {
            watch.stop("rawrepo.saveRecord.Record");
            updateSimpleTimerMetric(methodName, watch);
        }
    }

    public void removeLinks(RecordId bibliographicRecordId) throws UpdateException {
        StopWatch watch = new Log4JStopWatch().setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        final String methodName = "setRelationsFrom";
        try (Connection conn = dataSource.getConnection()) {
            try {
                RawRepoDAO dao = createDAO(conn);
                final HashSet<RecordId> references = new HashSet<>();
                dao.setRelationsFrom(bibliographicRecordId, references);
            } catch (RawRepoException e) {
                conn.rollback();
                LOGGER.error(e.getMessage(), e);
                throw new UpdateException(e.getMessage(), e);
            }
        } catch (SQLException ex) {
            LOGGER.error(ex.getMessage(), ex);
            throw new UpdateException(ex.getMessage(), ex);
        } catch (Exception e) {
            incrementErrorCounterMetric(methodName, e);
            throw e;
        } finally {
            watch.stop("rawrepo.removeLinks");
            updateSimpleTimerMetric(methodName, watch);
        }
    }

    /**
     * Creates a link between two records in rawrepo.
     *
     * @param id       Id of the record to link from.
     * @param referId Id of the record to link to.
     * @throws UpdateException In case of SQLException or RawRepoException, that exception
     *                         encapsulated in an UpdateException.
     */
    public void linkRecord(RecordId id, RecordId referId) throws UpdateException {
        final StopWatch watch = new Log4JStopWatch().setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        final String methodName = "setRelationsFrom";
        try (Connection conn = dataSource.getConnection()) {
            try {
                final RawRepoDAO dao = createDAO(conn);
                final Set<RecordId> references = new HashSet<>();
                references.add(referId);
                dao.setRelationsFrom(id, references);
            } catch (RawRepoException e) {
                conn.rollback();
                LOGGER.error(e.getMessage(), e);
                throw new UpdateException(e.getMessage(), e);
            }
        } catch (SQLException ex) {
            LOGGER.error(ex.getMessage(), ex);
            throw new UpdateException(ex.getMessage(), ex);
        } catch (Exception e) {
            incrementErrorCounterMetric(methodName, e);
            throw e;
        } finally {
            watch.stop("rawrepo.linkRecord");
            updateSimpleTimerMetric(methodName, watch);
        }
    }

    /**
     * Loads the existing links from the id record and adds referId to that list
     *
     * @param id       Id of the record to link from.
     * @param referId Id of the record to link to.
     * @throws UpdateException In case of SQLException or RawRepoException, that exception
     *                         encapsulated in an UpdateException.
     */
    public void linkRecordAppend(RecordId id, RecordId referId) throws UpdateException {
        final StopWatch watch = new Log4JStopWatch().setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        final String methodName = "linkRecordAppend";
        try (Connection conn = dataSource.getConnection()) {
            try {
                final RawRepoDAO dao = createDAO(conn);
                final Set<RecordId> references = dao.getRelationsFrom(id);
                references.add(referId);
                dao.setRelationsFrom(id, references);
            } catch (RawRepoException e) {
                conn.rollback();
                LOGGER.error(e.getMessage(), e);
                throw new UpdateException(e.getMessage(), e);
            }
        } catch (SQLException ex) {
            LOGGER.error(ex.getMessage(), ex);
            throw new UpdateException(ex.getMessage(), ex);
        } catch (Exception e) {
            incrementErrorCounterMetric(methodName, e);
            throw e;
        } finally {
            watch.stop("rawrepo.linkRecord");
            updateSimpleTimerMetric(methodName, watch);
        }
    }

    public void changedRecord(String provider, RecordId recordId) throws UpdateException {
        changedRecord(provider, recordId, 1000);
    }

    public void changedRecord(String provider, RecordId recordId, int priority) throws UpdateException {
        final StopWatch watch = new Log4JStopWatch().setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        final String methodName = "changedRecord";

        try (Connection conn = dataSource.getConnection()) {
            try {
                final RawRepoDAO dao = createDAO(conn);
                dao.changedRecord(provider, recordId, priority);
            } catch (RawRepoException ex) {
                conn.rollback();
                LOGGER.error(ex.getMessage(), ex);
                throw new UpdateException(ex.getMessage(), ex);
            }
        } catch (SQLException ex) {
            LOGGER.error(ex.getMessage(), ex);
            throw new UpdateException(ex.getMessage(), ex);
        } catch (Exception e) {
            incrementErrorCounterMetric(methodName, e);
            throw e;
        } finally {
            watch.stop("rawrepo.changedRecord");
            updateSimpleTimerMetric(methodName, watch);
        }
    }

    public void enqueue(RecordId recordId, String provider, boolean changed, boolean leaf, int priority) throws UpdateException {
        final StopWatch watch = new Log4JStopWatch().setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        final String methodName = "enqueue";

        try (Connection conn = dataSource.getConnection()) {
            try {
                final RawRepoDAO dao = createDAO(conn);
                dao.enqueue(recordId, provider, changed, leaf, priority);
            } catch (RawRepoException ex) {
                conn.rollback();
                LOGGER.error(ex.getMessage(), ex);
                throw new UpdateException(ex.getMessage(), ex);
            }
        } catch (SQLException ex) {
            LOGGER.error(ex.getMessage(), ex);
            throw new UpdateException(ex.getMessage(), ex);
        } catch (Exception e) {
            incrementErrorCounterMetric(methodName, e);
            throw e;

        } finally {
            watch.stop("rawrepo.enqueue");
            updateSimpleTimerMetric(methodName, watch);
        }
    }

    public boolean checkProvider(String provider) throws UpdateException {
        final StopWatch watch = new Log4JStopWatch().setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        final String methodName = "checkProvider";
        try (Connection conn = dataSource.getConnection()) {
            try {
                final RawRepoDAO dao = createDAO(conn);

                return dao.checkProvider(provider);
            } catch (RawRepoException ex) {
                conn.rollback();
                LOGGER.error(ex.getMessage(), ex);
                throw new UpdateException(ex.getMessage(), ex);
            }
        } catch (SQLException ex) {
            LOGGER.error(ex.getMessage(), ex);
            throw new UpdateException(ex.getMessage(), ex);
        } catch (Exception e) {
            incrementErrorCounterMetric(methodName, e);
            throw e;
        } finally {
            watch.stop("rawrepo.checkProvider");
            updateSimpleTimerMetric(methodName, watch);
        }
    }

    /**
     * Encodes the record as marcxchange.
     *
     * @param marcRecord The record to encode.
     * @return The encoded record as a sequence of bytes.
     * @throws JAXBException                if the record can not be encoded in marcxchange.
     * @throws UnsupportedEncodingException if the record can not be encoded in UTF-8
     */
    public byte[] encodeRecord(MarcRecord marcRecord) throws JAXBException, UnsupportedEncodingException {
        if (marcRecord.getFields().isEmpty()) {
            return null;
        }
        final RecordType marcXchangeType = MarcXchangeFactory.createMarcXchangeFromMarc(marcRecord);

        final ObjectFactory objectFactory = new ObjectFactory();
        final JAXBElement<RecordType> jAXBElement = objectFactory.createRecord(marcXchangeType);

        final JAXBContext jc = JAXBContext.newInstance(CollectionType.class);
        final Marshaller marshaller = jc.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, "http://www.loc.gov/standards/iso25577/marcxchange-1-1.xsd");

        final StringWriter recData = new StringWriter();
        marshaller.marshal(jAXBElement, recData);

        LOGGER.info("Marshalled record: {}", recData.toString());
        return recData.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Constructs a RawRepoDAO to access the rawrepo database.
     *
     * @param conn The JDBC connection to used to access the database.
     * @return A RawRepoDAO.
     * @throws RawRepoException      Throwed by RawRepoDAO in case of an error.
     * @throws IllegalStateException If authentication is null.
     */
    protected RawRepoDAO createDAO(Connection conn) throws RawRepoException {
        final StopWatch watch = new Log4JStopWatch().setTimeThreshold(LOG_DURATION_THRESHOLD_MS);
        try {
            final RawRepoDAO.Builder rawRepoBuilder = RawRepoDAO.builder(conn);

            rawRepoBuilder.relationHints(new RelationHintsVipCore(vipCoreLibraryRulesConnector));
            return rawRepoBuilder.build();
        } finally {
            watch.stop("rawrepo.createDAO");
        }
    }

    public static boolean isSchoolEnrichment(int agencyId) {
        return MIN_SCHOOL_AGENCY <= agencyId && agencyId <= RawRepo.MAX_SCHOOL_AGENCY;
    }

    private void incrementErrorCounterMetric(String methodName, Exception e) {
        metricsHandler.increment(rawrepoErrorCounterMetrics,
                new Tag(METHOD_NAME_KEY, methodName),
                new Tag(ERROR_TYPE, e.getMessage().toLowerCase()));
    }

    private void updateSimpleTimerMetric(String methodName, StopWatch watch) {
        metricsHandler.update(rawrepoDaoTimingMetrics,
                Duration.ofMillis(watch.getElapsedTime()), new Tag(METHOD_NAME_KEY, methodName));
    }
}
