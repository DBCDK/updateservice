/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.common.records.MarcConverter;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.utils.RecordContentTransformer;
import dk.dbc.openagency.client.OpenAgencyException;
import dk.dbc.rawrepo.Record;
import dk.dbc.updateservice.actions.GlobalActionState;
import dk.dbc.updateservice.actions.ServiceEngine;
import dk.dbc.updateservice.actions.ServiceResult;
import dk.dbc.updateservice.actions.UpdateRequestAction;
import dk.dbc.updateservice.auth.Authenticator;
import dk.dbc.updateservice.client.BibliographicRecordExtraData;
import dk.dbc.updateservice.dto.BibliographicRecordDTO;
import dk.dbc.updateservice.dto.DoubleRecordFrontendDTO;
import dk.dbc.updateservice.dto.DoubleRecordFrontendStatusDTO;
import dk.dbc.updateservice.dto.MessageEntryDTO;
import dk.dbc.updateservice.dto.RecordDataDTO;
import dk.dbc.updateservice.dto.SchemaDTO;
import dk.dbc.updateservice.dto.SchemasRequestDTO;
import dk.dbc.updateservice.dto.SchemasResponseDTO;
import dk.dbc.updateservice.dto.TypeEnumDTO;
import dk.dbc.updateservice.dto.UpdateRecordResponseDTO;
import dk.dbc.updateservice.dto.UpdateServiceRequestDTO;
import dk.dbc.updateservice.dto.UpdateStatusEnumDTO;
import dk.dbc.updateservice.dto.writers.UpdateRecordResponseDTOWriter;
import dk.dbc.updateservice.javascript.Scripter;
import dk.dbc.updateservice.javascript.ScripterException;
import dk.dbc.updateservice.javascript.ScripterPool;
import dk.dbc.updateservice.json.JsonMapper;
import dk.dbc.updateservice.solr.SolrBasis;
import dk.dbc.updateservice.solr.SolrFBS;
import dk.dbc.updateservice.utils.ResourceBundles;
import dk.dbc.updateservice.validate.Validator;
import dk.dbc.updateservice.ws.JNDIResources;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.UUID;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import javax.xml.transform.dom.DOMSource;
import javax.xml.ws.handler.MessageContext;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.MDC;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.w3c.dom.Node;

@Stateless
public class UpdateServiceCore {

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
    public UpdateStore updateStore;

    @EJB
    private LibraryRecordsHandler libraryRecordsHandler;


    private static final XLogger LOGGER = XLoggerFactory.getXLogger(UpdateServiceCore.class);
    private static final String UPDATE_WATCHTAG = "request.updaterecord";
    private static final String GET_SCHEMAS_WATCHTAG = "request.getSchemas";
    public static final String MDC_REQUEST_ID_LOG_CONTEXT = "requestId";
    public static final String MDC_PREFIX_ID_LOG_CONTEXT = "prefixId";
    public static final String MDC_REQUEST_PRIORITY = "priority";
    private static final String UPDATE_SERVICE_NIL_RECORD = "update.service.nil.record";
    public static final String MDC_TRACKING_ID_LOG_CONTEXT = "trackingId";
    private static final String UPDATE_SERVICE_UNAVAIABLE = "update.service.unavailable";
    private static final String DOUBLE_RECORD_CHECK_ENTRY_POINT = "checkDoubleRecordFrontend";


    private Properties settings = JNDIResources.getProperties();

    private static final ResourceBundle resourceBundle = ResourceBundles.getBundle("actions");


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

    /**
     * Returns a list of validation schemes.
     * <p>
     * The actual lookup of validation schemes is done by the Validator EJB
     * ({@link Validator#getValidateSchemas ()})
     *
     * @param schemasRequestDTO The request.
     * @return Returns an instance of SchemasResponseDTO with the list of
     * validation schemes.
     * @throws EJBException In case of an error.
     */
    public SchemasResponseDTO getSchemas(SchemasRequestDTO schemasRequestDTO) {
        LOGGER.entry();

        StopWatch watch = new Log4JStopWatch();
        SchemasResponseDTO schemasResponseDTO;
        try {
            MDC.put(MDC_TRACKING_ID_LOG_CONTEXT, schemasRequestDTO.getTrackingId());

            if (schemasRequestDTO.getAuthenticationDTO() != null &&
                    schemasRequestDTO.getAuthenticationDTO().getGroupId() != null) {
                if (schemasRequestDTO.getTrackingId() != null) {
                    LOGGER.info("getSchemas request from {} with tracking id {}", schemasRequestDTO.getAuthenticationDTO().getGroupId(), schemasRequestDTO.getTrackingId());
                } else {
                    LOGGER.info("getSchemas request from {}", schemasRequestDTO.getAuthenticationDTO().getGroupId());
                }
            }

            LOGGER.info("getSchemas request as json:{}", JsonMapper.encodePretty(schemasRequestDTO));

            final String groupId = schemasRequestDTO.getAuthenticationDTO().getGroupId();
            final String templateGroup = openAgencyService.getTemplateGroup(groupId);
            final Set<String> allowedLibraryRules = openAgencyService.getAllowedLibraryRules(groupId);
            final List<SchemaDTO> schemaDTOList = validator.getValidateSchemas(templateGroup, allowedLibraryRules);

            schemasResponseDTO = new SchemasResponseDTO();
            schemasResponseDTO.getSchemaDTOList().addAll(schemaDTOList);
            schemasResponseDTO.setUpdateStatusEnumDTO(UpdateStatusEnumDTO.OK);
            schemasResponseDTO.setError(false);
            return schemasResponseDTO;
        } catch (ScripterException ex) {
            LOGGER.error("Caught JavaScript exception", ex);
            schemasResponseDTO = new SchemasResponseDTO();
            schemasResponseDTO.setErrorMessage(ex.getMessage());
            schemasResponseDTO.setUpdateStatusEnumDTO(UpdateStatusEnumDTO.FAILED);
            schemasResponseDTO.setError(true);
            return schemasResponseDTO;
        } catch (OpenAgencyException ex) {
            LOGGER.error("Caught OpenAgencyException exception", ex);
            schemasResponseDTO = new SchemasResponseDTO();
            schemasResponseDTO.setErrorMessage(ex.getMessage());
            schemasResponseDTO.setUpdateStatusEnumDTO(UpdateStatusEnumDTO.FAILED);
            schemasResponseDTO.setError(true);
            return schemasResponseDTO;
        } catch (Throwable ex) {
            // TODO: returner ordentlig fejl her
            LOGGER.error("Caught Throwable", ex);
            schemasResponseDTO = new SchemasResponseDTO();
            schemasResponseDTO.setErrorMessage(ex.getMessage());
            schemasResponseDTO.setUpdateStatusEnumDTO(UpdateStatusEnumDTO.FAILED);
            schemasResponseDTO.setError(true);
            return schemasResponseDTO;
        } finally {
            watch.stop(GET_SCHEMAS_WATCHTAG);
            LOGGER.exit();
            MDC.remove(MDC_TRACKING_ID_LOG_CONTEXT);
        }
    }

    public UpdateRecordResponseDTO classificationCheck(BibliographicRecordDTO bibliographicRecordDTO) {
        try {
            final RecordDataDTO recordDataDTO = bibliographicRecordDTO.getRecordDataDTO();
            MarcRecord record = getRecord(recordDataDTO);

            ServiceResult serviceResult = ServiceResult.newOkResult();
            if (record != null) {
                final MarcRecordReader recordReader = new MarcRecordReader(record);
                final String recordId = recordReader.getValue("001", "a");
                final int agencyId = Integer.parseInt(recordReader.getValue("001", "b"));
                if (rawRepo.recordExists(recordId, agencyId)) {
                    final MarcRecord oldRecord = loadRecord(recordId, agencyId);
                    final Set<Integer> holdingAgencies = holdingsItems.getAgenciesThatHasHoldingsForId(recordId);
                    if (holdingAgencies.size() > 0) {
                        List<String> classificationsChangedMessages = new ArrayList<>();
                        if (libraryRecordsHandler.hasClassificationsChanged(oldRecord, record, classificationsChangedMessages)) {
                            final List<MessageEntryDTO> messageEntryDTOs = new ArrayList<>();

                            final MessageEntryDTO holdingsMessageEntryDTO = new MessageEntryDTO();
                            holdingsMessageEntryDTO.setType(TypeEnumDTO.WARNING);
                            holdingsMessageEntryDTO.setMessage("Count: " + holdingAgencies.size());
                            messageEntryDTOs.add(holdingsMessageEntryDTO);

                            for (String classificationsChangedMessage : classificationsChangedMessages) {
                                final MessageEntryDTO messageEntryDTO = new MessageEntryDTO();
                                messageEntryDTO.setType(TypeEnumDTO.WARNING);
                                messageEntryDTO.setMessage("Reason: " + resourceBundle.getString(classificationsChangedMessage));
                                messageEntryDTOs.add(messageEntryDTO);
                            }

                            serviceResult = new ServiceResult();
                            serviceResult.setStatus(UpdateStatusEnumDTO.FAILED);
                            serviceResult.setEntries(messageEntryDTOs);
                        }
                    }
                }
            } else {
                serviceResult = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, "No record data found in request");
            }
            return UpdateRecordResponseDTOWriter.newInstance(serviceResult);
        } catch (Exception ex) {
            LOGGER.error("Exception during classificationCheck", ex);
            ServiceResult serviceResult = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, "Please see the log for more information");
            return UpdateRecordResponseDTOWriter.newInstance(serviceResult);
        }
    }

    public UpdateRecordResponseDTO doubleRecordCheck(BibliographicRecordDTO bibliographicRecordDTO) {
        try {
            final RecordDataDTO recordDataDTO = bibliographicRecordDTO.getRecordDataDTO();
            MarcRecord record = getRecord(recordDataDTO);


            ServiceResult serviceResult;
            if (record != null) {
                MarcRecordReader reader = new MarcRecordReader(record);

                // Perform double record check only if the record doesn't already exist
                if (!rawRepo.recordExistsMaybeDeleted(reader.getRecordId(), reader.getAgencyIdAsInt())) {
                    final Object jsResult = scripter.callMethod(DOUBLE_RECORD_CHECK_ENTRY_POINT, JsonMapper.encode(record), JNDIResources.getProperties());
                    serviceResult = parseJavascript(jsResult);
                } else {
                    serviceResult = ServiceResult.newOkResult();
                }
            } else {
                serviceResult = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, "No record data found in request");
            }
            return UpdateRecordResponseDTOWriter.newInstance(serviceResult);
        }
        catch (Exception ex) {
            LOGGER.error("Exception during doubleRecordCheck", ex);
            ServiceResult serviceResult = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, "Please see the log for more information");
            return UpdateRecordResponseDTOWriter.newInstance(serviceResult);
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

    protected MarcRecord loadRecord(String recordId, Integer agencyId) throws UpdateException, UnsupportedEncodingException {
        LOGGER.entry(recordId, agencyId);
        MarcRecord result = null;
        try {
            Record record = rawRepo.fetchRecord(recordId, agencyId);
            return result = RecordContentTransformer.decodeRecord(record.getContent());
        } finally {
            LOGGER.exit(result);
        }
    }

    public ServiceResult parseJavascript(Object o) throws IOException {
        ServiceResult result;
        DoubleRecordFrontendStatusDTO doubleRecordFrontendStatusDTO = JsonMapper.decode(o.toString(), DoubleRecordFrontendStatusDTO.class);
        if ("ok".equals(doubleRecordFrontendStatusDTO.getStatus())) {
            result = ServiceResult.newOkResult();
        } else if ("doublerecord".equals(doubleRecordFrontendStatusDTO.getStatus())) {
            result = new ServiceResult();
            for (DoubleRecordFrontendDTO doubleRecordFrontendDTO : doubleRecordFrontendStatusDTO.getDoubleRecordFrontendDTOs()) {
                result.addServiceResult(ServiceResult.newDoubleRecordErrorResult(UpdateStatusEnumDTO.FAILED, doubleRecordFrontendDTO));
            }
            result.setDoubleRecordKey(updateStore.getNewDoubleRecordKey());
        } else {
            String msg = "Unknown error";
            if (doubleRecordFrontendStatusDTO.getDoubleRecordFrontendDTOs() != null && !doubleRecordFrontendStatusDTO.getDoubleRecordFrontendDTOs().isEmpty()) {
                msg = doubleRecordFrontendStatusDTO.getDoubleRecordFrontendDTOs().get(0).getMessage();
            }
            result = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, msg);
        }
        return result;
    }

    private MarcRecord getRecord(RecordDataDTO recordDataDTO) {
        MarcRecord record = null;
        if (recordDataDTO != null) {
            List<Object> list = recordDataDTO.getContent();
            for (Object o : list) {
                if (o instanceof Node) {
                    record = MarcConverter.createFromMarcXChange(new DOMSource((Node) o));
                    break;
                } else if (o instanceof String && !((String) o).trim().isEmpty()) {
                    record = MarcConverter.convertFromMarcXChange((String) o);
                    break;
                }
            }
        }
        return record;
    }
}
