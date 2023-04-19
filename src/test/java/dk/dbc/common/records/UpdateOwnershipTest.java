package dk.dbc.common.records;

import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.common.records.MarcSubField;
import dk.dbc.common.records.UpdateOwnership;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNull.nullValue;

public class UpdateOwnershipTest {

    private MarcRecord getCurrentRecordWithOwner(boolean includeSelf) {
        final MarcRecord record = new MarcRecord();

        final MarcField field996 = new MarcField("996", "00");

        final List<MarcSubField> subfields = new ArrayList<>();
        subfields.add(new MarcSubField("a", "789456"));
        subfields.add(new MarcSubField("o", "ORIGINAL"));
        subfields.add(new MarcSubField("m", "ABC"));

        if (includeSelf)
            subfields.add(new MarcSubField("m", "789456"));

        field996.setSubfields(subfields);
        record.getFields().add(field996);

        return record;
    }

    @Test
    public void testNullRecordBoth() {
        assertThat(UpdateOwnership.mergeRecord(null, null), nullValue());
    }

    @Test
    public void testNullRecord() {
        final MarcRecord currentRecord = new MarcRecord();

        assertThat(UpdateOwnership.mergeRecord(null, currentRecord), nullValue());
    }

    @Test
    public void testNullCurrentRecord() {
        final MarcRecord record = new MarcRecord();

        assertThat(UpdateOwnership.mergeRecord(record, null), equalTo(record));
    }

    @Test
    public void testEmptyRecord() {
        final MarcRecord record = new MarcRecord();
        final MarcRecord currentRecord = getCurrentRecordWithOwner(true);

        assertThat(UpdateOwnership.mergeRecord(record, currentRecord), equalTo(record));
    }

    @Test
    public void testMergeOwnersSameOwner() {
        final MarcField owner = new MarcField("996", "00");
        owner.getSubfields().add(new MarcSubField("a", "789456"));

        final MarcRecord record = new MarcRecord();
        record.getFields().add(owner);

        final MarcRecord currentRecord = getCurrentRecordWithOwner(true);

        assertThat(UpdateOwnership.mergeRecord(record, currentRecord), equalTo(currentRecord));
    }

    @Test
    public void testMergeOwners() {
        final MarcRecord record = getCurrentRecordWithOwner(true);
        final MarcRecord currentRecord = getCurrentRecordWithOwner(true);

        assertThat(UpdateOwnership.mergeRecord(record, currentRecord), equalTo(currentRecord));
    }

    @Test
    public void testMergeOwners_DifferentOwners_PreviousOwnersIncludeCurrent() {
        final MarcField owner = new MarcField("996", "00");
        owner.getSubfields().add(new MarcSubField("a", "777777"));
        final MarcRecord record = new MarcRecord();
        record.getFields().add(owner);

        final MarcRecord currentRecord = getCurrentRecordWithOwner(true);

        final MarcRecord expected = getCurrentRecordWithOwner(true);
        MarcRecordWriter expectedWriter = new MarcRecordWriter(expected);
        expectedWriter.addOrReplaceSubfield("996", "a", "777777");

        assertThat(UpdateOwnership.mergeRecord(record, currentRecord), equalTo(expected));
    }

    @Test
    public void testMergeOwners_DifferentOwners_PreviousOwnersDontIncludeCurrent() {
        final MarcField owner = new MarcField("996", "00");
        owner.getSubfields().add(new MarcSubField("a", "777777"));
        final MarcRecord record = new MarcRecord();
        record.getFields().add(owner);

        final MarcRecord currentRecord = getCurrentRecordWithOwner(false);

        final MarcRecord expected = getCurrentRecordWithOwner(true);
        final MarcRecordWriter expectedWriter = new MarcRecordWriter(expected);
        expectedWriter.addOrReplaceSubfield("996", "a", "777777");

        assertThat(UpdateOwnership.mergeRecord(record, currentRecord), equalTo(expected));
    }

    @Test
    public void testMergeOwners_RETToDBC() {
        final MarcRecord currentRecord = new MarcRecord();
        currentRecord.getFields().add(new MarcField("996", "00", Collections.singletonList(new MarcSubField("a", "RET"))));

        final MarcRecord record = new MarcRecord();
        record.getFields().add(new MarcField("996", "00", Collections.singletonList(new MarcSubField("a", "DBC"))));

        final MarcRecord expected = new MarcRecord();
        expected.getFields().add(new MarcField("996", "00", Collections.singletonList(new MarcSubField("a", "DBC"))));

        assertThat(UpdateOwnership.mergeRecord(record, currentRecord), equalTo(expected));
    }

    @Test
    public void testMergeOwners_DBCToRET() {
        final MarcRecord currentRecord = new MarcRecord();
        currentRecord.getFields().add(new MarcField("996", "00", Collections.singletonList(new MarcSubField("a", "DBC"))));

        final MarcRecord record = new MarcRecord();
        record.getFields().add(new MarcField("996", "00", Collections.singletonList(new MarcSubField("a", "RET"))));

        final MarcRecord expected = new MarcRecord();
        expected.getFields().add(new MarcField("996", "00", Collections.singletonList(new MarcSubField("a", "RET"))));

        assertThat(UpdateOwnership.mergeRecord(record, currentRecord), equalTo(expected));
    }

    @Test
    public void testMergeOwners_NewNon7xOwner() {
        final MarcRecord currentRecord = new MarcRecord();
        currentRecord.getFields().add(new MarcField("996", "00", Collections.singletonList(new MarcSubField("a", "888888"))));

        final MarcRecord record = new MarcRecord();
        record.getFields().add(new MarcField("996", "00", Collections.singletonList(new MarcSubField("a", "777777"))));

        final MarcRecord expected = new MarcRecord();
        expected.getFields().add(new MarcField("996", "00", Collections.singletonList(new MarcSubField("a", "777777"))));

        assertThat(UpdateOwnership.mergeRecord(record, currentRecord), equalTo(expected));
    }

    @Test
    public void testMergeOwners_RETTo7xOwner() {
        final MarcRecord currentRecord = new MarcRecord();
        currentRecord.getFields().add(new MarcField("996", "00", Collections.singletonList(new MarcSubField("a", "RET"))));

        final MarcRecord record = new MarcRecord();
        record.getFields().add(new MarcField("996", "00", Collections.singletonList(new MarcSubField("a", "777777"))));

        final MarcRecord expected = new MarcRecord();
        expected.getFields().add(new MarcField("996", "00", Collections.singletonList(new MarcSubField("a", "777777"))));

        assertThat(UpdateOwnership.mergeRecord(record, currentRecord), equalTo(expected));
    }

    @Test
    public void testMergeOwners_7xToDBC() {
        final MarcRecord currentRecord = new MarcRecord();
        currentRecord.getFields().add(new MarcField("996", "00", Collections.singletonList(new MarcSubField("a", "710100"))));

        final MarcRecord record = new MarcRecord();
        record.getFields().add(new MarcField("996", "00", Collections.singletonList(new MarcSubField("a", "DBC"))));

        final MarcRecord expected = new MarcRecord();
        expected.getFields().add(new MarcField("996", "00", Arrays.asList(new MarcSubField("a", "DBC"),
                new MarcSubField("o", "710100"))));

        assertThat(UpdateOwnership.mergeRecord(record, currentRecord), equalTo(expected));
    }

    @Test
    public void testMergeOwners_7xPreviousOwner() {
        final MarcRecord currentRecord = new MarcRecord();
        currentRecord.getFields().add(new MarcField("996", "00", Arrays.asList(new MarcSubField("a", "710100"),
                new MarcSubField("o", "720200"))));

        final MarcRecord record = new MarcRecord();
        record.getFields().add(new MarcField("996", "00", Collections.singletonList(new MarcSubField("a", "730300"))));

        final MarcRecord expected = new MarcRecord();
        expected.getFields().add(new MarcField("996", "00", Arrays.asList(new MarcSubField("a", "730300"),
                new MarcSubField("o", "720200"),
                new MarcSubField("m", "710100"))));

        assertThat(UpdateOwnership.mergeRecord(record, currentRecord), equalTo(expected));
    }

    @Test
    public void testMergeOwners_7xPreviousOwners() {
        final MarcRecord currentRecord = new MarcRecord();
        currentRecord.getFields().add(new MarcField("996", "00", Arrays.asList(new MarcSubField("a", "710100"),
                new MarcSubField("o", "720200"),
                new MarcSubField("m", "740400"))));

        final MarcRecord record = new MarcRecord();
        record.getFields().add(new MarcField("996", "00", Collections.singletonList(new MarcSubField("a", "730300"))));

        final MarcRecord expected = new MarcRecord();
        expected.getFields().add(new MarcField("996", "00", Arrays.asList(new MarcSubField("a", "730300"),
                new MarcSubField("o", "720200"),
                new MarcSubField("m", "740400"),
                new MarcSubField("m", "710100"))));

        assertThat(UpdateOwnership.mergeRecord(record, currentRecord), equalTo(expected));
    }

}
