/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.records.MarcXchangeFactory;
import dk.dbc.iscrum.records.marcxchange.CollectionType;
import dk.dbc.iscrum.records.marcxchange.ObjectFactory;
import dk.dbc.rawrepo.RawRepoDAO;
import dk.dbc.rawrepo.RawRepoException;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.ws.JNDIResources;
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
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/**
 * EJB to provide access to the RawRepo database.
 */
@Stateless
public class RawRepo {
    private static final XLogger logger = XLoggerFactory.getXLogger(RawRepo.class);
    public static final Integer COMMON_AGENCY = 870970;
    public static final Integer ARTICLE_AGENCY = 870971;
    public static final List<String> DBC_AGENCY_LIST = Arrays.asList("870970", "870971", "870973", "870974", "870975", "870976", "870979", "870978", "000002", "000004", "000007", "000008");
    public static final List<String> DBC_PRIVATE_AGENCY_LIST = Arrays.asList("870971", "870973", "870974", "870975", "870976", "870979", "870978", "000002", "000004", "000007", "000008");
    public static final Integer DBC_ENRICHMENT = 191919;
    public static final Integer SCHOOL_COMMON_AGENCY = 300000;
    public static final Integer MIN_SCHOOL_AGENCY = SCHOOL_COMMON_AGENCY + 1;
    public static final Integer MAX_SCHOOL_AGENCY = SCHOOL_COMMON_AGENCY + 99999;

    @Resource(lookup = JNDIResources.JNDI_NAME_UPDATESERVICE)
    private Properties settings;

    @EJB
    OpenAgencyService openAgency;

    /**
     * Injected DataSource to read from the rawrepo database.
     */
    @Resource(lookup = JNDIResources.JDBC_RAW_REPO_READONLY_NAME)
    private DataSource dataSourceReader;

    /**
     * Injected DataSource to write to the rawrepo database.
     */
    @Resource(lookup = JNDIResources.JDBC_RAW_REPO_WRITABLE_NAME)
    private DataSource dataSourceWriter;

    public enum RecordType {
        COMMON_TYPE,
        ENRICHMENT_TYPE,
        LOCAL_TYPE
    }

    public RawRepo() {
        this.dataSourceReader = null;
        this.dataSourceWriter = null;
    }

    public RawRepo(DataSource dataSourceReader, DataSource dataSourceWriter) {
        this.dataSourceReader = dataSourceReader;
        this.dataSourceWriter = dataSourceWriter;
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
            if (recordId == null) {
                throw new IllegalArgumentException("recordId can not be null");
            }
            if (recordId.isEmpty()) {
                throw new IllegalArgumentException("recordId can not be empty");
            }
            try (Connection conn = dataSourceReader.getConnection()) {
                try {
                    RawRepoDAO dao = createDAO(conn);
                    result = dao.allAgenciesForBibliographicRecordId(recordId);
                    result.remove(COMMON_AGENCY);
                    result.remove(ARTICLE_AGENCY);
                    result.remove(DBC_ENRICHMENT);
                    return result;
                } catch (RawRepoException ex) {
                    conn.rollback();
                    logger.error(ex.getMessage(), ex);
                    throw new UpdateException(ex.getMessage(), ex);
                }
            } catch (SQLException e) {
                logger.error(e.getMessage(), e);
                throw new UpdateException(e.getMessage(), e);
            }
        } finally {
            watch.stop("rawrepo.agenciesForRecord.String");
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

        try {
            if (recordId == null) {
                throw new IllegalArgumentException("recordId can not be null");
            }

            try (Connection conn = dataSourceReader.getConnection()) {
                try {
                    RawRepoDAO dao = createDAO(conn);
                    return dao.getRelationsChildren(recordId);
                } catch (RawRepoException ex) {
                    conn.rollback();
                    logger.error(ex.getMessage(), ex);
                    throw new UpdateException(ex.getMessage(), ex);
                }
            } catch (SQLException ex) {
                logger.error(ex.getMessage(), ex);
                throw new UpdateException(ex.getMessage(), ex);
            }
        } finally {
            watch.stop("rawrepo.children.RecordId");
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

        try {
            if (recordId == null) {
                throw new IllegalArgumentException("recordId can not be null");
            }

            try (Connection conn = dataSourceReader.getConnection()) {
                try {
                    RawRepoDAO dao = createDAO(conn);

                    return dao.getRelationsSiblingsToMe(recordId);
                } catch (RawRepoException ex) {
                    conn.rollback();
                    logger.error(ex.getMessage(), ex);
                    throw new UpdateException(ex.getMessage(), ex);
                }
            } catch (SQLException ex) {
                logger.error(ex.getMessage(), ex);
                throw new UpdateException(ex.getMessage(), ex);
            }
        } finally {
            watch.stop("rawrepo.enrichments.RecordId");
            logger.exit();
        }
    }

    /**
     * Fetches the RawRepo record for a MarcRecord.
     * <p/>
     * If the record does not exist in the RawRepo then it will be created.
     *
     * @param recId    String
     * @param agencyId Integer
     * @return The RawRepo record.
     * @throws UpdateException In case of an error from RawRepo or an SQL exception.
     */
    public Record fetchRecord(String recId, Integer agencyId) throws UpdateException {
        logger.entry(recId, agencyId);
        StopWatch watch = new Log4JStopWatch();
        Record result = null;
        try {
            if (recId == null) {
                throw new IllegalArgumentException("recId can not be null");
            }
            if (agencyId == null) {
                throw new IllegalArgumentException("agencyId can not be null");
            }
            try (Connection conn = dataSourceReader.getConnection()) {
                try {
                    RawRepoDAO dao = createDAO(conn);

                    result = dao.fetchRecord(recId, agencyId);
                    return result;
                } catch (RawRepoException ex) {
                    conn.rollback();
                    logger.error(ex.getMessage(), ex);
                    throw new UpdateException(ex.getMessage(), ex);
                }
            } catch (SQLException ex) {
                logger.error(ex.getMessage(), ex);
                throw new UpdateException(ex.getMessage(), ex);
            }
        } finally {
            watch.stop("rawrepo.fetchRecord");
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
    public boolean recordExists(String recordId, Integer agencyId) throws UpdateException {
        logger.entry(recordId, agencyId);
        logger.info("RawRepo.recordExists, input, recordId=" + recordId + ", agencyId=" + agencyId);
        StopWatch watch = new Log4JStopWatch();
        boolean result = false;
        if (dataSourceReader == null) {
            logger.info("RawRepo.recordExists, dataSourceReader == NULL");
        }
        try (Connection conn = dataSourceReader.getConnection()) {
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
                logger.error(e.getMessage(), e);
                throw new UpdateException(e.getMessage(), e);
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            throw new UpdateException(ex.getMessage(), ex);
        } finally {
            watch.stop("rawrepo.recordExists");
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
    public boolean recordExistsMaybeDeleted(String recordId, Integer agencyId) throws UpdateException {
        logger.entry(recordId, agencyId);
        StopWatch watch = new Log4JStopWatch();

        boolean result = false;
        try (Connection conn = dataSourceReader.getConnection()) {
            try {
                RawRepoDAO dao = createDAO(conn);

                result = dao.recordExistsMaybeDeleted(recordId, agencyId);
                return result;
            } catch (RawRepoException ex) {
                conn.rollback();
                logger.error(ex.getMessage(), ex);
                throw new UpdateException(ex.getMessage(), ex);
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            throw new UpdateException(ex.getMessage(), ex);
        } finally {
            watch.stop("rawrepo.recordExistsMaybeDeleted");
            logger.exit(result);
        }
    }

    public void saveRecord(Record record) throws UpdateException {
        logger.entry(record);
        StopWatch watch = new Log4JStopWatch();
        try (Connection conn = dataSourceWriter.getConnection()) {
            try {
                RawRepoDAO dao = createDAO(conn);
                if (record.isDeleted()) {
                    dao.setRelationsFrom(record.getId(), new HashSet<>());
                }
                dao.saveRecord(record);
            } catch (RawRepoException e) {
                conn.rollback();
                logger.error(e.getMessage(), e);
                throw new UpdateException(e.getMessage(), e);
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            throw new UpdateException(ex.getMessage(), ex);
        } finally {
            watch.stop("rawrepo.saveRecord.Record");
            logger.exit();
        }
    }

    public void removeLinks(RecordId recId) throws UpdateException {
        logger.entry(recId);
        StopWatch watch = new Log4JStopWatch();
        try (Connection conn = dataSourceWriter.getConnection()) {
            try {
                RawRepoDAO dao = createDAO(conn);
                final HashSet<RecordId> references = new HashSet<>();
                dao.setRelationsFrom(recId, references);
            } catch (RawRepoException e) {
                conn.rollback();
                logger.error(e.getMessage(), e);
                throw new UpdateException(e.getMessage(), e);
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            throw new UpdateException(ex.getMessage(), ex);
        } finally {
            watch.stop("rawrepo.removeLinks");
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
        try (Connection conn = dataSourceWriter.getConnection()) {
            try {
                RawRepoDAO dao = createDAO(conn);
                final HashSet<RecordId> references = new HashSet<>();
                references.add(refer_id);
                dao.setRelationsFrom(id, references);
            } catch (RawRepoException e) {
                conn.rollback();
                logger.error(e.getMessage(), e);
                throw new UpdateException(e.getMessage(), e);
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            throw new UpdateException(ex.getMessage(), ex);
        } finally {
            watch.stop("rawrepo.linkRecord");
            logger.exit();
        }
    }

    public void changedRecord(String provider, RecordId recId) throws UpdateException {
        logger.entry(provider, recId);
        StopWatch watch = new Log4JStopWatch();

        try (Connection conn = dataSourceWriter.getConnection()) {
            try {
                RawRepoDAO dao = createDAO(conn);
                dao.changedRecord(provider, recId);
            } catch (RawRepoException ex) {
                conn.rollback();
                logger.error(ex.getMessage(), ex);
                throw new UpdateException(ex.getMessage(), ex);
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            throw new UpdateException(ex.getMessage(), ex);
        } finally {
            watch.stop("rawrepo.changedRecord");
            logger.exit();
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
            dk.dbc.iscrum.records.marcxchange.RecordType marcXchangeType = MarcXchangeFactory.createMarcXchangeFromMarc(record);

            ObjectFactory objectFactory = new ObjectFactory();
            JAXBElement<dk.dbc.iscrum.records.marcxchange.RecordType> jAXBElement = objectFactory.createRecord(marcXchangeType);

            JAXBContext jc = JAXBContext.newInstance(CollectionType.class);
            Marshaller marshaller = jc.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, "http://www.loc.gov/standards/iso25577/marcxchange-1-1.xsd");

            StringWriter recData = new StringWriter();
            marshaller.marshal(jAXBElement, recData);

            logger.info("Marshalled record: {}", recData.toString());
            result = recData.toString().getBytes("UTF-8");
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
            rawRepoBuilder.openAgency(openAgency.getService(), null);
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

    public static boolean isSchoolEnrichment(Integer agencyId) {
        return MIN_SCHOOL_AGENCY <= agencyId && agencyId <= RawRepo.MAX_SCHOOL_AGENCY;
    }

    public static int convertAgencyId(String agencyId) throws UpdateException {
        try {
            return Integer.parseInt(agencyId, 10);
        } catch (NumberFormatException ex) {
            throw new UpdateException(String.format("Biblioteksnummeret '%s' er ikke et tal", agencyId), ex);
        }
    }

    private void linkEnrichment(RawRepoDAO dao, Record record) throws RawRepoException, SQLException {
        logger.entry(dao, record);
        try {
            linkToRecord(dao, record.getId(), new RecordId(record.getId().getBibliographicRecordId(), COMMON_AGENCY));
        } finally {
            logger.exit();
        }
    }

    private void linkMultivolume(RawRepoDAO dao, Record record, String parentId) throws RawRepoException, SQLException {
        logger.entry(dao, record, parentId);
        try {
            linkToRecord(dao, record.getId(), new RecordId(parentId, record.getId().getAgencyId()));
        } finally {
            logger.exit();
        }
    }

    private void linkToRecord(RawRepoDAO dao, RecordId id, RecordId refer_id) throws SQLException, RawRepoException {
        logger.entry(dao, id, refer_id);
        try {
            final HashSet<RecordId> references = new HashSet<>();
            references.add(refer_id);
            dao.setRelationsFrom(id, references);
            logger.info("Set relation from [{}:{}] -> [{}:{}]", id.getBibliographicRecordId(), id.getAgencyId(), refer_id.getBibliographicRecordId(), refer_id.getAgencyId());
        } finally {
            logger.exit();
        }
    }

    private void clearLinks(RawRepoDAO dao, Record record) throws RawRepoException {
        logger.entry();
        try {
            final HashSet<RecordId> references = new HashSet<>();
            dao.setRelationsFrom(record.getId(), references);
            logger.info("Clear relations for [{}:{}]", record.getId().getBibliographicRecordId(), record.getId().getAgencyId());
        } finally {
            logger.exit();
        }
    }
}
