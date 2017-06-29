package dk.dbc.updateservice.update;


import dk.dbc.iscrum.records.MarcField;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.updateservice.actions.AssertActionsUtil;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

public class ExpandCommonRecordTest {

    @Before
    public void before() throws IOException {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void expandCommonRecordOk_52846943() throws Exception {
        MarcRecord raw = AssertActionsUtil.loadRecord(AssertActionsUtil.AUT_RAW_52846943);
        MarcRecord expanded = AssertActionsUtil.loadRecord(AssertActionsUtil.AUT_EXPANDED_52846943);
        MarcRecord auth1 = AssertActionsUtil.loadRecord(AssertActionsUtil.AUTHORITY_19024709);
        MarcRecord auth2 = AssertActionsUtil.loadRecord(AssertActionsUtil.AUTHORITY_19024687);

        Map<String, MarcRecord> collection = new HashMap<>();
        collection.put("52846943", raw);
        collection.put("19024709", auth1);
        collection.put("19024687", auth2);

        assertThat(sortRecord(ExpandCommonRecord.expand(collection)), equalTo(expanded));
    }

    @Test
    public void expandCommonRecordOk_53025757() throws Exception {
        MarcRecord raw = AssertActionsUtil.loadRecord(AssertActionsUtil.AUT_RAW_53025757);
        MarcRecord expanded = AssertActionsUtil.loadRecord(AssertActionsUtil.AUT_EXPANDED_53025757);
        MarcRecord auth1 = AssertActionsUtil.loadRecord(AssertActionsUtil.AUTHORITY_68432359);
        MarcRecord auth2 = AssertActionsUtil.loadRecord(AssertActionsUtil.AUTHORITY_69328776);
        MarcRecord auth3 = AssertActionsUtil.loadRecord(AssertActionsUtil.AUTHORITY_19043800);

        Map<String, MarcRecord> collection = new HashMap<>();
        collection.put("53025757", raw);
        collection.put("68432359", auth1);
        collection.put("69328776", auth2);
        collection.put("19043800", auth3);

        assertThat(sortRecord(ExpandCommonRecord.expand(collection)), equalTo(expanded));
    }

    @Test
    public void expandCommonRecordOk_53161510() throws Exception {
        MarcRecord raw = AssertActionsUtil.loadRecord(AssertActionsUtil.AUT_RAW_53161510);
        MarcRecord expanded = AssertActionsUtil.loadRecord(AssertActionsUtil.AUT_EXPANDED_53161510);
        MarcRecord auth1 = AssertActionsUtil.loadRecord(AssertActionsUtil.AUTHORITY_69094139);
        MarcRecord auth2 = AssertActionsUtil.loadRecord(AssertActionsUtil.AUTHORITY_68098203);
        MarcRecord auth3 = AssertActionsUtil.loadRecord(AssertActionsUtil.AUTHORITY_19064689);

        Map<String, MarcRecord> collection = new HashMap<>();
        collection.put("53161510", raw);
        collection.put("69094139", auth1);
        collection.put("68098203", auth2);
        collection.put("19064689", auth3);

        assertThat(sortRecord(ExpandCommonRecord.expand(collection)), equalTo(expanded));
    }

    @Test
    public void expandCommonRecordOk_53180485() throws Exception {
        MarcRecord raw = AssertActionsUtil.loadRecord(AssertActionsUtil.AUT_RAW_53180485);
        MarcRecord expanded = AssertActionsUtil.loadRecord(AssertActionsUtil.AUT_EXPANDED_53180485);
        MarcRecord auth1 = AssertActionsUtil.loadRecord(AssertActionsUtil.AUTHORITY_68839734);
        MarcRecord auth2 = AssertActionsUtil.loadRecord(AssertActionsUtil.AUTHORITY_68584566);
        MarcRecord auth3 = AssertActionsUtil.loadRecord(AssertActionsUtil.AUTHORITY_68900719);
        MarcRecord auth4 = AssertActionsUtil.loadRecord(AssertActionsUtil.AUTHORITY_68560985);


        Map<String, MarcRecord> collection = new HashMap<>();
        collection.put("53180485", raw);
        collection.put("68839734", auth1);
        collection.put("68584566", auth2);
        collection.put("68900719", auth3);
        collection.put("68560985", auth4);

        assertThat(sortRecord(ExpandCommonRecord.expand(collection)), equalTo(expanded));
    }

    @Test
    public void expandCommonRecordOk_53213642() throws Exception {
        MarcRecord raw = AssertActionsUtil.loadRecord(AssertActionsUtil.AUT_RAW_53213642);
        MarcRecord expanded = AssertActionsUtil.loadRecord(AssertActionsUtil.AUT_EXPANDED_53213642);
        MarcRecord auth1 = AssertActionsUtil.loadRecord(AssertActionsUtil.AUTHORITY_68895650);
        MarcRecord auth2 = AssertActionsUtil.loadRecord(AssertActionsUtil.AUTHORITY_19130452);

        Map<String, MarcRecord> collection = new HashMap<>();
        collection.put("53213642", raw);
        collection.put("68895650", auth1);
        collection.put("19130452", auth2);

        assertThat(sortRecord(ExpandCommonRecord.expand(collection)), equalTo(expanded));
    }

    @Test
    public void expandCommonRecordOk_53214592() throws Exception {
        MarcRecord raw = AssertActionsUtil.loadRecord(AssertActionsUtil.AUT_RAW_53214592);
        MarcRecord expanded = AssertActionsUtil.loadRecord(AssertActionsUtil.AUT_EXPANDED_53214592);
        MarcRecord auth1 = AssertActionsUtil.loadRecord(AssertActionsUtil.AUTHORITY_68354153);
        MarcRecord auth2 = AssertActionsUtil.loadRecord(AssertActionsUtil.AUTHORITY_68354153);
        MarcRecord auth3 = AssertActionsUtil.loadRecord(AssertActionsUtil.AUTHORITY_68472806);
        MarcRecord auth4 = AssertActionsUtil.loadRecord(AssertActionsUtil.AUTHORITY_68585627);

        Map<String, MarcRecord> collection = new HashMap<>();
        collection.put("53214592", raw);
        collection.put("68354153", auth1);
        collection.put("68354153", auth2);
        collection.put("68472806", auth3);
        collection.put("68585627", auth4);

        assertThat(sortRecord(ExpandCommonRecord.expand(collection)), equalTo(expanded));
    }

    @Test
    public void expandCommonRecordOk_53214827() throws Exception {
        MarcRecord raw = AssertActionsUtil.loadRecord(AssertActionsUtil.AUT_RAW_53214827);
        MarcRecord expanded = AssertActionsUtil.loadRecord(AssertActionsUtil.AUT_EXPANDED_53214827);
        MarcRecord auth1 = AssertActionsUtil.loadRecord(AssertActionsUtil.AUTHORITY_68570492);

        Map<String, MarcRecord> collection = new HashMap<>();
        collection.put("53214827", raw);
        collection.put("68570492", auth1);

        assertThat(sortRecord(ExpandCommonRecord.expand(collection)), equalTo(expanded));
    }

    @Test
    public void expandCommonRecordOk_90004158() throws Exception {
        MarcRecord raw = AssertActionsUtil.loadRecord(AssertActionsUtil.AUT_RAW_90004158);
        MarcRecord expanded = AssertActionsUtil.loadRecord(AssertActionsUtil.AUT_EXPANDED_90004158);
        MarcRecord auth1 = AssertActionsUtil.loadRecord(AssertActionsUtil.AUTHORITY_68712742);
        MarcRecord auth2 = AssertActionsUtil.loadRecord(AssertActionsUtil.AUTHORITY_69294685);

        Map<String, MarcRecord> collection = new HashMap<>();
        collection.put("90004158", raw);
        collection.put("68712742", auth1);
        collection.put("69294685", auth2);

        assertThat(sortRecord(ExpandCommonRecord.expand(collection)), equalTo(expanded));
    }

    @Test(expected = UpdateException.class)
    public void noCommonRecord() throws Exception {
        Map<String, MarcRecord> collection = new HashMap<>();

        assertThat(sortRecord(ExpandCommonRecord.expand(collection)), equalTo(null));
    }

    @Test(expected = UpdateException.class)
    public void missingAuthorityRecords() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.AUT_RAW_90004158);

        Map<String, MarcRecord> collection = new HashMap<>();
        collection.put("90004158", record);

        assertThat(sortRecord(ExpandCommonRecord.expand(collection)), equalTo(null));
    }

    @Test
    public void expandCommonRecordWithoutAuthorityFields() throws Exception {
        MarcRecord record = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);

        Map<String, MarcRecord> collection = new HashMap<>();
        collection.put("20611529", record);

        assertThat(sortRecord(ExpandCommonRecord.expand(collection)), equalTo(record));
    }

    private MarcRecord sortRecord(MarcRecord record) {
        Collections.sort(record.getFields(), new Comparator<MarcField>() {
            public int compare(MarcField o1, MarcField o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });

        return record;
    }

}
