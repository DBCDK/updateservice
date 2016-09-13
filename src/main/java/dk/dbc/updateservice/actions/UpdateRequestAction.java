package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.records.MarcRecord;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.updateservice.client.BibliographicRecordExtraData;
import dk.dbc.updateservice.service.api.Options;
import dk.dbc.updateservice.service.api.UpdateOptionEnum;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.ws.JNDIResources;
import dk.dbc.updateservice.ws.MDCUtil;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.xml.ws.handler.MessageContext;
import java.util.Enumeration;
import java.util.Properties;

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

    private Properties settings;
    private MarcRecord record;

    public UpdateRequestAction(GlobalActionState globalActionState, Properties properties, MarcRecord marcRecord) {
        super(UpdateRequestAction.class.getSimpleName(), globalActionState);
        settings = properties;
        record = marcRecord;
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
            ServiceResult message = verifyData();
            if (message != null) {
                return message;
            }
            children.add(createValidateOperation());
            if (!hasValidateOnlyOption()) {
                children.add(createUpdateOperation());
            }
            return ServiceResult.newOkResult();
        } finally {
            logger.exit();
        }
    }

    private ServiceResult verifyData() throws UpdateException {
        if (!isAgencyIdAllowedToUseUpdateOnThisInstance()) {
            String message = String.format(state.getMessages().getString("agency.is.not.allowed.for.this.instance"), state.getUpdateRecordRequest().getAuthentication().getGroupIdAut());
            return ServiceResult.newErrorResult(UpdateStatusEnum.FAILED, message, state);
        }
        if (state.getUpdateRecordRequest().getBibliographicRecord() == null) {
            return ServiceResult.newErrorResult(UpdateStatusEnum.FAILED, state.getMessages().getString("request.record.is.missing"), state);
        }
        if (!state.isRecordSchemaValid()) {
            bizLogger.warn("Unknown record schema: {}", state.getUpdateRecordRequest().getBibliographicRecord().getRecordSchema());
            return ServiceResult.newStatusResult(UpdateStatusEnum.FAILED);
        }
        if (!state.isRecordPackingValid()) {
            bizLogger.warn("Unknown record packing: {}", state.getUpdateRecordRequest().getBibliographicRecord().getRecordPacking());
            return ServiceResult.newStatusResult(UpdateStatusEnum.FAILED);
        }
        return null;
    }

    @Override
    public void setupMDCContext() {
        MDCUtil.setupContextForRecord(state.readRecord());
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
            Options options = state.getUpdateRecordRequest().getOptions();
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
    private ServiceAction createValidateOperation() {
        ValidateOperationAction validateOperationAction = new ValidateOperationAction(state, settings);
        validateOperationAction.setOkStatus(UpdateStatusEnum.OK);
        return validateOperationAction;
    }

    /**
     * Constructs an action to update the record from the request.
     */
    private ServiceAction createUpdateOperation() throws UpdateException {
        UpdateOperationAction updateOperationAction = new UpdateOperationAction(state, settings, record);
        boolean allowExtraRecordData = false;
        if (settings.containsKey(JNDIResources.ALLOW_EXTRA_RECORD_DATA_KEY)) {
            allowExtraRecordData = Boolean.valueOf(settings.get(JNDIResources.ALLOW_EXTRA_RECORD_DATA_KEY).toString());
        }
        if (allowExtraRecordData) {
            // Overwrite "settings" with provider name from RecordExtraData
            BibliographicRecordExtraData bibliographicRecordExtraData = state.getRecordExtraData();
            if (bibliographicRecordExtraData != null) {
                if (bibliographicRecordExtraData.getProviderName() == null) {
                    throw new UpdateException(state.getMessages().getString("extra.record.data.provider.name.is.missing"));
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

    private void logRequest() {
        if (state.getWsContext() != null && state.getWsContext().getMessageContext() != null) {
            MessageContext mc = state.getWsContext().getMessageContext();
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
        bizLogger.info("Template name: {}", state.getSchemaName());
        bizLogger.info("ValidationOnly option: {}", hasValidateOnlyOption() ? "True" : "False");
        bizLogger.info("Request record: \n{}", state.readRecord());
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

            if (isProduction && state.getUpdateRecordRequest() != null && state.getUpdateRecordRequest().getAuthentication() != null && state.getUpdateRecordRequest().getAuthentication().getGroupIdAut() != null && state.getUpdateRecordRequest().getAuthentication().getGroupIdAut().startsWith("13")) {
                res = false;
            }
            return res;
        } finally {
            logger.exit(res);
        }
    }
}
