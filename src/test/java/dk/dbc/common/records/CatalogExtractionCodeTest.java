package dk.dbc.common.records;

import dk.dbc.common.records.CatalogExtractionCode;
import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcSubField;
import org.junit.Test;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

public class CatalogExtractionCodeTest {

    @Test
    public void testhasLastProductionDate() {
        assertThat(CatalogExtractionCode.hasPublishingDate(""), equalTo(false));
        assertThat(CatalogExtractionCode.hasPublishingDate("XXX20161"), equalTo(false));
        assertThat(CatalogExtractionCode.hasPublishingDate("XXX2016001"), equalTo(false));
        assertThat(CatalogExtractionCode.hasPublishingDate("DBIXXXXXX"), equalTo(false));

        assertThat(CatalogExtractionCode.hasPublishingDate("DBI201652"), equalTo(true));
        assertThat(CatalogExtractionCode.hasPublishingDate("DBI999999"), equalTo(true));
    }

    @Test
    public void testhasFutureLastProductionDate() {
        assertThat(CatalogExtractionCode.hasFuturePublishingDate("DBI999999"), equalTo(true));
        assertThat(CatalogExtractionCode.hasFuturePublishingDate("DBI201502"), equalTo(false));
        assertThat(CatalogExtractionCode.hasFuturePublishingDate("DBI211602"), equalTo(true));
    }

    @Test
    public void testIsRecordInProductionEmptyRecord() {
        MarcRecord record = new MarcRecord();

        assertThat(CatalogExtractionCode.isUnderProduction(record), equalTo(false));
    }

    @Test
    public void testIsRecordInProductionNo032() {
        MarcRecord record = new MarcRecord();
        record.getFields().add(new MarcField("004", "00"));

        assertThat(CatalogExtractionCode.isUnderProduction(record), equalTo(false));
    }

    private MarcRecord generateMarcRecordWith032(String subfieldName, String value) {
        MarcSubField subfield = new MarcSubField(subfieldName, value);

        MarcField field = new MarcField("032", "00");
        field.getSubfields().add(subfield);

        MarcRecord record = new MarcRecord();
        record.getFields().add(field);

        return record;
    }

    @Test
    public void testIsRecordInProductionNo032ax() {
        MarcRecord record = generateMarcRecordWith032("k", "xxx");

        assertThat(CatalogExtractionCode.isUnderProduction(record), equalTo(false));
    }

    @Test
    public void testIsRecordInProduction032aWrongFormat() {
        MarcRecord record = generateMarcRecordWith032("a", "xxx");

        assertThat(CatalogExtractionCode.isUnderProduction(record), equalTo(false));
    }

    @Test
    public void testIsRecordInProduction032aWrongProductionCode() {
        MarcRecord record = generateMarcRecordWith032("a", "XXX201504");

        assertThat(CatalogExtractionCode.isUnderProduction(record), equalTo(false));
    }

    @Test
    public void testIsRecordInProduction032aPublicityDateIsBeforeCurrentDate() {
        MarcRecord record = generateMarcRecordWith032("a", "DBI201504");

        assertThat(CatalogExtractionCode.isUnderProduction(record), equalTo(false));
    }

    @Test
    public void testIsRecordInProduction032aPublicityDateIsBeforeCurrentDateCustomCatalogCodes() {
        MarcRecord record = generateMarcRecordWith032("a", "DBF201504");

        assertThat(CatalogExtractionCode.isUnderProduction(record,
                Arrays.asList("DLF", "DBI", "DMF", "DMO", "DPF", "BKM", "GBF", "GMO", "GPF", "FPF", "DBR", "UTI")),
                equalTo(false));
    }

    @Test
    public void testIsRecordInProduction032aPublicityDateIsAfterCurrentDate() {
        MarcRecord record = generateMarcRecordWith032("a", "DBI211604");

        assertThat(CatalogExtractionCode.isUnderProduction(record), equalTo(true));
    }

    @Test
    public void testIsRecordInProduction032xWrongFormat() {
        MarcRecord record = generateMarcRecordWith032("x", "xxx");

        assertThat(CatalogExtractionCode.isUnderProduction(record), equalTo(false));
    }

    @Test
    public void testIsRecordInProduction032xWrongProductionCode() {
        MarcRecord record = generateMarcRecordWith032("x", "XXX201504");

        assertThat(CatalogExtractionCode.isUnderProduction(record), equalTo(false));
    }

    @Test
    public void testIsRecordInProduction032xPublicityDateIsBeforeCurrentDate() {
        MarcRecord record = generateMarcRecordWith032("x", "DBI201504");

        assertThat(CatalogExtractionCode.isUnderProduction(record), equalTo(false));
    }

    @Test
    public void testIsRecordInProduction032xPublicityDateIsAfterCurrentDate() {
        MarcRecord record = generateMarcRecordWith032("x", "DBI211604");

        assertThat(CatalogExtractionCode.isUnderProduction(record), equalTo(true));
    }

    @Test
    public void testIsRecordInProduction032xTemporaryDateOnly() {
        MarcRecord record = generateMarcRecordWith032("x", "DBI999999");

        assertThat(CatalogExtractionCode.isUnderProduction(record), equalTo(true));
    }

    @Test
    public void testIsRecordInProduction032aTemporaryDateOnly() {
        MarcRecord record = generateMarcRecordWith032("a", "DBI999999");

        assertThat(CatalogExtractionCode.isUnderProduction(record), equalTo(true));
    }

    @Test
    public void testIsRecordInProduction032aPublicityDateIsBeforeCurrentDateAnd032xPublicityDateIsAfterCurrentDate() {
        MarcSubField subfieldA = new MarcSubField("a", "DBI191304");
        MarcSubField subfieldX = new MarcSubField("x", "DBI211604");

        MarcField field = new MarcField("032", "00");
        field.getSubfields().add(subfieldA);
        field.getSubfields().add(subfieldX);

        MarcRecord record = new MarcRecord();
        record.getFields().add(field);

        assertThat(CatalogExtractionCode.isUnderProduction(record), equalTo(false));
    }

    @Test
    public void testIsRecordInProduction032aTemporaryDateAnd032xPublicityDateIsAfterCurrentDate() {
        MarcSubField subfieldA = new MarcSubField("a", "DBI999999");
        MarcSubField subfieldX = new MarcSubField("x", "DBI211604");

        MarcField field = new MarcField("032", "00");
        field.getSubfields().add(subfieldA);
        field.getSubfields().add(subfieldX);

        MarcRecord record = new MarcRecord();
        record.getFields().add(field);

        assertThat(CatalogExtractionCode.isUnderProduction(record), equalTo(true));
    }

    @Test
    public void testIsRecordInProduction032aPublicityDateIsBeforeCurrentDateAnd032xTemporaryDate() {
        MarcSubField subfieldA = new MarcSubField("a", "DBI191304");
        MarcSubField subfieldX = new MarcSubField("x", "DBI999999");

        MarcField field = new MarcField("032", "00");
        field.getSubfields().add(subfieldA);
        field.getSubfields().add(subfieldX);

        MarcRecord record = new MarcRecord();
        record.getFields().add(field);

        assertThat(CatalogExtractionCode.isUnderProduction(record), equalTo(false));
    }

    @Test
    public void testIsPublishedNoField032() {
        MarcRecord record = new MarcRecord();

        assertThat(CatalogExtractionCode.isPublished(record), equalTo(false));
    }


    @Test
    public void testIsPublishedHasProductionCodeInThePast() {
        MarcSubField subfieldA = new MarcSubField("a", "DBI191304");
        MarcSubField subfieldX = new MarcSubField("x", "DBI999999");

        MarcField field = new MarcField("032", "00");
        field.getSubfields().add(subfieldA);
        field.getSubfields().add(subfieldX);

        MarcRecord record = new MarcRecord();
        record.getFields().add(field);

        assertThat(CatalogExtractionCode.isPublished(record), equalTo(true));
    }

    @Test
    public void testIsPublishedHasProductionCodeInThePastCustomCatalogueCodes() {
        MarcSubField subfieldA = new MarcSubField("a", "DBI191304");
        MarcSubField subfieldX = new MarcSubField("x", "DBI999999");

        MarcField field = new MarcField("032", "00");
        field.getSubfields().add(subfieldA);
        field.getSubfields().add(subfieldX);

        MarcRecord record = new MarcRecord();
        record.getFields().add(field);

        assertThat(CatalogExtractionCode.isPublished(record, Arrays.asList("DBF", "DLF", "DMF", "DMO", "DPF", "BKM", "GBF", "GMO", "GPF", "FPF", "DBR", "UTI")), equalTo(false));
    }

    @Test
    public void verifySubfieldAndContent() {

        assertThat(CatalogExtractionCode.verifySubfieldAndContent("&", "text"), equalTo(false));
        assertThat(CatalogExtractionCode.verifySubfieldAndContent("a", "short"), equalTo(false));
        assertThat(CatalogExtractionCode.verifySubfieldAndContent("a", "0123456789"), equalTo(false));
        assertThat(CatalogExtractionCode.verifySubfieldAndContent("a", "123456789"), equalTo(true));
        assertThat(CatalogExtractionCode.verifySubfieldAndContent("a", "OVE456789"), equalTo(false));
        assertThat(CatalogExtractionCode.verifySubfieldAndContent("a", "BKM456789"), equalTo(true));
    }

    @Test
    public void testIsPublishedIgnoreCatalogCodes() {
        MarcSubField subfieldA = new MarcSubField("a", "XYZ191304");
        MarcSubField subfieldX = new MarcSubField("x", "ÅÅÅ999999");
        MarcSubField subfieldAmp = new MarcSubField("&", "715700");
        MarcSubField subfieldOve = new MarcSubField("a", "OVE202121");

        MarcField field = new MarcField("032", "00");
        field.getSubfields().add(subfieldA);
        field.getSubfields().add(subfieldX);

        MarcRecord record = new MarcRecord();
        record.getFields().add(field);

        assertThat(CatalogExtractionCode.isPublishedIgnoreCatalogCodes(record), equalTo(true));

        MarcField fieldAmp = new MarcField("032", "00");
        fieldAmp.getSubfields().add(subfieldAmp);
        MarcRecord recordAmp = new MarcRecord();
        recordAmp.getFields().add(fieldAmp);
        assertThat(CatalogExtractionCode.isPublishedIgnoreCatalogCodes(recordAmp), equalTo(false));
        fieldAmp.getSubfields().add(subfieldOve);
        assertThat(CatalogExtractionCode.isPublishedIgnoreCatalogCodes(recordAmp), equalTo(false));
        fieldAmp.getSubfields().add(subfieldA);
        assertThat(CatalogExtractionCode.isPublishedIgnoreCatalogCodes(recordAmp), equalTo(true));

    }

    @Test
    public void testIsPublishedHasProductionCodeInTheFuture() {
        MarcSubField subfieldA = new MarcSubField("a", "DBI291304");
        MarcSubField subfieldX = new MarcSubField("x", "DBI999999");

        MarcField field = new MarcField("032", "00");
        field.getSubfields().add(subfieldA);
        field.getSubfields().add(subfieldX);

        MarcRecord record = new MarcRecord();
        record.getFields().add(field);

        assertThat(CatalogExtractionCode.isPublished(record), equalTo(false));
    }

    @Test
    public void testIsPublishedHasACCCodeInThePast() {
        MarcSubField subfieldA = new MarcSubField("a", "ACC201839");
        MarcSubField subfieldX = new MarcSubField("x", "DBI999999");

        MarcField field = new MarcField("032", "00");
        field.getSubfields().add(subfieldA);
        field.getSubfields().add(subfieldX);

        MarcRecord record = new MarcRecord();
        record.getFields().add(field);

        assertThat(CatalogExtractionCode.isPublished(record), equalTo(false));
    }

}
