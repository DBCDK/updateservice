/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.ws;

import dk.dbc.openagency.client.OpenAgencyException;
import dk.dbc.updateservice.actions.GlobalActionState;
import dk.dbc.updateservice.actions.ServiceEngine;
import dk.dbc.updateservice.actions.ServiceResult;
import dk.dbc.updateservice.actions.UpdateRequestAction;
import dk.dbc.updateservice.auth.Authenticator;
import dk.dbc.updateservice.client.BibliographicRecordExtraData;
import dk.dbc.updateservice.dto.SchemaDTO;
import dk.dbc.updateservice.dto.SchemasRequestDTO;
import dk.dbc.updateservice.dto.SchemasResponseDTO;
import dk.dbc.updateservice.dto.UpdateServiceRequestDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.javascript.ScripterPool;
import dk.dbc.updateservice.json.JsonMapper;
import dk.dbc.updateservice.service.api.ObjectFactory;
import dk.dbc.updateservice.service.api.UpdateRecordRequest;
import dk.dbc.updateservice.service.api.UpdateRecordResult;
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
import org.apache.commons.lang3.builder.RecursiveToStringStyle;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.MDC;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.ws.handler.MessageContext;
import java.io.IOException;
import java.io.StringWriter;
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

    public static final String MARSHALLING_ERROR_MSG = "Got an error while marshalling input request, using reflection instead.";
    public static final String UPDATE_WATCHTAG = "request.updaterecord";
    public static final String MDC_REQUEST_ID_LOG_CONTEXT = "requestId";
    public static final String MDC_PREFIX_ID_LOG_CONTEXT = "prefixId";
    public static final String MDC_REQUEST_PRIORITY = "priority";
    public static final String MDC_TRACKING_ID_LOG_CONTEXT = "trackingId";
    public static final String UPDATE_SERVICE_VERSION = "2.0";

    private Properties settings = JNDIResources.getProperties();

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
    private SolrFBS solrService;

    @EJB
    private SolrBasis solrBasis;

    @EJB
    private ScripterPool scripterPool;

    @EJB
    private Validator validator;

    @EJB
    private UpdateStore updateStore;

    @EJB
    private LibraryRecordsHandler libraryRecordsHandler;

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
     * <p>
     * This operation has 2 uses:
     * <ol>
     * <li>Validation of the record only.</li>
     * <li>Validation and update of the record</li>
     * </ol>
     * The actual operation is specified in the request by Options object
     *
     * @param updateRecordRequest The request.
     * @return Returns an instance of UpdateRecordResult with the status of the
     * status and result of the update.
     * @throws EJBException in the case of an error.
     */
    public UpdateRecordResult updateRecord(UpdateRecordRequest updateRecordRequest, GlobalActionState globalActionState) throws SolrException {
        logger.entry();
        StopWatch watch = new Log4JStopWatch();
        ServiceResult serviceResult = null;
        UpdateRecordResult updateRecordResult = null;
        final UpdateRequestReader updateRequestReader = new UpdateRequestReader(updateRecordRequest);
        final UpdateServiceRequestDTO updateServiceRequestDTO = updateRequestReader.getUpdateServiceRequestDTO();
        final UpdateResponseWriter updateResponseWriter = new UpdateResponseWriter();
        final GlobalActionState state = inititializeGlobalStateObject(globalActionState, updateServiceRequestDTO);
        logMdcUpdateMethodEntry(state);
        UpdateRequestAction updateRequestAction = null;
        ServiceEngine serviceEngine = null;
        try {
            if (state.readRecord() != null) {
                final UpdateRecordRequest updateRecordRequestWithoutPassword = UpdateRequestReader.cloneWithoutPassword(updateRecordRequest);
                logger.info("Entering Updateservice, marshal(updateServiceRequestDto):\n" + marshal(updateRecordRequestWithoutPassword));
                logger.info("MDC: " + MDC.getCopyOfContextMap());
                logger.info("Request tracking id: " + updateServiceRequestDTO.getTrackingId());

                updateRequestAction = new UpdateRequestAction(state, settings);

                serviceEngine = new ServiceEngine();
                serviceEngine.setLoggerKeys(MDC.getCopyOfContextMap());
                serviceResult = serviceEngine.executeAction(updateRequestAction);

                updateResponseWriter.setServiceResult(serviceResult);

                updateRecordResult = updateResponseWriter.getResponse();

                logger.info("UpdateService returning updateRecordResult:\n" + JsonMapper.encodePretty(updateRecordResult));
                logger.info("Leaving UpdateService, marshal(updateRecordResult):\n" + marshal(updateRecordResult));
            } else {
                final ResourceBundle bundle = ResourceBundles.getBundle("messages");
                final String msg = bundle.getString(UPDATE_SERVICE_NIL_RECORD);

                serviceResult = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, msg);
                logger.error("Updateservice blev kaldt med tom record DTO");
            }
            return updateRecordResult;
        } catch (SolrException ex) {
            // have to catch and rethrow here, due to every throwable being caught below
            logger.error("catching and rethrowing SolrException");
            logger.catching(ex);
            throw new SolrException(ex.getMessage());
        } catch (Throwable ex) {
            logger.catching(ex);
            serviceResult = convertUpdateErrorToResponse(ex);
            updateResponseWriter.setServiceResult(serviceResult);
            updateRecordResult = updateResponseWriter.getResponse();
            return updateRecordResult;
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
     * @param schemasRequestDTO The request.
     * @return Returns an instance of GetValidateSchemasResult with the list of
     * validation schemes.
     * @throws EJBException In case of an error.
     */
    public SchemasResponseDTO getSchemas(SchemasRequestDTO schemasRequestDTO) {
        logger.entry();

        StopWatch watch = new Log4JStopWatch();
        SchemasResponseDTO schemasResponseDTO;
        try {
            MDC.put(MDC_TRACKING_ID_LOG_CONTEXT, schemasRequestDTO.getTrackingId());

            if (schemasRequestDTO.getAuthenticationDTO() != null &&
                    schemasRequestDTO.getAuthenticationDTO().getGroupId() != null) {
                if (schemasRequestDTO.getTrackingId() != null) {
                    logger.info("getSchemas request from {} with tracking id {}", schemasRequestDTO.getAuthenticationDTO().getGroupId(), schemasRequestDTO.getTrackingId());
                } else {
                    logger.info("getSchemas request from {}", schemasRequestDTO.getAuthenticationDTO().getGroupId());
                }
            }

            String groupId = schemasRequestDTO.getAuthenticationDTO().getGroupId();
            String templateGroup = openAgencyService.getTemplateGroup(groupId);
            List<SchemaDTO> schemaDTOList = validator.getValidateSchemas(groupId, templateGroup);
            schemasResponseDTO = new SchemasResponseDTO();
            schemasResponseDTO.getSchemaDTOList().addAll(schemaDTOList);
            schemasResponseDTO.setUpdateStatusEnumDTO(UpdateStatusEnumDTO.OK);
            schemasResponseDTO.setError(false);
            return schemasResponseDTO;
        } catch (ScripterException ex) {
            logger.error("Caught JavaScript exception: {}", ex.getCause().toString());
            schemasResponseDTO = new SchemasResponseDTO();
            schemasResponseDTO.setUpdateStatusEnumDTO(UpdateStatusEnumDTO.FAILED);
            // TODO: sæt en korrekt message vedr. fejl
            schemasResponseDTO.setError(true);
            return schemasResponseDTO;
        } catch (OpenAgencyException ex) {
            logger.error("Caught OpenAgencyException exception: {}", ex.getCause().toString());
            schemasResponseDTO = new SchemasResponseDTO();
            // TODO: sæt en korrekt message vedr. fejl
            schemasResponseDTO.setUpdateStatusEnumDTO(UpdateStatusEnumDTO.FAILED);
            schemasResponseDTO.setError(true);
            return schemasResponseDTO;
        } catch (RuntimeException ex) {
            // TODO: returner ordentlig fejl her
            logger.error("Caught runtime exception: {}", ex.getCause().toString());
            throw ex;
        } finally {
            watch.stop(GET_SCHEMAS_WATCHTAG);
            logger.exit();
            MDC.remove(MDC_TRACKING_ID_LOG_CONTEXT);
        }
    }

    private Throwable findServiceException(Throwable ex) {
        Throwable throwable = ex;
        while (throwable != null && throwable.getClass().getPackage().getName().startsWith("javax.ejb")) {
            throwable = throwable.getCause();
        }
        return throwable;
    }

    private ServiceResult convertUpdateErrorToResponse(Throwable ex) {
        Throwable throwable = findServiceException(ex);
        return ServiceResult.newFatalResult(UpdateStatusEnumDTO.FAILED, throwable.getMessage());
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

    @SuppressWarnings("Duplicates")
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
            logger.warn(UpdateService.MARSHALLING_ERROR_MSG);
            return objectToStringReflection(updateRecordRequest);
        }
    }

    @SuppressWarnings("Duplicates")
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
            logger.warn(UpdateService.MARSHALLING_ERROR_MSG);
            return objectToStringReflection(updateRecordResult);
        }
    }

    public String objectToStringReflection(Object object) {
        return new ReflectionToStringBuilder(object, new RecursiveToStringStyle()).toString();
    }

}
