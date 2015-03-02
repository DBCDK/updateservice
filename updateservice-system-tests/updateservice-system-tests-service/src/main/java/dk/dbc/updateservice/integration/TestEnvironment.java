package dk.dbc.updateservice.integration;

import dk.dbc.commons.jdbc.util.JDBCUtil;
import dk.dbc.updateservice.integration.testcase.TestcaseRunner;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Created by stp on 25/02/15.
 */
public class TestEnvironment {
    public TestEnvironment( String dirName ) throws IOException {
        this.settings = loadSettings();
        this.dirName = dirName;
        this.dir = new File( settings.getProperty( "opencat.business.basedir" ) + "/" +
                             settings.getProperty( "opencat.business.installname.basedir" ) + "/" +
                             settings.getProperty( "opencat.business.testcases.dirname" ) + "/" + dirName );
    }

    public TestcaseRunner createTestcase( String testcaseDirName ) throws IOException {
        logger.entry();

        try {
            String trackingId = this.dirName + "/" + testcaseDirName;
            trackingId = trackingId.replaceAll( "(/)", "_" );
            logger.debug( "Tracking id for new testcase: {}", trackingId );

            return new TestcaseRunner( new File( dir.getCanonicalPath() + "/" + testcaseDirName ), trackingId );
        }
        finally {
            logger.exit();
        }
    }

    public void initRawRepoDatabase() throws SQLException, IOException, ClassNotFoundException {
        try (final Connection conn = newRawRepoConnection() ) {
            try {
                JDBCUtil.update( conn, "INSERT INTO queueworkers(worker) VALUES(?)", "fbssync");
                JDBCUtil.update( conn, "INSERT INTO queuerules(provider, worker, changed, leaf) VALUES(?, ?, ?, ?)", "opencataloging-update", "fbssync", "Y", "A");

                conn.commit();
            }
            catch( SQLException ex ) {
                conn.rollback();
                logger.error( ex.getMessage(), ex );
            }
        }

        try (final Connection conn = newHoldingsConnection() ) {
            try {
                JDBCUtil.update( conn, "INSERT INTO queueworkers (worker) VALUES(?)", "solr-sync");
                JDBCUtil.update( conn, "INSERT INTO queueworkers (worker) VALUES(?)", "lokreg-sync");
                JDBCUtil.update( conn, "INSERT INTO queuerules (provider, worker) VALUES(?, ?)", "holdings-items-update", "solr-sync" );
                JDBCUtil.update( conn, "INSERT INTO queuerules (provider, worker) VALUES(?, ?)", "holdings-items-update", "lokreg-sync" );

                conn.commit();
            }
            catch( SQLException ex ) {
                conn.rollback();
                logger.error( ex.getMessage(), ex );
            }
        }
    }

    public void resetRawRepoDatabase() throws SQLException, IOException, ClassNotFoundException {
        try (final Connection conn = newRawRepoConnection()) {
            try {
                JDBCUtil.update( conn, "DELETE FROM queuerules");
                JDBCUtil.update( conn, "DELETE FROM queueworkers");

                conn.commit();
            }
            catch( SQLException ex ) {
                conn.rollback();
                logger.error( ex.getMessage(), ex );
            }
        }

        try (final Connection conn = newHoldingsConnection() ) {
            try {
                JDBCUtil.update( conn, "DELETE FROM queuerules");
                JDBCUtil.update( conn, "DELETE FROM queueworkers");

                conn.commit();
            }
            catch( SQLException ex ) {
                conn.rollback();
                logger.error( ex.getMessage(), ex );
            }
        }
    }

    public void clearRawRepoRecords() throws SQLException, IOException, ClassNotFoundException {
        try (final Connection conn = newRawRepoConnection()) {
            try {
                JDBCUtil.update( conn, "DELETE FROM relations" );
                JDBCUtil.update( conn, "DELETE FROM records" );
                JDBCUtil.update( conn, "DELETE FROM queue" );

                conn.commit();
            }
            catch( SQLException ex ) {
                conn.rollback();
                logger.error( ex.getMessage(), ex );
            }
        }

        try (final Connection conn = newHoldingsConnection()) {
            try {
                JDBCUtil.update( conn, "DELETE FROM holdingsitemsitem" );
                JDBCUtil.update( conn, "DELETE FROM holdingsitemscollection" );

                conn.commit();
            }
            catch( SQLException ex ) {
                conn.rollback();
                logger.error( ex.getMessage(), ex );
            }
        }
    }

    public Connection newRawRepoConnection() throws ClassNotFoundException, SQLException, IOException {
        Class.forName( settings.getProperty( "rawrepo.jdbc.driver" ) );
        String connString = String.format( settings.getProperty( "rawrepo.jdbc.conn" ),
                                           settings.getProperty( "rawrepo.host" ),
                                           settings.getProperty( "rawrepo.port" ),
                                           settings.getProperty( "rawrepo.dbname" ) );

        Connection conn = DriverManager.getConnection( connString,
                                                       settings.getProperty( "rawrepo.user.name" ),
                                                       settings.getProperty( "rawrepo.user.passwd" ) );
        conn.setAutoCommit( false );

        return conn;
    }

    public Connection newHoldingsConnection() throws ClassNotFoundException, SQLException, IOException {
        Class.forName( settings.getProperty( "holdingitems.jdbc.driver" ) );
        String connString = String.format( settings.getProperty( "holdingitems.jdbc.conn" ),
                                           settings.getProperty( "holdingitems.host" ),
                                           settings.getProperty( "holdingitems.port" ),
                                           settings.getProperty( "holdingitems.dbname" ) );

        Connection conn = DriverManager.getConnection( connString,
                                                       settings.getProperty( "holdingitems.user.name" ),
                                                       settings.getProperty( "holdingitems.user.passwd" ) );
        conn.setAutoCommit( false );

        return conn;
    }

    public static Properties loadSettings() throws IOException {
        Properties settings = new Properties();
        settings.load( TestEnvironment.class.getResourceAsStream( "/settings.properties" ) );

        return settings;
    }

    private static XLogger logger = XLoggerFactory.getXLogger( TestEnvironment.class );

    private File dir;
    private String dirName;
    Properties settings;
}
