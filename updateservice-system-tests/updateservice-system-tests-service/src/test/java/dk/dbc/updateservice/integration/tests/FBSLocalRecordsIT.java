package dk.dbc.updateservice.integration.tests;

import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.RawRepoDAO;
import dk.dbc.updateservice.integration.TestEnvironment;
import dk.dbc.updateservice.integration.testcase.TestcaseRunner;
import dk.dbc.updateservice.service.api.UpdateRecordResult;
import org.junit.*;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Created by stp on 27/02/15.
 */
public class FBSLocalRecordsIT {
    public FBSLocalRecordsIT() throws IOException {
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
     * Creates a new local record in a rawrepo with existing records.
     *
     * <dl>
     *      <dt>Given</dt>
     *      <dd>
     *          Rawrepo with common and DBC enrichment record.
     *      </dd>
     *      <dt>When</dt>
     *      <dd>
     *          Create a new local record.
     *      </dd>
     *      <dt>Then</dt>
     *      <dd>
     *          The new record is added but not linked to the common record. The common record and
     *          DBC enrichment record is not changed.
     *      </dd>
     * </dl>
     *
     * @throws Exception
     */
    @Test
    public void testNewRecord() throws Exception {
        logger.entry();

        try {
            TestcaseRunner runner = testEnvironment.createTestcase( "tc-01" );

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

                runner.checkRawRepoRecord( dao, "rawrepo-common.marc", MarcXChangeMimeType.MARCXCHANGE );
                runner.checkRawRepoRecord( dao, "rawrepo-dbc-enrichment.marc", MarcXChangeMimeType.ENRICHMENT );
                runner.checkRawRepoSibling( dao, "rawrepo-common.marc", "rawrepo-dbc-enrichment.marc" );

                runner.checkRawRepoRecord( dao, "request.marc", MarcXChangeMimeType.DECENTRAL );
                runner.checkRawRepoNoSibling( dao, "rawrepo-common.marc", "request.marc" );
            }
        }
        finally {
            logger.exit();
        }
    }

    private static XLogger logger = XLoggerFactory.getXLogger( FBSLocalRecordsIT.class );

    private static String TEST_ENVIR_NAME = "fbs/local_records";
    private TestEnvironment testEnvironment;
}
