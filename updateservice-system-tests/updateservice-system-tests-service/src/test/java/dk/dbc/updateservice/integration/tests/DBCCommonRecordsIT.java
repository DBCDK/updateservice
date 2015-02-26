package dk.dbc.updateservice.integration.tests;

import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.RawRepoDAO;
import dk.dbc.updateservice.integration.TestEnvironment;
import dk.dbc.updateservice.integration.service.UpdateRecordResult;
import dk.dbc.updateservice.integration.testcase.TestcaseRunner;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Created by stp on 25/02/15.
 */
public class DBCCommonRecordsIT {
    public DBCCommonRecordsIT() throws IOException {
        this.testEnvironment = new TestEnvironment( TEST_ENVIR_NAME );
    }

    @BeforeClass
    public static void setUpClass() throws ClassNotFoundException, SQLException, IOException {
        new TestEnvironment( TEST_ENVIR_NAME ).initRawRepoDatabase();
    }

    @AfterClass
    public static void tearDownClass() throws IOException, SQLException, ClassNotFoundException {
        new TestEnvironment( TEST_ENVIR_NAME ).resetRawRepoDatabase();
    }

    @After
    public void clearRawRepo() throws SQLException, ClassNotFoundException, IOException {
        testEnvironment.clearRawRepoRecords();
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

    private static String TEST_ENVIR_NAME = "dbc/common_records";
    private TestEnvironment testEnvironment;
}
