/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.common.records.*;
import dk.dbc.updateservice.actions.AssertActionsUtil;
import dk.dbc.updateservice.javascript.Scripter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

public class LibraryRecordsHandlerTest {
    private static final XLogger logger = XLoggerFactory.getXLogger(LibraryRecordsHandlerTest.class);

    @Before
    public void before() throws IOException {

    }

    @Mock
    private Scripter scripter;

    private class MockLibraryRecordsHandler extends LibraryRecordsHandler {
        MockLibraryRecordsHandler() {
            super(scripter);
        }
    }

    @Test
    public void testCleanupEnrichmentRecord() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        LibraryRecordsHandler instance = new MockLibraryRecordsHandler();
        // assertThat(instance.splitRecordDataIO(record), equalTo(""));
    }

    @Test
    public void testSplitCompleteBasisRecord() throws Exception {
        // Prepare record
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.addOrReplaceSubfield("001", "b", RawRepo.COMMON_AGENCY.toString());
        writer.addOrReplaceSubfield("aaa", "a", "b");
        writer.addOrReplaceSubfield("bbb", "a", "b");

        // Prepare expected common record
        MarcRecord expectedCommonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter expectedCommonRecordWriter = new MarcRecordWriter(expectedCommonRecord);
        expectedCommonRecordWriter.addOrReplaceSubfield("001", "b", RawRepo.COMMON_AGENCY.toString());

        // Prepare expected DBC/191919 record
        MarcRecord expectedDBCRecord = new MarcRecord();
        expectedDBCRecord.getFields().add(new MarcField(reader.getField("001")));
        expectedDBCRecord.getFields().add(new MarcField(reader.getField("004")));
        expectedDBCRecord.getFields().add(new MarcField(reader.getField("aaa")));
        expectedDBCRecord.getFields().add(new MarcField(reader.getField("bbb")));
        MarcRecordWriter expectedDBCRecordWriter = new MarcRecordWriter(expectedDBCRecord);
        expectedDBCRecordWriter.addOrReplaceSubfield("001", "b", RawRepo.DBC_ENRICHMENT.toString());

        List<MarcRecord> expectedList = Arrays.asList(expectedCommonRecord, expectedDBCRecord);

        LibraryRecordsHandler instance = new MockLibraryRecordsHandler();
        assertThat(instance.splitRecordDataIO(record, reader.getValue("001", "b")), equalTo(expectedList));
    }

    @Test
    public void testSplitCompleteBasisRecord870971() throws Exception {
        // Prepare record
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.addOrReplaceSubfield("001", "b", "870971");
        writer.addOrReplaceSubfield("aaa", "a", "b");
        writer.addOrReplaceSubfield("bbb", "a", "b");

        // Prepare expected common record
        MarcRecord expectedCommonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter expectedCommonRecordWriter = new MarcRecordWriter(expectedCommonRecord);
        expectedCommonRecordWriter.addOrReplaceSubfield("001", "b", "870971");

        // Prepare expected DBC/191919 record
        MarcRecord expectedDBCRecord = new MarcRecord();
        expectedDBCRecord.getFields().add(new MarcField(reader.getField("001")));
        expectedDBCRecord.getFields().add(new MarcField(reader.getField("004")));
        expectedDBCRecord.getFields().add(new MarcField(reader.getField("aaa")));
        expectedDBCRecord.getFields().add(new MarcField(reader.getField("bbb")));
        MarcRecordWriter expectedDBCRecordWriter = new MarcRecordWriter(expectedDBCRecord);
        expectedDBCRecordWriter.addOrReplaceSubfield("001", "b", RawRepo.DBC_ENRICHMENT.toString());

        List<MarcRecord> expectedList = Arrays.asList(expectedCommonRecord, expectedDBCRecord);

        LibraryRecordsHandler instance = new MockLibraryRecordsHandler();
        assertThat(instance.splitRecordDataIO(record, reader.getValue("001", "b")), equalTo(expectedList));
    }

    @Test
    public void testHasClassificationsChanged() throws Exception {
        MarcRecord oldRecord;
        MarcRecord newRecord;
        logger.info("Enter testHasClassificationsChanged");

        // felt 008
        oldRecord = MarcRecordFactory.readRecord("008 00 *tm");
        newRecord = MarcRecordFactory.readRecord("008 00 *tp");
        LibraryRecordsHandler instance = new MockLibraryRecordsHandler();
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
        oldRecord = MarcRecordFactory.readRecord("008 00 *ts");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
        newRecord = MarcRecordFactory.readRecord("008 00 *ty");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));

        // felt 009
        oldRecord = MarcRecordFactory.readRecord("009 00 *as *bb");
        newRecord = MarcRecordFactory.readRecord("009 00 *as *br");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));
        oldRecord = MarcRecordFactory.readRecord("009 00 *as *bb");
        newRecord = MarcRecordFactory.readRecord("009 00 *as *br*gxx");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
        oldRecord = MarcRecordFactory.readRecord("009 00 *as *bb*gxx");
        newRecord = MarcRecordFactory.readRecord("009 00 *as *br*gxx");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));
        oldRecord = MarcRecordFactory.readRecord("009 00 *as *bb*gxr");
        newRecord = MarcRecordFactory.readRecord("009 00 *as *br*gxx");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
        oldRecord = MarcRecordFactory.readRecord("009 00 *bb*gxr");
        newRecord = MarcRecordFactory.readRecord("009 00 *br*gxx");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
        oldRecord = MarcRecordFactory.readRecord("009 00 *as *bb*gxr*as*gxx");
        newRecord = MarcRecordFactory.readRecord("009 00 *as *br*gxx*as*gxr");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));
        oldRecord = MarcRecordFactory.readRecord("009 00 *as *bb*gxr*as*gxx");
        newRecord = MarcRecordFactory.readRecord("009 00 *as *br*gxx*as*gxy");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));

        // felt 038
        oldRecord = MarcRecordFactory.readRecord("038 00 *aer");
        newRecord = MarcRecordFactory.readRecord("038 00 *aeo");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
        oldRecord = MarcRecordFactory.readRecord("038 00 *aeo");
        newRecord = MarcRecordFactory.readRecord("038 00 *aeo");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));

        // felt 039
        oldRecord = MarcRecordFactory.readRecord("039 00 *afol*btr");
        newRecord = MarcRecordFactory.readRecord("039 00 *afol*bdk");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
        oldRecord = MarcRecordFactory.readRecord("039 00 *afol*btr");
        newRecord = MarcRecordFactory.readRecord("039 00 *bdk*afol");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
        oldRecord = MarcRecordFactory.readRecord("039 00 *afol*btr");
        newRecord = MarcRecordFactory.readRecord("039 00 *afol*btr");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));

        // felt 100
        oldRecord = MarcRecordFactory.readRecord("100 00 *aMarcus Aurelius*fkejser over romerriget");
        newRecord = MarcRecordFactory.readRecord("100 00 *aMarcus Aurelius*fkejser over romerriget");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));
        oldRecord = MarcRecordFactory.readRecord("100 00 *aMarcus Aurelius*E2*eII*fkejser over romerriget");
        newRecord = MarcRecordFactory.readRecord("100 00 *aMarcus Aurelius*fkejser over romerriget");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
        oldRecord = MarcRecordFactory.readRecord("100 00 *aMarcus Aurelius*fkejser over romerriget");
        newRecord = MarcRecordFactory.readRecord("100 00 *aMarcus Aurelius*fkejser over romérriget");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));

        // felt 110
        oldRecord = MarcRecordFactory.readRecord("110 00*aNordiska mejerikongressen*i35*k1989*jReykjavik");
        newRecord = MarcRecordFactory.readRecord("110 00*aNordiska mejerikongressen*i35*k1989*jReykjavik");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));
        oldRecord = MarcRecordFactory.readRecord("110 00*aNordiska feministkongressen*i35*k1989*jReykjavik");
        newRecord = MarcRecordFactory.readRecord("110 00*aNordiska mejerikongressen*i35*k1989*jReykjavik");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));

        // felt 239
        oldRecord = MarcRecordFactory.readRecord("120 00*aNordiska mejerikongressen*i35*k1989*jReykjavik");
        newRecord = MarcRecordFactory.readRecord("239 00*tPieces de viole, 1. livre (Suite for viola da gamba og continuo, A-dur)");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
        oldRecord = MarcRecordFactory.readRecord("239 00*tPieces de viole, 1. livre (Suite for viola da gamba og continuo, A-dur)");
        newRecord = MarcRecordFactory.readRecord("120 00*aNordiska mejerikongressen*i35*k1989*jReykjavik");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));

        oldRecord = MarcRecordFactory.readRecord("239 00*tPieces de viole, 1. livre (Suite for viola da gamba og continuo, A-dur)");
        newRecord = MarcRecordFactory.readRecord("239 00*tPieces de viole, 117. livre (Suite for viola da gamba og continuo, A-dur)");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));
        oldRecord = MarcRecordFactory.readRecord("245 00*aPieces 117. livre (Suite for viola da gamba og continuo, A-dur)");
        newRecord = MarcRecordFactory.readRecord("239 00*tPieces 1. livre (Suite for viola da gamba og continuo, A-dur)");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
        oldRecord = MarcRecordFactory.readRecord("239 00*tPieces 1. livre (Suite for viola da gamba og continuo, A-dur)");
        newRecord = MarcRecordFactory.readRecord("245 00*aPieces 117. livre (Suite for viola da gamba og continuo, A-dur)");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));

        oldRecord = MarcRecordFactory.readRecord("239 00*tPieces de viole, 1. livre (Suite for viola da gamba og continuo, A-dur)");
        newRecord = MarcRecordFactory.readRecord("239 00*tPieces de viole, 1. livre (Suite for viola da gamba og continuo, A-dur)");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));
        oldRecord = MarcRecordFactory.readRecord("239 00*tPieces de viole, 1. livre (Suite for viola da gamba og continuo, A-dur)");
        newRecord = MarcRecordFactory.readRecord("245 00*aPieces de viole, 1. livre (Suite for viola da gamba og continuo, A-dur)");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));
        oldRecord = MarcRecordFactory.readRecord("239 00*aYoung Frankenstein*\u00F8Brady");
        newRecord = MarcRecordFactory.readRecord("239 00*aYoung Frankenstein*\u00F8Lee");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));


        // felt 239 + 245
        oldRecord = MarcRecordFactory.readRecord("239 00*tPieces de viole, 1. livre (Suite for viola da gamba og continuo, A-dur) \n" +
                "245 00*aPiece de viole, 1. livre (Suite for viola da gamba og continuo, A-dur)");
        newRecord = MarcRecordFactory.readRecord("245 00*aPierces de viole, 1. livre (Suite for viola da gamba og continuo, A-dur)");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));


        oldRecord = MarcRecordFactory.readRecord("245 00*n4. Band*aKupperzeit");
        newRecord = MarcRecordFactory.readRecord("239 00*tPieces de viole, 1. livre (Suite for viola da gamba og continuo, A-dur) \n" +
                "004 00*as \n" +
                "245 00*n3. Band*aKupferzeit");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));


        // felt 245
        oldRecord = MarcRecordFactory.readRecord("245 00 *g[Bind] 2");
        newRecord = MarcRecordFactory.readRecord("245 00 *g[Bind] 4");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
        oldRecord = MarcRecordFactory.readRecord("245 00 *g[Bind] 2");
        newRecord = MarcRecordFactory.readRecord("245 00 *g[Bind] 2");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));
        oldRecord = MarcRecordFactory.readRecord("245 00 *g[Bind] 123456789");
        newRecord = MarcRecordFactory.readRecord("245 00 *g[Bind] 1234567890");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));

        oldRecord = MarcRecordFactory.readRecord("245 00 *mDiskette");
        newRecord = MarcRecordFactory.readRecord("245 00 *mPapirform");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
        oldRecord = MarcRecordFactory.readRecord("245 00 *mDiskette");
        newRecord = MarcRecordFactory.readRecord("245 00 *mDiskette");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));

        oldRecord = MarcRecordFactory.readRecord("245 00 *nBand 1");
        newRecord = MarcRecordFactory.readRecord("245 00 *nBand 2");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
        oldRecord = MarcRecordFactory.readRecord("245 00 *nBand 1");
        newRecord = MarcRecordFactory.readRecord("245 00 *nBand 1");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));

        oldRecord = MarcRecordFactory.readRecord("245 00 *oRobinsonader");
        newRecord = MarcRecordFactory.readRecord("245 00 *oRobinsonetter");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
        oldRecord = MarcRecordFactory.readRecord("245 00 *oRobinsonader");
        newRecord = MarcRecordFactory.readRecord("245 00 *oRobinsõnader");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));
        oldRecord = MarcRecordFactory.readRecord("245 00 *oRobinsonader");
        newRecord = MarcRecordFactory.readRecord("245 00 *oRobinsonaderne");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));

        oldRecord = MarcRecordFactory.readRecord("245 00 *ySüpplement");
        newRecord = MarcRecordFactory.readRecord("245 00 *ySupplement");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));
        oldRecord = MarcRecordFactory.readRecord("245 00 *ySupplement");
        newRecord = MarcRecordFactory.readRecord("245 00 *ySupplement");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));
        oldRecord = MarcRecordFactory.readRecord("245 00 *ySupplement");
        newRecord = MarcRecordFactory.readRecord("245 00 *ySupplementerne");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));

    }
}
