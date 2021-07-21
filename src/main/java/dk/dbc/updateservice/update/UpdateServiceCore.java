/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GNU GPL v3
 *  See license text at https://opensource.dbc.dk/licenses/gpl-3.0
 */

package dk.dbc.updateservice.update;

import dk.dbc.common.records.MarcConverter;
import dk.dbc.common.records.MarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.common.records.utils.RecordContentTransformer;
import dk.dbc.commons.metricshandler.MetricsHandlerBean;
import dk.dbc.opencat.connector.OpencatBusinessConnector;
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
import dk.dbc.updateservice.json.JsonMapper;
import dk.dbc.updateservice.solr.SolrBasis;
import dk.dbc.updateservice.solr.SolrFBS;
import dk.dbc.updateservice.utils.ResourceBundles;
import dk.dbc.updateservice.validate.Validator;
import dk.dbc.vipcore.exception.VipCoreException;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.MDC;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.w3c.dom.Node;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.xml.transform.dom.DOMSource;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.UUID;

import static dk.dbc.updateservice.utils.MDCUtil.MDC_PREFIX_ID_LOG_CONTEXT;
import static dk.dbc.updateservice.utils.MDCUtil.MDC_REQUEST_ID_LOG_CONTEXT;
import static dk.dbc.updateservice.utils.MDCUtil.MDC_REQUEST_PRIORITY;
import static dk.dbc.updateservice.utils.MDCUtil.MDC_TRACKING_ID_LOG_CONTEXT;

@Stateless
public class UpdateServiceCore {

    @EJB
    private Authenticator authenticator;

    @EJB
    private RawRepo rawRepo;

    @Inject
    private OpencatBusinessConnector opencatBusiness;

    @EJB
    private HoldingsItems holdingsItems;

    @EJB
    private VipCoreService vipCoreService;

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

    @Inject
    MetricsHandlerBean metricsHandlerBean;

    private static final XLogger LOGGER = XLoggerFactory.getXLogger(UpdateServiceCore.class);
    private static final String UPDATE_WATCHTAG = "request.updaterecord";
    private static final String GET_SCHEMAS_WATCHTAG = "request.getSchemas";
    private static final String UPDATE_SERVICE_NIL_RECORD = "update.service.nil.record";
    public static final String UPDATERECORD_STOPWATCH = "UpdateService";
    public static final String GET_SCHEMAS_STOPWATCH = "GetSchemas";

    private final Properties settings = JNDIResources.getProperties();

    private static final ResourceBundle resourceBundle = ResourceBundles.getBundle("actions");

    private GlobalActionState inititializeGlobalStateObject(GlobalActionState globalActionState, UpdateServiceRequestDTO updateServiceRequestDTO) {
        GlobalActionState newGlobalActionStateObject = new GlobalActionState(globalActionState);
        newGlobalActionStateObject.setUpdateServiceRequestDTO(updateServiceRequestDTO);
        newGlobalActionStateObject.setAuthenticator(authenticator);
        newGlobalActionStateObject.setRawRepo(rawRepo);
        newGlobalActionStateObject.setOpencatBusiness(opencatBusiness);
        newGlobalActionStateObject.setHoldingsItems(holdingsItems);
        newGlobalActionStateObject.setVipCoreService(vipCoreService);
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
        final StopWatch watch = new Log4JStopWatch();
        ServiceResult serviceResult;
        final GlobalActionState state = inititializeGlobalStateObject(globalActionState, updateServiceRequestDTO);
        logMdcUpdateMethodEntry(state);
        UpdateRequestAction updateRequestAction = null;
        ServiceEngine serviceEngine = null;
        UpdateRecordResponseDTO updateRecordResponseDTO = null;
        try {
            if (state.readRecord() != null) {
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("MDC: " + MDC.getCopyOfContextMap());
                    LOGGER.info("Request tracking id: " + updateServiceRequestDTO.getTrackingId());
                    LOGGER.info("updateRecord received UpdateServiceRequestDTO: {}", scramblePassword(JsonMapper.encodePretty(updateServiceRequestDTO)));
                }

                updateRequestAction = new UpdateRequestAction(state, settings);

                serviceEngine = new ServiceEngine(metricsHandlerBean);
                serviceEngine.setLoggerKeys(MDC.getCopyOfContextMap());
                serviceResult = serviceEngine.executeAction(updateRequestAction);

                updateRecordResponseDTO = UpdateRecordResponseDTOWriter.newInstance(serviceResult);

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
            updateRecordResponseDTO = UpdateRecordResponseDTOWriter.newInstance(serviceResult);
            return updateRecordResponseDTO;
        } catch (Throwable ex) {
            LOGGER.catching(ex);
            try {
                LOGGER.error("Exception while processing request: {}", scramblePassword(JsonMapper.encodePretty(updateServiceRequestDTO)));
            } catch (IOException e) {
                LOGGER.error("IOException while pretty printing updateServiceRequestDTO: {}", updateServiceRequestDTO);
            }
            serviceResult = convertUpdateErrorToResponse(ex);
            updateRecordResponseDTO = UpdateRecordResponseDTOWriter.newInstance(serviceResult);
            return updateRecordResponseDTO;
        } finally {
            if (LOGGER.isInfoEnabled()) {
                try {
                    LOGGER.info("updateRecord returning UpdateRecordResponseDTO: {}", scramblePassword(JsonMapper.encodePretty(updateRecordResponseDTO)));
                } catch (IOException e) {
                    LOGGER.info("updateRecord returning UpdateRecordResponseDTO: {}", updateRecordResponseDTO);
                }
            }
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
        final StopWatch watch = new Log4JStopWatch();
        SchemasResponseDTO schemasResponseDTO = null;

        try {
            MDC.put(MDC_TRACKING_ID_LOG_CONTEXT, schemasRequestDTO.getTrackingId());
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("getSchemas received SchemasRequestDTO: {}", scramblePassword(JsonMapper.encodePretty(schemasRequestDTO)));
            }

            if (schemasRequestDTO.getAuthenticationDTO() != null &&
                    schemasRequestDTO.getAuthenticationDTO().getGroupId() != null) {
                if (schemasRequestDTO.getTrackingId() != null) {
                    LOGGER.info("getSchemas request from {} with tracking id {}", schemasRequestDTO.getAuthenticationDTO().getGroupId(), schemasRequestDTO.getTrackingId());
                } else {
                    LOGGER.info("getSchemas request from {}", schemasRequestDTO.getAuthenticationDTO().getGroupId());
                }
            }

            final String groupId = Objects.requireNonNull(schemasRequestDTO.getAuthenticationDTO()).getGroupId();
            final String templateGroup = vipCoreService.getTemplateGroup(groupId);
            final Set<String> allowedLibraryRules = vipCoreService.getAllowedLibraryRules(groupId);
            final List<SchemaDTO> schemaDTOList = validator.getValidateSchemas(templateGroup, allowedLibraryRules);

            schemasResponseDTO = new SchemasResponseDTO();
            schemasResponseDTO.getSchemaDTOList().addAll(schemaDTOList);
            schemasResponseDTO.setUpdateStatusEnumDTO(UpdateStatusEnumDTO.OK);
            schemasResponseDTO.setError(false);
            return schemasResponseDTO;
        } catch (VipCoreException ex) {
            LOGGER.error("Caught VipCoreException exception", ex);
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
            try {
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("getSchemas returning SchemasResponseDTO: {}", scramblePassword(JsonMapper.encodePretty(schemasResponseDTO)));
                }
            } catch (IOException e) {
                LOGGER.info("getSchemas returning SchemasResponseDTO: {}", schemasResponseDTO);
            }
            watch.stop(GET_SCHEMAS_WATCHTAG);
        }
    }

    public UpdateRecordResponseDTO classificationCheck(BibliographicRecordDTO bibliographicRecordDTO) {
        try {
            final RecordDataDTO recordDataDTO = bibliographicRecordDTO.getRecordDataDTO();
            final MarcRecord marcRecord = getRecord(recordDataDTO);

            ServiceResult serviceResult = ServiceResult.newOkResult();
            if (marcRecord != null) {
                final MarcRecordReader recordReader = new MarcRecordReader(marcRecord);
                final String recordId = recordReader.getValue("001", "a");
                final int agencyId = Integer.parseInt(recordReader.getValue("001", "b"));
                if (rawRepo.recordExists(recordId, agencyId)) {
                    final MarcRecord oldRecord = loadRecord(recordId, agencyId);
                    final Set<Integer> holdingAgencies = holdingsItems.getAgenciesThatHasHoldingsForId(recordId);
                    if (!holdingAgencies.isEmpty()) {
                        List<String> classificationsChangedMessages = new ArrayList<>();
                        if (libraryRecordsHandler.hasClassificationsChanged(oldRecord, marcRecord, classificationsChangedMessages)) {
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
            final MarcRecord marcRecord = getRecord(recordDataDTO);

            ServiceResult serviceResult;
            if (marcRecord != null) {
                final MarcRecordReader reader = new MarcRecordReader(marcRecord);

                // Perform double record check only if the record doesn't already exist
                if (!rawRepo.recordExistsMaybeDeleted(reader.getRecordId(), reader.getAgencyIdAsInt())) {
                    final StopWatch watch = new Log4JStopWatch("opencatBusiness.checkDoubleRecordFrontend");
                    try {
                        final DoubleRecordFrontendStatusDTO doubleRecordFrontendStatusDTO = opencatBusiness.checkDoubleRecordFrontend(marcRecord);
                        serviceResult = DoubleRecordFrontendStatusDTOToServiceResult(doubleRecordFrontendStatusDTO);
                    } finally {
                        watch.stop();
                    }
                } else {
                    serviceResult = ServiceResult.newOkResult();
                }
            } else {
                serviceResult = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, "No record data found in request");
            }
            return UpdateRecordResponseDTOWriter.newInstance(serviceResult);
        } catch (Exception ex) {
            LOGGER.error("Exception during doubleRecordCheck", ex);
            ServiceResult serviceResult = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, "Please see the log for more information");
            return UpdateRecordResponseDTOWriter.newInstance(serviceResult);
        }
    }

    public boolean isServiceReady() {
        return true;
    }

    private void logMdcUpdateMethodEntry(GlobalActionState globalActionState) {
        final UpdateServiceRequestDTO updateServiceRequestDTO = globalActionState.getUpdateServiceRequestDTO();
        final UUID prefixId = UUID.randomUUID();
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
    }

    private void validateRequiredSettings() {
        for (String s : JNDIResources.getListOfRequiredJNDIResources()) {
            if (!settings.containsKey(s)) {
                throw new IllegalStateException("Required JNDI resource '" + s + "' not found");
            }
        }
    }

    private ServiceResult convertUpdateErrorToResponse(Throwable ex) {
        final Throwable throwable = findServiceException(ex);
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
        final Record record = rawRepo.fetchRecord(recordId, agencyId);
        return RecordContentTransformer.decodeRecord(record.getContent());
    }

    public ServiceResult DoubleRecordFrontendStatusDTOToServiceResult(DoubleRecordFrontendStatusDTO doubleRecordFrontendStatusDTO) {
        ServiceResult result;
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
        MarcRecord marcRecord = null;
        if (recordDataDTO != null) {
            final List<Object> list = recordDataDTO.getContent();
            for (Object o : list) {
                if (o instanceof Node) {
                    marcRecord = MarcConverter.createFromMarcXChange(new DOMSource((Node) o));
                    break;
                } else if (o instanceof String && !((String) o).trim().isEmpty()) {
                    marcRecord = MarcConverter.convertFromMarcXChange((String) o);
                    break;
                }
            }
        }
        return marcRecord;
    }

    /*
        When toString is called on AuthenticationDTO the password is scrambled. However then the DTO is pretty printed
        with JsonMapper the original password is kept which means the password is shown in the log which is not
        acceptable. This function takes the pretty printed Json (as a String) and replaced the password value with
        '****' so the value is suitable for logging.
     */
    static String scramblePassword(String input) {
        final int passwordValueStart = input.indexOf("password\" : \"") + 14;
        final int passwordValueEnd = input.indexOf("\"", passwordValueStart);

        return input.substring(0, passwordValueStart - 1) + "****" + input.substring(passwordValueEnd);
    }
}
