package dk.dbc.updateservice.ws;

import com.sun.xml.ws.developer.SchemaValidation;
import dk.dbc.iscrum.utils.ResourceBundles;
import dk.dbc.iscrum.utils.json.Json;
import dk.dbc.iscrum.utils.logback.filters.BusinessLoggerFilter;
import dk.dbc.updateservice.actions.GlobalActionState;
import dk.dbc.updateservice.actions.ServiceEngine;
import dk.dbc.updateservice.actions.ServiceResult;
import dk.dbc.updateservice.actions.UpdateRequestAction;
import dk.dbc.updateservice.auth.Authenticator;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.javascript.ScripterPool;
import dk.dbc.updateservice.service.api.CatalogingUpdatePortType;
import dk.dbc.updateservice.service.api.Entry;
import dk.dbc.updateservice.service.api.GetSchemasRequest;
import dk.dbc.updateservice.service.api.GetSchemasResult;
import dk.dbc.updateservice.service.api.ObjectFactory;
import dk.dbc.updateservice.service.api.Options;
import dk.dbc.updateservice.service.api.Param;
import dk.dbc.updateservice.service.api.Params;
import dk.dbc.updateservice.service.api.Schema;
import dk.dbc.updateservice.service.api.SchemasStatusEnum;
import dk.dbc.updateservice.service.api.Type;
import dk.dbc.updateservice.service.api.UpdateRecordRequest;
import dk.dbc.updateservice.service.api.UpdateRecordResult;
import dk.dbc.updateservice.service.api.UpdateStatusEnum;
import dk.dbc.updateservice.update.HoldingsItems;
import dk.dbc.updateservice.update.LibraryRecordsHandler;
import dk.dbc.updateservice.update.OpenAgencyService;
import dk.dbc.updateservice.update.RawRepo;
import dk.dbc.updateservice.update.SolrService;
import dk.dbc.updateservice.update.UpdateStore;
import dk.dbc.updateservice.validate.Validator;
import org.apache.commons.lang3.builder.RecursiveToStringStyle;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.MDC;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.jws.WebService;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.MessageContext;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Enumeration;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.UUID;

/**
 * Implements the Update web service.
 * <p>
 * The web services uses 3 EJB's to implement its functionality:
 * <dl>
 * <dt>Validator</dt>
 * <dd>
 * Validates a record by using an JavaScript engine. This EJB also has
 * the responsibility to return a list of valid validation schemes.
 * </dd>
 * <dt>Updater</dt>
 * <dd>
 * Updates a record in the rawrepo.
 * </dd>
 * <dt>JSEngine</dt>
 * <dd>EJB to execute JavaScript.</dd>
 * </dl>
 * Graphically it looks like this:
 * <p>
 * <img src="doc-files/ejbs.png" alt="ejbs.png">
 *
 * @author stp
 */
@WebService(
        serviceName = "CatalogingUpdateServices",
        portName = "CatalogingUpdatePort",
        endpointInterface = "dk.dbc.updateservice.service.api.CatalogingUpdatePortType",
        targetNamespace = "http://oss.dbc.dk/ns/catalogingUpdate",
        wsdlLocation = "WEB-INF/classes/META-INF/wsdl/update/catalogingUpdate.wsdl")
@SchemaValidation
@Stateless
public class UpdateService implements CatalogingUpdatePortType {
    private static final XLogger logger = XLoggerFactory.getXLogger(UpdateService.class);
    private static final XLogger bizLogger = XLoggerFactory.getXLogger(BusinessLoggerFilter.LOGGER_NAME);
    public static final String MDC_REQUEST_ID_LOG_CONTEXT = "requestId";
    public static final String MDC_PREFIX_ID_LOG_CONTEXT = "prefixId";
    public static final String MDC_TRACKING_ID_LOG_CONTEXT = "trackingId";

    @Resource(lookup = JNDIResources.SETTINGS_NAME)
    private Properties settings;

    @Resource
    private WebServiceContext wsContext;

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

    private LibraryRecordsHandler recordsHandler;
    private GlobalActionState globalActionState;

    /**
     * Initialization of the EJB after it has been created by the JavaEE
     * container.
     * <p>
     * It simply initialize the XLogger instance for logging.
     */
    @PostConstruct
    public void init() {
        logger.entry();
        try {
            if (recordsHandler == null) {
                recordsHandler = new LibraryRecordsHandler(scripter);
            }
            globalActionState = new GlobalActionState();
            globalActionState.setWsContext(wsContext);
            globalActionState.setAuthenticator(authenticator);
            globalActionState.setScripter(scripter);
            globalActionState.setRawRepo(rawRepo);
            globalActionState.setHoldingsItems(holdingsItems);
            globalActionState.setOpenAgencyService(openAgencyService);
            globalActionState.setSolrService(solrService);
            globalActionState.setValidator(validator);
            globalActionState.setUpdateStore(updateStore);
            globalActionState.setLibraryRecordsHandler(recordsHandler);
            globalActionState.setMessages(ResourceBundles.getBundle("actions"));
        } catch (MissingResourceException ex) {
            logger.error("Unable to load resource", ex);
        } finally {
            logger.exit();
        }
    }

    /**
     * Update or validate a bibliographic record to the rawrepo.
     * <p>
     * This operation has 2 uses:
     * <ol>
     * <li>Validation of the record only.</li>
     * <li>Validation and update of the record</li>
     * </ol>
     * The actual operation is specified in the request by {@link Options}
     *
     * @param updateRecordRequest The request.
     * @return Returns an instance of UpdateRecordResult with the status of the
     * status and result of the update.
     * @throws EJBException in the case of an error.
     */
    @Override
    public UpdateRecordResult updateRecord(UpdateRecordRequest updateRecordRequest) {
        StopWatch watch = new Log4JStopWatch();
        if (scripterPool.getStatus() == ScripterPool.Status.ST_NA) {
            MessageContext messageContext = wsContext.getMessageContext();
            HttpServletResponse httpServletResponse = (HttpServletResponse) messageContext.get(MessageContext.SERVLET_RESPONSE);
            try {
                httpServletResponse.sendError(Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), "Updateservice er ikke klar endnu, prøv igen senere");
            } catch (IOException e) {
                logger.catching(XLogger.Level.ERROR, e);
                return null;
            }
        }
        logMdcUpdateMethodEntry(updateRecordRequest);
        logger.entry(updateRecordRequest);
        logger.info("Entering Updateservice, marshal(updateRecordRequest):\n" + marshal(updateRecordRequest));

        globalActionState.setUpdateRecordRequest(updateRecordRequest);

        UpdateResponseWriter updateResponseWriter = new UpdateResponseWriter();
        UpdateRequestAction updateRequestAction = null;
        ServiceEngine serviceEngine = null;
        UpdateRecordResult result = null;
        try {
            logger.info("MDC: " + MDC.getCopyOfContextMap());
            logger.info("Request tracking id: " + updateRecordRequest.getTrackingId());
            updateRequestAction = new UpdateRequestAction(globalActionState, settings, globalActionState.readRecord());
            serviceEngine = new ServiceEngine();
            serviceEngine.setLoggerKeys(MDC.getCopyOfContextMap());
            ServiceResult serviceResult = serviceEngine.executeAction(updateRequestAction);
            updateResponseWriter.setUpdateStatus(serviceResult.getStatus());
            updateResponseWriter.addValidateEntries(serviceResult.getEntries());
            result = updateResponseWriter.getResponse();
            bizLogger.info("Returning response:\n{}", Json.encodePretty(result));
            logger.info("Leaving Updateservice, marshal(result):\n" + marshal(result));
            return result;
        } catch (Throwable ex) {
            bizLogger.error("Caught Exception: {}", findServiceException(ex).getMessage());
            logger.error("Caught Exception: {}", ex);
            //TODO: VERSION2: update to version 2.0 schema
            updateResponseWriter = convertUpdateErrorToResponse(ex, UpdateStatusEnum.FAILED, updateRecordRequest);
            return result = updateResponseWriter.getResponse();
        } finally {
            updateServiceFinallyCleanUp(watch, updateRequestAction, serviceEngine, result);
        }
    }

    private void logMdcUpdateMethodEntry(UpdateRecordRequest updateRecordRequest) {
        UUID prefixId = UUID.randomUUID();
        MDC.put(MDC_REQUEST_ID_LOG_CONTEXT, updateRecordRequest.getTrackingId());
        MDC.put(MDC_PREFIX_ID_LOG_CONTEXT, prefixId.toString());
        String trackingId = prefixId.toString();
        if (updateRecordRequest.getTrackingId() != null) {
            trackingId = updateRecordRequest.getTrackingId();
        }
        MDC.put(MDC_TRACKING_ID_LOG_CONTEXT, trackingId);
    }

    private void updateServiceFinallyCleanUp(StopWatch watch, UpdateRequestAction action, ServiceEngine engine, UpdateRecordResult result) {
        if (engine != null) {
            bizLogger.info("");
            bizLogger.info("Executed action:");
            engine.printActions(action);
        }
        bizLogger.info("");
        String watchTag = "request.updaterecord";
        if (action != null && action.hasValidateOnlyOption()) {
            watchTag += ".validate";
        } else {
            watchTag += ".update";
        }
        watch.stop(watchTag);
        bizLogger.exit(result);
        MDC.clear();
    }

    /**
     * WS operation to return a list of validation schemes.
     * <p>
     * The actual lookup of validation schemes is done by the Validator EJB
     * ({@link Validator#getValidateSchemas ()})
     *
     * @param getValidateSchemasRequest The request.
     * @return Returns an instance of GetValidateSchemasResult with the list of
     * validation schemes.
     * @throws EJBException In case of an error.
     */
    @Override
    public GetSchemasResult getSchemas(GetSchemasRequest getValidateSchemasRequest) {
        StopWatch watch = new Log4JStopWatch();
        try {
            MDC.put(MDC_TRACKING_ID_LOG_CONTEXT, getValidateSchemasRequest.getTrackingId());
            logger.entry(getValidateSchemasRequest);
            bizLogger.info(Json.encodePretty(getValidateSchemasRequest));
            List<Schema> names = validator.getValidateSchemas(getValidateSchemasRequest.getAuthentication().getGroupIdAut());
            GetSchemasResult response = new GetSchemasResult();
            response.setSchemasStatus(SchemasStatusEnum.OK);
            response.getSchema().addAll(names);
            return response;
        } catch (ScripterException ex) {
            logger.error("Caught JavaScript exception: {}", ex.getCause());
            GetSchemasResult response = new GetSchemasResult();
            response.setSchemasStatus(SchemasStatusEnum.FAILED_INTERNAL_ERROR);
            return response;
        } catch (IOException ex) {
            logger.error("Caught runtime exception: {}", ex.getCause());
            GetSchemasResult response = new GetSchemasResult();
            response.setSchemasStatus(SchemasStatusEnum.FAILED_INTERNAL_ERROR);
            return response;
        } catch (RuntimeException ex) {
            logger.error("Caught runtime exception: {}", ex.getCause());
            throw ex;
        } finally {
            watch.stop("request.getSchemas");
            logger.exit();
            MDC.remove(MDC_TRACKING_ID_LOG_CONTEXT);
        }
    }

    private void logRequest(UpdateRequestReader reader) {
        MessageContext mc = wsContext.getMessageContext();
        HttpServletRequest req = (HttpServletRequest) mc.get(MessageContext.SERVLET_REQUEST);

        bizLogger.info("REQUEST:");
        bizLogger.info("======================================");
        bizLogger.info("Auth type: {}", req.getAuthType());
        bizLogger.info("Context path: {}", req.getContextPath());
        bizLogger.info("Content type: {}", req.getContentType());
        bizLogger.info("Content length: {}", req.getContentLengthLong());
        bizLogger.info("URI: {}", req.getRequestURI());
        bizLogger.info("Client address: {}", req.getRemoteAddr());
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
        bizLogger.info("");
        bizLogger.info("Template name: {}", globalActionState.getSchemaName());
        bizLogger.info("ValidationOnly option: {}", reader.hasValidationOnlyOption() ? "True" : "False");
        bizLogger.info("Request record: \n{}", reader.readRecord().toString());
        bizLogger.info("======================================");
    }

    private Throwable findServiceException(Throwable ex) {
        Throwable throwable = ex;
        while (throwable != null && throwable.getClass().getPackage().getName().startsWith("javax.ejb")) {
            throwable = throwable.getCause();
        }
        return throwable;
    }

    private UpdateResponseWriter convertUpdateErrorToResponse(Throwable ex, UpdateStatusEnum status, UpdateRecordRequest updateRecordRequest) {
        Throwable throwable = findServiceException(ex);
        UpdateResponseWriter writer = new UpdateResponseWriter();
        writer.setUpdateStatus(status);
        //TODO: VERSION2: update to version 2.0 schema
        Entry entry = new Entry();
        entry.setType(Type.ERROR);
        Params params = new Params();
        entry.setParams(params);
        Param param = new Param();
        entry.getParams().getParam().add(param);
        param.setKey("pid");
        param.setValue(updateRecordRequest.getTrackingId());
        param = new Param();
        entry.getParams().getParam().add(param);
        param.setKey("message");
        param.setValue(throwable.getMessage());
        return writer;
    }

    private String marshal(UpdateRecordRequest updateRecordRequest) {
        try {
            ObjectFactory objectFactory = new ObjectFactory();
            JAXBElement<UpdateRecordRequest> jAXBElement = objectFactory.createUpdateRecordRequest(updateRecordRequest);
            StringWriter stringWriter = new StringWriter();
            JAXBContext jaxbContext = JAXBContext.newInstance(UpdateRecordRequest.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.marshal(jAXBElement, stringWriter);
            return stringWriter.toString();
        } catch (JAXBException e) {
            logger.catching(e);
            logger.warn("Got an error while marshalling input request, using reflectiong instead:");
            return objectToStringReflection(updateRecordRequest);
        }
    }

    private String marshal(UpdateRecordResult updateRecordResult) {
        try {
            ObjectFactory objectFactory = new ObjectFactory();
            JAXBElement<UpdateRecordResult> jAXBElement = objectFactory.createUpdateRecordResult(updateRecordResult);
            StringWriter stringWriter = new StringWriter();
            JAXBContext jaxbContext = JAXBContext.newInstance(UpdateRecordResult.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.marshal(jAXBElement, stringWriter);
            return stringWriter.toString();
        } catch (JAXBException e) {
            logger.catching(e);
            logger.warn("Got an error while marshalling input request, using reflectiong instead:");
            return objectToStringReflection(updateRecordResult);
        }
    }

    private String objectToStringReflection(Object object) {
        return (new ReflectionToStringBuilder(object, new RecursiveToStringStyle()).toString());
    }
}
