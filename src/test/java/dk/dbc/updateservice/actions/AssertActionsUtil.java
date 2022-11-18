/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcConverter;
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
import dk.dbc.updateservice.client.BibliographicRecordExtraData;
import dk.dbc.updateservice.client.BibliographicRecordExtraDataEncoder;
import dk.dbc.updateservice.dto.AuthenticationDTO;
import dk.dbc.updateservice.dto.BibliographicRecordDTO;
import dk.dbc.updateservice.dto.ExtraRecordDataDTO;
import dk.dbc.updateservice.dto.RecordDataDTO;
import dk.dbc.updateservice.solr.SolrFBS;
import dk.dbc.updateservice.update.HoldingsItemsConnector;
import dk.dbc.updateservice.update.LibraryRecordsHandler;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.RawRepoRecordMock;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.update.VipCoreService;
import dk.dbc.updateservice.utils.IOUtils;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.xml.bind.JAXBException;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Utility functions to reuse code in different test classes.
 */
public class AssertActionsUtil {
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(AssertActionsUtil.class);
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
    public static final String OVE_RECORD = "actions/ove-record.marc";
    public static final String LITTOLK_COMMON = "actions/littolk-common.marc";
    public static final String LITTOLK_ENRICHMENT = "actions/littolk-enrichment.marc";
    public static final String MATVURD_1 = "actions/matvurd1.marc";
    public static final String MATVURD_2 = "actions/matvurd2.marc";
    public static final String MATVURD_3 = "actions/matvurd3.marc";
    public static final String MATVURD_4 = "actions/matvurd4.marc";
    public static final String MATVURD_5 = "actions/matvurd5.marc";
    public static final String MATVURD_6 = "actions/matvurd6.marc";
    public static final String GLOBAL_ACTION_STATE = "actions/globalactionstate.marc";
    public static final String EXPANDED_VOLUME = "actions/expanded-volume.marc";

    public static MarcRecord loadRecord(String filename) throws IOException {
        final InputStream is = AssertActionsUtil.class.getResourceAsStream("/dk/dbc/updateservice/" + filename);
        return MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));
    }

    public static MarcRecord loadRecord(String filename, String newRecordId) throws IOException {
        final MarcRecord record = loadRecord(filename);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "a", newRecordId);

        return record;
    }

    public static MarcRecord loadRecordAndMarkForDeletion(String filename) throws IOException {
        final MarcRecord record = loadRecord(filename);
        new MarcRecordWriter(record).markForDeletion();

        return record;
    }

    public static MarcRecord loadRecordAndMarkForDeletion(String filename, String newRecordId) throws IOException {
        final MarcRecord record = loadRecordAndMarkForDeletion(filename);
        new MarcRecordWriter(record).addOrReplaceSubfield("001", "a", newRecordId);

        return record;
    }

    public static BibliographicRecordDTO constructBibliographicRecordDTO(MarcRecord record, BibliographicRecordExtraData data) throws JAXBException, ParserConfigurationException {
        final BibliographicRecordDTO bibliographicRecordDTO = new BibliographicRecordDTO();
        bibliographicRecordDTO.setRecordSchema("info:lc/xmlns/marcxchange-v1");
        bibliographicRecordDTO.setRecordPacking("xml");

        final ExtraRecordDataDTO extraRecordDataDTO = new ExtraRecordDataDTO();
        if (data != null) {
            extraRecordDataDTO.setContent(Arrays.asList("\n", BibliographicRecordExtraDataEncoder.toXmlDocument(data), "\n"));
        } else {
            extraRecordDataDTO.setContent(Collections.singletonList("\n"));
        }
        bibliographicRecordDTO.setExtraRecordDataDTO(extraRecordDataDTO);

        final RecordDataDTO recordDataDTO = new RecordDataDTO();
        recordDataDTO.setContent(Arrays.asList("\n", MarcConverter.convertToMarcXChangeAsDocument(record).getDocumentElement(), "\n"));
        bibliographicRecordDTO.setRecordDataDTO(recordDataDTO);

        return bibliographicRecordDTO;
    }

    public static Set<RecordId> createRecordSet(MarcRecord... records) {
        final Set<RecordId> result = new HashSet<>();

        for (MarcRecord record : records) {
            result.add(new RecordId(getBibliographicRecordId(record), getAgencyIdAsInt(record)));
        }

        return result;
    }

    public static Set<Integer> createAgenciesSet(Integer... agencies) {
        return new HashSet<>(Arrays.asList(agencies));
    }

    public static Record createRawRepoRecord(MarcRecord record, String mimetype) throws JAXBException, UnsupportedEncodingException {
        final RawRepoRecordMock result = new RawRepoRecordMock(getBibliographicRecordId(record), getAgencyIdAsInt(record));
        result.setMimeType(mimetype);
        result.setDeleted(false);
        result.setContent(RecordContentTransformer.encodeRecord(record));

        return result;
    }

    public static String getBibliographicRecordId(MarcRecord record) {
        return new MarcRecordReader(record).getRecordId();
    }

    public static String getAgencyId(MarcRecord record) {
        return new MarcRecordReader(record).getAgencyId();
    }

    public static int getAgencyIdAsInt(MarcRecord record) {
        return new MarcRecordReader(record).getAgencyIdAsInt();
    }

    public static RecordId getRecordId(MarcRecord record) {
        final String bibliographicRecordId = new MarcRecordReader(record).getRecordId();
        final int agencyId = new MarcRecordReader(record).getAgencyIdAsInt();

        return new RecordId(bibliographicRecordId, agencyId);
    }

    public static void assertAuthenticateRecordAction(ServiceAction action, MarcRecord record, Authenticator authenticator, AuthenticationDTO AuthenticationDTO) {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), is(AuthenticateRecordAction.class.getName()));

        final AuthenticateRecordAction authenticateRecordAction = (AuthenticateRecordAction) action;
        assertThat(authenticateRecordAction.state.readRecord(), is(record));
        assertThat(authenticateRecordAction.state.getAuthenticator(), is(authenticator));
        assertThat(authenticateRecordAction.state.getUpdateServiceRequestDTO().getAuthenticationDTO(), is(AuthenticationDTO));
    }

    public static void assertUpdateCommonRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, String groupId, LibraryRecordsHandler recordsHandler, HoldingsItemsConnector holdingsItems, VipCoreService vipCoreService) {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), is(UpdateCommonRecordAction.class.getName()));

        final UpdateCommonRecordAction updateCommonRecordAction = (UpdateCommonRecordAction) action;
        assertThat(updateCommonRecordAction.getRawRepo(), is(rawRepo));
        assertThat(updateCommonRecordAction.getRecord(), is(record));
        assertThat(updateCommonRecordAction.state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), is(groupId));
        assertThat(updateCommonRecordAction.state.getLibraryRecordsHandler(), is(recordsHandler));
        assertThat(updateCommonRecordAction.state.getHoldingsItems(), is(holdingsItems));
        assertThat(updateCommonRecordAction.state.getVipCoreService(), is(vipCoreService));
    }

    public static void assertCreateSingleRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, SolrFBS solrService, String providerId) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), is(CreateSingleRecordAction.class.getName()));

        final CreateSingleRecordAction createSingleRecordAction = (CreateSingleRecordAction) action;
        assertThat(createSingleRecordAction.getRawRepo(), is(rawRepo));
        assertThat(createSingleRecordAction.getRecord(), is(record));
        assertThat(createSingleRecordAction.state.getSolrFBS(), is(solrService));
        assertThat(createSingleRecordAction.settings.getProperty(createSingleRecordAction.state.getRawRepoProviderId()), is(providerId));
    }

    public static void assertCreateVolumeRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, HoldingsItemsConnector holdingsItems, SolrFBS solrService, String providerId) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), is(CreateVolumeRecordAction.class.getName()));

        final CreateVolumeRecordAction createVolumeRecordAction = (CreateVolumeRecordAction) action;
        assertThat(createVolumeRecordAction.getRawRepo(), is(rawRepo));
        assertThat(createVolumeRecordAction.getRecord(), is(record));
        assertThat(createVolumeRecordAction.state.getHoldingsItems(), is(holdingsItems));
        assertThat(createVolumeRecordAction.state.getSolrFBS(), is(solrService));
        assertThat(createVolumeRecordAction.settings.getProperty(createVolumeRecordAction.state.getRawRepoProviderId()), is(providerId));
    }

    public static void assertOverwriteVolumeRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, String groupId, LibraryRecordsHandler recordsHandler, HoldingsItemsConnector holdingsItems) {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), is(OverwriteVolumeRecordAction.class.getName()));

        final OverwriteVolumeRecordAction overwriteVolumeRecordAction = (OverwriteVolumeRecordAction) action;
        assertThat(overwriteVolumeRecordAction.getRawRepo(), is(rawRepo));
        assertThat(overwriteVolumeRecordAction.getRecord(), is(record));
        assertThat(overwriteVolumeRecordAction.state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), is(groupId));
        assertThat(overwriteVolumeRecordAction.state.getHoldingsItems(), is(holdingsItems));
        assertThat(overwriteVolumeRecordAction.state.getLibraryRecordsHandler(), is(recordsHandler));
    }

    public static void assertUpdateLocalRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, HoldingsItemsConnector holdingsItems) {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), is(UpdateLocalRecordAction.class.getName()));

        final UpdateLocalRecordAction updateLocalRecordAction = (UpdateLocalRecordAction) action;
        assertThat(updateLocalRecordAction.getRawRepo(), is(rawRepo));
        assertThat(updateLocalRecordAction.getRecord(), is(record));
        assertThat(updateLocalRecordAction.state.getHoldingsItems(), is(holdingsItems));
    }

    public static void assertUpdateEnrichmentRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, LibraryRecordsHandler recordsHandler, HoldingsItemsConnector holdingsItems) {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), is(UpdateEnrichmentRecordAction.class.getName()));

        final UpdateEnrichmentRecordAction updateEnrichmentRecordAction = (UpdateEnrichmentRecordAction) action;
        assertThat(updateEnrichmentRecordAction.getRawRepo(), is(rawRepo));
        assertThat(updateEnrichmentRecordAction.getRecord(), is(record));
        assertThat(updateEnrichmentRecordAction.state.getLibraryRecordsHandler(), is(recordsHandler));
        assertThat(updateEnrichmentRecordAction.state.getHoldingsItems(), is(holdingsItems));
    }

    public static void assertSchoolCommonRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, LibraryRecordsHandler recordsHandler, HoldingsItemsConnector holdingsItems, String providerId) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), is(UpdateSchoolCommonRecord.class.getName()));

        final UpdateSchoolCommonRecord updateSchoolCommonRecord = (UpdateSchoolCommonRecord) action;
        assertThat(updateSchoolCommonRecord.getRawRepo(), is(rawRepo));
        assertThat(updateSchoolCommonRecord.getRecord(), is(record));
        assertThat(updateSchoolCommonRecord.state.getLibraryRecordsHandler(), is(recordsHandler));
        assertThat(updateSchoolCommonRecord.state.getHoldingsItems(), is(holdingsItems));
        assertThat(updateSchoolCommonRecord.settings.getProperty(updateSchoolCommonRecord.state.getRawRepoProviderId()), is(providerId));
    }

    public static void assertSchoolEnrichmentRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, LibraryRecordsHandler recordsHandler, HoldingsItemsConnector holdingsItems, String providerId) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), is(UpdateSchoolEnrichmentRecordAction.class.getName()));

        final UpdateSchoolEnrichmentRecordAction updateSchoolEnrichmentRecordAction = (UpdateSchoolEnrichmentRecordAction) action;
        assertThat(updateSchoolEnrichmentRecordAction.getRawRepo(), is(rawRepo));
        assertThat(updateSchoolEnrichmentRecordAction.getRecord(), is(record));
        assertThat(updateSchoolEnrichmentRecordAction.state.getLibraryRecordsHandler(), is(recordsHandler));
        assertThat(updateSchoolEnrichmentRecordAction.state.getHoldingsItems(), is(holdingsItems));
        assertThat(updateSchoolEnrichmentRecordAction.settings.getProperty(updateSchoolEnrichmentRecordAction.state.getRawRepoProviderId()), is(providerId));
    }

    public static void assertStoreRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record) {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), is(StoreRecordAction.class.getName()));

        final StoreRecordAction storeRecordAction = (StoreRecordAction) action;
        assertThat(storeRecordAction.getRawRepo(), is(rawRepo));
        assertThat(storeRecordAction.getRecord(), is(record));
        assertThat(storeRecordAction.getMimetype(), is(MarcXChangeMimeType.MARCXCHANGE));
    }

    public static void assertStoreRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, String mimetype) {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), is(StoreRecordAction.class.getName()));

        final StoreRecordAction storeRecordAction = (StoreRecordAction) action;
        assertThat(storeRecordAction.getRawRepo(), is(rawRepo));
        assertThat(storeRecordAction.getRecord(), is(record));
        assertThat(storeRecordAction.getMimetype(), is(mimetype));
    }

    public static void assertDeleteRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, String mimetype) {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), is(DeleteRecordAction.class.getName()));

        final DeleteRecordAction deleteRecordAction = (DeleteRecordAction) action;
        assertThat(deleteRecordAction.getRawRepo(), is(rawRepo));
        assertThat(deleteRecordAction.getRecord(), is(record));
        assertThat(deleteRecordAction.getMimetype(), is(mimetype));
    }

    public static void assertDeleteCommonRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, LibraryRecordsHandler recordsHandler, HoldingsItemsConnector holdingsItems, String providerId) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), is(DeleteCommonRecordAction.class.getName()));

        final DeleteCommonRecordAction deleteCommonRecordAction = (DeleteCommonRecordAction) action;
        assertThat(deleteCommonRecordAction.getRawRepo(), is(rawRepo));
        assertThat(deleteCommonRecordAction.getRecord(), is(record));
        assertThat(deleteCommonRecordAction.state.getLibraryRecordsHandler(), is(recordsHandler));
        assertThat(deleteCommonRecordAction.state.getHoldingsItems(), is(holdingsItems));
        assertThat(deleteCommonRecordAction.settings.getProperty(deleteCommonRecordAction.state.getRawRepoProviderId()), is(providerId));
    }

    public static void assertLinkRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, MarcRecord target) {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), is(LinkRecordAction.class.getName()));

        final LinkRecordAction linkRecordAction = (LinkRecordAction) action;
        assertThat(linkRecordAction.getRawRepo(), is(rawRepo));
        assertThat(linkRecordAction.getRecord(), is(record));

        String recordId = getBibliographicRecordId(target);
        int agencyId = getAgencyIdAsInt(target);
        assertThat(linkRecordAction.getLinkToRecordId(), is(new RecordId(recordId, agencyId)));
    }

    public static void assertLinkAuthorityRecordsAction(ServiceAction action, RawRepo rawRepo, MarcRecord record) {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), is(LinkAuthorityRecordsAction.class.getName()));

        final LinkAuthorityRecordsAction linkAuthorityRecordsAction = (LinkAuthorityRecordsAction) action;
        assertThat(linkAuthorityRecordsAction.getRawRepo(), is(rawRepo));
        assertThat(linkAuthorityRecordsAction.getRecord(), is(record));
    }

    public static void assertLinkMatVurdRecordsAction(ServiceAction action, RawRepo rawRepo, MarcRecord record) {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), is(LinkMatVurdRecordsAction.class.getName()));

        final LinkMatVurdRecordsAction linkMatVurdRecordsAction = (LinkMatVurdRecordsAction) action;
        assertThat(linkMatVurdRecordsAction.getRawRepo(), is(rawRepo));
        assertThat(linkMatVurdRecordsAction.getRecord(), is(record));
    }

    public static void assertRemoveLinksAction(ServiceAction action, RawRepo rawRepo, MarcRecord record) {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), is(RemoveLinksAction.class.getName()));

        final RemoveLinksAction removeLinksAction = (RemoveLinksAction) action;
        assertThat(removeLinksAction.getRawRepo(), is(rawRepo));
        assertThat(removeLinksAction.getRecord(), is(record));
    }

    public static void assertCreateEnrichmentAction(ServiceAction action, RawRepo rawRepo, MarcRecord commonRecord, String agencyId, String commonRecordId) {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), is(CreateEnrichmentRecordWithClassificationsAction.class.getName()));

        final CreateEnrichmentRecordWithClassificationsAction createEnrichmentRecordWithClassificationsAction = (CreateEnrichmentRecordWithClassificationsAction) action;
        assertThat(createEnrichmentRecordWithClassificationsAction.state.getRawRepo(), is(rawRepo));
        assertThat(createEnrichmentRecordWithClassificationsAction.getUpdatingCommonRecord(), is(commonRecord));
        assertThat(createEnrichmentRecordWithClassificationsAction.agencyId, is(agencyId));
        assertThat(createEnrichmentRecordWithClassificationsAction.getTargetRecordId(), is(commonRecordId));
    }

    public static void assertUpdateEnrichmentRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, LibraryRecordsHandler recordsHandler, HoldingsItemsConnector holdingsItems, String providerId) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), is(UpdateEnrichmentRecordAction.class.getName()));

        final UpdateEnrichmentRecordAction updateEnrichmentRecordAction = (UpdateEnrichmentRecordAction) action;
        assertThat(updateEnrichmentRecordAction.getRawRepo(), is(rawRepo));
        assertThat(updateEnrichmentRecordAction.getRecord(), is(record));
        assertThat(updateEnrichmentRecordAction.state.getLibraryRecordsHandler(), is(recordsHandler));
        assertThat(updateEnrichmentRecordAction.state.getHoldingsItems(), is(holdingsItems));
        assertThat(updateEnrichmentRecordAction.settings.getProperty(updateEnrichmentRecordAction.state.getRawRepoProviderId()), is(providerId));
    }

    public static void assertUpdateClassificationsInEnrichmentRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord commonRecord, MarcRecord record) {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), is(UpdateClassificationsInEnrichmentRecordAction.class.getName()));

        final UpdateClassificationsInEnrichmentRecordAction updateClassificationsInEnrichmentRecordAction = (UpdateClassificationsInEnrichmentRecordAction) action;
        assertThat(updateClassificationsInEnrichmentRecordAction.state.getRawRepo(), is(rawRepo));
        assertThat(updateClassificationsInEnrichmentRecordAction.getUpdatingCommonRecord(), is(commonRecord));
        assertThat(updateClassificationsInEnrichmentRecordAction.getEnrichmentRecord(), is(record));
    }

    public static void assertDoubleRecordFrontendAction(ServiceAction action, MarcRecord record) {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), is(DoubleRecordFrontendAction.class.getName()));

        final DoubleRecordFrontendAction doubleRecordFrontendAction = (DoubleRecordFrontendAction) action;
        assertThat(doubleRecordFrontendAction.state.getMarcRecord(), is(record));
    }

    public static void assertDoubleRecordCheckingAction(ServiceAction action, MarcRecord record) {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), is(DoubleRecordCheckingAction.class.getName()));

        final DoubleRecordCheckingAction doubleRecordCheckingAction = (DoubleRecordCheckingAction) action;
        assertThat(doubleRecordCheckingAction.record, is(record));
    }

    public static void assertEnqueueRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, String providerId, String mimetype) throws UpdateException {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), is(EnqueueRecordAction.class.getName()));

        final EnqueueRecordAction enqueueRecordAction = (EnqueueRecordAction) action;
        assertThat(enqueueRecordAction.getRawRepo(), is(rawRepo));
        assertThat(enqueueRecordAction.getRecord(), is(record));
        assertThat(enqueueRecordAction.settings.getProperty(enqueueRecordAction.state.getRawRepoProviderId()), is(providerId));
    }

    public static void assertOverwriteSingleRecordAction(ServiceAction action, RawRepo rawRepo, MarcRecord record, LibraryRecordsHandler recordsHandler, HoldingsItemsConnector holdingsItems, VipCoreService vipCoreService, String groupId) {
        assertThat(action, notNullValue());
        assertThat(action.getClass().getName(), is(OverwriteSingleRecordAction.class.getName()));

        final OverwriteSingleRecordAction overwriteSingleRecordAction = (OverwriteSingleRecordAction) action;
        assertThat(overwriteSingleRecordAction.getRawRepo(), is(rawRepo));
        assertThat(overwriteSingleRecordAction.marcRecord, is(record));
        assertThat(overwriteSingleRecordAction.state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId(), is(groupId));
        assertThat(overwriteSingleRecordAction.state.getHoldingsItems(), is(holdingsItems));
        assertThat(overwriteSingleRecordAction.state.getVipCoreService(), is(vipCoreService));
        assertThat(overwriteSingleRecordAction.state.getLibraryRecordsHandler(), is(recordsHandler));
    }

    /**
     * Helper function for comparing two full records where 001 *c and *d are excluded from the comparison
     *
     * @param actual   The actual record
     * @param expected The expected record
     */
    public static void assertRecord(MarcRecord actual, MarcRecord expected) {
        assertThat(actual, notNullValue());
        assertThat(expected, notNullValue());
        assertThat(actual.getFields().size(), is(expected.getFields().size()));
        assertThat(actual.getFields().get(1), is(expected.getFields().get(1)));

        for (int f = 0; f < actual.getFields().size(); f++) {
            final MarcField actualField = actual.getFields().get(f);
            final MarcField expectedField = expected.getFields().get(f);
            if ("001".equals(actualField.getName())) {
                final List<String> ignoredSubfields = Arrays.asList("c", "d");
                assertThat(actualField.getSubfields().size(), is(expectedField.getSubfields().size()));

                for (int s = 0; s < actualField.getSubfields().size(); s++) {
                    final MarcSubField actualSubfield = actualField.getSubfields().get(s);
                    final MarcSubField expectedSubfield = expectedField.getSubfields().get(s);
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
