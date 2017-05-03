/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;


import dk.dbc.iscrum.records.MarcField;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcSubField;
import dk.dbc.iscrum.utils.ResourceBundles;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

public class DefaultEnrichmentRecordHandlerTest {

    private ResourceBundle bundle = ResourceBundles.getBundle("actions");

    @Test
    public void testCollectProductionCodesEmpty() throws Exception {
        List<MarcField> fields = new ArrayList<>();
        MarcRecord record = new MarcRecord(fields);

        List<String> expected = new ArrayList<>();

        assertThat(DefaultEnrichmentRecordHandler.collectProductionCodes(record), equalTo(expected));
    }

    @Test
    public void testCollectProductionCodes() throws Exception {
        MarcField f001 = new MarcField("001", "00");
        f001.getSubfields().add(new MarcSubField("a", "12345678"));
        f001.getSubfields().add(new MarcSubField("b", "870970"));

        MarcField f032 = new MarcField("032", "00");
        f032.getSubfields().add(new MarcSubField("a", ""));
        f032.getSubfields().add(new MarcSubField("a", "ABC"));
        f032.getSubfields().add(new MarcSubField("a", " DBF"));
        f032.getSubfields().add(new MarcSubField("a", "DBF999999"));
        f032.getSubfields().add(new MarcSubField("x", ""));
        f032.getSubfields().add(new MarcSubField("x", "ABC"));
        f032.getSubfields().add(new MarcSubField("x", " DBF"));
        f032.getSubfields().add(new MarcSubField("x", "DBF999999"));

        MarcRecord record = new MarcRecord();
        record.getFields().add(f001);
        record.getFields().add(f032);

        List<String> expected = new ArrayList<>();
        expected.add("a:DBF999999");
        expected.add("x:DBF999999");

        assertThat(DefaultEnrichmentRecordHandler.collectProductionCodes(record), equalTo(expected));
    }

    @Test
    public void testMatchCatCodesSingleMatch() {
        MarcRecord record1 = new MarcRecord();
        record1.getFields().add(new MarcField("032", "00", Arrays.asList(new MarcSubField("x", "DBF999999"))));

        MarcRecord record2 = new MarcRecord();
        record2.getFields().add(new MarcField("032", "00", Arrays.asList(new MarcSubField("x", "DBF999999"))));

        assertThat(DefaultEnrichmentRecordHandler.matchKatCodes(record1, record2), equalTo(true));
    }

    @Test
    public void testMatchCatCodesDoubleMatchReverseOrder() {
        MarcRecord record1 = new MarcRecord();
        record1.getFields().add(new MarcField("032", "00", Arrays.asList(new MarcSubField("a", "DBI999999"), new MarcSubField("x", "DBF999999"))));

        MarcRecord record2 = new MarcRecord();
        record2.getFields().add(new MarcField("032", "00", Arrays.asList(new MarcSubField("x", "DBF999999"), new MarcSubField("a", "DBI999999"))));

        assertThat(DefaultEnrichmentRecordHandler.matchKatCodes(record1, record2), equalTo(true));
    }

    @Test
    public void testMatchCatCodesNoMatchDiffentSizes() {
        MarcRecord record1 = new MarcRecord();
        record1.getFields().add(new MarcField("032", "00", Arrays.asList(new MarcSubField("x", "DBF999999"))));

        MarcRecord record2 = new MarcRecord();
        record2.getFields().add(new MarcField("032", "00", Arrays.asList(new MarcSubField("x", "DBF999999"), new MarcSubField("a", "DBI999999"))));

        assertThat(DefaultEnrichmentRecordHandler.matchKatCodes(record1, record2), equalTo(false));
    }

    @Test
    public void testMatchCatCodesNoMatchSameSizes() {
        MarcRecord record1 = new MarcRecord();
        record1.getFields().add(new MarcField("032", "00", Arrays.asList(new MarcSubField("x", "DBF999999"))));

        MarcRecord record2 = new MarcRecord();
        record2.getFields().add(new MarcField("032", "00", Arrays.asList(new MarcSubField("a", "DBI999999"))));

        assertThat(DefaultEnrichmentRecordHandler.matchKatCodes(record1, record2), equalTo(false));
    }

    @Test
    public void testShouldCreateEnrichmentRecordsOk() {
        MarcRecord updatingCommonRecord = new MarcRecord();
        updatingCommonRecord.getFields().add(new MarcField("032", "00", Arrays.asList(new MarcSubField("a", "DBI201542"), new MarcSubField("x", "DBF201542"))));

        MarcRecord currentCommonRecord = new MarcRecord();
        currentCommonRecord.getFields().add(new MarcField("032", "00", Arrays.asList(new MarcSubField("a", "DBI201541"), new MarcSubField("x", "DBF201541"))));
        currentCommonRecord.getFields().add(new MarcField("652", "00", Arrays.asList(new MarcSubField("m", "Grydesteg"))));

        assertThat(DefaultEnrichmentRecordHandler.shouldCreateEnrichmentRecordsResult(bundle, updatingCommonRecord, currentCommonRecord), equalTo(true));
    }

    @Test
    public void testShouldCreateEnrichmentRecordsFailNyTitle() {
        MarcRecord updatingCommonRecord = new MarcRecord();
        updatingCommonRecord.getFields().add(new MarcField("032", "00", Arrays.asList(new MarcSubField("a", "DBI201542"), new MarcSubField("x", "DBF201542"))));

        MarcRecord currentCommonRecord = new MarcRecord();
        currentCommonRecord.getFields().add(new MarcField("032", "00", Arrays.asList(new MarcSubField("a", "DBI201541"), new MarcSubField("x", "DBF201541"))));
        currentCommonRecord.getFields().add(new MarcField("652", "00", Arrays.asList(new MarcSubField("m", "ny titel"))));

        assertThat(DefaultEnrichmentRecordHandler.shouldCreateEnrichmentRecordsResult(bundle, updatingCommonRecord, currentCommonRecord), equalTo(false));
    }

    @Test
    public void testShouldCreateEnrichmentRecordsFail032a() {
        MarcRecord updatingCommonRecord = new MarcRecord();
        updatingCommonRecord.getFields().add(new MarcField("032", "00", Arrays.asList(new MarcSubField("a", "DBI999999"), new MarcSubField("x", "DBF201542"))));

        MarcRecord currentCommonRecord = new MarcRecord();
        currentCommonRecord.getFields().add(new MarcField("032", "00", Arrays.asList(new MarcSubField("a", "DBI201541"), new MarcSubField("x", "DBF201541"))));
        currentCommonRecord.getFields().add(new MarcField("652", "00", Arrays.asList(new MarcSubField("m", "Grydesteg"))));

        assertThat(DefaultEnrichmentRecordHandler.shouldCreateEnrichmentRecordsResult(bundle, updatingCommonRecord, currentCommonRecord), equalTo(false));
    }

    @Test
    public void testShouldCreateEnrichmentRecordsFail032x() {
        MarcRecord updatingCommonRecord = new MarcRecord();
        updatingCommonRecord.getFields().add(new MarcField("032", "00", Arrays.asList(new MarcSubField("a", "DBI201542"), new MarcSubField("x", "DBF999999"))));

        MarcRecord currentCommonRecord = new MarcRecord();
        currentCommonRecord.getFields().add(new MarcField("032", "00", Arrays.asList(new MarcSubField("a", "DBI201541"), new MarcSubField("x", "DBF201541"))));
        currentCommonRecord.getFields().add(new MarcField("652", "00", Arrays.asList(new MarcSubField("m", "Grydesteg"))));

        assertThat(DefaultEnrichmentRecordHandler.shouldCreateEnrichmentRecordsResult(bundle, updatingCommonRecord, currentCommonRecord), equalTo(false));
    }

    @Test
    public void testShouldCreateEnrichmentRecordsFailUnderProduction() {
        MarcRecord updatingCommonRecord = new MarcRecord();
        updatingCommonRecord.getFields().add(new MarcField("032", "00", Arrays.asList(new MarcSubField("a", "DBI211542"))));

        MarcRecord currentCommonRecord = new MarcRecord();
        currentCommonRecord.getFields().add(new MarcField("032", "00", Arrays.asList(new MarcSubField("a", "DBI211541"))));
        currentCommonRecord.getFields().add(new MarcField("652", "00", Arrays.asList(new MarcSubField("m", "Grydesteg"))));

        assertThat(DefaultEnrichmentRecordHandler.shouldCreateEnrichmentRecordsResult(bundle, updatingCommonRecord, currentCommonRecord), equalTo(false));
    }

    @Test
    public void testShouldCreateEnrichmentRecordsOkUnderProduction008ru() {
        MarcRecord updatingCommonRecord = new MarcRecord();
        updatingCommonRecord.getFields().add(new MarcField("008", "00", Arrays.asList(new MarcSubField("u", "r"))));
        updatingCommonRecord.getFields().add(new MarcField("032", "00", Arrays.asList(new MarcSubField("a", "DBI211542"))));


        MarcRecord currentCommonRecord = new MarcRecord();
        currentCommonRecord.getFields().add(new MarcField("032", "00", Arrays.asList(new MarcSubField("a", "DBI211541"))));
        currentCommonRecord.getFields().add(new MarcField("652", "00", Arrays.asList(new MarcSubField("m", "Grydesteg"))));

        assertThat(DefaultEnrichmentRecordHandler.shouldCreateEnrichmentRecordsResult(bundle, updatingCommonRecord, currentCommonRecord), equalTo(true));
    }

    @Test
    public void testShouldCreateEnrichmentRecordsFailUnderProduction008ru() {
        MarcRecord updatingCommonRecord = new MarcRecord();
        updatingCommonRecord.getFields().add(new MarcField("008", "00", Arrays.asList(new MarcSubField("u", "r"))));
        updatingCommonRecord.getFields().add(new MarcField("032", "00", Arrays.asList(new MarcSubField("a", "DBI211542"))));


        MarcRecord currentCommonRecord = new MarcRecord();
        currentCommonRecord.getFields().add(new MarcField("032", "00", Arrays.asList(new MarcSubField("a", "DBI211542"))));
        currentCommonRecord.getFields().add(new MarcField("652", "00", Arrays.asList(new MarcSubField("m", "Grydesteg"))));

        assertThat(DefaultEnrichmentRecordHandler.shouldCreateEnrichmentRecordsResult(bundle, updatingCommonRecord, currentCommonRecord), equalTo(false));
    }

    @Test
    public void testShouldCreateEnrichmentRecordsFailUnderProductionOk() {
        MarcRecord updatingCommonRecord = new MarcRecord();
        updatingCommonRecord.getFields().add(new MarcField("008", "00", Arrays.asList(new MarcSubField("u", "r"))));
        updatingCommonRecord.getFields().add(new MarcField("032", "00", Arrays.asList(new MarcSubField("a", "DBI211642"))));

        MarcRecord currentCommonRecord = new MarcRecord();
        currentCommonRecord.getFields().add(new MarcField("032", "00", Arrays.asList(new MarcSubField("a", "DBI999999"))));
        currentCommonRecord.getFields().add(new MarcField("652", "00", Arrays.asList(new MarcSubField("m", "Grydesteg"))));

        assertThat(DefaultEnrichmentRecordHandler.shouldCreateEnrichmentRecordsResult(bundle, updatingCommonRecord, currentCommonRecord), equalTo(true));
    }

}
