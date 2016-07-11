package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcConverter;
import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.updateservice.auth.Authenticator;
import dk.dbc.updateservice.client.BibliographicRecordExtraData;
import dk.dbc.updateservice.client.BibliographicRecordExtraDataDecoder;
import dk.dbc.updateservice.javascript.Scripter;
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
import dk.dbc.updateservice.ws.MDCUtil;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.w3c.dom.Node;

import javax.servlet.http.HttpServletRequest;
import javax.xml.transform.dom.DOMSource;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.MessageContext;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.ResourceBundle;

/**
 * Action to handle a complete Update request.
 * <p/>
 * This action verifies the request and and creates a new action:
 * <ol>
 * <li>ValidateOperationAction: To validate the record from the request.</li>
 * </ol>
 */
public class UpdateRequestAction extends AbstractAction {
    private static final XLogger logger = XLoggerFactory.getXLogger(UpdateRequestAction.class);
    private static final XLogger bizLogger = XLoggerFactory.getXLogger(BusinessLoggerFilter.LOGGER_NAME);

    // Defines SRU constant for the RecordSchema tag to accept marcxchange 1.1.
    private static final String RECORD_SCHEMA_MARCXCHANGE_1_1 = "info:lc/xmlns/marcxchange-v1";

    // Defines SRU constant for the RecordPacking tag to accept xml.
    private static final String RECORD_PACKING_XML = "xml";

    // RawRepo EJB to write records to the RawRepo.
    private RawRepo rawRepo;

    // Class to give access to the holdings database.
    private HoldingsItems holdingsItems;

    // Class to give access to the OpenAgency web service
    private OpenAgencyService openAgencyService;

    // Class to give access to lookups for the rawrepo in solr.
    private SolrService solrService;

    /**
     * Class to give access to the JavaScript engine to handle records.
     * <p>
     * The LibraryRecordsHandler is used to check records for changes in
     * classifications.
     * </p>
     */
    private LibraryRecordsHandler recordsHandler;

    private UpdateRecordRequest request;
    private WebServiceContext webServiceContext;

    private Authenticator authenticator;

    private Scripter scripter;
    private Properties settings;

    private ResourceBundle messages;

    public UpdateRequestAction(RawRepo rawRepo, UpdateRecordRequest request, WebServiceContext webServiceContext) {
        super("UpdateRequestAction");

        this.rawRepo = rawRepo;
        this.holdingsItems = null;
        this.openAgencyService = null;
        this.solrService = null;
        this.recordsHandler = null;

        this.request = request;
        this.webServiceContext = webServiceContext;

        this.authenticator = null;
        this.scripter = null;
        this.settings = null;

        this.messages = ResourceBundles.getBundle(this, "actions");
    }

    public RawRepo getRawRepo() {
        return rawRepo;
    }

    public void setRawRepo(RawRepo rawRepo) {
        this.rawRepo = rawRepo;
    }

    public HoldingsItems getHoldingsItems() {
        return holdingsItems;
    }

    public void setHoldingsItems(HoldingsItems holdingsItems) {
        this.holdingsItems = holdingsItems;
    }

    public OpenAgencyService getOpenAgencyService() {
        return openAgencyService;
    }

    public void setOpenAgencyService(OpenAgencyService openAgencyService) {
        this.openAgencyService = openAgencyService;
    }

    public SolrService getSolrService() {
        return solrService;
    }

    public void setSolrService(SolrService solrService) {
        this.solrService = solrService;
    }

    public LibraryRecordsHandler getRecordsHandler() {
        return recordsHandler;
    }

    public void setRecordsHandler(LibraryRecordsHandler recordsHandler) {
        this.recordsHandler = recordsHandler;
    }

    public UpdateRecordRequest getRequest() {
        return request;
    }

    public void setRequest(UpdateRecordRequest request) {
        this.request = request;
    }

    public WebServiceContext getWebServiceContext() {
        return webServiceContext;
    }

    public void setWebServiceContext(WebServiceContext webServiceContext) {
        this.webServiceContext = webServiceContext;
    }

    public Authenticator getAuthenticator() {
        return authenticator;
    }

    public void setAuthenticator(Authenticator authenticator) {
        this.authenticator = authenticator;
    }

    public Scripter getScripter() {
        return scripter;
    }

    public void setScripter(Scripter scripter) {
        this.scripter = scripter;
    }

    public Properties getSettings() {
        return settings;
    }

    public void setSettings(Properties settings) {
        this.settings = settings;
    }

    /**
     * Performs this actions and may create any child actions.
     *
     * @return A list of ValidationError to be reported in the web service response.
     * @throws UpdateException In case of an error.
     */
    @Override
    public ServiceResult performAction() throws UpdateException {
        logger.entry();

        try {
            logRequest();

            if (!isAgencyIdAllowedToUseUpdateOnThisInstance()) {
                String message = String.format(messages.getString("agency.is.not.allowed.for.this.instance"), request.getAuthentication().getGroupIdAut());
                return ServiceResult.newErrorResult(UpdateStatusEnum.FAILED_VALIDATION_INTERNAL_ERROR, message);
            }

            if (request.getBibliographicRecord() == null) {
                return ServiceResult.newErrorResult(UpdateStatusEnum.FAILED_UPDATE_INTERNAL_ERROR, messages.getString("request.record.is.missing"));
            }

            if (!isRecordSchemaValid()) {
                bizLogger.warn("Unknown record schema: {}", request.getBibliographicRecord().getRecordSchema());
                return ServiceResult.newStatusResult(UpdateStatusEnum.FAILED_INVALID_SCHEMA);
            }

            if (!isRecordPackingValid()) {
                bizLogger.warn("Unknown record packing: {}", request.getBibliographicRecord().getRecordPacking());
                return ServiceResult.newStatusResult(UpdateStatusEnum.FAILED_INVALID_SCHEMA);
            }

            MarcRecord record = readRecord();
            children.add(createValidateOperation(record));

            if (!hasValidateOnlyOption()) {
                children.add(createUpdateOperation(record));
            }

            return ServiceResult.newStatusResult(okStatusFromRequest());
        } finally {
            logger.exit();
        }
    }

    @Override
    public void setupMDCContext() {
        MDCUtil.setupContextForRecord(readRecord());
    }

    /**
     * Checks if the request is a validate only request.
     * <p>
     * It is declared public so {@link dk.dbc.updateservice.ws.UpdateService} can use it.
     * </p>
     *
     * @return Boolean value.
     */
    public boolean hasValidateOnlyOption() {
        logger.entry();

        try {
            Options options = request.getOptions();
            if (options != null && options.getOption() != null) {
                return options.getOption().contains(UpdateOptionEnum.VALIDATE_ONLY);
            }

            return false;
        } finally {
            logger.exit();
        }

    }

    /**
     * Constructs an action to validate the record from the request.
     */
    private ServiceAction createValidateOperation(MarcRecord record) {
        ValidateOperationAction validateOperationAction = new ValidateOperationAction();
        validateOperationAction.setAuthenticator(this.authenticator);
        validateOperationAction.setAuthentication(request.getAuthentication());
        validateOperationAction.setWebServiceContext(this.webServiceContext);
        validateOperationAction.setValidateSchema(readSchemaName());
        validateOperationAction.setOkStatus(okStatusFromRequest());
        validateOperationAction.setRecord(record);
        validateOperationAction.setScripter(this.scripter);
        validateOperationAction.setSettings(this.settings);

        return validateOperationAction;
    }

    /**
     * Constructs an action to update the record from the request.
     */
    private ServiceAction createUpdateOperation(MarcRecord record) throws UpdateException {
        UpdateOperationAction updateOperationAction = new UpdateOperationAction(rawRepo, record);
        updateOperationAction.setAuthenticator(this.authenticator);
        updateOperationAction.setAuthentication(request.getAuthentication());
        updateOperationAction.setHoldingsItems(this.holdingsItems);
        updateOperationAction.setOpenAgencyService(this.openAgencyService);
        updateOperationAction.setSolrService(this.solrService);
        updateOperationAction.setRecordsHandler(this.recordsHandler);
        updateOperationAction.setScripter(this.scripter);
        updateOperationAction.setSettings(settings);

        boolean allowExtraRecordData = false;
        if (settings.containsKey(JNDIResources.ALLOW_EXTRA_RECORD_DATA_KEY)) {
            allowExtraRecordData = Boolean.valueOf(settings.get(JNDIResources.ALLOW_EXTRA_RECORD_DATA_KEY).toString());
        }

        if (allowExtraRecordData) {
            // Overwrite "settings" with provider name from RecordExtraData

            BibliographicRecordExtraData bibliographicRecordExtraData = readRecordExtraData();
            if (bibliographicRecordExtraData != null) {
                if (bibliographicRecordExtraData.getProviderName() == null) {
                    throw new UpdateException(messages.getString("extra.record.data.provider.name.is.missing"));
                }

                String oldProviderName = settings.getProperty(JNDIResources.RAWREPO_PROVIDER_ID);
                logger.info("Overwrite provider id with new value from request. [{}] ==> [{}]", oldProviderName, bibliographicRecordExtraData.getProviderName());

                Properties newSettings = (Properties) settings.clone();
                newSettings.put(JNDIResources.RAWREPO_PROVIDER_ID, bibliographicRecordExtraData.getProviderName());

                updateOperationAction.setSettings(newSettings);
            }

        }

        return updateOperationAction;
    }

    /**
     * Return the OK status for this request.
     */
    private UpdateStatusEnum okStatusFromRequest() {
        logger.entry();

        try {
            if (hasValidateOnlyOption()) {
                return UpdateStatusEnum.VALIDATE_ONLY;
            } else {
                return UpdateStatusEnum.OK;
            }
        } finally {
            logger.exit();
        }
    }

    /**
     * Checks if the request contains a valid record scheme.
     * <p>
     * The valid record scheme is defined by the contant
     * {@link #RECORD_SCHEMA_MARCXCHANGE_1_1}
     *
     * @return Returns <code>true</code> if the record scheme is equal to
     * {@link #RECORD_SCHEMA_MARCXCHANGE_1_1}, <code>false</code> otherwise.
     */
    private boolean isRecordSchemaValid() {
        logger.entry();

        boolean result = false;
        try {
            if (request != null && request.getBibliographicRecord() != null && request.getBibliographicRecord().getRecordSchema() != null) {
                result = request.getBibliographicRecord().getRecordSchema().equals(RECORD_SCHEMA_MARCXCHANGE_1_1);
            } else {
                logger.warn("Unable to record schema from request");
            }

            return result;
        } finally {
            logger.exit(result);
        }
    }

    /**
     * Checks if the request contains a valid record packing.
     * <p>
     * The valid record packing is defined by the contant
     * {@link #RECORD_PACKING_XML}
     *
     * @return Returns <code>true</code> if the record packing is equal to
     * {@link #RECORD_PACKING_XML}, <code>false</code> otherwise.
     */
    public boolean isRecordPackingValid() {
        logger.entry();

        boolean result = false;
        try {
            if (request != null && request.getBibliographicRecord() != null && request.getBibliographicRecord().getRecordPacking() != null) {
                result = request.getBibliographicRecord().getRecordPacking().equals(RECORD_PACKING_XML);
            } else {
                logger.warn("Unable to record packing from request");
            }

            return result;
        } finally {
            logger.exit(result);
        }
    }

    /**
     * Reads the validation scheme, also known as the template name, of the
     * request.
     *
     * @return The validation scheme if it can be read from the request, the
     * empty string otherwise.
     */
    private String readSchemaName() {
        logger.entry();

        String result = "";
        try {
            if (request != null) {
                result = request.getSchemaName();
            } else {
                logger.warn("Unable to validate schema from request");
            }

            return result;
        } finally {
            logger.exit(result);
        }
    }

    /**
     * Reads the SRU record from the request and returns it.
     * <p>
     * If the request contains more than one record, then <code>null</code> is
     * returned.
     *
     * @return The found record as a {@link MarcRecord} or <code>null</code>
     * if the can not be converted or if no records exists.
     */
    public MarcRecord readRecord() {
        logger.entry();
        MarcRecord result = null;
        List<Object> list = null;

        try {
            if (request != null && request.getBibliographicRecord() != null && request.getBibliographicRecord().getRecordData() != null) {
                list = request.getBibliographicRecord().getRecordData().getContent();
            } else {
                logger.warn("Unable to read record from request");
            }

            if (list != null) {
                for (Object o : list) {
                    if (o instanceof Node) {
                        result = MarcConverter.createFromMarcXChange(new DOMSource((Node) o));
                        break;
                    }
                }
            }

            return result;
        } finally {
            logger.exit(result);
        }
    }

    /**
     * Reads any extra data associated with the SRU record.
     * <p>
     * If the request contains more than one record, then <code>null</code> is
     * returned.
     *
     * @return The found records extra data as a {@link BibliographicRecordExtraData} or <code>null</code>
     * if the can not be converted or if no records exists.
     */
    public BibliographicRecordExtraData readRecordExtraData() {
        logger.entry();
        BibliographicRecordExtraData result = null;
        List<Object> list = null;

        try {
            if (request != null && request.getBibliographicRecord() != null && request.getBibliographicRecord().getExtraRecordData() != null) {
                list = request.getBibliographicRecord().getExtraRecordData().getContent();
            } else {
                logger.warn("Unable to read record from request");
            }

            if (list != null) {
                for (Object o : list) {
                    if (o instanceof Node) {
                        result = BibliographicRecordExtraDataDecoder.fromXml(new DOMSource((Node) o));
                        break;
                    }
                }
            }

            return result;
        } finally {
            logger.exit(result);
        }
    }

    private void logRequest() {
        if (webServiceContext != null && webServiceContext.getMessageContext() != null) {
            MessageContext mc = webServiceContext.getMessageContext();
            HttpServletRequest req = (HttpServletRequest) mc.get(MessageContext.SERVLET_REQUEST);

            bizLogger.info("REQUEST:");
            bizLogger.info("======================================");
            bizLogger.info("Auth type: {}", req.getAuthType());
            bizLogger.info("Context path: {}", req.getContextPath());
            bizLogger.info("Content type: {}", req.getContentType());
            bizLogger.info("Content length: {}", req.getContentLengthLong());
            bizLogger.info("URI: {}", req.getRequestURI());
            bizLogger.info("Client address: {}", req.getRemoteAddr());
            // This takes 5 seconds ??
            bizLogger.info("Client host: {}", req.getRemoteHost());
            bizLogger.info("Client port: {}", req.getRemotePort());
            bizLogger.info("Headers");
            bizLogger.info("--------------------------------------");
            bizLogger.info("");
            Enumeration<String> headerNames = req.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                bizLogger.info("{}: {}", name, req.getHeader(name));
            }
            bizLogger.info("--------------------------------------");
        }

        bizLogger.info("");
        bizLogger.info("Template name: {}", readSchemaName());
        bizLogger.info("ValidationOnly option: {}", hasValidateOnlyOption() ? "True" : "False");
        bizLogger.info("Request record: \n{}", readRecord());
        bizLogger.info("======================================");
    }

    private boolean isAgencyIdAllowedToUseUpdateOnThisInstance() throws UpdateException {
        logger.entry();
        boolean res = true;
        try {
            if (!settings.containsKey(JNDIResources.UPDATE_PROD_STATE_KEY) || settings.getProperty(JNDIResources.UPDATE_PROD_STATE_KEY) == null) {
                throw new UpdateException("Required property '" + JNDIResources.UPDATE_PROD_STATE_KEY + "' not found");
            }
            boolean isProduction = Boolean.valueOf(settings.getProperty(JNDIResources.UPDATE_PROD_STATE_KEY));

            if (isProduction && request != null && request.getAuthentication() != null && request.getAuthentication().getGroupIdAut() != null && request.getAuthentication().getGroupIdAut().startsWith("13")) {
                res = false;
            }
            return res;
        } finally {
            logger.exit(res);
        }
    }
}
