package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordFactory;
import dk.dbc.iscrum.records.MarcRecordReader;
import dk.dbc.iscrum.records.MarcRecordWriter;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.updateservice.auth.Authenticator;
import dk.dbc.updateservice.dto.AuthenticationDto;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.update.HoldingsItems;
import dk.dbc.updateservice.update.LibraryRecordsHandler;
import dk.dbc.updateservice.update.OpenAgencyService;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.RawRepoEncoder;
import dk.dbc.updateservice.update.RawRepoRecordMock;
import dk.dbc.updateservice.update.SolrService;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.ws.JNDIResources;
import org.junit.Assert;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashSet;
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
    public static final String COMMON_SINGLE_RECORD_RESOURCE = "common_enrichment.marc";
    public static final String COMMON_MAIN_RECORD_RESOURCE = "rawrepo-main.marc";
    public static final String COMMON_MAIN_ENRICHMENT_RECORD_RESOURCE = "rawrepo-main-enrichment.marc";
    public static final String COMMON_VOLUME_RECORD_RESOURCE = "rawrepo-volume.marc";
    public static final String LOCAL_MAIN_RECORD_RESOURCE = "rawrepo-local-main.marc";
    public static final String LOCAL_VOLUME_RECORD_RESOURCE = "rawrepo-local-volume.marc";
    public static final String ENRICHMENT_SINGLE_RECORD_RESOURCE = "enrichment.marc";
    public static final String LOCAL_SINGLE_RECORD_RESOURCE = "book.marc";
    public static final String COMMON_SCHOOL_RECORD_RESOURCE = "common_school_enrichment.marc";
    public static final String SCHOOL_RECORD_RESOURCE = "school_enrichment.marc";
    public static final String DELETED_BOOK_TO_STORE_RESOURCE = "deleted_book_to_store.marc";
    public static final String VOLUME_RECORD_RESOURCE = "volume.marc";

    public static MarcRecord loadRecord(String filename) throws IOException {
        InputStream is = AssertActionsUtil.class.getResourceAsStream("/dk/dbc/updateservice/actions/" + filename);
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
            result.add(new RecordId(getRecordId(record), getAgencyIdAsInteger(record)));
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
        RawRepoRecordMock result = new RawRepoRecordMock(getRecordId(record), getAgencyIdAsInteger(record));
        result.setMimeType(mimetype);
        result.setDeleted(false);
        result.setContent(new RawRepoEncoder().encodeRecord(record));

        return result;
    }

    public static String getRecordId(MarcRecord record) {
        return new MarcRecordReader(record).recordId();
    }

    public static String getAgencyId(MarcRecord record) {
        return new MarcRecordReader(record).agencyId();
    }

    public static Integer getAgencyIdAsInteger(MarcRecord record) {
        return new MarcRecordReader(record).agencyIdAsInteger();
    }

    public static void assertAuthenticateRecordAction(ServiceAction action, MarcRecord record, Authenticator authenticator, AuthenticationDto authenticationDto) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(AuthenticateRecordAction.class.getName()));

        action.checkState();

        AuthenticateRecordAction authenticateRecordAction = (AuthenticateRecordAction) action;
        Assert.assertThat(authenticateRecordAction.state.readRecord(), is(record));
        Assert.assertThat(authenticateRecordAction.state.getAuthenticator(), is(authenticator));
        Assert.assertThat(authenticateRecordAction.state.getUpdateServiceRequestDto().getAuthenticationDto(), is(authenticationDto));
    }

    public static void assertUpdateCommonRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, String groupId, LibraryRecordsHandler recordsHandler, HoldingsItems holdingsItems, OpenAgencyService openAgencyService) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(UpdateCommonRecordAction.class.getName()));

        action.checkState();

        UpdateCommonRecordAction updateCommonRecordAction = (UpdateCommonRecordAction) action;
        Assert.assertThat(updateCommonRecordAction.getRawRepo(), is(rawRepo));
        Assert.assertThat(updateCommonRecordAction.getRecord(), is(record));
        Assert.assertThat(updateCommonRecordAction.state.getUpdateServiceRequestDto().getAuthenticationDto().getGroupId(), equalTo(groupId));
        Assert.assertThat(updateCommonRecordAction.state.getLibraryRecordsHandler(), is(recordsHandler));
        Assert.assertThat(updateCommonRecordAction.state.getHoldingsItems(), is(holdingsItems));
        Assert.assertThat(updateCommonRecordAction.state.getOpenAgencyService(), is(openAgencyService));
    }

    public static void assertCreateSingleRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, SolrService solrService, String providerId) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(CreateSingleRecordAction.class.getName()));

        action.checkState();

        CreateSingleRecordAction createSingleRecordAction = (CreateSingleRecordAction) action;
        Assert.assertThat(createSingleRecordAction.getRawRepo(), is(rawRepo));
        Assert.assertThat(createSingleRecordAction.getRecord(), is(record));
        Assert.assertThat(createSingleRecordAction.state.getSolrService(), is(solrService));
        Assert.assertThat(createSingleRecordAction.settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID), equalTo(providerId));
    }

    public static void assertCreateVolumeRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, HoldingsItems holdingsItems, SolrService solrService, String providerId) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(CreateVolumeRecordAction.class.getName()));

        action.checkState();

        CreateVolumeRecordAction createVolumeRecordAction = (CreateVolumeRecordAction) action;
        Assert.assertThat(createVolumeRecordAction.getRawRepo(), is(rawRepo));
        Assert.assertThat(createVolumeRecordAction.getRecord(), is(record));
        Assert.assertThat(createVolumeRecordAction.state.getHoldingsItems(), is(holdingsItems));
        Assert.assertThat(createVolumeRecordAction.state.getSolrService(), is(solrService));
        Assert.assertThat(createVolumeRecordAction.settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID), equalTo(providerId));
    }

    public static void assertOverwriteVolumeRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, String groupId, LibraryRecordsHandler recordsHandler, HoldingsItems holdingsItems) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(OverwriteVolumeRecordAction.class.getName()));

        action.checkState();

        OverwriteVolumeRecordAction overwriteVolumeRecordAction = (OverwriteVolumeRecordAction) action;
        assertThat(overwriteVolumeRecordAction.getRawRepo(), is(rawRepo));
        assertThat(overwriteVolumeRecordAction.getRecord(), is(record));
        assertThat(overwriteVolumeRecordAction.state.getUpdateServiceRequestDto().getAuthenticationDto().getGroupId(), equalTo(groupId));
        assertThat(overwriteVolumeRecordAction.state.getHoldingsItems(), is(holdingsItems));
        assertThat(overwriteVolumeRecordAction.state.getLibraryRecordsHandler(), is(recordsHandler));
    }

    public static void assertUpdateLocalRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, HoldingsItems holdingsItems) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(UpdateLocalRecordAction.class.getName()));

        action.checkState();

        UpdateLocalRecordAction updateLocalRecordAction = (UpdateLocalRecordAction) action;
        Assert.assertThat(updateLocalRecordAction.getRawRepo(), is(rawRepo));
        Assert.assertThat(updateLocalRecordAction.getRecord(), is(record));
        Assert.assertThat(updateLocalRecordAction.state.getHoldingsItems(), is(holdingsItems));
    }

    public static void assertUpdateEnrichmentRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, LibraryRecordsHandler recordsHandler, HoldingsItems holdingsItems) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(UpdateEnrichmentRecordAction.class.getName()));

        action.checkState();

        UpdateEnrichmentRecordAction updateEnrichmentRecordAction = (UpdateEnrichmentRecordAction) action;
        Assert.assertThat(updateEnrichmentRecordAction.getRawRepo(), is(rawRepo));
        Assert.assertThat(updateEnrichmentRecordAction.getRecord(), is(record));
        Assert.assertThat(updateEnrichmentRecordAction.state.getLibraryRecordsHandler(), is(recordsHandler));
        Assert.assertThat(updateEnrichmentRecordAction.state.getHoldingsItems(), is(holdingsItems));
    }

    public static void assertSchoolCommonRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, LibraryRecordsHandler recordsHandler, HoldingsItems holdingsItems, String providerId) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(UpdateSchoolCommonRecord.class.getName()));

        action.checkState();

        UpdateSchoolCommonRecord updateSchoolCommonRecord = (UpdateSchoolCommonRecord) action;
        Assert.assertThat(updateSchoolCommonRecord.getRawRepo(), is(rawRepo));
        Assert.assertThat(updateSchoolCommonRecord.getRecord(), is(record));
        Assert.assertThat(updateSchoolCommonRecord.state.getLibraryRecordsHandler(), is(recordsHandler));
        Assert.assertThat(updateSchoolCommonRecord.state.getHoldingsItems(), is(holdingsItems));
        Assert.assertThat(updateSchoolCommonRecord.settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID), equalTo(providerId));
    }

    public static void assertSchoolEnrichmentRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, LibraryRecordsHandler recordsHandler, HoldingsItems holdingsItems, String providerId) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(UpdateSchoolEnrichmentRecordAction.class.getName()));

        action.checkState();

        UpdateSchoolEnrichmentRecordAction updateSchoolEnrichmentRecordAction = (UpdateSchoolEnrichmentRecordAction) action;
        Assert.assertThat(updateSchoolEnrichmentRecordAction.getRawRepo(), is(rawRepo));
        Assert.assertThat(updateSchoolEnrichmentRecordAction.getRecord(), is(record));
        Assert.assertThat(updateSchoolEnrichmentRecordAction.state.getLibraryRecordsHandler(), is(recordsHandler));
        Assert.assertThat(updateSchoolEnrichmentRecordAction.state.getHoldingsItems(), is(holdingsItems));
        Assert.assertThat(updateSchoolEnrichmentRecordAction.settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID), equalTo(providerId));
    }

    public static void assertStoreRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(StoreRecordAction.class.getName()));

        action.checkState();

        StoreRecordAction storeRecordAction = (StoreRecordAction) action;
        assertThat(storeRecordAction.getRawRepo(), is(rawRepo));
        assertThat(storeRecordAction.getRecord(), is(record));
        assertThat(storeRecordAction.getMimetype(), equalTo(UpdateCommonRecordAction.MIMETYPE));
    }

    public static void assertDeleteRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, String mimetype) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(DeleteRecordAction.class.getName()));

        action.checkState();

        DeleteRecordAction deleteRecordAction = (DeleteRecordAction) action;
        assertThat(deleteRecordAction.getRawRepo(), is(rawRepo));
        assertThat(deleteRecordAction.getRecord(), is(record));
        assertThat(deleteRecordAction.getMimetype(), equalTo(mimetype));
    }

    public static void assertCommonDeleteRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, LibraryRecordsHandler recordsHandler, HoldingsItems holdingsItems, String providerId) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(DeleteCommonRecordAction.class.getName()));

        action.checkState();

        DeleteCommonRecordAction deleteCommonRecordAction = (DeleteCommonRecordAction) action;
        assertThat(deleteCommonRecordAction.getRawRepo(), is(rawRepo));
        assertThat(deleteCommonRecordAction.getRecord(), is(record));
        assertThat(deleteCommonRecordAction.state.getLibraryRecordsHandler(), is(recordsHandler));
        assertThat(deleteCommonRecordAction.state.getHoldingsItems(), is(holdingsItems));
        assertThat(deleteCommonRecordAction.settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID), equalTo(providerId));
    }

    public static void assertLinkRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, MarcRecord target) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(LinkRecordAction.class.getName()));

        action.checkState();

        LinkRecordAction linkRecordAction = (LinkRecordAction) action;
        assertThat(linkRecordAction.getRawRepo(), is(rawRepo));
        assertThat(linkRecordAction.getRecord(), is(record));

        String recordId = getRecordId(target);
        Integer agencyId = getAgencyIdAsInteger(target);
        assertThat(linkRecordAction.getLinkToRecordId(), equalTo(new RecordId(recordId, agencyId)));
    }

    public static void assertRemoveLinksAction(ServiceAction action, RawRepo rawRepo, MarcRecord record) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(RemoveLinksAction.class.getName()));

        action.checkState();

        RemoveLinksAction removeLinksAction = (RemoveLinksAction) action;
        assertThat(removeLinksAction.getRawRepo(), is(rawRepo));
        assertThat(removeLinksAction.getRecord(), is(record));
    }

    public static void assertCreateEnrichmentAction(ServiceAction action, RawRepo rawRepo, MarcRecord commonRecord, String agencyId, String commonRecordId) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(CreateEnrichmentRecordWithClassificationsAction.class.getName()));

        action.checkState();

        CreateEnrichmentRecordWithClassificationsAction createEnrichmentRecordWithClassificationsAction = (CreateEnrichmentRecordWithClassificationsAction) action;
        assertThat(createEnrichmentRecordWithClassificationsAction.state.getRawRepo(), is(rawRepo));
        assertThat(createEnrichmentRecordWithClassificationsAction.getUpdatingCommonRecord(), equalTo(commonRecord));
        assertThat(createEnrichmentRecordWithClassificationsAction.agencyId, equalTo(agencyId));
        assertThat(createEnrichmentRecordWithClassificationsAction.getCommonRecordId(), equalTo(commonRecordId));
    }

    public static void assertUpdateEnrichmentRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, LibraryRecordsHandler recordsHandler, HoldingsItems holdingsItems, String providerId) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(UpdateEnrichmentRecordAction.class.getName()));

        action.checkState();

        UpdateEnrichmentRecordAction updateEnrichmentRecordAction = (UpdateEnrichmentRecordAction) action;
        assertThat(updateEnrichmentRecordAction.getRawRepo(), is(rawRepo));
        assertThat(updateEnrichmentRecordAction.getRecord(), equalTo(record));
        assertThat(updateEnrichmentRecordAction.state.getLibraryRecordsHandler(), is(recordsHandler));
        assertThat(updateEnrichmentRecordAction.state.getHoldingsItems(), is(holdingsItems));
        assertThat(updateEnrichmentRecordAction.settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID), is(providerId));
    }

    public static void assertUpdateClassificationsInEnrichmentRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord commonRecord, MarcRecord record) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(UpdateClassificationsInEnrichmentRecordAction.class.getName()));

        action.checkState();

        UpdateClassificationsInEnrichmentRecordAction updateClassificationsInEnrichmentRecordAction = (UpdateClassificationsInEnrichmentRecordAction) action;
        assertThat(updateClassificationsInEnrichmentRecordAction.state.getRawRepo(), is(rawRepo));
        assertThat(updateClassificationsInEnrichmentRecordAction.getUpdatingCommonRecord(), equalTo(commonRecord));
        assertThat(updateClassificationsInEnrichmentRecordAction.getEnrichmentRecord(), equalTo(record));
    }

    public static void assertDoubleRecordFrontendAction(ServiceAction action, MarcRecord record, Scripter scripter) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(DoubleRecordFrontendAction.class.getName()));

        action.checkState();

        DoubleRecordFrontendAction doubleRecordFrontendAction = (DoubleRecordFrontendAction) action;
        Assert.assertThat(doubleRecordFrontendAction.record, is(record));
        Assert.assertThat(doubleRecordFrontendAction.state.getScripter(), is(scripter));
    }

    public static void assertDoubleRecordCheckingAction(ServiceAction action, MarcRecord record, Scripter scripter) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(DoubleRecordCheckingAction.class.getName()));

        action.checkState();

        DoubleRecordCheckingAction doubleRecordCheckingAction = (DoubleRecordCheckingAction) action;
        Assert.assertThat(doubleRecordCheckingAction.record, is(record));
        Assert.assertThat(doubleRecordCheckingAction.state.getScripter(), is(scripter));
    }

    public static void assertEnqueueRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, String providerId, String mimetype) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(EnqueueRecordAction.class.getName()));

        action.checkState();

        EnqueueRecordAction enqueueRecordAction = (EnqueueRecordAction) action;
        assertThat(enqueueRecordAction.getRawRepo(), is(rawRepo));
        assertThat(enqueueRecordAction.getRecord(), is(record));
        assertThat(enqueueRecordAction.settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID), equalTo(providerId));
        assertThat(enqueueRecordAction.getMimetype(), equalTo(mimetype));
    }

    public static void assertMoveEnrichmentRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, MarcRecord commonRecord, Properties settings) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), equalTo(MoveEnrichmentRecordAction.class.getName()));

        action.checkState();

        MoveEnrichmentRecordAction moveEnrichmentRecordAction = (MoveEnrichmentRecordAction) action;
        assertThat(moveEnrichmentRecordAction.getRawRepo(), is(rawRepo));
        assertThat(moveEnrichmentRecordAction.getRecord(), is(record));
        assertThat(moveEnrichmentRecordAction.getCommonRecord(), is(commonRecord));
        assertThat(moveEnrichmentRecordAction.settings, equalTo(settings));
    }
}
