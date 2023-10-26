package dk.dbc.updateservice.update;

import com.fasterxml.jackson.core.JsonProcessingException;
import dk.dbc.common.records.ExpandCommonMarcRecord;
import dk.dbc.common.records.MarcRecordReader;
import dk.dbc.commons.metricshandler.MetricsHandlerBean;
import dk.dbc.holdingitems.content.HoldingsItemsConnector;
import dk.dbc.marc.binding.MarcRecord;
import dk.dbc.marc.reader.MarcReaderException;
import dk.dbc.marcxmerge.MarcXChangeMimeType;
import dk.dbc.opencat.connector.OpencatBusinessConnector;
import dk.dbc.rawrepo.RawRepoException;
import dk.dbc.rawrepo.Record;
import dk.dbc.rawrepo.RecordId;
import dk.dbc.rawrepo.dto.RecordEntryDTO;
import dk.dbc.updateservice.actions.EnqueueRecordAction;
import dk.dbc.updateservice.actions.GlobalActionState;
import dk.dbc.updateservice.actions.ServiceEngine;
import dk.dbc.updateservice.actions.ServiceResult;
import dk.dbc.updateservice.actions.StoreRecordAction;
import dk.dbc.updateservice.actions.UpdateRequestAction;
import dk.dbc.updateservice.auth.Authenticator;
import dk.dbc.updateservice.client.BibliographicRecordExtraData;
import dk.dbc.updateservice.dto.BibliographicRecordDTO;
import dk.dbc.updateservice.dto.DoubleRecordFrontendDTO;
import dk.dbc.updateservice.dto.DoubleRecordFrontendStatusDTO;
import dk.dbc.updateservice.dto.MessageEntryDTO;
import dk.dbc.updateservice.dto.RecordDTOMapper;
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
import dk.dbc.updateservice.rest.ApplicationConfig;
import dk.dbc.updateservice.solr.SolrBasis;
import dk.dbc.updateservice.solr.SolrFBS;
import dk.dbc.updateservice.utils.DeferredLogger;
import dk.dbc.updateservice.utils.ResourceBundles;
import dk.dbc.updateservice.validate.Validator;
import dk.dbc.vipcore.exception.VipCoreException;
import jakarta.ejb.EJB;
import jakarta.ejb.EJBException;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import org.perf4j.StopWatch;
import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.MDC;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.UUID;

import static dk.dbc.updateservice.update.DefaultEnrichmentRecordHandler.hasMinusEnrichment;
import static dk.dbc.updateservice.update.DefaultEnrichmentRecordHandler.shouldCreateEnrichmentRecordsResult;
import static dk.dbc.updateservice.utils.MDCUtil.MDC_PREFIX_ID_LOG_CONTEXT;
import static dk.dbc.updateservice.utils.MDCUtil.MDC_REQUEST_ID_LOG_CONTEXT;
import static dk.dbc.updateservice.utils.MDCUtil.MDC_REQUEST_PRIORITY;
import static dk.dbc.updateservice.utils.MDCUtil.MDC_TRACKING_ID_LOG_CONTEXT;

@SuppressWarnings("PMD.TooManyStaticImports")
@Stateless
public class UpdateServiceCore {

    @EJB
    private Authenticator authenticator;

    @EJB
    RawRepo rawRepo;

    @Inject
    private OpencatBusinessConnector opencatBusiness;

    @EJB
    HoldingsItemsConnector holdingsItems;

    @EJB
    VipCoreService vipCoreService;

    @EJB
    private SolrFBS solrService;

    @EJB
    private SolrBasis solrBasis;

    @EJB
    private Validator validator;

    @EJB
    public UpdateStore updateStore;

    @EJB
    LibraryRecordsHandler libraryRecordsHandler;

    @Inject
    MetricsHandlerBean metricsHandlerBean;

    private static final DeferredLogger LOGGER = new DeferredLogger(UpdateServiceCore.class);
    private static final String UPDATE_WATCHTAG = "request.updaterecord";
    private static final String GET_SCHEMAS_WATCHTAG = "request.getSchemas";
    private static final String UPDATE_SERVICE_NIL_RECORD = "update.service.nil.record";
    private static final String UPDATE_SERVICE_ALLOWALL_TYPE_CHANGE = "update.service.allowall.type.change";
    public static final String UPDATERECORD_STOPWATCH = "UpdateService";
    public static final String GET_SCHEMAS_STOPWATCH = "GetSchemas";

    final Properties settings = JNDIResources.getProperties();

    private static final ResourceBundle resourceBundle = ResourceBundles.getBundle("actions");

    private GlobalActionState initializeGlobalStateObject(GlobalActionState globalActionState, UpdateServiceRequestDTO updateServiceRequestDTO) {
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
     * Currently this function only check if it's an allowall/superallowall request
     * and if it's an attempt to change the record type from single to head. More may follow.
     *
     * @param updateServiceRequestDTO The incoming request
     * @param state the state for the record - kind of silly, but that's life
     * @return Either an OK Service result or an ERROR
     * @throws UpdateException something went wrong trying to read current record
     */
    private ServiceResult checkAllowAll(UpdateServiceRequestDTO updateServiceRequestDTO, GlobalActionState state) throws UpdateException {
        if (updateServiceRequestDTO.getSchemaName().toLowerCase().contains("allowall")) {
            MarcRecordReader reader = new MarcRecordReader(state.readRecord());
            RawRepo rawRepo = state.getRawRepo();
            if (rawRepo.recordExists(reader.getRecordId(), reader.getAgencyIdAsInt())) {
                Record rr = rawRepo.fetchRecord(reader.getRecordId(), reader.getAgencyIdAsInt());
                MarcRecord origin = UpdateRecordContentTransformer.decodeRecord(rr.getContent());
                MarcRecordReader originReader = new MarcRecordReader(origin);
                if ("h".equals(reader.getValue("004", 'a')) &&
                        "e".equals(originReader.getValue("004", 'a'))) {
                    final ResourceBundle bundle = ResourceBundles.getBundle("messages");
                    final String msg = bundle.getString(UPDATE_SERVICE_ALLOWALL_TYPE_CHANGE);
                    return ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, msg);
                }
            }
        }
        return ServiceResult.newOkResult();
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
        final StopWatch watch = new Log4JStopWatch().setTimeThreshold(ApplicationConfig.LOG_DURATION_THRESHOLD_MS);
        return LOGGER.call(log -> {
            ServiceResult serviceResult;
            final GlobalActionState state = initializeGlobalStateObject(globalActionState, updateServiceRequestDTO);
            logMdcUpdateMethodEntry(state);
            UpdateRequestAction updateRequestAction = null;
            ServiceEngine serviceEngine = null;
            UpdateRecordResponseDTO updateRecordResponseDTO = null;

            try {
                if (state.readRecord() != null) {
                    if (log.isInfoEnabled()) {
                        log.info("MDC: " + MDC.getCopyOfContextMap());
                        log.info("Request tracking id: " + updateServiceRequestDTO.getTrackingId());
                        log.info("updateRecord received UpdateServiceRequestDTO: {}", scramblePassword(JsonMapper.encodePretty(updateServiceRequestDTO)));
                    }

                    updateRequestAction = new UpdateRequestAction(state, settings);

                    serviceEngine = new ServiceEngine(metricsHandlerBean);
                    serviceEngine.setLoggerKeys(MDC.getCopyOfContextMap());
                    serviceResult = checkAllowAll(updateServiceRequestDTO, state);
                    if (serviceResult.getStatus() == UpdateStatusEnumDTO.OK) {
                        serviceResult = serviceEngine.executeAction(updateRequestAction);
                    }

                    updateRecordResponseDTO = UpdateRecordResponseDTOWriter.newInstance(serviceResult);

                    return updateRecordResponseDTO;
                } else {
                    final ResourceBundle bundle = ResourceBundles.getBundle("messages");
                    final String msg = bundle.getString(UPDATE_SERVICE_NIL_RECORD);

                    serviceResult = ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, msg);
                    log.error("Updateservice blev kaldt med tom record DTO");
                    return UpdateRecordResponseDTOWriter.newInstance(serviceResult);
                }
            } catch (SolrException ex) {
                log.error("Caught solr exception", ex);
                serviceResult = convertUpdateErrorToResponse(ex);
                updateRecordResponseDTO = UpdateRecordResponseDTOWriter.newInstance(serviceResult);
                return updateRecordResponseDTO;
            } catch (Throwable ex) {
                try {
                    log.error("Exception while processing request: {}", scramblePassword(JsonMapper.encodePretty(updateServiceRequestDTO)));
                } catch (IOException e) {
                    log.error("IOException while pretty printing updateServiceRequestDTO: {}", updateServiceRequestDTO);
                }
                serviceResult = convertUpdateErrorToResponse(ex);
                updateRecordResponseDTO = UpdateRecordResponseDTOWriter.newInstance(serviceResult);
                return updateRecordResponseDTO;
            } finally {
                if (log.isInfoEnabled()) {
                    try {
                        log.info("updateRecord returning UpdateRecordResponseDTO: {}", scramblePassword(JsonMapper.encodePretty(updateRecordResponseDTO)));
                    } catch (IOException e) {
                        log.info("updateRecord returning UpdateRecordResponseDTO: {}", updateRecordResponseDTO);
                    }
                }
                updateServiceFinallyCleanUp(watch, updateRequestAction, serviceEngine);
            }
        });
    }

    public void updateRecord(RecordEntryDTO recordEntryDTO) throws UpdateException {
        try {
            final RecordId recordId = RecordDTOMapper.getRecordId(recordEntryDTO);
            final String bibliographicRecordId = recordEntryDTO.getRecordId().getBibliographicRecordId();
            final int agencyId = recordEntryDTO.getRecordId().getAgencyId();
            final Record rawRecord = rawRepo.fetchRecord(bibliographicRecordId, agencyId);
            final List<RecordId> relationParents;

            RecordDTOMapper.toRecord(recordEntryDTO, rawRecord);

            // Set relations for active record. Deleted record don't have any relations
            if (!rawRecord.isDeleted()) {
                if (MarcXChangeMimeType.ENRICHMENT.equals(rawRecord.getMimeType())) {
                    relationParents = Collections.singletonList(getCommonRecord(bibliographicRecordId, agencyId));
                } else {
                    // Look for referenced records
                    final MarcRecord marcRecord = RecordDTOMapper.getMarcRecord(recordEntryDTO);
                    relationParents = getParentRelations(marcRecord);
                }
            } else {
                relationParents = Collections.emptyList();
            }

            final LibraryGroup libraryGroup = vipCoreService.getLibraryGroup(Integer.toString(agencyId));
            final String providerId = EnqueueRecordAction.getProvider(settings, libraryGroup);
            rawRepo.saveRecord(rawRecord);
            rawRepo.removeLinks(recordId);
            for (RecordId relation : relationParents) {
                makeSureParentRecordExists(relation.getBibliographicRecordId(), relation.getAgencyId());
                rawRepo.linkRecordAppend(recordId, relation);
            }

            rawRepo.enqueue(recordId, providerId, true, false, RawRepo.ENQUEUE_PRIORITY_DEFAULT_BATCH);
        } catch (JsonProcessingException | VipCoreException | RawRepoException ex) {
            throw new UpdateException(ex.getMessage(), ex);
        }
    }

    private void makeSureParentRecordExists(String bibliographicRecordId, int agencyId) throws UpdateException {
        boolean shouldSave = false;
        final Record parentRecord = rawRepo.fetchRecord(bibliographicRecordId, agencyId);
        final Instant originalModified = parentRecord.getModified();
        if (parentRecord.getMimeType() == null || parentRecord.getMimeType().isEmpty()) {
            // In case it is a brand-new record
            parentRecord.setMimeType(StoreRecordAction.getMarcXChangeMimetype(agencyId));
            parentRecord.setContentJson("{}".getBytes());
            shouldSave = true;
        }
        if (parentRecord.isDeleted()) {
            // Record already exists but is deleted
            parentRecord.setDeleted(false);
            shouldSave = true;
        }
        if (shouldSave) {
            parentRecord.setModified(originalModified);
            rawRepo.saveRecord(parentRecord);
        }
    }

    private RecordId getCommonRecord(String bibliographicRecordId, int agencyId) throws RawRepoException, VipCoreException, UpdateException {
        if (agencyId == 191919) {
            final List<Integer> agencyPriorityList = vipCoreService.getAgencyPriority(agencyId);
            agencyPriorityList.remove(Integer.valueOf(agencyId)); // We have to use Integer, as the valueOf which takes an int uses the value as index not object
            final Set<Integer> agenciesForRecord = rawRepo.agenciesForRecordAll(bibliographicRecordId);
            for (Integer agencyForRecord : agenciesForRecord) {
                if (agencyPriorityList.contains(agencyForRecord)) {
                    return new RecordId(bibliographicRecordId, agencyForRecord);
                }
            }

            throw new UpdateException(String.format("Unable to determine parent agency id for enrichment record %s:%s", bibliographicRecordId, agencyId));
        } else {
            return new RecordId(bibliographicRecordId, 870970);
        }
    }

    private List<RecordId> getParentRelations(MarcRecord marcRecord) {
        final List<RecordId> res = new ArrayList<>();

        final MarcRecordReader reader = new MarcRecordReader(marcRecord);

        // Handle 014/016 parent record
        final String parentRecordId = reader.getParentRecordId();
        final String parentAgencyIdAsString = reader.getParentAgencyId();
        if (parentRecordId != null && parentAgencyIdAsString != null) {
            res.add(new RecordId(parentRecordId, reader.getAgencyIdAsInt()));
        }

        // Handle authority records
        for (String fieldName : ExpandCommonMarcRecord.AUTHORITY_FIELD_LIST) {
            final List<String> authReferences = reader.getValues(fieldName, '6');
            for (String authReference : authReferences) {
                res.add(new RecordId(authReference, 870979));
            }
        }
        return res;
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
        final StopWatch watch = new Log4JStopWatch().setTimeThreshold(ApplicationConfig.LOG_DURATION_THRESHOLD_MS);
        return LOGGER.call(log -> {
            SchemasResponseDTO schemasResponseDTO = null;

            try {
                MDC.put(MDC_TRACKING_ID_LOG_CONTEXT, schemasRequestDTO.getTrackingId());
                if (log.isInfoEnabled()) {
                    log.info("getSchemas received SchemasRequestDTO: {}", scramblePassword(JsonMapper.encodePretty(schemasRequestDTO)));
                }

                if (schemasRequestDTO.getAuthenticationDTO() != null &&
                        schemasRequestDTO.getAuthenticationDTO().getGroupId() != null) {
                    if (schemasRequestDTO.getTrackingId() != null) {
                        log.info("getSchemas request from {} with tracking id {}", schemasRequestDTO.getAuthenticationDTO().getGroupId(), schemasRequestDTO.getTrackingId());
                    } else {
                        log.info("getSchemas request from {}", schemasRequestDTO.getAuthenticationDTO().getGroupId());
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
                log.error("Caught VipCoreException exception", ex);
                schemasResponseDTO = new SchemasResponseDTO();
                schemasResponseDTO.setErrorMessage(ex.getMessage());
                schemasResponseDTO.setUpdateStatusEnumDTO(UpdateStatusEnumDTO.FAILED);
                schemasResponseDTO.setError(true);
                return schemasResponseDTO;
            } catch (Throwable ex) {
                // TODO: returner ordentlig fejl her
                log.error("Caught Throwable", ex);
                schemasResponseDTO = new SchemasResponseDTO();
                schemasResponseDTO.setErrorMessage(ex.getMessage());
                schemasResponseDTO.setUpdateStatusEnumDTO(UpdateStatusEnumDTO.FAILED);
                schemasResponseDTO.setError(true);
                return schemasResponseDTO;
            } finally {
                try {
                    if (log.isInfoEnabled()) {
                        log.info("getSchemas returning SchemasResponseDTO: {}", scramblePassword(JsonMapper.encodePretty(schemasResponseDTO)));
                    }
                } catch (IOException e) {
                    log.info("getSchemas returning SchemasResponseDTO: {}", schemasResponseDTO);
                }
                watch.stop(GET_SCHEMAS_WATCHTAG);
            }
        });
    }

    public UpdateRecordResponseDTO classificationCheck(BibliographicRecordDTO bibliographicRecordDTO) {
        try {
            final RecordDataDTO recordDataDTO = bibliographicRecordDTO.getRecordDataDTO();
            final MarcRecord marcRecord = getRecord(recordDataDTO);

            if (marcRecord == null) {
                return UpdateRecordResponseDTOWriter.newInstance(
                        ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, "No record data found in request"));
            }

            if (!hasMinusEnrichment(marcRecord)) {
                final MarcRecordReader recordReader = new MarcRecordReader(marcRecord);
                final String recordId = recordReader.getValue("001", 'a');
                final int agencyId = Integer.parseInt(recordReader.getValue("001", 'b'));
                if (rawRepo.recordExists(recordId, agencyId)) {
                    final MarcRecord oldRecord = loadRecord(recordId, agencyId);
                    final Set<Integer> holdingAgencies = holdingsItems.getAgenciesWithHoldings(recordId);
                    if (!holdingAgencies.isEmpty()) {
                        final List<String> classificationsChangedMessages = new ArrayList<>();
                        if (libraryRecordsHandler.hasClassificationsChanged(oldRecord, marcRecord, classificationsChangedMessages) &&
                                shouldCreateEnrichmentRecordsResult(resourceBundle, marcRecord, oldRecord)) {
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

                            final ServiceResult serviceResult = new ServiceResult();
                            serviceResult.setStatus(UpdateStatusEnumDTO.FAILED);
                            serviceResult.setEntries(messageEntryDTOs);

                            return UpdateRecordResponseDTOWriter.newInstance(serviceResult);
                        }
                    }
                }
            }

            return UpdateRecordResponseDTOWriter.newInstance(ServiceResult.newOkResult());
        } catch (Exception ex) {
            LOGGER.use(log -> log.error("Exception during classificationCheck", ex));
            return UpdateRecordResponseDTOWriter.newInstance(
                    ServiceResult.newErrorResult(UpdateStatusEnumDTO.FAILED, "Please see the log for more information"));
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
                    final StopWatch watch = new Log4JStopWatch("opencatBusiness.checkDoubleRecordFrontend").setTimeThreshold(ApplicationConfig.LOG_DURATION_THRESHOLD_MS);
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
            LOGGER.use(log -> log.error("Exception during doubleRecordCheck", ex));
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
        String priority = Integer.toString(RawRepo.ENQUEUE_PRIORITY_DEFAULT_USER);
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
        LOGGER.use(log -> {
            if (engine != null) {
                log.info("Executed action:");
                engine.printActions(action);
            }
            log.info("");
            String watchTag;
            if (action != null && action.hasValidateOnlyOption()) {
                watchTag = UPDATE_WATCHTAG + ".validate";
            } else {
                watchTag = UPDATE_WATCHTAG + ".update";
            }
            watch.stop(watchTag);
        });
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

    protected MarcRecord loadRecord(String recordId, Integer agencyId) throws UpdateException, UnsupportedEncodingException, MarcReaderException {
        final Record record = rawRepo.fetchRecord(recordId, agencyId);
        return UpdateRecordContentTransformer.decodeRecord(record.getContent());
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

    private MarcRecord getRecord(RecordDataDTO recordDataDTO) throws UpdateException {
        MarcRecord marcRecord = null;
        if (recordDataDTO != null) {
            final List<Object> list = recordDataDTO.getContent();
            if (list != null) {
                for (Object o : list) {
                    String marcString = (String) o;
                    if (!"".equals(marcString.trim())) {
                        marcRecord = UpdateRecordContentTransformer.decodeRecord(marcString.getBytes(StandardCharsets.UTF_8));
                        break;
                    }
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
        try {
            final int passwordValueStart = input.indexOf("password\" : \"") + 14;
            final int passwordValueEnd = input.indexOf("\"\n", passwordValueStart);

            return input.substring(0, passwordValueStart - 1) + "****" + input.substring(passwordValueEnd);
        } catch (Exception e) {
            // This function must not throw exceptions, so if anything happens just return the original value
            return input;
        }
    }
}
