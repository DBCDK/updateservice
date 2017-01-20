package dk.dbc.updateservice.ws;

import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.iscrum.utils.json.Json;
import dk.dbc.updateservice.actions.*;
import dk.dbc.updateservice.auth.Authenticator;
import dk.dbc.updateservice.dto.*;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.javascript.ScripterPool;
import dk.dbc.updateservice.update.*;
import dk.dbc.updateservice.validate.Validator;
import org.apache.commons.lang3.builder.RecursiveToStringStyle;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.MDC;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import javax.xml.ws.handler.MessageContext;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.UUID;

/**
 * UpdateService web service.
 * <p>
 * Validates a record by using an JavaScript engine. This EJB also has
 * the responsibility to return a list of valid validation schemes.
 */
@Stateless
public class UpdateService {
    private static final XLogger logger = XLoggerFactory.getXLogger(UpdateService.class);
    private static final String GET_SCHEMAS_WATCHTAG = "request.getSchemas";
    private static final String UPDATE_SERVICE_UNAVAIABLE = "update.service.unavailable";
    private static final String UPDATE_SERVICE_NIL_RECORD = "update.service.nil.record";
    private static final String UPDATE_SERIVCE_INTERNAL_ERROR = "update.service.internal.error";

    public static final String MARSHALLING_ERROR_MSG = "Got an error while marshalling input request, using reflection instead.";
    public static final String UPDATE_WATCHTAG = "request.updaterecord";
    public static final String MDC_REQUEST_ID_LOG_CONTEXT = "requestId";
    public static final String MDC_PREFIX_ID_LOG_CONTEXT = "prefixId";
    public static final String MDC_TRACKING_ID_LOG_CONTEXT = "trackingId";
    public static final String UPDATE_SERVICE_VERSION = "2.0";

    @Resource(lookup = JNDIResources.SETTINGS_NAME)
    private Properties settings;

    @EJB
    private Authenticator authenticator;

    @EJB
    private Scripter scripter;

    @EJB
    private RawRepo rawRepo;

    @EJB
    private HoldingsItems holdingsItems;

    @EJB
    private OpenAgencyService openAgencyService;

    @EJB
    private SolrService solrService;

    @EJB
    private ScripterPool scripterPool;

    @EJB
    private Validator validator;

    @EJB
    private UpdateStore updateStore;

    @EJB
    private LibraryRecordsHandler libraryRecordsHandler;

    private GlobalActionState inititializeGlobalStateObject(GlobalActionState globalActionState, UpdateServiceRequestDto updateServiceRequestDto) {
        GlobalActionState newGlobalActionStateObject = new GlobalActionState(globalActionState);
        newGlobalActionStateObject.setUpdateServiceRequestDto(updateServiceRequestDto);
        newGlobalActionStateObject.setAuthenticator(authenticator);
        newGlobalActionStateObject.setScripter(scripter);
        newGlobalActionStateObject.setRawRepo(rawRepo);
        newGlobalActionStateObject.setHoldingsItems(holdingsItems);
        newGlobalActionStateObject.setOpenAgencyService(openAgencyService);
        newGlobalActionStateObject.setSolrService(solrService);
        newGlobalActionStateObject.setValidator(validator);
        newGlobalActionStateObject.setUpdateStore(updateStore);
        newGlobalActionStateObject.setLibraryRecordsHandler(libraryRecordsHandler);
        newGlobalActionStateObject.setMessages(ResourceBundles.getBundle("actions"));
        newGlobalActionStateObject.setUpdateMode(new UpdateMode(settings.getProperty(JNDIResources.JAVASCRIPT_INSTALL_NAME_KEY)));
        validateRequiredSettings();
        return newGlobalActionStateObject;
    }

    /**
     * Update or validate a bibliographic record to the rawrepo.
     * <p>
     * This operation has 2 uses:
     * <ol>
     * <li>Validation of the record only.</li>
     * <li>Validation and update of the record</li>
     * </ol>
     * The actual operation is specified in the request by Options object
     *
     * @param updateServiceRequestDto The request.
     * @return Returns an instance of UpdateRecordResult with the status of the
     * status and result of the update.
     * @throws EJBException in the case of an error.
     */
    public ServiceResult updateRecord(UpdateServiceRequestDto updateServiceRequestDto, GlobalActionState globalActionState) {
        logger.entry();
        StopWatch watch = new Log4JStopWatch();
        ServiceResult serviceResult = null;
        GlobalActionState state = inititializeGlobalStateObject(globalActionState, updateServiceRequestDto);
        logMdcUpdateMethodEntry(state);
        UpdateRequestAction updateRequestAction = null;
        ServiceEngine serviceEngine = null;
        try {
            if (state.readRecord() != null) {
                logger.info("MDC: " + MDC.getCopyOfContextMap());
                logger.info("Request tracking id: " + updateServiceRequestDto.getTrackingId());
                updateRequestAction = new UpdateRequestAction(state, settings);
                serviceEngine = new ServiceEngine();
                serviceEngine.setLoggerKeys(MDC.getCopyOfContextMap());
                serviceResult = serviceEngine.executeAction(updateRequestAction);
            } else {
                ResourceBundle bundle = ResourceBundles.getBundle("messages");
                String msg = bundle.getString(UPDATE_SERVICE_NIL_RECORD);

                serviceResult = ServiceResult.newErrorResult(UpdateStatusEnumDto.FAILED, msg, state);
                logger.error("Updateservice blev kaldt med tom record DTO");
            }

            return serviceResult;
        } catch (Throwable ex) {
            logger.catching(ex);
            serviceResult = convertUpdateErrorToResponse(ex, state);
            return serviceResult;
        } finally {
            logger.exit(serviceResult);
            updateServiceFinallyCleanUp(watch, updateRequestAction, serviceEngine);
        }
    }

    public boolean isServiceReady(GlobalActionState globalActionState) {
        logger.entry();
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
                        logger.catching(XLogger.Level.ERROR, e);
                    }
                }
            }
            return res;
        } finally {
            logger.exit(res);
        }

    }

    private void logMdcUpdateMethodEntry(GlobalActionState globalActionState) {
        UpdateServiceRequestDto updateServiceRequestDto = globalActionState.getUpdateServiceRequestDto();
        UUID prefixId = UUID.randomUUID();
        MDC.put(MDC_REQUEST_ID_LOG_CONTEXT, updateServiceRequestDto.getTrackingId());
        MDC.put(MDC_PREFIX_ID_LOG_CONTEXT, prefixId.toString());
        String trackingId = prefixId.toString();
        if (updateServiceRequestDto.getTrackingId() != null) {
            trackingId = updateServiceRequestDto.getTrackingId();
        }
        MDC.put(MDC_TRACKING_ID_LOG_CONTEXT, trackingId);
    }

    private void updateServiceFinallyCleanUp(StopWatch watch, UpdateRequestAction action, ServiceEngine engine) {
        if (engine != null) {
            logger.info("Executed action:");
            engine.printActions(action);
        }
        logger.info("");
        String watchTag;
        if (action != null && action.hasValidateOnlyOption()) {
            watchTag = UPDATE_WATCHTAG + ".validate";
        } else {
            watchTag = UPDATE_WATCHTAG + ".update";
        }
        watch.stop(watchTag);
        MDC.clear();
    }

    /**
     * WS operation to return a list of validation schemes.
     * <p>
     * The actual lookup of validation schemes is done by the Validator EJB
     * ({@link Validator#getValidateSchemas ()})
     *
     * @param schemasRequestDto The request.
     * @return Returns an instance of GetValidateSchemasResult with the list of
     * validation schemes.
     * @throws EJBException In case of an error.
     */
    public SchemasResponseDto getSchemas(SchemasRequestDto schemasRequestDto) {
        StopWatch watch = new Log4JStopWatch();
        SchemasResponseDto schemasResponseDto;
        try {
            MDC.put(MDC_TRACKING_ID_LOG_CONTEXT, schemasRequestDto.getTrackingId());
            logger.entry(schemasRequestDto);
            logger.info(Json.encodePretty(schemasRequestDto));
            List<SchemaDto> schemaDtoList = validator.getValidateSchemas(schemasRequestDto.getAuthenticationDTO().getGroupId());
            schemasResponseDto = new SchemasResponseDto();
            schemasResponseDto.getSchemaDtoList().addAll(schemaDtoList);
            schemasResponseDto.setUpdateStatusEnumDto(UpdateStatusEnumDto.OK);
            schemasResponseDto.setError(false);
            return schemasResponseDto;
        } catch (ScripterException ex) {
            logger.error("Caught JavaScript exception: {}", ex.getCause());
            schemasResponseDto = new SchemasResponseDto();
            schemasResponseDto.setUpdateStatusEnumDto(UpdateStatusEnumDto.FAILED);
            // TODO: sæt en korrekt message vedr. fejl

            schemasResponseDto.setError(true);
            return schemasResponseDto;
        } catch (IOException ex) {
            logger.error("Caught runtime exception: {}", ex.getCause());
            schemasResponseDto = new SchemasResponseDto();
            // TODO: sæt en korrekt message vedr. fejl
            schemasResponseDto.setUpdateStatusEnumDto(UpdateStatusEnumDto.FAILED);
            schemasResponseDto.setError(true);
            return schemasResponseDto;
        } catch (RuntimeException ex) {
            // TODO: returner ordentlig fejl her
            logger.error("Caught runtime exception: {}", ex.getCause());
            throw ex;
        } finally {
            watch.stop(GET_SCHEMAS_WATCHTAG);
            logger.exit();
            MDC.remove(MDC_TRACKING_ID_LOG_CONTEXT);
        }
    }

//    private void logRequest(UpdateRequestReader reader) {
//        MessageContext mc = wsContext.getMessageContext();
//        HttpServletRequest req = (HttpServletRequest) mc.get(MessageContext.SERVLET_REQUEST);
//        logger.info("REQUEST:");
//        logger.info("======================================");
//        logger.info("Auth type: {}", req.getAuthType());
//        logger.info("Context path: {}", req.getContextPath());
//        logger.info("Content type: {}", req.getContentType());
//        logger.info("Content length: {}", req.getContentLengthLong());
//        logger.info("URI: {}", req.getRequestURI());
//        logger.info("Client address: {}", req.getRemoteAddr());
//        logger.info("Client host: {}", req.getRemoteHost());
//        logger.info("Client port: {}", req.getRemotePort());
//        logger.info("Headers");
//        logger.info("--------------------------------------");
//        logger.info("");
//        Enumeration<String> headerNames = req.getHeaderNames();
//        while (headerNames.hasMoreElements()) {
//            String name = headerNames.nextElement();
//            logger.info("{}: {}", name, req.getHeader(name));
//        }
//        logger.info("--------------------------------------");
//        logger.info("");
//        logger.info("Template name: {}", globalActionState.getSchemaName());
//        logger.info("ValidationOnly option: {}", reader.hasValidationOnlyOption() ? "True" : "False");
//        logger.info("Request record: \n{}", reader.readRecord().toString());
//        logger.info("======================================");
//    }

    private Throwable findServiceException(Throwable ex) {
        Throwable throwable = ex;
        while (throwable != null && throwable.getClass().getPackage().getName().startsWith("javax.ejb")) {
            throwable = throwable.getCause();
        }
        return throwable;
    }

    private ServiceResult convertUpdateErrorToResponse(Throwable ex, GlobalActionState globalActionState) {
        Throwable throwable = findServiceException(ex);
        ServiceResult serviceResult = ServiceResult.newFatalResult(UpdateStatusEnumDto.FAILED, throwable.getMessage(), globalActionState);
        return serviceResult;
    }

    private void validateRequiredSettings() {
        if (settings == null) {
            throw new IllegalStateException("JNDI settings '" + JNDIResources.SETTINGS_NAME + "' cannot be empty");
        }
        for (String s : JNDIResources.getListOfRequiredJNDIResources()) {
            if (!settings.containsKey(s)) {
                throw new IllegalStateException("Required JNDI resource '" + s + "' not found");
            }
        }
    }

    public String objectToStringReflection(Object object) {
        return (new ReflectionToStringBuilder(object, new RecursiveToStringStyle()).toString());
    }

}
