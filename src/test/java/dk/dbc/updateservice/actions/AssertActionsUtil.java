/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcField;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordFactory;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.MarcRecordWriter;
import dk.dbc.common.records.MarcSubField;
import dk.dbc.common.records.utils.RecordContentTransformer;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.auth.Authenticator;
import dk.dbc.updateservice.dto.AuthenticationDTO;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.solr.SolrFBS;
import dk.dbc.updateservice.update.HoldingsItems;
import dk.dbc.updateservice.update.LibraryRecordsHandler;
import dk.dbc.updateservice.update.OpenAgencyService;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.RawRepoRecordMock;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.utils.IOUtils;
import org.junit.Assert;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

/**
 * Utility functions to reuse code in different test classes.
 */
public class AssertActionsUtil {
    private static final XLogger logger = XLoggerFactory.getXLogger(AssertActionsUtil.class);
    public static final String COMMON_SINGLE_RECORD_RESOURCE = "actions/common_enrichment.marc";
    public static final String COMMON_MAIN_RECORD_RESOURCE = "actions/rawrepo-main.marc";
    public static final String COMMON_MAIN_ENRICHMENT_RECORD_RESOURCE = "actions/rawrepo-main-enrichment.marc";
    public static final String COMMON_VOLUME_RECORD_RESOURCE = "actions/rawrepo-volume.marc";
    public static final String LOCAL_MAIN_RECORD_RESOURCE = "actions/rawrepo-local-main.marc";
    public static final String LOCAL_VOLUME_RECORD_RESOURCE = "actions/rawrepo-local-volume.marc";
    public static final String ENRICHMENT_SINGLE_RECORD_RESOURCE = "actions/enrichment.marc";
    public static final String LOCAL_SINGLE_RECORD_RESOURCE = "actions/book.marc";
    public static final String COMMON_SCHOOL_RECORD_RESOURCE = "actions/common_school_enrichment.marc";
    public static final String SCHOOL_RECORD_RESOURCE = "actions/school_enrichment.marc";
    public static final String VOLUME_RECORD_RESOURCE = "actions/volume.marc";
    public static final String COMMON_RECORD_CLASSIFICATION = "actions/common_classification.marc";
    public static final String NATIONAL_COMMON_RECORD = "actions/national-common-record.marc";
    public static final String LITTOLK_COMMON = "actions/littolk-common.marc";
    public static final String LITTOLK_ENRICHMENT = "actions/littolk-enrichment.marc";
    public static final String MATVURD_1 = "actions/matvurd1.marc";
    public static final String MATVURD_2 = "actions/matvurd2.marc";
    public static final String MATVURD_3 = "actions/matvurd3.marc";
    public static final String MATVURD_4 = "actions/matvurd4.marc";
    public static final String MATVURD_5 = "actions/matvurd5.marc";
    public static final String MATVURD_6 = "actions/matvurd6.marc";

    public static MarcRecord loadRecord(String filename) throws IOException {
        InputStream is = AssertActionsUtil.class.getResourceAsStream("/dk/dbc/updateservice/" + filename);
        return MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));
    }

    public static MarcRecord loadRecord(String filename, String newRecordId) throws IOException {
        logger.entry();

        MarcRecord record = null;
        try {
            record = loadRecord(filename);
            new MarcRecordWriter(record).addOrReplaceSubfield("001", "a", newRecordId);

            return record;
        } finally {
            logger.exit(record);
        }
    }

    public static MarcRecord loadRecordAndMarkForDeletion(String filename) throws IOException {
        MarcRecord record = loadRecord(filename);
        new MarcRecordWriter(record).markForDeletion();

        return record;
    }

    public static MarcRecord loadRecordAndMarkForDeletion(String filename, String newRecordId) throws IOException {
        logger.entry();

        MarcRecord record = null;
        try {
            record = loadRecordAndMarkForDeletion(filename);
            new MarcRecordWriter(record).addOrReplaceSubfield("001", "a", newRecordId);

            return record;
        } finally {
            logger.exit(record);
        }
    }

    public static Set<RecordId> createRecordSet(MarcRecord... records) {
        Set<RecordId> result = new HashSet<>();

        for (MarcRecord record : records) {
            result.add(new RecordId(getRecordId(record), getAgencyIdAsInt(record)));
        }

        return result;
    }

    public static Set<Integer> createAgenciesSet(Integer... agencies) {
        Set<Integer> result = new HashSet<>();

        for (Integer agencyId : agencies) {
            result.add(agencyId);
        }

        return result;
    }

    public static Record createRawRepoRecord(MarcRecord record, String mimetype) throws JAXBException, UnsupportedEncodingException {
        RawRepoRecordMock result = new RawRepoRecordMock(getRecordId(record), getAgencyIdAsInt(record));
        result.setMimeType(mimetype);
        result.setDeleted(false);
        result.setContent(RecordContentTransformer.encodeRecord(record));

        return result;
    }

    public static String getRecordId(MarcRecord record) {
        return new MarcRecordReader(record).getRecordId();
    }

    public static String getAgencyId(MarcRecord record) {
        return new MarcRecordReader(record).getAgencyId();
    }

    public static int getAgencyIdAsInt(MarcRecord record) {
        return new MarcRecordReader(record).getAgencyIdAsInt();
    }

    public static void assertAuthenticateRecordAction(ServiceAction action, MarcRecord record, Authenticator authenticator, AuthenticationDTO AuthenticationDTO) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(AuthenticateRecordAction.class.getName()));

        AuthenticateRecordAction authenticateRecordAction = (AuthenticateRecordAction) action;
        Assert.assertThat(authenticateRecordAction.state.readRecord(), is(record));
        Assert.assertThat(authenticateRecordAction.state.getAuthenticator(), is(authenticator));
        Assert.assertThat(authenticateRecordAction.state.getUpdateServiceRequestDTO().getAuthenticationDTO(), is(AuthenticationDTO));
    }

    public static void assertUpdateCommonRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, String groupId, LibraryRecordsHandler recordsHandler, HoldingsItems holdingsItems, OpenAgencyService openAgencyService) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(UpdateCommonRecordAction.class.getName()));

        UpdateCommonRecordAction updateCommonRecordAction = (UpdateCommonRecordAction) action;
        Assert.assertThat(updateCommonRecordAction.getRawRepo(), is(rawRepo));
        Assert.assertThat(updateCommonRecordAction.getRecord(), is(record));
        Assert.assertThat(updateCommonRecordAction.state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), equalTo(groupId));
        Assert.assertThat(updateCommonRecordAction.state.getLibraryRecordsHandler(), is(recordsHandler));
        Assert.assertThat(updateCommonRecordAction.state.getHoldingsItems(), is(holdingsItems));
        Assert.assertThat(updateCommonRecordAction.state.getOpenAgencyService(), is(openAgencyService));
    }

    public static void assertCreateSingleRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, SolrFBS solrService, String providerId) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(CreateSingleRecordAction.class.getName()));

        CreateSingleRecordAction createSingleRecordAction = (CreateSingleRecordAction) action;
        Assert.assertThat(createSingleRecordAction.getRawRepo(), is(rawRepo));
        Assert.assertThat(createSingleRecordAction.getRecord(), is(record));
        Assert.assertThat(createSingleRecordAction.state.getSolrFBS(), is(solrService));
        Assert.assertThat(createSingleRecordAction.settings.getProperty(createSingleRecordAction.state.getRawRepoProviderId()), equalTo(providerId));
    }

    public static void assertCreateVolumeRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, HoldingsItems holdingsItems, SolrFBS solrService, String providerId) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(CreateVolumeRecordAction.class.getName()));

        CreateVolumeRecordAction createVolumeRecordAction = (CreateVolumeRecordAction) action;
        Assert.assertThat(createVolumeRecordAction.getRawRepo(), is(rawRepo));
        Assert.assertThat(createVolumeRecordAction.getRecord(), is(record));
        Assert.assertThat(createVolumeRecordAction.state.getHoldingsItems(), is(holdingsItems));
        Assert.assertThat(createVolumeRecordAction.state.getSolrFBS(), is(solrService));
        Assert.assertThat(createVolumeRecordAction.settings.getProperty(createVolumeRecordAction.state.getRawRepoProviderId()), equalTo(providerId));
    }

    public static void assertOverwriteVolumeRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, String groupId, LibraryRecordsHandler recordsHandler, HoldingsItems holdingsItems) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(OverwriteVolumeRecordAction.class.getName()));

        OverwriteVolumeRecordAction overwriteVolumeRecordAction = (OverwriteVolumeRecordAction) action;
        assertThat(overwriteVolumeRecordAction.getRawRepo(), is(rawRepo));
        assertThat(overwriteVolumeRecordAction.getRecord(), is(record));
        assertThat(overwriteVolumeRecordAction.state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), equalTo(groupId));
        assertThat(overwriteVolumeRecordAction.state.getHoldingsItems(), is(holdingsItems));
        assertThat(overwriteVolumeRecordAction.state.getLibraryRecordsHandler(), is(recordsHandler));
    }

    public static void assertUpdateLocalRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, HoldingsItems holdingsItems) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(UpdateLocalRecordAction.class.getName()));

        UpdateLocalRecordAction updateLocalRecordAction = (UpdateLocalRecordAction) action;
        Assert.assertThat(updateLocalRecordAction.getRawRepo(), is(rawRepo));
        Assert.assertThat(updateLocalRecordAction.getRecord(), is(record));
        Assert.assertThat(updateLocalRecordAction.state.getHoldingsItems(), is(holdingsItems));
    }

    public static void assertUpdateEnrichmentRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, LibraryRecordsHandler recordsHandler, HoldingsItems holdingsItems) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(UpdateEnrichmentRecordAction.class.getName()));

        UpdateEnrichmentRecordAction updateEnrichmentRecordAction = (UpdateEnrichmentRecordAction) action;
        Assert.assertThat(updateEnrichmentRecordAction.getRawRepo(), is(rawRepo));
        Assert.assertThat(updateEnrichmentRecordAction.getRecord(), is(record));
        Assert.assertThat(updateEnrichmentRecordAction.state.getLibraryRecordsHandler(), is(recordsHandler));
        Assert.assertThat(updateEnrichmentRecordAction.state.getHoldingsItems(), is(holdingsItems));
    }

    public static void assertSchoolCommonRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, LibraryRecordsHandler recordsHandler, HoldingsItems holdingsItems, String providerId) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(UpdateSchoolCommonRecord.class.getName()));

        UpdateSchoolCommonRecord updateSchoolCommonRecord = (UpdateSchoolCommonRecord) action;
        Assert.assertThat(updateSchoolCommonRecord.getRawRepo(), is(rawRepo));
        Assert.assertThat(updateSchoolCommonRecord.getRecord(), is(record));
        Assert.assertThat(updateSchoolCommonRecord.state.getLibraryRecordsHandler(), is(recordsHandler));
        Assert.assertThat(updateSchoolCommonRecord.state.getHoldingsItems(), is(holdingsItems));
        Assert.assertThat(updateSchoolCommonRecord.settings.getProperty(updateSchoolCommonRecord.state.getRawRepoProviderId()), equalTo(providerId));
    }

    public static void assertSchoolEnrichmentRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, LibraryRecordsHandler recordsHandler, HoldingsItems holdingsItems, String providerId) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(UpdateSchoolEnrichmentRecordAction.class.getName()));

        UpdateSchoolEnrichmentRecordAction updateSchoolEnrichmentRecordAction = (UpdateSchoolEnrichmentRecordAction) action;
        Assert.assertThat(updateSchoolEnrichmentRecordAction.getRawRepo(), is(rawRepo));
        Assert.assertThat(updateSchoolEnrichmentRecordAction.getRecord(), is(record));
        Assert.assertThat(updateSchoolEnrichmentRecordAction.state.getLibraryRecordsHandler(), is(recordsHandler));
        Assert.assertThat(updateSchoolEnrichmentRecordAction.state.getHoldingsItems(), is(holdingsItems));
        Assert.assertThat(updateSchoolEnrichmentRecordAction.settings.getProperty(updateSchoolEnrichmentRecordAction.state.getRawRepoProviderId()), equalTo(providerId));
    }

    public static void assertStoreRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(StoreRecordAction.class.getName()));

        StoreRecordAction storeRecordAction = (StoreRecordAction) action;
        assertThat(storeRecordAction.getRawRepo(), is(rawRepo));
        assertThat(storeRecordAction.getRecord(), is(record));
        assertThat(storeRecordAction.getMimetype(), equalTo(MarcXChangeMimeType.MARCXCHANGE));
    }

    public static void assertDeleteRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, String mimetype) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(DeleteRecordAction.class.getName()));

        DeleteRecordAction deleteRecordAction = (DeleteRecordAction) action;
        assertThat(deleteRecordAction.getRawRepo(), is(rawRepo));
        assertThat(deleteRecordAction.getRecord(), is(record));
        assertThat(deleteRecordAction.getMimetype(), equalTo(mimetype));
    }

    public static void assertDeleteCommonRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, LibraryRecordsHandler recordsHandler, HoldingsItems holdingsItems, String providerId) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(DeleteCommonRecordAction.class.getName()));

        DeleteCommonRecordAction deleteCommonRecordAction = (DeleteCommonRecordAction) action;
        assertThat(deleteCommonRecordAction.getRawRepo(), is(rawRepo));
        assertThat(deleteCommonRecordAction.getRecord(), is(record));
        assertThat(deleteCommonRecordAction.state.getLibraryRecordsHandler(), is(recordsHandler));
        assertThat(deleteCommonRecordAction.state.getHoldingsItems(), is(holdingsItems));
        assertThat(deleteCommonRecordAction.settings.getProperty(deleteCommonRecordAction.state.getRawRepoProviderId()), equalTo(providerId));
    }

    public static void assertLinkRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, MarcRecord target) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(LinkRecordAction.class.getName()));

        LinkRecordAction linkRecordAction = (LinkRecordAction) action;
        assertThat(linkRecordAction.getRawRepo(), is(rawRepo));
        assertThat(linkRecordAction.getRecord(), is(record));

        String recordId = getRecordId(target);
        int agencyId = getAgencyIdAsInt(target);
        assertThat(linkRecordAction.getLinkToRecordId(), equalTo(new RecordId(recordId, agencyId)));
    }

    public static void assertLinkAuthorityRecordsAction(ServiceAction action, RawRepo rawRepo, MarcRecord record) {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(LinkAuthorityRecordsAction.class.getName()));

        LinkAuthorityRecordsAction linkAuthorityRecordsAction = (LinkAuthorityRecordsAction) action;
        assertThat(linkAuthorityRecordsAction.getRawRepo(), is(rawRepo));
        assertThat(linkAuthorityRecordsAction.getRecord(), is(record));
    }

    public static void assertRemoveLinksAction(ServiceAction action, RawRepo rawRepo, MarcRecord record) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(RemoveLinksAction.class.getName()));

        RemoveLinksAction removeLinksAction = (RemoveLinksAction) action;
        assertThat(removeLinksAction.getRawRepo(), is(rawRepo));
        assertThat(removeLinksAction.getRecord(), is(record));
    }

    public static void assertCreateEnrichmentAction(ServiceAction action, RawRepo rawRepo, MarcRecord commonRecord, String agencyId, String commonRecordId) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(CreateEnrichmentRecordWithClassificationsAction.class.getName()));

        CreateEnrichmentRecordWithClassificationsAction createEnrichmentRecordWithClassificationsAction = (CreateEnrichmentRecordWithClassificationsAction) action;
        assertThat(createEnrichmentRecordWithClassificationsAction.state.getRawRepo(), is(rawRepo));
        assertThat(createEnrichmentRecordWithClassificationsAction.getUpdatingCommonRecord(), equalTo(commonRecord));
        assertThat(createEnrichmentRecordWithClassificationsAction.agencyId, equalTo(agencyId));
        assertThat(createEnrichmentRecordWithClassificationsAction.getTargetRecordId(), equalTo(commonRecordId));
    }

    public static void assertUpdateEnrichmentRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, LibraryRecordsHandler recordsHandler, HoldingsItems holdingsItems, String providerId) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(UpdateEnrichmentRecordAction.class.getName()));

        UpdateEnrichmentRecordAction updateEnrichmentRecordAction = (UpdateEnrichmentRecordAction) action;
        assertThat(updateEnrichmentRecordAction.getRawRepo(), is(rawRepo));
        assertThat(updateEnrichmentRecordAction.getRecord(), equalTo(record));
        assertThat(updateEnrichmentRecordAction.state.getLibraryRecordsHandler(), is(recordsHandler));
        assertThat(updateEnrichmentRecordAction.state.getHoldingsItems(), is(holdingsItems));
        assertThat(updateEnrichmentRecordAction.settings.getProperty(updateEnrichmentRecordAction.state.getRawRepoProviderId()), is(providerId));
    }

    public static void assertUpdateClassificationsInEnrichmentRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord commonRecord, MarcRecord record) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(UpdateClassificationsInEnrichmentRecordAction.class.getName()));

        UpdateClassificationsInEnrichmentRecordAction updateClassificationsInEnrichmentRecordAction = (UpdateClassificationsInEnrichmentRecordAction) action;
        assertThat(updateClassificationsInEnrichmentRecordAction.state.getRawRepo(), is(rawRepo));
        assertThat(updateClassificationsInEnrichmentRecordAction.getUpdatingCommonRecord(), equalTo(commonRecord));
        assertThat(updateClassificationsInEnrichmentRecordAction.getEnrichmentRecord(), equalTo(record));
    }

    public static void assertDoubleRecordFrontendAction(ServiceAction action, MarcRecord record, Scripter scripter) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(DoubleRecordFrontendAction.class.getName()));

        DoubleRecordFrontendAction doubleRecordFrontendAction = (DoubleRecordFrontendAction) action;
        Assert.assertThat(doubleRecordFrontendAction.state.getMarcRecord(), is(record));
        Assert.assertThat(doubleRecordFrontendAction.state.getScripter(), is(scripter));
    }

    public static void assertDoubleRecordCheckingAction(ServiceAction action, MarcRecord record, Scripter scripter) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(DoubleRecordCheckingAction.class.getName()));

        DoubleRecordCheckingAction doubleRecordCheckingAction = (DoubleRecordCheckingAction) action;
        Assert.assertThat(doubleRecordCheckingAction.record, is(record));
        Assert.assertThat(doubleRecordCheckingAction.state.getScripter(), is(scripter));
    }

    public static void assertEnqueueRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, String providerId, String mimetype) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(EnqueueRecordAction.class.getName()));

        EnqueueRecordAction enqueueRecordAction = (EnqueueRecordAction) action;
        assertThat(enqueueRecordAction.getRawRepo(), is(rawRepo));
        assertThat(enqueueRecordAction.getRecord(), is(record));
        assertThat(enqueueRecordAction.settings.getProperty(enqueueRecordAction.state.getRawRepoProviderId()), is(providerId));
    }

    public static void assertMoveEnrichmentRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, String targetRecordId, Properties settings) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(MoveEnrichmentRecordAction.class.getName()));

        MoveEnrichmentRecordAction moveEnrichmentRecordAction = (MoveEnrichmentRecordAction) action;
        assertThat(moveEnrichmentRecordAction.getRawRepo(), is(rawRepo));
        assertThat(moveEnrichmentRecordAction.getRecord(), is(record));
        assertThat(moveEnrichmentRecordAction.getTargetRecordId(), is(targetRecordId));
        assertThat(moveEnrichmentRecordAction.settings, equalTo(settings));
    }

    public static void assertOverwriteSingleRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, LibraryRecordsHandler recordsHandler, HoldingsItems holdingsItems, OpenAgencyService openAgencyService, String groupId) {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(OverwriteSingleRecordAction.class.getName()));

        OverwriteSingleRecordAction overwriteSingleRecordAction = (OverwriteSingleRecordAction) action;
        assertThat(overwriteSingleRecordAction.getRawRepo(), is(rawRepo));
        assertThat(overwriteSingleRecordAction.record, is(record));
        assertThat(overwriteSingleRecordAction.state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), equalTo(groupId));
        assertThat(overwriteSingleRecordAction.state.getHoldingsItems(), is(holdingsItems));
        assertThat(overwriteSingleRecordAction.state.getOpenAgencyService(), is(openAgencyService));
        assertThat(overwriteSingleRecordAction.state.getLibraryRecordsHandler(), is(recordsHandler));
    }

    /**
     * Helper function for comparing two full records where 001 *c and *d are excluded from the comparison
     *
     * @param actual   The actual record
     * @param expected The expected record
     * @throws UpdateException
     */
    public static void assertRecord(MarcRecord actual, MarcRecord expected) throws UpdateException {
        assertThat(actual, notNullValue());
        assertThat(expected, notNullValue());
        assertThat(actual.getFields().size(), is(expected.getFields().size()));
        assertThat(actual.getFields().get(1), is(expected.getFields().get(1)));

        for (int f = 0; f < actual.getFields().size(); f++) {
            MarcField actualField = actual.getFields().get(f);
            MarcField expectedField = expected.getFields().get(f);
            if ("001".equals(actualField.getName())) {
                List<String> ignoredSubfields = Arrays.asList("c", "d");
                assertThat(actualField.getSubfields().size(), is(expectedField.getSubfields().size()));

                for (int s = 0; s < actualField.getSubfields().size(); s++) {
                    MarcSubField actualSubfield = actualField.getSubfields().get(s);
                    MarcSubField expectedSubfield = expectedField.getSubfields().get(s);
                    if (!ignoredSubfields.contains(actualSubfield.getName())) {
                        assertThat(actualSubfield, is(expectedSubfield));
                    }
                }
            } else {
                assertThat(actualField, is(expectedField));
            }
        }
    }
}
