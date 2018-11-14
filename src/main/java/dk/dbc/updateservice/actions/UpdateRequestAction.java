/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.actions;

import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.updateservice.client.BibliographicRecordExtraData;
import dk.dbc.updateservice.dto.OptionEnumDTO;
import dk.dbc.updateservice.dto.OptionsDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
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
            children.add(new PreProcessingAction(state));
            children.add(new ValidateOperationAction(state, settings));
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
            String message = String.format(state.getMessages().getString("agency.is.not.allowed.for.this.instance"), state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId());
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
        }
        if (state.getUpdateServiceRequestDTO().getBibliographicRecordDTO() == null) {
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, state.getMessages().getString("request.record.is.missing"), state);
        }
        if (!state.isRecordSchemaValid()) {
            logger.warn("Unknown record schema: {}", state.getUpdateServiceRequestDTO().getBibliographicRecordDTO().getRecordSchema());
            return ServiceResult.newStatusResult(UpdateStatusEnumDTO.FAILED);
        }
        if (!state.isRecordPackingValid()) {
            logger.warn("Unknown record packing: {}", state.getUpdateServiceRequestDTO().getBibliographicRecordDTO().getRecordPacking());
            return ServiceResult.newStatusResult(UpdateStatusEnumDTO.FAILED);
        }
        if (!sanityCheckRecord()) {
            String message = state.getMessages().getString("sanity.check.failed");
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message, state);
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
            OptionsDTO optionsDTO = state.getUpdateServiceRequestDTO().getOptionsDTO();
            return optionsDTO != null && optionsDTO.getOption() != null && optionsDTO.getOption().contains(OptionEnumDTO.VALIDATE_ONLY);
        } finally {
            logger.exit();
        }
    }

    private ServiceAction createUpdateOperation() throws UpdateException {
        UpdateOperationAction updateOperationAction = new UpdateOperationAction(state, settings);
        if (state.getLibraryGroup().isDBC()) {
            // Overwrite "settings" with provider name from RecordExtraData
            BibliographicRecordExtraData bibliographicRecordExtraData = state.getRecordExtraData();
            if (bibliographicRecordExtraData != null) {
                Properties newSettings = (Properties) settings.clone();

                if (bibliographicRecordExtraData.getProviderName() != null) {
                    final String providerName = bibliographicRecordExtraData.getProviderName();
                    if (state.getRawRepo().checkProvider(providerName)) {
                        logger.info("Provider name found in request - using {} as override provider for rawrepo queue", providerName);
                        newSettings.setProperty(JNDIResources.RAWREPO_PROVIDER_ID_OVERRIDE, providerName);
                    } else {
                        logger.info("Provider name {} found in request but that provider doesn't match the queue configuration - aborting request.");
                        throw new UpdateException("Provider " + providerName + " findes ikke.");
                    }
                }

                if (bibliographicRecordExtraData.getPriority() != null) {
                    logger.info("Priority found in request - using {} as override priority for rawrepo queue", bibliographicRecordExtraData.getPriority());
                    newSettings.setProperty(JNDIResources.RAWREPO_PRIORITY_OVERRIDE, bibliographicRecordExtraData.getPriority().toString());
                }

                updateOperationAction.setSettings(newSettings);
            }
        }
        return updateOperationAction;
    }

    private void logRequest() {
        if (state.getWsContext() != null && state.getWsContext().getMessageContext() != null) {
            MessageContext mc = state.getWsContext().getMessageContext();
            HttpServletRequest req = (HttpServletRequest) mc.get(MessageContext.SERVLET_REQUEST);
            logger.info("REQUEST:");
            logger.info("======================================");
            logger.info("Auth type: {}", req.getAuthType());
            logger.info("Context path: {}", req.getContextPath());
            logger.info("Content type: {}", req.getContentType());
            logger.info("Content length: {}", req.getContentLengthLong());
            logger.info("URI: {}", req.getRequestURI());
            logger.info("Client address: {}", req.getRemoteAddr());
            // This takes 5 seconds ??
            logger.info("Client host: {}", req.getRemoteHost());
            logger.info("Client port: {}", req.getRemotePort());
            logger.info("Headers");
            logger.info("--------------------------------------");
            logger.info("");
            Enumeration<String> headerNames = req.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                logger.info("{}: {}", name, req.getHeader(name));
            }
            logger.info("--------------------------------------");
        }
        logger.info("");
        logger.info("Template name: {}", state.getSchemaName());
        logger.info("ValidationOnly option: {}", hasValidateOnlyOption() ? "True" : "False");
        logger.info("Request record: \n{}", state.readRecord());
        logger.info("======================================");
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
                    && state.getUpdateServiceRequestDTO() != null
                    && state.getUpdateServiceRequestDTO().getAuthenticationDTO() != null
                    && state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId() != null
                    && state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId().startsWith("13")) {
                res = false;
            }
            return res;
        } finally {
            logger.exit(res);
        }
    }

    private boolean sanityCheckRecord() {
        try {
            MarcRecord record = state.readRecord();
            MarcRecordReader reader = new MarcRecordReader(record);

            if (reader.hasField("001")) { // If 001 is completely missing it will be caught in a later validation
                if (!(reader.hasSubfield("001", "a") && !reader.getRecordId().isEmpty())) {
                    return false;
                }

                if (!(reader.hasSubfield("001", "b") && !reader.getAgencyId().isEmpty() && reader.getAgencyIdAsInt() > 0)) {
                    return false;
                }
            }
        } catch (Exception ex) {
            logger.error("Caught exception during sanity check", ex);
            return false;
        }

        return true;
    }
}
