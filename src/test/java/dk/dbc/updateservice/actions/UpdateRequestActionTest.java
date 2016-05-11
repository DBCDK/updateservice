package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.records.MarcRecordFactory;
import dk.dbc.iscrum.utils.IOUtils;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.updateservice.auth.Authenticator;
import dk.dbc.updateservice.client.BibliographicRecordExtraData;
import dk.dbc.updateservice.client.BibliographicRecordFactory;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.service.api.Authentication;
import dk.dbc.updateservice.service.api.BibliographicRecord;
import dk.dbc.updateservice.service.api.Options;
import dk.dbc.updateservice.service.api.UpdateOptionEnum;
import dk.dbc.updateservice.service.api.UpdateRecordRequest;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.HoldingsItems;
import dk.dbc.updateservice.update.LibraryRecordsHandler;
import dk.dbc.updateservice.update.OpenAgencyService;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.SolrService;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.ws.JNDIResources;
import org.junit.Test;

import javax.xml.ws.WebServiceContext;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.ResourceBundle;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class UpdateRequestActionTest {
    private static final String BOOK_RECORD_RESOURCE = "/dk/dbc/updateservice/actions/book.marc";

    private ResourceBundle messages;

    public UpdateRequestActionTest() {
        this.messages = ResourceBundles.getBundle(this, "actions");
    }

    /**
     * Test UpdateRequestAction.performAction() with an empty request and an empty
     * WebServiceContext.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * An empty request and WebServiceContext.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Perform the request.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return ServiceResult with an error.
     * </dd>
     * </dl>
     */
    @Test
    public void testEmptyRequest() throws Exception {
        UpdateRecordRequest request = new UpdateRecordRequest();
        WebServiceContext webServiceContext = mock(WebServiceContext.class);

        Authenticator authenticator = mock(Authenticator.class);
        Scripter scripter = mock(Scripter.class);

        RawRepo rawRepo = mock(RawRepo.class);
        HoldingsItems holdingsItems = mock(HoldingsItems.class);
        OpenAgencyService openAgencyService = mock(OpenAgencyService.class);
        SolrService solrService = mock(SolrService.class);
        LibraryRecordsHandler recordsHandler = mock(LibraryRecordsHandler.class);

        UpdateRequestAction instance = new UpdateRequestAction(rawRepo, request, webServiceContext);
        instance.setHoldingsItems(holdingsItems);
        instance.setOpenAgencyService(openAgencyService);
        instance.setSolrService(solrService);
        instance.setRecordsHandler(recordsHandler);
        instance.setAuthenticator(authenticator);
        instance.setScripter(scripter);
        instance.setSettings(getDefaultProperties());

        assertThat(instance.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, this.messages.getString("request.record.is.missing"))));
    }

    @Test
    public void test13LibraryInProduction() throws Exception {
        UpdateRecordRequest request = new UpdateRecordRequest();
        WebServiceContext webServiceContext = mock(WebServiceContext.class);

        BibliographicRecord bibRecord = BibliographicRecordFactory.loadMarcRecordInLineFormat(BOOK_RECORD_RESOURCE);
        request.setBibliographicRecord(bibRecord);
        Authentication authentication = new Authentication();
        authentication.setUserIdAut("user");
        authentication.setGroupIdAut("131010");
        authentication.setPasswordAut("password");
        request.setAuthentication(authentication);

        RawRepo rawRepo = mock(RawRepo.class);
        HoldingsItems holdingsItems = mock(HoldingsItems.class);
        OpenAgencyService openAgencyService = mock(OpenAgencyService.class);
        SolrService solrService = mock(SolrService.class);
        LibraryRecordsHandler recordsHandler = mock(LibraryRecordsHandler.class);
        Authenticator authenticator = mock(Authenticator.class);
        Scripter scripter = mock(Scripter.class);

        UpdateRequestAction instance = new UpdateRequestAction(rawRepo, request, webServiceContext);
        instance.setHoldingsItems(holdingsItems);
        instance.setOpenAgencyService(openAgencyService);
        instance.setSolrService(solrService);
        instance.setRecordsHandler(recordsHandler);
        instance.setAuthenticator(authenticator);
        instance.setScripter(scripter);
        instance.setSettings(getDefaultProperties());

        String message = String.format(messages.getString("agency.is.not.allowed.for.this.instance"), request.getAuthentication().getGroupIdAut());
        assertThat(instance.performAction(), equalTo(ServiceResult.newErrorResult(UpdateStatusEnum.FAILED_VALIDATION_INTERNAL_ERROR, message)));
    }

    /**
     * Test UpdateRequestAction.performAction() with a request containing a valid record
     * but with wrong record schema.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A request with wrong record schema.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Perform the request.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return ServiceResult with an error.
     * </dd>
     * </dl>
     */
    @Test
    public void testWrongRecordSchema() throws Exception {
        UpdateRecordRequest request = new UpdateRecordRequest();
        WebServiceContext webServiceContext = mock(WebServiceContext.class);

        BibliographicRecord bibRecord = BibliographicRecordFactory.loadMarcRecordInLineFormat(BOOK_RECORD_RESOURCE);
        request.setBibliographicRecord(bibRecord);

        RawRepo rawRepo = mock(RawRepo.class);
        HoldingsItems holdingsItems = mock(HoldingsItems.class);
        OpenAgencyService openAgencyService = mock(OpenAgencyService.class);
        SolrService solrService = mock(SolrService.class);
        LibraryRecordsHandler recordsHandler = mock(LibraryRecordsHandler.class);
        Authenticator authenticator = mock(Authenticator.class);
        Scripter scripter = mock(Scripter.class);

        UpdateRequestAction instance = new UpdateRequestAction(rawRepo, request, webServiceContext);
        instance.setHoldingsItems(holdingsItems);
        instance.setOpenAgencyService(openAgencyService);
        instance.setSolrService(solrService);
        instance.setRecordsHandler(recordsHandler);
        instance.setAuthenticator(authenticator);
        instance.setScripter(scripter);
        instance.setSettings(getDefaultProperties());

        bibRecord.setRecordSchema(null);
        assertThat(instance.performAction(), equalTo(ServiceResult.newStatusResult(UpdateStatusEnum.FAILED_INVALID_SCHEMA)));

        bibRecord.setRecordSchema("wrong");
        assertThat(instance.performAction(), equalTo(ServiceResult.newStatusResult(UpdateStatusEnum.FAILED_INVALID_SCHEMA)));
    }

    /**
     * Test UpdateRequestAction.performAction() with a request containing a valid record
     * but with wrong record schema.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A request with wrong record schema.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Perform the request.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return ServiceResult with an error.
     * </dd>
     * </dl>
     */
    @Test
    public void testWrongRecordPacking() throws Exception {
        UpdateRecordRequest request = new UpdateRecordRequest();
        WebServiceContext webServiceContext = mock(WebServiceContext.class);

        BibliographicRecord bibRecord = BibliographicRecordFactory.loadMarcRecordInLineFormat(BOOK_RECORD_RESOURCE);
        request.setBibliographicRecord(bibRecord);

        RawRepo rawRepo = mock(RawRepo.class);
        HoldingsItems holdingsItems = mock(HoldingsItems.class);
        OpenAgencyService openAgencyService = mock(OpenAgencyService.class);
        SolrService solrService = mock(SolrService.class);
        LibraryRecordsHandler recordsHandler = mock(LibraryRecordsHandler.class);
        Authenticator authenticator = mock(Authenticator.class);
        Scripter scripter = mock(Scripter.class);

        UpdateRequestAction instance = new UpdateRequestAction(rawRepo, request, webServiceContext);
        instance.setHoldingsItems(holdingsItems);
        instance.setOpenAgencyService(openAgencyService);
        instance.setSolrService(solrService);
        instance.setRecordsHandler(recordsHandler);
        instance.setAuthenticator(authenticator);
        instance.setScripter(scripter);
        instance.setSettings(getDefaultProperties());

        bibRecord.setRecordPacking(null);
        assertThat(instance.performAction(), equalTo(ServiceResult.newStatusResult(UpdateStatusEnum.FAILED_INVALID_SCHEMA)));

        bibRecord.setRecordPacking("wrong");
        assertThat(instance.performAction(), equalTo(ServiceResult.newStatusResult(UpdateStatusEnum.FAILED_INVALID_SCHEMA)));
    }

    /**
     * Test UpdateRequestAction.performAction() with a valid request for validating a record.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A complete valid request with for validating a record.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Perform the request.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return a ok service result.
     * </dd>
     * </dl>
     */
    @Test
    public void testValidRecordForValidate() throws Exception {
        UpdateRecordRequest request = new UpdateRecordRequest();
        WebServiceContext webServiceContext = mock(WebServiceContext.class);

        Authenticator authenticator = mock(Authenticator.class);
        Scripter scripter = mock(Scripter.class);

        InputStream is = getClass().getResourceAsStream(BOOK_RECORD_RESOURCE);
        MarcRecord record = MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));
        BibliographicRecord bibRecord = BibliographicRecordFactory.newMarcRecord(record);

        Authentication auth = new Authentication();
        auth.setGroupIdAut("group");
        auth.setUserIdAut("user");
        auth.setPasswordAut("passwd");
        request.setAuthentication(auth);

        request.setSchemaName("book");
        request.setOptions(new Options());
        request.getOptions().getOption().add(UpdateOptionEnum.VALIDATE_ONLY);
        request.setBibliographicRecord(bibRecord);

        RawRepo rawRepo = mock(RawRepo.class);
        HoldingsItems holdingsItems = mock(HoldingsItems.class);
        OpenAgencyService openAgencyService = mock(OpenAgencyService.class);
        SolrService solrService = mock(SolrService.class);
        LibraryRecordsHandler recordsHandler = mock(LibraryRecordsHandler.class);

        UpdateRequestAction instance = new UpdateRequestAction(rawRepo, request, webServiceContext);
        instance.setHoldingsItems(holdingsItems);
        instance.setOpenAgencyService(openAgencyService);
        instance.setSolrService(solrService);
        instance.setRecordsHandler(recordsHandler);
        instance.setAuthenticator(authenticator);
        instance.setScripter(scripter);
        instance.setSettings(getDefaultProperties());

        assertThat(instance.performAction(), equalTo(ServiceResult.newValidateOnlyResult()));

        List<ServiceAction> children = instance.children();
        assertThat(children.size(), is(1));

        ServiceAction child = children.get(0);
        assertTrue(child.getClass() == ValidateOperationAction.class);

        ValidateOperationAction validateOperationAction = (ValidateOperationAction) child;
        assertThat(validateOperationAction.getAuthenticator(), is(authenticator));
        assertThat(validateOperationAction.getAuthentication(), is(request.getAuthentication()));
        assertThat(validateOperationAction.getWebServiceContext(), is(webServiceContext));
        assertThat(validateOperationAction.getValidateSchema(), equalTo(request.getSchemaName()));
        assertThat(validateOperationAction.getOkStatus(), is(UpdateStatusEnum.VALIDATE_ONLY));
        assertThat(validateOperationAction.getRecord(), equalTo(record));
        assertThat(validateOperationAction.getScripter(), is(scripter));
        assertThat(validateOperationAction.getSettings(), equalTo(getDefaultProperties()));
    }

    /**
     * Test UpdateRequestAction.performAction() with a valid request for updating a record.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A complete valid request with for validating a record.
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Perform the request.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Return a ok service result.
     * </dd>
     * </dl>
     */
    @Test
    public void testValidRecordForUpdate() throws Exception {
        UpdateRecordRequest request = new UpdateRecordRequest();
        WebServiceContext webServiceContext = mock(WebServiceContext.class);

        Authenticator authenticator = mock(Authenticator.class);
        Scripter scripter = mock(Scripter.class);

        InputStream is = getClass().getResourceAsStream(BOOK_RECORD_RESOURCE);
        MarcRecord record = MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));
        BibliographicRecord bibRecord = BibliographicRecordFactory.newMarcRecord(record);

        Authentication auth = new Authentication();
        auth.setGroupIdAut("group");
        auth.setUserIdAut("user");
        auth.setPasswordAut("passwd");
        request.setAuthentication(auth);

        request.setSchemaName("book");
        request.setBibliographicRecord(bibRecord);

        RawRepo rawRepo = mock(RawRepo.class);
        HoldingsItems holdingsItems = mock(HoldingsItems.class);
        OpenAgencyService openAgencyService = mock(OpenAgencyService.class);
        SolrService solrService = mock(SolrService.class);
        LibraryRecordsHandler recordsHandler = mock(LibraryRecordsHandler.class);

        UpdateRequestAction instance = new UpdateRequestAction(rawRepo, request, webServiceContext);
        instance.setHoldingsItems(holdingsItems);
        instance.setOpenAgencyService(openAgencyService);
        instance.setSolrService(solrService);
        instance.setRecordsHandler(recordsHandler);
        instance.setAuthenticator(authenticator);
        instance.setScripter(scripter);
        instance.setSettings(getDefaultProperties());

        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = instance.children();
        assertThat(children.size(), is(2));

        ServiceAction child = children.get(0);
        assertTrue(child.getClass() == ValidateOperationAction.class);

        ValidateOperationAction validateOperationAction = (ValidateOperationAction) child;
        assertThat(validateOperationAction.getAuthenticator(), is(authenticator));
        assertThat(validateOperationAction.getAuthentication(), is(request.getAuthentication()));
        assertThat(validateOperationAction.getWebServiceContext(), is(webServiceContext));
        assertThat(validateOperationAction.getValidateSchema(), equalTo(request.getSchemaName()));
        assertThat(validateOperationAction.getOkStatus(), is(UpdateStatusEnum.OK));
        assertThat(validateOperationAction.getRecord(), equalTo(record));
        assertThat(validateOperationAction.getScripter(), is(scripter));
        assertThat(validateOperationAction.getSettings(), equalTo(getDefaultProperties()));

        child = children.get(1);
        assertTrue(child.getClass() == UpdateOperationAction.class);

        UpdateOperationAction updateOperationAction = (UpdateOperationAction) child;
        assertThat(updateOperationAction.getRawRepo(), is(rawRepo));
        assertThat(updateOperationAction.getHoldingsItems(), is(holdingsItems));
        assertThat(updateOperationAction.getOpenAgencyService(), is(openAgencyService));
        assertThat(updateOperationAction.getRecordsHandler(), is(recordsHandler));
        assertThat(updateOperationAction.getScripter(), is(scripter));
        assertThat(updateOperationAction.getRecord(), equalTo(record));
        assertThat(updateOperationAction.getAuthenticator(), is(authenticator));
        assertThat(updateOperationAction.getAuthentication(), is(request.getAuthentication()));
        assertThat(updateOperationAction.getSettings(), equalTo(getDefaultProperties()));
    }

    /**
     * Test UpdateRequestAction.performAction() with a valid request for updating a record, but with extra record data
     * with provider name.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A complete valid request with for validating a record.
     * <p>
     * No extra record settings in JNDI.
     * </p>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Perform the request.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Throw exception.
     * </dd>
     * </dl>
     */
    @Test
    public void testValidRecordForUpdate_NoJNDISettings_ExtraRecordData() throws Exception {
        UpdateRecordRequest request = new UpdateRecordRequest();
        WebServiceContext webServiceContext = mock(WebServiceContext.class);

        Authenticator authenticator = mock(Authenticator.class);
        Scripter scripter = mock(Scripter.class);

        Properties settings = getDefaultProperties();
        settings.setProperty(JNDIResources.RAWREPO_PROVIDER_ID, "opencataloging");

        InputStream is = getClass().getResourceAsStream(BOOK_RECORD_RESOURCE);
        MarcRecord record = MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));

        BibliographicRecordExtraData bibliographicRecordExtraData = new BibliographicRecordExtraData();
        bibliographicRecordExtraData.setProviderName("new_provider_name");

        BibliographicRecord bibRecord = BibliographicRecordFactory.newMarcRecord(record, bibliographicRecordExtraData);

        Authentication auth = new Authentication();
        auth.setGroupIdAut("group");
        auth.setUserIdAut("user");
        auth.setPasswordAut("passwd");
        request.setAuthentication(auth);

        request.setSchemaName("book");
        request.setBibliographicRecord(bibRecord);

        RawRepo rawRepo = mock(RawRepo.class);
        HoldingsItems holdingsItems = mock(HoldingsItems.class);
        OpenAgencyService openAgencyService = mock(OpenAgencyService.class);
        SolrService solrService = mock(SolrService.class);
        LibraryRecordsHandler recordsHandler = mock(LibraryRecordsHandler.class);

        UpdateRequestAction instance = new UpdateRequestAction(rawRepo, request, webServiceContext);
        instance.setHoldingsItems(holdingsItems);
        instance.setOpenAgencyService(openAgencyService);
        instance.setSolrService(solrService);
        instance.setRecordsHandler(recordsHandler);
        instance.setAuthenticator(authenticator);
        instance.setScripter(scripter);
        instance.setSettings(settings);

        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = instance.children();
        assertThat(children.size(), is(2));

        ServiceAction child = children.get(0);
        assertTrue(child.getClass() == ValidateOperationAction.class);

        ValidateOperationAction validateOperationAction = (ValidateOperationAction) child;
        assertThat(validateOperationAction.getAuthenticator(), is(authenticator));
        assertThat(validateOperationAction.getAuthentication(), is(request.getAuthentication()));
        assertThat(validateOperationAction.getWebServiceContext(), is(webServiceContext));
        assertThat(validateOperationAction.getValidateSchema(), equalTo(request.getSchemaName()));
        assertThat(validateOperationAction.getOkStatus(), is(UpdateStatusEnum.OK));
        assertThat(validateOperationAction.getRecord(), equalTo(record));
        assertThat(validateOperationAction.getScripter(), is(scripter));
        assertThat(validateOperationAction.getSettings(), equalTo(settings));

        child = children.get(1);
        assertTrue(child.getClass() == UpdateOperationAction.class);

        UpdateOperationAction updateOperationAction = (UpdateOperationAction) child;
        assertThat(updateOperationAction.getRawRepo(), is(rawRepo));
        assertThat(updateOperationAction.getHoldingsItems(), is(holdingsItems));
        assertThat(updateOperationAction.getOpenAgencyService(), is(openAgencyService));
        assertThat(updateOperationAction.getRecordsHandler(), is(recordsHandler));
        assertThat(updateOperationAction.getScripter(), is(scripter));
        assertThat(updateOperationAction.getRecord(), equalTo(record));
        assertThat(updateOperationAction.getAuthenticator(), is(authenticator));
        assertThat(updateOperationAction.getAuthentication(), is(request.getAuthentication()));
        assertThat(updateOperationAction.getSettings(), equalTo(settings));
    }

    /**
     * Test UpdateRequestAction.performAction() with a valid request for updating a record, but with extra record data
     * with provider name.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A complete valid request with for validating a record.
     * <p>
     * JNDI settings with "false" in allow extra record data.
     * </p>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Perform the request.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Throw exception.
     * </dd>
     * </dl>
     */
    @Test
    public void testValidRecordForUpdate_JNDISettingsIsFalse_ExtraRecordData() throws Exception {
        UpdateRecordRequest request = new UpdateRecordRequest();
        WebServiceContext webServiceContext = mock(WebServiceContext.class);

        Authenticator authenticator = mock(Authenticator.class);
        Scripter scripter = mock(Scripter.class);

        Properties settings = getDefaultProperties();
        settings.setProperty(JNDIResources.RAWREPO_PROVIDER_ID, "opencataloging");
        settings.setProperty(JNDIResources.ALLOW_EXTRA_RECORD_DATA_KEY, "False");

        InputStream is = getClass().getResourceAsStream(BOOK_RECORD_RESOURCE);
        MarcRecord record = MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));

        BibliographicRecordExtraData bibliographicRecordExtraData = new BibliographicRecordExtraData();
        bibliographicRecordExtraData.setProviderName("new_provider_name");

        BibliographicRecord bibRecord = BibliographicRecordFactory.newMarcRecord(record, bibliographicRecordExtraData);

        Authentication auth = new Authentication();
        auth.setGroupIdAut("group");
        auth.setUserIdAut("user");
        auth.setPasswordAut("passwd");
        request.setAuthentication(auth);

        request.setSchemaName("book");
        request.setBibliographicRecord(bibRecord);

        RawRepo rawRepo = mock(RawRepo.class);
        HoldingsItems holdingsItems = mock(HoldingsItems.class);
        OpenAgencyService openAgencyService = mock(OpenAgencyService.class);
        SolrService solrService = mock(SolrService.class);
        LibraryRecordsHandler recordsHandler = mock(LibraryRecordsHandler.class);

        UpdateRequestAction instance = new UpdateRequestAction(rawRepo, request, webServiceContext);
        instance.setHoldingsItems(holdingsItems);
        instance.setOpenAgencyService(openAgencyService);
        instance.setSolrService(solrService);
        instance.setRecordsHandler(recordsHandler);
        instance.setAuthenticator(authenticator);
        instance.setScripter(scripter);
        instance.setSettings(settings);

        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        List<ServiceAction> children = instance.children();
        assertThat(children.size(), is(2));

        ServiceAction child = children.get(0);
        assertTrue(child.getClass() == ValidateOperationAction.class);

        ValidateOperationAction validateOperationAction = (ValidateOperationAction) child;
        assertThat(validateOperationAction.getAuthenticator(), is(authenticator));
        assertThat(validateOperationAction.getAuthentication(), is(request.getAuthentication()));
        assertThat(validateOperationAction.getWebServiceContext(), is(webServiceContext));
        assertThat(validateOperationAction.getValidateSchema(), equalTo(request.getSchemaName()));
        assertThat(validateOperationAction.getOkStatus(), is(UpdateStatusEnum.OK));
        assertThat(validateOperationAction.getRecord(), equalTo(record));
        assertThat(validateOperationAction.getScripter(), is(scripter));
        assertThat(validateOperationAction.getSettings(), equalTo(settings));

        child = children.get(1);
        assertTrue(child.getClass() == UpdateOperationAction.class);

        UpdateOperationAction updateOperationAction = (UpdateOperationAction) child;
        assertThat(updateOperationAction.getRawRepo(), is(rawRepo));
        assertThat(updateOperationAction.getHoldingsItems(), is(holdingsItems));
        assertThat(updateOperationAction.getOpenAgencyService(), is(openAgencyService));
        assertThat(updateOperationAction.getRecordsHandler(), is(recordsHandler));
        assertThat(updateOperationAction.getScripter(), is(scripter));
        assertThat(updateOperationAction.getRecord(), equalTo(record));
        assertThat(updateOperationAction.getAuthenticator(), is(authenticator));
        assertThat(updateOperationAction.getAuthentication(), is(request.getAuthentication()));
        assertThat(updateOperationAction.getSettings(), equalTo(settings));
    }

    /**
     * Test UpdateRequestAction.performAction() with a valid request for updating a record, but with extra record data
     * with provider name.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A complete valid request with for validating a record.
     * <p>
     * JNDI settings with "true" in allow extra record data.
     * </p>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Perform the request.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Throw exception.
     * </dd>
     * </dl>
     */
    @Test
    public void testValidRecordForUpdate_JNDISettingsIsTrue_ExtraRecordData() throws Exception {
        UpdateRecordRequest request = new UpdateRecordRequest();
        WebServiceContext webServiceContext = mock(WebServiceContext.class);

        Authenticator authenticator = mock(Authenticator.class);
        Scripter scripter = mock(Scripter.class);

        Properties settings = getDefaultProperties();
        settings.setProperty(JNDIResources.RAWREPO_PROVIDER_ID, "opencataloging");
        settings.setProperty(JNDIResources.ALLOW_EXTRA_RECORD_DATA_KEY, "True");

        InputStream is = getClass().getResourceAsStream(BOOK_RECORD_RESOURCE);
        MarcRecord record = MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));

        BibliographicRecordExtraData bibliographicRecordExtraData = new BibliographicRecordExtraData();
        bibliographicRecordExtraData.setProviderName("new_provider_name");

        BibliographicRecord bibRecord = BibliographicRecordFactory.newMarcRecord(record, bibliographicRecordExtraData);

        Authentication auth = new Authentication();
        auth.setGroupIdAut("group");
        auth.setUserIdAut("user");
        auth.setPasswordAut("passwd");
        request.setAuthentication(auth);

        request.setSchemaName("book");
        request.setBibliographicRecord(bibRecord);

        RawRepo rawRepo = mock(RawRepo.class);
        HoldingsItems holdingsItems = mock(HoldingsItems.class);
        OpenAgencyService openAgencyService = mock(OpenAgencyService.class);
        SolrService solrService = mock(SolrService.class);
        LibraryRecordsHandler recordsHandler = mock(LibraryRecordsHandler.class);

        UpdateRequestAction instance = new UpdateRequestAction(rawRepo, request, webServiceContext);
        instance.setHoldingsItems(holdingsItems);
        instance.setOpenAgencyService(openAgencyService);
        instance.setSolrService(solrService);
        instance.setRecordsHandler(recordsHandler);
        instance.setAuthenticator(authenticator);
        instance.setScripter(scripter);
        instance.setSettings(settings);

        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));

        Properties expectedSettings = (Properties) settings.clone();
        expectedSettings.setProperty(JNDIResources.RAWREPO_PROVIDER_ID, bibliographicRecordExtraData.getProviderName());

        List<ServiceAction> children = instance.children();
        assertThat(children.size(), is(2));

        ServiceAction child = children.get(0);
        assertTrue(child.getClass() == ValidateOperationAction.class);

        ValidateOperationAction validateOperationAction = (ValidateOperationAction) child;
        assertThat(validateOperationAction.getAuthenticator(), is(authenticator));
        assertThat(validateOperationAction.getAuthentication(), is(request.getAuthentication()));
        assertThat(validateOperationAction.getWebServiceContext(), is(webServiceContext));
        assertThat(validateOperationAction.getValidateSchema(), equalTo(request.getSchemaName()));
        assertThat(validateOperationAction.getOkStatus(), is(UpdateStatusEnum.OK));
        assertThat(validateOperationAction.getRecord(), equalTo(record));
        assertThat(validateOperationAction.getScripter(), is(scripter));
        assertThat(validateOperationAction.getSettings(), equalTo(settings));

        child = children.get(1);
        assertTrue(child.getClass() == UpdateOperationAction.class);

        UpdateOperationAction updateOperationAction = (UpdateOperationAction) child;
        assertThat(updateOperationAction.getRawRepo(), is(rawRepo));
        assertThat(updateOperationAction.getHoldingsItems(), is(holdingsItems));
        assertThat(updateOperationAction.getOpenAgencyService(), is(openAgencyService));
        assertThat(updateOperationAction.getRecordsHandler(), is(recordsHandler));
        assertThat(updateOperationAction.getScripter(), is(scripter));
        assertThat(updateOperationAction.getRecord(), equalTo(record));
        assertThat(updateOperationAction.getAuthenticator(), is(authenticator));
        assertThat(updateOperationAction.getAuthentication(), is(request.getAuthentication()));
        assertThat(updateOperationAction.getSettings(), equalTo(expectedSettings));
    }

    /**
     * Test UpdateRequestAction.performAction() with a valid request for updating a record, but with extra record data
     * with provider name with null value.
     * <p>
     * <dl>
     * <dt>Given</dt>
     * <dd>
     * A complete valid request with for validating a record.
     * <p>
     * JNDI settings with "true" in allow extra record data.
     * </p>
     * </dd>
     * <dt>When</dt>
     * <dd>
     * Perform the request.
     * </dd>
     * <dt>Then</dt>
     * <dd>
     * Throw exception.
     * </dd>
     * </dl>
     */
    @Test(expected = UpdateException.class)
    public void testValidRecordForUpdate_JNDISettingsIsTrue_ExtraRecordData_ProviderNameIsNull() throws Exception {
        UpdateRecordRequest request = new UpdateRecordRequest();
        WebServiceContext webServiceContext = mock(WebServiceContext.class);

        Authenticator authenticator = mock(Authenticator.class);
        Scripter scripter = mock(Scripter.class);

        Properties settings = getDefaultProperties();
        settings.setProperty(JNDIResources.RAWREPO_PROVIDER_ID, "opencataloging");
        settings.setProperty(JNDIResources.ALLOW_EXTRA_RECORD_DATA_KEY, "True");

        InputStream is = getClass().getResourceAsStream(BOOK_RECORD_RESOURCE);
        MarcRecord record = MarcRecordFactory.readRecord(IOUtils.readAll(is, "UTF-8"));

        BibliographicRecordExtraData bibliographicRecordExtraData = new BibliographicRecordExtraData();
        bibliographicRecordExtraData.setProviderName(null);

        BibliographicRecord bibRecord = BibliographicRecordFactory.newMarcRecord(record, bibliographicRecordExtraData);

        Authentication auth = new Authentication();
        auth.setGroupIdAut("group");
        auth.setUserIdAut("user");
        auth.setPasswordAut("passwd");
        request.setAuthentication(auth);

        request.setSchemaName("book");
        request.setBibliographicRecord(bibRecord);

        RawRepo rawRepo = mock(RawRepo.class);
        HoldingsItems holdingsItems = mock(HoldingsItems.class);
        OpenAgencyService openAgencyService = mock(OpenAgencyService.class);
        SolrService solrService = mock(SolrService.class);
        LibraryRecordsHandler recordsHandler = mock(LibraryRecordsHandler.class);

        UpdateRequestAction instance = new UpdateRequestAction(rawRepo, request, webServiceContext);
        instance.setHoldingsItems(holdingsItems);
        instance.setOpenAgencyService(openAgencyService);
        instance.setSolrService(solrService);
        instance.setRecordsHandler(recordsHandler);
        instance.setAuthenticator(authenticator);
        instance.setScripter(scripter);
        instance.setSettings(settings);

        assertThat(instance.performAction(), equalTo(ServiceResult.newOkResult()));
    }

    private Properties getDefaultProperties() {
        Properties properties = new Properties();
        properties.put(JNDIResources.UPDATE_PROD_STATE_KEY, "true");
        return properties;
    }
}
