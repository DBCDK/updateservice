package dk.dbc.updateservice.integration;

import dk.dbc.commons.jdbc.util.JDBCUtil;
import dk.dbc.updateservice.integration.testcase.TestcaseRunner;

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
        this.dir = new File( settings.getProperty( "opencat.business.basedir" ) + "/" +
                             settings.getProperty( "opencat.business.installname.basedir" ) + "/" +
                             settings.getProperty( "opencat.business.testcases.dirname" ) + "/" + dirName );
    }

    public TestcaseRunner createTestcase( String dirName ) throws IOException {
        return new TestcaseRunner( new File( dir.getCanonicalPath() + "/" + dirName ) );
    }

    public void initRawRepoDatabase() throws SQLException, IOException, ClassNotFoundException {
        try (final Connection conn = newRawRepoConnection() ) {
            JDBCUtil.update( conn, "INSERT INTO queueworkers(worker) VALUES(?)", "fbssync");
            JDBCUtil.update( conn, "INSERT INTO queuerules(provider, worker, changed, leaf) VALUES(?, ?, ?, ?)", "opencataloging-update", "fbssync", "Y", "A");
        }
    }

    public void resetRawRepoDatabase() throws SQLException, IOException, ClassNotFoundException {
        try (final Connection conn = newRawRepoConnection()) {
            JDBCUtil.update( conn, "DELETE FROM queuerules");
            JDBCUtil.update( conn, "DELETE FROM queueworkers");
        }
    }

    public void clearRawRepoRecords() throws SQLException, IOException, ClassNotFoundException {
        try (final Connection conn = newRawRepoConnection()) {
            JDBCUtil.update( conn, "DELETE FROM relations" );
            JDBCUtil.update( conn, "DELETE FROM records" );
            JDBCUtil.update( conn, "DELETE FROM queue" );
        }
    }

    public Connection newRawRepoConnection() throws ClassNotFoundException, SQLException, IOException {
        Class.forName( settings.getProperty( "rawrepo.jdbc.driver" ) );
        Connection conn = DriverManager.getConnection(
                String.format( settings.getProperty( "rawrepo.jdbc.conn" ), settings.getProperty( "rawrepo.host" ), settings.getProperty( "rawrepo.port" ), settings.getProperty( "rawrepo.dbname" ) ),
                settings.getProperty( "rawrepo.user.name" ), settings.getProperty( "rawrepo.user.passwd" ) );
        conn.setAutoCommit( true );

        return conn;
    }

    public static Properties loadSettings() throws IOException {
        Properties settings = new Properties();
        settings.load( TestEnvironment.class.getResourceAsStream( "/settings.properties" ) );

        return settings;
    }

    private File dir;
    Properties settings;
}
