package dk.dbc.updateservice.integration.tests;

import dk.dbc.holdingsitems.HoldingsItemsDAO;
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
import java.util.Arrays;
import java.util.List;

/**
 * Created by stp on 25/02/15.
 */
public class DBCSingleRecordsIT {
    public DBCSingleRecordsIT() throws IOException {
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
     * Create a new single record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          An empty rawrepo
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Create a new single record.
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
    public void testNewSingleRecord() throws Exception {
        logger.entry();

        try {
            TestcaseRunner runner = testEnvironment.createTestcase( "tc-01" );
            UpdateRecordResult response = runner.sendRequest();

            runner.checkResponseForUpdateIsOk( response );

            try( Connection conn = testEnvironment.newRawRepoConnection() ) {
                RawRepoDAO dao = RawRepoDAO.newInstance( conn );

                runner.checkRawRepoRecord( dao, "result-common.marc", MarcXChangeMimeType.MARCXCHANGE );
                runner.checkRawRepoRecord( dao, "result-dbc-enrichment.marc", MarcXChangeMimeType.ENRICHMENT );
                runner.checkRawRepoSibling( dao, "result-common.marc", "result-dbc-enrichment.marc" );
            }
        }
        finally {
            logger.exit();
        }
    }

    /**
     * Update an existing single record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          Rawrepo with common and enrichment record for the existing record.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Update the existing record.
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
    public void testUpdateSingleRecord() throws Exception {
        logger.entry();

        try {
            TestcaseRunner runner = testEnvironment.createTestcase( "tc-02" );

            try( Connection conn = testEnvironment.newRawRepoConnection() ) {
                try {
                    RawRepoDAO dao = RawRepoDAO.newInstance( conn );

                    runner.saveRecord( dao, "rawrepo-common.marc", MarcXChangeMimeType.MARCXCHANGE );
                    runner.saveRecord( dao, "rawrepo-dbc-enrichment.marc", MarcXChangeMimeType.ENRICHMENT );
                    runner.linkSibling( dao, "rawrepo-common.marc", "rawrepo-dbc-enrichment.marc" );

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

                runner.checkRawRepoRecord( dao, "result-common.marc", MarcXChangeMimeType.MARCXCHANGE );
                runner.checkRawRepoRecord( dao, "result-dbc-enrichment.marc", MarcXChangeMimeType.ENRICHMENT );
                runner.checkRawRepoSibling( dao, "result-common.marc", "result-dbc-enrichment.marc" );
            }
        }
        finally {
            logger.exit();
        }
    }

    /**
     * Updates a record with no changes to classification data. 3 FBS libraries has holdings
     * for the record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          Rawrepo with the record to update and holdings for the 3 libraries.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Updates the record with no changes to classifications.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          The record is splited into 2 records:
     *          <ul>
     *              <li>A common record with dm2 fields</li>
     *              <li>A DBC enrichment record with DBC fields.</li>
     *          </ul>
     *          No enrichment records are created for FBS libraries
     *      </dd>
     * </dl>
     *
     * @throws Exception
     */
    @Test
    public void testUpdateSingleRecordWithHoldings_NoClassificationChanges() throws Exception {
        logger.entry();

        try {
            TestcaseRunner runner = testEnvironment.createTestcase( "tc-03" );

            try( Connection conn = testEnvironment.newRawRepoConnection() ) {
                try {
                    RawRepoDAO dao = RawRepoDAO.newInstance( conn );

                    runner.saveRecord( dao, "rawrepo-common.marc", MarcXChangeMimeType.MARCXCHANGE );
                    runner.saveRecord( dao, "rawrepo-dbc-enrichment.marc", MarcXChangeMimeType.ENRICHMENT );
                    runner.linkSibling( dao, "rawrepo-common.marc", "rawrepo-dbc-enrichment.marc" );

                    conn.commit();
                }
                catch( SQLException ex ) {
                    conn.rollback();
                    Assert.fail( "Unable to setup records in rawrepo: " + ex.getMessage() );
                }
            }

            List<Integer> agencies = Arrays.asList( 700400, 700500, 700600 );
            try( Connection conn = testEnvironment.newHoldingsConnection() ) {
                try {
                    HoldingsItemsDAO dao = HoldingsItemsDAO.newInstance( conn );

                    runner.addHoldings( dao, "request.marc", agencies );

                    conn.commit();
                }
                catch( SQLException ex ) {
                    conn.rollback();
                    Assert.fail( "Unable to setup records in holdingsitems: " + ex.getMessage() );
                }
            }

            UpdateRecordResult response = runner.sendRequest();

            runner.checkResponseForUpdateIsOk( response );

            try( Connection conn = testEnvironment.newRawRepoConnection() ) {
                RawRepoDAO dao = RawRepoDAO.newInstance( conn );

                runner.checkRawRepoRecord( dao, "result-common.marc", MarcXChangeMimeType.MARCXCHANGE );
                runner.checkRawRepoRecord( dao, "result-dbc-enrichment.marc", MarcXChangeMimeType.ENRICHMENT );
                runner.checkRawRepoSibling( dao, "result-common.marc", "result-dbc-enrichment.marc" );

                for( Integer agency : agencies ) {
                    runner.checkRawRecordDoesNotExist( dao, "result-common.marc", agency );
                }
            }
        }
        finally {
            logger.exit();
        }
    }

    /**
     * Updates a record with changes to classification data. 3 FBS libraries has holdings
     * for the record.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          Rawrepo with the record to update and holdings for the 3 libraries.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Updates the record with changes to classifications.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          The record is splited into 2 records:
     *          <ul>
     *              <li>A common record with dm2 fields</li>
     *              <li>A DBC enrichment record with DBC fields.</li>
     *          </ul>
     *          There are created one enrichment record for each FBS library that has holdings
     *          for the record. Each of these records contains classification data for the
     *          original common record.
     *      </dd>
     * </dl>
     *
     * @throws Exception
     */
    @Test
    public void testUpdateSingleRecordWithHoldings_ClassificationChanges() throws Exception {
        logger.entry();

        try {
            TestcaseRunner runner = testEnvironment.createTestcase( "tc-04" );

            try( Connection conn = testEnvironment.newRawRepoConnection() ) {
                try {
                    RawRepoDAO dao = RawRepoDAO.newInstance( conn );

                    runner.saveRecord( dao, "rawrepo-common.marc", MarcXChangeMimeType.MARCXCHANGE );
                    runner.saveRecord( dao, "rawrepo-dbc-enrichment.marc", MarcXChangeMimeType.ENRICHMENT );
                    runner.linkSibling( dao, "rawrepo-common.marc", "rawrepo-dbc-enrichment.marc" );

                    conn.commit();
                }
                catch( SQLException ex ) {
                    conn.rollback();
                    Assert.fail( "Unable to setup records in rawrepo: " + ex.getMessage() );
                }
            }

            List<Integer> agencies = Arrays.asList( 700400, 700500, 700600 );
            try( Connection conn = testEnvironment.newHoldingsConnection() ) {
                try {
                    HoldingsItemsDAO dao = HoldingsItemsDAO.newInstance( conn );

                    runner.addHoldings( dao, "request.marc", agencies );

                    conn.commit();
                }
                catch( SQLException ex ) {
                    conn.rollback();
                    Assert.fail( "Unable to setup records in holdingsitems: " + ex.getMessage() );
                }
            }

            UpdateRecordResult response = runner.sendRequest();

            runner.checkResponseForUpdateIsOk( response );

            try( Connection conn = testEnvironment.newRawRepoConnection() ) {
                RawRepoDAO dao = RawRepoDAO.newInstance( conn );

                runner.checkRawRepoRecord( dao, "result-common.marc", MarcXChangeMimeType.MARCXCHANGE );
                runner.checkRawRepoRecord( dao, "result-dbc-enrichment.marc", MarcXChangeMimeType.ENRICHMENT );
                runner.checkRawRepoSibling( dao, "result-common.marc", "result-dbc-enrichment.marc" );

                for( Integer agency : agencies ) {
                    String filename = String.format( "result-fbs-%s.marc", agency );
                    runner.checkRawRepoRecord( dao, filename, MarcXChangeMimeType.ENRICHMENT );
                    runner.checkRawRepoSibling( dao, "result-common.marc", filename );
                }
            }
        }
        finally {
            logger.exit();
        }
    }

    private static XLogger logger = XLoggerFactory.getXLogger( DBCSingleRecordsIT.class );

    private static String TEST_ENVIR_NAME = "dbc/single_records";
    private TestEnvironment testEnvironment;
}
