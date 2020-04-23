package dk.dbc.updateservice.rest;

import dk.dbc.updateservice.actions.GlobalActionState;
import dk.dbc.updateservice.actions.ServiceEngine;
import dk.dbc.updateservice.actions.ServiceResult;
import dk.dbc.updateservice.actions.UpdateRequestAction;
import dk.dbc.updateservice.auth.Authenticator;
import dk.dbc.updateservice.client.BibliographicRecordExtraData;
import dk.dbc.updateservice.dto.UpdateRecordResponseDTO;
import dk.dbc.updateservice.dto.UpdateServiceRequestDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.dto.writers.UpdateRecordResponseDTOWriter;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.javascript.ScripterPool;
import dk.dbc.updateservice.json.JsonMapper;
import dk.dbc.updateservice.solr.SolrBasis;
import dk.dbc.updateservice.solr.SolrFBS;
import dk.dbc.updateservice.update.HoldingsItems;
import dk.dbc.updateservice.update.LibraryRecordsHandler;
import dk.dbc.updateservice.update.OpenAgencyService;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.SolrException;
import dk.dbc.updateservice.update.UpdateStore;
import dk.dbc.updateservice.utils.ResourceBundles;
import dk.dbc.updateservice.validate.Validator;
import dk.dbc.updateservice.ws.JNDIResources;
import java.io.IOException;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.UUID;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import javax.xml.ws.handler.MessageContext;
import org.apache.commons.lang3.builder.RecursiveToStringStyle;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.MDC;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

@Stateless
public class UpdateServiceInternal {

    @EJB
    private Authenticator authenticator;

    @EJB
    private Scripter scripter;

    @EJB
    private ScripterPool scripterPool;


    @EJB
    private RawRepo rawRepo;

    @EJB
    private HoldingsItems holdingsItems;

    @EJB
    private OpenAgencyService openAgencyService;

    @EJB
    private SolrFBS solrService;

    @EJB
    private SolrBasis solrBasis;

    @EJB
    private Validator validator;

    @EJB
    private UpdateStore updateStore;

    @EJB
    private LibraryRecordsHandler libraryRecordsHandler;


    private static final XLogger LOGGER = XLoggerFactory.getXLogger(UpdateServiceInternal.class);
    private static final String UPDATE_WATCHTAG = "request.updaterecord";
    public static final String MDC_REQUEST_ID_LOG_CONTEXT = "requestId";
    public static final String MDC_PREFIX_ID_LOG_CONTEXT = "prefixId";
    public static final String MDC_REQUEST_PRIORITY = "priority";
    private static final String UPDATE_SERVICE_NIL_RECORD = "update.service.nil.record";
    public static final String MDC_TRACKING_ID_LOG_CONTEXT = "trackingId";
    private static final String UPDATE_SERVICE_UNAVAIABLE = "update.service.unavailable";


    private Properties settings = JNDIResources.getProperties();

    private GlobalActionState inititializeGlobalStateObject(GlobalActionState globalActionState, UpdateServiceRequestDTO updateServiceRequestDTO) {
        GlobalActionState newGlobalActionStateObject = new GlobalActionState(globalActionState);
        newGlobalActionStateObject.setUpdateServiceRequestDTO(updateServiceRequestDTO);
        newGlobalActionStateObject.setAuthenticator(authenticator);
        newGlobalActionStateObject.setScripter(scripter);
        newGlobalActionStateObject.setRawRepo(rawRepo);
        newGlobalActionStateObject.setHoldingsItems(holdingsItems);
        newGlobalActionStateObject.setOpenAgencyService(openAgencyService);
        newGlobalActionStateObject.setSolrService(solrService);
        newGlobalActionStateObject.setSolrBasis(solrBasis);
        newGlobalActionStateObject.setValidator(validator);
        newGlobalActionStateObject.setUpdateStore(updateStore);
        newGlobalActionStateObject.setLibraryRecordsHandler(libraryRecordsHandler);
        newGlobalActionStateObject.setMessages(ResourceBundles.getBundle("actions"));
        newGlobalActionStateObject.setLibraryGroup(null);
        validateRequiredSettings();
        return newGlobalActionStateObject;
    }

    /**
     * Update or validate a bibliographic record to the rawrepo.
     * Request is in internal DTO format.
     * <p>
     * This operation has 2 uses:
     * <ol>
     * <li>Validation of the record only.</li>
     * <li>Validation and update of the record</li>
     * </ol>
     * The actual operation is specified in the request by Options object
     *
     * @param updateServiceRequestDTO The request (in internal format).
     * @return Returns an instance of UpdateRecordResult with the status of the
     * status and result of the update.
     * @throws EJBException in the case of an error.
     */
    public UpdateRecordResponseDTO updateRecord(UpdateServiceRequestDTO updateServiceRequestDTO, GlobalActionState globalActionState) {
        LOGGER.entry();
        StopWatch watch = new Log4JStopWatch();
        ServiceResult serviceResult = null;
        final GlobalActionState state = inititializeGlobalStateObject(globalActionState, updateServiceRequestDTO);
        logMdcUpdateMethodEntry(state);
        UpdateRequestAction updateRequestAction = null;
        ServiceEngine serviceEngine = null;
        try {
            if (state.readRecord() != null) {
                LOGGER.info("MDC: " + MDC.getCopyOfContextMap());
                LOGGER.info("Request tracking id: " + updateServiceRequestDTO.getTrackingId());

                updateRequestAction = new UpdateRequestAction(state, settings);

                serviceEngine = new ServiceEngine();
                serviceEngine.setLoggerKeys(MDC.getCopyOfContextMap());
                serviceResult = serviceEngine.executeAction(updateRequestAction);

                UpdateRecordResponseDTO updateRecordResponseDTO = UpdateRecordResponseDTOWriter.newInstance(serviceResult);

                LOGGER.info("UpdateService returning updateRecordResult:\n{}", JsonMapper.encodePretty(updateRecordResponseDTO));
                return updateRecordResponseDTO;
            } else {
                final ResourceBundle bundle = ResourceBundles.getBundle("messages");
                final String msg = bundle.getString(UPDATE_SERVICE_NIL_RECORD);

                serviceResult = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, msg);
                LOGGER.error("Updateservice blev kaldt med tom record DTO");
                return UpdateRecordResponseDTOWriter.newInstance(serviceResult);
            }
        } catch (SolrException ex) {
            LOGGER.error("Caught solr exception", ex);
            serviceResult = convertUpdateErrorToResponse(ex);
            return UpdateRecordResponseDTOWriter.newInstance(serviceResult);
        } catch (Throwable ex) {
            LOGGER.catching(ex);
            serviceResult = convertUpdateErrorToResponse(ex);
            return UpdateRecordResponseDTOWriter.newInstance(serviceResult);
        } finally {
            LOGGER.exit(serviceResult);
            updateServiceFinallyCleanUp(watch, updateRequestAction, serviceEngine);
        }
    }

    public boolean isServiceReady(GlobalActionState globalActionState) {
        LOGGER.entry();
        boolean res = true;
        try {
            if (scripterPool.getStatus() == ScripterPool.Status.ST_NA) {
                res = false;
                if (globalActionState != null && globalActionState.getWsContext() != null) {
                    MessageContext messageContext = globalActionState.getWsContext().getMessageContext();
                    HttpServletResponse httpServletResponse = (HttpServletResponse) messageContext.get(MessageContext.SERVLET_RESPONSE);
                    try {
                        ResourceBundle bundle = ResourceBundles.getBundle("messages");
                        String msg = bundle.getString(UPDATE_SERVICE_UNAVAIABLE);
                        httpServletResponse.sendError(Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), msg);
                    } catch (IOException e) {
                        LOGGER.catching(XLogger.Level.ERROR, e);
                    }
                }
            }
            return res;
        } finally {
            LOGGER.exit(res);
        }

    }

    private void logMdcUpdateMethodEntry(GlobalActionState globalActionState) {
        UpdateServiceRequestDTO updateServiceRequestDTO = globalActionState.getUpdateServiceRequestDTO();
        UUID prefixId = UUID.randomUUID();
        MDC.put(MDC_REQUEST_ID_LOG_CONTEXT, updateServiceRequestDTO.getTrackingId());
        MDC.put(MDC_PREFIX_ID_LOG_CONTEXT, prefixId.toString());

        final BibliographicRecordExtraData bibliographicRecordExtraData = globalActionState.getRecordExtraData();
        String priority = Integer.toString(RawRepo.ENQUEUE_PRIORITY_DEFAULT);
        if (bibliographicRecordExtraData != null && bibliographicRecordExtraData.getPriority() != null) {
            priority = bibliographicRecordExtraData.getPriority().toString();
        }
        MDC.put(MDC_REQUEST_PRIORITY, priority);

        String trackingId = prefixId.toString();
        if (updateServiceRequestDTO.getTrackingId() != null) {
            trackingId = updateServiceRequestDTO.getTrackingId();
        }
        MDC.put(MDC_TRACKING_ID_LOG_CONTEXT, trackingId);
    }

    private void updateServiceFinallyCleanUp(StopWatch watch, UpdateRequestAction action, ServiceEngine engine) {
        if (engine != null) {
            LOGGER.info("Executed action:");
            engine.printActions(action);
        }
        LOGGER.info("");
        String watchTag;
        if (action != null && action.hasValidateOnlyOption()) {
            watchTag = UPDATE_WATCHTAG + ".validate";
        } else {
            watchTag = UPDATE_WATCHTAG + ".update";
        }
        watch.stop(watchTag);
        MDC.clear();
    }

    private void validateRequiredSettings() {
        if (settings == null) {
            throw new IllegalStateException("JNDI settings cannot be empty");
        }
        for (String s : JNDIResources.getListOfRequiredJNDIResources()) {
            if (!settings.containsKey(s)) {
                throw new IllegalStateException("Required JNDI resource '" + s + "' not found");
            }
        }
    }

    private ServiceResult convertUpdateErrorToResponse(Throwable ex) {
        Throwable throwable = findServiceException(ex);
        return ServiceResult.newFatalResult(UpdateStatusEnumDTO.FAILED, throwable.getMessage());
    }

    private Throwable findServiceException(Throwable ex) {
        Throwable throwable = ex;
        while (throwable != null && throwable.getClass().getPackage().getName().startsWith("javax.ejb")) {
            throwable = throwable.getCause();
        }
        return throwable;
    }
}
