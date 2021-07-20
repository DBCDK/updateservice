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
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.UpdateException;
import dk.dbc.updateservice.update.JNDIResources;
import dk.dbc.updateservice.utils.MDCUtil;
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
    private static final XLogger LOGGER = XLoggerFactory.getXLogger(UpdateRequestAction.class);

    private final Properties settings;

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
        logRequest();
        final ServiceResult message = verifyData();
        if (message != null) {
            return message;
        }
        children.add(new PreProcessingAction(state));
        children.add(new ValidateOperationAction(state, settings));
        if (!hasValidateOnlyOption()) {
            final MarcRecordReader reader = new MarcRecordReader(state.readRecord());
            if (RawRepo.MATVURD_AGENCY == reader.getAgencyIdAsInt()) {
                // Since this can result in writing/creating the common part of the matvurd record even when there is an error
                // in the r01/r02 fields, it has to be done before any kind of writing in the records table
                // Information that needs check is in the enrichment part so we have to look at the full request record
                children.add(new MatVurdR01R02CheckRecordsAction(state, state.readRecord()));
            }
            children.add(createUpdateOperation());
        }

        return ServiceResult.newOkResult();
    }

    private ServiceResult verifyData() throws UpdateException {
        if (!isAgencyIdAllowedToUseUpdateOnThisInstance()) {
            String message = String.format(state.getMessages().getString("agency.is.not.allowed.for.this.instance"), state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId());
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
        }
        if (state.getUpdateServiceRequestDTO().getBibliographicRecordDTO() == null) {
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, state.getMessages().getString("request.record.is.missing"));
        }
        if (!state.isRecordSchemaValid()) {
            LOGGER.warn("Unknown record schema: {}", state.getUpdateServiceRequestDTO().getBibliographicRecordDTO().getRecordSchema());
            return ServiceResult.newStatusResult(UpdateStatusEnumDTO.FAILED);
        }
        if (!state.isRecordPackingValid()) {
            LOGGER.warn("Unknown record packing: {}", state.getUpdateServiceRequestDTO().getBibliographicRecordDTO().getRecordPacking());
            return ServiceResult.newStatusResult(UpdateStatusEnumDTO.FAILED);
        }
        if (!sanityCheckRecord()) {
            String message = state.getMessages().getString("sanity.check.failed");
            return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, message);
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
     * It is declared public so {@link dk.dbc.updateservice.update.UpdateServiceCore} can use it.
     * </p>
     *
     * @return boolean value.
     */
    public boolean hasValidateOnlyOption() {
        final OptionsDTO optionsDTO = state.getUpdateServiceRequestDTO().getOptionsDTO();
        return optionsDTO != null && optionsDTO.getOption() != null && optionsDTO.getOption().contains(OptionEnumDTO.VALIDATE_ONLY);
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
                        LOGGER.info("Provider name found in request - using {} as override provider for rawrepo queue", providerName);
                        newSettings.setProperty(JNDIResources.RAWREPO_PROVIDER_ID_OVERRIDE, providerName);
                    } else {
                        LOGGER.info("Provider name {} found in request but that provider doesn't match the queue configuration - aborting request.");
                        throw new UpdateException("Provider " + providerName + " findes ikke.");
                    }
                }

                if (bibliographicRecordExtraData.getPriority() != null) {
                    LOGGER.info("Priority found in request - using {} as override priority for rawrepo queue", bibliographicRecordExtraData.getPriority());
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
            LOGGER.info("REQUEST:");
            LOGGER.info("======================================");
            LOGGER.info("Auth type: {}", req.getAuthType());
            LOGGER.info("Context path: {}", req.getContextPath());
            LOGGER.info("Content type: {}", req.getContentType());
            LOGGER.info("Content length: {}", req.getContentLengthLong());
            LOGGER.info("URI: {}", req.getRequestURI());
            LOGGER.info("Client address: {}", req.getRemoteAddr());
            // This takes 5 seconds ??
            LOGGER.info("Client host: {}", req.getRemoteHost());
            LOGGER.info("Client port: {}", req.getRemotePort());
            LOGGER.info("Headers");
            LOGGER.info("--------------------------------------");
            LOGGER.info("");
            Enumeration<String> headerNames = req.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                LOGGER.info("{}: {}", name, req.getHeader(name));
            }
            LOGGER.info("--------------------------------------");
        }
        LOGGER.info("");
        LOGGER.info("Template name: {}", state.getSchemaName());
        LOGGER.info("ValidationOnly option: {}", hasValidateOnlyOption() ? "True" : "False");
        LOGGER.info("Request record: \n{}", state.readRecord());
        LOGGER.info("======================================");
    }

    private boolean isAgencyIdAllowedToUseUpdateOnThisInstance() throws UpdateException {
        if (!settings.containsKey(JNDIResources.UPDATE_PROD_STATE) || settings.getProperty(JNDIResources.UPDATE_PROD_STATE) == null) {
            throw new UpdateException("Required property '" + JNDIResources.UPDATE_PROD_STATE + "' not found");
        }
        final boolean isProduction = Boolean.parseBoolean(settings.getProperty(JNDIResources.UPDATE_PROD_STATE));

        return !isProduction
                || state.getUpdateServiceRequestDTO() == null
                || state.getUpdateServiceRequestDTO().getAuthenticationDTO() == null
                || state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId() == null
                || !state.getUpdateServiceRequestDTO().getAuthenticationDTO().getGroupId().startsWith("13");
    }

    private boolean sanityCheckRecord() {
        try {
            final MarcRecord marcRecord = state.readRecord();
            final MarcRecordReader reader = new MarcRecordReader(marcRecord);

            if (reader.hasField("001")) { // If 001 is completely missing it will be caught in a later validation
                if (!(reader.hasSubfield("001", "a") && !reader.getRecordId().isEmpty())) {
                    return false;
                }

                if (!(reader.hasSubfield("001", "b") && !reader.getAgencyId().isEmpty() && reader.getAgencyIdAsInt() > 0)) {
                    return false;
                }
            }
        } catch (Exception ex) {
            LOGGER.error("Caught exception during sanity check", ex);
            return false;
        }

        return true;
    }
}
