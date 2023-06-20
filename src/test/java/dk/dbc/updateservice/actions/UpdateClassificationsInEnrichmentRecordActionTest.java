package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.marc.binding.DataField;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.marc.binding.SubField;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.update.UpdateRecordContentTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.ListIterator;
import java.util.Properties;

import static dk.dbc.updateservice.update.JNDIResources.RAWREPO_PROVIDER_ID_FBS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

class UpdateClassificationsInEnrichmentRecordActionTest {
    private GlobalActionState state;
    private Properties settings;

    @BeforeEach
    public void before() throws IOException, UpdateException {
        state = new UpdateTestUtils().getGlobalActionStateMockObject();
        settings = new UpdateTestUtils().getSettings();
    }

    /**
     * Test UpdateClassificationsInEnrichmentRecordAction.createRecord(): Update enrichment record
     * successfully.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * Common record and enrichment record.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Update classifications in enrichment record.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Returns a new enrichment record with extra 504 field.
     * </dd>
     * </dl>
     */
    @Test
    void testCreateRecord() throws Exception {
        final MarcRecord commonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        final MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        final MarcRecord newEnrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(newEnrichmentRecord).addOrReplaceSubField("504", 'a', "Ny Note");

        when(state.getLibraryRecordsHandler().updateLibraryExtendedRecord(isNull(), eq(commonRecord), eq(enrichmentRecord))).thenReturn(newEnrichmentRecord);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(enrichmentRecord, commonRecord)).thenReturn(true);

        final UpdateClassificationsInEnrichmentRecordAction instance = new UpdateClassificationsInEnrichmentRecordAction(state, settings, enrichmentRecord, "870970");
        instance.setCurrentCommonRecord(null);
        instance.setUpdatingCommonRecord(commonRecord);
        assertThat(instance.performAction(), is(ServiceResult.newOkResult()));

        final List<ServiceAction> children = instance.children();
        assertThat(children.size(), is(3));

        final ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), newEnrichmentRecord, MarcXChangeMimeType.ENRICHMENT);
        AssertActionsUtil.assertLinkRecordAction(iterator.next(), state.getRawRepo(), newEnrichmentRecord, commonRecord);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), newEnrichmentRecord, RAWREPO_PROVIDER_ID_FBS);
    }

    @Test
    void testModifyEnrichment() throws Exception {
        final MarcRecord commonRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.COMMON_SINGLE_RECORD_RESOURCE);
        final MarcRecord enrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        final MarcRecord newEnrichmentRecord = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(newEnrichmentRecord).addOrReplaceSubField("y08", 'a', "Ny Note");

        final MarcRecord expected = AssertActionsUtil.loadRecord(AssertActionsUtil.ENRICHMENT_SINGLE_RECORD_RESOURCE);
        new MarcRecordWriter(expected).addOrReplaceSubField("001", 'c', "20230619135542");

        final DataField y08 = new DataField("y08", "00");
        y08.getSubFields().add(new SubField('a', "Ny Note"));
        y08.getSubFields().add(new SubField('a', "UPDATE opstillingsændring"));
        expected.getFields().add(y08);

        when(state.getLibraryRecordsHandler().updateLibraryExtendedRecord(isNull(), eq(commonRecord), eq(enrichmentRecord))).thenReturn(newEnrichmentRecord);
        when(state.getLibraryRecordsHandler().hasClassificationsChanged(enrichmentRecord, commonRecord)).thenReturn(true);

        final UpdateClassificationsInEnrichmentRecordAction instance = new UpdateClassificationsInEnrichmentRecordAction(state, settings, enrichmentRecord, "870970");
        instance.setCurrentCommonRecord(null);
        instance.setUpdatingCommonRecord(commonRecord);
        instance.setOverrideChangedTimestamp("20230619135542");

        assertThat(instance.performAction(), is(ServiceResult.newOkResult()));

        final List<ServiceAction> children = instance.children();
        assertThat(children.size(), is(3));

        final ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), expected, MarcXChangeMimeType.ENRICHMENT);
        AssertActionsUtil.assertLinkRecordAction(iterator.next(), state.getRawRepo(), expected, commonRecord);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), expected, RAWREPO_PROVIDER_ID_FBS);
    }

    @Test
    void testUpdateEnrichmentWithIdenticalClassification() throws Exception {
        final String existingCommonRecordData = "001 00 *a 61443886 *b 870970 *c 20220616134845 *d 20210922 *f a \n" +
                "004 00 *r n *a e \n" +
                "008 00 *t m *u f *a 2021 *b dk *d y *l dan *x 06 *v 0 \n" +
                "009 00 *a a *g xx \n" +
                "021 00 *e 9788789074450 *c hf. \n" +
                "032 00 *x ACC202138 *a DBF202144 *x BKM202144 \n" +
                "100 00 *a Solsort *h Olga *4 aut *4 art \n" +
                "245 00 *a Mindesmærke for de trolddomsanklagede\n" +
                "260 00 *a [Køge] *b KØS Museum for kunst i det offentlige rum *c 2021 \n" +
                "300 00 *a 145 sider *b ill. (nogle i farver) \n" +
                "652 00 *m 72 *a Solsort *h Olga \n" +
                "996 00 *a DBC";

        final String updatingCommonRecordData = "001 00 *a 61443886 *b 870970 *c 20220616134845 *d 20210922 *f a \n" +
                "004 00 *r n *a e \n" +
                "008 00 *t m *u f *a 2021 *b dk *d y *l dan *x 06 *v 0 \n" +
                "009 00 *a a *g xx \n" +
                "021 00 *e 9788789074450 *c hf. \n" +
                "032 00 *x ACC202138 *a DBF202144 *x BKM202144 \n" +
                "100 00 *a Ravn *h Olga *4 aut *4 art \n" +
                "245 00 *a Mindesmærke for de trolddomsanklagede\n" +
                "260 00 *a [Køge] *b KØS Museum for kunst i det offentlige rum *c 2021 \n" +
                "300 00 *a 145 sider *b ill. (nogle i farver) \n" +
                "652 00 *m 72 *a Ravn *h Olga \n" +
                "996 00 *a DBC";

        final String existingEnrichmentRecordData = "001 00 *a 61443886 *b 710100 *c 20220616134845 *d 20210922 *f a \n" +
                "004 00 *r n *a e \n" +
                "008 00 *t m *u f *a 2021 *b dk *d y *l dan *x 06 *v 0 \n" +
                "009 00 *a a *g xx \n" +
                "100 00 *a Ravn *h Olga *4 aut *4 art \n" +
                "245 00 *a Mindesmærke for de trolddomsanklagede\n" +
                "631 00 *f Køge \n" +
                "631 00 *f Ellebækstien\n" +
                "652 00 *m 72 *a Ravn *h Olga ";

        final String expectedEnrichmentRecordData = "001 00 *a 61443886 *b 710100 *c 20220616134845 *d 20210922 *f a \n" +
                "004 00 *r n *a e \n" +
                "631 00 *f Køge \n" +
                "631 00 *f Ellebækstien";

        final MarcRecord existingCommonMarcRecord = UpdateRecordContentTransformer.readRecordFromString(existingCommonRecordData);
        final MarcRecord updatingCommonMarcRecord = UpdateRecordContentTransformer.readRecordFromString(updatingCommonRecordData);
        final MarcRecord existingEnrichmentMarcRecord = UpdateRecordContentTransformer.readRecordFromString(existingEnrichmentRecordData);
        final MarcRecord expectedEnrichmentMarcRecord = UpdateRecordContentTransformer.readRecordFromString(expectedEnrichmentRecordData);

        when(state.getLibraryRecordsHandler().hasClassificationsChanged(existingEnrichmentMarcRecord, updatingCommonMarcRecord)).thenReturn(false);

        final UpdateClassificationsInEnrichmentRecordAction instance = new UpdateClassificationsInEnrichmentRecordAction(state, settings, existingEnrichmentMarcRecord, "870970");
        instance.setCurrentCommonRecord(existingCommonMarcRecord);
        instance.setUpdatingCommonRecord(updatingCommonMarcRecord);
        assertThat(instance.performAction(), is(ServiceResult.newOkResult()));

        final List<ServiceAction> children = instance.children();
        assertThat(children.size(), is(3));

        final ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertStoreRecordAction(iterator.next(), state.getRawRepo(), expectedEnrichmentMarcRecord, MarcXChangeMimeType.ENRICHMENT);
        AssertActionsUtil.assertLinkRecordAction(iterator.next(), state.getRawRepo(), expectedEnrichmentMarcRecord, existingCommonMarcRecord);
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), expectedEnrichmentMarcRecord, RAWREPO_PROVIDER_ID_FBS);
    }

    @Test
    void testDeleteEnrichmentWithIdenticalClassification() throws Exception {
        final String existingCommonRecordData = "001 00 *a 61443886 *b 870970 *c 20220616134845 *d 20210922 *f a \n" +
                "004 00 *r n *a e \n" +
                "008 00 *t m *u f *a 2021 *b dk *d y *l dan *x 06 *v 0 \n" +
                "009 00 *a a *g xx \n" +
                "021 00 *e 9788789074450 *c hf. \n" +
                "032 00 *x ACC202138 *a DBF202144 *x BKM202144 \n" +
                "100 00 *a Solsort *h Olga *4 aut *4 art \n" +
                "245 00 *a Mindesmærke for de trolddomsanklagede\n" +
                "260 00 *a [Køge] *b KØS Museum for kunst i det offentlige rum *c 2021 \n" +
                "300 00 *a 145 sider *b ill. (nogle i farver) \n" +
                "652 00 *m 72 *a Solsort *h Olga \n" +
                "996 00 *a DBC";

        final String updatingCommonRecordData = "001 00 *a 61443886 *b 870970 *c 20220616134845 *d 20210922 *f a \n" +
                "004 00 *r n *a e \n" +
                "008 00 *t m *u f *a 2021 *b dk *d y *l dan *x 06 *v 0 \n" +
                "009 00 *a a *g xx \n" +
                "021 00 *e 9788789074450 *c hf. \n" +
                "032 00 *x ACC202138 *a DBF202144 *x BKM202144 \n" +
                "100 00 *a Ravn *h Olga *4 aut *4 art \n" +
                "245 00 *a Mindesmærke for de trolddomsanklagede\n" +
                "260 00 *a [Køge] *b KØS Museum for kunst i det offentlige rum *c 2021 \n" +
                "300 00 *a 145 sider *b ill. (nogle i farver) \n" +
                "652 00 *m 72 *a Ravn *h Olga \n" +
                "996 00 *a DBC";

        final String existingEnrichmentRecordData = "001 00 *a 61443886 *b 710100 *c 20220616134845 *d 20210922 *f a \n" +
                "004 00 *r n *a e \n" +
                "008 00 *t m *u f *a 2021 *b dk *d y *l dan *x 06 *v 0 \n" +
                "009 00 *a a *g xx \n" +
                "100 00 *a Ravn *h Olga *4 aut *4 art \n" +
                "245 00 *a Mindesmærke for de trolddomsanklagede\n" +
                "652 00 *m 72 *a Ravn *h Olga ";

        // Note that 004 *r won't be updated yet. That happens in the DeleteRecordAction
        final String expectedEnrichmentRecordData = "001 00 *a 61443886 *b 710100 *c 20220616134845 *d 20210922 *f a \n" +
                "004 00 *r n *a e \n" +
                "008 00 *t m *u f *a 2021 *b dk *d y *l dan *x 06 *v 0 \n" +
                "009 00 *a a *g xx \n" +
                "100 00 *a Ravn *h Olga *4 aut *4 art \n" +
                "245 00 *a Mindesmærke for de trolddomsanklagede\n" +
                "652 00 *m 72 *a Ravn *h Olga ";

        final MarcRecord existingCommonMarcRecord = UpdateRecordContentTransformer.readRecordFromString(existingCommonRecordData);
        final MarcRecord updatingCommonMarcRecord = UpdateRecordContentTransformer.readRecordFromString(updatingCommonRecordData);
        final MarcRecord existingEnrichmentMarcRecord = UpdateRecordContentTransformer.readRecordFromString(existingEnrichmentRecordData);
        final MarcRecord expectedEnrichmentMarcRecord = UpdateRecordContentTransformer.readRecordFromString(expectedEnrichmentRecordData);

        when(state.getLibraryRecordsHandler().hasClassificationsChanged(existingEnrichmentMarcRecord, updatingCommonMarcRecord)).thenReturn(false);
        when(state.getRawRepo().recordExists("61443886", 710100)).thenReturn(true);

        final UpdateClassificationsInEnrichmentRecordAction instance = new UpdateClassificationsInEnrichmentRecordAction(state, settings, existingEnrichmentMarcRecord, "870970");
        instance.setCurrentCommonRecord(existingCommonMarcRecord);
        instance.setUpdatingCommonRecord(updatingCommonMarcRecord);
        assertThat(instance.performAction(), is(ServiceResult.newOkResult()));

        final List<ServiceAction> children = instance.children();
        assertThat(children.size(), is(3));

        final ListIterator<ServiceAction> iterator = children.listIterator();
        AssertActionsUtil.assertEnqueueRecordAction(iterator.next(), state.getRawRepo(), expectedEnrichmentMarcRecord, RAWREPO_PROVIDER_ID_FBS);
        AssertActionsUtil.assertRemoveLinksAction(iterator.next(), state.getRawRepo(), expectedEnrichmentMarcRecord);
        AssertActionsUtil.assertDeleteRecordAction(iterator.next(), state.getRawRepo(), expectedEnrichmentMarcRecord, MarcXChangeMimeType.ENRICHMENT);
    }

}
