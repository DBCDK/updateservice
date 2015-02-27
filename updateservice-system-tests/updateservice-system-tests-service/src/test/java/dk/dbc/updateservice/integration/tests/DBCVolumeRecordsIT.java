package dk.dbc.updateservice.integration.tests;

import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.RawRepoDAO;
import dk.dbc.updateservice.integration.TestEnvironment;
import dk.dbc.updateservice.integration.service.UpdateRecordResult;
import dk.dbc.updateservice.integration.testcase.TestcaseRunner;
import org.junit.*;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Created by stp on 27/02/15.
 */
public class DBCVolumeRecordsIT {
    public DBCVolumeRecordsIT() throws IOException {
        this.testEnvironment = new TestEnvironment( TEST_ENVIR_NAME );
    }

    @BeforeClass
    public static void setUpClass() throws ClassNotFoundException, SQLException, IOException {
        logger.entry();

        try {
            new TestEnvironment( TEST_ENVIR_NAME ).initRawRepoDatabase();
        }
        finally {
            logger.exit();
        }
    }

    @AfterClass
    public static void tearDownClass() throws IOException, SQLException, ClassNotFoundException {
        logger.entry();

        try {
            new TestEnvironment( TEST_ENVIR_NAME ).resetRawRepoDatabase();
        }
        finally {
            logger.exit();
        }
    }

    @After
    public void clearRawRepo() throws SQLException, ClassNotFoundException, IOException {
        logger.entry();

        try {
            testEnvironment.clearRawRepoRecords();
        }
        finally {
            logger.exit();
        }
    }

    /**
     * Creates a new volume record and connects to an existing main record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          Rawrepo with common and enrichment record for the existing main record.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Create a new volume record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          The record is splited into 2 records:
     *          <ul>
     *              <li>A common record with dm2 fields</li>
     *              <li>A DBC enrichment record with DBC fields.</li>
     *          </ul>
     *      </dd>
     * </dl>
     *
     * @throws Exception
     */
    @Test
    public void testCreateVolumeRecord() throws Exception {
        logger.entry();

        try {
            TestcaseRunner runner = testEnvironment.createTestcase( "tc-01" );

            try( Connection conn = testEnvironment.newRawRepoConnection() ) {
                try {
                    RawRepoDAO dao = RawRepoDAO.newInstance( conn );

                    runner.saveRecord( dao, "rawrepo-main.marc", MarcXChangeMimeType.MARCXCHANGE );
                    runner.saveRecord( dao, "rawrepo-dbc-enrichment.marc", MarcXChangeMimeType.ENRICHMENT );
                    runner.linkSibling( dao, "rawrepo-main.marc", "rawrepo-dbc-enrichment.marc" );

                    conn.commit();
                }
                catch( SQLException ex ) {
                    conn.rollback();
                    Assert.fail( "Unable to setup records in rawrepo: " + ex.getMessage() );
                }
            }

            UpdateRecordResult response = runner.sendRequest();
            runner.checkResponseForUpdateIsOk( response );

            try( Connection conn = testEnvironment.newRawRepoConnection() ) {
                RawRepoDAO dao = RawRepoDAO.newInstance( conn );

                runner.checkRawRepoRecord( dao, "rawrepo-main.marc", MarcXChangeMimeType.MARCXCHANGE );
                runner.checkRawRepoRecord( dao, "rawrepo-dbc-enrichment.marc", MarcXChangeMimeType.ENRICHMENT );
                runner.checkRawRepoSibling( dao, "rawrepo-main.marc", "rawrepo-dbc-enrichment.marc" );

                runner.checkRawRepoRecord( dao, "result-volume.marc", MarcXChangeMimeType.MARCXCHANGE );
                runner.checkRawRepoRecord( dao, "result-volume-dbc-enrichment.marc", MarcXChangeMimeType.ENRICHMENT );
                runner.checkRawRepoSibling( dao, "result-volume.marc", "result-volume-dbc-enrichment.marc" );

                runner.checkRawRepoChildren( dao, "rawrepo-main.marc", "result-volume.marc" );
            }
        }
        finally {
            logger.exit();
        }
    }

    /**
     * Update a volume record and connects to an existing main record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          Rawrepo with common/volume and enrichment records for the existing main/volume records.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update the volume record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          The record is splited into 2 records:
     *          <ul>
     *              <li>A common record with dm2 fields</li>
     *              <li>A DBC enrichment record with DBC fields.</li>
     *          </ul>
     *      </dd>
     * </dl>
     *
     * @throws Exception
     */
    @Test
    public void testUpdateVolumeRecord() throws Exception {
        logger.entry();

        try {
            TestcaseRunner runner = testEnvironment.createTestcase( "tc-02" );

            try( Connection conn = testEnvironment.newRawRepoConnection() ) {
                try {
                    RawRepoDAO dao = RawRepoDAO.newInstance( conn );

                    runner.saveRecord( dao, "rawrepo-main.marc", MarcXChangeMimeType.MARCXCHANGE );
                    runner.saveRecord( dao, "rawrepo-main-dbc-enrichment.marc", MarcXChangeMimeType.ENRICHMENT );
                    runner.linkSibling( dao, "rawrepo-main.marc", "rawrepo-main-dbc-enrichment.marc" );

                    runner.saveRecord( dao, "rawrepo-volume.marc", MarcXChangeMimeType.MARCXCHANGE );
                    runner.saveRecord( dao, "rawrepo-volume-dbc-enrichment.marc", MarcXChangeMimeType.ENRICHMENT );
                    runner.linkSibling( dao, "rawrepo-volume.marc", "rawrepo-volume-dbc-enrichment.marc" );

                    runner.linkChildren( dao, "rawrepo-main.marc", "rawrepo-volume.marc" );

                    conn.commit();
                }
                catch( SQLException ex ) {
                    conn.rollback();
                    Assert.fail( "Unable to setup records in rawrepo: " + ex.getMessage() );
                }
            }

            UpdateRecordResult response = runner.sendRequest();
            runner.checkResponseForUpdateIsOk( response );

            try( Connection conn = testEnvironment.newRawRepoConnection() ) {
                RawRepoDAO dao = RawRepoDAO.newInstance( conn );

                runner.checkRawRepoRecord( dao, "rawrepo-main.marc", MarcXChangeMimeType.MARCXCHANGE );
                runner.checkRawRepoRecord( dao, "rawrepo-main-dbc-enrichment.marc", MarcXChangeMimeType.ENRICHMENT );
                runner.checkRawRepoSibling( dao, "rawrepo-main.marc", "rawrepo-main-dbc-enrichment.marc" );

                runner.checkRawRepoRecord( dao, "result-volume.marc", MarcXChangeMimeType.MARCXCHANGE );
                runner.checkRawRepoRecord( dao, "result-volume-dbc-enrichment.marc", MarcXChangeMimeType.ENRICHMENT );
                runner.checkRawRepoSibling( dao, "result-volume.marc", "result-volume-dbc-enrichment.marc" );

                runner.checkRawRepoChildren( dao, "rawrepo-main.marc", "result-volume.marc" );
            }
        }
        finally {
            logger.exit();
        }
    }

    private static XLogger logger = XLoggerFactory.getXLogger( DBCVolumeRecordsIT.class );

    private static String TEST_ENVIR_NAME = "dbc/volume_records";
    private TestEnvironment testEnvironment;
}
