/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordFactory;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
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
import static org.hamcrest.MatcherAssert.assertThat;

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
    public void testSplitCompleteBasisRecord() throws Exception {
        // Prepare record
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.addOrReplaceSubfield("001", "b", Integer.toString(RawRepo.COMMON_AGENCY));
        writer.addOrReplaceSubfield("aaa", "a", "b");
        writer.addOrReplaceSubfield("bbb", "a", "b");

        // Prepare expected common record
        MarcRecord expectedCommonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter expectedCommonRecordWriter = new MarcRecordWriter(expectedCommonRecord);
        expectedCommonRecordWriter.addOrReplaceSubfield("001", "b", Integer.toString(RawRepo.COMMON_AGENCY));

        // Prepare expected DBC/191919 record
        MarcRecord expectedDBCRecord = new MarcRecord();
        expectedDBCRecord.getFields().add(new MarcField(reader.getField("001")));
        expectedDBCRecord.getFields().add(new MarcField(reader.getField("004")));
        expectedDBCRecord.getFields().add(new MarcField(reader.getField("aaa")));
        expectedDBCRecord.getFields().add(new MarcField(reader.getField("bbb")));
        MarcRecordWriter expectedDBCRecordWriter = new MarcRecordWriter(expectedDBCRecord);
        expectedDBCRecordWriter.addOrReplaceSubfield("001", "b", Integer.toString(RawRepo.DBC_ENRICHMENT));

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
        expectedDBCRecordWriter.addOrReplaceSubfield("001", "b", Integer.toString(RawRepo.DBC_ENRICHMENT));

        List<MarcRecord> expectedList = Arrays.asList(expectedCommonRecord, expectedDBCRecord);

        LibraryRecordsHandler instance = new MockLibraryRecordsHandler();
        assertThat(instance.splitRecordDataIO(record, reader.getValue("001", "b")), equalTo(expectedList));
    }

    @Test
    public void testHasClassificationsChanged() {
        MarcRecord oldRecord;
        MarcRecord newRecord;
        logger.info("Enter testHasClassificationsChanged");

        String f001DBC = "001 00 *b 870970 \n";
        String f001FBS = "001 00 *b 763000 \n";

        // felt 008
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "008 00 *tm");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "008 00 *tp");
        LibraryRecordsHandler instance = new MockLibraryRecordsHandler();
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "008 00 *ts");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
        newRecord = MarcRecordFactory.readRecord(f001DBC + "008 00 *ty");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));

        // felt 009
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "009 00 *as *bb");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "009 00 *as *br");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "009 00 *as *bb");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "009 00 *as *br*gxx");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "009 00 *as *bb*gxx");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "009 00 *as *br*gxx");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "009 00 *as *bb*gxr");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "009 00 *as *br*gxx");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "009 00 *bb*gxr");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "009 00 *br*gxx");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "009 00 *as *bb*gxr*as*gxx");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "009 00 *as *br*gxx*as*gxr");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "009 00 *as *bb*gxr*as*gxx");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "009 00 *as *br*gxx*as*gxy");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));

        // felt 038
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "038 00 *aer");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "038 00 *aeo");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "038 00 *aeo");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "038 00 *aeo");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));

        // felt 039
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "039 00 *afol*btr");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "039 00 *afol*bdk");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "039 00 *afol*btr");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "039 00 *bdk*afol");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "039 00 *afol*btr");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "039 00 *afol*btr");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));

        // felt 100
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "100 00 *aMarcus Aurelius*fkejser over romerriget");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "100 00 *aMarcus Aurelius*fkejser over romerriget");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "100 00 *aMarcus Aurelius*E2*eII*fkejser over romerriget");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "100 00 *aMarcus Aurelius*fkejser over romerriget");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "100 00 *aMarcus Aurelius*fkejser over romerriget");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "100 00 *aMarcus Aurelius*fkejser over romérriget");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));

        // felt 110
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "110 00*aNordiska mejerikongressen*i35*k1989*jReykjavik");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "110 00*aNordiska mejerikongressen*i35*k1989*jReykjavik");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "110 00*aNordiska feministkongressen*i35*k1989*jReykjavik");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "110 00*aNordiska mejerikongressen*i35*k1989*jReykjavik");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));

        // felt 239
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "120 00*aNordiska mejerikongressen*i35*k1989*jReykjavik");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "239 00*tPieces de viole, 1. livre (Suite for viola da gamba og continuo, A-dur)");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "239 00*tPieces de viole, 1. livre (Suite for viola da gamba og continuo, A-dur)");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "120 00*aNordiska mejerikongressen*i35*k1989*jReykjavik");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));

        oldRecord = MarcRecordFactory.readRecord(f001DBC + "239 00*tPieces de viole, 1. livre (Suite for viola da gamba og continuo, A-dur)");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "239 00*tPieces de viole, 117. livre (Suite for viola da gamba og continuo, A-dur)");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "245 00*aPieces 117. livre (Suite for viola da gamba og continuo, A-dur)");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "239 00*tPieces 1. livre (Suite for viola da gamba og continuo, A-dur)");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "239 00*tPieces 1. livre (Suite for viola da gamba og continuo, A-dur)");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "245 00*aPieces 117. livre (Suite for viola da gamba og continuo, A-dur)");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));

        oldRecord = MarcRecordFactory.readRecord(f001DBC + "239 00*tPieces de viole, 1. livre (Suite for viola da gamba og continuo, A-dur)");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "239 00*tPieces de viole, 1. livre (Suite for viola da gamba og continuo, A-dur)");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "239 00*tPieces de viole, 1. livre (Suite for viola da gamba og continuo, A-dur)");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "245 00*aPieces de viole, 1. livre (Suite for viola da gamba og continuo, A-dur)");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "239 00*aYoung Frankenstein*\u00F8Brady");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "239 00*aYoung Frankenstein*\u00F8Lee");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));

        // felt 239 + 245
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "239 00*tPieces de viole, 1. livre (Suite for viola da gamba og continuo, A-dur) \n" +
                "245 00*aPiece de viole, 1. livre (Suite for viola da gamba og continuo, A-dur)");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "245 00*aPierces de viole, 1. livre (Suite for viola da gamba og continuo, A-dur)");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));

        oldRecord = MarcRecordFactory.readRecord(f001DBC + "245 00*n4. Band*aKupperzeit");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "239 00*tPieces de viole, 1. livre (Suite for viola da gamba og continuo, A-dur) \n" +
                "004 00*as \n" +
                "245 00*n3. Band*aKupferzeit");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));

        // felt 245
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "245 00 *g[Bind] 2");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "245 00 *g[Bind] 4");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "245 00 *g[Bind] 2");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "245 00 *g[Bind] 2");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "245 00 *g[Bind] 123456789");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "245 00 *g[Bind] 1234567890");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));

        oldRecord = MarcRecordFactory.readRecord(f001DBC + "245 00 *mDiskette");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "245 00 *mPapirform");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "245 00 *mDiskette");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "245 00 *mDiskette");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));

        oldRecord = MarcRecordFactory.readRecord(f001DBC + "245 00 *nBand 1");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "245 00 *nBand 2");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "245 00 *nBand 1");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "245 00 *nBand 1");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));

        oldRecord = MarcRecordFactory.readRecord(f001DBC + "245 00 *oRobinsonader");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "245 00 *oRobinsonetter");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "245 00 *oRobinsonader");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "245 00 *oRobinsõnader");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "245 00 *ySüpplement");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "245 00 *ySupplement");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));

        oldRecord = MarcRecordFactory.readRecord(f001DBC + "245 00 *ySupplement");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "245 00 *ySupplement");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "245 00 *ySupplement");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "245 00 *ySupplementerne");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
        oldRecord = MarcRecordFactory.readRecord(f001DBC + "245 00 *a Downton Abbey *ø A journey to the highlands");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "245 00 *a Downton Abbey *ø A journey to the highlands [Sæson 3]");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));

        oldRecord = MarcRecordFactory.readRecord(f001DBC + "245 00 *a Downton Abbey *ø A journey to the highlands");
        newRecord = MarcRecordFactory.readRecord(f001FBS + "245 00 *a Downton Abbey *ø A journey to the highlands [Sæson 3]");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));
    }

    @Test
    public void test652() {
        LibraryRecordsHandler instance = new MockLibraryRecordsHandler();

        MarcRecord oldRecord;
        MarcRecord newRecord;

        String f001DBC = "001 00 *b 870970 \n";

        oldRecord = MarcRecordFactory.readRecord(f001DBC);
        newRecord = MarcRecordFactory.readRecord(f001DBC + "652 00 *m Hejsa");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));

        oldRecord = MarcRecordFactory.readRecord(f001DBC + "654 00 *m Hejsa");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "652 00 *m Hejsa");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));

        oldRecord = MarcRecordFactory.readRecord(f001DBC + "654 00 *o Hejsa");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "652 00 *o Hejsa");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));

        oldRecord = MarcRecordFactory.readRecord(f001DBC + "654 00 *m Hejsa");
        newRecord = MarcRecordFactory.readRecord(f001DBC + "652 00 *o Hejsa");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(true));

        String f652Socialdemokraterne = "652 00 *p 32.269 *a Socialdemokraterne \n";
        String f652NyrupRasmussen = "652 00 *å 1 *m 99.4 *a Nyrup Rasmussen *h Poul \n";

        oldRecord = MarcRecordFactory.readRecord(f001DBC + f652Socialdemokraterne + f652NyrupRasmussen);
        newRecord = MarcRecordFactory.readRecord(f001DBC + f652NyrupRasmussen + f652Socialdemokraterne);
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord), equalTo(false));
    }

}
