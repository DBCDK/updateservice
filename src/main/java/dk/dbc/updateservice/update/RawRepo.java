/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcXchangeFactory;
import dk.dbc.common.records.marcxchange.CollectionType;
import dk.dbc.common.records.marcxchange.ObjectFactory;
import dk.dbc.common.records.marcxchange.RecordType;
import dk.dbc.common.records.utils.RecordContentTransformer;
import dk.dbc.commons.metricshandler.CounterMetric;
import dk.dbc.commons.metricshandler.MetricsHandlerBean;
import dk.dbc.commons.metricshandler.SimpleTimerMetric;
import dk.dbc.marcxmerge.FieldRules;
import dk.dbc.marcxmerge.MarcXMerger;
import dk.dbc.marcxmerge.MarcXMergerException;
import dk.dbc.rawrepo.RawRepoDAO;
import dk.dbc.rawrepo.RawRepoException;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.rawrepo.RelationHintsOpenAgency;
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
import javax.ejb.EJB;
import javax.ejb.Stateless;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * EJB to provide access to the RawRepo database.
 */
@Stateless
public class RawRepo {

    @Inject
    MetricsHandlerBean metricsHandler;

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

    private static final String METHOD_NAME_KEY = "method";
    private static final String ERROR_TYPE = "errortype";
    private static final Tag INTERNAL_SERVER_ERROR_TAG = new Tag(ERROR_TYPE, "internalservererror");

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


    private static final XLogger logger = XLoggerFactory.getXLogger(RawRepo.class);
    public static final int COMMON_AGENCY = 870970;
    public static final int ARTICLE_AGENCY = 870971;
    public static final int LITTOLK_AGENCY = 870974;
    public static final int MATVURD_AGENCY = 870976;
    public static final int AUTHORITY_AGENCY = 870979;
    public static final List<String> DBC_AGENCY_LIST = Arrays.asList("870970", "870971", "870973", "870974", "870975", "870976", "870978", "870979", "190002", "190004", "190007", "190008");
    public static final List<String> DBC_AGENCY_ALL = Arrays.asList("190002", "190004", "191919", "870970", "870971", "870974", "870979"); // More will probably be added later
    public static final List<String> DBC_PRIVATE_AGENCY_LIST = Arrays.asList("870971", "870973", "870974", "870975", "870976", "870978", "870979", "190002", "190004", "190007", "190008");
    public static final int DBC_ENRICHMENT = 191919;
    public static final int SCHOOL_COMMON_AGENCY = 300000;
    public static final int MIN_SCHOOL_AGENCY = SCHOOL_COMMON_AGENCY + 1;
    public static final int MAX_SCHOOL_AGENCY = SCHOOL_COMMON_AGENCY + 99999;
    public static final List<String> AUTHORITY_FIELDS = Arrays.asList("100", "600", "700", "770", "780");
    public static final List<String> MATVURD_FIELDS = Arrays.asList("r01", "r02");

    public static final int ENQUEUE_PRIORITY_DEFAULT = 500;

    @EJB
    private OpenAgencyService openAgency;

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
     * The agency for common records is not returned in the set.
     *
     * @param record The record to lookup local agencies for.
     * @return A Set of agency ids for the local agencies.
     * @throws UpdateException In case of an error from RawRepo or an SQL exception.
     */
    public Set<Integer> agenciesForRecord(MarcRecord record) throws UpdateException {
        logger.entry(record);
        StopWatch watch = new Log4JStopWatch();
        Set<Integer> result = null;
        try {
            if (record == null) {
                throw new IllegalArgumentException("record can not be null");
            }
            result = agenciesForRecord(getRecordId(record));
            return result;
        } finally {
            watch.stop("rawrepo.agenciesForRecord.MarcRecord");
            logger.exit(result);
        }
    }

    /**
     * Returns a Set of local agencies for a record.
     * <p/>
     * The agency for common records is not returned in the set nor is deleted records
     *
     * @param record The record to lookup local agencies for.
     * @return A Set of agency ids for the local agencies.
     * @throws UpdateException In case of an error from RawRepo or an SQL exception.
     */
    public Set<Integer> agenciesForRecordNotDeleted(MarcRecord record) throws UpdateException {
        logger.entry(record);
        StopWatch watch = new Log4JStopWatch();
        Set<Integer> activeAgencies = new HashSet<>();
        Set<Integer> allAgencies;
        try {
            if (record == null) {
                throw new IllegalArgumentException("record can not be null");
            }
            allAgencies = agenciesForRecord(getRecordId(record));

            if (allAgencies != null) {
                MarcRecordReader reader = new MarcRecordReader(record);
                for (Integer agencyId : allAgencies) {
                    if (recordExists(reader.getRecordId(), agencyId)) {
                        activeAgencies.add(agencyId);
                    }
                }
            }

            return activeAgencies;
        } finally {
            watch.stop("rawrepo.agenciesForRecord.MarcRecord");
            logger.exit(activeAgencies);
        }
    }

    public Set<Integer> agenciesForRecordAll(MarcRecord record) throws UpdateException {
        logger.entry(record);
        StopWatch watch = new Log4JStopWatch();
        Set<Integer> result = null;
        try {
            if (record == null) {
                throw new IllegalArgumentException("record can not be null");
            }
            result = agenciesForRecordAll(getRecordId(record));
            return result;
        } finally {
            watch.stop("rawrepo.agenciesForRecord.MarcRecord");
            logger.exit(result);
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
        logger.entry(recordId);
        StopWatch watch = new Log4JStopWatch();
        Set<Integer> result = null;
        try {
            result = agenciesForRecordAll(recordId);
            result.remove(COMMON_AGENCY);
            result.remove(ARTICLE_AGENCY);

            return result;
        } finally {
            watch.stop("rawrepo.agenciesForRecord.String");
            logger.exit(result);
        }
    }

    public Set<Integer> agenciesForRecordAll(String recordId) throws UpdateException {
        logger.entry(recordId);
        StopWatch watch = new Log4JStopWatch();
        Set<Integer> result = null;
        String methodName = "allAgenciesForBibliographicRecordId";

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
                    logger.error(ex.getMessage(), ex);
                    incrementErrorCounterMetric(methodName, ex);
                    throw new UpdateException(ex.getMessage(), ex);
                }
            } catch (SQLException e) {
                logger.error(e.getMessage(), e);
                incrementErrorCounterMetric(methodName, e);
                throw new UpdateException(e.getMessage(), e);
            }
        } catch (Throwable e) {
            incrementInternalServerErrorCounterMetric(methodName);
            throw e;
        } finally {
            watch.stop("rawrepo.agenciesForRecord.String");
            updateSimpleTimerMetric(methodName, watch);
            logger.exit(result);
        }
    }

    public Set<RecordId> children(MarcRecord record) throws UpdateException {
        logger.entry();
        StopWatch watch = new Log4JStopWatch();

        try {
            if (record == null) {
                throw new IllegalArgumentException("record can not be null");
            }

            RecordId recordId = new RecordId(getRecordId(record), convertAgencyId(getAgencyId(record)));
            return children(recordId);
        } finally {
            watch.stop("rawrepo.children.MarcRecord");
            logger.exit();
        }
    }

    public Set<RecordId> children(RecordId recordId) throws UpdateException {
        logger.entry();
        StopWatch watch = new Log4JStopWatch();
        String methodNameTag = "getRelationsChildren";

        try {
            if (recordId == null) {
                throw new IllegalArgumentException("recordId can not be null");
            }

            try (Connection conn = dataSource.getConnection()) {
                try {
                    RawRepoDAO dao = createDAO(conn);
                    return dao.getRelationsChildren(recordId);
                } catch (RawRepoException ex) {
                    conn.rollback();
                    logger.error(ex.getMessage(), ex);
                    incrementErrorCounterMetric(methodNameTag, ex);
                    throw new UpdateException(ex.getMessage(), ex);
                }
            } catch (SQLException ex) {
                logger.error(ex.getMessage(), ex);
                incrementErrorCounterMetric(methodNameTag, ex);
                throw new UpdateException(ex.getMessage(), ex);
            }
        } catch (Throwable e) {
            incrementInternalServerErrorCounterMetric(methodNameTag);
            throw e;
        } finally {
            watch.stop("rawrepo.children.RecordId");
            updateSimpleTimerMetric(methodNameTag, watch);
            logger.exit();
        }
    }

    public Set<RecordId> enrichments(MarcRecord record) throws UpdateException {
        logger.entry();
        StopWatch watch = new Log4JStopWatch();

        try {
            if (record == null) {
                throw new IllegalArgumentException("record can not be null");
            }

            RecordId recordId = new RecordId(getRecordId(record), convertAgencyId(getAgencyId(record)));
            return enrichments(recordId);
        } finally {
            watch.stop("rawrepo.enrichments.MarcRecord");
            logger.exit();
        }
    }

    public Set<RecordId> enrichments(RecordId recordId) throws UpdateException {
        logger.entry();
        StopWatch watch = new Log4JStopWatch();
        String methodNameTag = "getRelationsSiblingsToMe";

        try {
            if (recordId == null) {
                throw new IllegalArgumentException("recordId can not be null");
            }

            try (Connection conn = dataSource.getConnection()) {
                try {
                    RawRepoDAO dao = createDAO(conn);

                    return dao.getRelationsSiblingsToMe(recordId);
                } catch (RawRepoException ex) {
                    conn.rollback();
                    logger.error(ex.getMessage(), ex);
                    incrementErrorCounterMetric(methodNameTag, ex);
                    throw new UpdateException(ex.getMessage(), ex);
                }
            } catch (SQLException ex) {
                logger.error(ex.getMessage(), ex);
                incrementErrorCounterMetric(methodNameTag, ex);
                throw new UpdateException(ex.getMessage(), ex);
            }
        } catch (Throwable e) {
            incrementInternalServerErrorCounterMetric(methodNameTag);
            throw e;
        } finally {
            watch.stop("rawrepo.enrichments.RecordId");
            updateSimpleTimerMetric(methodNameTag, watch);
            logger.exit();
        }
    }

    /**
     * Fetches the RawRepo record for a MarcRecord.
     * <p/>
     * If the record does not exist in the RawRepo then it will be created.
     *
     * @param recId    String
     * @param agencyId int
     * @return The RawRepo record.
     * @throws UpdateException In case of an error from RawRepo or an SQL exception.
     */
    public Record fetchRecord(String recId, int agencyId) throws UpdateException {
        logger.entry(recId, agencyId);
        StopWatch watch = new Log4JStopWatch();
        Record result = null;
        String methodNameTag =  "fetchRecord";

        try {
            if (recId == null) {
                throw new IllegalArgumentException("recId can not be null");
            }
            try (Connection conn = dataSource.getConnection()) {
                try {
                    RawRepoDAO dao = createDAO(conn);

                    result = dao.fetchRecord(recId, agencyId);
                    return result;
                } catch (RawRepoException ex) {
                    conn.rollback();
                    logger.error(ex.getMessage(), ex);
                    incrementErrorCounterMetric(methodNameTag, ex);
                    throw new UpdateException(ex.getMessage(), ex);
                }
            } catch (SQLException ex) {
                logger.error(ex.getMessage(), ex);
                incrementErrorCounterMetric(methodNameTag, ex);
                throw new UpdateException(ex.getMessage(), ex);
            }
        } catch (Throwable e) {
            incrementInternalServerErrorCounterMetric(methodNameTag);
            throw e;
        } finally {
            watch.stop("rawrepo.fetchRecord");
            updateSimpleTimerMetric(methodNameTag, watch);
            logger.exit(result);
        }
    }

    public Map<String, MarcRecord> fetchRecordCollection(String recId, int agencyId) throws UpdateException {
        logger.entry(recId, agencyId);
        StopWatch watch = new Log4JStopWatch();
        String methodNamTag = "fetchRecordCollection";
        Map<String, MarcRecord> result = null;
        Map<String, Record> recordMap;
        try (Connection conn = dataSource.getConnection()) {
            if (recId == null) {
                throw new IllegalArgumentException("recId can not be null");
            }
            try {
                RawRepoDAO dao = createDAO(conn);
                MarcXMerger merger = new MarcXMerger();
                recordMap = dao.fetchRecordCollection(recId, agencyId, merger);
                if (recordMap.size() > 0) {
                    result = new HashMap<>();
                    for (Map.Entry<String, Record> entry : recordMap.entrySet()) {
                        Record record = entry.getValue();
                        result.put(entry.getKey(), RecordContentTransformer.decodeRecord(record.getContent()));
                    }
                }
                return result;
            } catch (RawRepoException | MarcXMergerException | UnsupportedEncodingException ex) {
                conn.rollback();
                logger.error(ex.getMessage(), ex);
                incrementErrorCounterMetric(methodNamTag, ex);
                throw new UpdateException(ex.getMessage(), ex);
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            incrementErrorCounterMetric(methodNamTag, ex);
            throw new UpdateException(ex.getMessage(), ex);
        } catch (Throwable e) {
            incrementInternalServerErrorCounterMetric(methodNamTag);
            throw e;
        } finally {
            watch.stop("rawrepo.fetchRecordCollection");
            updateSimpleTimerMetric(methodNamTag, watch);
            logger.exit();
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
        logger.entry(bibliographicRecordId, agencyId);
        StopWatch watch = new Log4JStopWatch();
        String methodNameTag = "fetchMergedRecord";
        Record result = null;
        try {
            if (bibliographicRecordId == null) {
                throw new IllegalArgumentException("recId can not be null");
            }
            try (Connection conn = dataSource.getConnection()) {
                try {
                    RawRepoDAO dao = createDAO(conn);

                    final String immutable = "001;010;020;990;991;996";
                    final String overwrite = "004;005;013;014;017;035;036;240;243;247;300;008 009 038 039 100 110 239 245 652 654";

                    final FieldRules customFieldRules = new FieldRules(immutable, overwrite, FieldRules.INVALID_DEFAULT, FieldRules.VALID_REGEX_DANMARC2);
                    final MarcXMerger merger = new MarcXMerger(customFieldRules, "USE_PARENT_AGENCY");

                    result = dao.fetchMergedRecord(bibliographicRecordId, agencyId, merger, true);
                    return result;
                } catch (RawRepoException | MarcXMergerException ex) {
                    conn.rollback();
                    logger.error(ex.getMessage(), ex);
                    incrementErrorCounterMetric(methodNameTag, ex);
                    throw new UpdateException(ex.getMessage(), ex);
                }
            } catch (SQLException ex) {
                logger.error(ex.getMessage(), ex);
                incrementErrorCounterMetric(methodNameTag, ex);
                throw new UpdateException(ex.getMessage(), ex);
            }
        } catch (Throwable e) {
            incrementInternalServerErrorCounterMetric(methodNameTag);
            throw e;
        } finally {
            watch.stop("rawrepo.fetchRecord");
            updateSimpleTimerMetric(methodNameTag, watch);
            logger.exit(result);
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
        logger.entry(recordId, agencyId);
        logger.info("RawRepo.recordExists, input, recordId=" + recordId + ", agencyId=" + agencyId);
        StopWatch watch = new Log4JStopWatch();
        String methodNameTag = "recordExists";
        boolean result = false;
        if (dataSource == null) {
            logger.info("RawRepo.recordExists, dataSourceReader == NULL");
        }
        try (Connection conn = dataSource.getConnection()) {
            if (conn == null) {
                logger.info("RawRepo.recordExists, conn == NULL");
            }
            try {
                RawRepoDAO dao = createDAO(conn);
                if (dao == null) {
                    logger.info("RawRepo.recordExists, dao == NULL");
                }
                result = dao.recordExists(recordId, agencyId);
                return result;
            } catch (RawRepoException e) {
                if (conn != null) {
                    conn.rollback();
                }
                incrementErrorCounterMetric(methodNameTag, e);
                logger.error(e.getMessage(), e);
                throw new UpdateException(e.getMessage(), e);
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            incrementErrorCounterMetric(methodNameTag, ex);
            throw new UpdateException(ex.getMessage(), ex);
        } catch (Throwable e) {
            incrementInternalServerErrorCounterMetric(methodNameTag);
            throw e;
        } finally {
            watch.stop("rawrepo.recordExists");
            updateSimpleTimerMetric(methodNameTag, watch);
            logger.exit(result);
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
        logger.entry(recordId, agencyId);
        StopWatch watch = new Log4JStopWatch();
        String  methodNameTag = "recordExistsMaybeDeleted";

        boolean result = false;
        try (Connection conn = dataSource.getConnection()) {
            try {
                RawRepoDAO dao = createDAO(conn);

                result = dao.recordExistsMaybeDeleted(recordId, agencyId);
                return result;
            } catch (RawRepoException ex) {
                conn.rollback();
                logger.error(ex.getMessage(), ex);
                incrementErrorCounterMetric(methodNameTag, ex);
                throw new UpdateException(ex.getMessage(), ex);
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            incrementErrorCounterMetric(methodNameTag, ex);
            throw new UpdateException(ex.getMessage(), ex);
        } catch (Throwable e) {
            incrementInternalServerErrorCounterMetric(methodNameTag);
            throw e;

        } finally {
            watch.stop("rawrepo.recordExistsMaybeDeleted");
            updateSimpleTimerMetric(methodNameTag, watch);
            logger.exit(result);
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
        logger.entry(recordId, agencyId);
        StopWatch watch = new Log4JStopWatch();
        String methodNameTag = "recordExistsMaybeDeleted";

        boolean result = false;
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
                logger.error(ex.getMessage(), ex);
                incrementErrorCounterMetric(methodNameTag, ex);
                throw new UpdateException(ex.getMessage(), ex);
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            incrementErrorCounterMetric(methodNameTag, ex);
            throw new UpdateException(ex.getMessage(), ex);
        } catch (Throwable e) {
            incrementInternalServerErrorCounterMetric(methodNameTag);
            throw e;
        } finally {
            watch.stop("rawrepo.recordExistsMaybeDeleted");
            updateSimpleTimerMetric(methodNameTag, watch);
            logger.exit(result);
        }
    }

    public void saveRecord(Record record) throws UpdateException {
        logger.entry(record);
        StopWatch watch = new Log4JStopWatch();
        String  methodNameTag = "saveRecord";
        try (Connection conn = dataSource.getConnection()) {
            try {
                RawRepoDAO dao = createDAO(conn);
                if (record.isDeleted()) {
                    dao.setRelationsFrom(record.getId(), new HashSet<>());
                }
                dao.saveRecord(record);
            } catch (RawRepoException e) {
                conn.rollback();
                logger.error(e.getMessage(), e);
                incrementErrorCounterMetric(methodNameTag, e);
                throw new UpdateException(e.getMessage(), e);
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            incrementErrorCounterMetric(methodNameTag, ex);
            throw new UpdateException(ex.getMessage(), ex);
        } catch (Throwable e) {
            incrementInternalServerErrorCounterMetric(methodNameTag);
            throw e;
        } finally {
            watch.stop("rawrepo.saveRecord.Record");
            updateSimpleTimerMetric(methodNameTag, watch);
            logger.exit();
        }
    }

    public void removeLinks(RecordId recId) throws UpdateException {
        logger.entry(recId);
        StopWatch watch = new Log4JStopWatch();
        String methodName = "setRelationsFrom";
        try (Connection conn = dataSource.getConnection()) {
            try {
                RawRepoDAO dao = createDAO(conn);
                final HashSet<RecordId> references = new HashSet<>();
                dao.setRelationsFrom(recId, references);
            } catch (RawRepoException e) {
                conn.rollback();
                logger.error(e.getMessage(), e);
                incrementErrorCounterMetric(methodName, e);
                throw new UpdateException(e.getMessage(), e);
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            incrementErrorCounterMetric(methodName, ex);
            throw new UpdateException(ex.getMessage(), ex);
        } catch (Throwable e) {
            incrementInternalServerErrorCounterMetric(methodName);
            throw e;
        } finally {
            watch.stop("rawrepo.removeLinks");
            updateSimpleTimerMetric(methodName,watch);
            logger.exit();
        }
    }

    /**
     * Creates a link between two records in rawrepo.
     *
     * @param id       Id of the record to link from.
     * @param refer_id Id of the record to link to.
     * @throws UpdateException In case of SQLException or RawRepoException, that exception
     *                         encapsulated in an UpdateException.
     */
    public void linkRecord(RecordId id, RecordId refer_id) throws UpdateException {
        logger.entry(id, refer_id);
        StopWatch watch = new Log4JStopWatch();
        String methodName = "setRelationsFrom";
        try (Connection conn = dataSource.getConnection()) {
            try {
                RawRepoDAO dao = createDAO(conn);
                Set<RecordId> references = new HashSet<>();
                references.add(refer_id);
                dao.setRelationsFrom(id, references);
            } catch (RawRepoException e) {
                conn.rollback();
                logger.error(e.getMessage(), e);
                incrementErrorCounterMetric(methodName, e);
                throw new UpdateException(e.getMessage(), e);
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            incrementErrorCounterMetric(methodName, ex);
            throw new UpdateException(ex.getMessage(), ex);
        } catch (Throwable e) {
            incrementInternalServerErrorCounterMetric(methodName);
            throw e;
        } finally {
            watch.stop("rawrepo.linkRecord");
            updateSimpleTimerMetric(methodName, watch);
            logger.exit();
        }
    }

    /**
     * Loads the existing links from the id record and adds refer_id to that list
     *
     * @param id       Id of the record to link from.
     * @param refer_id Id of the record to link to.
     * @throws UpdateException In case of SQLException or RawRepoException, that exception
     *                         encapsulated in an UpdateException.
     */
    public void linkRecordAppend(RecordId id, RecordId refer_id) throws UpdateException {
        logger.entry(id, refer_id);
        StopWatch watch = new Log4JStopWatch();
        String methodName = "linkRecordAppend";
        try (Connection conn = dataSource.getConnection()) {
            try {
                RawRepoDAO dao = createDAO(conn);
                Set<RecordId> references = dao.getRelationsFrom(id);
                references.add(refer_id);
                dao.setRelationsFrom(id, references);
            } catch (RawRepoException e) {
                conn.rollback();
                logger.error(e.getMessage(), e);
                incrementErrorCounterMetric(methodName, e);
                throw new UpdateException(e.getMessage(), e);
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            incrementErrorCounterMetric(methodName, ex);
            throw new UpdateException(ex.getMessage(), ex);
        } catch (Throwable e) {
            incrementInternalServerErrorCounterMetric(methodName);
            throw e;
        } finally {
            watch.stop("rawrepo.linkRecord");
            updateSimpleTimerMetric(methodName, watch);
            logger.exit();
        }
    }

    public void changedRecord(String provider, RecordId recId) throws UpdateException {
        changedRecord(provider, recId, 1000);
    }

    public void changedRecord(String provider, RecordId recId, int priority) throws UpdateException {
        logger.entry(provider, recId);
        StopWatch watch = new Log4JStopWatch();
        String methodName = "changedRecord";

        try (Connection conn = dataSource.getConnection()) {
            try {
                RawRepoDAO dao = createDAO(conn);
                dao.changedRecord(provider, recId, priority);
            } catch (RawRepoException ex) {
                conn.rollback();
                logger.error(ex.getMessage(), ex);
                incrementErrorCounterMetric(methodName, ex);
                throw new UpdateException(ex.getMessage(), ex);
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            incrementErrorCounterMetric(methodName, ex);
            throw new UpdateException(ex.getMessage(), ex);
        } catch (Throwable e) {
            incrementInternalServerErrorCounterMetric(methodName);
            throw e;
        } finally {
            watch.stop("rawrepo.changedRecord");
            updateSimpleTimerMetric(methodName, watch);
            logger.exit();
        }
    }

    public void enqueue(RecordId recId, String provider, boolean changed, boolean leaf, int priority) throws UpdateException {
        logger.entry(provider, recId);
        StopWatch watch = new Log4JStopWatch();
        String methodName = "enqueue";

        try (Connection conn = dataSource.getConnection()) {
            try {
                RawRepoDAO dao = createDAO(conn);
                dao.enqueue(recId, provider, changed, leaf, priority);
            } catch (RawRepoException ex) {
                conn.rollback();
                logger.error(ex.getMessage(), ex);
                incrementErrorCounterMetric(methodName, ex);
                throw new UpdateException(ex.getMessage(), ex);
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            incrementErrorCounterMetric(methodName, ex);
            throw new UpdateException(ex.getMessage(), ex);
        } catch (Throwable e) {
            incrementInternalServerErrorCounterMetric(methodName);
            throw e;

        } finally {
            watch.stop("rawrepo.enqueue");
            updateSimpleTimerMetric(methodName, watch);
            logger.exit();
        }
    }

    public boolean checkProvider(String provider) throws UpdateException {
        logger.entry(provider);
        boolean result = false;
        final StopWatch watch = new Log4JStopWatch();
        String methodName = "checkProvider";
        try (Connection conn = dataSource.getConnection()) {
            try {
                final RawRepoDAO dao = createDAO(conn);

                result = dao.checkProvider(provider);

                return result;
            } catch (RawRepoException ex) {
                conn.rollback();
                logger.error(ex.getMessage(), ex);
                incrementErrorCounterMetric(methodName, ex);
                throw new UpdateException(ex.getMessage(), ex);
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            incrementErrorCounterMetric(methodName, ex);
            throw new UpdateException(ex.getMessage(), ex);
        } catch (Throwable e) {
            incrementInternalServerErrorCounterMetric(methodName);
            throw e;
        } finally {
            watch.stop("rawrepo.checkProvider");
            updateSimpleTimerMetric(methodName, watch);
            logger.exit(result);
        }
    }

    /**
     * Encodes the record as marcxchange.
     *
     * @param record The record to encode.
     * @return The encoded record as a sequence of bytes.
     * @throws JAXBException                if the record can not be encoded in marcxchange.
     * @throws UnsupportedEncodingException if the record can not be encoded in UTF-8
     */
    public byte[] encodeRecord(MarcRecord record) throws JAXBException, UnsupportedEncodingException {
        logger.entry(record);
        byte[] result = null;
        try {
            if (record.getFields().isEmpty()) {
                return null;
            }
            RecordType marcXchangeType = MarcXchangeFactory.createMarcXchangeFromMarc(record);

            ObjectFactory objectFactory = new ObjectFactory();
            JAXBElement<RecordType> jAXBElement = objectFactory.createRecord(marcXchangeType);

            JAXBContext jc = JAXBContext.newInstance(CollectionType.class);
            Marshaller marshaller = jc.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, "http://www.loc.gov/standards/iso25577/marcxchange-1-1.xsd");

            StringWriter recData = new StringWriter();
            marshaller.marshal(jAXBElement, recData);

            logger.info("Marshalled record: {}", recData.toString());
            result = recData.toString().getBytes(StandardCharsets.UTF_8);
            return result;
        } finally {
            logger.exit(result);
        }
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
        StopWatch watch = new Log4JStopWatch();
        try {
            RawRepoDAO.Builder rawRepoBuilder = RawRepoDAO.builder(conn);
            rawRepoBuilder.relationHints(new RelationHintsOpenAgency(openAgency.getService()));
            return rawRepoBuilder.build();
        } finally {
            watch.stop("rawrepo.createDAO");
        }
    }

    public static String getRecordId(MarcRecord record) {
        MarcRecordReader mm = new MarcRecordReader(record);
        return mm.getValue("001", "a");
    }

    public static String getAgencyId(MarcRecord record) {
        MarcRecordReader mm = new MarcRecordReader(record);
        return mm.getValue("001", "b");
    }

    public static boolean isSchoolEnrichment(int agencyId) {
        return MIN_SCHOOL_AGENCY <= agencyId && agencyId <= RawRepo.MAX_SCHOOL_AGENCY;
    }

    public static int convertAgencyId(String agencyId) throws UpdateException {
        try {
            return Integer.parseInt(agencyId, 10);
        } catch (NumberFormatException ex) {
            throw new UpdateException(String.format("Biblioteksnummeret '%s' er ikke et tal", agencyId), ex);
        }
    }

    private void incrementErrorCounterMetric(String methodName, Exception e) {
        metricsHandler.increment(rawrepoErrorCounterMetrics,
                new Tag(METHOD_NAME_KEY, methodName),
                new Tag(ERROR_TYPE, e.getMessage().toLowerCase()));
    }

    private void incrementInternalServerErrorCounterMetric(String methodName) {
        metricsHandler.increment(rawrepoErrorCounterMetrics,
                new Tag(METHOD_NAME_KEY, methodName),
                INTERNAL_SERVER_ERROR_TAG);
    }

    private void updateSimpleTimerMetric(String methodName, StopWatch watch) {
        Tag methodNameTag = new Tag(METHOD_NAME_KEY, methodName);
        metricsHandler.update(rawrepoDaoTimingMetrics,
                Duration.ofMillis(watch.getElapsedTime()), methodNameTag);
    }
}
