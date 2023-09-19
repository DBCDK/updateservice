package dk.dbc.updateservice.update;

import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.marc.binding.DataField;
import dk.dbc.marc.binding.Leader;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.updateservice.actions.AssertActionsUtil;
import dk.dbc.updateservice.utils.ResourceBundles;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

import static dk.dbc.marc.reader.DanMarc2LineFormatReader.DEFAULT_LEADER;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class LibraryRecordsHandlerTest {
    private static final String f001DBC = "001 00 *b 870970 \n";
    private static final String f001FBS = "001 00 *b 763000 \n";

    private static final ResourceBundle resourceBundle = ResourceBundles.getBundle("actions");

    private static class MockLibraryRecordsHandler extends LibraryRecordsHandler {
        MockLibraryRecordsHandler() {
            super();
        }
    }

    @Test
    void testPerformAction_remove002Notifications() {
        LibraryRecordsHandler instance = new MockLibraryRecordsHandler();
        String content245a = "En [FLYT BEHOLDNING inden 1/1 2028] titel";
        assertThat(instance.remove002Notifications(content245a), is("En titel"));
        content245a = " [FLYT BEHOLDNING inden 1/1 2028]   En titel";
        assertThat(instance.remove002Notifications(content245a), is("En titel"));
        content245a = "En titel[FLYT BEHOLDNING inden 1/1 2028]";
        assertThat(instance.remove002Notifications(content245a), is("En titel"));
        content245a = "[POSTEN NEDLÆGGES inden 1/1 2028] En titel";
        assertThat(instance.remove002Notifications(content245a), is(content245a));

    }
    @Test
    void testSplitCompleteBasisRecord() throws Exception {
        // Prepare record
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.addOrReplaceSubField("001", 'b', Integer.toString(RawRepo.COMMON_AGENCY));
        writer.addOrReplaceSubField("aaa", 'a', "b");
        writer.addOrReplaceSubField("bbb", 'a', "b");

        // Prepare expected common record
        MarcRecord expectedCommonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter expectedCommonRecordWriter = new MarcRecordWriter(expectedCommonRecord);
        expectedCommonRecordWriter.addOrReplaceSubField("001", 'b', Integer.toString(RawRepo.COMMON_AGENCY));

        // Prepare expected DBC/191919 record
        MarcRecord expectedDBCRecord = new MarcRecord().setLeader(new Leader().setData(DEFAULT_LEADER));
        expectedDBCRecord.getFields().add(new DataField(reader.getField("001")));
        expectedDBCRecord.getFields().add(new DataField(reader.getField("004")));
        expectedDBCRecord.getFields().add(new DataField(reader.getField("aaa")));
        expectedDBCRecord.getFields().add(new DataField(reader.getField("bbb")));
        MarcRecordWriter expectedDBCRecordWriter = new MarcRecordWriter(expectedDBCRecord);
        expectedDBCRecordWriter.addOrReplaceSubField("001", 'b', Integer.toString(RawRepo.DBC_ENRICHMENT));

        List<MarcRecord> expectedList = Arrays.asList(expectedCommonRecord, expectedDBCRecord);

        LibraryRecordsHandler instance = new MockLibraryRecordsHandler();
        assertThat(instance.splitRecordDataIO(record, reader.getValue("001", 'b')), is(expectedList));
    }

    @Test
    void testSplitCompleteBasisRecord870971() throws Exception {
        // Prepare record
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordReader reader = new MarcRecordReader(record);
        MarcRecordWriter writer = new MarcRecordWriter(record);
        writer.addOrReplaceSubField("001", 'b', "870971");
        writer.addOrReplaceSubField("aaa", 'a', "b");
        writer.addOrReplaceSubField("bbb", 'a', "b");

        // Prepare expected common record
        MarcRecord expectedCommonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        MarcRecordWriter expectedCommonRecordWriter = new MarcRecordWriter(expectedCommonRecord);
        expectedCommonRecordWriter.addOrReplaceSubField("001", 'b', "870971");

        // Prepare expected DBC/191919 record
        MarcRecord expectedDBCRecord = new MarcRecord().setLeader(new Leader().setData(DEFAULT_LEADER));
        expectedDBCRecord.getFields().add(new DataField(reader.getField("001")));
        expectedDBCRecord.getFields().add(new DataField(reader.getField("004")));
        expectedDBCRecord.getFields().add(new DataField(reader.getField("aaa")));
        expectedDBCRecord.getFields().add(new DataField(reader.getField("bbb")));
        MarcRecordWriter expectedDBCRecordWriter = new MarcRecordWriter(expectedDBCRecord);
        expectedDBCRecordWriter.addOrReplaceSubField("001", 'b', Integer.toString(RawRepo.DBC_ENRICHMENT));

        List<MarcRecord> expectedList = Arrays.asList(expectedCommonRecord, expectedDBCRecord);

        LibraryRecordsHandler instance = new MockLibraryRecordsHandler();
        assertThat(instance.splitRecordDataIO(record, reader.getValue("001", 'b')), is(expectedList));
    }

    @Test
    void testHasClassificationsChanged008() throws UpdateException {
        MarcRecord oldRecord;
        MarcRecord newRecord;

        List<String> classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "008 00 *t m");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "008 00 *t p");
        LibraryRecordsHandler instance = new MockLibraryRecordsHandler();
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(true));
        assertThat(classificationMessages.size(), is(1));
        assertThat(resourceBundle.getString(classificationMessages.get(0)), is("008t er ændret fra m eller s til p"));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "008 00 *t s");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(true));
        assertThat(classificationMessages.size(), is(1));
        assertThat(resourceBundle.getString(classificationMessages.get(0)), is("008t er ændret fra m eller s til p"));

        classificationMessages = new ArrayList<>();
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "008 00 *t y");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(false));
        assertThat(classificationMessages.size(), is(0));
    }

    @Test
    void testHasClassificationsChanged009() throws UpdateException {
        MarcRecord oldRecord;
        MarcRecord newRecord;
        final LibraryRecordsHandler instance = new MockLibraryRecordsHandler();

        List<String> classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "009 00 *a s *b b");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "009 00 *a s *b r");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(false));
        assertThat(classificationMessages.size(), is(0));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "009 00 *a s *b b");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "009 00 *a s *b r *g xx");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(true));
        assertThat(classificationMessages.size(), is(1));
        assertThat(resourceBundle.getString(classificationMessages.get(0)), is("009ag er ændret"));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "009 00 *a s *b b *g xx");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "009 00 *a s *b r *g xx");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(false));
        assertThat(classificationMessages.size(), is(0));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "009 00 *as *b b *g xr");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "009 00 *as *b r *g xx");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(true));
        assertThat(classificationMessages.size(), is(1));
        assertThat(resourceBundle.getString(classificationMessages.get(0)), is("009ag er ændret"));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "009 00 *b b *g xr");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "009 00 *b r *g xx");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(true));
        assertThat(classificationMessages.size(), is(1));
        assertThat(resourceBundle.getString(classificationMessages.get(0)), is("009ag er ændret"));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "009 00 *as *b b *g xr *a s *g xx");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "009 00 *as *b r *g xx *a s *g xr");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(false));
        assertThat(classificationMessages.size(), is(0));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "009 00 *a s *b b *g xr *a s *g xx");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "009 00 *a s *b r *g xx *a s *g xy");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(true));
        assertThat(classificationMessages.size(), is(1));
        assertThat(resourceBundle.getString(classificationMessages.get(0)), is("009ag er ændret"));
    }

    @Test
    void testHasClassificationsChanged038() throws UpdateException {
        MarcRecord oldRecord;
        MarcRecord newRecord;
        final LibraryRecordsHandler instance = new MockLibraryRecordsHandler();

        List<String> classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "038 00 *a er");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "038 00 *a eo");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(true));
        assertThat(classificationMessages.size(), is(1));
        assertThat(resourceBundle.getString(classificationMessages.get(0)), is("038a er ændret"));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "038 00 *a eo");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "038 00 *a eo");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(false));
        assertThat(classificationMessages.size(), is(0));
    }

    @Test
    void testHasClassificationsChanged039() throws UpdateException {
        MarcRecord oldRecord;
        MarcRecord newRecord;
        final LibraryRecordsHandler instance = new MockLibraryRecordsHandler();

        List<String> classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "039 00 *a fol *b tr ");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "039 00 *a fol *b dk ");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(true));
        assertThat(classificationMessages.size(), is(1));
        assertThat(resourceBundle.getString(classificationMessages.get(0)), is("039 er ændret"));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "039 00 *a fol *b tr ");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "039 00 *b dk *a fol ");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(true));
        assertThat(classificationMessages.size(), is(1));
        assertThat(resourceBundle.getString(classificationMessages.get(0)), is("039 er ændret"));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "039 00 *a fol *b tr ");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "039 00 *a fol *b tr ");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(false));
        assertThat(classificationMessages.size(), is(0));
    }

    @Test
    void testHasClassificationsChanged100() throws UpdateException {
        MarcRecord oldRecord;
        MarcRecord newRecord;
        final LibraryRecordsHandler instance = new MockLibraryRecordsHandler();

        List<String> classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "100 00 *a Marcus Aurelius *f kejser over romerriget");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "100 00 *a Marcus Aurelius *f kejser over romerriget");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(false));
        assertThat(classificationMessages.size(), is(0));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "100 00 *a Marcus Aurelius *E 2 *e II *f kejser over romerriget");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "100 00 *a Marcus Aurelius *f kejser over romerriget");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(true));
        assertThat(classificationMessages.size(), is(1));
        assertThat(resourceBundle.getString(classificationMessages.get(0)), is("100 er ændret"));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "100 00 *a Marcus Aurelius *f kejser over romerriget");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "100 00 *a Marcus Aurelius *f kejser over romérriget");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(false));
        assertThat(classificationMessages.size(), is(0));
    }

    @Test
    void testHasClassificationsChanged110() throws UpdateException {
        MarcRecord oldRecord;
        MarcRecord newRecord;
        final LibraryRecordsHandler instance = new MockLibraryRecordsHandler();

        List<String> classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "110 00 *a Nordisk mejerikongressen *i 35 *k 1989 *j Reykjavik ");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "110 00 *a Nordisk mejerikongressen *i 35 *k 1989 *j Reykjavik ");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(false));
        assertThat(classificationMessages.size(), is(0));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "110 00 *a Nordisk feministkongressen *i 35 *k 1989 *j Reykjavik ");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "110 00 *a Nordisk mejerikongressen *i 35 *k 1989 *j Reykjavik ");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(true));
        assertThat(classificationMessages.size(), is(1));
        assertThat(resourceBundle.getString(classificationMessages.get(0)), is("110 er ændret"));
    }

    @Test
    void testHasClassificationsChanged239() throws UpdateException {
        MarcRecord oldRecord;
        MarcRecord newRecord;
        final LibraryRecordsHandler instance = new MockLibraryRecordsHandler();
        List<String> classificationMessages = new ArrayList<>();

        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "239 00 *t Pieces de viole, 1. livre (Suite for viola da gamba og continuo, A-dur)");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "239 00 *t Pieces de viole, 117. livre (Suite for viola da gamba og continuo, A-dur)");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(true));
        assertThat(classificationMessages.size(), is(1));
        assertThat(resourceBundle.getString(classificationMessages.get(0)), is("239 er ændret"));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "239 00 *t Pieces de viole, 1. livre (Suite for viola da gamba og continuo, A-dur)");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "239 00 *t Pieces de viole, 1. livre (Suite for viola da gamba og continuo, A-dur)");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(false));
        assertThat(classificationMessages.size(), is(0));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "239 00 *a Young Frankenstein *ø Brady");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "239 00 *a Young Frankenstein *ø Lee");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(true));
        assertThat(classificationMessages.size(), is(1));
        assertThat(resourceBundle.getString(classificationMessages.get(0)), is("239 er ændret"));
    }

    @Test
    void testHasClassificationsChanged245() throws UpdateException {
        MarcRecord oldRecord;
        MarcRecord newRecord;
        final LibraryRecordsHandler instance = new MockLibraryRecordsHandler();
        List<String> classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "245 00 *g [Bind] 2");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "245 00 *g [Bind] 4");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(true));
        assertThat(classificationMessages.size(), is(1));
        assertThat(resourceBundle.getString(classificationMessages.get(0)), is("245g er ændret"));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "245 00 *g [Bind] 2");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "245 00 *g [Bind] 2");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(false));
        assertThat(classificationMessages.size(), is(0));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "245 00 *g [Bind] 123456789");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "245 00 *g [Bind] 1234567890");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(true));
        assertThat(classificationMessages.size(), is(1));
        assertThat(resourceBundle.getString(classificationMessages.get(0)), is("245g er ændret"));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "245 00 *m Diskette ");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "245 00 *m Papirform ");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(true));
        assertThat(classificationMessages.size(), is(1));
        assertThat(resourceBundle.getString(classificationMessages.get(0)), is("245m er ændret"));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "245 00 *m Diskette ");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "245 00 *m Diskette ");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(false));
        assertThat(classificationMessages.size(), is(0));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "245 00 *n Band 1");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "245 00 *n Band 2");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(true));
        assertThat(classificationMessages.size(), is(1));
        assertThat(resourceBundle.getString(classificationMessages.get(0)), is("245n er ændret"));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "245 00 *n Band 1");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "245 00 *n Band 1");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(false));
        assertThat(classificationMessages.size(), is(0));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "245 00 *o Robinsonader ");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "245 00 *o Robinsonetter ");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(true));
        assertThat(classificationMessages.size(), is(1));
        assertThat(resourceBundle.getString(classificationMessages.get(0)), is("245o er ændret"));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "245 00 *o Robinsonader ");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "245 00 *o Robins õnader");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(false));
        assertThat(classificationMessages.size(), is(0));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "245 00 *y S üpplement");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "245 00 *y Supplement ");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(false));
        assertThat(classificationMessages.size(), is(0));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "245 00 *y Supplement ");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "245 00 *y Supplement ");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(false));
        assertThat(classificationMessages.size(), is(0));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "245 00 *y Supplement ");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "245 00 *y Supplementerne ");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(true));
        assertThat(classificationMessages.size(), is(1));
        assertThat(resourceBundle.getString(classificationMessages.get(0)), is("245y er ændret"));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "245 00 *a Downton Abbey *ø A journey to the highlands");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "245 00 *a Downton Abbey *ø A journey to the highlands [Sæson 3]");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(true));
        assertThat(classificationMessages.size(), is(1));
        assertThat(resourceBundle.getString(classificationMessages.get(0)), is("245ø er ændret"));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "245 00 *a Downton Abbey *ø A journey to the highlands");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001FBS + "245 00 *a Downton Abbey *ø A journey to the highlands [Sæson 3]");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(true));
        assertThat(classificationMessages.size(), is(1));
        assertThat(resourceBundle.getString(classificationMessages.get(0)), is("245ø er ændret"));
    }

    @Test
    void testHasClassificationsChangedSpecial() throws UpdateException {
        MarcRecord oldRecord;
        MarcRecord newRecord;
        final LibraryRecordsHandler instance = new MockLibraryRecordsHandler();

        List<String> classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "120 00 *a Nordisk mejerikongressen *i 35 *k 1989 *j Reykjavik ");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "239 00 *t Pieces de viole, 1. livre (Suite for viola da gamba og continuo, A-dur)");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(true));
        assertThat(classificationMessages.size(), is(1));
        assertThat(resourceBundle.getString(classificationMessages.get(0)), is("239t er ændret"));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "239 00 *t Pieces de viole, 1. livre (Suite for viola da gamba og continuo, A-dur)");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "120 00 *a Nordisk mejerikongressen *i 35 *k 1989 *j Reykjavik ");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(true));
        assertThat(classificationMessages.size(), is(1));
        assertThat(resourceBundle.getString(classificationMessages.get(0)), is("239t er ændret"));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "245 00 *a Pieces 117. livre (Suite for viola da gamba og continuo, A-dur)");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "239 00 *t Pieces 1. livre (Suite for viola da gamba og continuo, A-dur)");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(true));
        assertThat(classificationMessages.size(), is(1));
        assertThat(resourceBundle.getString(classificationMessages.get(0)), is("239t er ændret"));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "239 00 *t Pieces 1. livre (Suite for viola da gamba og continuo, A-dur)");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "245 00 *a Pieces 117. livre (Suite for viola da gamba og continuo, A-dur)");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(true));
        assertThat(classificationMessages.size(), is(1));
        assertThat(resourceBundle.getString(classificationMessages.get(0)), is("239t er ændret"));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "239 00 *t Pieces de viole, 1. livre (Suite for viola da gamba og continuo, A-dur)");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "245 00 *a Pieces de viole, 1. livre (Suite for viola da gamba og continuo, A-dur)");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(false));
        assertThat(classificationMessages.size(), is(0));

        // felt 239 + 245
        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "239 00 *t Pieces de viole, 1. livre (Suite for viola da gamba og continuo, A-dur) \n" +
                "245 00 *a Piece de viole, 1. livre (Suite for viola da gamba og continuo, A-dur)");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "245 00 *a Pierces de viole, 1. livre (Suite for viola da gamba og continuo, A-dur)");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(true));
        assertThat(classificationMessages.size(), is(1));
        assertThat(resourceBundle.getString(classificationMessages.get(0)), is("239t er ændret"));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "245 00 *n 4 . Band *a Kupperzeit ");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "239 00 *t Pieces de viole, 1. livre (Suite for viola da gamba og continuo, A-dur) \n" +
                "004 00 *a s  \n" +
                "245 00 *n 3. Band *a Kupferzeit ");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(true));
        assertThat(classificationMessages.size(), is(2));
        assertThat(resourceBundle.getString(classificationMessages.get(0)), is("239t er ændret"));
        assertThat(resourceBundle.getString(classificationMessages.get(1)), is("245n er ændret"));
    }

    @Test
    void test652() throws UpdateException {
        final LibraryRecordsHandler instance = new MockLibraryRecordsHandler();

        MarcRecord oldRecord;
        MarcRecord newRecord;

        final String f001DBC = "001 00 *b 870970 \n";

        List<String> classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC);
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "652 00 *m Hejsa");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(true));
        assertThat(classificationMessages.size(), is(1));
        assertThat(resourceBundle.getString(classificationMessages.get(0)), is("652m er ændret"));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "654 00 *m Hejsa");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "652 00 *m Hejsa");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(false));
        assertThat(classificationMessages.size(), is(0));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "654 00 *o Hejsa");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "652 00 *o Hejsa");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(false));
        assertThat(classificationMessages.size(), is(0));

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "654 00 *m Hejsa");
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + "652 00 *o Hejsa");
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(false));
        assertThat(classificationMessages.size(), is(0));

        final String f652Socialdemokraterne = "652 00 *p 32.269 *a Socialdemokraterne \n";
        final String f652NyrupRasmussen = "652 00 *å 1 *m 99.4 *a Nyrup Rasmussen *h Poul \n";

        classificationMessages = new ArrayList<>();
        oldRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + f652Socialdemokraterne + f652NyrupRasmussen);
        newRecord = UpdateRecordContentTransformer.readRecordFromString(f001DBC + f652NyrupRasmussen + f652Socialdemokraterne);
        assertThat(instance.hasClassificationsChanged(oldRecord, newRecord, classificationMessages), is(false));
        assertThat(classificationMessages.size(), is(0));
    }

}
