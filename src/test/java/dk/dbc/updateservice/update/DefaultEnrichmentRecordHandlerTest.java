package dk.dbc.updateservice.update;


import dk.dbc.marc.binding.DataField;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.marc.binding.SubField;
import dk.dbc.updateservice.utils.ResourceBundles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class DefaultEnrichmentRecordHandlerTest {

    private final ResourceBundle bundle = ResourceBundles.getBundle("actions");

    @Test
    void testCollectProductionCodesEmpty() {
        final List<DataField> fields = new ArrayList<>();
        final MarcRecord record = new MarcRecord().addAllFields(fields);

        final List<String> expected = new ArrayList<>();

        assertThat(DefaultEnrichmentRecordHandler.collectProductionCodes(record), is(expected));
    }

    @Test
    void testCollectProductionCodes() {
        DataField f001 = new DataField("001", "00");
        f001.getSubFields().add(new SubField('a', "12345678"));
        f001.getSubFields().add(new SubField('b', "870970"));

        DataField f032 = new DataField("032", "00");
        f032.getSubFields().add(new SubField('a', ""));
        f032.getSubFields().add(new SubField('a', "ABC"));
        f032.getSubFields().add(new SubField('a', " DBF"));
        f032.getSubFields().add(new SubField('a', "DBF999999"));
        f032.getSubFields().add(new SubField('x', ""));
        f032.getSubFields().add(new SubField('x', "ABC"));
        f032.getSubFields().add(new SubField('x', " DBF"));
        f032.getSubFields().add(new SubField('x', "DBF999999"));

        MarcRecord record = new MarcRecord();
        record.getFields().add(f001);
        record.getFields().add(f032);

        List<String> expected = new ArrayList<>();
        expected.add("a:DBF999999");
        expected.add("x:DBF999999");

        assertThat(DefaultEnrichmentRecordHandler.collectProductionCodes(record), is(expected));
    }

    @Test
    void testMatchCatCodesSingleMatch() {
        MarcRecord record1 = new MarcRecord();
        record1.getFields().add(new DataField("032", "00").addAllSubFields(Collections.singletonList(new SubField('x', "DBF999999"))));

        MarcRecord record2 = new MarcRecord();
        record2.getFields().add(new DataField("032", "00").addAllSubFields(Collections.singletonList(new SubField('x', "DBF999999"))));

        assertThat(DefaultEnrichmentRecordHandler.matchKatCodes(record1, record2), is(true));
    }

    @Test
    void testMatchCatCodesDoubleMatchReverseOrder() {
        MarcRecord record1 = new MarcRecord();
        record1.getFields().add(new DataField("032", "00").addAllSubFields(Arrays.asList(new SubField('a', "DBI999999"), new SubField('x', "DBF999999"))));

        MarcRecord record2 = new MarcRecord();
        record2.getFields().add(new DataField("032", "00").addAllSubFields(Arrays.asList(new SubField('x', "DBF999999"), new SubField('a', "DBI999999"))));

        assertThat(DefaultEnrichmentRecordHandler.matchKatCodes(record1, record2), is(true));
    }

    @Test
    void testMatchCatCodesNoMatchDiffentSizes() {
        MarcRecord record1 = new MarcRecord();
        record1.getFields().add(new DataField("032", "00").addAllSubFields(Collections.singletonList(new SubField('x', "DBF999999"))));

        MarcRecord record2 = new MarcRecord();
        record2.getFields().add(new DataField("032", "00").addAllSubFields(Arrays.asList(new SubField('x', "DBF999999"), new SubField('a', "DBI999999"))));

        assertThat(DefaultEnrichmentRecordHandler.matchKatCodes(record1, record2), is(false));
    }

    @Test
    void testMatchCatCodesNoMatchSameSizes() {
        MarcRecord record1 = new MarcRecord();
        record1.getFields().add(new DataField("032", "00").addAllSubFields(Collections.singletonList(new SubField('x', "DBF999999"))));

        MarcRecord record2 = new MarcRecord();
        record2.getFields().add(new DataField("032", "00").addAllSubFields(Collections.singletonList(new SubField('a', "DBI999999"))));

        assertThat(DefaultEnrichmentRecordHandler.matchKatCodes(record1, record2), is(false));
    }

    @Test
    void testShouldCreateEnrichmentRecordsOk() {
        MarcRecord updatingCommonRecord = new MarcRecord();
        updatingCommonRecord.getFields().add(new DataField("032", "00").addAllSubFields(Arrays.asList(new SubField('a', "DBI201542"), new SubField('x', "DBF201542"))));

        MarcRecord currentCommonRecord = new MarcRecord();
        currentCommonRecord.getFields().add(new DataField("032", "00").addAllSubFields(Arrays.asList(new SubField('a', "DBI201541"), new SubField('x', "DBF201541"))));
        currentCommonRecord.getFields().add(new DataField("652", "00").addAllSubFields(Collections.singletonList(new SubField('m', "Grydesteg"))));

        assertThat(DefaultEnrichmentRecordHandler.shouldCreateEnrichmentRecordsResult(bundle, updatingCommonRecord, currentCommonRecord), is(true));
    }

    @Test
    void testShouldCreateEnrichmentRecordsFailNyTitle() {
        MarcRecord updatingCommonRecord = new MarcRecord();
        updatingCommonRecord.getFields().add(new DataField("032", "00").addAllSubFields(Arrays.asList(new SubField('a', "DBI201542"), new SubField('x', "DBF201542"))));

        MarcRecord currentCommonRecord = new MarcRecord();
        currentCommonRecord.getFields().add(new DataField("032", "00").addAllSubFields(Arrays.asList(new SubField('a', "DBI201541"), new SubField('x', "DBF201541"))));
        currentCommonRecord.getFields().add(new DataField("652", "00").addAllSubFields(Collections.singletonList(new SubField('m', "ny titel"))));

        assertThat(DefaultEnrichmentRecordHandler.shouldCreateEnrichmentRecordsResult(bundle, updatingCommonRecord, currentCommonRecord), is(false));
    }

    @Test
    void testShouldCreateEnrichmentRecordsFail032a() {
        MarcRecord updatingCommonRecord = new MarcRecord();
        updatingCommonRecord.getFields().add(new DataField("032", "00").addAllSubFields(Arrays.asList(new SubField('a', "DBI999999"), new SubField('x', "DBF201542"))));

        MarcRecord currentCommonRecord = new MarcRecord();
        currentCommonRecord.getFields().add(new DataField("032", "00").addAllSubFields(Arrays.asList(new SubField('a', "DBI201541"), new SubField('x', "DBF201541"))));
        currentCommonRecord.getFields().add(new DataField("652", "00").addAllSubFields(Collections.singletonList(new SubField('m', "Grydesteg"))));

        assertThat(DefaultEnrichmentRecordHandler.shouldCreateEnrichmentRecordsResult(bundle, updatingCommonRecord, currentCommonRecord), is(false));
    }

    @Test
    void testShouldCreateEnrichmentRecordsFail032x() {
        MarcRecord updatingCommonRecord = new MarcRecord();
        updatingCommonRecord.getFields().add(new DataField("032", "00").addAllSubFields(Arrays.asList(new SubField('a', "DBI201542"), new SubField('x', "DBF999999"))));

        MarcRecord currentCommonRecord = new MarcRecord();
        currentCommonRecord.getFields().add(new DataField("032", "00").addAllSubFields(Arrays.asList(new SubField('a', "DBI201541"), new SubField('x', "DBF201541"))));
        currentCommonRecord.getFields().add(new DataField("652", "00").addAllSubFields(Collections.singletonList(new SubField('m', "Grydesteg"))));

        assertThat(DefaultEnrichmentRecordHandler.shouldCreateEnrichmentRecordsResult(bundle, updatingCommonRecord, currentCommonRecord), is(false));
    }

    @Test
    void testShouldCreateEnrichmentRecordsFailUnderProduction() {
        MarcRecord updatingCommonRecord = new MarcRecord();
        updatingCommonRecord.getFields().add(new DataField("032", "00").addAllSubFields(Collections.singletonList(new SubField('a', "DBI211542"))));

        MarcRecord currentCommonRecord = new MarcRecord();
        currentCommonRecord.getFields().add(new DataField("032", "00").addAllSubFields(Collections.singletonList(new SubField('a', "DBI211541"))));
        currentCommonRecord.getFields().add(new DataField("652", "00").addAllSubFields(Collections.singletonList(new SubField('m', "Grydesteg"))));

        assertThat(DefaultEnrichmentRecordHandler.shouldCreateEnrichmentRecordsResult(bundle, updatingCommonRecord, currentCommonRecord), is(false));
    }

    @ParameterizedTest
    @CsvSource({"DBI211542, DBI211541, true",
            "DBI211542, DBI211542, false",
            "DBI211642, DBI999999, true",
    })
    void testShouldCreateEnrichmentRecordsOkUnderProduction008ru(String updating032a, String current032a, boolean expected) {
        MarcRecord updatingCommonRecord = new MarcRecord();
        updatingCommonRecord.getFields().add(new DataField("008", "00").addAllSubFields(Collections.singletonList(new SubField('u', "r"))));
        updatingCommonRecord.getFields().add(new DataField("032", "00").addAllSubFields(Collections.singletonList(new SubField('a', updating032a))));

        MarcRecord currentCommonRecord = new MarcRecord();
        currentCommonRecord.getFields().add(new DataField("032", "00").addAllSubFields(Collections.singletonList(new SubField('a', current032a))));
        currentCommonRecord.getFields().add(new DataField("652", "00").addAllSubFields(Collections.singletonList(new SubField('m', "Grydesteg"))));

        assertThat(DefaultEnrichmentRecordHandler.shouldCreateEnrichmentRecordsResult(bundle, updatingCommonRecord, currentCommonRecord), is(expected));
    }

    @ParameterizedTest
    @CsvSource({"DBC, 123456, true",
            "800010, 123456, true",
            "654321, 123456, false",
    })
    void testDecentralOwnerChange(String updatingOwner, String currentOwner, boolean expected) {
        final MarcRecord updatingCommonRecord = new MarcRecord();
        updatingCommonRecord.getFields().add(new DataField("032", "00").addAllSubFields(Collections.singletonList(new SubField('a', "DBI211542"))));
        updatingCommonRecord.getFields().add(new DataField("996", "00").addAllSubFields(Collections.singletonList(new SubField('a', updatingOwner))));

        final MarcRecord currentCommonRecord = new MarcRecord();
        currentCommonRecord.getFields().add(new DataField("996", "00").addAllSubFields(Collections.singletonList(new SubField('a', currentOwner))));

        assertThat(DefaultEnrichmentRecordHandler.shouldCreateEnrichmentRecordsResult(bundle, updatingCommonRecord, currentCommonRecord), is(expected));
    }

    @Test
    void testHasMinusEnrichmentHasz98WithMinusEnrichment() throws UpdateException {
        final String record = "001 00 *a 19257355 *b 870979 *c 20181211090242 *d 20171102 *f a\n" +
                "996 00 *a DBCAUT\n" +
                "z98 00 *a Minus korrekturprint\n" +
                "z98 00 *b Minus påhængspost";

        assertThat(DefaultEnrichmentRecordHandler.hasMinusEnrichment(UpdateRecordContentTransformer.readRecordFromString(record)), is(true));
    }

    @Test
    void testHasMinusEnrichmentHasz98WithoutMinusEnrichment() throws UpdateException {
        final String record = "001 00 *a 19257355 *b 870979 *c 20181211090242 *d 20171102 *f a\n" +
                "996 00 *a DBCAUT\n" +
                "z98 00 *a minus korrekturprint";

        assertThat(DefaultEnrichmentRecordHandler.hasMinusEnrichment(UpdateRecordContentTransformer.readRecordFromString(record)), is(false));
    }

    @Test
    void testHasMinusEnrichmentHasNoz98() throws UpdateException {
        final String record = "001 00 *a 19257355 *b 870979 *c 20181211090242 *d 20171102 *f a\n" +
                "996 00 *a DBCAUT";

        assertThat(DefaultEnrichmentRecordHandler.hasMinusEnrichment(UpdateRecordContentTransformer.readRecordFromString(record)), is(false));
    }

}
