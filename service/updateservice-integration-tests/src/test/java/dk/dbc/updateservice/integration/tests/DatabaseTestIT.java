package dk.dbc.updateservice.integration.tests;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import dk.dbc.commons.jdbc.util.JDBCUtil;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author stp
 */
public class DatabaseTestIT {
    
    public DatabaseTestIT() {
    }
    
    @BeforeClass
    public static void setUpClass() throws ClassNotFoundException, SQLException {
        Class.forName("org.postgresql.Driver");
        Connection conn = DriverManager.getConnection(
                String.format("jdbc:postgresql://localhost:%s/%s", System.getProperty( "rawrepo.db.port" ), System.getProperty( "rawrepo.db.name" ) ),
                System.getProperty("user.name"), System.getProperty("user.name") );
        conn.setAutoCommit(true);

        JDBCUtil.update( conn, "INSERT INTO queueworkers(worker) VALUES(?)", "fbssync");
        JDBCUtil.update( conn, "INSERT INTO queuerules(provider, worker, changed, leaf) VALUES(?, ?, ?, ?)", "opencataloging-update", "fbssync", "Y", "A");
    }
    
    @AfterClass
    public static void tearDownClass() throws ClassNotFoundException, SQLException {
        try (final Connection conn = newRawRepoConnection() ) {        
            JDBCUtil.update( conn, "DELETE FROM queuerules");
            JDBCUtil.update( conn, "DELETE FROM queueworkers");
        }
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() throws ClassNotFoundException, SQLException {
        try (final Connection conn = newRawRepoConnection() ) {        
            JDBCUtil.update( conn, "DELETE FROM relations");
            JDBCUtil.update( conn, "DELETE FROM records");
            JDBCUtil.update( conn, "DELETE FROM queue");
        }
    }

    @Test
    public void hello() {
    }
    
    private static Connection newRawRepoConnection() throws ClassNotFoundException, SQLException {
        Class.forName("org.postgresql.Driver");
        Connection conn = DriverManager.getConnection(
                String.format("jdbc:postgresql://localhost:%s/%s", System.getProperty( "rawrepo.db.port" ), System.getProperty( "rawrepo.db.name" ) ),
                System.getProperty("user.name"), System.getProperty("user.name") );
        conn.setAutoCommit(true);
        
        return conn;
    }
    
}
