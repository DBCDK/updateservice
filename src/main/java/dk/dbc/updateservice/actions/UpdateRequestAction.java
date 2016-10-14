package dk.dbc.updateservice.actions;

import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.updateservice.client.BibliographicRecordExtraData;
import dk.dbc.updateservice.dto.OptionEnumDto;
import dk.dbc.updateservice.dto.OptionsDto;
import dk.dbc.updateservice.dto.UpdateStatusEnumDto;
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

    public UpdateRequestAction(GlobalActionState globalActionState, Properties properties) {
        super(UpdateRequestAction.class.getSimpleName(), globalActionState);
        settings = properties;
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
            String message = String.format(state.getMessages().getString("agency.is.not.allowed.for.this.instance"), state.getUpdateServiceRequestDto().getAuthenticationDto().getGroupId());
            return ServiceResult.newErrorResult(UpdateStatusEnumDto.FAILED, message, state);
        }
        if (state.getUpdateServiceRequestDto().getBibliographicRecordDto() == null) {
            return ServiceResult.newErrorResult(UpdateStatusEnumDto.FAILED, state.getMessages().getString("request.record.is.missing"), state);
        }
        if (!state.isRecordSchemaValid()) {
            bizLogger.warn("Unknown record schema: {}", state.getUpdateServiceRequestDto().getBibliographicRecordDto().getRecordSchema());
            return ServiceResult.newStatusResult(UpdateStatusEnumDto.FAILED);
        }
        if (!state.isRecordPackingValid()) {
            bizLogger.warn("Unknown record packing: {}", state.getUpdateServiceRequestDto().getBibliographicRecordDto().getRecordPacking());
            return ServiceResult.newStatusResult(UpdateStatusEnumDto.FAILED);
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
            OptionsDto optionsDto = state.getUpdateServiceRequestDto().getOptionsDto();
            if (optionsDto != null && optionsDto.getOption() != null) {
                return optionsDto.getOption().contains(OptionEnumDto.VALIDATE_ONLY);
            }
            return false;
        } finally {
            logger.exit();
        }
    }

    private ServiceAction createValidateOperation() {
        ValidateOperationAction validateOperationAction = new ValidateOperationAction(state, settings);
        validateOperationAction.setOkStatus(UpdateStatusEnumDto.OK);
        return validateOperationAction;
    }

    private ServiceAction createUpdateOperation() throws UpdateException {
        UpdateOperationAction updateOperationAction = new UpdateOperationAction(state, settings);
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
            if (isProduction
                    && state.getUpdateServiceRequestDto() != null
                    && state.getUpdateServiceRequestDto().getAuthenticationDto() != null
                    && state.getUpdateServiceRequestDto().getAuthenticationDto().getGroupId() != null
                    && state.getUpdateServiceRequestDto().getAuthenticationDto().getGroupId().startsWith("13")) {
                res = false;
            }
            return res;
        } finally {
            logger.exit(res);
        }
    }
}
