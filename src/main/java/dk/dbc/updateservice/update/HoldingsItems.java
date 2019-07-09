/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.holdingsitems.HoldingsItemsDAO;
import dk.dbc.holdingsitems.HoldingsItemsException;
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
    private static XLogger logger = XLoggerFactory.getXLogger(HoldingsItems.class);

    @Resource(lookup = "jdbc/holdingsitems")
    private DataSource dataSource;

    public HoldingsItems() {
        this.dataSource = null;
    }

    public HoldingsItems(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Set<Integer> getAgenciesThatHasHoldingsFor(MarcRecord record) throws UpdateException {
        logger.entry(record);
        StopWatch watch = new Log4JStopWatch();
        Set<Integer> result = new HashSet<>();
        try {
            result.addAll(getAgenciesThatHasHoldingsForId(new MarcRecordReader(record).getRecordId()));
            MarcRecordReader mm = new MarcRecordReader(record);
            List<String> aliasIds = mm.getCentralAliasIds();
            for (String s : aliasIds) {
                result.addAll(getAgenciesThatHasHoldingsForId(s));
            }
            return result;
        } finally {
            watch.stop("holdingsItems.getAgenciesThatHasHoldingsForId.MarcRecord");
            logger.exit(result);
        }
    }

    public Set<Integer> getAgenciesThatHasHoldingsForId(String recordId) throws UpdateException {
        logger.entry(recordId);
        logger.info("getAgenciesThatHasHoldingsForId : " + recordId);
        StopWatch watch = new Log4JStopWatch();
        Set<Integer> result = new HashSet<>();
        try (Connection conn = dataSource.getConnection()) {
            HoldingsItemsDAO dao = createDAO(conn);
            result = dao.getAgenciesThatHasHoldingsFor(recordId);
            return result;
        } catch (SQLException | HoldingsItemsException ex) {
            logger.error(ex.getMessage(), ex);
            throw new UpdateException(ex.getMessage(), ex);
        } finally {
            watch.stop("holdingsItems.getAgenciesThatHasHoldingsForId.String");
            logger.exit(result);
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
